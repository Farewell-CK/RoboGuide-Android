package com.syswonder.roboguide.hub;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Host-only probes of unchanged security/configuration code; no remote requests or device operations. */
public class V07SecurityAuditTest {
    /** The production TLS configurator must not accept a peer without identity evidence. */
    @Test public void tlsServerNameMustNotDisablePeerNameVerification() throws Exception {
        PhoneNodeManager manager = new PhoneNodeManager(null, PhoneNodeJournal.createInMemoryForTesting());
        PhoneNodeConfig cfg = new PhoneNodeConfig();
        cfg.useMtls = true;
        cfg.tlsServerName = "rg-controller";
        Field config = PhoneNodeManager.class.getDeclaredField("currentConfig");
        config.setAccessible(true);
        config.set(manager, cfg);
        HttpsURLConnection conn = (HttpsURLConnection) new URL("https://unrelated.invalid/").openConnection();
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);
        PhoneNodeSecurityHelper.SslBundle bundle = new PhoneNodeSecurityHelper.SslBundle(context, context.getSocketFactory(), null);
        Method apply = PhoneNodeManager.class.getDeclaredMethod("applyTlsToConnection", HttpURLConnection.class, PhoneNodeSecurityHelper.SslBundle.class);
        apply.setAccessible(true);
        apply.invoke(manager, conn, bundle);
        boolean accepted = conn.getHostnameVerifier().verify("unrelated.invalid", null);
        System.out.println("unrelated_hostname_without_peer_evidence_accepted=" + accepted);
        assertFalse("Configured server name bypasses all HTTPS peer-name checks", accepted);
    }

    /** Reproduce the production getConfig -> Vue defaults -> saveConfig model conversion. */
    @Test public void redactedConfigSaveMustPreserveExistingPrivateKeys() throws Exception {
        PhoneNodeConfig existing = new PhoneNodeConfig();
        existing.nodeKeyPem = "AUDIT-NONSECRET-NODE-KEY-MARKER";
        existing.operatorKeyPem = "AUDIT-NONSECRET-OPERATOR-KEY-MARKER";
        JSONObject uiState = existing.toJson(false);
        uiState.put("nodeKeyPem", "");
        uiState.put("operatorKeyPem", "");
        uiState.put("operatorHttpsUrl", "https://updated.invalid:8080");
        PhoneNodeConfig saved = PhoneNodeConfig.fromJson(uiState);
        System.out.println("redacted_save_node_key_retained=" + !saved.nodeKeyPem.isEmpty()
                + " operator_key_retained=" + !saved.operatorKeyPem.isEmpty());
        assertEquals("Ordinary config save deletes the previously stored device key", existing.nodeKeyPem, saved.nodeKeyPem);
        assertEquals("Ordinary config save deletes the previously stored operator key", existing.operatorKeyPem, saved.operatorKeyPem);
    }
}
