package com.syswonder.roboguide.hub;

import android.Manifest;
import android.content.pm.PackageManager;
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

import java.util.UUID;

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
        String host = call.getString("host", "127.0.0.1");
        int port = call.getInt("port", 50051);
        manager.connect(host, port);

        JSObject ret = new JSObject();
        ret.put("status", "connecting");
        ret.put("endpoint", host + ":" + port);
        call.resolve(ret);
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
