package org.fitzdircon.console.ifit2;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

public final class GrpcCredentials {
    public static final String CLIENT_ID_HEADER_VALUE = "com.ifit.rivendell";

    private static final String[] RESOURCE_PACKAGES = {
            "com.ifit.rivendell",
            "com.ifit.glassos_service"
    };

    private final SSLContext sslContext;

    private GrpcCredentials(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    public SSLContext sslContext() {
        return sslContext;
    }

    public static GrpcCredentials load(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        SharedPreferences prefs = context.getSharedPreferences("glassos_cred_v2", Context.MODE_PRIVATE);
        List<Exception> failures = new ArrayList<>();
        for (String packageName : RESOURCE_PACKAGES) {
            try {
                DiscoveredKeys keys = resolveKeys(pm, prefs, packageName);
                Resources resources = pm.getResourcesForApplication(packageName);
                String certPem = readPem(resources, packageName, keys.cert, "CERTIFICATE");
                String keyPem  = readPem(resources, packageName, keys.key,  "PRIVATE KEY");
                String caPem   = readPem(resources, packageName, keys.ca,   "CERTIFICATE");
                return new GrpcCredentials(buildSslContext(certPem, keyPem, caPem));
            } catch (Exception e) {
                Log.e("FZ:Platform", "Credential discovery failed for " + packageName + ": " + e.getMessage());
                failures.add(e);
            }
        }
        Exception e = new Exception("GlassOS credential resources not found in any known package");
        for (Exception f : failures) e.addSuppressed(f);
        Log.e("FZ:Platform", "All credential packages exhausted: " + e.getMessage(), e);
        throw e;
    }

    private static DiscoveredKeys resolveKeys(PackageManager pm, SharedPreferences prefs,
            String packageName) throws Exception {
        long currentVersion = pm.getPackageInfo(packageName, 0).versionCode;
        long cachedVersion  = prefs.getLong(packageName + "_version", -1);

        DiscoveredKeys cached = fromCache(currentVersion, cachedVersion,
                prefs.getString(packageName + "_cert", null),
                prefs.getString(packageName + "_key",  null),
                prefs.getString(packageName + "_ca",   null));
        if (cached != null) return cached;

        DiscoveredKeys keys = discoverKeys(pm, packageName);
        prefs.edit()
                .putLong(packageName + "_version", currentVersion)
                .putString(packageName + "_cert", keys.cert)
                .putString(packageName + "_key",  keys.key)
                .putString(packageName + "_ca",   keys.ca)
                .apply();
        return keys;
    }

    // Discovers the CA cert, client cert, and matching private key for the gRPC mTLS channel.
    // Enumerates img_icon_* raw resources via the resources.arsc key string pool (works regardless
    // of whether the APK uses full paths like res/raw/img_icon_x or obfuscated paths like res/I6.jpg),
    // then identifies the correct three resources by their cryptographic properties.
    private static DiscoveredKeys discoverKeys(PackageManager pm, String packageName) throws Exception {
        String apkPath = pm.getApplicationInfo(packageName, 0).sourceDir;
        Resources resources = pm.getResourcesForApplication(packageName);

        byte[] arsc = readArsc(apkPath);
        Map<String, byte[]> resourceContent = new LinkedHashMap<>();
        for (String fullName : CredentialIdentifier.extractImgIconNames(arsc)) {
            int id = resources.getIdentifier(fullName, "raw", packageName);
            if (id <= 0) continue;
            String key = fullName.substring("img_icon_".length());
            resourceContent.put(key, readFully(resources.openRawResource(id)));
        }
        return discoverFromCandidateContent(resourceContent, packageName);
    }

    static DiscoveredKeys discoverFromCandidateContent(
            Map<String, byte[]> resourceContent, String packageName) throws Exception {
        List<CredentialIdentifier.CandidateCert> certCandidates = new ArrayList<>();
        List<CredentialIdentifier.CandidateKey>  keyCandidates  = new ArrayList<>();

        for (Map.Entry<String, byte[]> entry : resourceContent.entrySet()) {
            String key = entry.getKey();
            String stripped = CredentialIdentifier.stripJpegMarkers(
                    new String(entry.getValue(), StandardCharsets.UTF_8));

            try {
                String pem = "-----BEGIN CERTIFICATE-----\n" + stripped + "-----END CERTIFICATE-----\n";
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(bytes(pem));
                certCandidates.add(new CredentialIdentifier.CandidateCert(key, cert));
            } catch (Exception ignored) {}

            try {
                String pem = "-----BEGIN PRIVATE KEY-----\n" + stripped + "-----END PRIVATE KEY-----\n";
                keyCandidates.add(new CredentialIdentifier.CandidateKey(key, parsePrivateKey(pem)));
            } catch (Exception ignored) {}
        }

        CredentialIdentifier.CandidateCert ca     = CredentialIdentifier.findCa(certCandidates, packageName);
        CredentialIdentifier.CandidateCert client = CredentialIdentifier.findClientCert(certCandidates, ca, packageName);
        CredentialIdentifier.CandidateKey  key    = CredentialIdentifier.findMatchingKey(keyCandidates, client, packageName);
        return new DiscoveredKeys(client.name, key.name, ca.name);
    }

    static DiscoveredKeys fromCache(long currentVersion, long cachedVersion,
            String cert, String key, String ca) {
        if (cachedVersion == currentVersion && cert != null && key != null && ca != null)
            return new DiscoveredKeys(cert, key, ca);
        return null;
    }

    static void checkCertificateValidity(X509Certificate cert, String label) throws Exception {
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            throw new Exception("Certificate expired [" + label + "]: " + e.getMessage(), e);
        } catch (CertificateNotYetValidException e) {
            throw new Exception("Certificate not yet valid [" + label + "]: " + e.getMessage(), e);
        }
    }

    private static byte[] readArsc(String apkPath) throws Exception {
        try (ZipFile apk = new ZipFile(apkPath)) {
            ZipEntry entry = apk.getEntry("resources.arsc");
            if (entry == null) throw new Exception("resources.arsc not found in " + apkPath);
            return readFully(apk.getInputStream(entry));
        }
    }

    private static byte[] readFully(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        } finally {
            in.close();
        }
        return out.toByteArray();
    }

    private static String readPem(Resources resources, String packageName, String key, String type)
            throws Exception {
        int id = resources.getIdentifier("img_icon_" + key, "raw", packageName);
        if (id <= 0) throw new Resources.NotFoundException("img_icon_" + key);
        try (InputStream in = resources.openRawResource(id)) {
            String body = CredentialIdentifier.stripJpegMarkers(
                    new String(readFully(in), StandardCharsets.UTF_8));
            return "-----BEGIN " + type + "-----\n" + body + "-----END " + type + "-----\n";
        }
    }

    private static SSLContext buildSslContext(String certPem, String keyPem, String caPem)
            throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate clientCert = cf.generateCertificate(bytes(certPem));
        Certificate caCert     = cf.generateCertificate(bytes(caPem));

        checkCertificateValidity((X509Certificate) clientCert, "client");
        checkCertificateValidity((X509Certificate) caCert, "ca");

        PrivateKey  privateKey = parsePrivateKey(keyPem);

        java.security.KeyStore keyStore = java.security.KeyStore.getInstance(
                java.security.KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("client", privateKey, new char[0], new Certificate[]{clientCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        java.security.KeyStore trustStore = java.security.KeyStore.getInstance(
                java.security.KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("glassos-ca", caCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return sslContext;
    }

    // GlassOS is API 26+; java.util.Base64 is safe here
    private static PrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    static final class DiscoveredKeys {
        final String cert, key, ca;
        DiscoveredKeys(String cert, String key, String ca) { this.cert = cert; this.key = key; this.ca = ca; }
    }

}
