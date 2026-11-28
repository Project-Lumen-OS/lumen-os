# base_patch: fixes in the code of a base APK

A few bugs of the LineageOS 21 base sit in an app the images take from the base build unchanged, and an overlay
cannot change code. base_patch carries such a fix as the source of the one class it replaces, puts it into the
base APK's dex at signing time and refuses anything it was not written for.

| file | role |
|---|---|
| `patches.tsv` | one row per fix: image path, fix id, class replaced (with its nested classes), the base `classes.dex` sha256 the fix is written for, the patched `classes.dex` sha256. Several fixes of one file apply in the order of the table, each on the result of the row above (its base pin is that row's result pin) |
| `<id>/` | the fix: the replacing class source, `stubs/` for the app's own types it compiles against |
| `base_patch.py` | `build` makes the patched dex, `apply` / `patched_apk()` swaps it into a copy of the APK |
| `DexSwap.java` | dexlib2: the base dex with the class and its nested classes replaced, nothing else touched |
| `tools.tsv` | dexlib2 (Google Maven) and guava (Maven Central), fetched to `~/.cache/lumen/base_patch`, sha256 pinned |
| `test/` | `run.sh` (host tests), `mutate.py` (mutants, all must die) |

The release gate (`tools/release/gate.py`) records every fix per model as a `patch` line: a row of `patches.tsv`
(its row and its folder) in every model, the services.jar fix of the model's base (its `framework.tsv` rows,
`services_hdmi/`, the `services*` tools, the LineageOS patch) and `tool` (the other files). A new or changed fix is
red on each model until its proof (`test/run.sh`, `mutate.py`, the result pin) and `gate.py record`.

Signers: `tools/devices/sign_device.py` (HB, RS30, Titan, `resign_input`), `devices/g0111/image/sign_x50_base.py`
(X50 family, the same `resign_input`) and `tools/play6/sign_play6.py` (Play 6) sign a row of `patches.tsv` from the
patched copy. The Z9X image has no base signer (it patches the published signed tar): its recipe
(`devices/horse_popular/image/steps.tsv`) adds the patched TvSettings signed with the release platform key on the Mac
and drops the base `oat/` folder of the app. The other images keep the base `oat/` files of the patched app: ART finds
their dex checksum stale, runs the app from the APK and compiles it in the background.

`build` checks the base dex against its pin, compiles the fix with `javac --release 17` against
`apps/sdk/framework-full-headers.jar`, dexes it with d8 of build-tools 36.0.0 (`--min-api 34`), swaps it in with
DexSwap and proves with `dexdump -d` that every other class is unchanged (dexlib2 lays the code out again: file and
code offsets, alignment nops, the outs count and explicit default static values are not compared, names, opcodes,
registers and every reference are). Then every method and field the new classes use must resolve: in the dex
(the app's own classes and its libraries, through superclasses and interfaces) or in the class files of the headers
and android.jar, static where the opcode is static (`link`). A stub that names a member the shipped app lacks stops
the build instead of crashing the screen. The result must equal the pin. The same sha256 came out of the Mac (Corretto
21.0.9) and builder-llm (OpenJDK 21.0.13). Another JDK or build-tools version that changes it is a new pin after
the dexdump proof. `patched_apk()` builds the dex into `build/base_patch/<id>/` when it is missing.

## tvsettings_a2dp

Settings > Remotes & Accessories > Add accessory says "Couldn't pair" for a Bluetooth speaker although the speaker
connects (HB 1.0.10-beta14, UGREEN Studio Max5s, 2026-11-14). TvSettings' `BluetoothA2dpConnector` (LineageOS 21 =
Android 14) counts only an A2DP broadcast Connecting -> Connected that arrives after its profile proxy is up. A
speaker that brings its A2DP link up right after the bond (0.9 s on the HB) is connected before that, so the
connector waits for its 10 s timeout and reports failed, and the device stays connected. Android 15 fixed it in
the same class: a target that is connected when the proxy arrives, or at the timeout, is a success
(`isTargetConnected`). The fix is that Android 15 file, unchanged (`tvsettings_a2dp/BluetoothA2dpConnector.java`).
`test/lineage21/` holds the shipped one, and `test/A2dpRaceTest.java` runs both through the orders of events after
a bond (the HB report is S1). The base dex `a28b16be...` is the same on the HB, Play 6, X50, RS30, Titan and Z9X
1.0.11-beta1 images.

```
sh tools/base_patch/test/run.sh
BASE_APK=~/lumen-builder-backup/play6-out/target/product/generic/system/system_ext/priv-app/TvSettingsTwoPanel/TvSettingsTwoPanel.apk \
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 BUILD_TOOLS=~/lumen-tools/android-sdk/build-tools/36.0.0 \
  sh tools/base_patch/test/run.sh          # builder-llm: also the real dex against its pin
python3 tools/base_patch/test/mutate.py
```

## tvsettings_about

Settings > System > About shows what a user needs to find the projector's XGIMI side, for example to go back to the
XGIMI system: right after Model come the rows of the projector app's provider `org.lumen.about`
(`apps/core/lumen-about`): Model code (G0082), XGIMI firmware (V6.15.62) and Back to the XGIMI system, which opens
the app's screen with the USB stick file name and the official XGIMI file. The fix is the LineageOS 21
`AboutFragment` (`test/lineage21_about/`, rev 4bf987d3, the shipped class: same methods, references and constants as
our compile, only the lambda names of R8 differ) with one call at the end of `onCreatePreferences` and the nested
class `LumenRows`, nothing else changed (`test/base_patch_test.py` checks it). `LumenRows` asks only a provider of a
system app, takes only keys that start with `lumen_`, opens only an activity of that same app and leaves the page as
it was when the provider is missing or fails. `test/AboutRowsTest.java` runs that part (`test/about_extract.py`)
on host stand-ins. It is the second fix of TvSettings: base `3753686b...` is the result of `tvsettings_a2dp`.

## tvsettings_focus

The special app access lists an app opens by intent (Notification access, Install unknown apps, Usage access, Display
over other apps, Picture-in-picture, Alarms, ...) are TvSettings' TwoPanel pages on every image. When the first row of
such a list is disabled, the remote is dead there: nothing gets focus, UP / DOWN / OK do nothing, only BACK works
(a test app on a TITAN, 2026-11-28: "Android TV Core Services, Required by system" first). Such rows: the ATV
default notification listener, an app that cannot change (Install unknown apps under a user restriction), every "No
apps" row. androidx disables the whole row view of a disabled preference, a disabled view cannot take focus, and the
leanback list hands the page's focus only to the row at its focus position, so focus falls to the panel's scroll
view. The fix is the LineageOS 21 `SettingsPreferenceFragment` (`test/lineage21_focus/`; our compile has the shipped
class's methods, interfaces and references, only R8's lambda names and `Boolean.TRUE` for `valueOf(true)` differ) with
one method in the adapter of `onCreateAdapter`: after androidx binds a disabled selectable row, its row view is enabled
again. The row stays grey (title, summary and switch keep the disabled state) and OK does nothing on it
(`Preference.performClick` skips a disabled preference), but the remote can land on it and move on. Rows that are not
selectable stay as they were. `test/FocusRowsTest.java` runs the bind (`test/focus_extract.py`) on host stand-ins of
View and androidx Preference for the LineageOS class (no focus) and the fix (`expected_focus_*.txt`). Third fix of
TvSettings: base `16c4aa9a...` is the result of `tvsettings_about`.

Emulator proof (android-34 `default` x86_64 AVD, the TITAN image's TvSettings and RROs re-signed with the AOSP test
key, phone Settings and SystemUI removed, an RRO making a demo app the default listener): D-pad only, the base
leaves focus on `TwoPanelScrollView` (Notification access, Install unknown apps under `no_install_unknown_sources`,
Picture-in-picture with no app); the fix focuses the grey first row, OK leaves it as it is, DOWN reaches Test App and
OK grants its listener (`dumpsys notification`); Install unknown apps, Usage access and Display over other apps toggle
the app op (`appops get`). The android-34 emulator framework lacks `UserInfo.isPrivateProfile()`, which settingslib
calls for app icons, so the app-op lists ran with that one reference pointed at `isManagedProfile()` in both the base
and the fixed test APK (emulator only, never in an image).

## services.jar (`services.py`, `framework.tsv`)

The same idea for the framework: `services_hdmi` is the stock XGIMI eARC link of `lineage/patches/hdmi/0001` in the HDMI
service. A soong rebuild of services.jar is never byte-reproducible (protologtool names a `ProtoLogImpl_<random>` class
in every build, and the jar is R8-shrunk), so `services.py build BASE` (builder-llm, docker) swaps the patched classes
into the base jar instead and pins every result per base in `framework.tsv` (z9x: Z9X, X50 family, TITAN, RS 20;
p6: HB, Play 6; rs30: RS30 lines, MIRA). Proofs in one run: the base build's own soong dexpreopt command
(`services_dexpreopt.sh`, read from the RS30 base build's ninja file; OUT_DIR `out` for the Z9X base, `/out` for the
others, because the app image records the class loader jar's host path) gives the shipped .prof, .bprof, odex, vdex and
art byte for byte on the shipped jar; the LineageOS 21 sources (`services_hdmi/lineage21`, javac of the tree's JDK 17)
have the methods, references and strings of the shipped classes (`src_cmp.py`); every other class of the dex is
unchanged (dexdump, call sites written out), the other dex files are byte equal, `JarLink.java` finds no dangling
reference into the jar and every new platform reference resolves in the headers. Images take the six files with one
recipe step (`tools/build/framework.py`, build.conf `framework=`).

```
JAVAC=~/rs30-base/src/prebuilts/jdk/jdk17/linux-x86/bin/javac python3 tools/base_patch/services.py build z9x \
  --sys SYSTEM_ROOT --hostout BASE_OUT/host/linux-x86 --boot BASE_OUT/soong/dexpreopt_arm64 \
  --profiles ~/rs30-base/src/frameworks/base/services
python3 tools/base_patch/services.py check z9x build/base_patch/services/z9x
```

## service-wifi.jar odex and vdex (`wifi_dexpreopt.sh`)

The arm64 images swap the base's Wi-Fi APEX for one with the patched `service-wifi.jar` (docs/casting/WIFI_P2P_HAL.md).
Its dexpreopt files must come along: odrefresh accepts system server artifacts on /system by their presence, and when
one is missing odsign keeps `odsign.key.done` unset until odrefresh has compiled the jar, while init's post-fs-data
waits for that key. On empty data (a fresh install, a new Lumen OS Live) early-boot, where the vendor loads
`pwm-mt5896.ko`, then came after gmpf_main's 12 s wait for pwmchip8 and the Z9X restarted itself (z9x-installfail-1128).
`wifi_dexpreopt.sh` is the base build's soong command for the jar (LineageOS 21 ninja, module service-wifi), run on
builder-llm with the Z9X base build's host tools and boot image:

```
bash tools/base_patch/wifi_dexpreopt.sh ~/lumen-builder-backup/lineage-out/host/linux-x86 \
  ~/lumen-builder-backup/lineage-out/soong/dexpreopt_arm64 IN JAR OUT
```

IN holds the class loader jars (three from system/framework, the APEXes of the other ten). Proof (2026-11-28): the base
jar d7118f07 with the Z9X 1.0.9 base's own jars gives the shipped odex 064178eb and vdex 42e21396 byte for byte, twice.
The image files: the patched jar 5be60c45 with the image's services.jar 92388c8f (framework.tsv z9x) give odex 751c241d
and vdex 683a088c (the vdex holds the patched classes.dex checksum, the odex the image's services.jar checksum), staged
from `build/wifi_apex/arm64/` and pinned in build.conf (WIFI_ODEX_SHA, WIFI_VDEX_SHA). `build.py check` refuses any
recipe that removes a file below framework/oat (`removes_dexpreopt`, replay: tools/devices/test/first_boot_oat_test.py).
