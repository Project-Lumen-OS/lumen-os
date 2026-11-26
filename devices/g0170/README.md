# XGIMI RS30 Ultra 高亮版 (G0170): Lumen codename g0170

Status (2026-11-21): trial images only (`rs30ultra-tN-net`, published as a beta trial), trial only. Nothing has been
written to any RS30 unit.

## Identity
`ro.boot.xgimi.modelname` G0170, `ro.product.name` Product_athena35ultrahl, `ro.product.device` horse_popular (as the
Z9X, the X50 and the RS30 Ultra Max G0155), board mt5877 / mt9681, 32-bit userspace (armeabi-v7a only), VNDK 34. First
stock read: a Lumen Installer report of a unit on stock v1.2.5 (built 2026-11-12 17:38:12 CST, branch
MT9681_YDN3.5_2-3Q_MP_20260729, kernel 5.15.192, remote B872C). The image says `ro.lumen.device=g0170`,
`ro.lumen.model=G0170`; its own keys live in `~/.lumen-keys/g0170` (Mac only). Analysis:
`gsi-research/G0170_RS30_ULTRA.md`.

## What it shares with the G0155
The XGIMI build of the RS30 3.5 line serves G0155, G0157 and G0167 to G0171 (one xgimiconfig, the model chosen at boot).
So this model is the G0155's data with its own identity: the same arm32 LineageOS base (`devices/g0155/image/BASE.md`),
the same recipe (`devices/g0155/image/steps.tsv`, this model's files and identity as `build.conf` vars), the same X50
family apps under its own recipes (`app/`). Its own data: `menu.json` from its stock tree
(settings_tree_product_athena35ultrahl: the G0155 tree but the single iris f/2.0, f/3.5, f/5.0, dynamic, motion
compensation without the original frame, colour temperature warm for vendor mode 17), the gmpf interface hash of its
stock v1.2.5 (`research/aidl_codes.tsv`), its model props (`image/g0170_model_prop.txt`) and its blobs allow-list
(`image/g0170_blobs_allow.txt`).

## Full dump (2026-11-21)
A LumenDump of a unit on stock v1.2.5 (xgimi-llm `~/devices/g0170_mt9681`: `dump/` as uploaded, `info/` the checks,
`work/fs/` the trees). Every partition of its active slot _b equals the official v1.2.5 FULL OTA byte for byte (boot,
dtbo, vbmeta, vbmeta_system, system, vendor, product, system_ext, tvconfig, tvservice, bootdata, mboot, owl, satf,
the AVB chain verifies); the only difference is a file the unit writes itself in xgimiconfig (its Dolby Vision user
values). The recipe takes the stock kernel from this dump (`build.conf` var.DUMP, KBOOT = its boot_b). The facts the
model data follows are in `research/stock_v125.tsv`, held against the data by `tools/devices/test/stock_facts_gmpf.py`
(one rule for every model with stock facts, mutants: `stock_facts_gmpf_test.py`).

Needs a live check on a unit (not provable from the dump): which remote the B872C is (its HID ids, every XGIMI id is
mapped), that the vendor starts no hd_server2 when eye protection is on (the RS30 detection runs inside xgimi_middle,
HumanDetect SOURCE_MODE 4), the iris values f/3.5 and f/5.0 on the wall, the zoom, shift and pan-tilt rows, HDMI 3
without CEC (stock tbl_Hdmi_Hardware_Mapping), the wall colour camera.

## Trial only
As the G0155: no computer installer profile, no install for good, no OTA, no release channel; the in-trial installer
refuses the RS30 codes (`LumenDsuInstall` Profile.refusal). Lumen Installer starts the published trial from the catalog
(`catalog.conf`).
