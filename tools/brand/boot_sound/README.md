# Lumen power-on sound

`apps/core/lumen-audio/assets/lumen_audio/poweron.wav` (one copy in the repository) is the sound every Lumen
projector plays at power-on (owner pick B "shimmer fifth", 2026-11-19): a
quick rising A major arpeggio that passes from left to right and settles on an open fifth over a warm bass.

- Format: RIFF WAVE, PCM 16 bit, 48 kHz (the vendor audio HAL's own rate, no resampling), stereo, 3.0 s, 576044 bytes.
- Level: -18.0 LUFS integrated, true peak -6.92 dBTP, first and last sample exactly zero (20 ms fade in, 0.6 s fade
  out), bass below 140 Hz in mono, L/R correlation 0.55 (it also works on one speaker: -1.28 dB in mono).
- Made by `gen_poweron.py` (variant B, `python3 gen_poweron.py OUTDIR B`, needs numpy, scipy and pyloudnorm). The
  script synthesises every sound itself (additive and FM tones, shaped noise, a reverb from a synthetic impulse
  response): no samples and no third-party audio.

The public source export leaves the WAV out (`docs/repo/publish_audit.py`: REGENERATE): build it with the
script, the bytes are checked against `metrics.json` values above.

Licence: own work of the Lumen OS project, under the repository licence (Apache-2.0) like the rest of the brand
(`tools/brand`). XGIMI's own power-on music (`bootpoweron.wav` of the stock system) is never shipped.

Where it plays (one file for every model):
- Cold boot: `tools/brand/gen_bootanim.py` stores it as `part0/audio.wav` (Stored) in the shared
  `overlay/v1/bootanimation.zip`, so the AOSP boot animation plays it with the logo while
  `persist.sys.bootanim.play_sound` is not 0.
- Wake from standby: the projector app plays the same bytes from its assets (`apps/core/lumen-audio` BootSound) on
  the music stream at the user's volume, never while muted or at 0.
- The gate: the app writes `persist.lumen.boot_sound.play` and `apps/core/lumen-audio/image/lumen_boot_sound.rc`
  (every image) copies it to `persist.sys.bootanim.play_sound` (a prop the Z9X and Play 6 apps may not set).
- The switch: All settings, General, Power on, "Power-on melody" (lumen-menu module row `power.on_music`).
