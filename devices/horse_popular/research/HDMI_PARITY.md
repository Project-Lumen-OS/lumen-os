# Z9X (G0082) HDMI input parity: stock V6.15.62 against Lumen OS

Status: research finished to the limit of this audit. Every row marked NOT VERIFIED or UNKNOWN is open, see section 12.

Branch `z9x-hdmi-parity`, cut from `origin/integ-1115d`.

Row status words: SAME, DIFFERENT, LUMEN MISSING, LUMEN EXTRA, UNKNOWN (needs a live snapshot), NOT VERIFIED (not read).
"Picture" column: can the row change brightness or colour on screen.

## 0. Method and sources

Stock code (jadx, host `xgimi-llm`, `~/z9xsys/dec/<name>/sources`):

- `xhplayer` = `com.xgimi.xhplayer`, the stock HDMI player (`InputActivity`). Paths below are relative to `com/xgimi/xhplayer`.
- `XgimiTvInputService` = `com.xgimi.tvinput`, the stock TV input service (TIS).
- `XRMService` = `com.xgimi.xrmservice` (audio keys, AI picture mode, HDMI auto switch).
- `release_838_newsettings` = the stock settings app. Paths `signal/`, `picture/`, `device/`, `mutex/`, `core/`, `gmuiapi/` are relative to its `com/xgimi`.
- `SystemUI_release_838`, `XgimiService`.

Stock data (host `xgimi-builder`, unpacked image `~/devices/g0082_mt9952/v61562/fs/`):

- `xgimiconfig_a/G0082/pq/xgimipq.db` (factory picture tables), `tv/tvconfig.db`, `tv/edid_cfg.ini`, `tv/edid*.bin`, `feature/feature.xml`.
- The mutex rules of the settings app: `assets/platform/common.json` inside `system/priv-app/release_838_newsettings/release_838_newsettings.apk` (unzipped, not decompiled).
- `vendor_a/lib/libxgimi.so`: STRINGS ONLY (`strings -a`), no disassembly. Every claim marked "strings" is a log format or a symbol name, not a traced code path.

Stock live data (owner's unit, stock V6.15.62, PS5 4K on HDMI 1): `~/lumen-build-tmp/z9x-picture-parity/stock_live/hdmi_ps5/` (`hal.txt`, `SurfaceFlinger.txt`, `tv_input.txt`, `hdmi_control.txt`, ...), home baseline `stock_live/standard/hal_home.txt`.

Lumen live data with HDMI in front: there is no full snapshot (no `hal.txt`), but a logcat of the same unit exists:
`~/lumen-build-tmp/z9x-picture-parity/live/boot1/logcat.txt.3`, Lumen `ro.z9x.version=1.0.11-beta10`, 2026-11-16 12:47, PS5 3840x2160p@59.9 on HDMI 1. It contains the vendor's own log lines (tags `GM_DISP_*`). Caveat: that boot ran vendor `v6.15.58` (`live/boot1/info.txt`), the stock capture is `V6.15.62`. Lines quoted from it are marked "Lumen log".

Lumen code: worktree paths. `Z9xTvInput` = `apps/LumenTvInput/src/org/lumen/tvinput`, `Z9xProjector` = `apps/ProjectorBase/src/org/lumen/projector`.

Related branches: `hdmi-color`, `hdmi-relink`, `hdmi-bt-audio`, `cec-core`, `arc-return` have 0 commits ahead of `integ-1115d`, their content is in the base read here. `cec-standby-stock` (ff9b52c2, "HDMI-CEC defaults as stock on every model") is 1 commit ahead and is NOT in the base.

## 1. Result in short

Proven so far, ranked. Details and evidence in the sections named.

| # | Difference | Status | Picture | Sound / CEC | Section |
|---|---|---|---|---|---|
| 1 | Game mode on the HDMI input. Stock keeps type AUTO (1) and its player turns game mode OFF for a non console source on every entry. Lumen writes type MANUAL (0) for a console and puts the earlier state back when the session ends, it does not switch game mode off on entry | DIFFERENT | Console: none, same table measured. Non console source: UNKNOWN. If game mode was left on for that input, Lumen shows the game table where stock shows the normal one | no | A |
| 2 | Audio HAL keys `xgimi_source_hdmi=1/0` and `xgimi_source_hdmi_delay=0` on HDMI signal in / out | LUMEN MISSING on the base. Added for the Z9X in this branch (section 13), not tested on a unit | no | sound | 4 |
| 3 | Audio focus (stream 3, GAIN) taken by the HDMI player | LUMEN MISSING | no | sound (other apps keep playing under HDMI) | 4 |
| 4 | CEC wake and standby: stock keeps the AOSP settings at 0 and uses XGIMI's own flags, Lumen uses the AOSP settings | DIFFERENT mechanism | no | CEC | 5 |
| 5 | `setDisplayArea` on signal in and on picture mode / aspect change | LUMEN MISSING | geometry only (4:3 box), not brightness | no | 2 |
| 6 | HGiG / HDR setting row of the stock game menu | LUMEN MISSING | HDR10 games only | no | D.3 |
| 7 | One EDID flip when a powered CEC source shows no picture for 10 s (`HdmiRelink`) | LUMEN EXTRA | link only | no | A |
| 8 | Game page and HDMI mode row off HDMI: stock shows and refuses with a toast, Lumen hides | DIFFERENT presentation | no | no | C |
| 9 | `HDMI_INFOS` session event and `notifyTracksChanged` from the TIS | LUMEN MISSING | no | no | 6 |
| 10 | Property `xgimi.tag.tplayer.in=1`, audio key `xgimi_pq_state` | LUMEN MISSING | no (no native reader that touches the picture found) | no | 2, 4 |
| 11 | Boot source mechanism (stock props + database, Lumen own pref) | DIFFERENT | no | no | 6 |

Measured SAME on entering HDMI with the PS5 (stock snapshot against the Lumen log): vendor source 1, picture mode read 10, saturation 55, sharpness 30, 3DNR 0, MEMC 0, UCD 0, game mode option 0, game state 0 (running). See section 3.

SAME by code: HDMI range (values, call, no automatic write on either system), the picture table key (window 0, the vendor adds input, mode, signal type), the EDID rows, the surface path API.

Not explained by anything proven here: a paler or dimmer picture with a NON console source (TV box). The only proven difference that can touch the picture table of such a source is row 1. Its effect there is UNKNOWN until the snapshot of section 11 is taken on both systems.

## 2. Entering and leaving the HDMI source

### 2.1 Stock, in order

| Step | Stock call | Evidence |
|---|---|---|
| 1 | `InputActivity.onCreate`: wait for the TV input list and the bound API, `initGameMode()`, set `xgimi.tag.tplayer.in=1` | `InputActivity.java:471-535` (prop at :532) |
| 2 | `checkInSignal(inputId)`: `GameMode.beforeSwitchInputSource(id)`, then if there is no signal yet `TV.switchInputSource(id)` = `GmTvManager.switchInputSource(1/2/3)` | `InputActivity.java:1142-1214`, `InputActivity$checkInSignal$1$1.java:51-54`, `utils/TV.java:320-326`, `:338-356` |
| 3 | `TvViewAn.tune(id, passthrough uri)`, save last input | `InputActivity.java:1207-1212` |
| 4 | TIS `onSetSurface`: `setStreamVolume(1.0f)`, `TvInputHardware.setSurface(surface, configs[0])` (1x1 default config when the list is empty) | TIS `data/HdmiSession.java:83-126` |
| 5 | TIS `onSetMain(true)`: CEC `portSelect`, plus `deviceSelect` for a CEC child input | TIS `inputservice/CecHandler.java:21-36`, `:55-70` |
| 6 | `onResume`: `requestAudioFocus(stream 3, GAIN)` | `InputActivity.java:2326-2342` |
| 7 | Vendor event signal in (`status:3`): `signalIn()` = hide the no signal animation, `updateCEC`, `setDisplayArea()` | `InputActivity.java:1437`, `:2169-2177`, `:2571-2596` |
| 8 | TIS on `status:3`: `getHdmiInfo()` (3 retries of 300 ms), `notifyVideoAvailable`, `notifyTracksChanged`, session event `HDMI_INFOS` | TIS `inputservice/TvHandler.java:73-97` |
| 9 | XRMService on `status:3`: `AudioSystem.setParameters("xgimi_source_hdmi=1")` | XRMService `hdmi/SignalChangeReceiver.java:105-106` |
| 10 | `checkGameDevice()`: after the signal dialog time, `GameMode.check(deviceName, inputId)` (section A) | `InputActivity.java:1266-1303`, `GameMode.java:176-345` |
| 11 | XRMService `checkAndSetEffect(pkg)` for the front package: video list gives `xgimi.pkg.effect.mod=1` | XRMService `audio/XrmAudioMannager.java:405-460` (:423) |

xhplayer itself never writes a picture value on entry (no `setPictureMode`, saturation, sharpness, range or EDID call in the package, except user actions in its game menu). The picture of the HDMI source is loaded by the vendor inside `switchInputSource` (section 3).

Leaving, stock:

| Step | Stock call | Evidence |
|---|---|---|
| 1 | `onStop`: close game dialogs, `exitSignal(false)`, `TvViewAn.reset()`, `abandonAudioFocus`, unregister callbacks | `InputActivity.java:2412-2499` |
| 2 | `exitSignal`: `TV.switchToMM()` = `GmTvManager.switchInputSource(0)` | `InputActivity.java:2240-2283`, `utils/TV.java:330-332` |
| 3 | TIS `onSetMain(false)`: after 1000 ms CEC `deviceSelect(0)` | TIS `CecHandler.java:21-36`, `:71-76` |
| 4 | TIS `onRelease` = `onSetSurface(null)` | TIS `data/HdmiSession.java:69-75` |
| 5 | XRMService on signal not 3: `xgimi_source_hdmi=0`, 500 ms later `xgimi_source_hdmi_delay=0` | `SignalChangeReceiver.java:108-116` |
| 6 | `onDestroy`: service `ACTION_HDMI_SHOW_TIPS` to `com.xgimi.config`, clear listeners, `System.exit(0)` | `InputActivity.java:2502-2534`, `:3848-3853` |

Cable pulled on the shown port: `checkHotPlug` gives `exitSignal(true)` (toast, back to source 0, finish), ignored during suspend or boot animation (`InputActivity.java:2193-2215`).

### 2.2 Lumen, in order

| Step | Lumen call | Evidence |
|---|---|---|
| 1 | Source picker starts `org.lumen.tvinput/.PassthroughActivity` | Lumen log 12:47:15.362 `Z9xSource: pick HDMI 1`, 12:47:15.419 |
| 2 | TIS `acquireTvInputHardware`, `setStreamVolume(1.0f)`, `TvInputHardware.setSurface(surface, config)` | `Z9xTvInput/HdmiSession.java:138`, `:152`, `:203` |
| 3 | On hardware acquire the TIS sends `HDMI_EVENT (port, active, "session")` | `Z9xTvInput/HdmiSession.java:184` |
| 4 | `HdmiEventReceiver` fans the event out to `StandbyController`, `HdmiSource`, `HdmiRelink`, `GameProfile` | `Z9xProjector/game/HdmiEventReceiver.java:23-49` |
| 5 | `HdmiSource`: 150 ms later IGmpf 698 `switchInputSourceWithCallingPid(port, pid)` | `Z9xProjector/source/HdmiSource.java:104-107`, `:124-152`. Lumen log 12:47:17.027 `IGmpf 698 (1, pid 2271) -> true` |
| 6 | `onSetMain(true)`: CEC `deviceSelect` or `portSelect` when the CEC control pref is on | `Z9xTvInput/HdmiSession.java:369-391` |
| 7 | `GameProfile`: console detection, then 648 option, IGmpf2 56 type MANUAL, 57 state on | `Z9xProjector/game/GameProfile.java:61-93`, `:1087-1088`. Lumen log 12:47:22.232-22.299 |

Leaving, Lumen: `releaseOnWorker` = `setSurface(null, null)` then `releaseTvInputHardware` (`HdmiSession.java:289-313`), session event with active false, `HdmiSource.decide()` sends 698(0) (`HdmiSource.java:124-152`), `GameProfile` restores what it changed (`GameProfile.java:95-103`), CEC internal select after 1 s when its pref is on (`HdmiSession.java:369-391`).

### 2.3 Row by row

| Row | Stock | Lumen | Status | Picture |
|---|---|---|---|---|
| Vendor source switch to the port | `switchInputSource(n)` from the player, before the tune, only when there is no signal yet | 698 `(n, pid)` 150 ms after the TIS acquired the hardware | SAME result (vendor log `Src change:[0->1]`), different moment. The stock binder code behind `GmTvManager.switchInputSource` is NOT VERIFIED (plain or with pid) | no |
| Vendor source switch back to 0 | `switchToMM()` in `exitSignal` | 698(0) on session end, also self heal on SCREEN_ON (`HdmiSource.java:163-177`) | SAME | no |
| Surface to the hardware | AOSP `TvInputHardware.setSurface` | same API | SAME (section 7) | no |
| Stream volume 1.0 | TIS `HdmiSession.java:116` | `HdmiSession.java:152` | SAME | no |
| `setDisplayArea` (window rect, 4:3 box when the ratio setting is 0) | on every signal in, on picture mode change, on aspect change (`InputActivity.java:2176`, `:1531-1538`, `:2571-2596`) | never called on the Z9X (only `Play6Gmpf.java:132` in the repo) | LUMEN MISSING | geometry only. NOT VERIFIED whether the vendor needs it after an aspect change |
| `xgimi.tag.tplayer.in=1` | `InputActivity.java:532` | no writer | LUMEN MISSING | no. Only reader found in the image is `system/lib64/libbootanimation.so` (strings) |
| `vendor.xgimi.hdmi.playing.state=1` | no Java writer in any stock decompile. String present in `vendor/lib(64)/hw/audio.primary.mt5877.so` and `vendor/lib/libporting.so` | same vendor | UNKNOWN on Lumen (snapshot) | no |
| `xgimi.pkg.effect.mod=1` | XRMService, package list based (`XrmAudioMannager.java:423`) | written by `AiSoundEngine` (`PROP_PKG_MOD`) | value with HDMI in front UNKNOWN (snapshot). That the stock list holds xhplayer is NOT VERIFIED | no |
| Audio focus | `requestAudioFocus(3, GAIN)` in `onResume`, abandon in `onStop` | none in `Z9xTvInput` or `lumen-hdmi` | LUMEN MISSING | no |
| Audio keys | section 4 | section 4 | LUMEN MISSING | no |
| Game mode check | section A | section A | DIFFERENT | section A |
| Tips service to `com.xgimi.config` on destroy | `InputActivity.java:2502-2534` | none | LUMEN MISSING, UI hint only | no |

## 3. Picture on the HDMI source

### 3.1 The stock snapshot facts, each explained

| Fact (stock, PS5) | Who writes it, when | Lumen | Status |
|---|---|---|---|
| `getCurrentInputSource()` = 1 | xhplayer `TV.switchInputSource` (section 2.1 step 2) | 698(1), Lumen log `Src change:[0->1]` | SAME |
| Picture mode read 10 while home is Standard | The vendor keeps the picture mode per input. Its key is (window, input, mode, video type), section B. Vendor log format `Src[%d->%d], Pic[%d->%d]` | Lumen log 12:47:17.037 `getPictureMode ... enPicMode=10` right after the switch | SAME (measured) |
| Saturation 55, sharpness 30, 3DNR 0, MEMC 0, UCD 0 | Not written by any app. They are row `u8gamepicMode 0` of the factory table `tbl_GamePicMode_Setting` (50, 50, 55, 30, NR 0, colour temp 1, MEMC 0, dlc 2, UCD 0, hsl 6, gamma 4). The vendor loads them in `_setPictureMode` inside `switchInputSource` because game mode is running on that input | Lumen log 12:47:16.593 `PQCfg:Src/Dlp/PicMode=[1,0,4], Br/Con/Sat/Sharp=[50,50,55,30], nr/memc=[0,0], game/pc/film=[1,0,0], dlc/ucd/hsl/gamma=[2,0,6,4]` | SAME (measured, vendor 6.15.58) |
| AISR type 0 (home 1) | Vendor, inside `switchInputSource` (strings `SetPicture=%d, %d, IntPic=%d, Aisr=%d,%d`). Factory default `aisr_switcher=0` for the game mode (`settings_default` of `xgimipq.db`) | no line captured | UNKNOWN (snapshot IGmpf2 321) |
| Game mode option 0 | xhplayer game flow, section A | Lumen log `648=BASIC` | SAME |
| `getGameModeProp` = "true 0,1,0" | Field order state, type, option (`Gmpf2Base.java:79-90`, stock `DisplayManager.setGameMode` in `SystemUI_release_838/.../gmuiapi/manager/DisplayManager.java:3690-3717`). State 0 = running, type 1 = AUTO. Home reads "1,0,0" = off, manual: the property is per input | Lumen log 12:47:17.248 `getGameModeProp Type/State/Opt= 0 / 0 / 0` = running, MANUAL | DIFFERENT (type), section A |
| HDMI range 0 | Vendor default, saved per input. No stock app writes it except the user's settings row (section D) | no write on entry | SAME code. Value on Lumen UNKNOWN (snapshot IGmpf 634) |
| EDID version 1 | Raw vendor value 1 = HDMI 2.0, the "Enhanced" row. gmuiapi adds 1 on read and subtracts 1 on write (`gmuiapi/manager/GmTvManager.java:141-155`), the menu value for HDMI 2.0 is 2 (`signal/SignalEnum.java:54`), and `edid_2_version_val` is 1 = 2.0 (`feature.xml:70-71`). Written only by the user's settings row | Lumen menu writes the same raw 0 / 1 (`Z9xProjector/panel/PanelRules.java:184-190`) | SAME rows. Value on Lumen UNKNOWN (snapshot IGmpf 661) |
| MEMC 0 | part of the game table row above | measured, `nr/memc=[0,0]` | SAME |
| Focus `com.xgimi.xhplayer/.InputActivity` | the stock player | `org.lumen.tvinput/.PassthroughActivity` | DIFFERENT app, expected |

### 3.2 Picture features on HDMI

| Feature | Stock | Lumen | Status | Picture |
|---|---|---|---|---|
| AI scene (AIPQ) | XRMService `utils/AIPQ.java`: monitor thread every 1.5 s, acts only when the user picture mode is 10..17 (:309), always window 0. On HDMI (source 1, 2, 3) it returns before the toast broadcast (:197). Skips Dolby Vision, IMAX, HDR Vivid auto (:223, :294) | `apps/core/lumen-aipq`, `Z9xProjector/AiScenePolicy.java` (listens to the same vendor event `picturemode:N`). Its header says that an AI mode left at scene 10 instead of the default scene 16 was "the pale picture of the reports" on Lumen 1.0.8 | NOT VERIFIED on HDMI (Lumen side not compared in this audit). Check with the snapshot: IGmpf 666 with a TV box on HDMI must read the same on both systems | yes if it differs |
| Game mode auto switch | section A | section A | DIFFERENT | section A |
| ALLM, VRR | section A | section A | section A | link |
| HDR / Dolby Vision on signal change | Vendor signal thread reloads the picture itself (strings `HdrEvent type change: %d->%d`, `bReloadPic=%d, bReloadASR=%d, bSendHdrEvent=%d`, `reload PQ`). xhplayer only shows a Dolby Vision badge on event 13 `Hdrtype:2` (`InputActivity.java:1460-1465`) and skips the game check on Dolby (`GameModeApi.java:560-563`) | No Lumen code is in that path, the same vendor does it | SAME by construction. NOT VERIFIED live | yes if it differed |
| HDMI range | section D | section D | SAME code | yes |
| Colour space | Vendor on picture mode change, Lumen log `CBForPicModeChange will set cs to 4-4-4`. Stock snapshot `getDispColorSpace` = 4 | same vendor | SAME by construction | yes if it differed |
| EDID choice | section A | section A | section A | link |

## 4. Sound of the HDMI input

| Row | Stock | Lumen | Status |
|---|---|---|---|
| `xgimi_source_hdmi=1` on signal in (`status:3`) | XRMService `hdmi/SignalChangeReceiver.java:105-106` | no sender in `apps`, `devices/horse_popular` | LUMEN MISSING |
| `xgimi_source_hdmi=0` on signal out or unstable, on event 4, on hot plug out of the current port | `SignalChangeReceiver.java:108`, `:122-123`, `:125-130` | no sender | LUMEN MISSING |
| `xgimi_source_hdmi_delay=0`, 500 ms after signal out | `SignalChangeReceiver.java:109-116` | no sender | LUMEN MISSING |
| eARC state to the audio manager | at register `getEARCStatus()==3`, then event 11 `eARCstate:1` (`SignalChangeReceiver.java:62-66`, `:133-139`) | Lumen has its own eARC watcher (Lumen log tag `Z9xEarc`) | NOT VERIFIED (not compared) |
| Stream volume of the HDMI stream | 1.0 | 1.0 | SAME |
| Audio focus | section 2.3 | none | LUMEN MISSING |
| Sound effect property `xgimi.pkg.effect.mod` | section 2.3 | section 2.3 | UNKNOWN |
| `xgimi_pq_state=1/0` from the AI picture monitor | XRMService `utils/AIPQ.java:146-148` | not sent on purpose (`apps/core/lumen-aipq/src/org/lumen/aipq/AiPq.java:18`) | LUMEN MISSING, no effect: no file under `vendor` or the system libraries of the stock image holds that string |
| `xgimi_audio_source_change`, `xgimi_audio_EQ_change` | Listed as never sent by Lumen in `SYSTEM_PARITY.md` gap 3. Their stock writers were NOT traced in this audit. The string `xgimi_audio_source_change` is in `audio.primary.mt5877.so` | no sender | LUMEN MISSING (from the earlier report), writer NOT VERIFIED |
| Routing to speaker / ARC / eARC / Bluetooth | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED |

What the audio HAL does with `xgimi_source_hdmi` is not known (string hit in `audio.primary.mt5877.so` only). It is the stock signal that HDMI audio is live, so it is the first candidate for the HDMI sound reports. Not a picture input.

The vendor event stream that stock uses for these keys exists on Lumen: IGmpf 702 `setGmTvListener`, registered by `Z9xProjector/AiScenePolicy.java:41` (`apps/core/lumen-gmpf/src/org/lumen/projector/hal/GmpfBase.java:127-135`). This branch uses it to send the three `xgimi_source_hdmi` keys on the Z9X (section 13).

## 5. CEC

Stock live settings of the AOSP CEC service (`hdmi_control.txt` of the stock capture, lines 11-22, 44): `hdmi_cec_enabled` 1, `hdmi_cec_version` 6, `routing_control` 0, `power_control_mode` to_tv_and_audio_system, `system_audio_control` 1, `system_audio_mode_muting` 1, `volume_control_enabled` 1, `tv_wake_on_one_touch_play` 0, `tv_send_standby_on_sleep` 0, `set_menu_language` 1, `earc_enabled` 1. Ports (lines 49-50): 1 = 0x1000 with CEC, ARC, eARC. 2 = 0x2000 with CEC only. Active source (0x08, 0x1000) = the PS5.

| Row | Stock | Lumen | Status |
|---|---|---|---|
| Input shown: route the CEC bus to it | TIS `CecHandler` msg 1: `portSelect(portId)`, plus `deviceSelect(logicalAddress)` for a CEC child input (`CecHandler.java:55-70`) | `HdmiSession.onSetMain(true)`: `deviceSelect` or `portSelect`, only when pref `CEC_CONTROL` is on (`HdmiSession.java:369-391`) | SAME when the pref is on. Pref default NOT VERIFIED |
| Input left: back to the internal source | msg 0 after 1000 ms: `deviceSelect(0)` (`CecHandler.java:71-76`) | after 1 s `CecHelper.selectInternal`, only when pref `CEC_INTERNAL_ON_EXIT` is on | SAME when the pref is on. Pref default NOT VERIFIED |
| Follow the CEC active source | msg 2 `setCurrentActiveSourceToCec`, retry 500 ms (`CecHandler.java:77-94`) | NOT VERIFIED | NOT VERIFIED |
| Remote keys to the source | only for CEC child inputs, `HdmiTvClient.sendKeyEvent` (TIS `data/HdmiSession.java:161-176`) | key forwarding over CEC (`HdmiSession.java:423-454`) | NOT VERIFIED in detail |
| CEC master switch | settings row sets `setHdmiCecControlEnabled` and with it `setHdmiCecAutoDeviceOffEnabled` and `setHdmiCecAutoWakeupEnabled` (`signal/SignalRepository.java:904`, `:920`, `:1038-1040`) | `setHdmiCecEnabled` (`apps/core/lumen-cec/src/org/lumen/projector/cec/CecSettings.java:85`) | DIFFERENT mechanism |
| Wake by one touch play | AOSP `tv_wake_on_one_touch_play` stays 0. XGIMI's own auto wakeup flag is used (row above). Where that flag is enforced is NOT VERIFIED | AOSP `setTvWakeOnOneTouchPlay` (`CecSettings.java:111`) | DIFFERENT mechanism |
| Standby of the sources on sleep | AOSP `tv_send_standby_on_sleep` stays 0. XGIMI's own auto device off flag | AOSP `setTvSendStandbyOnSleep` (`CecSettings.java:138`) | DIFFERENT mechanism |
| System audio, volume control | both 1 | `setSystemAudioControl`, `setHdmiCecVolumeControlEnabled` (`CecSettings.java:164-167`) | defaults NOT VERIFIED |
| Lumen CEC defaults | | unmerged branch `cec-standby-stock` (ff9b52c2) sets them as stock | not in this base |
| Active source lost | NOT VERIFIED | NOT VERIFIED | NOT VERIFIED |

## 6. Signal change, sleep and wake, boot source

| Row | Stock | Lumen | Status |
|---|---|---|---|
| Signal out | TIS `notifyVideoUnavailable(1)`, unstable `notifyVideoUnavailable(0)` (`TvHandler.java:50-68`). Player shows the no signal animation (`InputActivity.java:2180-2189`) | `notifyVideoUnavailable` with reasons (`HdmiSession.java:270-287`), zero stream configs = cable out (`:248-254`) | SAME idea, source of the event differs (stock vendor event, Lumen stream configs) |
| Signal in | TIS `notifyVideoAvailable`, `notifyTracksChanged`, session event `HDMI_INFOS` (`TvHandler.java:73-97`) | `notifyVideoAvailable` only | LUMEN MISSING for `HDMI_INFOS` and tracks, UI info only |
| Resolution or HDR change | Vendor reloads the picture (section 3.2). The stream config callback calls `updateStream` (TIS `BaseTvInputService.java:79-91`) | `apply()` reopens the stream when the configs change (`HdmiSession.java:227-268`) | SAME idea |
| Conflict event | `exitSignal(true)` (`InputActivity.java:1412`) | NOT VERIFIED | NOT VERIFIED |
| Auto switch to a newly plugged source | XRMService `onSignalIn`, only when `getHdmiAutoSwitch()` is on, not during setup or sleep (`SignalChangeReceiver.java:169-215`) | NOT VERIFIED | NOT VERIFIED |
| No signal shutdown | `Settings.Global NoSignalShutdown`, default 5 (`signal/SignalManager.java:402-407`). Who enforces it is NOT VERIFIED | NOT VERIFIED | NOT VERIFIED |
| Sleep with HDMI in front | hot plug ignored during suspend (`InputActivity.java:2193-2215`) | vendor switch held during lamp only standby, self heal on SCREEN_ON (`HdmiSource.java:139-143`, `:163-177`) | NOT VERIFIED in detail |
| Boot source | props `persist.sys.hdmi.bootsource`, `persist.sys.bootanim.alwayswait`, database keys `BootSource`, `BootSourceName` (`signal/SignalManager.java:171-184`). Snapshot `xgimi.tag.bootsource.in=1` | own pref, opens the port 3 s after power on (`Z9xProjector/source/BootSource.java:49-78`) | DIFFERENT mechanism, no picture effect |

## 7. Surface path of the HDMI video

Stock, from the live dump:

- `SurfaceFlinger.txt:583-594`: layer `SurfaceView[com.xgimi.xhplayer/com.xgimi.xhplayer.InputActivity](BLAST)#498`, composition type SIDEBAND (5), a sideband stream handle, no buffer, bounds 0 0 1920 1080, dataspace UNKNOWN (0), identity colour transform, alpha 1.
- `SurfaceFlinger.txt:1276-1285`: HWC layers, `#498` SIDEBAND full screen, the activity window `#492` DEVICE on top.
- `SurfaceFlinger.txt:1293` HWC capability SIDEBAND_STREAM. `:1301` timing 3840x2160, panel 3840x2160, display region 1920x1080, colour mode 0. `:7` no wide colour.
- `tv_input.txt:42` session of `com.xgimi.tvinput/.inputservice.Hdmi1TvInputService/HDMI100008`. `:72` `TvInputHardwareInfo id=1 type=9 hdmi_port=1`, three stream configs.

So the stock video never goes through SurfaceFlinger buffers. The 1920x1080 is the UI layer size, the video plane is the vendor's (4K timing).

Lumen: the same AOSP API (`TvInputHardware.setSurface`, `Z9xTvInput/HdmiSession.java:203`), so a sideband layer is expected. Status SAME by code, UNKNOWN live: no SurfaceFlinger dump of Lumen with HDMI in front exists (section 11).

## A. Game mode and the HDMI handshake (owner addition)

### A.1 What stock Java does

- Model flags: `display_gamemode_version` 3, `support_hfr` true (`feature.xml:34`, `:16`). So the GameMode3 branch of the player runs.
- Detection: `GameMode.check(name, inputId)` (`GameMode.java:176-345`). Skipped on a Dolby Vision signal (`GameModeApi.java:560-563`). A game device is a CEC or EDID name that contains an entry of `/data/vendor/xgimi/stream_game_list.json` or of the built in list (`GameModeApi.java:51`, `:567-620`, `:717-758`).
- With type AUTO (`GameMode.java:269-305`): a game device, or ALLM from the source when the ALLM switch is on, gives option "auto". No game device and no ALLM gives "close".
- With type MANUAL (`GameMode.java:307-316`): state running gives the "on" dialog once.
- `GameModeService.dealGameModeDialog` (`service/GameModeService.java:242-292`): "on" / "auto" call `setStandardMode` or `seTopSpeedGameMode` (`:581`, `:606`, `:642`, `:712`, `:950`, `:980`, `:1003`, `:1073`), "close" calls `setGameModeState(1)` = game mode off (`:276-277`).
- Game mode option values (`api/GameModeApi.java:208-354`): 0 standard, 1 standard + high frame rate, 2 top speed + high frame rate, 3 top speed.
- After the option, only with the game menu feature: 200 ms later `setVRRModeEnable(saved state, source)` and `setGamePictureMode(saved mode, source)` (`GameModeApi.java:426-437`, `:761-782`).
- ALLM events from the vendor: event 19 and 20 start "auto" or "close" (`InputActivity.java:1473-1528`).
- NO stock Java code calls `setEDIDVersion` for game mode. The only caller in all decompiled apps is the settings row (`signal/SignalRepository.java:955-962`, `signal/SignalManager.java:136-141`). xhplayer has no EDID and no range call at all.

### A.2 What the vendor does (the handshake part)

The owner's hypothesis is CONFIRMED in this form: game mode changes the EDID, and it happens inside the vendor HAL, not in Java.

- Strings of `libxgimi.so`, `Msrv_Tv_Control::_setEdidVersion(EN_EDID_VERSION, EN_XGIMI_INPUT_SOURCE, bSave)`: `m_u8GameModeVersion:%d m_bSupportHightRateFrame:%d m_eGameModeState:%d m_eGameOption:%d m_enVrrEnableState:%d m_bAmdFreeSync:%d, m_kstType:%d`, `set edid[%d]:%d for not game mode`, `edid version is same(%d), no need to set again`, `setEdid %d -> %d`. So the EDID file is chosen from the user EDID version, game state, game option, VRR state, FreeSync and the keystone type.
- Proof that it runs, Lumen log on entering HDMI 1: `_setEdidVersion: in enEdidVer:2, src:0, bSave:0`, `m_u8GameModeVersion:3 m_bSupportHightRateFrame:1 m_eGameModeState:1 m_eGameOption:0 m_enVrrEnableState:0 m_bAmdFreeSync:0, m_kstType:1`, `setEdid 0 -> 4`, `out edidVer:4`. The vendor calls it from `switchInputSource` itself.
- Game state, VRR and FreeSync changes also reach the EDID (strings `game mode auto & allm enable, skip set edid when allm=0 to off game mode`, `set vrr mode to %d, source=%d`, `vrr:%d, gamemode off to close vrr/freesync`, `Warning,NOT support HRR, change to: %d`).
- Raw EDID values (as documented in `apps/core/lumen-hdmi/src/org/lumen/hdmi/EdidModes.java:8-21`): 0 = 1.4, 1 = 2.0, 2 = the game mode request, 3 = 2.1.

What the EDID files of the model advertise (`xgimiconfig/G0082/tv/*_2.bin`, parsed with a short script written for this audit, not cross checked with edid-decode):

| File | HDMI Forum block | Max FRL rate | ALLM | VRR range | Size |
|---|---|---|---|---|---|
| `edid14` | none (HDMI 1.4 block only, 340 MHz) | none | no | none | 256 |
| `edid20`, `edid20_non_hrr`, `edid20_120hz` | yes, 600 MHz | 6 | yes | none | 256 |
| `edid21_120hz` | yes, 600 MHz | 6 | yes | 40-120 Hz | 256 |
| `edid21` | yes, 600 MHz | 6 | yes | 40-240 Hz | 256 |
| `edid21_freesync_120hz` | yes, 600 MHz | 6 | yes | 40-120 Hz | 384 (one more block) |
| `edid21_freesync` | yes, 600 MHz | 6 | yes | 40-240 Hz | 384 (one more block) |

So the part of the hypothesis about the FRL rate (3 / 4 / 5 / 6) is NOT supported by the data: every file except `edid14` advertises the same maximum FRL rate 6 and ALLM. What game mode changes through the file is the VRR range (none, 40-120, 40-240), the FreeSync block, and by the file names the 120 Hz timing list (timing lists were not parsed).

NOT VERIFIED: which file the log's index 4 is, the FRL rate the link really trains to, and whether the vendor toggles HPD after writing (symbol `Device_HdmiInput::setHotPlug` exists, no call path read. `apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiLink.java:14` states that the vendor pulls HPD low only when it writes another EDID, that statement was not re checked here). No disassembly was done.

### A.3 Lumen against stock

| Row | Stock | Lumen | Status | Picture |
|---|---|---|---|---|
| Game option, state, type calls reach the same vendor | IGmpf 648, IGmpf2 56, 57 through gmuiapi | same codes (`GameProfile.java:1087-1088`, `panel/Z9xGameRows.java:86-94`) | SAME calls | |
| Type on a console | stays AUTO (1), the player only sets the option and lets the vendor run the state | `GameProfile` writes type MANUAL (0) then state on. Lumen log `setGameModeType type = 0`, `getGameModeProp Type/State/Opt= 0 / 0 / 0` | DIFFERENT | With the PS5 the loaded table is the same (measured) |
| ALLM auto switch | With AUTO the vendor reports ALLM changes and stock reacts (events 19, 20) | With MANUAL the vendor answers `getAllmEnableState: gameType:0(not auto), return false` (Lumen log). Lumen polls IGmpf2 310 itself (`GameProfile.java:61-67`) | DIFFERENT mechanism | link only |
| Non console source | AUTO + no game device gives "close" = `setGameModeState(1)`, game mode off, normal picture table (`GameMode.java:290-295`, `GameModeService.java:276-277`). Done on every entry | `GameProfile` applies only for a console. At session end it puts back what it found: AUTO, else MANUAL + state off (`GameProfile.java:1425-1442`), only while the state is still its own. No switch off on entry was found (the file was read in part: first 190 lines of 1400+ and greps). The state the vendor holds for that input when a TV box is shown is UNKNOWN | UNKNOWN | YES if the state differs: game table (saturation 55, sharpness 30, no NR, no UCD, AISR off, MEMC off) against the normal table |
| VRR and game picture mode re applied after the option | `GameModeApi.java:426-437` | Lumen log shows a vendor `setVRRModeEnable state:0->0` 380 ms after the switch, caller NOT VERIFIED | NOT VERIFIED | |
| EDID on game change | vendor | same vendor | SAME by construction | link |
| EDID flip without a user action | none | `HdmiRelink`: one EDID flip when a powered CEC source gives no picture for 10 s (Lumen log `Z9xRelink: armed`, `apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiRelink.java:223`, `:248`) | LUMEN EXTRA | link only, then written back |
| Menu game mode Off / On / Auto | `DisplayManager.setGameMode(0/1/2)` (`DisplayManager.java:3690-3717`) | `Z9xGameRows.java:86-94` same triples | SAME | |

## B. Per input picture tables (owner addition)

### B.1 Where they are

Factory tables, read only, `/mnt/vendor/xgimiconfig/G0082/pq/xgimipq.db`:

| Table | Key | Rows | Content |
|---|---|---|---|
| `tbl_PicMode_Setting` | (`u8inputSrc`, `u8dlpLumensMode`, `u8picMode`) | 581 = 4 inputs x 5 lumens modes x 29 picture modes + 1 placeholder | brightness, contrast, saturation, sharpness, nr, colortemp, memc, gameMode, pcMode, filmMode, dlc, ucd, hsl, gamma |
| `tbl_GamePicMode_Setting` | (`u8inputSrc`, `u8gamepicMode` 0..4) | 20 | the game picture modes, plus AiContrast and darkboost |
| `tbl_ClorTemp_Setting` | (`u8inputSrc`, `u8dlpLumensMode`, `u8colorTempMode`) | 60 | colour temperature |
| `tbl_NonLine_Setting` | (`u8inputSrc`, `u8nonlineType`) | 32 | non linear curves |

Inputs 0, 1, 2, 3 (0 = local playback, 1..3 = HDMI ports by `utils/TV.java:338-356`. The meaning of 0 inside the table is by analogy, NOT VERIFIED).

The factory defaults are IDENTICAL for all four inputs: a self join of input 0 against 1, 2, 3 finds 0 differing rows in `tbl_PicMode_Setting`, and `tbl_GamePicMode_Setting` is the same per input. So a fresh unit has no "paler HDMI default" in the tables.

User store, written at run time: `/data/xgimidisp.db` (strings `xgimipq.db`, `/data`, `xgimidisp.db` in `libxgimi.so`. The file is present on Lumen: `-rw------- root system /data/xgimidisp.db` with `-wal` and `-shm`). Tables `disp_settings(owner, name, val_int, val_txt, val_float, val_blob)` and `disp_custom`.

It is on the userdata partition. It is NOT shared between a stock install and a Lumen install, each starts from the factory defaults.

### B.2 The key that selects a table

`owner` = `window-input-mode-video_type` (strings `will query setting for key: window=%d, input=%d, mode=%d, video_type=%d, field=%s`, format `%d-%d-%d-%d`).

- window: the only part an app passes. Stock passes literal 0 in every picture get / set (XgimiService `wrapper/xgimi/XgimiVideo.java:82-222`, `wrapper/mtk/MtkVideo.java:20-148`, XRMService `utils/AIPQ.java`, newsettings, SystemUI, xhplayer. Wrapper parameter name `window` / `eWindow`).
- input, mode, video type: taken by the vendor from its own state (current input set by `switchInputSource`, current picture mode, current signal type).
- video type: remapped by `settings_key_field_remapping`, (3|4|6|7|8|9) become 3. Which number is SDR, HDR10, HLG, Dolby Vision is NOT VERIFIED.
- Which fields are stored per key: `settings_field_info`. Brightness, contrast, saturation, sharpness, lumens mode, memc per (input, mode, video type) for the listed modes. White balance, colour space, colour enhance, hsy, gamma level, local contrast, dynamic contrast, AI contrast, dynamic black, frame multix, force 120 Hz, nr, AISR switch for the pro modes too. `hdr_dy_tone` only for video type 1.
- Shared across modes: `lumens_level` and `lens_mode` (`settings_sto_mapping`, `*_general`), `force_120hz` always.

So the owner's premise holds: the tables are per input, per picture mode and per signal type. With 4 inputs x picture modes x video types it is far more than 6 per input.

Lumen log shows the vendor building the key on Lumen: `key: window=0, input=0, mode=1, video_type=0` and `video_type=1` (local), `key: window=0, input=1, mode=4, video_type=0` and `mode=16` (HDMI 1).

### B.3 Does Lumen use the same key

| Row | Stock | Lumen | Status |
|---|---|---|---|
| Leading int of picture get / set | literal 0 (window) | `WINDOW_MAIN = 0` (`apps/core/lumen-gmpf/src/org/lumen/projector/hal/Gmpf2Base.java:28`, uses in `GmpfBase.java:319-360`, `Gmpf2Base.java:207-242`) | SAME |
| Input part of the key | chosen by the vendor | chosen by the vendor | SAME. A Lumen menu cannot name another input in these calls, it reads and writes the table of the input on screen, like stock |
| Colour space read `getDispColorSpace(source, mode)` | source from `getCurrentInputSource()` (newsettings `picture/PictureManager.java:88-145`) | source = the 696 read, 1 / 2 on HDMI, else 0. Mode = the 666 read (`Z9xProjector/panel/ProPicture.java:285-290`, `GmpfBase.java:405-406`) | SAME |
| `getAllmStatus(source)` | `TV.inputToInt(input)` (`GameModeApi.java:548-551`) | the port (`Z9xProjector/panel/Z9xGameIo.java:146`, `game/GameProfile.java:739`). Lumen log `getAllmStatus source = 1` | SAME |
| Game picture mode get / set `(mode, source)` | source of the input (`GameModeApi.java:761-782`) | `Z9xProjector/hal/Gmpf2Client.java:76-79` source 1 / 2 | SAME by signature, call sites NOT VERIFIED |
| VRR per source, HDR Vivid state per source | `getVRREnableState(src)`, `getHdrVividState(src, mode)` (XRMService `AIPQ.java:220`) | NOT VERIFIED | NOT VERIFIED |
| Factory tables | read only partition | The repo has no copy and no writer of `xgimipq.db` or `xgimidisp.db` for the Z9X (the names appear only in `apps/HbProjector` and `apps/X50Projector` sources) | SAME |

## C. Menu rows on the HDMI source (owner addition)

PARTIAL. Only the rows whose stock rule was read are listed. Stock has three layers of rules.

### C.1 Stock rules

Layer 1, the picture settings page refusal (`core/SettingMutexManager.java:969`): while `getPictureMode() == 4` or the game state is 0 (running), the picture settings page refuses to open with a toast. Lumen's `apps/core/lumen-menu/src/org/lumen/menu/PictureSettingsRule.java` documents the full stock order (game mode on, then picture mode AI = exactly 10, then Performance = 5, the last two not while Dolby Vision plays). Only the game line (:969) and the Performance line (:1001) were re read in this audit.

Layer 2, the mutex rules. `mutex/config/MutexConfig.java:36-57` loads `platform/<chip>.json`, falls back to `platform/common.json`, then adds `device/<product name>.json`. The unit reports chip `mtk9681` and product `Product_dapeng`. The APK has neither `platform/mtk9681.json` nor `device/product_dapeng.json`, so `common.json` is the rule set (46 functions). What `getCurrentChip()` returns was not read, the fallback is inferred from the file list.

Rule format: function <= list of (condition, action). Action 2 = refuse with a toast (`mutex/DispatchFun.java:122-123`), 4 = confirm dialog (:128-135), 5 = toast through the caller (:137-150). No action hides a row: stock rows stay visible and refuse.

Conditions (`mutex/helper/DisplayHelper.java`): `dolbyVideo` = `getHdrType() == 2` (:96-97). `gameModeBasic` = picture mode 4 or game state 0 (:130-131). `gameModeFast` = game running with option 2 or 3 (:119-127). `hdmiSignalSource` = the current source is NOT HDMI (:178-182). `isHdrOn`, `isHighFrameRateOn`, `isHDMI120Hz`, `imaxRunning` (:162-175).

| Stock row (function) | Refused while | Lumen | Status |
|---|---|---|---|
| Game mode (`gameMode`) | Dolby Vision signal, 3D, IMAX playing, source is not HDMI | Game page refusal "Dolby Vision, 3D, off HDMI" (`Z9xProjector/panel/Z9xMenuRules.java:359-362`). Off HDMI the row is hidden and only a hint is shown (`:185-189`) | SAME for Dolby Vision, 3D. Off HDMI: stock shows and refuses, Lumen hides. IMAX NOT VERIFIED |
| HDMI version (`hdmiVersion`) | source is not HDMI | hidden off HDMI (`Z9xMenuRules.java:258-264`), rule `PanelRules.hdmiMode` (`:417-418`) | stock shows and refuses, Lumen hides |
| HDMI colour range | no rule in `common.json`, never refused by the mutex. Shown with the page (`signal/SignalRepository.java:184-195`) | hidden off HDMI (`Z9xMenuRules.java:257-264`) | Stock visibility off HDMI NOT VERIFIED |
| Picture mode (`pictureMode`) | game mode running, Dolby Vision, IMAX playing, 3D | `PanelRules.pictureMode(rules, gameModeOn)` (`Z9xMenuRules.java:389-391`) | game mode SAME by the call. Dolby Vision, IMAX NOT VERIFIED |
| Picture sliders, colour temperature, MEMC, AI contrast, super resolution, pro page, reset | layer 1 (game mode running, AI, Performance) | `PictureSettingsRule` on each press (`Z9xMenuRules.java:355-357`, `:373-405`) | SAME rule. Stock refuses the page entry, Lumen refuses each row |
| 3D (`threeDimension`) | game mode running, top speed, HDR on, high frame rate on, HDMI 120 Hz, IMAX, nothing playing, 4K without 3D support | NOT VERIFIED | NOT VERIFIED |
| Keystone rows, zoom, rotation (`autoKeystone`, `manualKeystone`, `morePointKeystone`, `cameraKeystone`, `pictureZoom`, `screenRotation`, `autoWallPositioning`, `zoomDisplacement`) | game mode top speed (`gameModeFast`) | `PanelRules.java:61-63` (`KstGameLock`) | SAME idea, row list NOT VERIFIED |
| Master colour, IMAX enhanced (`masterColor`, `masterColorMode`, `iMaxEnhanced`) | Dolby Vision | NOT VERIFIED | NOT VERIFIED |

Layer 3, the stock game menu of the player (`gamemenu/bussiness/MutexManager.java`):

| Stock game menu row | Refused while | Lumen | Status |
|---|---|---|---|
| HDR setting (Off / Brightness mapping / HGiG) | HDR10 is not playing (:70-77) | no HGiG row on the Z9X (section D.3) | LUMEN MISSING |
| FreeSync | video ratio 6 or 7 (:81-88) | NOT VERIFIED | NOT VERIFIED |
| Aspect ratio | VRR is on, or the mini map is showing (:92-108) | `PanelRules.aspect(rules, gameModeOn)` (`Z9xMenuRules.java:419-421`) | NOT VERIFIED |
| Mini map | ratio 6, 32:9 without the map, no stable signal (:40-65) | no row | not compared |

Layer 4, the vendor's own refusals (data of `xgimipq.db`, same vendor on both systems): `settings_picmode_srcruler` (picture modes allowed per HDR type: Dolby Vision 21, 22, 23. HDR type 6: 27, 21, 22, 23, 2, 29), `settings_interceptor` (`memc` refused while `frame_multix` is 1, `lumens_level` and `cs` refused while `lens_mode` is 1), `settings_t_chain` (`frame_multix` 1 sets `memc` 0 and `force_120hz` 6), `settings_fireware_picmode` (modes 19, 20, 3, 8 and 30+ refused).

### C.2 The three lists asked for

- Lumen shows a row that stock hides: none found.
- Lumen hides a row that stock shows: the game mode row and the HDMI version row while the source is not HDMI (stock shows them and refuses with a toast). Presentation only.
- Lumen leaves a row editable that stock locks: none PROVEN. Not checked: picture mode and game mode during IMAX playback, picture mode during Dolby Vision, aspect ratio while VRR is on, the 3D row.
- Stock row with no Lumen row: HGiG / HDR setting of the game menu.

## D. HDMI range, per signal tables, HGiG (owner addition)

### D.1 HDMI range

| Question | Answer | Evidence |
|---|---|---|
| Values | 0 auto, 1 full, 2 limited | `signal/SignalEnum.java:55-57` |
| Who writes it in stock | ONLY the user's row in the settings HDMI page. No other caller in newsettings, xhplayer, SystemUI, XgimiService, XRMService, the TIS | `signal/SignalRepository.java:965-1027` to `device/GmDisplayManager.java:3358-3368` to `gmuiapi/manager/GmTvManager.java:189-190` `setHdmiRange(byte)` |
| Who reads it | the same page | `signal/SignalManager.java:191-193` |
| Per input | yes, the vendor saves it per input source | symbols `Msrv_DataBase_Manage::saveHdmiRangeType(HdmiRange, EN_XGIMI_INPUT_SOURCE)`, `getHdmiRange(EN_XGIMI_INPUT_SOURCE)` (strings) |
| Re applied by Java on entry or signal change | no, there is no such call | grep over all stock decompiles |
| Default | 0 (auto) on the stock unit that never changed it | `hal.txt:35` |
| What auto does with the AVI infoframe | NOT VERIFIED (vendor native `Device_HdmiInput::setHdmiRange`, no log string found). The vendor does read the AVI infoframe on entry (Lumen log `getAttr E_MI_EXTIN_ATTR_TYPE_HDMI_AVI_INFO_FRAME, eContentType: 0, eColorimetry: 0, eExtColorimetry: 0`) | |

Lumen:

| Row | Lumen | Status |
|---|---|---|
| Values | `HdmiRange` AUTO 0, FULL 1, LIMITED 2 (`Z9xProjector/hal/HdmiRange.java`) | SAME |
| Write | IGmpf 635 `setHdmiRange(wire)` from the menu row only, and only when HDMI is on screen (`Z9xProjector/panel/Z9xHdmiRows.java:22-31`) | SAME call and argument. No source index is passed in stock either |
| Read | IGmpf 634 (`panel/Snapshot.java:120`), row shown only on HDMI with a value 0..2 (`panel/Z9xMenuRules.java:257-264`) | SAME |
| Any other writer (entry, signal change, boot) | none in `apps/ProjectorBase`, `apps/core`, `apps/LumenTvInput`, `devices/horse_popular` | SAME as stock |

Conclusion for the pale picture complaint: Lumen never writes the range by itself, so a wrong range can only come from (a) the user's row, (b) the vendor default of a fresh `/data` store, which is the same vendor code as stock. The live value on Lumen is not captured yet (the read at 12:47:17.215 is in the log without its result). It is the first item of section 11.

The branch `origin/hdmi-color` holds nothing beyond the base and no HDMI range change.

### D.2 Pro picture values per input and per signal type

- Stock: stored per (input, picture mode, video type), section B.2. A value set on local playback is a different key from the same row on HDMI. The X50 tester fact holds on this model.
- Lumen pro picture menu: every get / set passes window 0 and the vendor adds the current input, mode and signal type (section B.3, and the key lines of the Lumen log). So it reads and writes the table of the current input and current signal type. Status SAME.
- Lumen at install: ships no copy of the tables (section B.3). Whether any Lumen code writes a picture value at first boot before the user does is NOT VERIFIED.

### D.3 Game mode and HDR tone mapping / HGiG

Stock:

- The only writer of HGiG is the HDR setting row of the game menu: Off = `ToggleHDRDyToneMapping(0, 0)` + `setHGiGMode(false)`. Brightness mapping = `ToggleHDRDyToneMapping(0, 1)` + `setHGiGMode(false)`. HGiG = `ToggleHDRDyToneMapping(0, 0)` + `setHGiGMode(true)` (`gamemenu/bussiness/other/OtherViewModel.java:326-336`). Read back `:341-347`.
- The row refuses unless HDR10 is playing (`gamemenu/bussiness/MutexManager.java:70-77`).
- No other caller of `setHGiGMode` exists in xhplayer or newsettings. So stock Java does NOT re apply HGiG when game mode is entered. The tester note (HGiG has to be enabled again after every switch into game mode) fits that: nothing restores it. Whether the vendor itself resets it on game entry is NOT VERIFIED (`Msrv_Tv_Control::setHGiGMode(u8, u8)`, strings. Vendor debug help: `p2:mode 0(off), 1~15(mode1~15), 16(HDR game auto)`).
- HDR dynamic tone mapping is stored by the vendor only for video type 1 (`hdr_dy_tone`, section B.2). Settings writes it from the picture page (`picture/PictureRepository.java:3332-3336`).

Lumen:

- No HGiG call on the Z9X at all (no match for "hgig" in `apps/ProjectorBase/src` and `apps/core` except a comment in `apps/core/lumen-menu/src/org/lumen/projector/panel/GmModel.java:41`). Status LUMEN MISSING.
- HDR dynamic tone mapping: pro picture row, IGmpf2 270 write, 269 read, window 0 (`Z9xProjector/panel/ProPicture.java:209`, `:300`, `apps/core/lumen-gmpf/src/org/lumen/projector/hal/Gmpf2Base.java:222-227`). Same call as stock.
- On game mode entry Lumen writes neither (no tone mapping call outside `ProPicture`). Status SAME as stock: both do nothing on entry.

## 11. What to snapshot on Lumen with HDMI in front

Same unit, same source, HDMI picture settings untouched. Once with the PS5, once with a non console source (the TV box from the complaints).

1. The picture state script that produced `stock_live/hdmi_ps5/hal.txt` (85 values). The rows that matter: IGmpf 696, 666, 690 (saturation), 688 (sharpness), 692, 694, 684, 668 (MEMC), 670 (3DNR), 651 (UCD), 111 (colour space), 620 (HDR type), 649, 634 (HDMI range), 661 (EDID), 647 (game option), 590. IGmpf2 55 (game mode prop), 285, 287, 321 (AISR type), 326, 328, 330, 269, 306, 307.
2. `getprop | grep -E 'hdmi.playing.state|pkg.effect.mod|tplayer|bootsource'`.
3. `dumpsys SurfaceFlinger` (layer type of the HDMI SurfaceView, expect SIDEBAND), `dumpsys tv_input`, `dumpsys hdmi_control`, `dumpsys audio`, `dumpsys media.audio_flinger`, `dumpsys media.audio_policy`.
4. `logcat -d | grep -E 'GM_DISP_TVCTRL|GM_DISP_TV_CTRLV2|GM_DISP_SCENE_V4|GM_DISP_SCENE_V3'` right after entering HDMI: the lines `PQCfg:Src/Dlp/PicMode`, `_setEdidVersion`, `setEdid`, `getGameModeProp Type/State/Opt`, `Src change`, `Cur GameState`.
5. The same four on STOCK with the non console source (the stock capture so far is PS5 only). This pair answers row 1 of section 1: is game mode off on stock and on (or left over) on Lumen for a TV box.

6. After the audio key change of section 13: `logcat -d | grep LumenHdmiAudio` while entering and leaving HDMI and while pulling the cable, and the HDMI sound itself.

Do not read IGmpf2 252, IGmpf 194 or 275.

## 12. Not verified, in one list

- Everything marked NOT VERIFIED or UNKNOWN above.
- All `libxgimi.so` claims are strings only.
- The Lumen log is from vendor 6.15.58, the stock capture from 6.15.62.
- `GameProfile.java` was read only in part (first 190 of 1400+ lines, plus greps).
- CEC: where XGIMI's auto wakeup and auto device off flags act, active source lost, Lumen pref defaults (section 5).
- Audio routing to ARC, eARC, Bluetooth and the writers of `xgimi_audio_source_change`, `xgimi_audio_EQ_change` (section 4).
- Section C covers only the rows listed. The EDID timing lists and the FreeSync block were not parsed.
- The AI scene path on HDMI was not compared.

## 13. Code changes in this branch

One change, commit 7635e303 "Z9X: send the stock HDMI audio keys to the audio HAL".

- `apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiAudioKeys.java` (plain Java): the stock rule of `SignalChangeReceiver.java:100-132`. Event 0 `status:3` sends `xgimi_source_hdmi=1`. Any other status sends `xgimi_source_hdmi=0` and 500 ms later `xgimi_source_hdmi_delay=0`. Event 4 sends `xgimi_source_hdmi=0`. Event 1 `x:SOURCE,state:0` sends `xgimi_source_hdmi=0` when SOURCE is the vendor's current input source.
- `apps/core/lumen-hdmi/src/org/lumen/hdmi/HdmiAudio.java` (Android): sends them with `AudioManager.setParameters` (stock: `AudioSystem.setParameters`). Log tag `LumenHdmiAudio`.
- Wiring: `Z9xProjector/App.java` installs it with the HAL thread and adds it as a second receiver of the vendor events. `Z9xProjector/AiScenePolicy.java` got `addTvEventSink`, because the vendor keeps one callback per client.
- Only the Z9X app installs it. The HB and Play 6 apps share `AiScenePolicy.java` but install nothing, their stock rule was not read. The X50 family app is a separate base and is untouched. `devices/goat_elegant/forks.tsv` records the new origin of the HB `App.java` fork.

Tests, all on `xgimi-llm`:

- `sh apps/core/lumen-hdmi/test/run.sh`: HdmiLinkTest 59 checks, EdidModesTest 28 checks, HdmiAudioKeysTest 51 checks, 0 failures. HdmiRelink and HdmiAudio compile against the platform headers. Wiring 53 lines.
- `python3 apps/core/lumen-hdmi/test/mutate.py`: 82 mutants killed, 0 alive (12 of them new).
- The assembled apps compile and package: Z9xProjector, HbProjector, Play6Projector (`tools/devices/assemble_app.py` + `apps/build_apk.sh`, throwaway key). `check_forks.py --device goat_elegant` is green.

Not done:

- No run on a projector. What the audio HAL does with the key is still unknown (section 4), so the effect on the HDMI sound reports has to be checked on a unit.
- The Z9X gate (`tools/play6/z9x_gate.sh`) and `tools/devices/check.sh` were not run. The Z9X app changes on purpose, so the gate needs a re record when this is merged.
- The X50 family app was not built.
- The other proven gaps (audio focus, `setDisplayArea`, HGiG row, `HDMI_INFOS`, game mode type) are not changed: each needs a check on a unit or an owner decision first.
