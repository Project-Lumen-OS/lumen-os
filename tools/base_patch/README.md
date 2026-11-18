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
