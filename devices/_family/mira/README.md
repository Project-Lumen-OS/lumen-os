# MIRA line (MIRA 4K G0109, MIRA 4K Pro G0110, AURA 3 Max G0104)

One XGIMI build (`MTK9681/bollywoodproguonei`, job OSCAR, branch `MTK9681_BollywoodGN_Release_MP_*`) serves the
models, the model chosen at boot from `ro.boot.xgimi.modelname`. Lumen OS reaches them only as a trial on their stock
v1.3.1. Nothing was ever written to one of these units and no dump of one exists: every fact comes from the official
FULL OTA, unpacked on xgimi-llm `~/devices/mira_fw/v131` (`research/stock_v131.tsv`), the route the G0170 and the RS30
old line took.

| model | codename | product | restore file |
|---|---|---|---|
| MIRA 4K | g0109 | Product_bollywood | XGIMI_HORSEPOPULAR_XR03G.bin |
| MIRA 4K Pro | g0110 | Product_bollywoodpro | XGIMI_HORSEPOPULAR_XR06G.bin |
| AURA 3 Max | g0104 | Product_oscar | XGIMI_HORSEPOPULAR_XR03F.bin |

The files of v1.3.1 are one object on the CDN (equal plain ETag and size), each model keeps its own name. The same
build also serves the AURA 3 Pro G0106 (oscar4q, XR06F): not a Lumen model yet.

What the line shares with the RS30 family: the arm32 base and recipe of the G0155 (`devices/g0155/image/steps.tsv`),
the kernel line, the boot HAL, fstab, the VNDK and the gmpf table of the G0155 v1.1.42 (`research/aidl_codes_v131.tsv`,
`GmpfHash.MIRA_RULE`: lens memory at ProjectorFocusManager 107..111, isKeystoneAdjusted, no rainbow reduction). Its own
data: the line's menu layer (`mira.menu.json`: its picture modes, colour temperature, the rows its trees lack), the blob
allow list (`image/mira_blobs_allow.txt`), each model's stock model props, wall colour and tree rows, and the model
rows of the X50 app (ModelMenu.MIRA / AURA_3, StockLight.MIRA / AURA_3).

Open, needs a unit: eye protection (feature.xml support_human_detect_source_type 0: what the stock switch watches), the
stock rows Lumen has none for (auxiliary curtain alignment, flat wall adaptation, focus at power on and after a move,
keystone after a move, silent mode, the AURA 3 four step dynamic black and Dolby soundtrack), the HDR Vivid and IMAX
Enhanced picture modes (signal modes, left out as on the X50), the f/2.0 picture modes of the AURA 3 pq database, the
remote (remote.txt names the 3838 and c081 voice remotes, the recipe ships the RS30 3840 layout).
