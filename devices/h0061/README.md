# XGIMI TITAN Noir (h0061)

Model H0061 (`ro.boot.xgimi.modelname`), product Product_dianahy, stock update file `XGIMI_HORSEPOPULAR_XR06EOS.bin` (overseas, `xgimiconfig/H0061/system.ini`, `DeviceShowName = "TITAN Noir"`).
The smallest model of the overseas TITAN Noir line (XGIMI: TITAN Noir 4800 ISO lumens with 30 laser chips, Noir Pro 6000 with 40,
Noir Max 7000 with 50) on the very XGIMI build of the TITAN Noir Pro (devices/h0062) and Max (devices/h0063): the official
overseas FULL OTAs XR03EOS V1.2.54 and V1.2.99 carry `xgimiconfig/H0061` and `xgimimodelname/H0061` next to H0062 and H0063,
and the download server gives the XR06EOS file of V1.2.99 with the same size and ETag as XR03EOS and XR04EOS. Evidence and the
config folder comparison: gsi-research/H0061_TITAN_NOIR.md.

- State 2026-11-19: trial image only, no unit ever read. Own keys `~/.lumen-keys/h0061` (Mac only), public certificates in
  `certs/` (platform, OTA, APEX `certs/apex`).
- Image: the X50 family recipe (`build.conf`, steps `devices/g0111/image/steps.tsv`) with this model's identity
  (`image/h0061_prop_pub.txt`, `image/h0061_model_prop.txt` = the stock H0061 file by the rule of `x50_props.py`; the stock file
  is the H0062 one with product dianahy).
- Same as the Pro: feature.xml, keypad.ini, the eye detection file `xlab/HumanDetect.ini` (only its own path differs:
  detection process hd_server2), the HDMI port map, the newsettings tree (`settings_tree_product_dianahy` is res/FP.json, the
  same bytes as dianahypro and dianahymax).
- Own: the light engine tables (`dlp/dlp.ini`, `dlp01.ini`, `dispcomm.ini`), no `dlp/FlashImage.img` and no `dlp/cali`
  per colour space calibration LUTs, calibration files, EDID files, temperature control tables, and the stock default light
  level 3 (`env/CustomerEnvTbl.xml` prepare_env_lumen_level, the Pro 2). None of these is a Lumen setting.
- In-trial installer (apps/LumenDsuInstall): profile `titannoir`, image `h0061`, DRY rows on v1.2.99 and v1.2.54 (check and dry
  run only), every other version refused. Every other image is refused on it.
- Lumen Installer: `catalog.conf` (tag `titannoir-{version}`, the trial copy `system_dsu.img`), in catalog-v2.json only (Lumen
  Installer 1.0.4 and later). Trial only on every stock version (no installer.conf).
- HAL: the X50 apps and allow lists unchanged, `org.lumen.hal.GmpfModel.TITAN_NOIR` with `GmpfHash.TITAN_RULE` (one build with
  the Pro). Blobs: set `x50-v1264-b` (the v1.2.99 eye-protection pair).
- Menu: `menu.json` = the Pro's rows (the same stock tree and feature.xml), picture modes `ModelMenu.TITAN`.
- `app/*.layers`: the recipes of devices/h0062/app with this profile.
