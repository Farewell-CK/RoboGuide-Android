package com.syswonder.roboguide.hub;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent Journal for RoboGuide Android Phone Node.
 * Provides ACID durability, atomic admission, parameter conflict detection,
 * terminal state monotonicity, cancel intent tracking, artifact upload journaling,
 * and session restart reconciliation.
 */
public class PhoneNodeJournal {
    private static final String TAG = "PhoneNodeJournal";
    private static final String DATABASE_NAME = "phone_node_journal.db";
    private static final int DATABASE_VERSION = 2;

    public enum AdmissionStatus {
        ADMITTED,
        DUPLICATE,
        CONFLICT,
        PRE_CANCELLED
    }

    public static class AdmissionResult {
        public final AdmissionStatus status;
        public final ExecutionRecord record;
        public final String reason;

        public AdmissionResult(AdmissionStatus status, ExecutionRecord record, String reason) {
            this.status = status;
            this.record = record;
            this.reason = reason;
        }
    }

    public enum CancelStatus {
        CANCELLED_ACTIVE,
        RECORDED_PRE_CANCEL,
        ALREADY_TERMINAL
    }

    public static class CancelResult {
        public final CancelStatus status;
        public final String currentPhase;
        public final String reason;

        public CancelResult(CancelStatus status, String currentPhase, String reason) {
            this.status = status;
            this.currentPhase = currentPhase;
            this.reason = reason;
        }
    }

    public static class ExecutionRecord {
        public String executionId;
        public String commandId;
        public String sessionId;
        public String operation;
        public String parametersJson;
        public String phase;
        public String reason;
        public String artifactDigest;
        public String artifactPath;
        public long artifactSize;
        public long createdAtMs;
        public long updatedAtMs;

        public ExecutionRecord copy() {
            ExecutionRecord r = new ExecutionRecord();
            r.executionId = this.executionId;
            r.commandId = this.commandId;
            r.sessionId = this.sessionId;
            r.operation = this.operation;
            r.parametersJson = this.parametersJson;
            r.phase = this.phase;
            r.reason = this.reason;
            r.artifactDigest = this.artifactDigest;
            r.artifactPath = this.artifactPath;
            r.artifactSize = this.artifactSize;
            r.createdAtMs = this.createdAtMs;
            r.updatedAtMs = this.updatedAtMs;
            return r;
        }
    }

    public static class UploadRecord {
        public String uploadId;
        public String executionId;
        public String contentDigest;
        public long byteSize;
        public String filePath;
        public String phase;
        public long offsetBytes;
        public long createdAtMs;
        public long updatedAtMs;
    }

    private static class EventRecord {
        public String executionId;
        public long sequence;
        public String phase;
        public String reason;
        public long timestampMs;
    }

    public static boolean isTerminalPhase(String phase) {
        if (phase == null) return false;
        String p = phase.trim().toUpperCase();
        return "COMPLETED".equals(p) || "FAILED".equals(p) || "CANCELLED".equals(p);
    }

    // Storage Backend Interface
    public interface Backend {
        String getOrGenerateNodeId();
        AdmissionResult atomicAdmitExecution(String executionId, String commandId, String sessionId, String operation, String paramsJson);
        CancelResult atomicCancelExecution(String executionId, String commandId, String reason);
        boolean atomicTransitionPhase(String executionId, String expectedCurrentPhase, String newPhase, String reason);
        boolean atomicCompleteArtifactExecution(String executionId, String reason, String artifactDigest, String artifactPath, long artifactSize);
        ExecutionRecord getExecution(String executionId);
        void recordEvent(String executionId, long sequence, String phase, String reason);
        long getLatestSequence(String executionId);
        long getMaxPersistedSequence();
        List<ExecutionRecord> getReconcilableExecutions();
        List<ExecutionRecord> reconcileInterruptedExecutions();
        JSONArray listExecutionsAsJson(int limit);
        void recordUploadIntent(String uploadId, String executionId, String contentDigest, long byteSize, String filePath);
        void updateUploadProgress(String uploadId, String phase, long offsetBytes);
        UploadRecord getUploadRecord(String uploadId);
        UploadRecord getUploadRecordByExecution(String executionId);
    }

    private final Backend backend;
    private static PhoneNodeJournal instance;

    public static synchronized PhoneNodeJournal getInstance(Context context) {
        if (instance == null) {
            instance = new PhoneNodeJournal(new SqliteBackend(context.getApplicationContext()));
        }
        return instance;
    }

    public static PhoneNodeJournal createInMemoryForTesting() {
        return new PhoneNodeJournal(new InMemoryBackend());
    }

    public PhoneNodeJournal(Backend backend) {
        this.backend = backend;
    }

    public String getOrGenerateNodeId() {
        return backend.getOrGenerateNodeId();
    }

    public AdmissionResult atomicAdmitExecution(String executionId, String commandId, String sessionId, String operation, String paramsJson) {
        return backend.atomicAdmitExecution(executionId, commandId, sessionId, operation, paramsJson);
    }

    public CancelResult atomicCancelExecution(String executionId, String commandId, String reason) {
        return backend.atomicCancelExecution(executionId, commandId, reason);
    }

    public boolean atomicTransitionPhase(String executionId, String expectedCurrentPhase, String newPhase, String reason) {
        return backend.atomicTransitionPhase(executionId, expectedCurrentPhase, newPhase, reason);
    }

    public boolean atomicCompleteArtifactExecution(String executionId, String reason, String artifactDigest, String artifactPath, long artifactSize) {
        return backend.atomicCompleteArtifactExecution(executionId, reason, artifactDigest, artifactPath, artifactSize);
    }

    public ExecutionRecord getExecution(String executionId) {
        return backend.getExecution(executionId);
    }

    public void recordEvent(String executionId, long sequence, String phase, String reason) {
        backend.recordEvent(executionId, sequence, phase, reason);
    }

    public long getLatestSequence(String executionId) {
        return backend.getLatestSequence(executionId);
    }

    public long getMaxPersistedSequence() {
        return backend.getMaxPersistedSequence();
    }

    public List<ExecutionRecord> getReconcilableExecutions() {
        return backend.getReconcilableExecutions();
    }

    public List<ExecutionRecord> reconcileInterruptedExecutions() {
        return backend.reconcileInterruptedExecutions();
    }

    public JSONArray listExecutionsAsJson(int limit) {
        return backend.listExecutionsAsJson(limit);
    }

    public void recordUploadIntent(String uploadId, String executionId, String contentDigest, long byteSize, String filePath) {
        backend.recordUploadIntent(uploadId, executionId, contentDigest, byteSize, filePath);
    }

    public void updateUploadProgress(String uploadId, String phase, long offsetBytes) {
        backend.updateUploadProgress(uploadId, phase, offsetBytes);
    }

    public UploadRecord getUploadRecord(String uploadId) {
        return backend.getUploadRecord(uploadId);
    }

    public UploadRecord getUploadRecordByExecution(String executionId) {
        return backend.getUploadRecordByExecution(executionId);
    }

    // =========================================================================
    // SQLite Production Backend
    // =========================================================================
    private static class SqliteBackend extends SQLiteOpenHelper implements Backend {

        public SqliteBackend(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS node_identity (" +
                    "key TEXT PRIMARY KEY, " +
                    "value TEXT NOT NULL)");

            db.execSQL("CREATE TABLE IF NOT EXISTS executions (" +
                    "execution_id TEXT PRIMARY KEY, " +
                    "command_id TEXT, " +
                    "session_id TEXT, " +
                    "operation TEXT, " +
                    "parameters_json TEXT, " +
                    "phase TEXT, " +
                    "reason TEXT, " +
                    "artifact_digest TEXT, " +
                    "artifact_path TEXT, " +
                    "artifact_size INTEGER DEFAULT 0, " +
                    "created_at_ms INTEGER, " +
                    "updated_at_ms INTEGER)");

            db.execSQL("CREATE TABLE IF NOT EXISTS execution_events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "execution_id TEXT, " +
                    "sequence INTEGER, " +
                    "phase TEXT, " +
                    "reason TEXT, " +
                    "timestamp_ms INTEGER)");

            db.execSQL("CREATE TABLE IF NOT EXISTS cancellation_intents (" +
                    "execution_id TEXT PRIMARY KEY, " +
                    "command_id TEXT, " +
                    "reason TEXT, " +
                    "created_at_ms INTEGER)");

            db.execSQL("CREATE TABLE IF NOT EXISTS artifact_uploads (" +
                    "upload_id TEXT PRIMARY KEY, " +
                    "execution_id TEXT, " +
                    "content_digest TEXT, " +
                    "byte_size INTEGER, " +
                    "file_path TEXT, " +
                    "phase TEXT, " +
                    "offset_bytes INTEGER DEFAULT 0, " +
                    "created_at_ms INTEGER, " +
                    "updated_at_ms INTEGER)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("CREATE TABLE IF NOT EXISTS cancellation_intents (" +
                        "execution_id TEXT PRIMARY KEY, " +
                        "command_id TEXT, " +
                        "reason TEXT, " +
                        "created_at_ms INTEGER)");

                db.execSQL("CREATE TABLE IF NOT EXISTS artifact_uploads (" +
                        "upload_id TEXT PRIMARY KEY, " +
                        "execution_id TEXT, " +
                        "content_digest TEXT, " +
                        "byte_size INTEGER, " +
                        "file_path TEXT, " +
                        "phase TEXT, " +
                        "offset_bytes INTEGER DEFAULT 0, " +
                        "created_at_ms INTEGER, " +
                        "updated_at_ms INTEGER)");
            }
        }

        @Override
        public synchronized String getOrGenerateNodeId() {
            SQLiteDatabase db = getWritableDatabase();
            Cursor cursor = db.query("node_identity", new String[]{"value"},
                    "key = ?", new String[]{"node_id"}, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            } finally {
                cursor.close();
            }

            String newId = "node-phone-" + UUID.randomUUID().toString();
            ContentValues cv = new ContentValues();
            cv.put("key", "node_id");
            cv.put("value", newId);
            db.insertWithOnConflict("node_identity", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            return newId;
        }

        private ExecutionRecord getExecutionLocked(SQLiteDatabase db, String executionId) {
            Cursor cursor = db.query("executions", null,
                    "execution_id = ?", new String[]{executionId}, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    ExecutionRecord r = new ExecutionRecord();
                    r.executionId = cursor.getString(cursor.getColumnIndexOrThrow("execution_id"));
                    r.commandId = cursor.getString(cursor.getColumnIndexOrThrow("command_id"));
                    r.sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id"));
                    r.operation = cursor.getString(cursor.getColumnIndexOrThrow("operation"));
                    r.parametersJson = cursor.getString(cursor.getColumnIndexOrThrow("parameters_json"));
                    r.phase = cursor.getString(cursor.getColumnIndexOrThrow("phase"));
                    r.reason = cursor.getString(cursor.getColumnIndexOrThrow("reason"));
                    r.artifactDigest = cursor.getString(cursor.getColumnIndexOrThrow("artifact_digest"));
                    r.artifactPath = cursor.getString(cursor.getColumnIndexOrThrow("artifact_path"));
                    r.artifactSize = cursor.getLong(cursor.getColumnIndexOrThrow("artifact_size"));
                    r.createdAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("created_at_ms"));
                    r.updatedAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at_ms"));
                    return r;
                }
            } finally {
                cursor.close();
            }
            return null;
        }

        @Override
        public synchronized AdmissionResult atomicAdmitExecution(String executionId, String commandId, String sessionId, String operation, String paramsJson) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                // 1. Check if cancel intent arrived first
                Cursor cancelCursor = db.query("cancellation_intents", new String[]{"reason"},
                        "execution_id = ?", new String[]{executionId}, null, null, null);
                boolean preCancelled = false;
                String cancelReason = "Pre-emptively cancelled before execution admission";
                try {
                    if (cancelCursor.moveToFirst()) {
                        preCancelled = true;
                        String cr = cancelCursor.getString(0);
                        if (cr != null && !cr.isEmpty()) cancelReason = cr;
                    }
                } finally {
                    cancelCursor.close();
                }

                if (preCancelled) {
                    long now = System.currentTimeMillis();
                    ContentValues cv = new ContentValues();
                    cv.put("execution_id", executionId);
                    cv.put("command_id", commandId);
                    cv.put("session_id", sessionId);
                    cv.put("operation", operation);
                    cv.put("parameters_json", paramsJson);
                    cv.put("phase", "CANCELLED");
                    cv.put("reason", cancelReason);
                    cv.put("created_at_ms", now);
                    cv.put("updated_at_ms", now);
                    db.insertWithOnConflict("executions", null, cv, SQLiteDatabase.CONFLICT_IGNORE);

                    db.setTransactionSuccessful();
                    ExecutionRecord rec = getExecutionLocked(db, executionId);
                    return new AdmissionResult(AdmissionStatus.PRE_CANCELLED, rec, cancelReason);
                }

                // 2. Check if execution record already exists
                ExecutionRecord existing = getExecutionLocked(db, executionId);
                if (existing != null) {
                    boolean opMatches = (existing.operation == null && operation == null) ||
                            (existing.operation != null && existing.operation.equals(operation));
                    boolean paramMatches = (existing.parametersJson == null && paramsJson == null) ||
                            (existing.parametersJson != null && existing.parametersJson.equals(paramsJson));

                    if (opMatches && paramMatches) {
                        db.setTransactionSuccessful();
                        return new AdmissionResult(AdmissionStatus.DUPLICATE, existing, "Idempotent duplicate submission");
                    } else {
                        // Conflict: altered parameters. Do NOT mutate original record!
                        db.setTransactionSuccessful();
                        return new AdmissionResult(AdmissionStatus.CONFLICT, existing,
                                "E_PARAMETER_CONFLICT: Execution parameters differ from originally accepted submission");
                    }
                }

                // 3. Fresh task admission
                long now = System.currentTimeMillis();
                ContentValues cv = new ContentValues();
                cv.put("execution_id", executionId);
                cv.put("command_id", commandId);
                cv.put("session_id", sessionId);
                cv.put("operation", operation);
                cv.put("parameters_json", paramsJson);
                cv.put("phase", "ACCEPTED");
                cv.put("reason", "Accepted by Android Phone Node");
                cv.put("created_at_ms", now);
                cv.put("updated_at_ms", now);
                db.insertOrThrow("executions", null, cv);

                db.setTransactionSuccessful();
                ExecutionRecord rec = getExecutionLocked(db, executionId);
                return new AdmissionResult(AdmissionStatus.ADMITTED, rec, "Accepted by Android Phone Node");
            } finally {
                db.endTransaction();
            }
        }

        @Override
        public synchronized CancelResult atomicCancelExecution(String executionId, String commandId, String reason) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                long now = System.currentTimeMillis();
                // 1. Record cancel intent
                ContentValues intentCv = new ContentValues();
                intentCv.put("execution_id", executionId);
                intentCv.put("command_id", commandId);
                intentCv.put("reason", reason);
                intentCv.put("created_at_ms", now);
                db.insertWithOnConflict("cancellation_intents", null, intentCv, SQLiteDatabase.CONFLICT_REPLACE);

                // 2. Query execution
                ExecutionRecord existing = getExecutionLocked(db, executionId);
                if (existing == null) {
                    db.setTransactionSuccessful();
                    return new CancelResult(CancelStatus.RECORDED_PRE_CANCEL, null, "Cancellation intent recorded prior to execution admission");
                }

                // 3. Monotonic terminal state
                if (isTerminalPhase(existing.phase)) {
                    db.setTransactionSuccessful();
                    return new CancelResult(CancelStatus.ALREADY_TERMINAL, existing.phase,
                            "Task already in terminal phase " + existing.phase + "; terminal state preserved");
                }

                // 4. Cancel active task
                ContentValues cv = new ContentValues();
                cv.put("phase", "CANCELLED");
                cv.put("reason", reason);
                cv.put("updated_at_ms", now);
                db.update("executions", cv, "execution_id = ?", new String[]{executionId});

                db.setTransactionSuccessful();
                return new CancelResult(CancelStatus.CANCELLED_ACTIVE, "CANCELLED", reason);
            } finally {
                db.endTransaction();
            }
        }

        @Override
        public synchronized boolean atomicTransitionPhase(String executionId, String expectedCurrentPhase, String newPhase, String reason) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ExecutionRecord rec = getExecutionLocked(db, executionId);
                if (rec == null) return false;

                // Terminal state monotonicity
                if (isTerminalPhase(rec.phase)) {
                    Log.w(TAG, "Cannot transition " + executionId + " to " + newPhase + ": already in terminal phase " + rec.phase);
                    db.setTransactionSuccessful();
                    return false;
                }

                long now = System.currentTimeMillis();
                ContentValues cv = new ContentValues();
                cv.put("phase", newPhase);
                cv.put("reason", reason);
                cv.put("updated_at_ms", now);
                db.update("executions", cv, "execution_id = ?", new String[]{executionId});
                db.setTransactionSuccessful();
                return true;
            } finally {
                db.endTransaction();
            }
        }

        @Override
        public synchronized boolean atomicCompleteArtifactExecution(String executionId, String reason, String artifactDigest, String artifactPath, long artifactSize) {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                ExecutionRecord rec = getExecutionLocked(db, executionId);
                if (rec == null || isTerminalPhase(rec.phase)) {
                    Log.w(TAG, "Cannot complete artifact execution " + executionId + ": already terminal or null");
                    db.setTransactionSuccessful();
                    return false;
                }

                long now = System.currentTimeMillis();
                ContentValues cv = new ContentValues();
                cv.put("phase", "COMPLETED");
                cv.put("reason", reason);
                cv.put("artifact_digest", artifactDigest);
                cv.put("artifact_path", artifactPath);
                cv.put("artifact_size", artifactSize);
                cv.put("updated_at_ms", now);
                db.update("executions", cv, "execution_id = ?", new String[]{executionId});
                db.setTransactionSuccessful();
                return true;
            } finally {
                db.endTransaction();
            }
        }

        @Override
        public synchronized ExecutionRecord getExecution(String executionId) {
            SQLiteDatabase db = getReadableDatabase();
            return getExecutionLocked(db, executionId);
        }

        @Override
        public synchronized void recordEvent(String executionId, long sequence, String phase, String reason) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("execution_id", executionId);
            cv.put("sequence", sequence);
            cv.put("phase", phase);
            cv.put("reason", reason);
            cv.put("timestamp_ms", System.currentTimeMillis());
            db.insert("execution_events", null, cv);
        }

        @Override
        public synchronized long getLatestSequence(String executionId) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT MAX(sequence) FROM execution_events WHERE execution_id = ?",
                    new String[]{executionId});
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            } finally {
                cursor.close();
            }
            return 1L;
        }

        @Override
        public synchronized long getMaxPersistedSequence() {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.rawQuery("SELECT MAX(sequence) FROM execution_events", null);
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            } finally {
                cursor.close();
            }
            return 0L;
        }

        @Override
        public synchronized List<ExecutionRecord> getReconcilableExecutions() {
            List<ExecutionRecord> list = new ArrayList<>();
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query("executions", null, null, null, null, null, "created_at_ms DESC", "50");
            try {
                while (cursor.moveToNext()) {
                    ExecutionRecord r = new ExecutionRecord();
                    r.executionId = cursor.getString(cursor.getColumnIndexOrThrow("execution_id"));
                    r.commandId = cursor.getString(cursor.getColumnIndexOrThrow("command_id"));
                    r.sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id"));
                    r.operation = cursor.getString(cursor.getColumnIndexOrThrow("operation"));
                    r.parametersJson = cursor.getString(cursor.getColumnIndexOrThrow("parameters_json"));
                    r.phase = cursor.getString(cursor.getColumnIndexOrThrow("phase"));
                    r.reason = cursor.getString(cursor.getColumnIndexOrThrow("reason"));
                    r.artifactDigest = cursor.getString(cursor.getColumnIndexOrThrow("artifact_digest"));
                    r.artifactPath = cursor.getString(cursor.getColumnIndexOrThrow("artifact_path"));
                    r.artifactSize = cursor.getLong(cursor.getColumnIndexOrThrow("artifact_size"));
                    r.createdAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("created_at_ms"));
                    r.updatedAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at_ms"));
                    list.add(r);
                }
            } finally {
                cursor.close();
            }
            return list;
        }

        @Override
        public synchronized List<ExecutionRecord> reconcileInterruptedExecutions() {
            List<ExecutionRecord> interrupted = new ArrayList<>();
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                Cursor cursor = db.query("executions", null,
                        "phase IN ('ACCEPTED', 'STARTED')", null, null, null, null);
                try {
                    long now = System.currentTimeMillis();
                    while (cursor.moveToNext()) {
                        String id = cursor.getString(cursor.getColumnIndexOrThrow("execution_id"));
                        String err = "E_INTERRUPTED_BY_RESTART: Process restarted before task reached terminal phase; reconciliation required";
                        ContentValues cv = new ContentValues();
                        cv.put("phase", "FAILED");
                        cv.put("reason", err);
                        cv.put("updated_at_ms", now);
                        db.update("executions", cv, "execution_id = ?", new String[]{id});

                        ExecutionRecord r = getExecutionLocked(db, id);
                        if (r != null) interrupted.add(r);
                    }
                } finally {
                    cursor.close();
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return interrupted;
        }

        @Override
        public synchronized JSONArray listExecutionsAsJson(int limit) {
            JSONArray arr = new JSONArray();
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query("executions", null, null, null, null, null, "created_at_ms DESC", String.valueOf(limit));
            try {
                while (cursor.moveToNext()) {
                    JSONObject obj = new JSONObject();
                    obj.put("execution_id", cursor.getString(cursor.getColumnIndexOrThrow("execution_id")));
                    obj.put("command_id", cursor.getString(cursor.getColumnIndexOrThrow("command_id")));
                    obj.put("session_id", cursor.getString(cursor.getColumnIndexOrThrow("session_id")));
                    obj.put("operation", cursor.getString(cursor.getColumnIndexOrThrow("operation")));
                    obj.put("parameters_json", cursor.getString(cursor.getColumnIndexOrThrow("parameters_json")));
                    obj.put("phase", cursor.getString(cursor.getColumnIndexOrThrow("phase")));
                    obj.put("reason", cursor.getString(cursor.getColumnIndexOrThrow("reason")));
                    obj.put("artifact_digest", cursor.getString(cursor.getColumnIndexOrThrow("artifact_digest")));
                    obj.put("artifact_path", cursor.getString(cursor.getColumnIndexOrThrow("artifact_path")));
                    obj.put("artifact_size", cursor.getLong(cursor.getColumnIndexOrThrow("artifact_size")));
                    obj.put("created_at_ms", cursor.getLong(cursor.getColumnIndexOrThrow("created_at_ms")));
                    obj.put("updated_at_ms", cursor.getLong(cursor.getColumnIndexOrThrow("updated_at_ms")));
                    arr.put(obj);
                }
            } catch (Exception e) {
                Log.w(TAG, "Error listing executions as JSON", e);
            } finally {
                cursor.close();
            }
            return arr;
        }

        @Override
        public synchronized void recordUploadIntent(String uploadId, String executionId, String contentDigest, long byteSize, String filePath) {
            SQLiteDatabase db = getWritableDatabase();
            long now = System.currentTimeMillis();
            ContentValues cv = new ContentValues();
            cv.put("upload_id", uploadId);
            cv.put("execution_id", executionId);
            cv.put("content_digest", contentDigest);
            cv.put("byte_size", byteSize);
            cv.put("file_path", filePath);
            cv.put("phase", "STARTED");
            cv.put("offset_bytes", 0);
            cv.put("created_at_ms", now);
            cv.put("updated_at_ms", now);
            db.insertWithOnConflict("artifact_uploads", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        }

        @Override
        public synchronized void updateUploadProgress(String uploadId, String phase, long offsetBytes) {
            SQLiteDatabase db = getWritableDatabase();
            long now = System.currentTimeMillis();
            ContentValues cv = new ContentValues();
            cv.put("phase", phase);
            cv.put("offset_bytes", offsetBytes);
            cv.put("updated_at_ms", now);
            db.update("artifact_uploads", cv, "upload_id = ?", new String[]{uploadId});
        }

        @Override
        public synchronized UploadRecord getUploadRecord(String uploadId) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query("artifact_uploads", null, "upload_id = ?", new String[]{uploadId}, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    UploadRecord r = new UploadRecord();
                    r.uploadId = cursor.getString(cursor.getColumnIndexOrThrow("upload_id"));
                    r.executionId = cursor.getString(cursor.getColumnIndexOrThrow("execution_id"));
                    r.contentDigest = cursor.getString(cursor.getColumnIndexOrThrow("content_digest"));
                    r.byteSize = cursor.getLong(cursor.getColumnIndexOrThrow("byte_size"));
                    r.filePath = cursor.getString(cursor.getColumnIndexOrThrow("file_path"));
                    r.phase = cursor.getString(cursor.getColumnIndexOrThrow("phase"));
                    r.offsetBytes = cursor.getLong(cursor.getColumnIndexOrThrow("offset_bytes"));
                    r.createdAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("created_at_ms"));
                    r.updatedAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at_ms"));
                    return r;
                }
            } finally {
                cursor.close();
            }
            return null;
        }

        @Override
        public synchronized UploadRecord getUploadRecordByExecution(String executionId) {
            SQLiteDatabase db = getReadableDatabase();
            Cursor cursor = db.query("artifact_uploads", null, "execution_id = ?", new String[]{executionId}, null, null, "created_at_ms DESC", "1");
            try {
                if (cursor.moveToFirst()) {
                    UploadRecord r = new UploadRecord();
                    r.uploadId = cursor.getString(cursor.getColumnIndexOrThrow("upload_id"));
                    r.executionId = cursor.getString(cursor.getColumnIndexOrThrow("execution_id"));
                    r.contentDigest = cursor.getString(cursor.getColumnIndexOrThrow("content_digest"));
                    r.byteSize = cursor.getLong(cursor.getColumnIndexOrThrow("byte_size"));
                    r.filePath = cursor.getString(cursor.getColumnIndexOrThrow("file_path"));
                    r.phase = cursor.getString(cursor.getColumnIndexOrThrow("phase"));
                    r.offsetBytes = cursor.getLong(cursor.getColumnIndexOrThrow("offset_bytes"));
                    r.createdAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("created_at_ms"));
                    r.updatedAtMs = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at_ms"));
                    return r;
                }
            } finally {
                cursor.close();
            }
            return null;
        }
    }

    // =========================================================================
    // In-Memory Backend (for Unit Tests and Verification without Android Device)
    // =========================================================================
    public static class InMemoryBackend implements Backend {
        private final Object lock = new Object();
        private String nodeId = "node-phone-" + UUID.randomUUID().toString();
        private final Map<String, ExecutionRecord> executions = new HashMap<>();
        private final Map<String, String> cancellationIntents = new HashMap<>();
        private final Map<String, UploadRecord> uploads = new HashMap<>();
        private final List<EventRecord> events = new ArrayList<>();

        @Override
        public String getOrGenerateNodeId() {
            synchronized (lock) {
                return nodeId;
            }
        }

        @Override
        public AdmissionResult atomicAdmitExecution(String executionId, String commandId, String sessionId, String operation, String paramsJson) {
            synchronized (lock) {
                // 1. Check pre-cancel
                if (cancellationIntents.containsKey(executionId)) {
                    String cancelReason = cancellationIntents.get(executionId);
                    long now = System.currentTimeMillis();
                    ExecutionRecord r = new ExecutionRecord();
                    r.executionId = executionId;
                    r.commandId = commandId;
                    r.sessionId = sessionId;
                    r.operation = operation;
                    r.parametersJson = paramsJson;
                    r.phase = "CANCELLED";
                    r.reason = cancelReason;
                    r.createdAtMs = now;
                    r.updatedAtMs = now;
                    executions.put(executionId, r);
                    return new AdmissionResult(AdmissionStatus.PRE_CANCELLED, r.copy(), cancelReason);
                }

                // 2. Check existing
                ExecutionRecord existing = executions.get(executionId);
                if (existing != null) {
                    boolean opMatches = (existing.operation == null && operation == null) ||
                            (existing.operation != null && existing.operation.equals(operation));
                    boolean paramMatches = (existing.parametersJson == null && paramsJson == null) ||
                            (existing.parametersJson != null && existing.parametersJson.equals(paramsJson));

                    if (opMatches && paramMatches) {
                        return new AdmissionResult(AdmissionStatus.DUPLICATE, existing.copy(), "Idempotent duplicate submission");
                    } else {
                        // Conflict: altered parameters. Do NOT mutate original record!
                        return new AdmissionResult(AdmissionStatus.CONFLICT, existing.copy(),
                                "E_PARAMETER_CONFLICT: Execution parameters differ from originally accepted submission");
                    }
                }

                // 3. New record
                long now = System.currentTimeMillis();
                ExecutionRecord r = new ExecutionRecord();
                r.executionId = executionId;
                r.commandId = commandId;
                r.sessionId = sessionId;
                r.operation = operation;
                r.parametersJson = paramsJson;
                r.phase = "ACCEPTED";
                r.reason = "Accepted by Android Phone Node";
                r.createdAtMs = now;
                r.updatedAtMs = now;
                executions.put(executionId, r);
                return new AdmissionResult(AdmissionStatus.ADMITTED, r.copy(), "Accepted by Android Phone Node");
            }
        }

        @Override
        public CancelResult atomicCancelExecution(String executionId, String commandId, String reason) {
            synchronized (lock) {
                cancellationIntents.put(executionId, reason);
                ExecutionRecord existing = executions.get(executionId);
                if (existing == null) {
                    return new CancelResult(CancelStatus.RECORDED_PRE_CANCEL, null, "Cancellation intent recorded prior to execution admission");
                }

                if (isTerminalPhase(existing.phase)) {
                    return new CancelResult(CancelStatus.ALREADY_TERMINAL, existing.phase,
                            "Task already in terminal phase " + existing.phase + "; terminal state preserved");
                }

                existing.phase = "CANCELLED";
                existing.reason = reason;
                existing.updatedAtMs = System.currentTimeMillis();
                return new CancelResult(CancelStatus.CANCELLED_ACTIVE, "CANCELLED", reason);
            }
        }

        @Override
        public boolean atomicTransitionPhase(String executionId, String expectedCurrentPhase, String newPhase, String reason) {
            synchronized (lock) {
                ExecutionRecord rec = executions.get(executionId);
                if (rec == null) return false;
                if (isTerminalPhase(rec.phase)) {
                    return false;
                }
                rec.phase = newPhase;
                rec.reason = reason;
                rec.updatedAtMs = System.currentTimeMillis();
                return true;
            }
        }

        @Override
        public boolean atomicCompleteArtifactExecution(String executionId, String reason, String artifactDigest, String artifactPath, long artifactSize) {
            synchronized (lock) {
                ExecutionRecord rec = executions.get(executionId);
                if (rec == null || isTerminalPhase(rec.phase)) {
                    return false;
                }
                rec.phase = "COMPLETED";
                rec.reason = reason;
                rec.artifactDigest = artifactDigest;
                rec.artifactPath = artifactPath;
                rec.artifactSize = artifactSize;
                rec.updatedAtMs = System.currentTimeMillis();
                return true;
            }
        }

        @Override
        public ExecutionRecord getExecution(String executionId) {
            synchronized (lock) {
                ExecutionRecord r = executions.get(executionId);
                return r != null ? r.copy() : null;
            }
        }

        @Override
        public void recordEvent(String executionId, long sequence, String phase, String reason) {
            synchronized (lock) {
                EventRecord e = new EventRecord();
                e.executionId = executionId;
                e.sequence = sequence;
                e.phase = phase;
                e.reason = reason;
                e.timestampMs = System.currentTimeMillis();
                events.add(e);
            }
        }

        @Override
        public long getLatestSequence(String executionId) {
            synchronized (lock) {
                long max = 1L;
                for (EventRecord e : events) {
                    if (executionId.equals(e.executionId) && e.sequence > max) {
                        max = e.sequence;
                    }
                }
                return max;
            }
        }

        @Override
        public long getMaxPersistedSequence() {
            synchronized (lock) {
                long max = 0L;
                for (EventRecord e : events) {
                    if (e.sequence > max) {
                        max = e.sequence;
                    }
                }
                return max;
            }
        }

        @Override
        public List<ExecutionRecord> getReconcilableExecutions() {
            synchronized (lock) {
                List<ExecutionRecord> list = new ArrayList<>();
                for (ExecutionRecord r : executions.values()) {
                    list.add(r.copy());
                }
                Collections.sort(list, (a, b) -> Long.compare(b.createdAtMs, a.createdAtMs));
                return list;
            }
        }

        @Override
        public List<ExecutionRecord> reconcileInterruptedExecutions() {
            synchronized (lock) {
                List<ExecutionRecord> interrupted = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (ExecutionRecord r : executions.values()) {
                    if ("ACCEPTED".equalsIgnoreCase(r.phase) || "STARTED".equalsIgnoreCase(r.phase)) {
                        r.phase = "FAILED";
                        r.reason = "E_INTERRUPTED_BY_RESTART: Process restarted before task reached terminal phase; reconciliation required";
                        r.updatedAtMs = now;
                        interrupted.add(r.copy());
                    }
                }
                return interrupted;
            }
        }

        @Override
        public JSONArray listExecutionsAsJson(int limit) {
            synchronized (lock) {
                JSONArray arr = new JSONArray();
                List<ExecutionRecord> list = getReconcilableExecutions();
                int count = Math.min(limit, list.size());
                for (int i = 0; i < count; i++) {
                    ExecutionRecord r = list.get(i);
                    try {
                        JSONObject obj = new JSONObject();
                        obj.put("execution_id", r.executionId);
                        obj.put("command_id", r.commandId);
                        obj.put("session_id", r.sessionId);
                        obj.put("operation", r.operation);
                        obj.put("parameters_json", r.parametersJson);
                        obj.put("phase", r.phase);
                        obj.put("reason", r.reason);
                        obj.put("artifact_digest", r.artifactDigest);
                        obj.put("artifact_path", r.artifactPath);
                        obj.put("artifact_size", r.artifactSize);
                        obj.put("created_at_ms", r.createdAtMs);
                        obj.put("updated_at_ms", r.updatedAtMs);
                        arr.put(obj);
                    } catch (Exception ignored) {}
                }
                return arr;
            }
        }

        @Override
        public void recordUploadIntent(String uploadId, String executionId, String contentDigest, long byteSize, String filePath) {
            synchronized (lock) {
                UploadRecord u = new UploadRecord();
                u.uploadId = uploadId;
                u.executionId = executionId;
                u.contentDigest = contentDigest;
                u.byteSize = byteSize;
                u.filePath = filePath;
                u.phase = "STARTED";
                u.offsetBytes = 0;
                long now = System.currentTimeMillis();
                u.createdAtMs = now;
                u.updatedAtMs = now;
                uploads.put(uploadId, u);
            }
        }

        @Override
        public void updateUploadProgress(String uploadId, String phase, long offsetBytes) {
            synchronized (lock) {
                UploadRecord u = uploads.get(uploadId);
                if (u != null) {
                    u.phase = phase;
                    u.offsetBytes = offsetBytes;
                    u.updatedAtMs = System.currentTimeMillis();
                }
            }
        }

        @Override
        public UploadRecord getUploadRecord(String uploadId) {
            synchronized (lock) {
                return uploads.get(uploadId);
            }
        }

        @Override
        public UploadRecord getUploadRecordByExecution(String executionId) {
            synchronized (lock) {
                for (UploadRecord u : uploads.values()) {
                    if (executionId.equals(u.executionId)) return u;
                }
                return null;
            }
        }
    }
}
