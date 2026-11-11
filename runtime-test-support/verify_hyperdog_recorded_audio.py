#!/usr/bin/env python3
"""Validate Minecraft-produced PCM from an isolated PulseAudio monitor, and
publish short listenable audio proof. Must FAIL on silent or missing recordings.

The audio file is sampled from the REAL Minecraft output mixer in the same
95-game Paper/Fabric integration test, not a synthetic sound or test fixture.
"""
from __future__ import annotations
import audioop
import json
import sys
import wave
from pathlib import Path

RATE, CHANNELS, BYTES_PER_SAMPLE = 44100, 2, 2
SECOND = RATE * CHANNELS * BYTES_PER_SAMPLE
THRESHOLD = 40

def main() -> int:
    if len(sys.argv) != 2:
        raise SystemExit("usage: verify_hyperdog_recorded_audio.py <raw pcm path>")
    raw = Path(sys.argv[1])
    root = raw.parent
    if not raw.is_file() or raw.stat().st_size < SECOND * 20:
        raise AssertionError("Minecraft PCM capture is missing or too short")
    rms_values: list[int] = []
    peaks: list[int] = []
    with raw.open("rb") as f:
        while True:
            chunk = f.read(SECOND)
            if not chunk:
                break
            if len(chunk) % (CHANNELS * BYTES_PER_SAMPLE):
                chunk = chunk[:len(chunk) // 4 * 4]
            if not chunk:
                break
            rms_values.append(audioop.rms(chunk, BYTES_PER_SAMPLE))
            peaks.append(audioop.max(chunk, BYTES_PER_SAMPLE))
    active = [i for i, rms in enumerate(rms_values) if rms >= THRESHOLD]
    summary = {
        "source": "PulseAudio isolated hyperdog_test.monitor recording of actual Minecraft sound output",
        "durationSeconds": len(rms_values),
        "activeSecondCount": len(active),
        "peakAmplitude16Bit": max(peaks, default=0),
        "maxOneSecondRms16Bit": max(rms_values, default=0),
        "positiveThresholdRms16Bit": THRESHOLD,
        "firstActiveSecond": active[0] if active else None,
        "acousticSpeakerVerified": False,
        "signalVerified": bool(active),
    }
    summary_path = root / "minecraft-audio-capture-result.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2))
    print("HYPER_DOG_REAL_MINECRAFT_AUDIO", json.dumps(summary), flush=True)
    if len(active) < 3:
        raise AssertionError(
            "SoundManager may have received play() calls, but no real non-silent PCM was recorded"
        )
    start = max(0, active[0] - 1)
    listen = root / "hyperdog-live-minecraft-audio-sample.wav"
    with raw.open("rb") as f, wave.open(str(listen), "wb") as w:
        f.seek(start * SECOND)
        w.setnchannels(CHANNELS)
        w.setsampwidth(BYTES_PER_SAMPLE)
        w.setframerate(RATE)
        w.writeframes(f.read(12 * SECOND))
    print("HYPER_DOG_LISTENABLE_SAMPLE", listen, flush=True)
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
