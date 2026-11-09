package com.syswonder.roboguide.hub;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.net.ssl.HttpsURLConnection;

import org.json.JSONObject;

/**
 * Native cryptographic and mutual TLS (mTLS) helper for RoboGuide Phone Node.
 * Manages X.509 certificate parsing, PKCS#8 private key loading, KeyStore/TrustStore
 * creation, certificate digest computation, and SSLContext generation for gRPC and HTTPS.
 */
public class PhoneNodeSecurityHelper {

    private static final String TAG = "PhoneNodeSecurity";

    public static class SslBundle {
        public final SSLContext sslContext;
        public final SSLSocketFactory socketFactory;
        public final X509TrustManager trustManager;

        public SslBundle(SSLContext sslContext, SSLSocketFactory socketFactory, X509TrustManager trustManager) {
            this.sslContext = sslContext;
            this.socketFactory = socketFactory;
            this.trustManager = trustManager;
        }
    }

    /**
     * Compute SHA-256 fingerprint of X.509 certificate DER bytes as lowercase 64-character hex.
     * Matches RoboGuide Controller's peer_certificate_digest calculation.
     */
    public static String computeCertSha256Digest(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] der = cert.getEncoded();
            byte[] hash = md.digest(der);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to compute cert digest", e);
            return "";
        }
    }

    /**
     * Parse X.509 certificates from PEM string containing one or more -----BEGIN CERTIFICATE----- blocks.
     */
    public static List<X509Certificate> parseCertificates(String pem) throws Exception {
        if (pem == null || pem.trim().isEmpty()) {
            return Collections.emptyList();
        }
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream bais = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
        List<X509Certificate> certs = new ArrayList<>();
        while (bais.available() > 0) {
            try {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);
                if (cert != null) {
                    certs.add(cert);
                }
            } catch (Exception e) {
                // End of certificates in stream
                break;
            }
        }
        return certs;
    }

    public static byte[] decodeBase64(String str) {
        try {
            return java.util.Base64.getDecoder().decode(str);
        } catch (Throwable t) {
            return android.util.Base64.decode(str, android.util.Base64.DEFAULT);
        }
    }

    public static String encodeBase64(byte[] bytes) {
        try {
            return java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Throwable t) {
            return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
        }
    }

    /**
     * Parse a PKCS#8 PrivateKey from PEM string (supports both RSA and EC secp256r1 keys).
     */
    public static PrivateKey parsePrivateKey(String pem) throws Exception {
        if (pem == null || pem.trim().isEmpty()) {
            throw new IllegalArgumentException("Private key PEM string is empty");
        }

        // Clean PEM boundaries and whitespace
        String clean = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN EC PRIVATE KEY-----", "")
                .replace("-----END EC PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = decodeBase64(clean);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

        // Try RSA first, then EC
        try {
            KeyFactory kfRsa = KeyFactory.getInstance("RSA");
            return kfRsa.generatePrivate(keySpec);
        } catch (Exception rsaEx) {
            try {
                KeyFactory kfEc = KeyFactory.getInstance("EC");
                return kfEc.generatePrivate(keySpec);
            } catch (Exception ecEx) {
                throw new IllegalArgumentException("Unsupported private key format. Expected PKCS#8 RSA or EC key", ecEx);
            }
        }
    }

    /**
     * Generate an EC (secp256r1) or RSA 2048 keypair for the device.
     */
    public static KeyPair generateDeviceKeyPair(String algorithm) throws Exception {
        KeyPairGenerator kpg;
        if ("EC".equalsIgnoreCase(algorithm)) {
            kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        } else {
            kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
        }
        return kpg.generateKeyPair();
    }

    /**
     * Export PrivateKey to PEM string.
     */
    public static String exportPrivateKeyToPem(PrivateKey key) {
        String base64 = encodeBase64(key.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PRIVATE KEY-----\n");
        int idx = 0;
        while (idx < base64.length()) {
            int end = Math.min(idx + 64, base64.length());
            sb.append(base64, idx, end).append("\n");
            idx = end;
        }
        sb.append("-----END PRIVATE KEY-----\n");
        return sb.toString();
    }

    /**
     * Export PublicKey to PEM string.
     */
    public static String exportPublicKeyToPem(PublicKey key) {
        String base64 = encodeBase64(key.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PUBLIC KEY-----\n");
        int idx = 0;
        while (idx < base64.length()) {
            int end = Math.min(idx + 64, base64.length());
            sb.append(base64, idx, end).append("\n");
            idx = end;
        }
        sb.append("-----END PUBLIC KEY-----\n");
        return sb.toString();
    }

    /**
     * Build an mTLS SslBundle with server CA verification and client certificate authentication.
     *
     * @param serverCaPem CA certificate PEM to verify server TLS certificate (null/empty uses system CAs)
     * @param clientCertPem Client certificate PEM chain (null if one-way TLS)
     * @param clientKeyPem Client private key PEM (null if one-way TLS)
     */
    public static SslBundle buildSslBundle(String serverCaPem, String clientCertPem, String clientKeyPem) throws Exception {
        // 1. Build TrustManager for Server CA
        TrustManager[] trustManagers;
        if (serverCaPem != null && !serverCaPem.trim().isEmpty()) {
            List<X509Certificate> caCerts = parseCertificates(serverCaPem);
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            for (int i = 0; i < caCerts.size(); i++) {
                trustStore.setCertificateEntry("ca-" + i, caCerts.get(i));
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        } else {
            // Use system default trust managers
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            trustManagers = tmf.getTrustManagers();
        }

        X509TrustManager x509TrustManager = null;
        for (TrustManager tm : trustManagers) {
            if (tm instanceof X509TrustManager) {
                x509TrustManager = (X509TrustManager) tm;
                break;
            }
        }
        if (x509TrustManager == null) {
            throw new IllegalStateException("No X509TrustManager available");
        }

        // 2. Build KeyManager for Client Certificate
        KeyManager[] keyManagers = null;
        if (clientCertPem != null && !clientCertPem.trim().isEmpty() &&
            clientKeyPem != null && !clientKeyPem.trim().isEmpty()) {
            List<X509Certificate> certChain = parseCertificates(clientCertPem);
            if (certChain.isEmpty()) {
                throw new IllegalArgumentException("No valid certificates in clientCertPem");
            }
            PrivateKey privateKey = parsePrivateKey(clientKeyPem);

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            char[] pwd = "changeit".toCharArray();
            keyStore.setKeyEntry("client-key", privateKey, pwd, certChain.toArray(new X509Certificate[0]));

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, pwd);
            keyManagers = kmf.getKeyManagers();
        }

        // 3. Initialize SSLContext (supporting TLS 1.2 and TLS 1.3)
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagers, trustManagers, new SecureRandom());

        return new SslBundle(sslContext, sslContext.getSocketFactory(), x509TrustManager);
    }

    /**
     * Strict HostnameVerifier that checks the peer certificate against the expected server name.
     * Never bypasses hostname checks: rejects null sessions or mismatched certificates.
     */
    public static HostnameVerifier createStrictHostnameVerifier(final String expectedServerName) {
        final HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        return new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                if (session == null) {
                    return false;
                }
                if (expectedServerName != null && !expectedServerName.trim().isEmpty()) {
                    String expected = expectedServerName.trim();
                    return defaultVerifier.verify(expected, session);
                }
                return defaultVerifier.verify(hostname, session);
            }
        };
    }

    /**
     * Verifies that the provided private key matches the public key embedded in the leaf certificate.
     */
    public static boolean verifyKeyMatchesCertificate(String certPem, String keyPem) {
        if (certPem == null || certPem.trim().isEmpty() || keyPem == null || keyPem.trim().isEmpty()) {
            return false;
        }
        try {
            List<X509Certificate> certs = parseCertificates(certPem);
            if (certs.isEmpty()) return false;
            PublicKey pubKey = certs.get(0).getPublicKey();
            PrivateKey privKey = parsePrivateKey(keyPem);

            byte[] challenge = "RoboGuide-Key-Match-Challenge-Nonce".getBytes(StandardCharsets.UTF_8);
            String signAlgorithm;
            String alg = pubKey.getAlgorithm();
            if ("RSA".equalsIgnoreCase(alg)) {
                signAlgorithm = "SHA256withRSA";
            } else if ("EC".equalsIgnoreCase(alg)) {
                signAlgorithm = "SHA256withECDSA";
            } else {
                signAlgorithm = "SHA256with" + alg;
            }
            java.security.Signature signer = java.security.Signature.getInstance(signAlgorithm);
            signer.initSign(privKey);
            signer.update(challenge);
            byte[] signature = signer.sign();

            java.security.Signature verifier = java.security.Signature.getInstance(signAlgorithm);
            verifier.initVerify(pubKey);
            verifier.update(challenge);
            return verifier.verify(signature);
        } catch (Exception e) {
            Log.w(TAG, "Key and certificate match verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Secure credential persistence in application's private internal storage directory.
     * Never stores in shared external storage or browser localStorage.
     */
    public static synchronized void saveCredentialsToSecureStorage(Context context, JSONObject credentials) {
        if (context == null) return;
        try {
            File secDir = new File(context.getFilesDir(), "security");
            if (!secDir.exists()) {
                secDir.mkdirs();
                secDir.setReadable(true, true);
                secDir.setWritable(true, true);
            }
            File credFile = new File(secDir, "credentials.json");
            try (FileOutputStream fos = new FileOutputStream(credFile)) {
                fos.write(credentials.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            credFile.setReadable(false, false);
            credFile.setReadable(true, true);
            credFile.setWritable(true, true);
            Log.i(TAG, "Saved security credentials securely to " + credFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Failed to save secure credentials", e);
        }
    }

    /**
     * Load credentials from application's private internal storage directory.
     */
    public static synchronized JSONObject loadCredentialsFromSecureStorage(Context context) {
        if (context == null) return new JSONObject();
        try {
            File credFile = new File(new File(context.getFilesDir(), "security"), "credentials.json");
            if (!credFile.exists()) {
                return new JSONObject();
            }
            try (FileInputStream fis = new FileInputStream(credFile)) {
                byte[] data = new byte[(int) credFile.length()];
                fis.read(data);
                return new JSONObject(new String(data, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load secure credentials from file: " + e.getMessage());
            return new JSONObject();
        }
    }

    /**
     * Explicitly delete secure credential storage.
     */
    public static synchronized boolean clearSecureStorage(Context context) {
        if (context == null) return false;
        try {
            File credFile = new File(new File(context.getFilesDir(), "security"), "credentials.json");
            if (credFile.exists()) {
                return credFile.delete();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to clear secure storage", e);
            return false;
        }
    }
}
