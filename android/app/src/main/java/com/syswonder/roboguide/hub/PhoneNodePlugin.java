package com.syswonder.roboguide.hub;

import android.Manifest;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

@CapacitorPlugin(
    name = "PhoneNode",
    permissions = {
        @Permission(
            strings = { Manifest.permission.CAMERA },
            alias = "camera"
        )
    }
)
public class PhoneNodePlugin extends Plugin {

    private PhoneNodeManager manager;
    private PhoneNodeJournal journal;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Override
    public void load() {
        super.load();
        manager = PhoneNodeManager.getInstance(getContext());
        journal = PhoneNodeJournal.getInstance(getContext());

        manager.setStatusListener(new PhoneNodeManager.StatusListener() {
            @Override
            public void onStatusChanged(PhoneNodeManager.NodeState state, String detail) {
                JSObject data = new JSObject();
                data.put("state", state.name());
                data.put("detail", detail);
                data.put("nodeId", manager.getNodeId());
                data.put("sessionId", manager.getSessionId());
                data.put("leaseId", manager.getLeaseId());
                data.put("heartbeatCount", manager.getHeartbeatCount());
                data.put("lastHeartbeatAckMs", manager.getLastHeartbeatAckMs());
                notifyListeners("nodeStatusChanged", data);
            }

            @Override
            public void onExecutionEvent(String executionId, String phase, String reason) {
                JSObject data = new JSObject();
                data.put("executionId", executionId);
                data.put("phase", phase);
                data.put("reason", reason);
                notifyListeners("executionEvent", data);
            }
        });
    }

    @PluginMethod
    public void getNodeStatus(PluginCall call) {
        JSObject ret = new JSObject();
        boolean hasCamera = ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        PhoneNodeManager.NodeState state = manager.getState();

        ret.put("connected", state == PhoneNodeManager.NodeState.STREAMING || state == PhoneNodeManager.NodeState.REGISTERED);
        ret.put("state", state.name());
        ret.put("detail", manager.getDetail());
        ret.put("nodeId", manager.getNodeId());
        ret.put("sessionId", manager.getSessionId());
        ret.put("leaseId", manager.getLeaseId());
        ret.put("heartbeatCount", manager.getHeartbeatCount());
        ret.put("lastHeartbeatAckMs", manager.getLastHeartbeatAckMs());
        ret.put("endpoint", manager.getTargetEndpoint());
        ret.put("hasCameraPermission", hasCamera);
        call.resolve(ret);
    }

    @PluginMethod
    public void connect(PluginCall call) {
        JSObject configObj = call.getObject("config", null);
        PhoneNodeConfig cfg;
        if (configObj != null) {
            try {
                cfg = PhoneNodeConfig.fromJson(new JSONObject(configObj.toString()));
                cfg.save(getContext());
            } catch (Exception e) {
                cfg = PhoneNodeConfig.load(getContext());
            }
        } else {
            cfg = PhoneNodeConfig.load(getContext());
            if (call.hasOption("host")) {
                cfg.nodeGrpcHost = call.getString("host", "127.0.0.1");
            }
            if (call.hasOption("port")) {
                cfg.nodeGrpcPort = call.getInt("port", 50051);
            }
            if (call.hasOption("artifactPort")) {
                cfg.artifactHttpsUrl = "http://" + cfg.nodeGrpcHost + ":" + call.getInt("artifactPort", 8090);
            }
            if (call.hasOption("useMtls")) {
                cfg.useMtls = call.getBoolean("useMtls", false);
            }
        }

        manager.connectWithConfig(cfg);

        JSObject ret = new JSObject();
        ret.put("status", "connecting");
        ret.put("endpoint", cfg.nodeGrpcHost + ":" + cfg.nodeGrpcPort);
        ret.put("useMtls", cfg.useMtls);
        ret.put("nodeId", manager.getNodeId());
        call.resolve(ret);
    }

    @PluginMethod
    public void saveConfig(PluginCall call) {
        JSObject configObj = call.getObject("config", null);
        if (configObj == null) {
            call.reject("Missing config object");
            return;
        }
        try {
            JSONObject json = new JSONObject(configObj.toString());
            PhoneNodeConfig config = PhoneNodeConfig.fromJson(json);
            config.save(getContext());

            JSObject ret = new JSObject(config.toJson(false).toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to save config: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void getConfig(PluginCall call) {
        try {
            PhoneNodeConfig config = PhoneNodeConfig.load(getContext());
            JSObject ret = new JSObject(config.toJson(false).toString());
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to load config: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void generateDeviceIdentity(PluginCall call) {
        String algorithm = call.getString("algorithm", "EC"); // "EC" (secp256r1) or "RSA"
        String nodeId = call.getString("nodeId", "");
        if (nodeId == null || nodeId.trim().isEmpty()) {
            nodeId = "phone-node-" + UUID.randomUUID().toString().substring(0, 8);
        }
        try {
            KeyPair kp = PhoneNodeSecurityHelper.generateDeviceKeyPair(algorithm);
            String pubPem = PhoneNodeSecurityHelper.exportPublicKeyToPem(kp.getPublic());
            String privPem = PhoneNodeSecurityHelper.exportPrivateKeyToPem(kp.getPrivate());

            PhoneNodeConfig config = PhoneNodeConfig.load(getContext());
            config.nodeId = nodeId;
            config.nodeKeyPem = privPem;
            config.save(getContext());

            JSObject ret = new JSObject();
            ret.put("nodeId", nodeId);
            ret.put("publicKeyPem", pubPem);
            ret.put("algorithm", algorithm);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("Failed to generate device identity: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void operatorRequest(PluginCall call) {
        String method = call.getString("method", "GET").toUpperCase();
        String path = call.getString("path", "/v1/inventory");
        JSObject bodyObj = call.getObject("body", null);

        executor.submit(() -> {
            HttpURLConnection conn = null;
            try {
                PhoneNodeConfig config = PhoneNodeConfig.load(getContext());
                String base = config.operatorHttpsUrl != null && !config.operatorHttpsUrl.isEmpty()
                        ? config.operatorHttpsUrl.replaceAll("/+$", "")
                        : "http://127.0.0.1:8080";

                String fullUrl = base + (path.startsWith("/") ? path : ("/" + path));
                URL url = new URL(fullUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("Accept", "application/json");

                if (conn instanceof HttpsURLConnection && config.useMtls) {
                    PhoneNodeSecurityHelper.SslBundle bundle = PhoneNodeSecurityHelper.buildSslBundle(
                            config.serverCaCertPem,
                            config.operatorCertPem,
                            config.operatorKeyPem);
                    ((HttpsURLConnection) conn).setSSLSocketFactory(bundle.socketFactory);
                    if (config.tlsServerName != null && !config.tlsServerName.trim().isEmpty()) {
                        ((HttpsURLConnection) conn).setHostnameVerifier((hostname, session) -> true);
                    }
                }

                if (("POST".equals(method) || "PUT".equals(method)) && bodyObj != null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setDoOutput(true);
                    byte[] bytes = bodyObj.toString().getBytes(StandardCharsets.UTF_8);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bytes);
                    }
                }

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
                String respText = "";
                if (is != null) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    respText = baos.toString(StandardCharsets.UTF_8.name());
                }

                JSObject ret = new JSObject();
                ret.put("status", code);
                ret.put("rawBody", respText);
                try {
                    if (respText.trim().startsWith("{")) {
                        ret.put("data", new JSObject(respText));
                    } else if (respText.trim().startsWith("[")) {
                        ret.put("data", new JSArray(respText));
                    } else {
                        ret.put("data", respText);
                    }
                } catch (Exception ex) {
                    ret.put("data", respText);
                }
                call.resolve(ret);
            } catch (Exception e) {
                Log.e("PhoneNodePlugin", "operatorRequest failed for " + path + ": " + e.getMessage(), e);
                call.reject("Operator request error: " + e.getMessage(), e);
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        manager.disconnect();
        JSObject ret = new JSObject();
        ret.put("status", "disconnected");
        call.resolve(ret);
    }

    @PluginMethod
    public void getJournalRecords(PluginCall call) {
        int limit = call.getInt("limit", 50);
        JSONArray arr = journal.listExecutionsAsJson(limit);
        JSObject ret = new JSObject();
        try {
            ret.put("records", new JSArray(arr.toString()));
        } catch (Exception e) {
            ret.put("records", new JSArray());
        }
        call.resolve(ret);
    }

    @PluginMethod
    public void requestCameraPermission(PluginCall call) {
        if (getPermissionState("camera") != com.getcapacitor.PermissionState.GRANTED) {
            requestPermissionForAlias("camera", call, "cameraPermsCallback");
        } else {
            JSObject ret = new JSObject();
            ret.put("granted", true);
            call.resolve(ret);
        }
    }

    @PermissionCallback
    private void cameraPermsCallback(PluginCall call) {
        boolean granted = getPermissionState("camera") == com.getcapacitor.PermissionState.GRANTED;
        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }

    @PluginMethod
    public void manualCapture(PluginCall call) {
        String condition = call.getString("condition", "manual-test");
        String executionId = "manual-" + UUID.randomUUID().toString().substring(0, 8);
        CameraCaptureHelper.CaptureResult capture = CameraCaptureHelper.captureObservation(getContext(), executionId, condition);

        JSObject ret = new JSObject();
        ret.put("success", capture.success);
        if (capture.success) {
            ret.put("filePath", capture.filePath);
            ret.put("digest", capture.sha256Digest);
            ret.put("sizeBytes", capture.sizeBytes);
            ret.put("mimeType", capture.mimeType);
            ret.put("timestampMs", capture.timestampMs);
        } else {
            ret.put("error", capture.errorMessage);
        }
        call.resolve(ret);
    }
}
