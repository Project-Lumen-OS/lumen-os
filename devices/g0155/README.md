# XGIMI RS30 Ultra Max (G0155): Lumen codename g0155

Status (2026-11-12): TEST images only (`rs30-tN-net`), trial only. Nothing has been written to any RS30 unit.

Owner decisions (2026-11-12): Lumen OS reaches this model only as a trial, like the X50 (no computer installer, no
install for good); features exist in its menu only where its stock has them; the pan-tilt head, 3D and the 29 calls
new against the X50 come later.

## Identity
`ro.boot.xgimi.modelname` G0155, `ro.product.name` Product_athena35ultramax, `ro.product.device` horse_popular (as the
Z9X and the X50), board mt5877 / mt9681, stock v1.1.42 (built 2026-10-07), VNDK 34, slot `_b` live. The pair
(horse_popular, G0155) is its own registry entry with its own keys (`~/.lumen-keys/g0155`, Mac only) and image props
`ro.lumen.device=g0155`, `ro.lumen.model=G0155`. Stock analysis: `gsi-research/RS30_ANALYSIS.md`, `RS30_PARITY.md`.

## Image
- Base: `image/BASE.md` (own arm32 LineageOS 21 build with uffd GC on builder-llm).
- Recipe: `build.conf` + `image/steps.tsv` of the one image builder (`tools/build`), composed on builder-llm
  (`tools/image/compose.sh g0155 tN net` from the Mac, `image/compose_rs30.sh` on the host): every APEX uncompressed
  (no `.capex` for apexd's first boot), the debloat of `image/debloat.txt` (LineageOS setup wizard, sample launcher,
  updater, emulator overlays), identity props with USB in host mode (`persist.sys.usb.config=mtp`, as stock) and
  `ro.build.characteristics=tv`, network adb, the vendor app folders hidden, the X50 blobs reader with
  `image/rs30_blobs_allow.txt` (read only from the stock slot in a trial) and the stock eye-protection drawer, Lumen's
  key layouts in `/system/product/usr` with the X50 power-key edit (the RS30's own 000d:3840 scancodes 172, 135, 136,
  217, 234 unmapped), then on the signed stage the platform set on the g0155 key and the X50 family apps. Host checks:
  `test/rs30_image_test.sh`.
- Variants: `net` (the trial image, needs the Mac's signed stage) and `unsigned` (base test keys, no Lumen app: a host
  build check only, never for a unit).
- Signing: `tools/devices/sign_device.py g0155 all` on the Mac (`image/sign.conf`), keys never leave the Mac.

## Apps
X50EyeGuard, X50PowerOverlay (gear, its hold, projector keys and power), X50Projector and X50TvInput (org.lumen.tvinput,
the HDMI inputs behind the input, HDMI and game rows: the same tv.input AIDL HAL as the X50) under their own recipes
(`app/`), the same code as the X50: every HAL code they send is
proven against this model's stock table (`research/aidl_codes.tsv`); only the interface hash differs
(`org.lumen.hal.GmpfModel`). The menu profile `menu.json` offers only what the stock settings tree has
(`research/RS30_MENU_STOCK.md`), Performance (id 5) included behind the stock overheating warning (this model has no
Boost).
