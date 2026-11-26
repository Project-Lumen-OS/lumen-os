# Wi-Fi Direct on the MediaTek supplicant (why phones could not connect, and the fix)

## Facts (proven from files, 2026-11-23)

- Every MT9952/MT9681 stock dump we have ships the same 64-bit `vendor/lib64/android.hardware.wifi.supplicant-V3-ndk.so`
  (sha256 b03a7ca91fa7..., interface hash `4652398bc565e185ec0799d2a9ea9db1774710bc`): Z9X G0082 v61519, v61558,
  v61562, X50 Ultra G0111 (v1.2.64 dump), TITAN H0062 v1.2.54 and v1.2.99. RS30 Ultra Max G0155 has the 32-bit build of
  the same interface (same hash). Its vintf manifest says `android.hardware.wifi.supplicant` version 3.
- Z9X HB G0130 / G0148 declare the supplicant without a version (V1): not affected, the patch changes nothing there.
  Play 6 has a HIDL supplicant: never reaches this code.
- The frozen V3 hash (AOSP `aidl_api/android.hardware.wifi.supplicant/3/.hash`) is `55b58c9bd6d40c1459073b5d03f4ede5cfc9a212`.
- Transaction codes of the MediaTek library (the `mov w1, #code` before `AIBinder_transact` of every Bp method, and the
  Bn `onTransact` jump table, which agree): ISupplicantP2pIface codes 1..63 are exactly frozen V2, code 64 is a vendor
  call `startWpsPinKeypadWithMac`, nothing above 64. Frozen V3 puts `connectWithParams` at 64, `findWithParams` at
  65, `configureExtListenWithParams` at 66. ISupplicantP2pIfaceCallback is exactly V2 (19 calls). ISupplicant,
  ISupplicantStaIface, ISupplicantStaNetwork and their callbacks are exactly V2 as well.
- So the LineageOS Wi-Fi module (built against frozen V3, `getInterfaceVersion()` answers 3) sends
  `configureExtListenWithParams` (66, "Method ... is unimplemented", the framework then drops the P2P interface:
  every later P2P call fails with "ISupplicantP2pIface is null", the projector is invisible) and would send
  `connectWithParams` (64), which the vendor runs as `startWpsPinKeypadWithMac` with a P2pConnectInfo parcel. A fallback
  that only catches "unimplemented" cannot fix the connect call: the decision must come from the interface hash.
- Stock works because XGIMI's framework is built against the same pre-freeze interface.
- STA side: the module sends V3 STA calls only for MSCS (a system API nothing on a projector calls) and on Android 15+;
  STA keeps working (proven on every unit).

## Fix

`lineage/patches/wifi/0001-p2p-supplicant-v3-snapshot.patch` (packages/modules/Wifi at the pinned revision):
`SupplicantP2pV3Calls` decides each V3 P2P call from the interface hash. Only the frozen V3 hash gets
`connectWithParams`, `findWithParams` and `configureExtListenWithParams`. Any other hash (the MediaTek one, an unknown
draft, a hash that could not be read) gets the V2 calls `connect`, `find`, `configureExtListen`. A V3 call that still
answers "unimplemented" is switched off for the life of the interface and the V2 call is made, the interface is kept.
V1/V2 supplicants and frozen V3 ones get exactly the calls they got before.

Tests: `sh lineage/patches/wifi/test/run.sh` (the class from the patch itself, 63 checks, and `wiring.py` on the
patched SupplicantP2pIfaceHalAidlImpl), `python3 lineage/patches/wifi/test/mutate.py` (31 mutants), `sh
lineage/patches/wifi/check.sh` (applies to the pinned source).

## Build and delivery

- `service-wifi.jar` is built from the pinned tree (the LineageOS source mounted read-only in the `lineage-builder`
  container, the patched p2p directory mounted over it, an own out dir, product gsi_tv_arm64). The unpatched control build must give the base's `classes.dex` (sha256 48c28a60...), which both bases
  (Z9X 1.0.9 arm64 and the 32-bit HB/Play 6 base) carry.
- The APEX: the base's original `com.android.wifi.capex` (sha256 80a4410e..., from the Z9X 1.0.9 base tar) through
  `tools/sign/apex_sign.py run ... --swap system/apex/com.android.wifi.capex:javalib/service-wifi.jar=JAR@d7118f07...`:
  the same re-signing as every Z9X APEX (Lumen `com.android.wifi` keys, keys stay on the Mac, payload rebuilt with
  apexer on the build host, compressed again). Without `--swap` that run reproduces the shipped 1.0.9 capex byte for byte
  (eea71051..., 2026-11-23).
- The image: the capex replaces `system/apex/com.android.wifi.capex` (owner, mode, label as the base's) and the base's
  dexpreopt files of the old jar go (the `.odex` and `.vdex` of `service-wifi.jar` in `system/framework/oat/arm64`). One file serves the arm64 base: Z9X, X50 Ultra, X50 Ultra Max, TITAN H0061 to H0063. RS30 (32-bit base,
  AOSP module test keys) needs its own build of the same jar into its own capex.

## Live check (X50 Ultra)

Logcat `SupplicantP2pIfaceHalAidlImpl: Supplicant P2P interface: V3 hash 4652398b... (not the frozen V3, V2 calls)`,
no "is unimplemented", no "ISupplicantP2pIface is null", `dumpsys wifip2p` without DISABLE after the first discovery,
the Miracast card "ready", `/apex/com.android.wifi/javalib/service-wifi.jar` = the patched jar. A phone connecting
needs a person at the unit (TEST_PLAN_RU.md).

## Proofs before the merge (2026-11-23, nothing live yet)

- STA untouched: `python3 lineage/patches/wifi/test/dexclass_diff.py BASE.jar PATCHED.jar OUT` compares every class
  of the base `service-wifi.jar` (d7118f07...) with the patched one (5be60c45...) on the normalised `dexdump -d`
  (names, flags, signatures, every instruction with its operands, try/catch, line tables, the head of each payload).
  3947 classes, 3944 identical. Different: `SupplicantP2pIfaceHalAidlImpl` (the patch), the new
  `SupplicantP2pV3Calls`, and `ISupplicantP2pIface` with its `Stub$Proxy`, which only gain `getInterfaceHash()` (R8
  keeps it now that the P2P HAL calls it; no line of the old code changes). Every STA, scan, SoftAP, Aware and
  ClientModeImpl class and every other supplicant interface is identical. The jar differs from the base only in
  `classes.dex`, and the signed capex differs from the reproduced 1.0.9 one only in that jar (`apex_signed.json`).
- X50 image check: the published X50 Ultra 1.0.10-beta13 image with only the recipe's edit (the capex replaced, the
  two dexpreopt files gone) passes the new `verify_tree_x50.py` (removed 61, changed 66), the unedited image is
  refused by it (the base capex, the oat files still there) and passes the old one.
- Recipes: `build.py check` 0 errors for horse_popular, g0111, g0100, h0061, h0062, h0063 (and the untouched g0155,
  g0170, goat_elegant, dragon_healthy), `build.py plan` places the capex and removes the two oat files in each.
- RS30 (G0155, G0170): not shipped, its 32-bit base needs its own capex.
