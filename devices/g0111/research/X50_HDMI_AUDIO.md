# X50 (G0111): HDMI, sound output, ARC / eARC, CEC, EDID, game mode, source at power-on

Stock X50 v1.2.64 against the Lumen X50 image (branch `x50-hdmi`, apps/X50Projector), ported from what works on the HB
(G0130, apps/HbProjector = apps/Z9xProjector + HB layer). Offline from the dump; nothing was sent to a projector.

Sources (on xgimi-builder, never copied into git):
- dump `~/devices/g0111_mt9681/work/fs` (system_root, vendor, mnt/vendor/xgimiconfig/G0111/feature/feature.xml);
- jadx of the stock apps `~/devices/g0111_mt9681/work/dec/{release_838_newsettings,release_838_xhplayer,XgimiService,XRMService,XgimiTvInputService}`
  (abbreviations: NS = newsettings `sources/com/xgimi`, XP = xhplayer `sources/com/xgimi/xhplayer`, XS = XgimiService
  `sources/com/xgimi/xgimiservice`, XR = XRMService `sources/com/xgimi/xrmservice`);
- stock live state `full_20261108_1335/getprop.txt`, `live_20261107_0845/settings_all.txt`;
- Lumen t7 DSU trial on the same unit `dsu_t7_debug_20261108/` (props, dumpsys audio / hdmi_control / tv_input).

X50 HAL codes by method name: `devices/g0111/research/aidl_codes.tsv` (every row below is equal to the HB code). None of
them is BANNED or REVIEW in `devices/goat_elegant/research/banned.tsv`.

## 1. What the X50 hardware and stock config say

| fact | evidence |
|---|---|
| 3 HDMI inputs; HDMI 1 has ARC and eARC, HDMI 2 / 3 none | t7 `hdmi_control.txt`: port 1 `arc: true, earc: true`, ports 2, 3 false; t7 `tv_input.txt` HW1..HW3; stock XgimiTvInputService Hdmi1/2/3TvInputService; XP utils/TV.inputToInt HDMI1..3 -> 1..3 |
| no optical (SPDIF) jack | feature.xml `support_spdif=false`; the AOSP policy still lists `Spdif` as attached (t7 `audio_policy.txt` available outputs: Speaker, HDMI ARC, Spdif, XGIMI WLSPK_SPK, WLSPK_BASSSPK), and in t7 media played to {hdmi_arc, spdif} (`dumpsys_audio.txt`) |
| same audio HAL parameters as the Z9X / HB | `vendor/lib64/hw/audio.primary.mt5877.so` strings: `digital_output_format`, `spk_volume`, `xgimi_audiopath_switch_start`, `xgimi_audiopath_switch_end`, `bluetooth_volume` |
| the stock audio server reports the media path to the vendor | `system/lib64/libxgimiaudiopolicy.so`: AIDL IGmAudioManager, `threadInit`, `setXgimiPathForDeviceDelay`, `Egn_getDeviceForMedia`, `xgimi_audiopath_switch_start=%d` / `_end=%d`, `digital_output_format=pcm` |
| Lumen's AOSP audio server does not: the vendor never gets a path | stock `vender.xgimi.audio.ready=1`; t7 Lumen: the property is absent |
| EDID values | feature.xml `edid_2_version_val=1` -> NS signal/SignalEnum: HDMI 1.4 = 1, HDMI 2.0 = 2 (4 only for value 3) |
| game mode 3.0 with ALLM and game enhancement, HFR | feature.xml `display_gamemode_version=3`, `support_hfr=true`, `hgig_mode=1`, `support_amd_free_sync_premium=true` |
| ARC volume optimisation | feature.xml `support_arc_volume_adjust_optimization=1` |
| screen off during a source switch | feature.xml `support_screen_on_off_when_switchsource=true` |
| stock live choices of this unit | `spdif_mode=1` (Passthrough), `vendor.xgimi.system.audio.digital_output_format=bypass`, `volume_music_hdmi_arc=25`, `persist.sys.hdmi.bootsource=2` with `BootSourcePkgName=com.xgimi.atmosphere` |
| no `ro.vendor.xgimi.system.isatv` | `full_20261108_1335/getprop.txt`: absent (stock xhplayer shows its "BACK controls the device" hint only with it) |

## 2. Stock vs Lumen X50, per feature

Status words: **ported** = in X50Projector / the image, host-tested, not yet seen working live; **as before** = already in
the X50 image through org.lumen.tvinput or the framework; **not ported** = with the reason.

| feature | stock X50 v1.2.64 (evidence) | Lumen X50 (this branch) | status |
|---|---|---|---|
| Sound output: Auto / Speaker / Soundbar (ARC, eARC) / Bluetooth / wired / USB | NS voice/VoiceHelper.setAudioDeviceV5 -> GmAudioManager.setAudioDeviceOn(audio, 0) -> XS common/GimiAudioService.setAudioDeviceOn: `AudioSystem.setForceUse(FOR_MEDIA, 1 / 16 / 17 / 4 / 2 / 18)` (XGIMI-only values, only XGIMI's patched audio server takes them); Auto = NS voice/VoiceRepository.setAutoSound -> setAudioDeviceSwitchMode(0) (AUDIO 31) | `AudioOutput`: AOSP `setPreferredDeviceForStrategy(media, device)`, the HB AudioOutputChoice rules (HbOutputRows: Auto first, Speaker always, Soundbar while known, the stored choice always listed); never SPDIF, never the XGIMI wireless speaker ports. AUDIO 31 / 32 not sent (as HB) | ported |
| SPDIF ghost device | the stock audio server routes by its own force-use; no jack (`support_spdif=false`) | `AudioOutput.spdifOff`: SPDIF marked non-default for media (`setDeviceAsNonDefaultForStrategy`), as HB / Z9X SpdifOff: the volume keys then move the real output (ARC soundbar, Bluetooth) | ported |
| Audio format PCM / Passthrough / Auto | NS voice/VoiceRepository.setAutoFormat -> GmAudioManager.setAudioFormat; XS wrapper/mtk/MtkAudio: `spdif_mode` 0 / 1 / 2 -> `digital_output_format=pcm` / `bypass` / `auto` (setParameters + vendor prop); never chosen = PCM (MtkAudio.init) | `AudioOutput.format`: stock order PCM, Passthrough, Auto; never chosen = PCM sent at boot and when ARC appears (HB 1.0.8 parity item 19); row only with a soundbar | ported |
| eARC Auto / Off | NS voice/VoiceRepository.setEarcSound: TV 65 setEARCEnable(z), read 66 getEARCEnableState | row "eARC" (TV 65 / 66, sent only from the row, as stock); the output row says "eARC" while TV 79 reads 3 | ported |
| eARC link -> sound output | XR hdmi/SignalChangeReceiver: getEARCStatus() == 3 at start and GmTv event "eARCstate:1\|0" -> AudioManager.seteARCStatus(true\|false) (XGIMI framework API), the stock AudioService connects the plain HDMI ARC output | `EarcLink` (the HB / Z9X cec/EarcLink): TV 79 watched (1 s steps for 15 s after start, the eARC row, ARC output changes, SCREEN_ON, ARC-port hotplug; 10 s while linked); linked and no ARC output = `setWiredDeviceConnectionState(HDMI_ARC, 1)`, link ended = only our own output removed. The HB "ARC restart" (setArcMode via reflection, never sent by stock) is not ported | ported |
| Vendor audio path report | libxgimiaudiopolicy.so threadInit: AUDIO 2 setAudioOutput(100) at a switch, 1 s, setAudioOutput(P) + switch_end=P; P: speaker 0, ARC 2, BT 3, wired 4, USB 6 (NS voice/VoiceEnum: INTERNAL_SPEAKER 0, ARC 2, BLUETOOTH 3, HEADSET 4, USB 6) | `AudioPath`: the HB AudioPathReporter (2(100), debounce, 2(P), read-back 3 + `vender.xgimi.audio.ready`, switch_start / switch_end around ARC, BT `switch_end=3` + `bluetooth_volume`) | ported |
| Remote volume on the speaker while HDMI plays | stock audio server `spk_volume=N` (setDspVolumesToHal) | `HdmiVolume`: the HB rule (spk_volume follows the music volume only while the speaker is routed and confirmed; never on ARC) | ported |
| Soundbar volume / mute over CEC | framework HdmiControlService (vendor `vendor.cec-default`), t7: System Audio on with the LG soundbar, absolute volume on | framework (as before) + `AudioOutput`: Speaker chosen = System Audio and CEC volume off, Soundbar / Auto = on (HbAudioRules.systemAudioFor / cecVolumeFor) | ported |
| CEC on / off, power with the projector, wake by the device | NS signal/SignalManager.setCecSwitch -> `hdmi_control_enabled`; XS GimiCommonService `tv_send_standby_on_sleep`, `tv_wake_on_one_touch_play` | framework settings, shown in org.lumen.tvinput settings (link row "HDMI inputs") | as before |
| Input switching by the device (One Touch Play), BACK / keys to the CEC device | XR SignalService.initActiveSource (`persist.vendor.xgimi.onetouchplay.enable`), xhplayer key forwarding | org.lumen.tvinput (CEC child inputs, key forwarding, switch on device power-on); X50 has no `isatv` prop, so the one-time BACK hint stays off as on the Z9X | as before |
| HDMI picture: video window follows the shown input | XP utils/TV.switchInputSource: TV 3 switchInputSource(1..3) when InputActivity tunes, switchToMM = (0) when it leaves | `HdmiSource`: the Z9X / HB HdmiSource, ports 1..3 from org.lumen.tvinput's HdmiStateProvider (TV 3, read TV 4 getCurrentInputSource) | ported |
| EDID version per input (1.4 / 2.0) | NS signal/SignalManager.setHdmiVersion -> GmDeviceManager.setEDIDVersion -> TV 40 (1 or 2); read TV 39 (2 = "2.0", else "1.4"); the call has no port: the vendor applies it to the input on screen (the stock row lives on the input's signal page) | row "HDMI version" (TV 39 / 40, values 1 and 2 only), shown and changed only while an HDMI input is on screen (TV 4 reads 1..3) | ported |
| HDMI colour range Auto / Full / Limited | NS signal/SignalManager.getHdmiColorRange / SignalViewModel: TV 63 / 64 (0, 1, 2) | row "HDMI colour range" (TV 63 / 64), HDMI on screen only | ported |
| Game mode Off / On / Auto | NS picture/PictureViewModel setGameMode (147 setGameModeType + 146 setGameModeState, read 148), game speed 76 setGameModeOption (normal 1 with HFR, top speed 3 with HFR), read 77 | row "Game mode" (Off: 147(0) + 146(1); On: 77 read, 76 re-sent, 147(0) + 146(0); Auto: 147(1)), the HB QuickPanelController.onGameMode sequence; read 148 | ported |
| ALLM: when Auto game mode switches on (game enhance timing) | NS picture/PictureRepository.setGameEnhanceTiming: TV 129 setAllmEnable(type == 0), type 0 = on game content (ALLM), 1 = on a game device; read 130 (timing = !130 ? 1 : 0); both choices shown only while the game mode is Auto (PictureFactory); store `allm_enable_state`, default 1:1:1:1 (CustomerEnvTbl.xml); XP api/GameModeApi.getAllmStatus TV 131 | row "Auto game mode: On ALLM signal / On game console" (TV 129 / 130), only in Auto (t16; X50_MENU_STOCK.md t16) | ported |
| Source at power-on (Home / HDMI 1 / 2 / 3 / app) | NS signal/SignalManager.setBootSourcePro: `persist.sys.hdmi.bootsource`, Settings `BootSource`, `BootSourceName`, `BootSourcePkgName`; applied by XR BootControlService | org.lumen.tvinput BootSource + HdmiWatcher (Home or any HDMI port, at power-on and after every wake); the app choice is not offered | as before (app source: not ported) |
| Auto switch to a newly plugged HDMI | XR signal/SignalService + SignalTip, `hdmi_auto_switch` (SystemManager 25 / 26) | org.lumen.tvinput HdmiWatcher | as before |
| Game bar (VRR, game picture, dark boost, HGiG, FPS) | XP GameMenuActivity (GameModeApi) | not ported (HB GameBar is an overlay of HbProjector; needs the X50 GameModeApi per-source calls TV 61 / 62, 145-150, 191 / 192 mapped first) | not ported |
| Dolby / DTS sound effects, Harman | DolbyAudioService (system_ext), feature `support_dts_effect` | not ported (system_ext app lost in a GSI) | not ported |
| Screen off during a source switch | feature `support_screen_on_off_when_switchsource=true` (xhplayer) | not ported (needs the stock xhplayer sequence mapped) | not ported |

## 3. Calls added to apps/X50Projector (allowed_x50projector.tsv)

See the tsv rows marked `x50-hdmi`. Every value a setter may carry is fixed in Gmpf.java and refused otherwise:
TV 3 (0..3), AUDIO 2 (0, 2, 3, 4, 6, 100: never 1 = SPDIF), TV 40 (1, 2), TV 63 (0..2), DISPLAY 76 (0..3),
DISPLAY 146 (0 on | 1 off), DISPLAY 147 (0 manual | 1 auto).

Host checks (2026-11-08, nothing built into an image, nothing sent to a projector): `sh apps/X50Projector/test/run.sh
build/g0111/stage/X50Projector.apk` = ProjectorTest 1988, HdmiAudioTest 626, HomePanelTest 28, RemoteTest 65, all
passed; check_aidl_hal OK 61 calls on 7 interfaces (sources and dex); `sh apps/X50Projector/test/mutate.sh` 81 killed,
0 survived (26 x50-hdmi mutants); `devices/g0111/test/x50_menu_apk_check_test.sh` 4 passed (MENU_APP 61 calls).

## 4. What to check live (only with the owner's OK; read-only first)

1. `getprop vender.xgimi.audio.ready` after boot = 1, `logcat -s X50AudioPath` shows `2(0) sent` and `path 0 confirmed`.
2. Sound > Output with the LG soundbar on HDMI 1: rows Auto, Speaker, Soundbar; `dumpsys audio | grep -A2 "Preferred devices"`;
   Speaker chosen = sound on the speaker and the volume keys move the speaker; Soundbar = the soundbar again.
3. Audio format PCM / Passthrough / Auto with the soundbar: `digital_output_format=` in `X50AudioOut`.
4. HDMI 2 (Box R 4K Plus) on screen: picture present, `X50Hdmi` `TV 3 (2)`; HOME: `TV 3 (0)`.
5. HDMI version and colour range rows on HDMI 2: one step and back, TV 39 / 64 read the chosen value.
6. Game mode Off / On / Auto and ALLM: one step and back, 148 / 130 read back.
7. eARC with the LG soundbar: `logcat -s X50Earc` shows the TV 79 reads; with 3 and no ARC output, `HDMI ARC output
   connected (eARC linked)` and sound on the soundbar.
