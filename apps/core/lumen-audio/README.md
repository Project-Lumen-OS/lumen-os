# lumen-audio

The media route rules of every Lumen projector (Z9X, Z9X HB, Play 6, X50 Ultra, X50 Ultra Max, RS30).

- `MediaPath` (plain Java): the vendor audio path a media route plays on (`heard`: Bluetooth, the 3.5 mm jack, USB,
  then HDMI ARC / eARC, then the speaker, never the list order) and when the digital output format goes to the HAL
  again (`formatAgain`: on every switch to another path, as stock).
- `AutoPick` (plain Java) and `AutoOutput` (Android): the output of the choice Auto, the stock rule (libxgimiaudiopolicy
  Egn_setDeviceConnectionState / Egn_getDeviceForMedia): the device connected last while Auto is on plays (Bluetooth,
  the 3.5 mm jack, USB, HDMI ARC); when it goes, the output of the last manual choice while it is connected, else
  Bluetooth, the jack, USB, ARC, the speaker. Each output choice makes that device the preferred media device, as for a
  chosen one. A jack the kernel does not sense (HB) is never an Auto candidate, as on stock.
- `JackOutput` (Android): a 3.5 mm jack the kernel does not sense (HB) connected once and kept, out of the default media
  route (AOSP non-default role, the one SpdifOff uses for SPDIF) unless it is the choice: never removed, so no
  ACTION_HEADSET_PLUG 0 and no AUDIO_BECOMING_NOISY pause players when the user leaves Headphones.
- `SinkOrder` (plain Java) and `HalSink` (Android): the one writer of the vendor HAL sink (`xgimi_audiopath_switch_end`,
  and the speaker pair switch_start + end). A sink gets a ticket where it is decided, with the media route it is for;
  it reaches the HAL only when no newer ticket did and the route is still that one, so a late speaker end never lands
  after a newer A2DP end (HB rig 2026-11-14: HAL sink "speaker" with the route on Bluetooth, A2DP never played).
- `A2dpPick` (plain Java) and `BtA2dp` (Android): the device of a Bluetooth output choice, the stored one while it is
  connected, else the connected one, made the active A2DP device when the stack dropped it (a wired output does that
  in AOSP), so choosing Bluetooth always routes to the connected device.
- `OutputDevice` (plain Java) and `OutputScan` (Android, framework reads only): the outputs of the shared sound output
  page (lumen-menu `OutputPages`): connected ones with their real names (CEC OSD name of the audio system, Bluetooth
  alias, USB product name), paired Bluetooth audio sinks that are not connected, and where media plays now (the media
  route by `MediaPath.heard`). `BtA2dp.connect` is the page's Connect: a normal connect of a paired device.
- `SoundbarMode` (plain Java): CEC System Audio and CEC volume control of an output choice. The soundbar and Auto turn
  System Audio on, every other choice (Speaker too, as stock) leaves it and ARC alone, a local output turns CEC volume
  control off. Turning it off for Speaker (up to HB beta15) ended ARC, and the soundbar chosen later stayed silent
  under a running stream. Choosing the soundbar sends
  `HdmiTvClient.setSystemAudioMode(true)` also while System Audio is already on: the framework sends
  &lt;System Audio Mode Request&gt; on every call (AOSP 14 `SystemAudioActionFromTv`, checked in the Lumen HB
  1.0.10-beta14 services.jar and the stock G0130 one), the soundbar answers &lt;Set System Audio Mode&gt; on, goes back
  to its TV input and ARC is initiated again when it was terminated. The System Audio flag only says what the soundbar
  broadcast last, not which input it listens to (HB rig 2026-11-14, LG SK8 left on its Bluetooth input: silent until
  a reboot asked again).

- `StockVolume` (Android) with `ChipTable`, `ChipKey`, `ChipLevels` (plain Java) and `DspOwned`: the stock volume split,
  one implementation for every model. Stock XGIMI AudioService (VolumeStreamState.setDspVolumes / setDspVolumesToHal,
  the same code on the Z9X, HB, X50 family and, in its Android 11 form, the Play 6) keeps Android's index of every
  stream on every device at the maximum and sends each device's music index to the vendor DSP with that device's key.
  Here every output with a key in the model's table gets AudioService's absolute volume behaviour (`DspOwned`: mixer
  and offload gain at 0 dB for every stream, the UI clicks included, the index still moving with the keys), and each
  output's OWN music index goes to its key at process start, at boot, after an audio server restart and on every music
  volume or mute change (unchanged values skipped). So the volume is applied once, after the Dolby chain, for Android
  PCM, offload and the HDMI input alike (before, the same index was applied in the mixer for PCM, as an offload stream
  gain and in the DSP for HDMI: PCM apps sounded much louder than offload at one index on the Z9X).
- The model data: `devices/<code>/audio/chip_volume.tsv`, shipped as the app asset `lumen_audio/chip_volume.tsv` by the
  model's recipe (`file` line). One row per output: AudioDeviceInfo type, chip key (`spk_volume`, `headphone_volume`,
  `bluetooth_volume` with `;device=` on the Android 14 stocks, `usb_volume`, `xgimi_pcm_volume`), and its vendor path;
  `fixed` rows (stock fixed volume devices), `levels after-end` (the Play 6 HAL keeps no level per sink). HDMI ARC /
  eARC keep AOSP's full volume behaviour: the sound is the stock one (mixer at 0 dB, no chip key, the soundbar's own
  volume), and AOSP 14 forwards the volume keys to a CEC soundbar only for a full volume device.
- `PathSwitch` (Android) with the per-model `VendorAudio` adapter (`VendorOutput`, `VendorCall`): the one vendor audio
  path switch (stock libxgimiaudiopolicy threadInit): on a change `switch_start=P`, 59(100) and the digital format at
  once, 59(P) after `MediaPath.switchDelayMs`, then `switch_end=P` for every path with that path's levels stored first
  (`StockVolume.store`), through `HalSink`; confirmation by `vender.xgimi.audio.ready` and 60, retries, a check again
  on screen on and HAL reconnect, the sink again after an audio server restart or an A2DP sink coming back. Adapters:
  Z9X / HB `AudioPathReporter` (IGmpf 59 / 60), X50 family `AudioPath` (AUDIO 2 / 3), Play 6 `BtAudio` (IGmpf 59 / 60
  and its A2DP force-use fix).

Why: AOSP 14 adds an available HDMI ARC to every media route and returns the route in heap order, so with a soundbar
on ARC the path of connected Bluetooth headphones could be ARC, by heap order (headphones connected and silent), and
the stream volume followed ARC instead of the headphones.

Hosts: every model's vendor adapter installs `PathSwitch` (which installs `StockVolume`), every output choice asks
`BtA2dp` and takes Auto from `AutoOutput` (the HB also keeps its jack with `JackOutput`), the output choices with CEC (HB `cec/HbSystemAudio` through `HbAudioRules`, X50
`AudioOutput` through `AudioRules`) ask `SoundbarMode`. Tests: `bash apps/core/lumen-audio/test/run.sh` (every model's
chip table against its stock rows included), mutants: `python3 apps/core/lumen-audio/test/mutate.py` (on a frozen copy).
- `BootSound` (Android) and `BootSoundRules` (plain Java): Lumen's power-on sound on every model
  (`assets/lumen_audio/poweron.wav`, own work, `tools/brand/boot_sound`). A cold boot plays it from the shared
  `bootanimation.zip` (`part0/audio.wav`, the same bytes) while `persist.sys.bootanim.play_sound` is not 0: the app
  writes the gate `persist.lumen.boot_sound.play` (switch on and the music audible) and `image/lumen_boot_sound.rc`
  copies it. A wake from standby plays it from the persistent projector process 3.5 s after SCREEN_ON (after the
  vendor path check) on the music stream, never while muted, at 0 or over a playing app. The switch is the lumen-menu
  module row `power.on_music` (`persist.lumen.boot_sound`, unset = on as the stock default); the Z9X, HB and Play 6
  adapters give it the unit's stock switch as the first value and turn the stock music off.
