package org.fitzdircon.console.ifit2;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GrpcCredentialsTest {

    // --- discoverFromCandidateContent ---

    @Test
    public void discoverFromCandidateContent_discoversCredentials() throws Exception {
        Map<String, byte[]> content = new LinkedHashMap<>();
        content.put("ca",     pemBody("ca.pem"));
        content.put("client", pemBody("client.pem"));
        content.put("ckey",   pemBody("client.key"));

        GrpcCredentials.DiscoveredKeys keys =
                GrpcCredentials.discoverFromCandidateContent(content, "com.ifit.rivendell");

        assertEquals("client", keys.cert);
        assertEquals("ckey",   keys.key);
        assertEquals("ca",     keys.ca);
    }

    @Test
    public void discoverFromCandidateContent_fallbackPackage_secondSucceeds() throws Exception {
        Map<String, byte[]> badContent = new LinkedHashMap<>();
        badContent.put("u1", pemBody("unrelated.pem"));

        try {
            GrpcCredentials.discoverFromCandidateContent(badContent, "com.ifit.rivendell");
            fail("expected exception when first package has no identifiable credentials");
        } catch (Exception ignored) {}

        Map<String, byte[]> goodContent = new LinkedHashMap<>();
        goodContent.put("ca",     pemBody("ca.pem"));
        goodContent.put("client", pemBody("client.pem"));
        goodContent.put("ckey",   pemBody("client.key"));

        GrpcCredentials.DiscoveredKeys keys =
                GrpcCredentials.discoverFromCandidateContent(goodContent, "com.ifit.glassos_service");
        assertNotNull(keys.cert);
        assertNotNull(keys.key);
        assertNotNull(keys.ca);
    }

    @Test
    public void discoverFromCandidateContent_certChainRejectsUnrelatedCert() {
        Map<String, byte[]> content = new LinkedHashMap<>();
        content.put("u1", pemBody("unrelated.pem"));

        try {
            GrpcCredentials.discoverFromCandidateContent(content, "com.ifit.rivendell");
            fail("expected exception — unrelated cert cannot satisfy discovery");
        } catch (Exception ignored) {}
    }

    // --- fromCache ---

    @Test
    public void fromCache_hitWhenVersionMatches() {
        GrpcCredentials.DiscoveredKeys keys =
                GrpcCredentials.fromCache(42L, 42L, "client", "ckey", "ca");
        assertNotNull(keys);
        assertEquals("client", keys.cert);
        assertEquals("ckey",   keys.key);
        assertEquals("ca",     keys.ca);
    }

    @Test
    public void fromCache_missWhenVersionDiffers() {
        assertNull(GrpcCredentials.fromCache(43L, 42L, "client", "ckey", "ca"));
    }

    @Test
    public void fromCache_missWhenValuesMissing() {
        assertNull(GrpcCredentials.fromCache(42L, 42L, null,     "ckey", "ca"));
        assertNull(GrpcCredentials.fromCache(42L, 42L, "client", null,   "ca"));
        assertNull(GrpcCredentials.fromCache(42L, 42L, "client", "ckey", null));
    }

    // --- checkCertificateValidity ---

    @Test
    public void checkCertificateValidity_throwsOnExpiredCert() throws Exception {
        X509Certificate expired = loadCert("expired.pem");
        try {
            GrpcCredentials.checkCertificateValidity(expired, "test-label");
            fail("expected exception for expired cert");
        } catch (Exception e) {
            assertTrue("cause should be CertificateExpiredException",
                    e.getCause() instanceof CertificateExpiredException);
            assertTrue("message should mention expired",
                    e.getMessage().contains("expired") || e.getMessage().contains("Expired"));
        }
    }

    @Test
    public void checkCertificateValidity_passesForValidCert() throws Exception {
        X509Certificate ca = loadCert("ca.pem");
        GrpcCredentials.checkCertificateValidity(ca, "ca");
    }

    // --- helpers ---

    private static byte[] pemBody(String filename) {
        try (InputStream in = GrpcCredentialsTest.class.getResourceAsStream(
                "/credentials/" + filename)) {
            String pem = new String(readFully(in), StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (String line : pem.split("\n")) {
                if (!line.startsWith("-----")) sb.append(line).append('\n');
            }
            return sb.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load pemBody " + filename, e);
        }
    }

    private static X509Certificate loadCert(String filename) throws Exception {
        try (InputStream in = GrpcCredentialsTest.class.getResourceAsStream(
                "/credentials/" + filename)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(in);
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
