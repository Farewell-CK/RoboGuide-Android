package com.syswonder.roboguide.hub;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

/**
 * Foreground keep-alive service for RoboGuide Phone Node.
 * Maintains CPU PARTIAL_WAKE_LOCK and High-Performance WIFI_LOCK so that
 * HyperOS / Android background policies do not suspend the mTLS gRPC heartbeat stream
 * when the user switches apps or locks the screen.
 */
public class PhoneNodeForegroundService extends Service {
    private static final String TAG = "PhoneNodeFGService";
    private static final String CHANNEL_ID = "roboguide_phone_node_channel";
    private static final int NOTIFICATION_ID = 9501;

    public static final String ACTION_START_OR_UPDATE = "com.syswonder.roboguide.hub.ACTION_START_OR_UPDATE";
    public static final String ACTION_STOP = "com.syswonder.roboguide.hub.ACTION_STOP";

    public static final String EXTRA_NODE_ID = "extra_node_id";
    public static final String EXTRA_STATE = "extra_state";
    public static final String EXTRA_DETAIL = "extra_detail";

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;

    public static void startOrUpdate(Context context, String nodeId, String state, String detail) {
        if (context == null) return;
        try {
            Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            Intent intent = new Intent(appCtx, PhoneNodeForegroundService.class);
            intent.setAction(ACTION_START_OR_UPDATE);
            intent.putExtra(EXTRA_NODE_ID, nodeId != null ? nodeId : "phone-node");
            intent.putExtra(EXTRA_STATE, state != null ? state : "STREAMING");
            intent.putExtra(EXTRA_DETAIL, detail != null ? detail : "");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(intent);
            } else {
                appCtx.startService(intent);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Unable to start/update foreground service: " + t.getMessage());
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        try {
            Context appCtx = context.getApplicationContext() != null ? context.getApplicationContext() : context;
            Intent intent = new Intent(appCtx, PhoneNodeForegroundService.class);
            appCtx.stopService(intent);
        } catch (Throwable t) {
            Log.w(TAG, "Unable to stop foreground service: " + t.getMessage());
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireLocks();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            releaseLocks();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        String nodeId = intent != null ? intent.getStringExtra(EXTRA_NODE_ID) : "phone-node-alpha";
        String state = intent != null ? intent.getStringExtra(EXTRA_STATE) : "STREAMING";
        String detail = intent != null ? intent.getStringExtra(EXTRA_DETAIL) : "mTLS gRPC lease active";

        acquireLocks();
        Notification notification = buildNotification(nodeId, state, detail);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Throwable t) {
            Log.w(TAG, "startForeground failed: " + t.getMessage());
        }

        // Ensure PhoneNodeManager session is active if started sticky or via intent
        try {
            PhoneNodeManager mgr = PhoneNodeManager.getInstance(getApplicationContext());
            if (mgr.getState() == PhoneNodeManager.NodeState.DISCONNECTED) {
                PhoneNodeConfig cfg = PhoneNodeConfig.load(getApplicationContext());
                if (cfg != null && cfg.useMtls
                        && cfg.nodeCertPem != null && !cfg.nodeCertPem.trim().isEmpty()
                        && cfg.nodeKeyPem != null && !cfg.nodeKeyPem.trim().isEmpty()) {
                    mgr.connectWithConfig(cfg);
                }
            }
        } catch (Throwable ignored) {}

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "RoboGuide Node Session Keep-Alive",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Maintains RoboGuide mTLS gRPC node session and lease heartbeats in background");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String nodeId, String state, String detail) {
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, openIntent, piFlags);

        String title = "RoboGuide 节点保活: " + (nodeId != null ? nodeId : "phone-node");
        String content = "[" + (state != null ? state : "ONLINE") + "] " + (detail != null ? detail : "");

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private synchronized void acquireLocks() {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RoboGuide:PhoneNodeWakeLock");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire();
                Log.i(TAG, "Acquired CPU PARTIAL_WAKE_LOCK for background gRPC heartbeat stream");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to acquire WakeLock: " + t.getMessage());
        }

        try {
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    int lockType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            ? WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                            : WifiManager.WIFI_MODE_FULL_HIGH_PERF;
                    wifiLock = wm.createWifiLock(lockType, "RoboGuide:PhoneNodeWifiLock");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
                Log.i(TAG, "Acquired High-Perf WIFI_LOCK for low-latency mTLS streaming");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to acquire WifiLock: " + t.getMessage());
        }
    }

    private synchronized void releaseLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.i(TAG, "Released CPU PARTIAL_WAKE_LOCK");
            }
        } catch (Throwable ignored) {}

        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                Log.i(TAG, "Released WIFI_LOCK");
            }
        } catch (Throwable ignored) {}
    }
}
