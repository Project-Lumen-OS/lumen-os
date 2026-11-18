# lumen-about: the XGIMI side in Settings > About

Users find their XGIMI model code, the XGIMI firmware the projector came with and the way back to the XGIMI system
in the system Settings About page (Settings > System > About), on every projector. One module, no model code: what a
model has is data (its own config on the projector, its rows in Lumen Installer's catalog).

## Rows of the About page

TvSettings' About page (`AboutFragment`, the base_patch fix `tools/base_patch/tvsettings_about`) asks the provider
`content://org.lumen.about` (method `rows`, `AboutProvider`) and adds the rows it gets right after Model, as plain
stock rows. Without the provider (an image without this module) the page stays as it was.

| row (in this order) | value | source |
|---|---|---|
| Model code | G0082, G0130, H0062 ... | `ro.boot.xgimi.modelname` (set by the boot loader), else `ro.xgimi.modelname` |
| XGIMI firmware | V6.15.62 from 2026-03-25 | `ro.vendor.build.version.incremental` (the XGIMI vendor Lumen OS runs on, the version the unit came from) and the date of `ro.vendor.build.date`; on the XGIMI system itself `ro.build.version.incremental` and `ro.build.date` stand in when the vendor ones are hidden |
| Startup firmware | 01.01.260325 | `ro.bootloader` (the MediaTek boot loader's version), only in the form NN.NN.NNNNNN: HB's 123456789 and Play 6's 20170621 are placeholders |
| XGIMI interface | GMUI 6.17.200.03 | `ro.xgimi.gmui.version` of the vendor (Titan's `overseas_` prefix dropped), what the stock settings show as GMUI |
| MediaTek platform | t-apollo-mp-2101-cnu-mt9681-55-001-112 | `export VERSION :=` of `/vendor/etc/version.txt` |
| DLP controller | TI DLPC8445, firmware 9.4.128 | `DlpType` of `/mnt/vendor/xgimiconfig/<model>/dlp/dlp.ini`, named by the enum in that file's own comments, and `DlpFlashImageVersion` of `dlp/DlpFlashImageInfo.ini` (the controller image the XGIMI firmware carries) |
| FPGA | 160T, PH1A400 | `extFPGAType` of the same dlp.ini, named by its comments (`E_FPGA_DEV_TYPE_*`), none for type NONE |
| Remote | B940D, firmware 1.0.3 | `persist.xgimi.rc.version` (XGIMI's Bluetooth service stores the remote paired last: model, then `_V` and its firmware) |
| Wi-Fi | MT7920, firmware 20260205002547 | `vendor.wlan.firmware.version` (the MediaTek Wi-Fi firmware: chip and build stamp) |
| Back to the XGIMI system | the stick file name, else "With the XGIMI recovery USB stick" | opens the screen below |

The same rows (`AboutRows.versions`, model data none, all in `FirmwareParts` and `LightEngine`) make the card Firmware
versions of Lumen Installer on the XGIMI system, plus Android with its security update (`ro.build.version.release`,
`ro.build.version.security_patch`) and the kernel (uname release, version part only), which the About page lists on
its own. Lumen Installer takes the plain classes and the texts through `apps/LumenInstaller/lumen-about.files` (no
copy) and looks a text up by its name (`lumen_about_<rowtext>`). On the XGIMI system an ordinary app reads all of it:
every dumped stock firmware boots with SELinux permissive and the config files are o+r (`test/models/stock_app.tsv`
also lists what each image's policy would hide if one ever boots enforcing: those rows then simply stay away). No
version of the MCU, keystone or focus motor, picture engine (PQ) or audio processor is readable without a factory
call: the stock firmware names none in a property or a readable file, so no row. Per-model rows from the dumps:
`test/models/` and `AboutTest.modelTests`.

A unit without a model code gets no rows. The firmware row is left out when the vendor names no version. The DLP
controller and FPGA rows (`LightEngine`) are left out when the model's config does not name them: no model data in the
code, the unit's own file is the data. From the firmware dumps (2026-11-18): Z9X G0082 TI DLPC8445 (no flash image
file, no FPGA), Z9X HB G0148 TI DLPC8445 firmware 6.12.12 (no FPGA), X50 Ultra G0111 TI DLPC8445 9.4.128 FPGA 160T,
X50 Ultra Max G0100 9.3.128 to 9.3.130 FPGA 160T, TITAN H0062 9.4.4 to 9.4.7 and H0063 9.3.51 to 9.3.63 FPGA 160T,
RS30 Ultra Max G0155 TI DLPC8445 11.4.16 FPGA PH1A400, Play 6 G0058 TI DLPC3436 (`DlpType = 9`, its FPGA line not
seen yet). Not read live yet: whether each image's SELinux policy lets the app read `dlp/` (like `system.ini`, the
rows simply stay hidden when it does not). The stock settings app shows none of this: its system info screen has no
DLP or FPGA row, and the only getter of a device version is a factory call, which Lumen never makes.

## Back to the XGIMI system

`XgimiSystemActivity`: model code, XGIMI firmware, the exact file name the stick needs and, when the catalog knows
it, the official XGIMI file for this model and firmware (version and size in bytes) with its QR code; else the QR
code of the install guide's Back to stock section (Russian for ru, uk, be, kk). It only shows facts, nothing on the
projector changes: the way back stays the XGIMI recovery USB stick, Lumen OS itself never switches back.

- File name: `UpdateBinName` of `/mnt/vendor/xgimiconfig/<model>/system.ini` (what the stock boot loader looks for),
  else the catalog's `restore.file` of the model (an image whose SELinux policy keeps the app off that file).
- Official file: Lumen Installer's signed catalog (`catalog-v2.json` + `.sig` on the release `index` of
  Project-Lumen-OS/lumen-installer, verified with the list certificate `assets/lumen_about/index.x509.pem`, the same
  file as `apps/LumenInstaller/assets/index/index.x509.pem`). Each model entry may carry
  `"restore": {"file": NAME, "files": [{"stock": "v6.15.58", "version": "v6.15.62", "size": N, "url": URL}]}`, made
  by `tools/devices/installer_catalog.py` from `restore_*` keys of `devices/<codename>/catalog.conf`. A row is used
  only when its address is on XGIMI's firmware server and ends in the stick name, when that name is the one the
  unit's own config names and when its version is not older than the unit's firmware: the stick may refuse a file
  older than the projector's firmware (XGIMI's anti-rollback). The generator keeps the same rule: each stock goes back
  with its own version, with a newer official one only when its own has no file, never with an older one. The last verified copy is kept in the app's files (offline), a newer one is read when the
  screen opens; an older one never replaces it.

| model | stock -> official file (catalog.conf) |
|---|---|
| G0082 Z9X | V6.15.19 -> V6.15.19, V6.15.58 and V6.15.62 -> V6.15.62 (`XGIMI_HORSEPOPULAR_XR03D.bin`, V6.15.58 is only in XGIMI's debug tree) |
| G0130 HB | V6.16.24 -> V6.16.24, V6.17.12 -> V6.17.12 (`XGIMI_GOATELEGANT_XR03D.bin`) |
| G0148 HB | V6.17.12 -> V6.17.12 |
| G0058 Play 6 | V1.7.116 -> V1.7.116, V1.8.127 -> V1.8.127 (`XGIMI_DRAGONHEALTHY_XN03C.bin`) |
| G0111 X50 Ultra | V1.2.54 -> V1.2.54, V1.2.64 -> V1.2.64 (`XGIMI_HORSEPOPULAR_XR04E.bin`) |
| G0100 X50 Ultra Max | V1.2.54 -> V1.2.54, V1.2.64 -> V1.2.64 (`XGIMI_HORSEPOPULAR_XR03E.bin`) |
| G0155, H0062, H0063 | none known yet: the stick name from the unit, the guide's QR code |

The published catalog carries these rows once it is built and signed again (until then the screen shows the stick
name and the guide).

## Wiring

Every projector recipe (`devices/*/app/*Projector.layers`) names `module lumen-qr` and `module lumen-about`, every
projector app manifest declares the provider (authority `org.lumen.about`, exported, read only) and the activity
(exported: TvSettings opens it). Texts: `res/values*/lumen_strings_about.xml`, 18 files.

## Tests

`sh apps/core/lumen-about/test/run.sh` (host, JDK 17+, ANDROID_SDK with android-34): XgimiFacts (properties,
firmware forms, system.ini forms), the rows, the catalog (signature, frame, size cap, official addresses, version
mapping, which copy is kept, the published catalog with the real certificate in `test/live/`), WayBack, the Android
compile, `check_repo.py` (texts, wiring, the TvSettings contract, read only, no model data in the code) and
`tools/i18n/ui_words.py`. `python3 apps/core/lumen-about/test/mutate.py`: every mutant must be killed. The TvSettings
side: `sh tools/base_patch/test/run.sh`, the catalog side: `tools/devices/test/installer_catalog_test.py`.
