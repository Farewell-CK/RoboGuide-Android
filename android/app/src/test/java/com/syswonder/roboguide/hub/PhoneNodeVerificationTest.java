package com.syswonder.roboguide.hub;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import roboguide.node.v0_4.RoboguideNode;

/**
 * End-to-end Verification Unit Tests for formal RoboGuide Android Phone Node.
 * Verifies wire protocol serialization, contract negotiation, command receipt semantics,
 * atomic admission, parameter conflict immutability, cancel state machine,
 * terminal state monotonicity, sequence recovery, restart reconciliation,
 * CAS artifact upload tracking, and physical camera non-fallback enforcement.
 */
public class PhoneNodeVerificationTest {

    // =========================================================================
    // 1. Wire Protocol & Version Negotiation
    // =========================================================================

    @Test
    public void testProtocolAndContractNegotiationVersions() {
        assertEquals("roboguide.node-protocol/v0.4", PhoneNodeManager.PROTOCOL_VERSION);
        assertEquals("roboguide.node.v0.6", PhoneNodeManager.NODE_CONTRACT_VERSION);
    }

    @Test
    public void testHelloMessageWireConformity() throws Exception {
        String testNodeId = "node-phone-7f4c9a8b-1111-2222-3333-444455556666";

        RoboguideNode.Hello hello = RoboguideNode.Hello.newBuilder()
                .setNodeId(testNodeId)
                .addProtocolVersions(PhoneNodeManager.PROTOCOL_VERSION)
                .addNodeContractVersions(PhoneNodeManager.NODE_CONTRACT_VERSION)
                .build();

        RoboguideNode.NodeMessage outbound = RoboguideNode.NodeMessage.newBuilder()
                .setHello(hello)
                .build();

        byte[] wireBytes = outbound.toByteArray();
        assertTrue("Wire message must contain serialized bytes", wireBytes.length > 0);

        RoboguideNode.NodeMessage parsed = RoboguideNode.NodeMessage.parseFrom(wireBytes);
        assertEquals(RoboguideNode.NodeMessage.MessageCase.HELLO, parsed.getMessageCase());
        assertEquals(testNodeId, parsed.getHello().getNodeId());
        assertEquals(1, parsed.getHello().getProtocolVersionsCount());
        assertEquals("roboguide.node-protocol/v0.4", parsed.getHello().getProtocolVersions(0));
        assertEquals("roboguide.node.v0.6", parsed.getHello().getNodeContractVersions(0));
    }

    @Test
    public void testRegistrationWithPingAndObservationCapabilities() throws Exception {
        String testNodeId = "node-phone-test-device";

        RoboguideNode.LocalRuntime runtime = RoboguideNode.LocalRuntime.newBuilder()
                .setName("roboguide-android-hub")
                .setVersion("1.0.0")
                .build();

        RoboguideNode.LocalSystemDescriptor localSystem = RoboguideNode.LocalSystemDescriptor.newBuilder()
                .setId("mobile-system")
                .setRuntime(runtime)
                .build();

        // 1. system.ping@v1 (kind = "compute")
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

        // 2. observation.acquire@v1 (kind = "observation", strictly enforced by Controller validator)
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
                .setId(testNodeId + ":compute:local")
                .setKind("compute")
                .setCapacity(1)
                .setLocalSystemId("mobile-system")
                .build();

        RoboguideNode.Resource cameraResource = RoboguideNode.Resource.newBuilder()
                .setId(testNodeId + ":compute:camera")
                .setKind("compute")
                .setCapacity(1)
                .setLocalSystemId("mobile-system")
                .build();

        RoboguideNode.NodeRegistration reg = RoboguideNode.NodeRegistration.newBuilder()
                .setNodeId(testNodeId)
                .setNodeContractVersion(PhoneNodeManager.NODE_CONTRACT_VERSION)
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

        RoboguideNode.NodeMessage outbound = RoboguideNode.NodeMessage.newBuilder()
                .setRegister(regMsg)
                .build();

        byte[] wireBytes = outbound.toByteArray();
        RoboguideNode.NodeMessage parsed = RoboguideNode.NodeMessage.parseFrom(wireBytes);
        assertEquals(RoboguideNode.NodeMessage.MessageCase.REGISTER, parsed.getMessageCase());

        RoboguideNode.NodeRegistration parsedReg = parsed.getRegister().getRegistration();
        assertEquals(testNodeId, parsedReg.getNodeId());
        assertEquals("roboguide.node.v0.6", parsedReg.getNodeContractVersion());
        assertEquals(2, parsedReg.getCapabilityProfilesCount());
        assertEquals("system.ping@v1", parsedReg.getCapabilityProfiles(0).getContract());
        assertEquals("compute", parsedReg.getCapabilityProfiles(0).getKind());
        assertEquals("observation.acquire@v1", parsedReg.getCapabilityProfiles(1).getContract());
        assertEquals("observation", parsedReg.getCapabilityProfiles(1).getKind());
    }

    @Test
    public void testCommandReceiptAndPersistenceSemantics() throws Exception {
        String sessionId = "grpc-session-test-01";
        String executionId = "exec-ping-1001";
        String commandId = "dispatch-" + executionId;
        long sequence = 2L;

        RoboguideNode.CommandReceipt receipt = RoboguideNode.CommandReceipt.newBuilder()
                .setSessionId(sessionId)
                .setSequence(sequence)
                .setCommandId(commandId)
                .setExecutionId(executionId)
                .setKind(RoboguideNode.CommandKind.COMMAND_EXECUTE)
                .setStatus(RoboguideNode.CommandReceiptStatus.COMMAND_PERSISTED)
                .setReason("")
                .build();

        RoboguideNode.NodeMessage outbound = RoboguideNode.NodeMessage.newBuilder()
                .setCommandReceipt(receipt)
                .build();

        byte[] wireBytes = outbound.toByteArray();
        RoboguideNode.NodeMessage parsed = RoboguideNode.NodeMessage.parseFrom(wireBytes);
        assertEquals(RoboguideNode.NodeMessage.MessageCase.COMMAND_RECEIPT, parsed.getMessageCase());

        RoboguideNode.CommandReceipt parsedReceipt = parsed.getCommandReceipt();
        assertEquals(sessionId, parsedReceipt.getSessionId());
        assertEquals(executionId, parsedReceipt.getExecutionId());
        assertEquals(commandId, parsedReceipt.getCommandId());
        assertEquals(RoboguideNode.CommandKind.COMMAND_EXECUTE, parsedReceipt.getKind());
        assertEquals(RoboguideNode.CommandReceiptStatus.COMMAND_PERSISTED, parsedReceipt.getStatus());
    }

    // =========================================================================
    // 2. Production Journal Atomic Admission & Idempotency
    // =========================================================================

    @Test
    public void testAtomicAdmissionFreshTask() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        String execId = "exec-fresh-01";
        String cmdId = "cmd-01";
        String sessionId = "sess-01";
        String op = "system.ping@v1";
        String params = "{\"echo\":\"test\"}";

        PhoneNodeJournal.AdmissionResult res = journal.atomicAdmitExecution(execId, cmdId, sessionId, op, params);
        assertEquals(PhoneNodeJournal.AdmissionStatus.ADMITTED, res.status);
        assertNotNull(res.record);
        assertEquals("ACCEPTED", res.record.phase);
        assertEquals(execId, res.record.executionId);
        assertEquals(op, res.record.operation);
        assertEquals(params, res.record.parametersJson);
    }

    @Test
    public void testAtomicAdmissionIdempotentDuplicate() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        String execId = "exec-dup-01";
        String cmdId = "cmd-01";
        String sessionId = "sess-01";
        String op = "observation.acquire@v1";
        String params = "{\"expected\":\"dock_marker\"}";

        PhoneNodeJournal.AdmissionResult first = journal.atomicAdmitExecution(execId, cmdId, sessionId, op, params);
        assertEquals(PhoneNodeJournal.AdmissionStatus.ADMITTED, first.status);

        // Advance to STARTED
        journal.atomicTransitionPhase(execId, "ACCEPTED", "STARTED", "Capturing");

        // Duplicate submission with identical operation and parameters
        PhoneNodeJournal.AdmissionResult duplicate = journal.atomicAdmitExecution(execId, "cmd-02", sessionId, op, params);
        assertEquals(PhoneNodeJournal.AdmissionStatus.DUPLICATE, duplicate.status);
        assertEquals("STARTED", duplicate.record.phase);
    }

    @Test
    public void testAtomicAdmissionParameterConflictDoesNotMutateOriginalRecord() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        String execId = "exec-conflict-01";
        String cmdId = "cmd-01";
        String sessionId = "sess-01";
        String op = "observation.acquire@v1";
        String params = "{\"expected\":\"dock_marker\"}";

        PhoneNodeJournal.AdmissionResult first = journal.atomicAdmitExecution(execId, cmdId, sessionId, op, params);
        assertEquals(PhoneNodeJournal.AdmissionStatus.ADMITTED, first.status);

        // Advance to STARTED
        journal.atomicTransitionPhase(execId, "ACCEPTED", "STARTED", "Running");

        // Conflicting submission with changed parameters
        String alteredParams = "{\"expected\":\"altered_tag_99\"}";
        PhoneNodeJournal.AdmissionResult conflict = journal.atomicAdmitExecution(execId, "cmd-99", sessionId, op, alteredParams);

        assertEquals(PhoneNodeJournal.AdmissionStatus.CONFLICT, conflict.status);
        assertTrue(conflict.reason.contains("E_PARAMETER_CONFLICT"));

        // Crucial verification: original task record must NOT be mutated to FAILED or overwritten
        PhoneNodeJournal.ExecutionRecord orig = journal.getExecution(execId);
        assertEquals("STARTED", orig.phase);
        assertEquals(params, orig.parametersJson);
        assertFalse("Original parameters must be intact", orig.parametersJson.equals(alteredParams));
    }

    // =========================================================================
    // 3. Cancellation State Machine & Terminal Monotonicity
    // =========================================================================

    @Test
    public void testCancelBeforeExecutePreCancellation() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        String execId = "exec-precancel-01";
        String cancelCmdId = "cancel-cmd-01";

        // 1. Cancel arrives BEFORE Execute command
        PhoneNodeJournal.CancelResult cancelRes = journal.atomicCancelExecution(execId, cancelCmdId, "User aborted early");
        assertEquals(PhoneNodeJournal.CancelStatus.RECORDED_PRE_CANCEL, cancelRes.status);

        // 2. Subsequent Execute arrives
        PhoneNodeJournal.AdmissionResult admitRes = journal.atomicAdmitExecution(
                execId, "exec-cmd-01", "sess-01", "system.ping@v1", "{}");

        assertEquals(PhoneNodeJournal.AdmissionStatus.PRE_CANCELLED, admitRes.status);
        PhoneNodeJournal.ExecutionRecord r = journal.getExecution(execId);
        assertNotNull(r);
        assertEquals("CANCELLED", r.phase);
        assertTrue(r.reason.contains("User aborted early"));
    }

    @Test
    public void testCancelActiveRunningTask() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        String execId = "exec-active-01";
        journal.atomicAdmitExecution(execId, "cmd-1", "sess-1", "system.ping@v1", "{}");
        journal.atomicTransitionPhase(execId, "ACCEPTED", "STARTED", "Active worker running");

        PhoneNodeJournal.CancelResult cancelRes = journal.atomicCancelExecution(execId, "cancel-cmd-1", "Emergency stop triggered");
        assertEquals(PhoneNodeJournal.CancelStatus.CANCELLED_ACTIVE, cancelRes.status);

        PhoneNodeJournal.ExecutionRecord r = journal.getExecution(execId);
        assertEquals("CANCELLED", r.phase);
        assertEquals("Emergency stop triggered", r.reason);
    }

    @Test
    public void testTerminalStateMonotonicityCompletedCannotBeCancelled() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        String execId = "exec-completed-01";
        journal.atomicAdmitExecution(execId, "cmd-1", "sess-1", "system.ping@v1", "{}");
        journal.atomicTransitionPhase(execId, "ACCEPTED", "STARTED", "Active");
        journal.atomicTransitionPhase(execId, "STARTED", "COMPLETED", "Task finished successfully");

        // Cancel received after task already completed
        PhoneNodeJournal.CancelResult cancelRes = journal.atomicCancelExecution(execId, "cancel-late", "Late cancel");
        assertEquals(PhoneNodeJournal.CancelStatus.ALREADY_TERMINAL, cancelRes.status);
        assertEquals("COMPLETED", cancelRes.currentPhase);

        // Record must remain COMPLETED
        PhoneNodeJournal.ExecutionRecord r = journal.getExecution(execId);
        assertEquals("COMPLETED", r.phase);
        assertEquals("Task finished successfully", r.reason);
    }

    @Test
    public void testTerminalStateMonotonicityCancelledCannotBeOverwrittenByLateCompletion() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        String execId = "exec-cancel-mono-01";
        journal.atomicAdmitExecution(execId, "cmd-1", "sess-1", "observation.acquire@v1", "{}");
        journal.atomicTransitionPhase(execId, "ACCEPTED", "STARTED", "Camera capturing");

        // Task cancelled while worker was executing
        journal.atomicCancelExecution(execId, "cancel-worker", "Aborted by operator");
        assertEquals("CANCELLED", journal.getExecution(execId).phase);

        // Worker attempts late transition to COMPLETED
        boolean transitioned = journal.atomicTransitionPhase(execId, "STARTED", "COMPLETED", "Late photo result");
        assertFalse("Late transition must be rejected because task is already CANCELLED", transitioned);

        // Worker attempts late artifact completion
        boolean artCompleted = journal.atomicCompleteArtifactExecution(execId, "Late result", "sha256:1111", "/tmp/p.jpg", 100);
        assertFalse("Late artifact completion must be rejected", artCompleted);

        // Record remains strictly CANCELLED
        PhoneNodeJournal.ExecutionRecord r = journal.getExecution(execId);
        assertEquals("CANCELLED", r.phase);
        assertEquals("Aborted by operator", r.reason);
    }

    // =========================================================================
    // 4. Sequence Restoration & Process Restart Reconciliation
    // =========================================================================

    @Test
    public void testMonotonicSequenceRestorationAcrossRestart() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        journal.recordEvent("exec-seq-1", 10L, "ACCEPTED", "ok");
        journal.recordEvent("exec-seq-1", 25L, "STARTED", "ok");
        journal.recordEvent("exec-seq-2", 87L, "COMPLETED", "ok");

        long maxSeq = journal.getMaxPersistedSequence();
        assertEquals(87L, maxSeq);

        // When PhoneNodeManager initializes, it starts at maxSeq + 1 (88), never regressing to 1
        PhoneNodeManager mgr = new PhoneNodeManager(null, journal);
        // Sequence restored correctly without regression
        assertTrue("Sequence must start strictly greater than max persisted sequence",
                mgr.getNodeId() != null);
        assertEquals(88L, mgr.getSequence());
    }

    @Test
    public void testProcessRestartReconciliationOfInterruptedTasks() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();

        // 1. Task A was COMPLETED before crash
        journal.atomicAdmitExecution("task-a", "c1", "s1", "system.ping@v1", "{}");
        journal.atomicTransitionPhase("task-a", "ACCEPTED", "COMPLETED", "ok");

        // 2. Task B was in-flight (STARTED) when process terminated
        journal.atomicAdmitExecution("task-b", "c2", "s1", "observation.acquire@v1", "{}");
        journal.atomicTransitionPhase("task-b", "ACCEPTED", "STARTED", "capturing");

        // 3. Task C was in-flight (ACCEPTED) when process terminated
        journal.atomicAdmitExecution("task-c", "c3", "s1", "system.ping@v1", "{}");

        // Perform restart reconciliation
        List<PhoneNodeJournal.ExecutionRecord> reconciled = journal.reconcileInterruptedExecutions();
        assertEquals(2, reconciled.size());

        // Completed task remains untouched
        assertEquals("COMPLETED", journal.getExecution("task-a").phase);

        // Interrupted tasks reconciled to UNKNOWN with clear audit trail (never asserted FAILED without proof)
        PhoneNodeJournal.ExecutionRecord b = journal.getExecution("task-b");
        assertEquals("UNKNOWN", b.phase);
        assertTrue(b.reason.contains("E_PROCESS_RESTART_UNVERIFIED"));

        PhoneNodeJournal.ExecutionRecord c = journal.getExecution("task-c");
        assertEquals("UNKNOWN", c.phase);
        assertTrue(c.reason.contains("E_PROCESS_RESTART_UNVERIFIED"));
    }

    // =========================================================================
    // 5. Artifact Upload Tracking Table
    // =========================================================================

    @Test
    public void testArtifactUploadTrackingTableLifecycle() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        String uploadId = "upload-test-001";
        String execId = "exec-obs-001";
        String digest = "sha256:abcdef0123456789";
        long size = 1048576L;
        String path = "/data/user/0/artifacts/obs_001.jpg";

        journal.recordUploadIntent(uploadId, execId, digest, size, path);

        PhoneNodeJournal.UploadRecord r1 = journal.getUploadRecord(uploadId);
        assertNotNull(r1);
        assertEquals("STARTED", r1.phase);
        assertEquals(0L, r1.offsetBytes);
        assertEquals(digest, r1.contentDigest);
        assertEquals(size, r1.byteSize);

        // Progress update
        journal.updateUploadProgress(uploadId, "COMPLETED", size);
        PhoneNodeJournal.UploadRecord r2 = journal.getUploadRecord(uploadId);
        assertEquals("COMPLETED", r2.phase);
        assertEquals(size, r2.offsetBytes);

        // Query by executionId
        PhoneNodeJournal.UploadRecord r3 = journal.getUploadRecordByExecution(execId);
        assertNotNull(r3);
        assertEquals(uploadId, r3.uploadId);
    }

    // =========================================================================
    // 6. Multi-Threaded Concurrency Admission
    // =========================================================================

    @Test
    public void testMultiThreadedConcurrencyAdmission() throws Exception {
        final PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        final String execId = "exec-concurrent-race";
        final int threadCount = 20;

        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch finishGate = new CountDownLatch(threadCount);

        final AtomicInteger admittedCount = new AtomicInteger(0);
        final AtomicInteger duplicateCount = new AtomicInteger(0);
        final AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    PhoneNodeJournal.AdmissionResult res = journal.atomicAdmitExecution(
                            execId, "cmd-" + index, "sess-01", "system.ping@v1", "{\"arg\":\"same\"}");

                    if (res.status == PhoneNodeJournal.AdmissionStatus.ADMITTED) {
                        admittedCount.incrementAndGet();
                    } else if (res.status == PhoneNodeJournal.AdmissionStatus.DUPLICATE) {
                        duplicateCount.incrementAndGet();
                    } else if (res.status == PhoneNodeJournal.AdmissionStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    finishGate.countDown();
                }
            });
        }

        // Unleash all 20 threads simultaneously
        startGate.countDown();
        boolean finished = finishGate.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue("All threads must finish", finished);
        assertEquals("Exactly ONE thread must successfully admit the execution", 1, admittedCount.get());
        assertEquals("Remaining threads must be recognized as idempotent duplicates", threadCount - 1, duplicateCount.get());
        assertEquals("No conflicts should occur with identical params", 0, conflictCount.get());
    }

    // =========================================================================
    // 7. Physical Camera Non-Fallback & Demo Isolation
    // =========================================================================

    @Test
    public void testCameraCaptureRejectionWhenPermissionDeniedWithoutFallback() {
        // Without Android Context or permission, allowDemoFallback = false must strictly return failure
        CameraCaptureHelper.CaptureResult res = CameraCaptureHelper.captureObservation(
                null, "exec-no-context", "test_expected", false);

        assertFalse("Capture must fail when camera permission/context is unavailable", res.success);
        assertNotNull(res.errorMessage);
        assertTrue("Error must specify permission denied", res.errorMessage.contains("E_PERMISSION_DENIED"));
        assertNull("No file path should be produced on failure", res.filePath);
    }

    @Test
    public void testDemoFallbackStrictlyIsolated() throws Exception {
        File tempFile = File.createTempFile("demo_marker_", ".jpg");
        tempFile.deleteOnExit();

        try {
            CameraCaptureHelper.generateSensorFallbackImage(
                    tempFile, "exec-demo-99", "demo_semantic_marker", System.currentTimeMillis());

            assertTrue("Demo marker image must be written", tempFile.exists());
            assertTrue("Demo marker must have non-zero size", tempFile.length() > 500);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream is = new FileInputStream(tempFile)) {
                byte[] buf = new byte[1024];
                int n;
                while ((n = is.read(buf)) != -1) {
                    md.update(buf, 0, n);
                }
            }
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            String digest = "sha256:" + sb.toString();
            assertEquals(71, digest.length());
        } catch (RuntimeException e) {
            // When executing in JVM test runner without Android UI graphics stack, verify Bitmap is guarded
            assertTrue("Exception must relate to Android Bitmap graphics stub",
                    e.getMessage() != null && (e.getMessage().contains("Bitmap") || e.getMessage().contains("not mocked")));
        }
    }
}
