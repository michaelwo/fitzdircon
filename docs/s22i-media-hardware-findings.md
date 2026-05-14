# S22i Camera and Microphone Findings

## Summary

The NordicTrack S22i console tested here exposes both camera and microphone hardware through normal Android APIs. The known iFit / GlassOS gRPC workout control surface does not appear to expose camera or microphone controls, and the installed iFit packages do not request Android camera or microphone permissions.

The hardware appears to be basic embedded tablet media hardware:

- Camera: single front-facing camera, approximately 2 MP (`1600x1200`), Camera2 `LIMITED`.
- Microphone: basic embedded mono capture path, usable through `AudioRecord`.

These findings are from the S22i at `192.168.1.213:5555` using the local probe app in `tools/s22i-media-probe`.

## Probe Results

Artifacts:

- System dump run: `/tmp/s22i-media-probe-20260513-161735`
- Runtime probe log: `/tmp/s22i-media-probe-20260513-161735-second/probe-logcat.txt`

Android feature flags reported:

```text
feature:android.hardware.camera
feature:android.hardware.camera.any
feature:android.hardware.microphone
```

Runtime probe app result:

```text
Camera IDs reported: 1
Camera 0: facing=front, capabilities=[0]
Camera 0 open succeeded.
AudioRecord minBuffer=1280
AudioRecord state=1
AudioRecord recordingState=3
Microphone capture: samples=24000, nonZero=22262, maxAbs=6793
```

The camera and microphone are therefore accessible to a normal installed Android app once `CAMERA` and `RECORD_AUDIO` are granted.

## Camera Hardware

`dumpsys media.camera` reports:

```text
Camera module name: Camera HAL3
Camera module author: Nexell
Number of camera devices: 1
Facing: FRONT
android.info.supportedHardwareLevel: LIMITED
android.sensor.info.pixelArraySize: 1600 1200
android.sensor.info.activeArraySize: 0 0 1600 1200
android.lens.info.availableFocalLengths: 3.43
android.sensor.info.physicalSize: 3.2 2.4
android.control.aeAvailableTargetFpsRanges: 15 30, 20 20
```

Supported output sizes include:

```text
1600x1200
800x600
640x480
352x288
176x144
```

Inference:

- Maximum still/frame size is roughly 2 MP.
- The sensor is 4:3, not a modern wide-angle HD camera.
- `LIMITED` Camera2 support means do not assume advanced controls or robust manual tuning.
- Expected useful computer-vision cases are coarse: presence detection, marker/QR detection in good light, gross motion, and simple face/person framing.
- Expected weak areas are low light, fine detail, high dynamic range, and high-frame-rate tracking.

## Microphone Hardware

The probe confirmed successful `AudioRecord` capture from `MediaRecorder.AudioSource.MIC`.

Device audio metadata:

```text
/proc/asound/cards:
0 [I2Srt5651]: I2S-rt5651 - I2S-rt5651
```

Runtime capture summary:

```text
samples=24000
nonZero=22262
maxAbs=6793
```

Inference:

- The microphone path is active and returns real PCM samples.
- The hardware appears to be a basic embedded I2S audio codec path.
- No evidence was found for microphone array processing, beamforming, multichannel capture, or conferencing-grade noise rejection.
- Expected useful cases are voice presence, rough speech capture, simple command/event detection, and ambient level estimation.
- Far-field clarity and noise rejection remain unknown without a short listening or reference-tone test.

## iFit and GlassOS Access

The installed package dumps for `com.ifit.rivendell` and `com.ifit.glassos_service` did not show `android.permission.CAMERA` or `android.permission.RECORD_AUDIO` in requested or runtime permissions.

This matches the earlier control-surface investigation:

- GlassOS gRPC is a local workout/telemetry/control surface.
- Known gRPC services cover console info, workout state, incline, resistance, speed, cadence/RPM, watts, and heart rate.
- No camera, microphone, audio, video, or WebRTC service has been identified in the current extracted protos or APK searches.

## Practical Classification

Camera:

- **Accessible:** yes.
- **Quality class:** basic embedded front camera.
- **Likely good enough for:** simple computer vision in good lighting.
- **Likely not good enough for:** fine-detail CV, low-light analysis, high-quality video, or high-frame-rate tracking.

Microphone:

- **Accessible:** yes.
- **Quality class:** basic embedded mono microphone path.
- **Likely good enough for:** voice/event presence and rough audio capture.
- **Unknown without further testing:** far-field speech quality, noise rejection, echo behavior, and frequency response.

## Reproduction

Build the probe:

```sh
./gradlew :tools:s22i-media-probe:assembleDebug
```

Run the system and runtime checks:

```sh
DEVICE=192.168.1.213:5555 tools/s22i-media-probe/run-s22i-media-checks.sh
```

If Android leaves the permission screen open, grant permissions manually or through ADB:

```sh
adb -s 192.168.1.213:5555 shell pm grant \
  org.fitzdircon.tools.mediaprobe \
  android.permission.CAMERA

adb -s 192.168.1.213:5555 shell pm grant \
  org.fitzdircon.tools.mediaprobe \
  android.permission.RECORD_AUDIO
```

Then relaunch the probe:

```sh
adb -s 192.168.1.213:5555 shell monkey -p \
  org.fitzdircon.tools.mediaprobe 1
```
