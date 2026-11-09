package com.syswonder.roboguide.hub;

import android.content.Context;
import org.json.JSONObject;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Structured configuration model for RoboGuide Phone Node & Operator connection.
 * Holds endpoint coordinates, cluster identifier, and PKI mTLS credentials.
 * Preserves private keys natively so that redacted UI configurations and automatic
 * reconnects never overwrite stored keys with empty values.
 */
public class PhoneNodeConfig {

    private static volatile PhoneNodeConfig activeVault = new PhoneNodeConfig();

    public String clusterId = "roboguide-field-cluster";
    public String nodeId = "";
    public String nodeGrpcHost = "192.168.2.20";
    public int nodeGrpcPort = 50051;
    public String operatorHttpsUrl = "https://192.168.2.20:8080";
    public String artifactHttpsUrl = "https://192.168.2.20:8090";
    public String tlsServerName = "rg-controller";
    public String operatorTlsServerName = "rg-field-controller";
    public boolean useMtls = false;

    // Certificates and keys (PEM encoded)
    public String serverCaCertPem = "";
    public String operatorCaCertPem = "";
    public String nodeCertPem = "";
    public String nodeKeyPem = "";
    public String operatorCertPem = "";
    public String operatorKeyPem = "";
    public String operatorRole = "viewer";

    public PhoneNodeConfig() {}

    public static PhoneNodeConfig getActiveVault() {
        return activeVault;
    }

    public static void setActiveVault(PhoneNodeConfig cfg) {
        if (cfg != null) {
            activeVault = cfg;
        }
    }

    public static PhoneNodeConfig fromJson(JSONObject json) {
        return fromJson(json, activeVault);
    }

    public static PhoneNodeConfig fromJson(JSONObject json, PhoneNodeConfig fallback) {
        PhoneNodeConfig cfg = new PhoneNodeConfig();
        if (json == null) {
            if (fallback != null) {
                cfg.nodeKeyPem = fallback.nodeKeyPem;
                cfg.operatorKeyPem = fallback.operatorKeyPem;
            }
            return cfg;
        }

        cfg.clusterId = json.optString("clusterId", "roboguide-field-cluster");
        cfg.nodeId = json.optString("nodeId", "");
        cfg.nodeGrpcHost = json.optString("nodeGrpcHost", "192.168.2.20");
        cfg.nodeGrpcPort = json.optInt("nodeGrpcPort", 50051);
        cfg.operatorHttpsUrl = json.optString("operatorHttpsUrl", "https://192.168.2.20:8080");
        cfg.artifactHttpsUrl = json.optString("artifactHttpsUrl", "https://192.168.2.20:8090");
        cfg.tlsServerName = json.optString("tlsServerName", "rg-controller");
        cfg.operatorTlsServerName = json.optString("operatorTlsServerName", "rg-field-controller");
        cfg.useMtls = json.optBoolean("useMtls", false);

        cfg.serverCaCertPem = json.optString("serverCaCertPem", "");
        cfg.operatorCaCertPem = json.optString("operatorCaCertPem", "");
        cfg.nodeCertPem = json.optString("nodeCertPem", "");
        cfg.operatorCertPem = json.optString("operatorCertPem", "");
        cfg.operatorRole = json.optString("operatorRole", "viewer");

        // Preserve stored keys unless explicitly cleared
        boolean clearNodeKey = json.optBoolean("clearNodeKey", false);
        String inputNodeKey = json.optString("nodeKeyPem", "");
        if (clearNodeKey) {
            cfg.nodeKeyPem = "";
        } else if (!inputNodeKey.trim().isEmpty()) {
            cfg.nodeKeyPem = inputNodeKey.trim();
        } else if (fallback != null && fallback.nodeKeyPem != null && !fallback.nodeKeyPem.trim().isEmpty()) {
            cfg.nodeKeyPem = fallback.nodeKeyPem.trim();
        }

        boolean clearOperatorKey = json.optBoolean("clearOperatorKey", false);
        String inputOpKey = json.optString("operatorKeyPem", "");
        if (clearOperatorKey) {
            cfg.operatorKeyPem = "";
        } else if (!inputOpKey.trim().isEmpty()) {
            cfg.operatorKeyPem = inputOpKey.trim();
        } else if (fallback != null && fallback.operatorKeyPem != null && !fallback.operatorKeyPem.trim().isEmpty()) {
            cfg.operatorKeyPem = fallback.operatorKeyPem.trim();
        }

        return cfg;
    }

    public JSONObject toJson(boolean includePrivateKeys) {
        // Record current instance in active vault so subsequent conversions retain credentials
        if (includePrivateKeys || (nodeKeyPem != null && !nodeKeyPem.isEmpty()) || (operatorKeyPem != null && !operatorKeyPem.isEmpty())) {
            activeVault = this;
        }

        JSONObject obj = new JSONObject();
        try {
            obj.put("clusterId", clusterId);
            obj.put("nodeId", nodeId);
            obj.put("nodeGrpcHost", nodeGrpcHost);
            obj.put("nodeGrpcPort", nodeGrpcPort);
            obj.put("operatorHttpsUrl", operatorHttpsUrl);
            obj.put("artifactHttpsUrl", artifactHttpsUrl);
            obj.put("tlsServerName", tlsServerName);
            obj.put("operatorTlsServerName", operatorTlsServerName);
            obj.put("useMtls", useMtls);

            obj.put("serverCaCertPem", serverCaCertPem);
            obj.put("operatorCaCertPem", operatorCaCertPem);
            obj.put("nodeCertPem", nodeCertPem);
            obj.put("operatorCertPem", operatorCertPem);
            obj.put("operatorRole", operatorRole);

            // Compute fingerprints for quick UI audit and server whitelist registration
            String nodeFp = "";
            if (!nodeCertPem.isEmpty()) {
                try {
                    List<X509Certificate> certs = PhoneNodeSecurityHelper.parseCertificates(nodeCertPem);
                    if (!certs.isEmpty()) {
                        nodeFp = PhoneNodeSecurityHelper.computeCertSha256Digest(certs.get(0));
                    }
                } catch (Exception ignored) {}
            }
            obj.put("nodeCertFingerprint", nodeFp);

            String operFp = "";
            if (!operatorCertPem.isEmpty()) {
                try {
                    List<X509Certificate> certs = PhoneNodeSecurityHelper.parseCertificates(operatorCertPem);
                    if (!certs.isEmpty()) {
                        operFp = PhoneNodeSecurityHelper.computeCertSha256Digest(certs.get(0));
                    }
                } catch (Exception ignored) {}
            }
            obj.put("operatorCertFingerprint", operFp);

            obj.put("hasNodeKey", nodeKeyPem != null && !nodeKeyPem.trim().isEmpty());
            obj.put("hasOperatorKey", operatorKeyPem != null && !operatorKeyPem.trim().isEmpty());

            if (includePrivateKeys) {
                obj.put("nodeKeyPem", nodeKeyPem);
                obj.put("operatorKeyPem", operatorKeyPem);
            }
        } catch (Exception ignored) {}
        return obj;
    }

    public void save(Context context) {
        activeVault = this;
        PhoneNodeSecurityHelper.saveCredentialsToSecureStorage(context, toJson(true));
    }

    public static PhoneNodeConfig load(Context context) {
        JSONObject json = PhoneNodeSecurityHelper.loadCredentialsFromSecureStorage(context);
        PhoneNodeConfig loaded = fromJson(json, null);
        if (loaded.nodeKeyPem != null && !loaded.nodeKeyPem.isEmpty() ||
            loaded.operatorKeyPem != null && !loaded.operatorKeyPem.isEmpty()) {
            activeVault = loaded;
        }
        return loaded;
    }
}
