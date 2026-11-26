# Z9X (G0082): system parity, stock V6.15.62 against Lumen

What the stock system does outside the menus (framework hunks, persistent services, native daemons, props, apps) and
what Lumen does in its place. The feature and menu parity lives in `docs/z9x/parity/`, this file covers the system
lifecycle that those reports skip: sleep, wake, thermal, audio routing, keys, memory, time.

Lumen base: `origin/integ-1115d` a2f166a9. Stock: G0082, mt5877, V6.15.62 (Android 14, user build).

## 1. Method and sources

- Static analysis only: jadx of the stock jars and APKs, `strings` and `readelf` of the native files, grep of the
  Lumen tree. Read-only snapshots of a running Z9X on Lumen 1.0.11-beta10 (getprop, ps, lshal, service list, packages,
  settings, `dumpsys thermalservice`, `dumpsys SurfaceFlinger`, sha256 of five native libraries). Nothing was tested or
  changed on a unit.
- A row marked "strings only" was not traced through code. A row marked "name only" is an APK that was not decompiled.
- Evidence shorthand:
  - `DEC/` = `xgimi-llm:~/z9xsys/dec/<name>/sources` (jadx output of services, framework, xgimiapi, XgimiService,
    XRMService, SystemUI, NewSounderMode, XgimiMateService, XgimiTvInputService, systwind, SettingsProvider and others)
  - `FS/` = `xgimi-builder:~/devices/g0082_mt9952/v61562/fs` (stock file systems. The folder name says mt9952, the
    audio HAL of this model is `audio.primary.mt5877.so`)
  - `OUT/` = `~/lumen-build-tmp/z9x-system-parity/out` (saved command output), `LIVE/` = `.../live`,
    `NOTES` = `.../NOTES.md` sections A to L
  - Lumen paths are relative to the repo root
- HB column: "yes" means the same mechanism exists in the stock HB firmware (G0130 6.16.24, G0148 6.17.12) and the
  Lumen HB sources have no counterpart. "not checked" means exactly that.

How Lumen is built on this model: LineageOS 21 TV GSI as the system, the stock vendor unchanged. The framework carries
one patch (`lineage/patches/z9x/0001-tvinput-jni-stream-lock-deadlock.patch`). Every XGIMI behaviour is rebuilt in
`apps/LumenProjector/core`, `apps/core/*` and `overlay/v1/*.rc`. `/vendor/app` and `/vendor/priv-app` are hidden at
post-fs-data (`xgimi_compat.rc`), so no `com.xgimi.*` package runs (`LIVE/packages.txt`: 121 packages, none of them
XGIMI).

## 2. Top gaps, ranked

Status values:

- VERIFIED: the stock mechanism was read in the stock code or binary, and the Lumen tree was searched for the same
  behaviour under other names at a2f166a9 and at the two unreleased tips. `volume-z9x-play6` 742bca0b is already
  contained in a2f166a9 (0 commits ahead). `cec-standby-stock` ff9b52c2 is one commit ahead (HDMI-CEC defaults, 34
  files, none in the areas below). So the three refs give the same answer for every row (`OUT/lumen_lmk.txt`,
  `OUT/lumen_checks.txt` "file counts per ref").
- WEAKENED: the second pass found the gap smaller than the notes said. The reason is in section 3.

User impact is an inference from the code unless the row says it was measured. Nothing here was tried on a unit.

Row numbers are those of the first version of this file, because sections 4 to 7 refer to them. Order by weight after
the second pass: 1, 2, 3, 5, 7, 6, 10, 9, 4, 8.

| # | gap | stock | Lumen | user impact | HB | status |
|---|---|---|---|---|---|---|
| 1 | Overheat and fan failure: no warning, no orderly power-off | SystemUI `WarnTipUIV2` 381-462 listens to the gmpf temperature events. TempException and MCU error start a 30 s countdown, HightPowernDown and fan error a 15 s one, then lamp off and POWER. XRMService sets `sys.xgimi.xrm.temperature_err` so that this POWER becomes a real power-off and not a sleep | The Z9X app registers two gmpf listeners only, focus (306) and TV (702). Temperature is read for a log line. A thermal listener exists only inside lamp-only standby (`StandbyController.java:1018-1030`) and uses the Android thermal service, which sees one SoC zone | The user gets no warning and the system does not power off in an orderly way when the light source, the board or the fan runs hot. The vendor thermal thread and a kernel watchdog reset still run, see 3.1 | yes | VERIFIED for the warning and the power-off. WEAKENED on "nothing protects the hardware" |
| 2 | No cold power-off after a number of sleeps | `PowerManagerService.getShutdownMode` (5119-5142): once `strSuspendCnt >= persist.xgimi.max_str_cnt` (default 15, not set in any G0082 prop file) the POWER press powers off instead of suspending. The same function powers off on a pending OTA, on repeated false wake-ups, on `temperature_err` and before setup is complete | No counter and no such decision under any name | Vendor daemons and the MCU are never restarted by a cold boot unless the user pulls the plug. Drift over long uptime is possible, not measured | yes | VERIFIED |
| 3 | HDMI input sound: three audio HAL keys are never sent | `xgimi_source_hdmi` (XRMService `SignalChangeReceiver` 106-130), `xgimi_audio_source_change` and `xgimi_audio_EQ_change` (XgimiService `XgimiServices` 636-662). The audio HAL contains all three keys | Not sent. The keys Lumen sends are `xgimi_audiopath_switch_start/end`, `xgimi_rc_*`, `xgimi_model`, `xgimi_factory_*`, `xgimi_x_remote` | The HAL is not told that the HDMI input is the source and that the vendor changed source or EQ. What the HAL does with the keys was not traced (one nearby string: "set hdmi input source volume") | yes, with the correct HB library | VERIFIED as missing. Effect not measured |
| 4 | Volume per output: Bluetooth absolute volume switch and the soundbar volume bar | `AudioService` 7096-7098 sets `xgimi.bluetooth.abs.volume.enable` to 1 or 0. SystemUI `HdmiVolumeReceiver` shows a bar on the Android broadcast `OSD_MESSAGE`. `SignalChangeReceiver` 135-141 forwards TV event 11 `eARCstate` to `seteARCStatus` | Per-output volume memory and the push to the DSP are present (`StockVolume.java:28-40`). The eARC state is followed by `cec/EarcLink.java` in the Z9X and HB apps. No hit for the Bluetooth switch prop or an `OSD_MESSAGE` receiver | Small. No reader of the Bluetooth prop was found in the stock files. The on-screen bar for a soundbar volume change may differ from stock, not checked on a unit | yes | WEAKENED. The eARC part of the first version was wrong, Lumen has it |
| 5 | Wake locks are not dropped and new ones are not refused before sleep | `xgimiPrepareSleep` (5094-5113) calls `releaseAllWakeLockForSleep` (1193-1203: removes every wake lock and disables them) and sets `isSuspending`, which refuses new locks | Standard Android: the Z9X app handles its own locks only (`z9x:standby`, `z9x:quickwake`, `z9x:sleepprep`). `z9x_power.rc` uses one kernel lock to forbid STR on request. Live: `mLowPowerStandbyActive=false` | An app that holds a partial wake lock (a download, a player, a sync) can keep the SoC awake with the lamp off. Not measured | yes | VERIFIED |
| 6 | No dynamic repeat for held volume keys | `/system/bin/keydetectservice` (own rc, reads `/dev/input`) with `sys.volume_key_repeat_delay` and `sys.repeated_key_vol_steps`. `AudioService` 2507-2514 copies the delay into `sys.dynamic_key_repeat_delay` while a volume key repeats. A patched `libinputflinger.so` reads that prop | No daemon (it lives on the stock system partition), no patch, no prop | A held volume key repeats at the standard Android rate and step. The notes said "lists and volume", the code shows volume only | yes | VERIFIED, scope narrowed |
| 7 | No Bluetooth speaker mode | NewSounderMode app, `bluetooth.profile.a2dp.sink.enabled=true`, `bluetooth.a2dp.src_sink_coexist.enabled=true`, `bluetooth.profile.avrcp.controller.enabled=true` (system `build.prop:133-143`), remote key 64 | The three props are absent on the live unit, key 64 is dropped from the layout (`Vendor_000d_Product_3838.kl:37`), no app. A working Lumen implementation exists for the Play 6 only (`apps/Play6Projector/.../speaker/BtRole.java`) | The projector cannot play sound from a phone over Bluetooth | yes | VERIFIED |
| 8 | Memory tuning differs from stock | `ro.lmk.swap_free_low_percentage=50`, `thrashing_limit=5`, `psi_complete_stall_ms=500` (vendor `build.prop:207-213`), swappiness 60 (`init.fusion.rc:31`) | 10 / 30 / 700 (`overlay/v1/product_prop_v1.txt:15-19`), swappiness 100 (`overlay/v1/z9x_speed.rc:11`) | Deliberate, with a recorded reason (3.8). Apps are killed later and zram is used more (live snapshot: 700 of 819 MB swap in use) | not checked | VERIFIED as a difference. Not a defect |
| 9 | Android time is not pushed to the MCU clock | XRMService `RtcTimerSyncService` 70-95: `setCommonCmd("setRtc", "1", now)` | No write under any name. Lumen also turns the `rtc0` wake source off (`z9x_power.rc`) | The MCU clock is not kept in step. Low impact today because Lumen does not use timed wake-ups | yes | VERIFIED |
| 10 | XGIMI special key codes | `XgimiWindowManagerDomestic`: 3123 and 3124 screen on and off (299), 2102 speaker mode (602), 2200 music control to POWER (621), 2110 long press (636). `KeyEvent` names 2111 ENTER_SLEEP, 2112 EXIT_SLEEP, 2117 SHORTCUT | The Lumen layout remaps or drops the XGIMI labels (`Vendor_000d_Product_3838.kl:18`), no handler for the codes | Long press and music control keys of the remotes that send these codes do nothing. Which shipped remotes send them was not checked | yes | VERIFIED for the keys. The remote low battery part is WEAKENED |

Smaller items outside the list: `media.aac_51_output_enabled=true` (stock system_ext `build.prop:23`) is absent on
Lumen (AAC 5.1 output), SELinux is Permissive and the build is userdebug with `ro.debuggable=1` against the stock
user build (`LIVE/getprop.txt`, `LIVE/misc.txt`). The media and graphics differences are in section 9.

## 3. Verification of the top gaps

Each check can be repeated with the scripts in `~/lumen-build-tmp/z9x-system-parity/scripts/pass2`. Their output is in
`OUT/`. The Lumen search covered `apps`, `overlay`, `devices`, `lineage` and `tools` at each ref, without docs and
tests.

### 3.1 Overheat: does the vendor power the projector off by itself

Short answer: not proven either way. The warning and the orderly power-off are app-side on stock and missing on Lumen.
A hardware-level last resort exists for the SoC only.

- Stock Java (`OUT/java_thermal.txt`): `WarnTipUIV2` 381-462 maps MCU_STATUS_ERR to a 30 s countdown, TEMP_EXCEPTION
  to 30 s, HIGHT_POWERNDOWN to 15 s, FAN_STATUS_ERR to 15 s. `WarningWindowV2.shutDown` 375-392 switches the light off
  through `GmDisplayManager.setScreenOnOff(false)` and then sends POWER (`XgimiKeyEvent.shutDown` 32-33, key 26).
  `XRMService.registerTemperatureListener` (about 411-420) sets `sys.xgimi.xrm.temperature_err=true` on the same
  events, and `getShutdownMode` turns POWER into a power-off when that prop is set.
- Vendor (`OUT/vendor_window.txt`, `vendor_trace.txt`, `vendor_shutdown.txt`, `thermal_callees.txt`): in `libxgimi.so`
  the thread `Msrv_Temperature_Control_New::innerThread::run` logs "temperature too high,will sleep" and "temperature
  exception,will sleep" and then sends the events HightPowernDown and TempException to its listeners.
  `Exception_Handle_Base::doTempExcep` and `doTempHigh` do three virtual calls and pass the event name to the sender.
  The direct callees of both thermal threads are light effect, fan and algorithm functions. There is no direct call to
  a power-off, a standby, a key injection or `sys.powerctl`. The string `sendKeyEvent:kppower` is referenced from
  `IotCtrl` only. Three virtual calls in the handlers were not resolved, so a light cut through a device object
  cannot be excluded from the binary alone.
- The words "will sleep" in the vendor log match the stock flow: the vendor announces, the app does it.
- Thresholds (`FS/../xgimiconfig_a/G0082/tempCtrlCfg/tempctrl.xml:115-177`, `OUT/vendor_cites.txt`): system sensor
  warning 63, power-off 65, restore 52, with a failed fan 60. Green light source 71 / 74 / 63 / 69. Red 63 / 65 / 52 /
  60. Environment 45 / 45. `fan_fail_cnt` 5.
- Under Lumen the vendor is unchanged, so the thread still runs, still drives the fan and still emits the events.
  Nobody listens.
- Android side (`OUT/thermal_hal.txt`, `OUT/live_thermal.txt`): the thermal HAL is
  `android.hardware.thermal-service.mediatek`, strings show `thermal_zone0` and `cooling_device0` only. On the live
  unit the HAL returned 67.0 for all 14 sensor types, one SoC zone copied to every type (the cached list held 43.0 for
  all 14, an older sample). So the Lumen standby listener can react to the SoC temperature only. It knows nothing
  about the light source, the DMD or a stopped fan, and it is registered only during lamp-only standby.
- Kernel (`OUT/vendor_cites3.txt`): `mtktv-thermal.ko` has "Reach reset temperature point" and "trigger WDT reset to
  protect SOC!!!". This is a watchdog reset of the SoC, independent of Android, present under Lumen too. The other
  three thermal modules have no critical or power-off strings.
- One related stock tip does have a Lumen counterpart under another name. Stock SystemUI has a
  `HighTemperatureTipsDialog`, created by `FocusUIV2` (295-299), and the focus event text `brightnessAdjust` is a
  constant of the same package (`ConstantFocus.java:64`). Lumen shows a "brightness reduced" notice on that event
  (focus event 608, `apps/LumenProjector/core/.../eye/EyeGuard.java:261-269`, at most once per 30 minutes). That the stock
  dialog is shown on this event is taken from the Lumen comment, the stock branch was not read. The same comment
  attributes the event to the distance dimming thread. What makes the vendor send it was not traced
  (`OUT/lumen_checks11.txt`, `OUT/java_ctx6.txt`). It is a notice only, with no countdown and no power-off.

Verdict: a real gap, not a cosmetic one, until someone shows that the vendor cuts the light by itself. What is certain:
no warning, no countdown, no power-off from the system side. What protects the unit today: the fan control of the
vendor thread and the SoC watchdog reset. What is open: the light source and fan failure cases. Lumen already has a
real power-off routine (`StandbyController.powerOffNow`, line 902) that a temperature listener can call.

One constraint is recorded in the tree: `apps/LumenSensors/.../hal/GmpfAllowlist.java:40-45` bans IGmpf 432 and 440
(temperature manager and listener) for a second app, because the vendor waits for the listener's reply while it
holds its listener mutex. So the listener belongs in the projector app, once, with a reply that never blocks.

### 3.2 Sleep counter

`OUT/java_ctx.txt`, `java_ctx2.txt`: `strMaxCount = SystemProperties.getInt("persist.xgimi.max_str_cnt", 15)` (680),
`strSuspendCnt = 1` (681), incremented in `xgimiPrepareSleep` (5106). The caller (4908-4937) maps mode 0 to sleep, 1 to
power-off, 2 to reboot. The prop is not set in any of the five G0082 `build.prop` files or in the init files
(`OUT/vendor_cites2.txt`), so 15 applies. The counter starts at 1, so the power-off comes at the 14th or 15th POWER
press after a boot. The exact press was not counted on a unit. Lumen: 0 files at each ref for `max_str_cnt`,
`strSuspend`, `str_cnt`, `strCount`, `suspendCount`, `sleepCount`, `coldBoot`. `PowerKey.java` and `StandbyController`
enter sleep with no count.

### 3.3 HDMI audio keys, redone for HB with the correct library

`OUT/vendor_cites.txt` "audio HAL keys per model". The Z9X HAL `audio.primary.mt5877.so` (lib and lib64, also in
6.15.19 and 6.15.58) contains `xgimi_source_hdmi`, `xgimi_audio_source_change` and `xgimi_audio_EQ_change`. The HB HAL
is `audio.primary.mt5873.so`: all three keys are present in G0130 6.16.24 and in G0148 6.17.12. The stock HB
XRMService and XgimiService APKs carry the keys as well. `audio.primary.default.so` has none of them on any model,
which is the library an earlier check had looked at. Lumen: 0 files at each ref for the three keys and for the
shorter forms `source_hdmi`, `audio_source_change`, `EQ_change`. The gap is the same on Z9X and HB.

### 3.4 Volume per output (weakened)

- `volume_music_spdif` and `volume_music_hmdi_arc` are the standard Android per-device volume settings, not XGIMI
  ones. Android keeps them on Lumen as well.
- The XGIMI part is the push of that level to the DSP as `xgimi_pcm_volume` when the output switches to SPDIF or ARC
  (`XgimiServices` 636-662). Lumen does the same from `apps/core/lumen-audio/.../StockVolume.java` (28-40: each
  output's own music level goes to its key at start, at boot, after an audio server restart and on every change).
- `android.hardware.hdmi.action.OSD_MESSAGE` is an Android broadcast that the LineageOS framework also sends. Stock
  SystemUI has a receiver for it (`HdmiVolumeReceiver` 17-63), Lumen has none. Whether Lumen shows the standard
  Android volume bar for a soundbar instead was not checked on a unit.
- The eARC state forward is not a gap. The first version of this file listed it as missing, which was wrong. Lumen
  has the behaviour under another name: `apps/LumenProjector/core/.../cec/EarcLink.java` (the same class in
  `apps/HbProjector`) reads the vendor link state and connects the HDMI ARC output while the link is eARC. That is
  what the stock `seteARCStatus` does, and the class header describes the stock flow it replaces
  (`OUT/lumen_checks9.txt`).
- Left as real: `xgimi.bluetooth.abs.volume.enable`. Stock `AudioService` writes it. In the whole stock file tree
  only `services.jar` names it and no decompiled class reads it (`OUT/dolby_strings.txt` last block,
  `OUT/java_ctx5.txt`). The Bluetooth module `com.android.btservices.apex` is a packed image and was not unpacked,
  so a reader there is possible. Lumen drives the Bluetooth output through its own DSP key in `StockVolume`.

### 3.5 Wake locks

`OUT/java_ctx.txt` "PMS 5094-5142" and "PMS 1193-1215". Lumen: 0 files for `releaseAllWakeLock`, `isSuspending`,
`LowPowerStandby`, `forceSuspend` used as a mechanism (`LampControl.java:44` names `forceSuspend` as forbidden). The
power package of the Z9X app touches only its own three locks (`OUT/lumen_checks6.txt`). Android's Low Power Standby,
the closest stock Android feature, is off on the live unit (`OUT/live_native_sha.txt`).

### 3.6 Key repeat

`OUT/vendor_cites3.txt`: `keydetectservice` (138560 bytes, `keydetectservice.rc`) is the only native reader of
`sys.volume_key_repeat_delay`. Its strings: `checkRepeat`, `sys.repeated_key_vol_steps`,
`sys.dynamic_key_repeat_delay`, "Don't set key repeat,because already key up". `AudioService` 2507-2514 is the only
Java user (`OUT/java_ctx2.txt`). `libinputflinger.so` has the one vendor string `sys.dynamic_key_repeat_delay`, also in
the HB library. The internals of the daemon were read as strings only. Lumen: 0 files for the props, and no repeat
handling for volume keys in `lumen-audio` or the Z9X keys package (`OUT/lumen_checks6.txt`).

### 3.7 Bluetooth speaker mode

`OUT/vendor_cites2.txt`, `OUT/lumen_checks2.txt`. Stock props at system `build.prop:133-143`. Live Lumen has
`bluetooth.profile.a2dp.source.enabled` and `avrcp.target` only. NewSounderMode is a priv-app on G0082, G0130 and
G0148. The Lumen hits for A2DP sink are in `apps/Play6Projector` (`BtRole.java`), nothing for the Z9X.

### 3.8 Memory tuning: why 10 / 30 / 700 / 100

Git history gives no separate commit: the four values arrive with the first commit of the private tree (eba561b1,
2026-11-03, `OUT/lumen_lmk.txt`). The reason is written next to the values:

- `overlay/v1/product_prop_v1.txt:11-21`. With 50 the "swap is low" condition (less than 50 % of 800 MB free) held
  almost always under load, and every dip under the 220 MB high watermark then killed down to adj 201: "the storm of
  10 kills in 0.9 s that took the launcher (adj 600) at a Kinopoisk start". 10 is the lmkd default. `thrashing_limit`
  5 counted 5 % refaults as thrashing, 30 is the lmkd low-RAM default. 700 is the lmkd default and gives fewer "device
  not responding" kills.
- `overlay/v1/z9x_speed.rc:1-11`. Swappiness 100 prefers compressing idle pages into zram (about 7:1 on this unit)
  over dropping file pages that must be read again from eMMC at the next app switch.
- Both files cite "Speed spec section 5" (v7 in `z9x_speed.rc`). That document is not in the tree at a2f166a9 and
  not in the older project notes on the Mac (`OUT/lumen_cites8.txt`), so these comments are the only recorded reason.

The stock values in the Lumen comment are from V6.15.58. V6.15.62 has the same ones (vendor `build.prop:207-213`,
`OUT/vendor_cites6.txt`). `libandroid_runtime.so` on stock also reads a per-package low memory list (4.6), which has no
Lumen counterpart.

### 3.9 Clock

`OUT/java_ctx.txt` "RtcTimer 70-95". Lumen: the only RTC mention is a diagnostic read of `/proc/driver/rtc`
(`overlay/v1/z9x_diag.sh:143`).

### 3.10 Special keys and the remote battery

`OUT/java_ctx4.txt`: the four handlers in `XgimiWindowManagerDomestic.java` at 299, 602, 621, 636. Lumen:
`overlay/v1/keylayout/Vendor_000d_Product_3838.kl` lines 18, 37 and 40 record that XGIMI labels are remapped or dropped,
key 64 MUSIC_SOUNDERMODE and key 88 RC_LOW_BATTERY among them.

Weakened part: no stock Java handler for RC_LOW_BATTERY or scan code 88 was found in the decompiled set. The only
"low battery" hits are the `LowPowerPoweroff` constants of the projector's own battery (`OUT/java_ctx3.txt`). Lumen
reads the battery level of a Bluetooth remote through the standard Android broadcast
(`apps/core/lumen-remote/.../RemotePairing.java:170-171`). Whether either side shows a low battery notice was not
established.

## 4. Component table

Lumen status values: runs unchanged, replaced fully, replaced partially, missing.

### 4.1 Framework (stock `services.jar` and `framework.jar` hunks)

| stock component | what it does | Lumen status | user impact | evidence | HB | fix plan |
|---|---|---|---|---|---|---|
| PMS `goToSleep` patch (4896-4930) | Every sleep request is funnelled through the XGIMI power key path (key 2111, animation, countdown), only reason 100 really sleeps | replaced partially (`StandbyController`, `PowerKey`, `SleepTimer`) | Same visible result for the POWER key. Sleep from apps or CEC skips the stock countdown | `DEC/services` PowerManagerService.java:4896 | yes | none planned, owner decision on the power key stands |
| PMS `getShutdownMode` (5119) | Picks STR or real shutdown: update pending, false wake-up storm, temperature error, setup not finished, every 15th sleep | missing | Gaps 1 and 2 | PowerManagerService.java:5119, 680 | yes | one shared sleep policy in `apps/core` with the stock conditions as data |
| PMS `xgimiPrepareSleep` (5094) | Broadcast XGIMI_SCREEN_OFF, gmpf goToSleep (241), remove recent tasks, drop all wake locks, lamp off (196 then 195), refuse new wake locks | replaced partially: lamp off, media pause, mute, `PowerManager.goToSleep` | Gap 5. Recents are not cleared on sleep | PowerManagerService.java:5094, 1062 | yes | add wake lock handling to the shared sleep policy |
| PMS wake path (1504), `XgimiBootController.bootSourceCheck` | On STR wake starts the HDMI player from the CEC active source or the stored boot source, or enters speaker mode | replaced fully for HDMI (`BootSource`, applied on each STR wake), speaker mode missing | none for HDMI | PowerManagerService.java:1504, `DEC/services` com/xgimi/control | yes for speaker mode | part of gap 7 |
| PMS idle (`vendor.xgimi.idle_threshold`) | Broadcasts XGIMI_START_IDLE and XGIMI_STOP_IDLE. Receivers are only JobScheduler idle tracking | not needed | none | NOTES L | no | none |
| `XgimiWindowManagerDomestic` (909 lines) | Key policy: 2102, 2110, 2111, 2117, 2200, 3123, 1040, 1041, power, volume. Shutdown animation, sleep timer intents | replaced partially (`Z9xFrameworkKeysOverlay`, `KeyReceiver`, global keys) | Gap 10 | `DEC/services` com/xgimi/wmmanager/domestic | yes | handle the missing key codes in `apps/core/lumen-remote` |
| PhoneWindowManager mouse mode | App list `/vendor/etc/xgimisys/sys_mouse_applist.txt`, `debug.xgimi.mousemode` | missing | Air-mouse behaviour per app differs | NOTES C | not checked | low priority |
| AudioService hunks | `xgimi_pcm_volume` on volume change (6308-6335), wireless speaker `XGIMI_DSPK` (5423-5469), `sys.volume_key_repeat_delay` (2507), `xgimi.bluetooth.abs.volume.enable` (7096) | replaced partially (`StockVolume` sends `xgimi_pcm_volume` and the per-output chip keys) | Gap 4 and 6 | `DEC/services` AudioService.java | yes | shared volume module (branch volume-z9x-play6) |
| UsbAlsaManager, UsbHostManager | XGIMI USB audio device list, USB black list, curtain plug-in by vid and pid | missing | No effect today: no USB audio card on the unit (`/proc/asound/cards`) | NOTES C, L | not checked | only with USB mic or curtain work |
| `XgimiCloudConfiguration` (ActiveServices, ProcessList, BroadcastQueue, ActivityStarter) | Cloud-driven memory killer, service restart delay by free memory, background app black list, wake-up allow list | missing (Lumen has its own `MemoryGuard`) | Background app policy differs. Not measured | NOTES C | yes | review together with gap 8 |
| Watchdog | Adds `/vendor/bin/gmpf_main` to native stack dumps, `setPowerState` | missing | Diagnostics only | NOTES C | yes | none |
| Task top-activity hook | Starts XRMService with `top_activity_changed` for per-app audio effect and the IMAX app list | replaced partially (AI scene in `AiScenePolicy`) | Per-app audio effect not applied | NOTES C, E | yes | low priority |
| AlarmManagerService | 10000 false wake-ups set `sys.xgimi.alarm.large_false_wakeups` which forces a shutdown | missing | Guard against a wake-up storm absent | NOTES C | yes | fold into the shared sleep policy |
| MountServiceIdler | fstrim schedule props | missing (AOSP schedule applies) | none known | NOTES C | not checked | none |
| framework.jar | Key codes 2110, 2200, 1040, 1041, wireless speaker audio devices, NTP server `ntp1.aliyun.com`, skin manager, resolver home = `com.xgimi.home` | missing (not needed: Lumen uses its own home and the AOSP NTP server) | Key codes: gap 10 | NOTES G | yes | none beyond gap 10 |
| HdmiControlService, display, media, tv | Plain AOSP in stock, no XGIMI hunks | runs unchanged (LineageOS code) | none | NOTES G | no | none |

### 4.2 XRMService (persistent, 17 services)

| stock component | what it does | Lumen status | user impact | evidence | HB | fix plan |
|---|---|---|---|---|---|---|
| XRMService.onCreate | `goToBootCompleted(0)` to gmpf, listeners, AI init | replaced fully (`BootStage.java`) | none | NOTES E | no | none |
| registerTemperatureListener | Sets `sys.xgimi.xrm.temperature_err=true` on TEMP_EXCEPTION, HIGHT_POWERNDOWN, FAN_STATUS_ERR | missing | Gap 1 | NOTES E, K | yes | shared temperature listener |
| registerGmpfEvent (audio event 1, 10, key 35) | Follows the vendor audio path switch, remote voice enable, music key long press | replaced partially (`AudioPath.java`) | none proven | NOTES E, L | not checked | none |
| BootControlService | Boot source on wake, wake animation, waits for audio ready, lamp on | replaced fully (`BootSource`, `StandbyController`, `z9x_nui`) | none known | NOTES E | no | none |
| SignalChangeReceiver, SignalService, XRMHdmiService | TV events: HDMI signal state to `xgimi_source_hdmi`, event 11 eARC state, event 5 picture mode to AIPQ, event 14 to AISR | replaced partially (`HidlTvCallback`, AI scene, `cec/EarcLink` for the eARC state) | Gap 3 | NOTES K, L | yes | send the three audio keys from the shared HDMI module |
| AIPQ, AISR | Video type to picture mode 10..17, playback monitor | replaced fully (`AiScenePolicy.java`, AISR rows) | none known | NOTES D, J | no | none |
| SmartTurboLightService | Ambient light driven brightness | replaced partially (`Lamp3d`, ambient rows) | not compared in detail | NOTES E | not checked | none |
| RtcTimerSyncService | `setCommonCmd('setRtc','1',millis)` | missing | Gap 9 | NOTES K, L | yes | one call after time sync |
| VolumeObserverService | Stores the volume, starts hilinknew on sleep | missing (hilink is dead in stock: product id commented out) | none | NOTES K | no | none |
| SleepCountDownReceiver | Sleep timer intents | replaced fully (`SleepTimer`) | none | NOTES E | no | none |
| ScreenSaver one-time config | `screen_off_timeout=300000`, `sleep_timeout=-1`, screensaver on | replaced fully (same two values on the live unit) | none | NOTES L, `LIVE/settings.txt` | no | none |
| MemMonitorService, SystemStateService, AppDurationService, MachineUsageTimeService | Memory monitor, perf and usage telemetry | missing on purpose (telemetry), memory part covered by `MemoryGuard` | none | NOTES D, E | no | none |
| MachineStateService | Convex lens and atmosphere lamp, hardware the Z9X does not have (unverified) | missing | none expected | NOTES E | not checked | none |
| WiredNfcService, SmartConnectService, AlarmWakeupService, WifiScanningHelperService | NFC tap, phone connect, alarm wake, Wi-Fi scan helper | missing | NFC tap of the remote not handled | name and manifest only | not checked | low priority |
| AutoBurninController | Factory burn-in | missing on purpose | none | NOTES E | no | none |

### 4.3 Stock SystemUI (`com.xgimi.systemui`, persistent) and TvSystemUI

| stock component | what it does | Lumen status | user impact | evidence | HB | fix plan |
|---|---|---|---|---|---|---|
| tip/WarnTipUIV2 | Temperature, fan, MCU, display failure warnings with countdown and power-off | missing | Gap 1 | `DEC/SystemUI` WarnTipUIV2:391, NOTES H, K | yes | shared warning card plus shutdown |
| DustProofDialog, DoorAbnormalityDialog, FanMutexTips | Dust filter, door, fan notices | missing | Not proven to apply to the Z9X hardware | NOTES H | not checked | check the feature flags first |
| volume/ (VolumeUI, ARCVolumeDialog, HdmiVolumeReceiver) | Volume OSD, ARC volume OSD on `ACTION_OSD_MESSAGE` | replaced partially (Lumen volume UI, no ARC OSD) | Gap 4 | NOTES H, L | yes | shared volume module |
| focus/ (65 files) | Auto focus, device move detection, curtain align UI | replaced partially (`HidlFocusCallback`, AF card, fit to screen) | covered by `docs/z9x/parity/focus.md` | NOTES H | n/a | see the focus report |
| global/ (AIPQ animation, Dolby logo, eco mode, eye protection) | On-screen badges and animations | replaced partially (`EyeGuard`, AI scene) | No Dolby logo badge | NOTES H | not checked | low priority |
| contrast/ContrastPatchHelper | In performance picture mode turns the lamp off on fully black frames in the stock players | missing | Black level on black frames in that mode. Gating for the Z9X is unverified | NOTES H | not checked | hand to the picture work |
| power/PowerUI, remote/, update/ | Shutdown dialog, custom key UI, INUI app update | replaced fully (Lumen power menu, key rebinding, Lumen Updater) | none | NOTES H | no | none |
| TvSystemUI (system_ext) | AOSP TV SystemUI | replaced fully by the LineageOS TvSystemUI | none | `LIVE/packages.txt` | no | none |

### 4.4 Other persistent stock services

| stock component | what it does | Lumen status | user impact | evidence | HB | fix plan |
|---|---|---|---|---|---|---|
| XgimiService (XgimiServices, BootService) | gmpf audio events 1 and 6 to `xgimi_audio_source_change` and `xgimi_audio_EQ_change`, first-time SPDIF and ARC volume, OSD frequency | replaced partially (`StockVolume` pushes the SPDIF and ARC levels, the two event keys are not sent) | Gap 3 | `DEC/XgimiService` XgimiServices.registerAudioEvent, NOTES K | yes | shared audio module |
| NewSounderMode (SounderModeService, IGimiSound) | Bluetooth speaker and AirPlay or DLNA sound mode, gets `sys_state:wakeup` and `gotosleep` | missing | Gap 7 | NOTES G, L | yes | port only if the owner wants speaker mode |
| XgimiMateService | Dongle, wireless speaker, microphone accessories | missing | XGIMI accessories unsupported (already a known "will not do") | manifest | not checked | none |
| XgimiTvInputService | Hdmi1/2/3 TV input services | replaced fully (`apps/LumenTvInput`) | none | `LIVE/packages.txt` | no | none |
| systwind (WondowService) | System window helper | missing | unknown, small APK (41 KB), not traced | manifest | not checked | none |
| BluetoothService (XgimiBluetoothService) | XGIMI Bluetooth pairing and remote service | replaced fully (`apps/core/lumen-remote`) | none known | manifest | no | none |
| XgimiRemoteDebug | Remote debug by XGIMI support | missing on purpose | none | manifest | no | none |

### 4.5 Native daemons and init services (system side)

| stock component | what it does | Lumen status | user impact | evidence | HB | fix plan |
|---|---|---|---|---|---|---|
| nativeui | Native OSD (skia), reader of the vendor nui fifo | replaced fully (`overlay/v1/z9x_nui.sh`, services z9x_nui and z9x_nuihold) | none | NOTES A, I | no (HB has its own) | none |
| rckeydetect | Remote key during the boot video (skip, BT remote) | missing | Boot video cannot be skipped by a key. Lumen has no boot video | NOTES A, I | yes | none |
| keydetectservice | Dynamic key repeat, GPU limit through vendor qos | missing | Gap 6 | NOTES I, L | yes | see gap 6 |
| xgimicollectd | Stability log collector | missing on purpose (telemetry) | none | NOTES A | no | none |
| CaptureRecorderServer (system_ext) | Screen recorder server on top of the vendor HAL | missing, the vendor HAL runs | No screen recording through the stock API | NOTES A | no (HB stock lacks it) | none |
| blur_sysprop_notifier | Blur property helper | missing | none known | NOTES A | not checked | none |
| usbcastd, logserver.sh, debugserver.sh, xgimi_reclaimservice, browserserver | Wired cast, log and debug servers. The last two binaries do not exist in stock either | missing | No wired phone mirroring over USB | NOTES A, J | not checked | low priority |
| Vendor rc services | 47 running, 16 stopped, 28 never started on Lumen. All vendor daemons that run on stock at idle run on Lumen | runs unchanged | none | `OUT/svc_join.tsv`, `LIVE/ps.txt` | n/a | none |
| Vendor HALs (vintf) | All registered except `vendor.mediatek.qos` and `vendor.mediatek.wirelessspeaker` | runs unchanged | qos: used by keydetectservice and voice. Wireless speaker: XGIMI accessory | `LIVE/lshal.txt`, NOTES I | not checked | none |
| Vendor partitions | xgimiconfig, xgimidatabase, xgimisps, tvservice, tvconfig mounted | runs unchanged | none | `OUT/live_mounts.txt` | n/a | none |

### 4.6 Native system libraries with XGIMI or MTK changes

Audio policy, input, vold, init and boot animation rows. The media and graphics libraries are in section 9.

| stock component | what it does | Lumen status | user impact | evidence | HB | fix plan |
|---|---|---|---|---|---|---|
| libxgimiaudiopolicy.so (linked by the audio policy manager and engine) | Per-model audio policy from `/vendor/xgimiconfig/<model>/audio/audio.ini` and `aq/sound.ini`: speaker curve and pre-scale, device preference order, sends `xgimi_audiopath_switch_start/end` | replaced partially: Lumen sends `xgimi_audiopath_switch_start/end`, `spk_volume` and the speaker curve from the app (`AudioPath.java`, `StockVolume`, `EqCurve`) | Routing decisions are made in the app, not in the policy manager. Covered by the volume spec work | `OUT/n2.txt` | yes (HB stock has the lib) | shared volume and audio path module |
| libaudiopolicymanagerdefault.so | XGIMI_DSPK wireless speaker, USB mic names, `vendor.xgimi.tts.volume_max/min` | missing | XGIMI USB mic and wireless speaker not recognised, TTS volume limits absent | `OUT/n2.txt` | not checked | none |
| libinputreader.so, libinput.so | "XGIMI X_Remote", `debug.xgimi.mousemode`, `xgimi.music.slide`, key labels XGIMI_LONG_PRESS and XGIMI_MUSICCT_* | missing (Lumen ships its own key layouts with AOSP labels) | Gap 10 | `OUT/n2.txt` | yes | see gap 10 |
| libinputflinger.so | Reads `sys.dynamic_key_repeat_delay` | missing | Gap 6 | NOTES L | yes | see gap 6 |
| vold | Mounts the xgimidatabase partition, exfat, ntfs and ISO through `xgimiMount` | replaced partially (vendor fstab mounts xgimidatabase, LineageOS vold handles exfat and ntfs) | ISO images on USB are not mounted | `OUT/n2.txt`, `OUT/live_mounts.txt` | not checked | none |
| init | Loads `/vendor/etc/xgimimodelname/<model>/build.prop`, sets `ro.xgimi.modelname`, `xgimi.boot.reason` | replaced partially (`tools/devices/model_props.py` sets the lines that have a reader) | Section 6 | `OUT/n2.txt`, MODEL_PROPS.md | no | none |
| libbootanimation.so | XGIMI boot animation: boot music, key monitor, animation state to gmpf, curtain animation, shutdown animation | replaced partially (LineageOS animation, `BootStage` reports the state to gmpf) | No shutdown animation, no boot music from the animation | `OUT/n2.txt` | yes | none planned |
| libandroid_runtime.so | Reads `/vendor/etc/xgimisys/sys_lowmemory_packages.json` | missing | Per-package low memory list unused | `OUT/n3.txt` | not checked | review with gap 8 |

### 4.7 Settings defaults

| stock default | Lumen (live unit) | note |
|---|---|---|
| `def_screen_off_timeout=2147483647`, then XRM ScreenSaver sets 300000 | 300000 | same in effect |
| `def_sleep_timeout=86400000`, then XRM sets -1 | -1 | same |
| `auto_time_zone=true` | 0 on the owner unit | may be a user choice |
| `def_wifi_scan_always_available=0`, `def_location_mode=3`, `long_press_timeout=400` | same | |
| low power standby | `low_power_standby_enabled=1` | Lumen addition |

Evidence: NOTES H and K, `LIVE/settings.txt`.

## 5. Checked, not a gap

- Sleep through IGmpf 241 (goToSleep): not needed. The vendor power HAL notifies gmpf on kernel suspend and resume by
  itself (strings `gmpf_suspend_start/finish`, `gmpf_resume_start/finish` in `mediatek.hardware.power-service`, and a
  trace on a Play 6 of the same stock family in `docs/play6/research/sleep_stock.md`). Based on strings and the Play 6
  trace, not on a Z9X trace.
- Frame lock (`vendor.xgimi.framelock`): XRMService sets it only when `Build.DEVICE` is `rat_charm`.
- Fake vsync: dead in stock. XgimiService writes `mstar.fakevsync.freq`, the composer reads
  `vendor.mstar.fakevsync.freq`.
- `updateXgimiAudioPatch`: returns at once unless the SoC platform is `huanglong` (HiSilicon).
- `sys_state:wakeup` and `gotosleep` sound commands: only reach NewSounderMode, which matters only with speaker mode.
- XGIMI_START_IDLE and XGIMI_STOP_IDLE: only the JobScheduler idle tracker listens. Not a sleep path.
- Screensaver and sleep timeouts: 300000 and -1 on both.
- Vendor side: every HAL is registered except qos and wirelessspeaker, the vendor partitions are mounted, the PQ
  daemons run (`LIVE/ps.txt`).
- PM51 wake sources: no stock rc or vendor binary writes `/sys/mtk_pm/wakeup_source`. Lumen sets them in
  `overlay/v1/z9x_power.rc` (a Lumen addition, not a deviation).
- Load average 44 on the live unit: 41 vendor kernel threads in D state, the same on stock.
- hilink: never starts on stock (`vendor.xgimi.hilink.productid` is commented out).
- `vendor.trapezoid.xgimi.aa`: not in the G0082 prop files (five other models in the same vendor image set it to 1).
  `libxgimi.so` is the only stock binary that names it and it runs unchanged on Lumen. Live Lumen value 0, the stock
  run-time value was not captured (`OUT/vendor_cites7.txt`, `OUT/lumen_cites8.txt`).

## 6. Model props (`/vendor/etc/xgimimodelname/G0082/build.prop`)

38 lines: 13 equal on the live unit, 18 missing, 7 differ. Readers are from MODEL_PROPS.md in this folder (grep of
every vendor file and stock system dex, 2026-11-14).

### 6.1 Missing on Lumen (18)

| prop | stock value | reader on stock | effect of the absence on Lumen |
|---|---|---|---|
| persist.vendor.xgimi.hd_server | 2 | none found | none |
| ro.xgimi.fun.hidesystemapp | com.yunos.tv.alitvasr | none found | none |
| persist.xgimi.wifidongle.check | enable | none found | none |
| vendor.xgimi.softap.sta.coexist | true | vendor wirelessscreen (hidden) | none today. Would matter for a hotspot port |
| vendor.xgimi.disable.subwoofer | 1 | none found | none |
| ro.xgimi.line.cast | 0 | vendor wirelessscreen (hidden) | none |
| xgimi.image4k.support | 1 | vendor gimiplayer, mediaPlayer, minitvfactory (hidden) | none, the readers do not run |
| tv.series.model | Product_dapeng | vendor IqiyiSdk (hidden) | none |
| persist.xgimi.gmui.font.bold | Medium | XGIMI apps (hidden or replaced) | none |
| persist.xgimi.external.usb_bus | /dev/bus/usb/001:/dev/bus/usb/002 | stock services.jar UsbAlsaManager (replaced) | none today, no USB audio device on the unit. The stock USB audio classification is absent |
| ro.xgimi.internal.usb_device | 1d6b:0102 | same | same |
| persist.vendor.xgimi.animation.style | blue | vendor update app (hidden) | none |
| persist.xgimi.bootanimation.style | blue | stock libbootanimation.so (replaced) | none, the boot animation is not the XGIMI one |
| ro.xgimi.default.aipq | film | stock XRMService (replaced) | none: the Z9X app writes the film scene itself after AI |
| log.tag.ANNPreparedModelBase | S | no binary logs under this tag | none |
| log.tag.ANNExecutor | S | same | none |
| ro.product.unique | XGIMI+MT9681+...+dapeng+ProjectorUHD_3 | none found (iQIYI codec id) | none |
| persist.vendor.xgimi.enforce.volume | 25 | stock libbootanimation.so (replaced) | none, the stock boot music player is absent |

No missing line has a reader that runs on Lumen. None of them touches the picture path.

### 6.2 Differing (7), all identity

| prop | stock | Lumen (live) | effect |
|---|---|---|---|
| ro.product.device | horse_popular | generic_arm64 | Stock code keyed on the device name does not run on Lumen. Apps and Google services see a generic device |
| ro.product.model | XGIMI TV DOHKO | XGIMI Z9X | Name shown to apps, casting and Bluetooth peers |
| ro.build.product | horse_popular | generic_arm64 | as ro.product.device |
| ro.product.brand | XGIMI | Android | App device lists keyed on the brand do not match |
| ro.product.manufacturer | XGIMI | unknown | same |
| ro.product.board | horse_popular | mt5877_an64_db | none known |
| ro.product.name | Product_dapeng | lineage_gsi_tv_arm64 | none known beyond identity |

Lumen keeps its own identity by rule (MODEL_PROPS.md). The effect on app compatibility lists is an inference, it was
not measured per app.

### 6.3 System-side props (stock system, system_ext and product build.prop against live getprop)

| prop | stock | Lumen | effect |
|---|---|---|---|
| bluetooth.profile.a2dp.sink.enabled, bluetooth.a2dp.src_sink_coexist.enabled, bluetooth.profile.avrcp.controller.enabled | true | absent | Gap 7 |
| bluetooth.profile.hfp.hf / pbap.client / map.client / bap.broadcast.source | false | absent (defaults) | none known |
| bluetooth.profile pan, pbap.server, map.server, opp, mcp.server | false | true | Extra Bluetooth profiles are on |
| media.aac_51_output_enabled | true | absent | AAC 5.1 is downmixed by the decoder instead of output as 5.1 (inference from the AOSP meaning of the prop) |
| ro.media.maxmem | 536870912 | absent | not traced |
| chrome.tv.hole_threshold, ms.overlayrolling, dfb_jpd_write_buffer | 0, 1, SkiaDecodeMutex | absent | not traced, MStar legacy names |
| persist.adb.tcp.port | 5555 | absent (Lumen has its own adb network rule) | none |
| logd.klogd, logd.statistics | false | absent | more logging on Lumen |
| ro.debuggable | 0 | 1 | Security state differs from the stock user build |
| dalvik.vm.usap_pool_enabled | false | true | not measured |
| pm.dexopt.post-boot | extract | verify | not measured |
| ro.dalvik.vm.enable_uffd_gc | false | true | not measured |
| dalvik.vm.dex2oat-Xms | 64m | 128m | not measured |

Evidence: NOTES B, `OUT/defaults_props.txt`, `LIVE/getprop.txt`.

## 7. Stock apps (101 APKs)

Counted from `stock/apk_jar.txt`: product 4, system/app 12, system/framework 5, system/priv-app 33, system_ext 2,
vendor/app 6, vendor/overlay 3, vendor/priv-app 36. On Lumen the whole stock system is replaced and both vendor app
folders are hidden, so none of the 101 runs.

| group | APKs | count | on Lumen |
|---|---|---|---|
| AOSP base apps and providers | CaptivePortalLogin, CertInstaller, CompanionDeviceManager, ExtShared, HTMLViewer, KeyChain, PacProcessor, PrintSpooler, SecureElement, BackupRestoreConfirmation, CalendarProvider, ContactsProvider, DownloadProvider, ExternalStorageProvider, FusedLocation, InputDevices, IntentResolver, MediaProviderLegacy, NetworkStack, NetworkStackNext, PackageInstaller_tv, ProxyHandler, SettingsProvider, SharedStorageBackup, Shell, SoundPicker, StatementService, Telecom, TvProvider, UserDictionaryProvider, VpnDialogs, ModuleMetadata, webview, framework-res | 34 | replaced by the LineageOS builds of the same apps. SettingsProvider defaults differ, section 4.7 |
| Resource overlays | TvNetworkStackOverlay, TvWifiOverlay, TetheringInprocessRROOverlay, TetheringRROOverlay, WifiRROOverlay | 5 | product overlays replaced by LineageOS ones. The three vendor overlays still load from /vendor/overlay (not checked on the unit) |
| System lifecycle services | XRMService, XgimiService, SystemUI_release_838, TvSystemUI, systwind, PersistentServive, GMService, GlobalConfig | 8 | replaced partially by `apps/LumenProjector/core` and `apps/core/*`. Gaps 1 to 5 and 9 come from here |
| Settings and setup | release_838_newsettings, bootwizard_release_848, Instruction, XgimiFrameworkUi | 4 | replaced by `apps/LumenProjector/core` (menus), LineageOS TvSettings, `apps/LumenSetup` |
| Home and launcher | XgimiHome, AgileWall, Atmosphere, screensaver, msgcenter | 5 | replaced by `apps/LumenHome` (with its screensaver). Wallpaper and atmosphere scenes missing |
| Inputs and players | XgimiTvInputService, release_838_xhplayer, gimiplayer, mediaPlayer, XgimiMusicPlayer, FileManger, com.xgimi.karaokesdk | 7 | HDMI input replaced by `apps/LumenTvInput`, file browsing by the Files part of Home, playback by Lumen Play (separate app). Music player and karaoke missing |
| Picture and focus helpers | AF, AutoKst, RgbdUpgrade | 3 | replaced partially by Z9xProjector (focus, keystone through the HAL). RgbdUpgrade (sensor firmware update) missing |
| Sound | NewSounderMode, DolbyAudioService | 2 | NewSounderMode missing (gap 7). DolbyAudioService missing, the vendor Dolby stack runs, its Java service does not (effect not traced) |
| Remote, Bluetooth, accessories | BluetoothService, XgimiMateService, smartaccessories, XgimiOtaAccessories, SmartConnect | 5 | remote pairing replaced by `apps/core/lumen-remote`. Accessory support and accessory firmware update missing |
| Casting and phone control | wirelessscreen, vcontrol, MobileBridgeService, IotService, xgimiiotserver | 5 | replaced partially: `apps/LumenAirPlay`, `apps/LumenMiracast`, phone remote answers in `apps/core/lumen-control`. XGIMI IoT missing |
| Update | update, inuiserver_release_838 | 2 | replaced by `apps/Z9xUpdater` (`apps/core/lumen-updater`). INUI app self-update not needed |
| Power | release_838_shutdown | 1 | replaced by the Lumen power menu in Z9xProjector |
| Voice and keyboard | XgimiVoice, SogouInput | 2 | voice missing (Google voice where the user installs it). Keyboard replaced by LeanbackIME |
| Store, account, cloud, ads, pay | XgimiAppMarket, xgimiappdb, AccountCenter, AccountManager, XgimiManager, XgimiAdService, xgimipaysdk, IqiyiSdk, datareporter | 9 | missing on purpose (China services, ads, telemetry). Store role taken by Google Play |
| Skins and UI libraries | xgimi-skin-black, xgimi-skin-black_classicblue, xgimi-skin-black_light_blue, xgimi-skinconfig, XgimiSkinManager, xgimiuiapi | 6 | missing, not needed |
| Factory and support | minitvfactory, XgimiRemoteDebug, XgimiBugReport | 3 | factory and remote debug missing on purpose. Bug report replaced by `apps/core/lumen-report` |

Total 101. The function of an APK that is not in the `DEC/` list above is taken from its name and manifest, not from
its code.

## 8. Fix order (proposal, research only)

1. Gap 1: one gmpf temperature listener shared by Z9X and HB, with the stock countdowns and a real power-off.
2. Gaps 2 and 5: one shared sleep policy (sleep counter, wake lock handling, the other `getShutdownMode` reasons).
3. Gap 3: the three HDMI keys inside the shared audio module, after a trace of what the audio HAL does with them.
   Gap 4: nothing until a reader of the Bluetooth prop is found. The soundbar volume bar needs one look on a unit.
4. Gap 7: port the Play 6 speaker mode.
5. Gaps 6 and 10: keys.
6. Gap 9: push the time to the MCU clock.
7. Gap 8: no action unless the owner wants the stock values back.

## 9. Native media and graphics patches

Question behind this section: the open complaint that the Lumen picture looks dimmer and paler than stock.

Method: `strings` of the stock file against the Lumen file, then a search of every stock-only string in the LineageOS
21 source tree on the builder (`scripts/native_diff.sh`, `native_cand.sh`, `pass2/sf_colour.sh`, `pass2/hdr_meta.sh`).
"src=none" means the string is in no LineageOS source file, so it is an XGIMI or MediaTek addition. The Lumen files
were read from the X50 image root on the builder. Their sha256 equals the files on the live Z9X for all five
libraries (`OUT/rc_tunnel_sha.txt`, `OUT/live_native_sha.txt`), so the comparison holds for the Z9X. This is a string
and symbol comparison. No function body was disassembled here.

### 9.1 Vendor additions that touch HDR, Dolby Vision, tunnel mode, colour or output format

| stock file | addition | what it touches | Lumen equivalent | bearing on the picture |
|---|---|---|---|---|
| surfaceflinger | `vendor.mtk.4k2k.2k1k.coexist`, `.reverse`, `.version` | Output format: a 4K video plane next to a 2K interface plane. Also read by `vendor/bin/hw/mtkhwcomposer` | No reader in the Lumen file. Lumen instead raises the interface to 3840x2160 (`overlay/v1/z9x_uires/product_prop.txt:7-8`, live value 3840). Stock is 1920x1080 (vendor `build.prop:235-236`) | Different plane setup: stock composes the interface at 1080p, Lumen at 4K. Effect on brightness or colour not known |
| surfaceflinger | `vendor.mstar.trapezoid.secure.enable` | Keystone with protected (DRM) layers. Set to 1 for G0082 (model `build.prop:83`), also read by the hardware composer | The prop is set (`overlay/v1/z9x_trapezoid.rc:21-26`, live 1), the Lumen file has no reader | Geometry of protected video under keystone. Not colour |
| surfaceflinger | `vendor.mtk.inside` | Not traced. Set to 1 in vendor `build.prop:252` | Prop present on live, no reader in the Lumen file | Unknown |
| surfaceflinger | `vendor.mtk.3d.enable` | 3D output format, also read by the hardware composer | No reader. The Z9X app drives 3D through gmpf (`ThreeD.java`) | 3D only |
| surfaceflinger | `vendor.mstar.screencap.mode`, `debug.sf.mtk.showFpsOfAllVisibleLayers` | Screen capture, debug overlay | None | None |
| libstagefright | `vendor.mstar.trapezoid.enable` with "Trapezoid Configuring native window for tunneled mode with transform" | Tunnel mode video under keystone | None | Geometry of tunnelled video. Not colour |
| libstagefright | `parseDolbyVisionProfileLevelFromCsd` | Dolby Vision: profile and level taken from the codec data when the container does not give them | None (src=none) | A Dolby Vision stream without a declared profile may be opened differently. Not tested. The model is recorded as having no Dolby Vision output (9.3) |
| libstagefright | `vendor.mtk-codec2.thumbnailmode`, `vendor.vdecomx.rotate-degree`, `media.framework.*`, ten "[FWK]" log lines | Thumbnails, rotation, logging | None | None |
| libsfplugin_ccodec | `sys.mtk.running.application` | Read next to `debug.stagefright.c2inputsurface`. Purpose not traced | None | No file in the stock image sets this prop and no stock Java uses it (`OUT/vendor_cites5.txt`). Inert as far as static analysis shows |
| libsfplugin_ccodec | `enqueueSecureInputTasks`, "[FWK]" decrypt logs | Protected input queue | None | None |
| libmediaplayerservice | `media.nuplayer.tunneling`, `determineTunnelingMode`, `isVideoCodecTunnelingSupported`, `findTunnelingCodec`, `media.nuplayer.vpeek.enable` | Tunnel mode: the stock NuPlayer decides by itself to send video to the vendor decoder and video plane | None. The LineageOS NuPlayer uses tunnel mode only when the app asks for it. The prop is unset on the live unit | Candidate, see 9.3 |
| libmediaplayerservice | `vendor.vdecomx.disable-keep-lastframe`, `vendor.vdecomx.rotate-degree`, `libimgplayer.so`, offload props | Last frame, rotation, image player, audio offload | None | None for colour |
| libinputflinger, libinputreader | `sys.dynamic_key_repeat_delay`, "XGIMI X_Remote", `debug.xgimi.mousemode`, `vendor.mtk.amotion.event.button`, `xgimi.music.slide` | Keys and pointer | None (gaps 6 and 10) | None |

`media.nuplayer.tunneling` is written by `init.fusion.rc` only in the `vendor.mtk.stay.awake` triggers, and no file in
the stock image sets `vendor.mtk.stay.awake` (`OUT/vendor_cites5.txt`, `OUT/rc_tunnel_sha.txt`). So in normal use the
default inside the stock library decides. That default was not read from the binary.

The vendor `build.prop` (289-293) also carries `vendor.mtk.exoplayer.audio_only.tunnel=1`, `video.decoder.type=1`,
`seek.mode=1` and `aosp_cn_support=1`. They are vendor props, so the live Lumen unit has them too. No stock binary
in the vendor or system `bin` and `lib` folders and no decompiled class reads them (`OUT/vendor_cites7.txt`,
`OUT/java_ctx5.txt`). A reader inside a stock app that was not decompiled is possible.

### 9.2 Stock-only strings that are not vendor additions

| stock file | strings | reading |
|---|---|---|
| surfaceflinger | `getColorManagement` (3), `color_space_agnostic_dataspace`, `isY410BT2020`, "highp vec3 ToneMap" (4), `GLESRenderEngine` (2) | Code of an older Android SurfaceFlinger generation: a colour management switch and the GLES render engine with its own tone mapping shader. The LineageOS 21 file has the Skia engine only. Which engine stock uses at run time is not known, no stock prop file sets `debug.renderengine.backend` |
| libstagefright, libsfplugin_ccodec | "TunnelPeekState", "Ignoring tunnel-peek", "first tunnel frame ready", "Keep tunneled, drop frame with format change" | Present in the LineageOS source. Log lines that the Lumen build compiles out. Same code expected |
| libsfplugin_ccodec | `hdr10-plus-info` 3 against 2, direct gralloc4 decode calls | HDR static and HDR10+ metadata: `GetHdrMetadataFromGralloc4Handle` and `SetMetadataToGralloc4Handle` exist on both sides (`OUT/hdr_meta.txt`). Lumen goes through `GraphicBufferMapper` (`Codec2Buffer.cpp:1078-1252`). Same function, newer Android code |

The Dolby Vision, HDR and tunnel strings that `OUT/n3.txt` lists for libstagefright, libmedia, libstagefright_omx,
libstagefright_foundation and libmedia_codeclist were compared as sets (`OUT/dolby_strings.txt`). They are identical
on both sides for the last four. In libstagefright nine are stock-only: the two vendor additions of 9.1 and seven
log or assert lines of the standard source that the Lumen build does not keep.

### 9.3 What this says about the dim and pale picture

Facts:

- No string in the patched system libraries points to an XGIMI brightness, gamma or colour setting. The picture
  processing (picture quality engine, HDR tone mapping, Dolby Vision) is in the vendor: `mtkhwcomposer` (strings
  HDR10_PLUS, DOLBY_VISION, HDR_OUTPUT_CONVERSION_CONFIG), the MediaTek TV services and the codec components
  (`media_codecs*.xml`: 22 Dolby Vision lines, 45 tunnelled playback lines, `c2.mtk.dvav.*`, `c2.mtk.dvhe.*`,
  `OUT/vendor_cites8.txt`). Lumen runs all of that unchanged.
- No stock prop file sets a render engine, colour mode or wide colour prop, and the live Lumen unit has none either
  (`OUT/props_colour.txt`, `OUT/vendor_cites6.txt`). The only picture-related prop that differs is the interface
  resolution.
- Live Lumen state, read at the screensaver with no video playing (`OUT/live_sf.txt`): wide colour not supported,
  colour mode NATIVE only, hardware composer reports HDR10, HDR10+ and HLG, no Dolby Vision, peak luminance 500. There
  is no stock capture of the same dump to compare with, neither in the repo nor in the scratch folder.

Candidates, none proven:

1. Path of the video. A player built on Android MediaPlayer goes through the stock NuPlayer tunnel logic on stock and
   through the normal buffer path on Lumen. In tunnel mode the vendor decoder feeds the video plane directly. Apps
   built on ExoPlayer choose the path themselves on both systems, so this candidate does not cover them.
2. Composition of the video layer. If the hardware composer refuses the overlay for a video layer on Lumen (the 4K
   interface plane and the missing `4k2k.2k1k` logic are possible reasons), SurfaceFlinger composes it on the GPU
   with the Skia engine and its own HDR to SDR conversion. That typically looks dimmer and less saturated than the
   vendor video plane. Whether this happens on the Z9X was not observed.
3. Dolby Vision, unlikely. The Lumen code records that this model has no Dolby Vision
   (`apps/LumenProjector/core/.../hal/GmpfClient.java:147-153`, "SupportHashkeyMode=8", picture mode calls 623 and 624
   commented out) and the live hardware composer reports none. The missing profile parsing can then matter only for
   how a Dolby Vision file falls back to its base layer. The vendor codec list still declares Dolby Vision decoders
   (`c2.mtk.dvav`, `c2.mtk.dvav1`, `c2.mtk.dvhe`), so only a test with a Dolby Vision file closes this.
4. Picture mode logic in the apps (stock XRMService AIPQ and SystemUI ContrastPatchHelper against the Lumen
   `AiScenePolicy`). That belongs to `docs/z9x/parity/picture.md` and was not repeated here.

A read-only measurement that would separate them: play the same HDR clip in the app from the complaint, then read
`dumpsys SurfaceFlinger` (composition type and dataspace of the video layer), the gmpf HDR type (620) and the picture
mode. Compare with a stock unit if one is available. No doc in `docs/z9x` or `devices/horse_popular` records a
measured brightness or colour difference so far.

## 10. Not verified

- Nothing in this file was tested on a unit. The live unit was only read (getprop, dumpsys, sha256sum).
- Whether the vendor cuts the light by itself on overheat (3.1). Three virtual calls in the handlers are unresolved.
- User impact of gaps 2, 3, 4, 5 and 9 is inferred from code.
- The exact POWER press at which stock powers off (3.2).
- What the audio HAL does with the three HDMI keys (3.3), and whether anything reads
  `xgimi.bluetooth.abs.volume.enable` (3.4).
- The internals of `keydetectservice` (3.6) and every row of section 9 rest on strings and symbols.
- The default of `media.nuplayer.tunneling` inside the stock library, and the function of `vendor.mtk.inside` and
  `sys.mtk.running.application`.
- HB: "yes" means the stock HB files (G0130 6.16.24, G0148 6.17.12) contain the same marker strings
  (`OUT/vendor_cites2.txt`) and the Lumen search, which covers `apps/HbProjector` and `overlay/hb`, found no
  counterpart. The HB stock Java was not read line by line.
- Functions of APKs outside the decompiled set are inferred from names and manifests.
