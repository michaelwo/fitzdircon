# S22i Media Probe

This tool checks whether an S22i console exposes camera and microphone hardware through normal Android APIs. It intentionally does not probe gRPC.

## Build

```sh
./gradlew :tools:s22i-media-probe:assembleDebug
```

## Run

```sh
DEVICE=192.168.1.213:5555 tools/s22i-media-probe/run-s22i-media-checks.sh
```

`DEVICE` is optional if only one ADB device is connected.

The script captures:

- Android feature flags.
- iFit and GlassOS package dumps.
- `media.camera`, `audio`, and `media.audio_flinger` dumps.
- Probe app logcat output under `S22iMediaProbe`.

The app requests `CAMERA` and `RECORD_AUDIO`, enumerates cameras, attempts open/close for each camera ID, and performs a short `AudioRecord` microphone capture. It does not save photos, audio, or video.
