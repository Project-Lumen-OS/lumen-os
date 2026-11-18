# Z9X (G0082): the stock model build.prop and what Lumen sets of it

The stock XGIMI init loads `/vendor/etc/xgimimodelname/G0082/build.prop` at every boot, LineageOS init does not. Lumen
sets the lines a Lumen Z9X image has a reader for, through the shared loader `tools/devices/model_props.py` (the same
one the HB uses; the X50, RS30 and Titan images append the same rule's lines to build.prop): data
`devices/horse_popular/image/z9x_model_props.tsv` and `z9x_model_prop_dapeng.txt`, generated
`z9x_model_props.rc` (early-init, keyed on `ro.boot.xgimi.modelname` and `ro.vendor.build.version.incremental`),
proven in the recipe against each pinned stock file (Mac dumps, vars Z19 / Z58 / Z62 of build.conf).

Stock files (dumps on xgimi-builder `~/devices/g0082_mt9952/{official/v61519/fs/vendor, official/v61558/fs/vendor,
v61562/fs/vendor_a}`, Mac copies under `z9x-firmware-dump/{v61519/files19, analysis, v61562}`):

| firmware | sha256 | note |
|---|---|---|
| v6.15.19 | 3ac178ea87d0d9e6167c5a148f090f1a3e2002a1fb7489e9185e888b959cf45c | `persist.vendor.xgimi.animation.style` commented out |
| v6.15.58 | d858043dbfad702c834c04f58902a45972c4d808b76224ad45c00383393c7ab1 | |
| v6.15.62 | d858043dbfad702c834c04f58902a45972c4d808b76224ad45c00383393c7ab1 | same file as v6.15.58 |

How the readers were found (v6.15.62 dump, 2026-11-14): `grep -a` of every vendor file, the dex of every vendor and
stock system APK and jar, and every non-APK file of the stock system. On Lumen, `/vendor/app` and `/vendor/priv-app`
are hidden at post-fs-data (`xgimi_compat.rc`, the XGIMI apps share android.uid.system with another key) and the stock
system is replaced, so a reader there does nothing on Lumen.

| line | reader on stock | on Lumen Z9X |
|---|---|---|
| ro.product.device / model / name, ro.build.product, ro.product.brand / manufacturer / board | identity | not set (model rule: Lumen keeps its own identity) |
| ro.xgimi.product.name=Product_dapeng | XGIMI vendor apps (hidden), vendor hilinknew (never starts: `vendor.xgimi.hilink.productid` is commented out), stock SystemUI, newsettings, XRMService | **set**: lumen-control in Z9xProjector answers it as productName to the XGIMI phone remote (Replies, AndroidFacts), stock answers Product_dapeng, Lumen answered nothing |
| persist.sys.theme.mode=false | | not set (model rule: persist.sys.*) |
| persist.vendor.xgimi.hd_server=2 | none found (the vendor starts hd_server1..3 by service name, libxgimi.so) | not set |
| ro.xgimi.fun.hidesystemapp | none found | not set |
| persist.xgimi.wifidongle.check | none found | not set |
| vendor.xgimi.softap.sta.coexist, ro.xgimi.line.cast | vendor priv-app wirelessscreen (hidden) | not set |
| vendor.xgimi.disable.subwoofer | none found | not set |
| xgimi.image4k.support | vendor gimiplayer, mediaPlayer, minitvfactory (hidden) | not set |
| tv.series.model | vendor IqiyiSdk (hidden) | not set |
| persist.xgimi.gmui.font.bold | XGIMI apps (hidden or replaced) | not set |
| vendor.mstar.hwc.framebuffer.format=1 | vendor mtkhwcomposer, audio.primary.mt5877 (VendorProperties mstar_hwc_framebuffer_format) | **set**: `/vendor/build.prop` already sets the same 1 before init, so no change in effect |
| persist.xgimi.external.usb_bus, ro.xgimi.internal.usb_device | stock services.jar (replaced) | not set |
| persist.vendor.xgimi.animation.style (v6.15.58 / v6.15.62) | vendor priv-app update (hidden) | not set |
| persist.xgimi.bootanimation.style, persist.vendor.xgimi.enforce.volume | stock libbootanimation.so (replaced) | not set |
| ro.xgimi.default.aipq=film | stock XRMService (replaced) | not set: the Z9X app writes the film scene itself after AI |
| log.tag.ANNPreparedModelBase, log.tag.ANNExecutor | no binary of the vendor or the stock system logs under these tags | not set |
| ro.product.unique | none found (the iQIYI codec id) | not set |
| the seven `##trapezoid` lines (vendor.xgimi.hwc.aa, vendor.mstar.trapezoid.secure.enable / 4K.enable / dual.gop.mode / video.gop / resolution.switch, vendor.mstar.hwc.tcvideo.format) | vendor mtkhwcomposer | set since 1.0.8 by `overlay/v1/z9x_trapezoid.rc` (model only, any firmware), kept there and not repeated; `tools/devices/test/model_props_test.py` proves them equal to the block of every pinned stock file |
| persist.sys.autotheme.*, persist.sys.xgimi.theme.url | | not set (model rule: persist.sys.*) |
| ro.xgimi.mem.policy | not in the G0082 file (the HB vendor init.fusion.rc reads it), nothing in the Z9X vendor | not set |

Not verified on a unit: `getprop ro.xgimi.product.name` after boot and the phone remote's productName.
