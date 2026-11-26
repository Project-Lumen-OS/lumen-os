# RS30 old line (RS30 Pro G0125, Pro Max G0124, Ultra G0114, Ultra Max G0115)

One XGIMI build (`MTK9681/athenathirdultra`, job DOHKO, branch `MTK9681_YDN3_FACTORY_release_*`) serves the four
models, the model chosen at boot from `ro.boot.xgimi.modelname`. Lumen OS reaches them only as a trial on their stock
v1.1.47 or v1.2.27 (the two versions users run). Nothing was ever written to one of these units, and no dump of one
exists: every fact comes from the official FULL OTAs, unpacked on xgimi-llm `~/devices/rs30old_fw/v1147` and `v1227`
(`research/stock_v1147.tsv`, `stock_v1227.tsv`). The G0170 proved this route: its trial built from the official zip
was later byte-identical to a full dump of a unit.

| model | codename | product | restore file |
|---|---|---|---|
| RS30 Pro | g0125 | Product_athena3pro | XGIMI_HORSEPOPULAR_XR04C.bin |
| RS30 Pro Max | g0124 | Product_athena3promax | XGIMI_HORSEPOPULAR_XR06C.bin |
| RS30 Ultra | g0114 | Product_athena3ultra | XGIMI_HORSEPOPULAR_XR07C.bin |
| RS30 Ultra Max | g0115 | Product_athena3ultramax | XGIMI_HORSEPOPULAR_XR08C.bin |

The four files of one version are one object on the CDN (equal plain ETag and size), each model keeps its own name.

What the line shares with the RS30 family: the arm32 base and recipe of the G0155 (`devices/g0155/image/steps.tsv`),
the kernel line (5.15.192, userfaultfd), the boot HAL, fstab and the VNDK. Its gmpf interface is the RS30 Ultra v1.2.5
table (`research/aidl_codes_v*.tsv`, `GmpfHash.RS30_OLD_RULE`): the shift flag at ProjectorFocusManager 107, no lens
memory, no isKeystoneAdjusted. Its own data: this line's menu layer (`rs30old.menu.json`), the blob allow list of both
versions (`image/rs30old_blobs_allow.txt`), each model's stock model props, wall colour and settings tree rows.

Open, needs a unit: the Boost step its feature.xml names (`support_brightness_boost 2`), the screen memory row of its
tree, the remote (vendor 3840 layout without the RS30 Ultra Max picture keys), the iris refusal modes of its own pq
database (taken from the RS30 rule), eye protection on the vendor ToF (HumanDetect SOURCE_MODE 4, as the G0155).
