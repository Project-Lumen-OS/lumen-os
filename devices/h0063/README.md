# XGIMI TITAN Noir Max (h0063)

Model H0063 (`ro.boot.xgimi.modelname`), product Product_dianahymax, stock update file `XGIMI_HORSEPOPULAR_XR03EOS.bin` (overseas, `xgimiconfig/H0063/system.ini`).
The overseas X50 Ultra Max (g0100) on the X50 firmware line: one XGIMI build carries every model of the line
(`xgimiconfig/<model>` and `xgimimodelname/<model>` chosen at boot), the official overseas FULL OTAs XR03EOS V1.2.54 and
V1.2.99 have the X50 kernel, kernel config, boot ramdisk, fstab, boot HAL, super group and partition list
(gsi-research/H0062_TITAN_NOIR.md). Its `HumanDetect.ini` is the G0100 file (detection process hd_server2).

- State 2026-11-13: trial image built, not published, no unit ever read. Own keys `~/.lumen-keys/h0063` (Mac only), public
  certificates in `certs/` (platform, OTA, APEX `certs/apex`).
- Image: the X50 family recipe (`build.conf`, steps `devices/g0111/image/steps.tsv`) with this model's identity
  (`image/h0063_prop_pub.txt`, `image/h0063_model_prop.txt` = the stock H0063 file by the rule of `x50_props.py`). No tool names
  this model: `devices/g0111/image/x50_family.py` reads the family from the build descriptors.
- In-trial installer (apps/LumenDsuInstall): profile `titanmax`, image `h0063`, DRY rows on v1.2.99 and v1.2.54 (check and dry
  run only), every other version refused. Every other image is refused on it.
- Lumen Installer: `catalog.conf` (tag `titanmax-{version}`, the trial copy `system_dsu.img`). In catalog-v2.json only (Lumen
  Installer 1.0.4 and later): 1.0.3 refuses the whole catalog.json for a model code that is not a G and four digits. The
  entry is trial only on every stock version (no installer.conf).
- HAL: the X50 apps and allow lists unchanged, `org.lumen.hal.GmpfModel` row with `GmpfHash.TITAN_RULE` (the X50 rule plus
  the v1.2.99 hash ad74d059 on exactly v1.2.99). The v1.2.99 table `devices/h0062/research/aidl_codes_v1299.tsv` is the
  v1.2.64 table plus 20 appended methods, none of them ours. Blobs: set `x50-v1264-b` (the v1.2.99 eye-protection pair).
- Menu: `menu.json` follows the stock tree (product dianahypro, the Max tree dianahymax is the same bytes): the X50 Ultra Max's rows with the light level, the iris and the DLP dynamic black (`StockLight`, the stock tree has them), picture
  modes standard / movie / sport / Filmmaker / ISF day / ISF night / performance (`ModelMenu.TITAN`, ids 35 29 9 27 38 39 5).
- `app/*.layers`: the recipes of devices/g0100/app with this profile.
