package com.syswonder.roboguide.hub;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.UUID;

/**
 * Persistent SQLite Journal for RoboGuide Android Phone Node.
 * Stores node identity, execution records, command idempotency, and artifact metadata across restarts.
 */
public class PhoneNodeJournal extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "phone_node_journal.db";
    private static final int DATABASE_VERSION = 1;

    private static PhoneNodeJournal instance;

    public static synchronized PhoneNodeJournal getInstance(Context context) {
        if (instance == null) {
            instance = new PhoneNodeJournal(context.getApplicationContext());
        }
        return instance;
    }

    private PhoneNodeJournal(Context context) {
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Migration logic if schema changes
    }

    /**
     * Retrieves or generates a durable, globally unique Node ID for this Android device.
     * Persists across application restarts and device reboots.
     */
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

        // Generate persistent node ID with standard prefix
        String newId = "node-phone-" + UUID.randomUUID().toString();
        ContentValues cv = new ContentValues();
        cv.put("key", "node_id");
        cv.put("value", newId);
        db.insertWithOnConflict("node_identity", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        return newId;
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
    }

    public synchronized ExecutionRecord getExecution(String executionId) {
        SQLiteDatabase db = getReadableDatabase();
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

    /**
     * Checks idempotency:
     * - Returns 0 if new execution
     * - Returns 1 if identical execution (idempotent duplicate)
     * - Returns -1 if conflict (same execution_id but different parameters or operation)
     */
    public synchronized int checkConflict(String executionId, String operation, String parametersJson) {
        ExecutionRecord existing = getExecution(executionId);
        if (existing == null) {
            return 0; // New execution
        }
        boolean opMatches = (existing.operation == null && operation == null) ||
                (existing.operation != null && existing.operation.equals(operation));
        boolean paramMatches = (existing.parametersJson == null && parametersJson == null) ||
                (existing.parametersJson != null && existing.parametersJson.equals(parametersJson));
        if (opMatches && paramMatches) {
            return 1; // Idempotent match
        } else {
            return -1; // Parameter / operation conflict
        }
    }

    public synchronized void recordNewExecution(String executionId, String commandId, String sessionId,
                                               String operation, String parametersJson, String initialPhase, String reason) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("execution_id", executionId);
        cv.put("command_id", commandId);
        cv.put("session_id", sessionId);
        cv.put("operation", operation);
        cv.put("parameters_json", parametersJson);
        cv.put("phase", initialPhase);
        cv.put("reason", reason);
        cv.put("created_at_ms", now);
        cv.put("updated_at_ms", now);
        db.insertWithOnConflict("executions", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void updateExecutionPhase(String executionId, String phase, String reason) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("phase", phase);
        cv.put("reason", reason);
        cv.put("updated_at_ms", now);
        db.update("executions", cv, "execution_id = ?", new String[]{executionId});
    }

    public synchronized void updateExecutionArtifact(String executionId, String phase, String reason,
                                                    String artifactDigest, String artifactPath, long artifactSize) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues cv = new ContentValues();
        cv.put("phase", phase);
        cv.put("reason", reason);
        cv.put("artifact_digest", artifactDigest);
        cv.put("artifact_path", artifactPath);
        cv.put("artifact_size", artifactSize);
        cv.put("updated_at_ms", now);
        db.update("executions", cv, "execution_id = ?", new String[]{executionId});
    }

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
            e.printStackTrace();
        } finally {
            cursor.close();
        }
        return arr;
    }
}
