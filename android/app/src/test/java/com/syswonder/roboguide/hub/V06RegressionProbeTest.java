package com.syswonder.roboguide.hub;

import io.grpc.stub.StreamObserver;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Test;
import roboguide.node.v0_4.RoboguideNode;
import static org.junit.Assert.*;

/** Independent host-only regression probes against production classes. */
public class V06RegressionProbeTest {
    /** Set a production private field without changing the production source. */
    private static void set(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    /** Invoke a production private method and propagate its result. */
    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    /** Stop host-only scheduler threads created by each test Manager. */
    private static void close(PhoneNodeManager manager) throws Exception {
        Field f = PhoneNodeManager.class.getDeclaredField("scheduler");
        f.setAccessible(true);
        ((ScheduledExecutorService) f.get(manager)).shutdownNow();
    }

    /** A same-process reconnect must not assert that a live worker crashed. */
    @Test public void reconnectMustKeepLiveExecutionStarted() throws Exception {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        journal.atomicAdmitExecution("live", "c1", "old", "observation.acquire@v1", "{}");
        journal.atomicTransitionPhase("live", "ACCEPTED", "STARTED", "worker still alive");
        PhoneNodeManager manager = new PhoneNodeManager(null, journal);
        List<RoboguideNode.NodeMessage> sent = new ArrayList<>();
        set(manager, "requestObserver", new StreamObserver<RoboguideNode.NodeMessage>() {
            public void onNext(RoboguideNode.NodeMessage value) { sent.add(value); }
            public void onError(Throwable t) { }
            public void onCompleted() { }
        });
        try {
            call(manager, "handleRegistered", new Class<?>[]{RoboguideNode.Registered.class},
                    RoboguideNode.Registered.newBuilder().setSessionId("new").setLeaseId("lease").build());
            System.out.println("reconnect stored_phase=" + journal.getExecution("live").phase
                    + " snapshot_phase=" + sent.get(0).getExecutionSnapshot().getPhase());
            assertEquals("Live execution was falsely classified as a crash", "STARTED", journal.getExecution("live").phase);
        } finally { close(manager); }
    }

    /** Extend the delivered terminal-state test with the next duplicate Execute. */
    @Test public void lateCancelThenDuplicateMustPreserveCompleted() {
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        journal.atomicAdmitExecution("done", "c1", "s1", "system.ping@v1", "{}");
        journal.atomicTransitionPhase("done", "ACCEPTED", "COMPLETED", "finished");
        assertEquals(PhoneNodeJournal.CancelStatus.ALREADY_TERMINAL,
                journal.atomicCancelExecution("done", "cancel", "late cancel").status);
        PhoneNodeJournal.AdmissionResult result = journal.atomicAdmitExecution(
                "done", "duplicate", "s1", "system.ping@v1", "{}");
        System.out.println("late_cancel duplicate_status=" + result.status
                + " stored_phase=" + journal.getExecution("done").phase);
        assertEquals("Late cancel incorrectly became a pre-cancel", PhoneNodeJournal.AdmissionStatus.DUPLICATE, result.status);
        assertEquals("COMPLETED", journal.getExecution("done").phase);
    }

    /** Exercise the actual uploader against an isolated server returning incorrect bytes. */
    @Test public void cas200WithWrongBytesMustFailVerification() throws Exception { uploadMustRejectBadRetrieval(200); }

    /** A successful empty HTTP response is not proof that an artifact can be retrieved. */
    @Test public void cas204WithoutBodyMustFailVerification() throws Exception { uploadMustRejectBadRetrieval(204); }

    /** Supply valid upload status codes but a deliberately invalid retrieval response. */
    private void uploadMustRejectBadRetrieval(int getStatus) throws Exception {
        ServerSocket server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        server.setSoTimeout(10000);
        Thread responder = new Thread(() -> {
            try {
                for (int request = 0; request < 4; request++) {
                    try (Socket socket = server.accept()) {
                        socket.setSoTimeout(5000);
                        ByteArrayOutputStream head = new ByteArrayOutputStream();
                        int b;
                        while ((b = socket.getInputStream().read()) != -1) {
                            head.write(b);
                            if (head.toString("US-ASCII").endsWith("\r\n\r\n")) break;
                            if (head.size() > 16384) throw new IllegalStateException("Oversized test request header");
                        }
                        String header = head.toString("US-ASCII");
                        int length = 0;
                        for (String line : header.split("\r\n")) {
                            if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
                        }
                        socket.getInputStream().readNBytes(length);
                        boolean get = header.startsWith("GET ");
                        int status = get ? getStatus : (header.startsWith("POST /v1/artifact-uploads HTTP") ? 201 : 200);
                        byte[] body = status == 204 ? new byte[0] : (get ? "WRONG-CONTENT" : "{}").getBytes(StandardCharsets.UTF_8);
                        socket.getOutputStream().write(("HTTP/1.1 " + status + " Audit\r\nContent-Length: " + body.length
                                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        socket.getOutputStream().write(body);
                        socket.getOutputStream().flush();
                    }
                }
            } catch (Exception error) { if (!server.isClosed()) error.printStackTrace(); }
        }, "v06-loopback-response");
        responder.setDaemon(true);
        File image = File.createTempFile("v06-audit-", ".jpg");
        byte[] expected = "EXPECTED-ARTIFACT-BYTES".getBytes(StandardCharsets.UTF_8);
        Files.write(image.toPath(), expected);
        StringBuilder digest = new StringBuilder("sha256:");
        for (byte b : MessageDigest.getInstance("SHA-256").digest(expected)) digest.append(String.format("%02x", b));
        PhoneNodeJournal journal = PhoneNodeJournal.createInMemoryForTesting();
        PhoneNodeManager manager = new PhoneNodeManager(null, journal);
        set(manager, "targetHost", "127.0.0.1");
        set(manager, "artifactPort", server.getLocalPort());
        responder.start();
        try {
            boolean verified = (boolean) call(manager, "uploadAndVerifyArtifact",
                    new Class<?>[]{String.class, String.class, String.class, long.class},
                    "upload-audit", image.getAbsolutePath(), digest.toString(), (long) expected.length);
            System.out.println("cas_get_status=" + getStatus + " invalid_body_accepted=" + verified
                    + " upload_phase=" + journal.getUploadRecordByExecution("upload-audit").phase);
            assertFalse("Uploader accepted incorrect/absent remote artifact bytes", verified);
        } finally {
            server.close();
            responder.join(1000);
            close(manager);
            Files.deleteIfExists(image.toPath());
        }
    }
}
