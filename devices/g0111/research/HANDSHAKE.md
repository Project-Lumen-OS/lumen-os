# X50 Ultra (G0111): the stock boot handshake with the vendor gmpf service

Read-only evidence from the stock v1.2.64 dump on xgimi-builder (`~/devices/g0111_mt9681/work/fs`, `info/getprop.txt`),
2026-11-07. Native: llvm-objdump (clang-r450784e) on `system/lib64/libbootanimation.so` (aarch64). Java: dexdump
(build-tools 36.0.0) and jadx 1.5 on the Mac copies of `system/priv-app/XRMService/XRMService.apk` (sha256
`47774fc5...302c`), `system/priv-app/SystemUI_release_838/SystemUI_release_838.apk` (`763d9265...8f10`) and
`system_ext/framework/com.xgimi.middle.jar` (`0e860b7a...ff99`); nothing proprietary is in git. Why it matters:
every human-detection path and the keystone start-up thread of the X50 libxgimi wait for `setBootAnimState(2)`
(gsi-research/X50_T4_REVIEW.md A11, B1, B4).

## Who sends what, in which order (same five places as on the HB, `devices/goat_elegant/research/handshake.md`)

| # | when (stock) | call | sender, evidence |
|---|---|---|---|
| A | boot animation process starts (`ro.boottime.bootanim` 6.67 s on the dump) | SystemManager 32 `setBootAnimState(1)` | `libbootanimation.so` `BootAnimation::xgimiSetBootAnimState(bool)` @0x20b70 (gets `xgimi.hardware.gmpf.ISystemManager/default`, log "%s() set boot anim running to gmpf!"), called with `mov w1, #1` from both `BootAnimation` constructors (0x20734, 0x2150c) |
| B | SystemUI starts its services | SystemManager 36 `setSystemUIState(1)` | SystemUI `com.xgimi.systemui.focus.FocusUIV2.start` -> `SystemUtils.systemUIServicesCompleted` (`const/4 #1` -> `setSystemUIState`) |
| C | XRMService is created (persistent) | PowerManager 2 `goToBootCompleted(0)` | XRMService `XRMService.onCreate` -> `initXRMServices` (jadx line 164, `const/4 #0`) |
| D | end of the boot animation | SystemManager 32 `setBootAnimState(2)` | `BootAnimation::threadLoop` calls `xgimiSetBootAnimState(false)` (0x25b20, 0x25d34, `mov w1, wzr`); in that function the false branch logs and calls vtable slot +280 with `mov w1, #2` (0x20d24..0x20d38). Fallbacks with the same value: XRM `BootControlService.smartCurtainAnimExit` / `stopBootanimMask` (`const/4 #2`, jadx lines 379, 638) |
| E | user unlocked and boot animation stopped | PowerManager 2 `goToBootCompleted(1)` | `XRMService.onUserUnlocked` starts `BootControlService` with `acon=true` (jadx line 240); `bootCompletedCheck` re-posts itself every 100 ms while `init.svc.bootanim` is `running`, then `goToBootCompleted(1)` and `xgimi.close.bootlogo=1` (jadx lines 251-262) |

Order: A first (the animation starts long before system_server); B and C come from two processes started at
systemReady (order not fixed on stock; B then C as in HbBoot); D always before E (E waits for the animation to stop).
Nothing is periodic. The vendor detector waits 3000 x 100 ms for D from its own start inside `xgimi_middle`
(`ro.boottime.xgimi_middle` 4.09 s on the dump), so on stock D (end of the animation) lands well inside it.

## Lumen: `apps/X50EyeGuard` sends it (`Handshake.java`, ported from `apps/HbBoot` BootSequence)

The same values in the same phases (A only while the animation runs and 33 does not read 2 yet), once per boot
(boot_id in device protected storage; the app is direct boot aware, so B and C come before unlock as on stock).
`Armed.java` then calls human detection armed only when D reached the service at most 270 s after `xgimi_middle`
started, `init.svc_debug_pid.xgimi_middle` is unchanged since then and 33 `getBootAnimState` reads 2 (read back after
the handshake and every 60 s). Not armed = the light goes off (DisplayManager 66 with false, read back with 67), the
mode goes to 1, and the user is told once that the picture was turned off. Calls: `allowed_x50eyeguard.tsv`.

UNPROVEN until a live run: that the vendor answers these calls from a normally signed app, that HumDet logs
"BootAnimation Finished" instead of "Boot Wait Too Long", whether 66(false) turns the light fully off or dims it,
and whether the vendor keeps 66(false) across a reboot (nothing in this app turns it back on).
