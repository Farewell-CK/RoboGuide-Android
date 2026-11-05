package com.syswonder.roboguide.hub;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera Capture Helper for observation.acquire@v1 on RoboGuide Android Phone Node.
 * Enforces camera permission verification, captures real image artifact via Camera2 API,
 * closes camera devices and image readers deterministically across all exit paths,
 * computes SHA-256 digest, and strictly disallows synthetic fallbacks during real execution.
 */
public class CameraCaptureHelper {
    private static final String TAG = "CameraCaptureHelper";

    public static class CaptureResult {
        public boolean success;
        public String filePath;
        public String sha256Digest;
        public long sizeBytes;
        public String mimeType;
        public String errorMessage;
        public long timestampMs;
    }

    /**
     * Default observation capture: strictly disallows synthetic demo fallbacks.
     */
    public static CaptureResult captureObservation(Context context, String executionId, String expectedCondition) {
        return captureObservation(context, executionId, expectedCondition, false);
    }

    /**
     * Observation capture with explicit fallback control.
     * @param allowDemoFallback If false (production), failure/missing camera strictly returns error.
     *                          If true (isolated DEMO only), generates synthetic visual marker.
     */
    public static CaptureResult captureObservation(Context context, String executionId, String expectedCondition, boolean allowDemoFallback) {
        CaptureResult result = new CaptureResult();
        result.timestampMs = System.currentTimeMillis();
        result.mimeType = "image/jpeg";

        // 1. Strict permission check
        if (context == null) {
            result.success = false;
            result.errorMessage = "E_PERMISSION_DENIED: Android Context unavailable or missing CAMERA permission";
            return result;
        }

        int permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            result.success = false;
            result.errorMessage = "E_PERMISSION_DENIED: Android CAMERA permission not granted by user";
            return result;
        }

        try {
            // 2. Prepare artifact output file
            File artifactDir = new File(context.getFilesDir(), "artifacts");
            if (!artifactDir.exists()) {
                artifactDir.mkdirs();
            }

            String fileName = "obs_" + executionId + "_" + result.timestampMs + ".jpg";
            File outputFile = new File(artifactDir, fileName);

            // 3. Try capturing from physical hardware camera via Camera2 API
            boolean physicalCaptured = tryPhysicalCameraCapture(context, outputFile);
            if (!physicalCaptured) {
                if (!allowDemoFallback) {
                    // Production path: never falsify real observation with synthetic images
                    result.success = false;
                    result.errorMessage = "E_CAMERA_UNAVAILABLE: Physical camera capture failed or camera hardware busy/unavailable";
                    return result;
                } else {
                    // Isolated demo path only
                    Log.i(TAG, "DEMO MODE: Using synthetic sensor pattern for observation artifact");
                    generateSensorFallbackImage(outputFile, executionId, expectedCondition, result.timestampMs);
                }
            }

            if (!outputFile.exists() || outputFile.length() == 0) {
                result.success = false;
                result.errorMessage = "E_CAPTURE_FAILED: Output image file was not created or has 0 bytes";
                return result;
            }

            // 4. Compute exact SHA-256 digest over persisted bytes
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            try (InputStream is = new FileInputStream(outputFile)) {
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }

            result.success = true;
            result.filePath = outputFile.getAbsolutePath();
            result.sizeBytes = outputFile.length();
            result.sha256Digest = "sha256:" + sb.toString();
            return result;

        } catch (Exception e) {
            result.success = false;
            result.errorMessage = "E_CAPTURE_FAILED: " + e.getMessage();
            return result;
        }
    }

    @SuppressLint("MissingPermission")
    private static boolean tryPhysicalCameraCapture(Context context, File outputFile) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            return false;
        }

        HandlerThread bgThread = new HandlerThread("CameraBackground");
        bgThread.start();
        Handler bgHandler = new Handler(bgThread.getLooper());

        final AtomicBoolean captured = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);

        CameraDevice[] openDeviceHolder = new CameraDevice[1];
        CameraCaptureSession[] openSessionHolder = new CameraCaptureSession[1];
        ImageReader[] openReaderHolder = new ImageReader[1];

        try {
            String[] cameraIdList = manager.getCameraIdList();
            if (cameraIdList.length == 0) {
                return false;
            }

            String targetCameraId = cameraIdList[0];
            for (String id : cameraIdList) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = id;
                    break;
                }
            }

            final ImageReader imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2);
            openReaderHolder[0] = imageReader;

            imageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                            fos.write(bytes);
                            fos.flush();
                        }
                        captured.set(true);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to write image from ImageReader", e);
                } finally {
                    if (image != null) {
                        try { image.close(); } catch (Exception ignored) {}
                    }
                    latch.countDown();
                }
            }, bgHandler);

            manager.openCamera(targetCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    openDeviceHolder[0] = camera;
                    try {
                        camera.createCaptureSession(
                                Collections.singletonList(imageReader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(@NonNull CameraCaptureSession session) {
                                        openSessionHolder[0] = session;
                                        try {
                                            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                            builder.addTarget(imageReader.getSurface());
                                            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                            session.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                                                @Override
                                                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                                               @NonNull CaptureRequest request,
                                                                               @NonNull TotalCaptureResult result) {
                                                    super.onCaptureCompleted(session, request, result);
                                                }
                                            }, bgHandler);
                                        } catch (CameraAccessException e) {
                                            Log.w(TAG, "CaptureSession capture error", e);
                                            latch.countDown();
                                        }
                                    }

                                    @Override
                                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                                        latch.countDown();
                                    }
                                },
                                bgHandler
                        );
                    } catch (CameraAccessException e) {
                        Log.w(TAG, "Camera createCaptureSession error", e);
                        latch.countDown();
                    }
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    try { camera.close(); } catch (Exception ignored) {}
                    openDeviceHolder[0] = null;
                    latch.countDown();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    try { camera.close(); } catch (Exception ignored) {}
                    openDeviceHolder[0] = null;
                    latch.countDown();
                }
            }, bgHandler);

            boolean finished = latch.await(3000, TimeUnit.MILLISECONDS);
            if (!finished) {
                Log.w(TAG, "Physical camera capture timed out after 3000ms");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "Physical camera capture interrupted during latch await");
            return false;
        } catch (Throwable t) {
            Log.w(TAG, "Physical camera capture exception: " + t.getMessage());
        } finally {
            // Clean up session
            if (openSessionHolder[0] != null) {
                try { openSessionHolder[0].close(); } catch (Exception ignored) {}
            }
            // Clean up device
            if (openDeviceHolder[0] != null) {
                try { openDeviceHolder[0].close(); } catch (Exception ignored) {}
            }
            // Clean up image reader
            if (openReaderHolder[0] != null) {
                try { openReaderHolder[0].close(); } catch (Exception ignored) {}
            }
            // Quit background thread safely
            bgThread.quitSafely();
            try {
                bgThread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return captured.get();
    }

    /**
     * Isolated DEMO helper: generates labeled test pattern strictly when explicitly authorized.
     */
    public static void generateSensorFallbackImage(File outputFile, String executionId, String expectedCondition, long timestampMs) throws Exception {
        int width = 1280;
        int height = 720;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        canvas.drawColor(Color.rgb(24, 28, 36));

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Sensor reticle grid
        paint.setColor(Color.rgb(40, 50, 65));
        paint.setStrokeWidth(2f);
        for (int x = 160; x < width; x += 160) {
            canvas.drawLine(x, 0, x, height, paint);
        }
        for (int y = 90; y < height; y += 90) {
            canvas.drawLine(0, y, width, y, paint);
        }

        // Central crosshair
        paint.setColor(Color.rgb(56, 189, 248));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawCircle(width / 2f, height / 2f, 80f, paint);
        canvas.drawLine(width / 2f - 110, height / 2f, width / 2f + 110, height / 2f, paint);
        canvas.drawLine(width / 2f, height / 2f - 110, width / 2f, height / 2f + 110, paint);

        // Watermark & semantic observation metadata
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextSize(26f);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        String timeStr = sdf.format(new Date(timestampMs));

        canvas.drawText("[DEMO SIMULATION] OPTICAL OBSERVATION ARTIFACT", 40, 50, paint);
        paint.setTextSize(20f);
        paint.setColor(Color.rgb(200, 210, 225));
        canvas.drawText("Execution ID: " + executionId, 40, 90, paint);
        canvas.drawText("Timestamp:    " + timeStr, 40, 120, paint);
        canvas.drawText("Condition:    " + (expectedCondition != null ? expectedCondition : "unspecified"), 40, 150, paint);
        canvas.drawText("Device Sensor: [DEMO ONLY] Simulated Pattern", 40, 180, paint);

        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            fos.flush();
        }
        bitmap.recycle();
    }
}
