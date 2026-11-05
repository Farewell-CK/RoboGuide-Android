package com.syswonder.roboguide.hub;

import android.content.Context;
import org.json.JSONObject;

import java.io.File;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Structured configuration model for RoboGuide Phone Node & Operator connection.
 * Holds endpoint coordinates, cluster identifier, and PKI mTLS credentials.
 */
public class PhoneNodeConfig {

    public String clusterId = "roboguide-field-cluster";
    public String nodeId = "";
    public String nodeGrpcHost = "192.168.2.20";
    public int nodeGrpcPort = 50051;
    public String operatorHttpsUrl = "https://192.168.2.20:8080";
    public String artifactHttpsUrl = "https://192.168.2.20:8090";
    public String tlsServerName = "";
    public boolean useMtls = false;

    // Certificates and keys (PEM encoded)
    public String serverCaCertPem = "";
    public String nodeCertPem = "";
    public String nodeKeyPem = "";
    public String operatorCertPem = "";
    public String operatorKeyPem = "";
    public String operatorRole = "viewer";

    public PhoneNodeConfig() {}

    public static PhoneNodeConfig fromJson(JSONObject json) {
        PhoneNodeConfig cfg = new PhoneNodeConfig();
        if (json == null) return cfg;

        cfg.clusterId = json.optString("clusterId", "roboguide-field-cluster");
        cfg.nodeId = json.optString("nodeId", "");
        cfg.nodeGrpcHost = json.optString("nodeGrpcHost", "192.168.2.20");
        cfg.nodeGrpcPort = json.optInt("nodeGrpcPort", 50051);
        cfg.operatorHttpsUrl = json.optString("operatorHttpsUrl", "https://192.168.2.20:8080");
        cfg.artifactHttpsUrl = json.optString("artifactHttpsUrl", "https://192.168.2.20:8090");
        cfg.tlsServerName = json.optString("tlsServerName", "");
        cfg.useMtls = json.optBoolean("useMtls", false);

        cfg.serverCaCertPem = json.optString("serverCaCertPem", "");
        cfg.nodeCertPem = json.optString("nodeCertPem", "");
        cfg.nodeKeyPem = json.optString("nodeKeyPem", "");
        cfg.operatorCertPem = json.optString("operatorCertPem", "");
        cfg.operatorKeyPem = json.optString("operatorKeyPem", "");
        cfg.operatorRole = json.optString("operatorRole", "viewer");

        return cfg;
    }

    public JSONObject toJson(boolean includePrivateKeys) {
        JSONObject obj = new JSONObject();
        try {
            obj.put("clusterId", clusterId);
            obj.put("nodeId", nodeId);
            obj.put("nodeGrpcHost", nodeGrpcHost);
            obj.put("nodeGrpcPort", nodeGrpcPort);
            obj.put("operatorHttpsUrl", operatorHttpsUrl);
            obj.put("artifactHttpsUrl", artifactHttpsUrl);
            obj.put("tlsServerName", tlsServerName);
            obj.put("useMtls", useMtls);

            obj.put("serverCaCertPem", serverCaCertPem);
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
        PhoneNodeSecurityHelper.saveCredentialsToSecureStorage(context, toJson(true));
    }

    public static PhoneNodeConfig load(Context context) {
        JSONObject json = PhoneNodeSecurityHelper.loadCredentialsFromSecureStorage(context);
        return fromJson(json);
    }
}
