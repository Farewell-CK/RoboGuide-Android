package com.syswonder.roboguide.hub;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import io.grpc.ManagedChannel;
import io.grpc.okhttp.OkHttpChannelBuilder;
import io.grpc.stub.StreamObserver;
import roboguide.node.v0_4.RoboguideNode;

/**
 * Formal RoboGuide Node Session Manager for Android Phone.
 * Implements bidirectional gRPC streaming protocol (roboguide.node-protocol/v0.4),
 * Hello -> Welcome -> Register -> Registered -> Heartbeat lifecycle,
 * Command receipt persistence, idempotency verification, system.ping and observation.acquire tasks,
 * Session restart/reconnection snapshot reconciliation, and Controller CAS artifact upload.
 */
public class PhoneNodeManager {
    private static final String TAG = "PhoneNodeManager";

    public static final String PROTOCOL_VERSION = "roboguide.node-protocol/v0.4";
    public static final String NODE_CONTRACT_VERSION = "roboguide.node.v0.6";

    public enum NodeState {
        DISCONNECTED,
        CONNECTING,
        HELLO_SENT,
        REGISTERING,
        REGISTERED,
        STREAMING,
        ERROR
    }

    public interface StatusListener {
        void onStatusChanged(NodeState state, String detail);
        void onExecutionEvent(String executionId, String phase, String reason);
    }

    private static PhoneNodeManager instance;

    public static synchronized PhoneNodeManager getInstance(Context context) {
        if (instance == null) {
            instance = new PhoneNodeManager(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final PhoneNodeJournal journal;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, Future<?>> activeExecutionTasks = new ConcurrentHashMap<>();

    private ManagedChannel channel;
    private StreamObserver<RoboguideNode.NodeMessage> requestObserver;
    private ScheduledFuture<?> heartbeatFuture;
    private ScheduledFuture<?> reconnectFuture;

    private String targetHost = "10.0.2.2";
    private int targetPort = 50051;
    private int artifactPort = 8090;
    private boolean autoReconnect = true;
    private int reconnectBackoffSeconds = 2;

    private NodeState currentState = NodeState.DISCONNECTED;
    private String currentDetail = "Not connected";
    private String nodeId;
    private String sessionId = "";
    private String leaseId = "";
    private long heartbeatIntervalMs = 5000;
    private long leaseDurationMs = 15000;
    private long lastHeartbeatAckMs = 0;
    private long heartbeatCount = 0;
    private final AtomicLong sequenceGenerator = new AtomicLong(1);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    private StatusListener statusListener;

    private PhoneNodeManager(Context context) {
        this.context = context;
        this.journal = PhoneNodeJournal.getInstance(context);
        this.nodeId = journal.getOrGenerateNodeId();
        Log.i(TAG, "Initialized PhoneNodeManager with Node ID: " + nodeId);
    }

    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }

    public synchronized String getNodeId() {
        return nodeId;
    }

    public synchronized String getSessionId() {
        return sessionId;
    }

    public synchronized String getLeaseId() {
        return leaseId;
    }

    public synchronized NodeState getState() {
        return currentState;
    }

    public synchronized String getDetail() {
        return currentDetail;
    }

    public synchronized long getHeartbeatCount() {
        return heartbeatCount;
    }

    public synchronized long getLastHeartbeatAckMs() {
        return lastHeartbeatAckMs;
    }

    public synchronized String getTargetEndpoint() {
        return targetHost + ":" + targetPort;
    }

    public synchronized void connect(String host, int port) {
        this.targetHost = host;
        this.targetPort = port;
        this.autoReconnect = true;
        this.reconnectBackoffSeconds = 2;
        startSession();
    }

    public synchronized void disconnect() {
        this.autoReconnect = false;
        closeSession("Disconnected by user");
    }

    private synchronized void updateState(NodeState newState, String detail) {
        this.currentState = newState;
        this.currentDetail = detail;
        Log.i(TAG, "State -> " + newState + " (" + detail + ")");
        if (statusListener != null) {
            mainHandler.post(() -> statusListener.onStatusChanged(newState, detail));
        }
    }

    private synchronized void startSession() {
        if (isConnecting.get() || currentState == NodeState.STREAMING || currentState == NodeState.REGISTERED) {
            return;
        }
        isConnecting.set(true);
        updateState(NodeState.CONNECTING, "Connecting to " + targetHost + ":" + targetPort);

        try {
            if (heartbeatFuture != null) {
                heartbeatFuture.cancel(true);
                heartbeatFuture = null;
            }

            if (channel != null && !channel.isShutdown()) {
                channel.shutdownNow();
            }

            channel = OkHttpChannelBuilder.forAddress(targetHost, targetPort)
                    .usePlaintext()
                    .keepAliveTime(10, TimeUnit.SECONDS)
                    .keepAliveTimeout(5, TimeUnit.SECONDS)
                    .build();

            RoboGuideNodeProtocolGrpc.RoboGuideNodeProtocolStub stub = RoboGuideNodeProtocolGrpc.newStub(channel);

            requestObserver = stub.nodeSession(new StreamObserver<RoboguideNode.ServerMessage>() {
                @Override
                public void onNext(RoboguideNode.ServerMessage serverMessage) {
                    handleServerMessage(serverMessage);
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "NodeSession stream error: " + t.getMessage(), t);
                    handleStreamClosed("Stream error: " + t.getMessage());
                }

                @Override
                public void onCompleted() {
                    Log.i(TAG, "NodeSession stream completed by server");
                    handleStreamClosed("Stream closed by server");
                }
            });

            isConnecting.set(false);
            sendHello();

        } catch (Exception e) {
            isConnecting.set(false);
            Log.e(TAG, "Failed to start NodeSession", e);
            handleStreamClosed("Connection initialization failed: " + e.getMessage());
        }
    }

    private synchronized void closeSession(String reason) {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
            heartbeatFuture = null;
        }
        if (requestObserver != null) {
            try {
                requestObserver.onCompleted();
            } catch (Exception ignored) {}
            requestObserver = null;
        }
        if (channel != null && !channel.isShutdown()) {
            channel.shutdownNow();
            channel = null;
        }
        sessionId = "";
        leaseId = "";
        isConnecting.set(false);
        updateState(NodeState.DISCONNECTED, reason);
    }

    private synchronized void handleStreamClosed(String reason) {
        closeSession(reason);
        if (autoReconnect) {
            int delay = reconnectBackoffSeconds;
            reconnectBackoffSeconds = Math.min(reconnectBackoffSeconds * 2, 30);
            Log.i(TAG, "Scheduling auto-reconnect in " + delay + "s");
            reconnectFuture = scheduler.schedule(this::startSession, delay, TimeUnit.SECONDS);
        }
    }

    private synchronized void sendHello() {
        if (requestObserver == null) return;
        updateState(NodeState.HELLO_SENT, "Sending Hello handshake");

        RoboguideNode.Hello hello = RoboguideNode.Hello.newBuilder()
                .setNodeId(nodeId)
                .addProtocolVersions(PROTOCOL_VERSION)
                .addNodeContractVersions(NODE_CONTRACT_VERSION)
                .build();

        RoboguideNode.NodeMessage msg = RoboguideNode.NodeMessage.newBuilder()
                .setHello(hello)
                .build();

        try {
            requestObserver.onNext(msg);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send Hello", e);
            handleStreamClosed("Failed to send Hello: " + e.getMessage());
        }
    }

    private synchronized void handleServerMessage(RoboguideNode.ServerMessage message) {
        switch (message.getMessageCase()) {
            case WELCOME:
                handleWelcome(message.getWelcome());
                break;
            case REGISTERED:
                handleRegistered(message.getRegistered());
                break;
            case EXECUTE:
                handleExecute(message.getExecute());
                break;
            case CANCEL:
                handleCancel(message.getCancel());
                break;
            case ACK:
                handleAck(message.getAck());
                break;
            case ERROR:
                handleProtocolError(message.getError());
                break;
            default:
                Log.w(TAG, "Unhandled server message case: " + message.getMessageCase());
                break;
        }
    }

    private synchronized void handleWelcome(RoboguideNode.Welcome welcome) {
        Log.i(TAG, "Received Welcome: protocol=" + welcome.getSelectedProtocolVersion() +
                ", contract=" + welcome.getSelectedNodeContractVersion() +
                ", hb=" + welcome.getHeartbeatIntervalMs() + "ms, lease=" + welcome.getLeaseDurationMs() + "ms");

        if (!PROTOCOL_VERSION.equals(welcome.getSelectedProtocolVersion()) ||
                !NODE_CONTRACT_VERSION.equals(welcome.getSelectedNodeContractVersion())) {
            Log.e(TAG, "Incompatible protocol or contract version negotiated");
            handleStreamClosed("Incompatible version: " + welcome.getSelectedProtocolVersion());
            return;
        }

        this.heartbeatIntervalMs = Math.max(welcome.getHeartbeatIntervalMs(), 1000);
        this.leaseDurationMs = welcome.getLeaseDurationMs();
        updateState(NodeState.REGISTERING, "Sending formal Node registration");

        sendRegister();
    }

    private synchronized void sendRegister() {
        if (requestObserver == null) return;

        RoboguideNode.LocalRuntime runtime = RoboguideNode.LocalRuntime.newBuilder()
                .setName("roboguide-android-hub")
                .setVersion("1.0.0")
                .build();

        RoboguideNode.LocalSystemDescriptor localSystem = RoboguideNode.LocalSystemDescriptor.newBuilder()
                .setId("mobile-system")
                .setRuntime(runtime)
                .build();

        // 1. system.ping@v1 capability & operation (kind = "compute")
        RoboguideNode.CapabilityProfile pingProfile = RoboguideNode.CapabilityProfile.newBuilder()
                .setContract("system.ping@v1")
                .setKind("compute")
                .setLocalSystemId("mobile-system")
                .setReady(true)
                .build();

        RoboguideNode.OperationRef pingOpRef = RoboguideNode.OperationRef.newBuilder()
                .setNamespace("system")
                .setName("ping")
                .setVersion("v1")
                .build();

        RoboguideNode.OperationSupport pingOp = RoboguideNode.OperationSupport.newBuilder()
                .setOperation(pingOpRef)
                .setLocalSystemId("mobile-system")
                .build();

        // 2. observation.acquire@v1 capability & operation (kind = "observation")
        RoboguideNode.CapabilityProfile obsProfile = RoboguideNode.CapabilityProfile.newBuilder()
                .setContract("observation.acquire@v1")
                .setKind("observation")
                .setLocalSystemId("mobile-system")
                .setReady(true)
                .build();

        RoboguideNode.OperationRef obsOpRef = RoboguideNode.OperationRef.newBuilder()
                .setNamespace("observation")
                .setName("acquire")
                .setVersion("v1")
                .build();

        RoboguideNode.OperationSupport obsOp = RoboguideNode.OperationSupport.newBuilder()
                .setOperation(obsOpRef)
                .setLocalSystemId("mobile-system")
                .build();

        // Resources (kind must be "space" | "compute" | "time")
        RoboguideNode.Resource pingResource = RoboguideNode.Resource.newBuilder()
                .setId(nodeId + ":compute:local")
                .setKind("compute")
                .setCapacity(1)
                .setLocalSystemId("mobile-system")
                .build();

        RoboguideNode.Resource cameraResource = RoboguideNode.Resource.newBuilder()
                .setId(nodeId + ":compute:camera")
                .setKind("compute")
                .setCapacity(1)
                .setLocalSystemId("mobile-system")
                .build();

        RoboguideNode.NodeRegistration reg = RoboguideNode.NodeRegistration.newBuilder()
                .setNodeId(nodeId)
                .setNodeContractVersion(NODE_CONTRACT_VERSION)
                .addLocalSystems(localSystem)
                .addCapabilityProfiles(pingProfile)
                .addCapabilityProfiles(obsProfile)
                .addOperationSupport(pingOp)
                .addOperationSupport(obsOp)
                .addResources(pingResource)
                .addResources(cameraResource)
                .build();

        RoboguideNode.Register regMsg = RoboguideNode.Register.newBuilder()
                .setRegistration(reg)
                .build();

        RoboguideNode.NodeMessage msg = RoboguideNode.NodeMessage.newBuilder()
                .setRegister(regMsg)
                .build();

        try {
            requestObserver.onNext(msg);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send Register", e);
            handleStreamClosed("Failed to send Register: " + e.getMessage());
        }
    }

    private synchronized void handleRegistered(RoboguideNode.Registered registered) {
        this.sessionId = registered.getSessionId();
        this.leaseId = registered.getLeaseId();
        this.reconnectBackoffSeconds = 2; // Reset backoff on successful registration
        updateState(NodeState.STREAMING, "Registered (session=" + sessionId + ")");
        Log.i(TAG, "Registration accepted: session=" + sessionId + ", lease=" + leaseId);

        // Reconcile durable state with Controller across restarts / reconnects
        replaySnapshots();

        startHeartbeatLoop();
    }

    /**
     * Replays ExecutionSnapshots from durable SQLite journal to the Controller.
     * Prevents re-execution and reconciles existing/in-progress/terminal tasks without ID churn.
     */
    private void replaySnapshots() {
        List<PhoneNodeJournal.ExecutionRecord> records = journal.getReconcilableExecutions();
        Log.i(TAG, "Replaying " + records.size() + " execution snapshots to Controller for session reconciliation");
        for (PhoneNodeJournal.ExecutionRecord r : records) {
            long lastSeq = Math.max(journal.getLatestSequence(r.executionId), 1L);
            RoboguideNode.ExecutionPhase phase = parsePhase(r.phase);
            RoboguideNode.ExecutionSnapshot snapshot = RoboguideNode.ExecutionSnapshot.newBuilder()
                    .setSessionId(sessionId)
                    .setExecutionId(r.executionId)
                    .setLastSequence(lastSeq)
                    .setPhase(phase)
                    .setReason(r.reason != null ? r.reason : "")
                    .build();

            sendNodeMessage(RoboguideNode.NodeMessage.newBuilder().setExecutionSnapshot(snapshot).build());
        }
    }

    private synchronized void startHeartbeatLoop() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
        }

        long interval = Math.min(heartbeatIntervalMs / 2, 4000);
        heartbeatFuture = scheduler.scheduleAtFixedRate(this::sendHeartbeat, 500, interval, TimeUnit.MILLISECONDS);
    }

    private synchronized void sendHeartbeat() {
        if (requestObserver == null || currentState != NodeState.STREAMING) {
            return;
        }

        long seq = sequenceGenerator.getAndIncrement();
        RoboguideNode.NodeStatus status = RoboguideNode.NodeStatus.newBuilder()
                .setHealth("online")
                .setDetail("Android Phone Node operational")
                .build();

        RoboguideNode.Heartbeat hb = RoboguideNode.Heartbeat.newBuilder()
                .setSessionId(sessionId)
                .setLeaseId(leaseId)
                .setSequence(seq)
                .setStatus(status)
                .build();

        RoboguideNode.NodeMessage msg = RoboguideNode.NodeMessage.newBuilder()
                .setHeartbeat(hb)
                .build();

        try {
            requestObserver.onNext(msg);
            heartbeatCount++;
        } catch (Exception e) {
            Log.e(TAG, "Failed to send Heartbeat seq=" + seq, e);
        }
    }

    private synchronized void handleAck(RoboguideNode.Ack ack) {
        this.lastHeartbeatAckMs = System.currentTimeMillis();
        Log.d(TAG, "Received Server Ack sequence=" + ack.getSequence());
    }

    private synchronized void handleProtocolError(RoboguideNode.ProtocolError error) {
        Log.e(TAG, "Received ProtocolError from Controller: [" + error.getCode() + "] " + error.getReason());
        updateState(NodeState.ERROR, "Controller error: " + error.getReason());
    }

    private void handleExecute(RoboguideNode.Execute execute) {
        String executionId = execute.getExecutionId();
        String commandId = execute.getCommandId();

        Log.i(TAG, "Received Execute: execution=" + executionId + ", command=" + commandId);

        // 1. Immediately acknowledge command persistence to Controller
        long receiptSeq = sequenceGenerator.getAndIncrement();
        RoboguideNode.CommandReceipt receipt = RoboguideNode.CommandReceipt.newBuilder()
                .setSessionId(sessionId)
                .setSequence(receiptSeq)
                .setCommandId(commandId)
                .setExecutionId(executionId)
                .setKind(RoboguideNode.CommandKind.COMMAND_EXECUTE)
                .setStatus(RoboguideNode.CommandReceiptStatus.COMMAND_PERSISTED)
                .setReason("")
                .build();

        sendNodeMessage(RoboguideNode.NodeMessage.newBuilder().setCommandReceipt(receipt).build());

        // 2. Extract canonical operation and parameters
        RoboguideNode.CanonicalInvocation invocation = execute.getInvocation();
        String operation = "";
        String paramsJson = "{}";

        if (invocation.hasIntent() && invocation.getIntent().hasOperation()) {
            RoboguideNode.OperationRef op = invocation.getIntent().getOperation();
            operation = op.getNamespace() + "." + op.getName() + "@" + op.getVersion();
        } else if (!invocation.getCapabilityContract().isEmpty()) {
            operation = invocation.getCapabilityContract();
        }

        try {
            JSONObject pObj = new JSONObject();
            for (Map.Entry<String, RoboguideNode.ScalarValue> entry : invocation.getParametersMap().entrySet()) {
                pObj.put(entry.getKey(), scalarToString(entry.getValue()));
            }
            if (invocation.hasIntent()) {
                for (Map.Entry<String, RoboguideNode.ScalarValue> entry : invocation.getIntent().getParametersMap().entrySet()) {
                    pObj.put(entry.getKey(), scalarToString(entry.getValue()));
                }
            }
            paramsJson = pObj.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed to serialize parameters to JSON", e);
        }

        final String finalOperation = operation;
        final String finalParamsJson = paramsJson;

        // 3. Process task in background executor and register into active task tracker
        Future<?> future = scheduler.submit(() -> {
            try {
                processExecution(executionId, commandId, finalOperation, finalParamsJson);
            } finally {
                activeExecutionTasks.remove(executionId);
            }
        });
        activeExecutionTasks.put(executionId, future);
    }

    private String scalarToString(RoboguideNode.ScalarValue val) {
        switch (val.getValueCase()) {
            case STRING_VALUE: return val.getStringValue();
            case INTEGER_VALUE: return String.valueOf(val.getIntegerValue());
            case FLOAT_VALUE: return String.valueOf(val.getFloatValue());
            case BOOL_VALUE: return String.valueOf(val.getBoolValue());
            default: return "";
        }
    }

    private void processExecution(String executionId, String commandId, String operation, String paramsJson) {
        // Check idempotency & conflict against persistent SQLite journal
        int check = journal.checkConflict(executionId, operation, paramsJson);
        if (check == -1) {
            // Conflict: same executionId with altered parameters
            String conflictReason = "E_PARAMETER_CONFLICT: Execution parameters differ from originally accepted submission";
            Log.e(TAG, "Conflict on execution=" + executionId + ": " + conflictReason);
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, conflictReason);
            return;
        }

        if (check == 1) {
            // Duplicate replay: return existing status idempotently without re-executing
            PhoneNodeJournal.ExecutionRecord existing = journal.getExecution(executionId);
            Log.i(TAG, "Idempotent duplicate Execute for " + executionId + ", current phase=" + existing.phase);
            RoboguideNode.ExecutionPhase p = parsePhase(existing.phase);
            emitExecutionPhase(executionId, p, existing.reason);
            return;
        }

        // New execution: Record ACCEPTED
        journal.recordNewExecution(executionId, commandId, sessionId, operation, paramsJson, "ACCEPTED", "Accepted by Android Phone Node");
        emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.ACCEPTED, "Accepted by Android Phone Node");

        // Record STARTED
        journal.updateExecutionPhase(executionId, "STARTED", "Running on Android");
        emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.STARTED, "Execution started");

        // Dispatch specific capability implementation
        if ("system.ping@v1".equals(operation)) {
            executePing(executionId);
        } else if ("observation.acquire@v1".equals(operation)) {
            executeObservationAcquire(executionId, paramsJson);
        } else {
            String err = "E_UNSUPPORTED_OPERATION: Operation " + operation + " is not supported by phone node";
            journal.updateExecutionPhase(executionId, "FAILED", err);
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, err);
        }
    }

    private void executePing(String executionId) {
        try {
            // Minimal compute task: echo execution evidence
            Thread.sleep(100);
            long now = System.currentTimeMillis();
            String reason = "{\"status\":\"ok\",\"operation\":\"system.ping@v1\",\"node_id\":\"" + nodeId + "\",\"timestamp_ms\":" + now + "}";
            journal.updateExecutionPhase(executionId, "COMPLETED", reason);
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.COMPLETED, reason);
            Log.i(TAG, "Ping execution completed: " + executionId);
        } catch (Exception e) {
            String err = "E_PING_ERROR: " + e.getMessage();
            journal.updateExecutionPhase(executionId, "FAILED", err);
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, err);
        }
    }

    private void executeObservationAcquire(String executionId, String paramsJson) {
        String expectedCondition = "semantic site observation";
        try {
            JSONObject obj = new JSONObject(paramsJson);
            if (obj.has("expected")) {
                expectedCondition = obj.getString("expected");
            }
        } catch (Exception ignored) {}

        CameraCaptureHelper.CaptureResult capture = CameraCaptureHelper.captureObservation(context, executionId, expectedCondition);
        if (!capture.success) {
            journal.updateExecutionPhase(executionId, "FAILED", capture.errorMessage);
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, capture.errorMessage);
            Log.w(TAG, "Observation capture failed: " + capture.errorMessage);
            return;
        }

        // Upload artifact to Controller Content-Addressable Storage (CAS)
        boolean uploaded = uploadArtifactToController(capture.filePath, capture.sha256Digest, capture.sizeBytes);

        try {
            JSONObject artJson = new JSONObject();
            artJson.put("status", "ok");
            artJson.put("operation", "observation.acquire@v1");
            JSONObject artifact = new JSONObject();
            artifact.put("digest", capture.sha256Digest);
            artifact.put("path", capture.filePath);
            artifact.put("size_bytes", capture.sizeBytes);
            artifact.put("mime_type", capture.mimeType);
            artifact.put("timestamp_ms", capture.timestampMs);
            artifact.put("uploaded_to_controller", uploaded);
            artJson.put("artifact", artifact);

            String resultReason = artJson.toString();
            journal.updateExecutionArtifact(executionId, "COMPLETED", resultReason,
                    capture.sha256Digest, capture.filePath, capture.sizeBytes);
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.COMPLETED, resultReason);
            Log.i(TAG, "Observation acquire completed: " + executionId + ", digest=" + capture.sha256Digest + ", uploaded=" + uploaded);

        } catch (Exception e) {
            String err = "E_ARTIFACT_PACKAGING: " + e.getMessage();
            journal.updateExecutionPhase(executionId, "FAILED", err);
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, err);
        }
    }

    /**
     * Uploads captured artifact bytes to the formal Controller Artifact HTTP service.
     */
    private boolean uploadArtifactToController(String filePath, String sha256Digest, long sizeBytes) {
        try {
            String uploadId = "upload-phone-" + UUID.randomUUID().toString();
            String artifactBaseUrl = "http://" + targetHost + ":" + artifactPort;

            // 1. POST /v1/artifact-uploads
            URL startUrl = new URL(artifactBaseUrl + "/v1/artifact-uploads");
            HttpURLConnection conn1 = (HttpURLConnection) startUrl.openConnection();
            conn1.setRequestMethod("POST");
            conn1.setRequestProperty("Content-Type", "application/json");
            conn1.setDoOutput(true);
            conn1.setConnectTimeout(3000);
            conn1.setReadTimeout(5000);
            byte[] startBody = ("{\"upload_id\":\"" + uploadId + "\"}").getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn1.getOutputStream()) {
                os.write(startBody);
            }
            int code1 = conn1.getResponseCode();
            conn1.disconnect();
            if (code1 != 200 && code1 != 201) {
                Log.w(TAG, "Artifact upload start failed with HTTP " + code1);
                return false;
            }

            // 2. POST /v1/artifact-uploads/{id}/content
            URL contentUrl = new URL(artifactBaseUrl + "/v1/artifact-uploads/" + uploadId + "/content");
            HttpURLConnection conn2 = (HttpURLConnection) contentUrl.openConnection();
            conn2.setRequestMethod("POST");
            conn2.setRequestProperty("Content-Type", "application/octet-stream");
            conn2.setDoOutput(true);
            conn2.setConnectTimeout(3000);
            conn2.setReadTimeout(15000);
            try (InputStream fis = new FileInputStream(filePath);
                 OutputStream os = conn2.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    os.write(buf, 0, n);
                }
            }
            int code2 = conn2.getResponseCode();
            conn2.disconnect();
            if (code2 != 200 && code2 != 202) {
                Log.w(TAG, "Artifact upload content failed with HTTP " + code2);
                return false;
            }

            // 3. POST /v1/artifact-uploads/{id}/finalize
            URL finalizeUrl = new URL(artifactBaseUrl + "/v1/artifact-uploads/" + uploadId + "/finalize");
            HttpURLConnection conn3 = (HttpURLConnection) finalizeUrl.openConnection();
            conn3.setRequestMethod("POST");
            conn3.setRequestProperty("Content-Type", "application/json");
            conn3.setDoOutput(true);
            conn3.setConnectTimeout(3000);
            conn3.setReadTimeout(5000);
            byte[] finBody = ("{\"content_digest\":\"" + sha256Digest + "\",\"byte_size\":" + sizeBytes + "}").getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn3.getOutputStream()) {
                os.write(finBody);
            }
            int code3 = conn3.getResponseCode();
            conn3.disconnect();
            if (code3 != 200 && code3 != 201) {
                Log.w(TAG, "Artifact upload finalize failed with HTTP " + code3);
                return false;
            }

            Log.i(TAG, "Artifact uploaded successfully to Controller CAS: " + sha256Digest + " (upload_id=" + uploadId + ")");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Artifact upload skipped or failed (offline / endpoint unreachable): " + e.getMessage());
            return false;
        }
    }

    private void handleCancel(RoboguideNode.Cancel cancel) {
        String executionId = cancel.getExecutionId();
        String commandId = cancel.getCommandId();
        Log.i(TAG, "Received Cancel for execution=" + executionId + ", command=" + commandId);

        // 1. Immediately acknowledge Cancel persistence to Controller
        long receiptSeq = sequenceGenerator.getAndIncrement();
        RoboguideNode.CommandReceipt receipt = RoboguideNode.CommandReceipt.newBuilder()
                .setSessionId(sessionId)
                .setSequence(receiptSeq)
                .setCommandId(commandId)
                .setExecutionId(executionId)
                .setKind(RoboguideNode.CommandKind.COMMAND_CANCEL)
                .setStatus(RoboguideNode.CommandReceiptStatus.COMMAND_PERSISTED)
                .setReason("")
                .build();
        sendNodeMessage(RoboguideNode.NodeMessage.newBuilder().setCommandReceipt(receipt).build());

        // 2. Abort active background task if running
        Future<?> activeTask = activeExecutionTasks.remove(executionId);
        if (activeTask != null) {
            activeTask.cancel(true);
        }

        // 3. Record CANCELLED in journal and emit event
        journal.updateExecutionPhase(executionId, "CANCELLED", "Execution cancelled by Controller");
        emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.CANCELLED, "Execution cancelled by Controller");
    }

    private void emitExecutionPhase(String executionId, RoboguideNode.ExecutionPhase phase, String reason) {
        long seq = sequenceGenerator.getAndIncrement();
        RoboguideNode.ExecutionEvent event = RoboguideNode.ExecutionEvent.newBuilder()
                .setSessionId(sessionId)
                .setExecutionId(executionId)
                .setSequence(seq)
                .setPhase(phase)
                .setReason(reason != null ? reason : "")
                .build();

        journal.recordEvent(executionId, seq, phase.name(), reason);
        sendNodeMessage(RoboguideNode.NodeMessage.newBuilder().setExecutionEvent(event).build());

        if (statusListener != null) {
            mainHandler.post(() -> statusListener.onExecutionEvent(executionId, phase.name(), reason));
        }
    }

    private synchronized void sendNodeMessage(RoboguideNode.NodeMessage msg) {
        if (requestObserver != null) {
            try {
                requestObserver.onNext(msg);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send NodeMessage", e);
            }
        }
    }

    private RoboguideNode.ExecutionPhase parsePhase(String name) {
        if ("ACCEPTED".equalsIgnoreCase(name)) return RoboguideNode.ExecutionPhase.ACCEPTED;
        if ("STARTED".equalsIgnoreCase(name)) return RoboguideNode.ExecutionPhase.STARTED;
        if ("COMPLETED".equalsIgnoreCase(name)) return RoboguideNode.ExecutionPhase.COMPLETED;
        if ("FAILED".equalsIgnoreCase(name)) return RoboguideNode.ExecutionPhase.FAILED;
        if ("CANCELLED".equalsIgnoreCase(name)) return RoboguideNode.ExecutionPhase.CANCELLED;
        return RoboguideNode.ExecutionPhase.EXECUTION_PHASE_UNSPECIFIED;
    }
}
