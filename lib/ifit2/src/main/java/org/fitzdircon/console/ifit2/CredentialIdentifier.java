package org.fitzdircon.console.ifit2;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;

// Pure-Java crypto identification logic extracted from GrpcCredentials for testability.
// No Android imports — callable from plain JUnit tests.
final class CredentialIdentifier {

    static final class CandidateCert {
        final String name;
        final X509Certificate cert;
        CandidateCert(String name, X509Certificate cert) { this.name = name; this.cert = cert; }
    }

    static final class CandidateKey {
        final String name;
        final PrivateKey key;
        CandidateKey(String name, PrivateKey key) { this.name = name; this.key = key; }
    }

    static CandidateCert findCa(List<CandidateCert> certs, String pkg) throws Exception {
        for (CandidateCert c : certs)
            if (c.cert.getSubjectX500Principal().equals(c.cert.getIssuerX500Principal())) return c;
        throw new Exception("No self-signed CA certificate found in " + pkg);
    }

    static CandidateCert findClientCert(List<CandidateCert> certs, CandidateCert ca,
            String pkg) throws Exception {
        CandidateCert first = null;
        for (CandidateCert c : certs) {
            if (c == ca) continue;
            try {
                c.cert.verify(ca.cert.getPublicKey());
                if (GrpcCredentials.CLIENT_ID_HEADER_VALUE.equals(certCn(c.cert))) return c;
                if (first == null) first = c;
            } catch (Exception ignored) {}
        }
        if (first != null) return first;
        throw new Exception("No client certificate signed by CA found in " + pkg);
    }

    static String certCn(X509Certificate cert) {
        for (String part : cert.getSubjectX500Principal().getName().split(",")) {
            part = part.trim();
            if (part.startsWith("CN=")) return part.substring(3);
        }
        return "";
    }

    static CandidateKey findMatchingKey(List<CandidateKey> keys, CandidateCert client,
            String pkg) throws Exception {
        if (!(client.cert.getPublicKey() instanceof RSAPublicKey))
            throw new Exception("Client cert is not RSA in " + pkg);
        BigInteger modulus = ((RSAPublicKey) client.cert.getPublicKey()).getModulus();
        for (CandidateKey k : keys)
            if (k.key instanceof RSAPrivateKey && ((RSAPrivateKey) k.key).getModulus().equals(modulus))
                return k;
        throw new Exception("No RSA private key matching client cert found in " + pkg);
    }

    static String stripJpegMarkers(String content) {
        StringBuilder sb = new StringBuilder();
        for (String line : content.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !"FFD8".equals(t) && !"FFD9".equals(t)) sb.append(t).append('\n');
        }
        return sb.toString();
    }

    // Scans the binary resources.arsc for img_icon_* key strings. These are stored as UTF-8 in the
    // package key string pool, so a simple byte scan finds them regardless of obfuscated file paths.
    static List<String> extractImgIconNames(byte[] arsc) {
        List<String> names = new ArrayList<>();
        byte[] prefix = "img_icon_".getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= arsc.length - prefix.length; i++) {
            for (int j = 0; j < prefix.length; j++) {
                if (arsc[i + j] != prefix[j]) continue outer;
            }
            int end = i + prefix.length;
            while (end < arsc.length) {
                byte b = arsc[end];
                if ((b >= '0' && b <= '9') || (b >= 'a' && b <= 'f')) end++;
                else break;
            }
            String name = new String(arsc, i, end - i, StandardCharsets.UTF_8);
            if (!names.contains(name)) names.add(name);
        }
        return names;
    }

    private CredentialIdentifier() {}
}
