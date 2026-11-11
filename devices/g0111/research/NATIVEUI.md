# X50 Ultra (G0111): the stock eye-protection drawer `nativeui` on Lumen OS

Read-only evidence from the stock v1.2.64 dump on xgimi-builder (`~/devices/g0111_mt9681/`: `work/fs/` trees,
`info/` captures, `live_20261107_0845/` read-only adb captures of a running stock X50) and from the Lumen base image
`~/x50-out/base/z9x-1.0.9-anp-system.img` (sha256 37f9696cc432aef71df9b7eef04c27231809d0f99e202f94a33e12079c42e2e8,
the base of `x50-t1-net`, mounted read-only). Nothing was run on a projector for this file; nothing proprietary is
in git (hashes, sizes, offsets, short strings only). `libxgimi` = `vendor/lib/libxgimi.so` (32-bit ARM, inside
`xgimi_middle` = `/vendor/bin/hw/xgimi.hardware.gmpf-service`); addresses are its virtual addresses (llvm-objdump of
clang-r510928, literal pools resolved). "UNPROVEN" marks what this evidence does not settle.

## 1. The files (all from the user's own projector, never in git or in an image)

| file (stock path) | size | sha256 | role |
|---|---|---|---|
| `/system/bin/nativeui` | 6021480 | `61e4ee8ec158c7dd5604c7959a0c2d8a73105a77d3701e7f859835b339f3ea5c` | the drawer (aarch64 PIE, `/system/bin/linker64`, BuildID md5 263e8c5e14a9c78ae71c20305c8093de, stripped; Skia statically linked) |
| `/system/lib64/xgimi.hardware.gmpf-V1-ndk.so` | 1852504 | `76e8a5e9f234773263196410dfe67ad5ec16070936268ad04ccc125d1c01e90e` | its only NEEDED library the Lumen system lacks |
| `/system/etc/init/nativeui.rc` | 64 | `6d289394ea73e2130ac7e1caf2c1761372acc44648dc072faaaadd40425869ec` | not copied: `x50_nui.rc` restates it (section 2) |

The live X50 has the same `nativeui` (sha256 and size above, `-rwxr-xr-x root shell
u:object_r:nativeui_main_exec:s0`, `live_20261107_0845/nativeui.txt`). The vendor copy
`/vendor/lib64/xgimi.hardware.gmpf-V1-ndk.so` has the same size but another sha256 (46001bf8...); a system binary
loads the `/system/lib64` one (default namespace), so that one is the blob. Of the nine codec / audio files of the
Z9X set, the X50 dump's copies all have the Z9X sha256 (the audio policy through its `from=` deletes): checked file
by file against `x50_blobs_allow.txt`.

## 2. How stock starts it

- `nativeui.rc` is exactly (`od -c`): `service nativeui /system/bin/nativeui` / `    class main` / `    oneshot`. No
  `user`, `group`, `seclabel` or `capabilities`: root, domain by the file label. It is the only rc file in the dump
  (system, vendor, odm, product, system_ext) that names nativeui; no vendor binary or rc names it otherwise (only the
  SELinux files below), so nothing starts it by name or by `ctl.start`.
- `class main` is started by the stock `init.rc` line 1214-1215 `on nonencrypted` / `class_start main` (the Lumen base
  `init.rc`: line 1208-1209, the same). `nonencrypted` is queued by `mount_all` for the file-encrypted userdata, so it
  runs after `boot`.
- Times (`ro.boottime.*`, `info/getprop.txt` / live `props.txt`): surfaceflinger 2.285 / 2.065 s, xgimi_middle 4.091 /
  3.939 s, zygote 4.287 / 4.138 s, nativeui 6.400 / 6.052 s, bootanim 6.675 / 6.781 s. Live: pid 725, PPid 1, Uid 0,
  `u:r:nativeui_main:s0`, `init.svc.nativeui=running` 2 days after boot (oneshot, never restarted).
- SELinux: `vendor/etc/selinux/vendor_file_contexts` line 445 `/system/bin/nativeui u:object_r:nativeui_main_exec:s0`;
  `vendor_sepolicy.cil` lines 2164-2167 (types), 11120-11125 (`typetransition init_34_0 nativeui_main_exec process
  nativeui_main`, entrypoint, transition) and the attributes `domain` and `coredomain`; no other allow rule names
  nativeui_main. The Lumen base `plat_sepolicy.cil` / `plat_file_contexts` do not name it (0 hits): the image must
  carry the label itself. Stock and Lumen boot permissive (`androidboot.selinux=permissive` on the live cmdline,
  `ro.boot.selinux=permissive`), so denials would not stop it either way.

## 3. What it uses (strings of nativeui, file offsets)

- Files: `/data/vendor/tmp/nuififo` (0x47dc1), `/data/vendor/tmp/retnuififo` (0x54ece), `/proc/self/cmdline`
  (0x4d822). Messages `mkfifo`, `mkfifo %s error` (0x4b2fe), `open fifo %s error(%d)` (0x448f3): it makes both fifos.
- Reply format `%d;%s;%s;%d` (0x47ddc). Tags `NUI(FifoListener)`, `NUI(BaseGraphics)`, `NUI(utils)`, states
  `NUI_INIT[GRAPHICS_INITIALIZED]`, `NUI_SHOW[GRAPHICS_NOT_INIT]`, `NUI_HIDE[GRAPHICS_NOT_INIT]`, graphics
  `protectEyesGraphics`, `generalGraphics`, `akGraphics`, `configGraphics`.
- Properties: only `ro.arch` (0x4a73a). No socket path; the live `/dev/socket` listing has none of its own.
- Binder: it carries the ISystemManager / ISystemCallback AIDL code statically (`aidl::xgimi::hardware::gmpf::
  ISystemManager::hash` and `ISystemCallback::hash` are defined in it) and resolves 0 symbols from the gmpf ndk
  library, which it still NEEDS (the loader needs the file). Which ISystemManager calls it makes (registerNui 14,
  notifyNuiStatus 16, notifyNuiError 17 are the candidates) is UNPROVEN.
- `/data/vendor/tmp` is a plain directory on userdata (`mt_tmp`, vendor_file_contexts line 166; not a mount in
  `info/mounts.txt`): the fifos stay from boot to boot.

## 4. Its dependency closure, stock against the Lumen base image

Recursive `readelf -d` NEEDED, resolved like a /system binary (`/system/lib64`, `/system_ext/lib64`, then the APEX
`lib64` the `/system/lib64` symlinks point into; every APEX of each tree unpacked read-only):
- stock tree: 173 libraries, 0 missing; Lumen base with the user's gmpf ndk library bound at `/system/lib64`:
  182 libraries, 0 missing (173 in `/system/lib64`; libc, libm, libdl, libdl_android in com.android.runtime;
  libicu, libandroidicu, libicuuc, libicui18n in com.android.i18n; the bound one).
- Direct NEEDED of nativeui (24): libcutils liblog libutils libbinder libui libhwui libEGL libGLESv2 libgui
  libtinyalsa libbase xgimi.hardware.gmpf-V1-ndk libETC1 libpng libdng_sdk libjpeg libpiex libexpat libft2 libheif
  libc++ libc libm libdl; of the gmpf ndk library: libbinder_ndk libc++ libc libm libdl. All but the gmpf one are in
  the Lumen base.
- Only in the stock closure: android.hardware.graphics.common-V4-ndk, graphics.composer3-V2-ndk,
  media.bufferpool2-V1-ndk, media.audio.common.types-V2-cpp; only in Lumen's: aconfig_mediacodec_flags_c_lib,
  aconfig_text_flags_c_lib, graphics.common-V5-ndk, graphics.composer3-V3-ndk, bufferpool2-V2-ndk (the Lumen
  libraries' own dependencies; nativeui does not link them).
- Symbols: nativeui has 503 undefined dynamic symbols, the gmpf library 86; every one is defined by a library of the
  Lumen closure (IFUNC mem*/str* included), with no version mismatch (nativeui needs LIBBINDER LIBC LIBC_N LIBC_O
  LIBC_R LIBLOG LIBLOG_M; the gmpf library LIBBINDER_NDK LIBBINDER_NDK30 LIBC). By first definer: libc++ 98, libc 96,
  libft2 50, libpng 45, libdng_sdk 44, libGLESv2 38, libm 37, libjpeg 21, libgui 16, libutils 15, libEGL 13, libexpat 10,
  libbinder 4, liblog 4, libETC1 4, libcutils 4, ...
- ABI: 7 of its 16 libgui functions, 3 of 4 libbinder and 5 of 15 libutils have another st_size on Lumen (other
  builds). The object extents nativeui depends on match: `SurfaceComposerClient::Transaction::Transaction()` stores up
  to offset 344 of `this` in both, `getActiveDisplayMode(..., ui::DisplayMode*)` up to 88 of the mode in both,
  `DisplayState::DisplayState()` up to 72 in both. That it links is PROVEN; that it draws correctly above video on the
  Lumen SurfaceFlinger is UNPROVEN (needs one live run, section 7).

## 5. What the vendor does with the drawer (libxgimi)

- `Msrv_System_Control::nuiCommand(int, const char*, const char*)` @0x199b1b4 (1684 bytes), under its mutex
  (this+248): opens `nuififo` (this+256) `O_WRONLY`, blocking; only a failed open is retried, 40 x `usleep(100000)`
  (log `NUI FIFO not created, waiting...(%d)`), then it returns 0. Same for `retnuififo` (this+356) `O_RDONLY|O_NONBLOCK`
  (`NUI RetFIFO not created`). The fds are cached (this+0xe0, +0xe4) and never reset. It writes
  `snprintf("%d;%s;%s")`, waits with `FIONREAD` until the reader drained it, then `select` 5 s on the reply: timeout
  -> `client timeout.`, error -> `select Error : %s[%d]`, else `read` 200 bytes, log `%s[%d]:%s`, and looks for `;0`.
  It returns 1 in every one of these cases (reply with or without `;0`, timeout, select error); 0 only when a fifo
  cannot be opened in 4 s or the reply fd is >= 1024 (`select create Failed.`). So an existing fifo without a reader
  blocks the vendor's NUI path for ever; a missing fifo costs it 4 s per command.
- Callers: `HumDet_Multtof_Cutout::native_ui_show` @0x1424018 sends `nuiCommand(2, "protectEyesGraphics",
  {"type":2,"config":{"textureType":1,"path":"%s",...,"Points":[...]}})` and returns `nuiCommand != 0`;
  `humdet_by_multtof_cutout` sends `nuiCommand(0, "protectEyesGraphics", {... "Points":[]})` at 0x141ba08 and
  0x141ca70 and calls native_ui_show at 0x141c424 ("Anti mask hide"), 0x141cdf4, 0x141d65c; native_ui_show is also
  called from `setHumanDetectMode` (0x141a93c) and `multtof_cutout_mask_hide` (0x141ad10). At all five
  humdet_by_multtof_cutout sites the result is dropped (r0 is overwritten by the next instruction). `native_ui_init`
  has no caller.
- nativeui's reports: `Gmhal_System::registerNui / _notifyNuiStatus / _nofityNuiError` (the ISystemManager server)
  only store them in `Msrv_System_Control` (registered flag at +0xa4, status string at +165 and int at +0xd8, error at
  +0xdc); `getNuiError()` / `getNuiStatus()` have one caller each, the test functions `getError()` / `getStatus()`.
- So on mask failure (no drawer, drawer crashed, drawer not drawing, error reported) the vendor does NOT fall back:
  no full-screen blank, no light change from the NUI path, the eye-follow mode keeps the picture running. Whether
  mode 0 also lowers the laser by itself is UNPROVEN (X50_HARDWARE_SAFETY.md E.5). Whether xgimi_middle ignores
  SIGPIPE (a write into a fifo whose reader died) is UNPROVEN.

## 6. NativeUIWatch (the UI heartbeat, DisplayManager 99 / 100)

- Armed on stock by XGIMI SystemUI: `EyeProjectionDialog.setLowLight` -> `HumanDetectUtils.uiHeartBeatSet(
  "NativeUIWatch", 1, 1500)`, fed by `uiHeartBeatDo("NativeUIWatch")` (EyeProjectionDialog.java:127), disarmed in
  `restore` with `("NativeUIWatch", 0, 1500)` (jadx of `SYS/priv-app/SystemUI_release_838`). The only other dex with the string is
  vendor `GlobalConfig_release838` (not decompiled here, no jadx on the builder: what it does with it is UNPROVEN;
  Lumen hides /vendor/priv-app). Nothing in libxgimi calls `UIHeartBeatSet` itself (no call site of any of its five
  implementations except the AIDL entry `Gmhal_XlabCtrl::UIHeartBeatSet` -> `XlabControl::UIHeartBeatSet`), and
  nativeui has no DisplayManager code: the heartbeat belongs to the full-mask dialog (500/601), not to nativeui.
- `HumDet_Process::heartBeat()` @0x1450cbc: after `hdWaitForSystemBootComplete`, polls every 1 ms; when a period
  passes unfed it logs `fed dog fail` and calls vtable slot +0x34 with 1, which is `setHumanDetectScreenOnOff(1)` in
  all four HumDet vtables: the vendor turns the picture back ON when the UI stops beating. It restores light; it does
  not protect.

## 7. Open (one read-only look at a live X50 settles each)

1. Does stock nativeui on Lumen draw the mask above the picture (`dumpsys SurfaceFlinger --list` for its layer while
   a 502 is up; a person in front)? Only provable on the projector, with the Lumen image.
2. Which ISystemManager calls nativeui makes and when (logcat `NUI(` lines are blocked on stock: dmesg / a Lumen boot).
3. Mode 0 and the laser: does 502 lower the light too (`Request_Display_CurrentLevel` in the vendor log)?
4. Does xgimi_middle survive a nativeui crash (SIGPIPE on the cached fifo fd)?
5. GlobalConfig's use of NativeUIWatch (decompile it on the builder).
6. Does 126 setHumanDetectMode(1) answer while nativeui is down (it calls `native_ui_show`, section 5: 4 s on removed
   fifos, possibly a write into a dead reader on a fifo the vendor still holds), and does mode 1 then blank or dim on
   Lumen without the stock CoverDialog?

## 8. The Lumen fail-safe: `apps/X50EyeGuard` (org.lumen.x50.eyeguard)

The vendor never falls back (section 5), so Lumen does: a small X50-only persistent app, separate from any projector
app (the X50 image has none; a package named org.lumen.projector would also change Lumen TV input's Source-key and
picker behaviour, which key on that package being installed).
- Signal: `sys.lumen.nui` (x50_blobs.sh: `ok`, `missing`, `missing:<path>`, `bad:<why>`, `target:`, `noplaceholder:`,
  `option:`, `mount:<path>`; x50_nui.rc: `down`), `init.svc.nativeui` and `sys.boot_completed`, read every second from
  process start and again at BOOT_COMPLETED (init does not notify apps of property changes, so polling is the watch;
  a nativeui exit shows as `init.svc.nativeui=stopped` and, one trigger later, `sys.lumen.nui=down`). Eye follow is
  allowed only with `ok` + `running`; before boot completed, `ok` without `running` (or no value) waits; any other
  value, known or not, is unsafe (fail closed).
- Action while unsafe: DisplayManager 127 getHumanDetectMode; if not 1, 126 setHumanDetectMode(1) (the constant, the
  only value the transport can write), then 127 again; the switch counts only when it reads 1. Again every 60 s while
  unsafe, back-off 2/5/10/30/60 s on failure, one HAL call in flight at most (126 goes through `native_ui_show`, so it
  may block like any NUI command; a call running for 15 s counts as failed for the notice). Mode 0 is never written,
  and the mode is not switched back when the drawer returns (user's choice). Allow list
  `research/allowed_x50eyeguard.tsv` (meta 2 + 127 + 126), checked by `tools/devices/check_aidl_hal.py` on the
  sources and the dex; interface guard version 1 + hash 715affd0, else nothing is sent.
- The user is told once per episode, at the earliest 10 s after boot completed: eye follow not available, the reason
  (files not installed / a file failed its check / could not be set up / stopped / not running), switched to the
  full-screen mode, do not look into the lens; or, after 3 failed tries or a stuck call while the full-screen mode is
  not confirmed, that the projector did not take the switch. Dialog as a persistent system app, long toast otherwise.
  Never a claim that the person is protected.
- Gate: `ro.lumen.device=g0111` and `ro.boot.xgimi.modelname=G0111` (and `ro.xgimi.modelname` empty or G0111), else
  idle. No Z9X / HB / Play 6 file or Z9X gate input is touched.
- Tests: `sh apps/X50EyeGuard/test/run.sh [APK]` (294 host checks + check_aidl_hal on sources and dex; 17 mutations
  of the state rules, scheduler, read-back, setter value, call list, X50 gate and strings all turn it red).
- In the image since t3 (`lumen-x50-t3-202611070745`): `/system/app/X50EyeGuard/X50EyeGuard.apk`, built and signed
  with the X50's own platform key on the Mac (`tools/devices/build_apps.sh g0111 build/g0111/stage`), registry
  `apps`, `hal_check aidl-table`, `hal_app`, `hal_allowed`; `image/verify_tree_x50.py` HAL_APP lets only this APK
  name the gmpf HAL, only byte-identical to the staged one, only with the g0111 signer and exactly the four calls
  above (read from its dex by `image/x50_apk_check.py`; `test/x50_apk_check_test.sh`). In that image it is not a
  platform app (the framework keeps the Z9X 1.0.9 release key): a system app with the default SELinux domain,
  which needs no permission beyond RECEIVE_BOOT_COMPLETED. UNPROVEN until a live run: item 6 above, that such an
  app reaches the gmpf service and reads `init.svc.*` (permissive boot), and the dialog on the X50.
- 2026-11-07 (branch x50-boot): nativeui running is not enough, the vendor detector only runs after the stock boot
  handshake (X50_T4_REVIEW.md B1). The app now sends it and checks that detection is armed; not armed = light off
  (66 false) + mode 1 + notice. Ten calls (`allowed_x50eyeguard.tsv`), evidence and rules in `HANDSHAKE.md`.
