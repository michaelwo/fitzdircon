package org.fitzdircon.tools.mediaprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MediaProbeActivity extends Activity {
    private static final String TAG = "S22iMediaProbe";
    private static final int REQUEST_PERMISSIONS = 2201;

    private TextView output;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private SurfaceView surfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("S22i Media Probe");
        setContentView(createView());
        startCameraThread();
        logLine("S22i media probe ready.");
        logLine("This app logs hardware/API availability only; it does not save photos, audio, or video.");
        requestProbePermissions();
    }

    @Override
    protected void onDestroy() {
        if (cameraThread != null) {
            cameraThread.quitSafely();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            logLine("Permission result: camera=%s, recordAudio=%s",
                    permissionState(Manifest.permission.CAMERA),
                    permissionState(Manifest.permission.RECORD_AUDIO));
            runProbe();
        }
    }

    private LinearLayout createView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        Button run = new Button(this);
        run.setText("Run Probe");
        run.setOnClickListener(v -> {
            if (hasPermission(Manifest.permission.CAMERA) && hasPermission(Manifest.permission.RECORD_AUDIO)) {
                runProbe();
            } else {
                requestProbePermissions();
            }
        });
        root.addView(run, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        surfaceView = new SurfaceView(this);
        root.addView(surfaceView, new LinearLayout.LayoutParams(1, 1));

        output = new TextView(this);
        output.setTextSize(14);
        output.setGravity(Gravity.START);
        output.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));
        return root;
    }

    private void requestProbePermissions() {
        if (hasPermission(Manifest.permission.CAMERA) && hasPermission(Manifest.permission.RECORD_AUDIO)) {
            logLine("Permissions already granted.");
            runProbe();
            return;
        }
        logLine("Requesting CAMERA and RECORD_AUDIO permissions.");
        requestPermissions(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
        }, REQUEST_PERMISSIONS);
    }

    private void runProbe() {
        logLine("=== Probe start ===");
        logLine("Package manager features: camera.any=%s, camera=%s, camera.front=%s, microphone=%s",
                hasFeature(PackageManager.FEATURE_CAMERA_ANY),
                hasFeature(PackageManager.FEATURE_CAMERA),
                hasFeature(PackageManager.FEATURE_CAMERA_FRONT),
                hasFeature(PackageManager.FEATURE_MICROPHONE));
        probeCameras();
        probeMicrophone();
        logLine("=== Probe complete ===");
    }

    private void probeCameras() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (manager == null) {
            logLine("CameraManager unavailable.");
            return;
        }
        if (!hasPermission(Manifest.permission.CAMERA)) {
            logLine("Camera probe skipped: CAMERA permission is %s.", permissionState(Manifest.permission.CAMERA));
            return;
        }
        try {
            String[] ids = manager.getCameraIdList();
            logLine("Camera IDs reported: %d", ids.length);
            for (String id : ids) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                int[] capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                logLine("Camera %s: facing=%s, capabilities=%s", id, facingLabel(facing), intArray(capabilities));
                openCloseCamera(manager, id);
            }
        } catch (Exception e) {
            logLine("Camera enumeration failed: %s: %s", e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void openCloseCamera(CameraManager manager, String id) {
        CountDownLatch latch = new CountDownLatch(1);
        final CameraDevice[] opened = new CameraDevice[1];
        try {
            manager.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    opened[0] = camera;
                    logLine("Camera %s open succeeded.", id);
                    createOneSurfaceSession(camera, id);
                    latch.countDown();
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    logLine("Camera %s disconnected.", id);
                    camera.close();
                    latch.countDown();
                }

                @Override
                public void onError(CameraDevice camera, int error) {
                    logLine("Camera %s open failed with error=%d.", id, error);
                    camera.close();
                    latch.countDown();
                }
            }, cameraHandler);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                logLine("Camera %s open timed out.", id);
            }
        } catch (SecurityException e) {
            logLine("Camera %s open denied: %s", id, e.getMessage());
        } catch (CameraAccessException e) {
            logLine("Camera %s access failed: %s", id, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logLine("Camera %s open interrupted.", id);
        } finally {
            if (opened[0] != null) {
                opened[0].close();
                logLine("Camera %s closed.", id);
            }
        }
    }

    private void createOneSurfaceSession(CameraDevice camera, String id) {
        Surface surface = surfaceView.getHolder().getSurface();
        if (surface == null || !surface.isValid()) {
            logLine("Camera %s preview session skipped: surface is not valid.", id);
            return;
        }
        try {
            camera.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    logLine("Camera %s preview session configured.", id);
                    session.close();
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    logLine("Camera %s preview session configure failed.", id);
                    session.close();
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            logLine("Camera %s preview session failed: %s", id, e.getMessage());
        }
    }

    private void probeMicrophone() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            logLine("Microphone probe skipped: RECORD_AUDIO permission is %s.",
                    permissionState(Manifest.permission.RECORD_AUDIO));
            return;
        }
        int sampleRate = 16000;
        int minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        logLine("AudioRecord minBuffer=%d", minBuffer);
        if (minBuffer <= 0) {
            logLine("Microphone unavailable: invalid min buffer size.");
            return;
        }

        int bufferSize = Math.max(minBuffer, sampleRate);
        AudioRecord record = null;
        try {
            record = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
            logLine("AudioRecord state=%d", record.getState());
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                logLine("Microphone unavailable: AudioRecord did not initialize.");
                return;
            }
            record.startRecording();
            logLine("AudioRecord recordingState=%d", record.getRecordingState());
            short[] buffer = new short[Math.min(bufferSize / 2, sampleRate / 2)];
            int total = 0;
            long nonZero = 0;
            int maxAbs = 0;
            long deadline = System.currentTimeMillis() + 1500;
            while (System.currentTimeMillis() < deadline) {
                int read = record.read(buffer, 0, buffer.length);
                if (read > 0) {
                    total += read;
                    for (int i = 0; i < read; i++) {
                        int abs = Math.abs((int) buffer[i]);
                        if (abs != 0) nonZero++;
                        if (abs > maxAbs) maxAbs = abs;
                    }
                } else {
                    logLine("AudioRecord read returned %d", read);
                    break;
                }
            }
            logLine("Microphone capture: samples=%d, nonZero=%d, maxAbs=%d", total, nonZero, maxAbs);
        } catch (SecurityException e) {
            logLine("Microphone denied: %s", e.getMessage());
        } catch (Exception e) {
            logLine("Microphone probe failed: %s: %s", e.getClass().getSimpleName(), e.getMessage());
        } finally {
            if (record != null) {
                try {
                    if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop();
                    }
                } catch (IllegalStateException ignored) {
                }
                record.release();
            }
        }
    }

    private void startCameraThread() {
        cameraThread = new HandlerThread("s22i-media-probe-camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private String permissionState(String permission) {
        return hasPermission(permission) ? "granted" : "denied";
    }

    private boolean hasFeature(String feature) {
        return getPackageManager().hasSystemFeature(feature);
    }

    private String facingLabel(Integer facing) {
        if (facing == null) return "unknown";
        switch (facing) {
            case CameraCharacteristics.LENS_FACING_FRONT:
                return "front";
            case CameraCharacteristics.LENS_FACING_BACK:
                return "back";
            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                return "external";
            default:
                return "value=" + facing;
        }
    }

    private String intArray(int[] values) {
        if (values == null) return "[]";
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i]);
        }
        return out.append(']').toString();
    }

    private void logLine(String format, Object... args) {
        String line = args.length == 0 ? format : String.format(Locale.US, format, args);
        Log.i(TAG, line);
        runOnUiThread(() -> output.append(line + "\n"));
    }
}
