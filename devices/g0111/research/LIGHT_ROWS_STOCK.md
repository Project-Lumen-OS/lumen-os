# Light level, iris and dynamic black: what each X50 family stock has

Read-only research, 2026-11-14 (branch x50-iris). Owner decision 2026-11-14: offer the light level and the iris on
every model whose stock has them, the dynamic black only where that model's stock offers it. The data is
`apps/X50Projector/src/org/lumen/x50/projector/StockLight.java` (one row per GmpfModel, fail closed for any other
model), the profiles are `devices/<c>/menu.json`, X50FeaturesTest holds both equal.

Sources on xgimi-builder:
- X50 1.2.64 (G0111 and G0100, one build): `~/devices/g0111_mt9681/work/dec/release_838_newsettings` (jadx),
  vendor config `~/devices/g0111_mt9681/work/fs/mnt/vendor/xgimiconfig/<model>/`.
- TITAN 1.2.99 OTA (H0062 and H0063, one build): resources `~/scratch/iris/ns_t1299`, single classes decompiled
  with jadx 1.5.6 `--show-bad-code` in `~/scratch/iris/t1299cls/`, vendor config
  `~/devices/h0062_mt9681/official/v1299/fs/xgimiconfig/<model>/`.
- RS30 1.1.42 dump (G0155): `~/devices/g0155_mt9681/work/dec/release_838_newsettings`, SystemUI_release_848, vendor
  config `~/devices/g0155_mt9681/work/fs/xgimiconfig_b/G0155/`.

## Per model

| model | tree (res raw) | light level | iris node, map order | dynamic black | Boost read 152 | vendor iris rule |
|---|---|---|---|---|---|---|
| G0111 X50 Ultra | settings_tree_product_dianapro | yes, 1..10 | `picture_iris_ring_adjustment_diana`: 0, 20, 30, 40, 55, 70 | yes | yes (support_brightness_boost 2) | none |
| G0100 X50 Ultra Max | settings_tree_product_dianamax (byte-equal to dianapro) | yes, 1..10 | diana, same order | yes | yes (2) | none |
| G0155 RS30 Ultra Max | settings_tree_product_athena35ultramax | yes, 1..10 | `picture_iris_ring_adjustment_athena3ultramax`: 20, 30, 40, 55, 70, 0 | yes | no (support_brightness_boost 0) | only f/2.0 in picmode 5, 25, 40 |
| H0062 TITAN Noir Pro | settings_tree_product_dianahypro | yes, 1..10 | diana, same order | yes | yes (2) | none |
| H0063 TITAN Noir Max | settings_tree_product_dianahymax (byte-equal to dianahypro) | yes, 1..10 | diana, same order | yes | yes (2) | none |

Rows: every tree has `root/image/brightness_adjustment`, `root/image/picture_iris_ring_adjustment_<x>`,
`root/image/image_mode_4_state` and `root/image/image_mode_setting/image_dynamic_black_bit_enhancement`, in that order.
No code hides them: PictureFactory (iris action `picture.picture_iris_ring_adjustment`, DBLE switch case 1574764486)
and BrightFactory (`brightness.brightness.settings.brightness_adjustment`) set no visibility on these cells in the
X50 and RS30 builds. The DBLE node has the hint picture `img_dynamic_black_bit_enhancement_tips` in all three
picture.json files.

Vendor side, the same for all five: feature.xml has `support_luminance_mode 3`, `new_lumens_mode_support 1`,
`support_ambient_light_version 3`, no `support_lumens_lvl` (FeatureUtils.getSupportBrightness default "1"), feature.xml
sha256 G0111 = G0100 651cd1e9, H0062 = H0063 a49e0d52, G0155 6b6bbb44. `dlp/dynamicBlack.ini`: `type = 2`
(FPGA P47), `force_level = 1`, `use_iris = 1`, `iris_num = 2` on every model. gmpf codes: DisplayManager 6, 7, 152,
172, 174 and GmTvManager 199 SetIrisLevel, 200 GetIrisLevel are equal in `devices/g0111/research/aidl_codes.tsv`,
`devices/g0155/research/aidl_codes.tsv` and `devices/h0062/research/aidl_codes_v1299.tsv`.

Light level range: BrightnessCell `setMaxProgress(FeatureUtils.getSupportBrightnessBoost() ? 11 : 10)`, the tree page
brightness_adjustment_progress itemListMin 1, itemListMax 10. Boost read: BrightnessCell reads
`GmDisplayManager.getLensDispMode()` only behind `getSupportBrightnessBoost()` (support_brightness_boost >= 1).

Iris calls: PictureRepository.setIrisRing -> MiddleWareApi.setIrisRing -> GmDisplayManager.SetIrisLevel, read
MiddleWareApi.getIrisRingValue = GmDisplayManager.GetIrisLevel: the same in X50 1.2.64, RS30 1.1.42 and TITAN 1.2.99
(MiddleWareApi decompiled from the TITAN APK). Radios in node map order: FullPageTreeDialogKt.toBaseEntityList walks
the node map.

## Stock rules (Lumen: IrisRule, Sequences.irisRefusal)

- Light level held by the iris: `com.xgimi.mutex.module.manager.DisplayMutexManager.setDlpLumenLevel` refuses when
  GetIrisLevel reads 0 (toast `auto_iris_ring_not_support_light_adj`) or 55, 70, 50, 51
  (`current_iris_ring_not_support_light_adj`). Same text in X50 1.2.64 and RS30 1.1.42 (file md5 73032513) and TITAN
  1.2.99 (method diff empty). 50 and 51 are not in any of these maps.
- Iris refused by the picture mode: `com.xgimi.core.SettingMutexManager.mutex` on every iris node id (diana,
  athena3ultramax, athena3, plain) returns false with `office_mode_not_support_iris_adjustment` when
  PictureUtils.isOfficeImageMode (picture mode 6 or 25) and `performance_mode_not_support_iris_adjustment` when
  isPerformanceImageMode (5). Present in all three builds (the jadx `mutex` method needs `--show-bad-code`).
- Vendor pq database `pq/xgimipq.db` table settings_interceptor (initiator, interceptor, rex_initiator,
  rex_interceptor): X50 and TITAN models have `lumens_level / aperture_f_num ^([1-9]|10)$ / ^(5[5-9]|[6-9][0-9])$`
  only. The RS30 has `aperture_f_num / picmode ^(?!20$).+$ / ^(5|25|40)$` (any iris but f/2.0 refused in performance,
  office and Bright), `lumens_level / aperture_f_num ^([1-9])$ / ^(5[0-9]|[6-9][0-9])$` and `... / 0`, plus Boost rows
  (lens_mode) that do not apply without a Boost. RS30 SystemUI shows `display_aperture_blocked_by_picmode_tip` for it.
  The iris and light chains (settings_t_chain) are equal on G0111, G0100, H0062, H0063.

## Stock words for the iris (row title in Lumen)

TITAN 1.2.99 newsettings (the only build with translations) and RS30 SystemUI 848:
- ru `диафрагма` (auto_iris_ring_not_support_light_adj, office_mode_not_support_iris_adjustment, SystemUI
  display_aperture_blocked_by_picmode_tip), the row title is "IRIS": Lumen title "Диафрагма".
- de `Blende`, es `apertura`, fr `ouverture`, it `apertura`, pl `przysłona`, pt `abertura` (newsettings, SystemUI
  says diafragma / diaphragme / diaframma for es, fr, it, pt): Lumen titles Blende, Apertura, Ouverture, Apertura,
  Przysłona, Abertura (pt-rBR falls back to pt on stock: Abertura).
- ja 絞り調整, ko 조리개 조정, zh-rCN 光圈调节, zh-rTW 光圈調整: already the stock titles.
- en-rUS title "IRIS" (notes say "aperture"): kept. ar, be, kk, tr, uk: no stock strings, kept.
