package com.syswonder.roboguide.hub;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;

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
    private Handler mainHandler;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, Future<?>> activeExecutionTasks = new ConcurrentHashMap<>();

    private synchronized Handler getMainHandler() {
        if (mainHandler == null) {
            try {
                Looper looper = Looper.getMainLooper();
                if (looper != null) {
                    mainHandler = new Handler(looper);
                }
            } catch (Throwable ignored) {}
        }
        return mainHandler;
    }

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
    private final AtomicLong sequenceGenerator;
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);

    private StatusListener statusListener;

    public PhoneNodeManager(Context context, PhoneNodeJournal journal) {
        this.context = context;
        this.journal = journal;
        this.nodeId = journal.getOrGenerateNodeId();
        long maxSeq = journal.getMaxPersistedSequence();
        this.sequenceGenerator = new AtomicLong(Math.max(maxSeq + 1, 1L));
        Log.i(TAG, "Initialized PhoneNodeManager: nodeId=" + nodeId + ", initialSequence=" + sequenceGenerator.get());
    }

    private PhoneNodeManager(Context context) {
        this(context, PhoneNodeJournal.getInstance(context));
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

    public synchronized int getArtifactPort() {
        return artifactPort;
    }

    public synchronized void setArtifactPort(int port) {
        this.artifactPort = port;
    }

    public synchronized void connect(String host, int port) {
        connect(host, port, this.artifactPort);
    }

    public synchronized void connect(String host, int port, int artifactPort) {
        this.targetHost = host;
        this.targetPort = port;
        this.artifactPort = artifactPort;
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
            Handler h = getMainHandler();
            if (h != null) {
                h.post(() -> statusListener.onStatusChanged(newState, detail));
            } else {
                statusListener.onStatusChanged(newState, detail);
            }
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

        // 2. Dynamic camera availability check: do NOT report ready=true if permission or hardware is missing
        boolean hasCameraPermission = context != null &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean cameraHardwareAvailable = false;
        if (context != null) {
            try {
                CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                if (cm != null && cm.getCameraIdList().length > 0) {
                    cameraHardwareAvailable = true;
                }
            } catch (Exception ignored) {}
        }
        boolean cameraReady = hasCameraPermission && cameraHardwareAvailable;

        RoboguideNode.CapabilityProfile obsProfile = RoboguideNode.CapabilityProfile.newBuilder()
                .setContract("observation.acquire@v1")
                .setKind("observation")
                .setLocalSystemId("mobile-system")
                .setReady(cameraReady)
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
        this.reconnectBackoffSeconds = 2;
        updateState(NodeState.STREAMING, "Registered (session=" + sessionId + ")");
        Log.i(TAG, "Registration accepted: session=" + sessionId + ", lease=" + leaseId);

        // Reconcile durable state with Controller across restarts / reconnects
        journal.reconcileInterruptedExecutions();
        replaySnapshots();

        startHeartbeatLoop();
    }

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

        // 1. Extract canonical operation and preserve scalar typed parameters
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
                putScalarValue(pObj, entry.getKey(), entry.getValue());
            }
            if (invocation.hasIntent()) {
                for (Map.Entry<String, RoboguideNode.ScalarValue> entry : invocation.getIntent().getParametersMap().entrySet()) {
                    putScalarValue(pObj, entry.getKey(), entry.getValue());
                }
            }
            paramsJson = pObj.toString();
        } catch (Exception e) {
            Log.w(TAG, "Failed to serialize parameters to JSON", e);
        }

        final String finalOperation = operation;
        final String finalParamsJson = paramsJson;

        // 2. Perform atomic durable admission FIRST
        PhoneNodeJournal.AdmissionResult admission = journal.atomicAdmitExecution(
                executionId, commandId, sessionId, finalOperation, finalParamsJson);

        if (admission.status == PhoneNodeJournal.AdmissionStatus.CONFLICT) {
            // Reject conflict without altering the original execution record
            long rejectSeq = sequenceGenerator.getAndIncrement();
            RoboguideNode.CommandReceipt rejectReceipt = RoboguideNode.CommandReceipt.newBuilder()
                    .setSessionId(sessionId)
                    .setSequence(rejectSeq)
                    .setCommandId(commandId)
                    .setExecutionId(executionId)
                    .setKind(RoboguideNode.CommandKind.COMMAND_EXECUTE)
                    .setStatus(RoboguideNode.CommandReceiptStatus.COMMAND_REJECTED)
                    .setReason(admission.reason)
                    .build();
            sendNodeMessage(RoboguideNode.NodeMessage.newBuilder().setCommandReceipt(rejectReceipt).build());
            Log.e(TAG, "Execution rejected due to parameter conflict: " + admission.reason);
            return;
        }

        // 3. Command is durably persisted; send COMMAND_PERSISTED receipt
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

        if (admission.status == PhoneNodeJournal.AdmissionStatus.PRE_CANCELLED) {
            Log.i(TAG, "Execution " + executionId + " was pre-cancelled prior to Execute dispatch");
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.CANCELLED, admission.reason);
            return;
        }

        if (admission.status == PhoneNodeJournal.AdmissionStatus.DUPLICATE) {
            Log.i(TAG, "Idempotent duplicate Execute for " + executionId + ", re-emitting phase=" + admission.record.phase);
            RoboguideNode.ExecutionPhase p = parsePhase(admission.record.phase);
            emitExecutionPhase(executionId, p, admission.record.reason);
            return;
        }

        // 4. Freshly admitted: emit ACCEPTED
        emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.ACCEPTED, "Accepted by Android Phone Node");

        // 5. Transition to STARTED and submit worker
        boolean started = journal.atomicTransitionPhase(executionId, "ACCEPTED", "STARTED", "Running on Android");
        if (started) {
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.STARTED, "Execution started");
            Future<?> future = scheduler.submit(() -> {
                try {
                    processExecution(executionId, finalOperation, finalParamsJson);
                } finally {
                    activeExecutionTasks.remove(executionId);
                }
            });
            activeExecutionTasks.put(executionId, future);
        }
    }

    private void putScalarValue(JSONObject obj, String key, RoboguideNode.ScalarValue val) throws Exception {
        switch (val.getValueCase()) {
            case STRING_VALUE:
                obj.put(key, val.getStringValue());
                break;
            case INTEGER_VALUE:
                obj.put(key, val.getIntegerValue());
                break;
            case FLOAT_VALUE:
                obj.put(key, val.getFloatValue());
                break;
            case BOOL_VALUE:
                obj.put(key, val.getBoolValue());
                break;
            default:
                obj.put(key, JSONObject.NULL);
                break;
        }
    }

    private void processExecution(String executionId, String operation, String paramsJson) {
        if ("system.ping@v1".equals(operation)) {
            executePing(executionId);
        } else if ("observation.acquire@v1".equals(operation)) {
            executeObservationAcquire(executionId, paramsJson);
        } else {
            String err = "E_UNSUPPORTED_OPERATION: Operation " + operation + " is not supported by phone node";
            boolean failed = journal.atomicTransitionPhase(executionId, "STARTED", "FAILED", err);
            if (failed) {
                emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, err);
            }
        }
    }

    private void executePing(String executionId) {
        try {
            Thread.sleep(100);
            if (Thread.currentThread().isInterrupted()) {
                Log.w(TAG, "Ping execution interrupted: " + executionId);
                return;
            }
            long now = System.currentTimeMillis();
            String reason = "{\"status\":\"ok\",\"operation\":\"system.ping@v1\",\"node_id\":\"" + nodeId + "\",\"timestamp_ms\":" + now + "}";
            boolean completed = journal.atomicTransitionPhase(executionId, "STARTED", "COMPLETED", reason);
            if (completed) {
                emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.COMPLETED, reason);
                Log.i(TAG, "Ping execution completed: " + executionId);
            } else {
                Log.w(TAG, "Ping completion rejected (already terminal/cancelled): " + executionId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Ping execution cancelled/interrupted: " + executionId);
        } catch (Exception e) {
            String err = "E_PING_ERROR: " + e.getMessage();
            boolean failed = journal.atomicTransitionPhase(executionId, "STARTED", "FAILED", err);
            if (failed) {
                emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, err);
            }
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

        // Real observation capture: allowDemoFallback is strictly false
        CameraCaptureHelper.CaptureResult capture = CameraCaptureHelper.captureObservation(context, executionId, expectedCondition, false);
        if (!capture.success) {
            boolean failed = journal.atomicTransitionPhase(executionId, "STARTED", "FAILED", capture.errorMessage);
            if (failed) {
                emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, capture.errorMessage);
            }
            Log.w(TAG, "Observation capture failed: " + capture.errorMessage);
            return;
        }

        // Check cancellation before uploading
        if (Thread.currentThread().isInterrupted()) {
            Log.w(TAG, "Observation task interrupted before artifact upload: " + executionId);
            return;
        }

        // Upload and verify artifact with Controller CAS
        boolean uploaded = uploadAndVerifyArtifact(executionId, capture.filePath, capture.sha256Digest, capture.sizeBytes);
        if (!uploaded) {
            String uploadErr = "E_ARTIFACT_UPLOAD_FAILED: Artifact could not be uploaded or verified with Controller CAS endpoint " + targetHost + ":" + artifactPort;
            boolean failed = journal.atomicTransitionPhase(executionId, "STARTED", "FAILED", uploadErr);
            if (failed) {
                emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, uploadErr);
            }
            Log.w(TAG, "Observation upload failed: " + uploadErr);
            return;
        }

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
            artifact.put("uploaded_to_controller", true);
            artJson.put("artifact", artifact);

            String resultReason = artJson.toString();
            boolean completed = journal.atomicCompleteArtifactExecution(executionId, resultReason,
                    capture.sha256Digest, capture.filePath, capture.sizeBytes);
            if (completed) {
                emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.COMPLETED, resultReason);
                Log.i(TAG, "Observation acquire completed and verified: " + executionId + ", digest=" + capture.sha256Digest);
            } else {
                Log.w(TAG, "Observation completion rejected (terminal state monotonicity): " + executionId);
            }

        } catch (Exception e) {
            String err = "E_ARTIFACT_PACKAGING: " + e.getMessage();
            boolean failed = journal.atomicTransitionPhase(executionId, "STARTED", "FAILED", err);
            if (failed) {
                emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.FAILED, err);
            }
        }
    }

    /**
     * Uploads captured artifact bytes to the formal Controller Artifact HTTP service and verifies CAS retrieval.
     */
    private boolean uploadAndVerifyArtifact(String executionId, String filePath, String sha256Digest, long sizeBytes) {
        String uploadId = "upload-phone-" + UUID.randomUUID().toString();
        journal.recordUploadIntent(uploadId, executionId, sha256Digest, sizeBytes, filePath);

        try {
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
                journal.updateUploadProgress(uploadId, "FAILED", 0);
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
            long bytesUploaded = 0;
            try (InputStream fis = new FileInputStream(filePath);
                 OutputStream os = conn2.getOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    os.write(buf, 0, n);
                    bytesUploaded += n;
                }
            }
            int code2 = conn2.getResponseCode();
            conn2.disconnect();
            if (code2 != 200 && code2 != 202) {
                Log.w(TAG, "Artifact upload content failed with HTTP " + code2);
                journal.updateUploadProgress(uploadId, "FAILED", bytesUploaded);
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
                journal.updateUploadProgress(uploadId, "FAILED", bytesUploaded);
                return false;
            }

            // 4. Remote CAS Retrieval Verification: GET /v1/artifacts/{digest}
            URL verifyUrl = new URL(artifactBaseUrl + "/v1/artifacts/" + sha256Digest);
            HttpURLConnection connVerify = (HttpURLConnection) verifyUrl.openConnection();
            connVerify.setRequestMethod("GET");
            connVerify.setConnectTimeout(3000);
            connVerify.setReadTimeout(5000);
            int verifyCode = connVerify.getResponseCode();
            connVerify.disconnect();
            if (verifyCode != 200 && verifyCode != 204) {
                Log.w(TAG, "Remote CAS retrieval verification failed with HTTP " + verifyCode);
                journal.updateUploadProgress(uploadId, "FAILED", bytesUploaded);
                return false;
            }

            journal.updateUploadProgress(uploadId, "COMPLETED", bytesUploaded);
            Log.i(TAG, "Artifact uploaded and verified with Controller CAS: " + sha256Digest);
            return true;

        } catch (Exception e) {
            Log.w(TAG, "Artifact upload failed: " + e.getMessage(), e);
            journal.updateUploadProgress(uploadId, "FAILED", 0);
            return false;
        }
    }

    private void handleCancel(RoboguideNode.Cancel cancel) {
        String executionId = cancel.getExecutionId();
        String commandId = cancel.getCommandId();
        Log.i(TAG, "Received Cancel for execution=" + executionId + ", command=" + commandId);

        // 1. Perform atomic durable cancellation
        PhoneNodeJournal.CancelResult cancelResult = journal.atomicCancelExecution(
                executionId, commandId, "Execution cancelled by Controller");

        // 2. Acknowledge Cancel command persistence
        long receiptSeq = sequenceGenerator.getAndIncrement();
        RoboguideNode.CommandReceipt receipt = RoboguideNode.CommandReceipt.newBuilder()
                .setSessionId(sessionId)
                .setSequence(receiptSeq)
                .setCommandId(commandId)
                .setExecutionId(executionId)
                .setKind(RoboguideNode.CommandKind.COMMAND_CANCEL)
                .setStatus(RoboguideNode.CommandReceiptStatus.COMMAND_PERSISTED)
                .setReason(cancelResult.reason)
                .build();
        sendNodeMessage(RoboguideNode.NodeMessage.newBuilder().setCommandReceipt(receipt).build());

        // 3. Act on cancellation status
        if (cancelResult.status == PhoneNodeJournal.CancelStatus.CANCELLED_ACTIVE) {
            Future<?> activeTask = activeExecutionTasks.remove(executionId);
            if (activeTask != null) {
                activeTask.cancel(true);
            }
            emitExecutionPhase(executionId, RoboguideNode.ExecutionPhase.CANCELLED, "Execution cancelled by Controller");
            Log.i(TAG, "Active execution cancelled: " + executionId);
        } else if (cancelResult.status == PhoneNodeJournal.CancelStatus.RECORDED_PRE_CANCEL) {
            Log.i(TAG, "Pre-cancel recorded for execution=" + executionId);
        } else if (cancelResult.status == PhoneNodeJournal.CancelStatus.ALREADY_TERMINAL) {
            Log.i(TAG, "Cancel received for already terminal execution " + executionId + " (phase=" + cancelResult.currentPhase + "); state preserved");
        }
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
            Handler h = getMainHandler();
            if (h != null) {
                h.post(() -> statusListener.onExecutionEvent(executionId, phase.name(), reason));
            } else {
                statusListener.onExecutionEvent(executionId, phase.name(), reason);
            }
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
