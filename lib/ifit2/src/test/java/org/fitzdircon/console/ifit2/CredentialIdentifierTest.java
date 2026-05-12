package org.fitzdircon.console.ifit2;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class CredentialIdentifierTest {

    private X509Certificate caCert;
    private X509Certificate clientCert;
    private PrivateKey clientKey;
    private X509Certificate otherCert;
    private PrivateKey otherKey;
    private X509Certificate unrelatedCert;

    @Before
    public void loadFixtures() throws Exception {
        caCert      = loadCert("ca.pem");
        clientCert  = loadCert("client.pem");
        clientKey   = loadKey("client.key");
        otherCert   = loadCert("other.pem");
        otherKey    = loadKey("other.key");
        unrelatedCert = loadCert("unrelated.pem");
    }

    // --- findCa ---

    @Test
    public void findCa_returnsSelfSignedCert() throws Exception {
        List<CredentialIdentifier.CandidateCert> certs = Arrays.asList(
                candidate("client", clientCert),
                candidate("ca", caCert),
                candidate("other", otherCert)
        );
        CredentialIdentifier.CandidateCert result = CredentialIdentifier.findCa(certs, "pkg");
        assertSame(caCert, result.cert);
    }

    @Test
    public void findCa_throwsWhenNoCaPresent() {
        List<CredentialIdentifier.CandidateCert> certs = Arrays.asList(
                candidate("client", clientCert),
                candidate("other", otherCert)
        );
        try {
            CredentialIdentifier.findCa(certs, "pkg");
            fail("expected exception");
        } catch (Exception e) {
            assertEquals("No self-signed CA certificate found in pkg", e.getMessage());
        }
    }

    // --- findClientCert ---

    @Test
    public void findClientCert_prefersCnMatch() throws Exception {
        CredentialIdentifier.CandidateCert ca = candidate("ca", caCert);
        List<CredentialIdentifier.CandidateCert> certs = Arrays.asList(
                ca,
                candidate("other", otherCert),   // signed by CA, CN=other — appears first
                candidate("client", clientCert)   // signed by CA, CN=com.ifit.rivendell
        );
        CredentialIdentifier.CandidateCert result =
                CredentialIdentifier.findClientCert(certs, ca, "pkg");
        assertSame(clientCert, result.cert);
    }

    @Test
    public void findClientCert_fallsBackToFirstValid() throws Exception {
        CredentialIdentifier.CandidateCert ca = candidate("ca", caCert);
        List<CredentialIdentifier.CandidateCert> certs = Arrays.asList(
                ca,
                candidate("other", otherCert)   // signed by CA, CN=other — no CN match
        );
        CredentialIdentifier.CandidateCert result =
                CredentialIdentifier.findClientCert(certs, ca, "pkg");
        assertSame(otherCert, result.cert);
    }

    @Test
    public void findClientCert_rejectsUnsignedByCa() throws Exception {
        CredentialIdentifier.CandidateCert ca = candidate("ca", caCert);
        List<CredentialIdentifier.CandidateCert> certs = Arrays.asList(
                ca,
                candidate("unrelated", unrelatedCert)
        );
        try {
            CredentialIdentifier.findClientCert(certs, ca, "pkg");
            fail("expected exception");
        } catch (Exception e) {
            assertEquals("No client certificate signed by CA found in pkg", e.getMessage());
        }
    }

    @Test
    public void findClientCert_throwsWhenNoneValid() throws Exception {
        CredentialIdentifier.CandidateCert ca = candidate("ca", caCert);
        try {
            CredentialIdentifier.findClientCert(Arrays.asList(ca), ca, "pkg");
            fail("expected exception");
        } catch (Exception e) {
            assertEquals("No client certificate signed by CA found in pkg", e.getMessage());
        }
    }

    // --- findMatchingKey ---

    @Test
    public void findMatchingKey_matchesByModulus() throws Exception {
        CredentialIdentifier.CandidateCert client = candidate("client", clientCert);
        List<CredentialIdentifier.CandidateKey> keys = Arrays.asList(
                candidateKey("other", otherKey),    // wrong modulus
                candidateKey("client", clientKey)   // correct modulus
        );
        CredentialIdentifier.CandidateKey result =
                CredentialIdentifier.findMatchingKey(keys, client, "pkg");
        assertSame(clientKey, result.key);
    }

    @Test
    public void findMatchingKey_throwsWhenNoMatch() throws Exception {
        CredentialIdentifier.CandidateCert client = candidate("client", clientCert);
        List<CredentialIdentifier.CandidateKey> keys =
                Arrays.asList(candidateKey("other", otherKey));
        try {
            CredentialIdentifier.findMatchingKey(keys, client, "pkg");
            fail("expected exception");
        } catch (Exception e) {
            assertEquals("No RSA private key matching client cert found in pkg", e.getMessage());
        }
    }

    // --- stripJpegMarkers ---

    @Test
    public void stripJpegMarkers_stripsLeadingAndTrailing() {
        String body = "MIIB\nAAAB\n";
        String withMarkers = "FFD8\n" + body + "FFD9\n";
        assertEquals(body, CredentialIdentifier.stripJpegMarkers(withMarkers));
    }

    @Test
    public void stripJpegMarkers_noopWhenNoMarkers() {
        String content = "MIIB\nAAAB\n";
        assertEquals(content, CredentialIdentifier.stripJpegMarkers(content));
    }

    // --- certCn ---

    @Test
    public void certCn_extractsCommonName() throws Exception {
        assertEquals("com.ifit.rivendell", CredentialIdentifier.certCn(clientCert));
    }

    // --- extractImgIconNames ---

    @Test
    public void extractImgIconNames_findsHexSuffixedNames() {
        byte[] arsc = "garbage\0img_icon_abc123\0stuff\0img_icon_def456\0end".getBytes();
        List<String> names = CredentialIdentifier.extractImgIconNames(arsc);
        assertEquals(Arrays.asList("img_icon_abc123", "img_icon_def456"), names);
    }

    @Test
    public void extractImgIconNames_deduplicates() {
        byte[] arsc = "img_icon_abc123\0img_icon_abc123".getBytes();
        List<String> names = CredentialIdentifier.extractImgIconNames(arsc);
        assertEquals(1, names.size());
    }

    @Test
    public void extractImgIconNames_stopsAtNonHexChar() {
        // 'g' is not a hex digit — scanner stops there and returns the prefix up to it
        byte[] arsc = "img_icon_abcg".getBytes();
        List<String> names = CredentialIdentifier.extractImgIconNames(arsc);
        assertEquals(Arrays.asList("img_icon_abc"), names);
    }

    // --- helpers ---

    private static CredentialIdentifier.CandidateCert candidate(String name, X509Certificate cert) {
        return new CredentialIdentifier.CandidateCert(name, cert);
    }

    private static CredentialIdentifier.CandidateKey candidateKey(String name, PrivateKey key) {
        return new CredentialIdentifier.CandidateKey(name, key);
    }

    private static X509Certificate loadCert(String filename) throws Exception {
        try (InputStream in = CredentialIdentifierTest.class.getResourceAsStream(
                "/credentials/" + filename)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
        }
    }

    private static PrivateKey loadKey(String filename) throws Exception {
        try (InputStream in = CredentialIdentifierTest.class.getResourceAsStream(
                "/credentials/" + filename)) {
            String pem = new String(readFully(in));
            String base64 = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
    }

    private static byte[] readFully(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
