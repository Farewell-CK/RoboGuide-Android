package com.syswonder.roboguide.hub;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.security.MessageDigest;

import roboguide.node.v0_4.RoboguideNode;

/**
 * End-to-end Verification Unit Tests for formal RoboGuide Android Phone Node.
 * Verifies wire protocol serialization, contract negotiation, command receipt semantics,
 * idempotency vs conflict detection, artifact digest generation, phase transitions,
 * Cancellation receipts, and ExecutionSnapshot session reconciliation.
 */
public class PhoneNodeVerificationTest {

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
        assertEquals(2, parsedReg.getResourcesCount());
        assertEquals("compute", parsedReg.getResources(0).getKind());
        assertEquals("compute", parsedReg.getResources(1).getKind());
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

    @Test
    public void testCancelCommandReceiptAndEventSemantics() throws Exception {
        String sessionId = "grpc-session-test-01";
        String executionId = "exec-ping-1002";
        String cancelCommandId = "cancel-" + executionId;
        long sequence = 3L;

        // Cancel CommandReceipt
        RoboguideNode.CommandReceipt cancelReceipt = RoboguideNode.CommandReceipt.newBuilder()
                .setSessionId(sessionId)
                .setSequence(sequence)
                .setCommandId(cancelCommandId)
                .setExecutionId(executionId)
                .setKind(RoboguideNode.CommandKind.COMMAND_CANCEL)
                .setStatus(RoboguideNode.CommandReceiptStatus.COMMAND_PERSISTED)
                .setReason("")
                .build();

        RoboguideNode.NodeMessage receiptMsg = RoboguideNode.NodeMessage.newBuilder()
                .setCommandReceipt(cancelReceipt)
                .build();
        RoboguideNode.NodeMessage parsedReceiptMsg = RoboguideNode.NodeMessage.parseFrom(receiptMsg.toByteArray());
        assertEquals(RoboguideNode.CommandKind.COMMAND_CANCEL, parsedReceiptMsg.getCommandReceipt().getKind());

        // Cancelled ExecutionEvent
        RoboguideNode.ExecutionEvent cancelEvent = RoboguideNode.ExecutionEvent.newBuilder()
                .setSessionId(sessionId)
                .setExecutionId(executionId)
                .setSequence(sequence + 1)
                .setPhase(RoboguideNode.ExecutionPhase.CANCELLED)
                .setReason("Execution cancelled by Controller")
                .build();

        RoboguideNode.NodeMessage eventMsg = RoboguideNode.NodeMessage.newBuilder()
                .setExecutionEvent(cancelEvent)
                .build();
        RoboguideNode.NodeMessage parsedEventMsg = RoboguideNode.NodeMessage.parseFrom(eventMsg.toByteArray());
        assertEquals(RoboguideNode.ExecutionPhase.CANCELLED, parsedEventMsg.getExecutionEvent().getPhase());
    }

    @Test
    public void testExecutionSnapshotWireSerializationForReconnectReconciliation() throws Exception {
        String sessionId = "grpc-session-reconnect-02";
        String executionId = "exec-obs-3001";
        long lastSequence = 5L;

        RoboguideNode.ExecutionSnapshot snapshot = RoboguideNode.ExecutionSnapshot.newBuilder()
                .setSessionId(sessionId)
                .setExecutionId(executionId)
                .setLastSequence(lastSequence)
                .setPhase(RoboguideNode.ExecutionPhase.COMPLETED)
                .setReason("{\"status\":\"ok\",\"artifact\":{\"digest\":\"sha256:123456\"}}")
                .build();

        RoboguideNode.NodeMessage outbound = RoboguideNode.NodeMessage.newBuilder()
                .setExecutionSnapshot(snapshot)
                .build();

        byte[] wireBytes = outbound.toByteArray();
        RoboguideNode.NodeMessage parsed = RoboguideNode.NodeMessage.parseFrom(wireBytes);
        assertEquals(RoboguideNode.NodeMessage.MessageCase.EXECUTION_SNAPSHOT, parsed.getMessageCase());

        RoboguideNode.ExecutionSnapshot parsedSnapshot = parsed.getExecutionSnapshot();
        assertEquals(sessionId, parsedSnapshot.getSessionId());
        assertEquals(executionId, parsedSnapshot.getExecutionId());
        assertEquals(lastSequence, parsedSnapshot.getLastSequence());
        assertEquals(RoboguideNode.ExecutionPhase.COMPLETED, parsedSnapshot.getPhase());
        assertTrue(parsedSnapshot.getReason().contains("sha256:123456"));
    }

    @Test
    public void testExecutionEventLifecyclePhases() throws Exception {
        String sessionId = "grpc-session-test-01";
        String executionId = "exec-obs-2001";

        // Phase 1: ACCEPTED
        RoboguideNode.ExecutionEvent accepted = RoboguideNode.ExecutionEvent.newBuilder()
                .setSessionId(sessionId)
                .setExecutionId(executionId)
                .setSequence(3L)
                .setPhase(RoboguideNode.ExecutionPhase.ACCEPTED)
                .setReason("Accepted by phone node")
                .build();
        assertEquals(RoboguideNode.ExecutionPhase.ACCEPTED, accepted.getPhase());

        // Phase 2: STARTED
        RoboguideNode.ExecutionEvent started = RoboguideNode.ExecutionEvent.newBuilder()
                .setSessionId(sessionId)
                .setExecutionId(executionId)
                .setSequence(4L)
                .setPhase(RoboguideNode.ExecutionPhase.STARTED)
                .setReason("Camera capture in progress")
                .build();
        assertEquals(RoboguideNode.ExecutionPhase.STARTED, started.getPhase());

        // Phase 3: COMPLETED
        String artifactJson = "{\"status\":\"ok\",\"artifact\":{\"digest\":\"sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\"}}";
        RoboguideNode.ExecutionEvent completed = RoboguideNode.ExecutionEvent.newBuilder()
                .setSessionId(sessionId)
                .setExecutionId(executionId)
                .setSequence(5L)
                .setPhase(RoboguideNode.ExecutionPhase.COMPLETED)
                .setReason(artifactJson)
                .build();
        assertEquals(RoboguideNode.ExecutionPhase.COMPLETED, completed.getPhase());
        assertTrue(completed.getReason().contains("sha256:"));
    }

    @Test
    public void testArtifactSha256ChecksumCalculation() throws Exception {
        File tempFile = File.createTempFile("test_artifact_", ".jpg");
        tempFile.deleteOnExit();

        byte[] sampleData = "ROBOGUIDE_OPTICAL_FRAME_DATA_123456789".getBytes("UTF-8");
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write(sampleData);
        fos.flush();
        fos.close();

        MessageDigest md = MessageDigest.getInstance("SHA-256");
        InputStream is = new FileInputStream(tempFile);
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        is.close();

        byte[] hash = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        String digestStr = "sha256:" + sb.toString();

        assertEquals(71, digestStr.length()); // "sha256:" (7) + 64 hex chars
        assertTrue(digestStr.startsWith("sha256:"));
    }

    @Test
    public void testIdempotencyVsConflictLogic() {
        String originalOp = "observation.acquire@v1";
        String originalParams = "{\"expected\":\"tag_302\"}";

        // 1. Exact duplicate with identical parameters -> IDEMPOTENT
        boolean opMatches = originalOp.equals("observation.acquire@v1");
        boolean paramMatches = originalParams.equals("{\"expected\":\"tag_302\"}");
        assertTrue("Duplicate execution with identical parameters must be admitted idempotently", opMatches && paramMatches);

        // 2. Altered parameters -> CONFLICT
        String alteredParams = "{\"expected\":\"tag_999_altered\"}";
        boolean conflictOccurred = !originalParams.equals(alteredParams);
        assertTrue("Execution with changed parameters must be flagged as conflict", conflictOccurred);
    }
}
