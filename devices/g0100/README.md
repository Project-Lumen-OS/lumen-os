# XGIMI X50 Ultra Max (g0100)

Model G0100 (`ro.boot.xgimi.modelname`), product Product_dianamax, stock restore file `XGIMI_HORSEPOPULAR_XR03E.bin`.
The same XGIMI firmware build as the X50 Ultra (devices/g0111): the stock zip and `.bin` of XR03E and XR04E are the
same files at 1.2.54 and 1.2.64, so the partition layout, kernel, vendor and the blobs allow-list are the X50 Ultra's.
What differs is only the per-model config of the light path (laser current tables, DLP controller image, FPGA image,
temperature tables) and calibration files: gsi-research/X50_ULTRA_MAX_RU.md.

- Image: the X50 recipe `devices/g0111/image/compose_x50.sh` with `X50_CODENAME=g0100` (identity `ro.lumen.device=g0100`,
  `ro.lumen.model=G0100`, the stock G0100 model properties), every APK and the platform set signed with the g0100 keys
  on the Mac (`~/.lumen-keys/g0100`, public certificates in `certs/`).
- Trial only (owner, 2026-11-11): the trial starts; the install from the trial is refused (real and dry, no switch
  opens it) until a dump of a real Ultra Max has been checked. The computer installer has no profile for it.
- Light level (DisplayManager 6 / 7 / 152) and DLP dynamic black (172 / 174) are off on this model
  (apps/X50Projector `Menu.laserRows`, `Gmpf.laserGuard`). No ambient light getter, no laser current call.
- `app/*.layers`: the same recipes as devices/g0111/app.
- Published (2026-11-11, pre-release, public repo Project-Lumen-OS/lumen-os): `x50max-1.0.10-beta1`, image
  `lumen-os-x50max-1.0.10-beta1-nogms-system.img` sha256 4374b14ae2a0ef203bae0ed7e3114eed5f743ab11486a4cc2b16a852d90e39ec
  (build lumen-x50max-1.0.10-beta1-202611111759), channel manifest `x50/update-x50max-beta.json` (+ .sig, OTA key of g0100).
