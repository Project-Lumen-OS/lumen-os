# RS30 Ultra Max (G0155): stock menu facts behind devices/g0155/menu.json

Source: the stock newsettings app of v1.1.42 (`release_838_newsettings`, decompiled on xgimi-builder
`~/devices/g0155_mt9681/work/dec/`), its tree `res/raw/settings_tree_product_athena35ultramax.json` and the node tables
`res/raw/picture.json`, `res/raw/more.json`; the game menu app xhplayer (`release_838_xhplayer`) and the model's
`feature.xml`. Read only, 2026-11-12 / 2026-11-13.

## Picture modes (`picture.mode`, X50Projector `ModelMenu.RS30`)
`image_mode_4_state` lists, with the content each node is shown for (`support`):

| node | vendor id (`other`) | stock text | shown for | Lumen token |
|---|---|---|---|---|
| image_mode_ai | 10 | image_set_mode_ai_text | SDR, HDR, HDR10, IMAX, HdrVivid, Filmmaker | ai |
| image_mode_sport | 9 | image_set_mode_sport_text | same | sport |
| image_mode_imax_enhanced | 2 | image_mode_imax_enhanced_text | IMAX, HDR10 | (not offered) |
| image_mode_movice_v3 | 1 | image_set_mode_movies_text (电影, Movie) | SDR ... Filmmaker | movie |
| image_mode_hdr_vivid | 26 | image_mode_hdr_vivid_text | HdrVivid | (not offered) |
| image_mode_prof_color_accuracy | 29 | image_mode_prof_color_accuracy_text | SDR, HDR, HDR10 | color_accuracy |
| image_mode_light_athena3 | 40 | image_set_bright_mode (明亮, Bright) | SDR ... Filmmaker | bright |
| image_mode_performance | 5 | image_set_mode_performance_text | SDR ... Filmmaker | performance (behind the stock warning) |
| image_mode_picture_dolby_bright / _soft | | Dolby Vision | DOLBY | (not offered) |

One vendor id names different modes on different trees of the same app (`picture.json`: id 1 is also
image_mode_standard and image_mode_movice, id 40 also image_mode_office_athena3 and image_mode_standard_athena3), so the
X50's names of a read-back id (1 = standard there) never apply here: the token table is per model. Every picture mode
id of `picture.json` lies in 0..41 (GmDisplayManager `EN_PICTURE_MODE_*` 0..31, the athena3 nodes 36..41): the range
the colour space read (DisplayManager 103) accepts as its mode argument.

## Eye protection (`eye`)
The RS30 tree has `more_eye` (`more.json`: type 2 = a switch, action `more.more.radiation_eye_protection`, no
`tipsImg`), not the X50's `more_eye_state` page with follow / be-at-ease modes. MoreViewModel sends
`MoreRepository.setHumanDetectOnOff(isCheck)` with the same confirmation dialog as the X50. Lumen: options off / on,
no hint picture, on = DisplayManager 61 + `persist.sys.humandetect.enable` (Sequences.eyeSet), no 126 / 127.

## Light level, iris, dynamic black (owner 2026-11-14: open what stock has)
In the profile since x50-iris: light level 1..10 (no Boost, feature support_brightness_boost 0), iris
(`picture_iris_ring_adjustment_athena3ultramax`, f/2.0 .. f/7.0 then dynamic), dynamic black. Model data in
apps/X50Projector StockLight, evidence in devices/g0111/research/LIGHT_ROWS_STOCK.md.
## Light level, iris and dynamic black (`lamp`, `picture.iris`, `picture.dynamic_black`, owner 2026-11-14: open what stock has)
`brightness_adjustment_progress` (itemListMin 1, itemListMax 10): BrightnessManager.setLedLight = DisplayManager 6
setDlpLumensLevel((byte) level), read DisplayManager 7 (BrightnessManager.getLedLevel). feature.xml
`support_brightness_boost` 0, so stock never reads DisplayManager 152 here (X50: 2, Boost = 11 while 152 reads 1).
StockLight.RS30_ULTRA_MAX `boost` false, Gmpf refuses 152 on a model without the Boost. The RS232 mirror (SystemManager
230) stays unsent, as on the X50.

The iris holds the light level. Vendor default `aperture_f_num` 0 (dynamic, pq settings_default), and the vendor pq
interceptor (settings_interceptor, enforced in vendor libxgimi.so repo::disp::rule) refuses `lumens_level` 1..9 while
`aperture_f_num` is 0 or 50..99. Stock newsettings DisplayMutexManager.setDlpLumenLevel refuses any level while
GetIrisLevel reads 0 (toast auto_iris_ring_not_support_light_adj) or 55 / 70 (current_iris_ring_not_support_light_adj).
So on a unit at the factory iris the light level does not move until the iris is fixed at f/2.0, f/3.0 or f/4.0. Lumen:
the iris row (`picture_iris_ring_adjustment_athena3ultramax`, f/2.0 .. f/7.0 then dynamic, GmTvManager 199 / 200) and
IrisRule on the light slider with the stock note. The iris is refused in the office and performance picture modes
(SettingMutexManager), any iris but f/2.0 in Bright, office and performance (pq `aperture_f_num` / `picmode`
`^(5|25|40)$`, SystemUI display_aperture_blocked_by_picmode_tip). Dynamic black (`image_dynamic_black_bit_enhancement`):
DisplayManager 172 / 174 as on the X50 Ultra. Model data in apps/X50Projector StockLight, evidence in
devices/g0111/research/LIGHT_ROWS_STOCK.md.

## The RS30's own picture rows (`ModelMenu.RS30` extras, X50Projector `PictureSequences` over `PictureHal`)
| row (token) | stock node | write | read | Lumen values |
|---|---|---|---|---|
| picture.memc | image_mode_motion_compensation_v4 | GmTvManager 160 toggleFDBoost(0, 0), then 31 setMemcLevel(0, level) (XgimiService MtkVideo.setMfcLevel) | 161 getFDBoostStatus(0): on = level 4, else 32 getMemcLevel(0) | off 0, low 1, medium 2, high 3 (4, the original frame with its heavy mutex rules, is left out: the row then marks none) |
| picture.gamut | picture_gamut_athenaultra | DisplayManager 102 switchDispColorSpace(v) | 103 getDispColorSpace(source, GmTvManager 34 mode), source = the HDMI port 1..3 on screen else 0 | native 4, bt2020 1, adobe 7, p3 2, bt709 3 (map order), refused in the office mode 25 (SettingMutexManager office_mode_dont_support_color_space) |
| picture.ai_contrast | image_ai_contrast | GmTvManager 156 toggleAIContrast(0, on) | 157 getAIContrastStatus(0) == 1 | off / on |
| picture.super_res | picture_super_resolution_switch | GmTvManager 120 setAisrType(on) (PictureRepository.enableSuperResolution) | 121 getAisrType() == 1 | off / on (the game menu's sr item sends the same pair) |
| picture.anti_rbe | picture_anti_rbe | DisplayManager 205 setRainbowReductionStatus(on) | 203 getRainbowReductionStatus() == 1 | off / on |

Every write is read back with the stock getter; Gmpf refuses all twelve calls on any model whose ModelMenu does not
name the row (extraGuard), before the transact.

## Screen shift with the pan-tilt head (`zoom.move`, X50Projector `PanTiltMove`, `MoveActivity`)
Node `move_only` (projection_screen), page OnlyDisplacementView over `keystone/view/zoommove/displacement/PtzViewModel`;
feature.xml `support_zoom_and_displacement_type` 2 (supportPtzDisplace), `support_pantilt_type` 1 (pan and tilt).

- open: MotionDetectionManager 7 getAccTriggerAK(); when on, 15 temporarySwitchTrigger(2, false) while the page is open.
- DPAD DOWN / repeat (19 up = 1, 20 down = 2, 21 / 149 left = 3, 22 / 153 right = 4): DisplayManager 72
  translationKst(d, true); 0 = it can move, then translationKst(d, false) one step (auto keystone paused again);
  -1 = the edge: the head turns, once per hold: SystemManager 122 pantiltElectricMove(6 up, 7 down, 3 left, 4 right,
  0f), auto keystone resumed first (15(2, true)).
- DPAD UP: 122(8, 0f) after up / down, 122(5, 0f) after left / right, for every DPAD key as stock.
- Lumen adds: no key repeat for LensHold.WATCHDOG_MS, focus loss and closing stop the head too. close: 15(2, true)
  when it was paused. Not sent: the reads stock makes for its data reporter (DisplayManager 12, setImageShift).
- 122 is a BANNED row of banned.tsv: unbanned for X50Projector only with exactly 3..8 and the float 0
  (devices/g0111, g0100, g0155 `unbanned_x50projector.tsv`, the same rows), sent only on the RS30.

## Remote picture keys (B870C 000d:3840, `devices/g0155/image/rs30_menu_keys.py`)
Stock `Vendor_000d_Product_3840.kl` adds 172 PICTURE_MODE (Settings image/mode), 217 PRO_SETTING (image/mode_setting),
234 BRIGHTNESS (brightness_misckey) and 135 / 136 SHORTCUT_1 / 2 (user-assigned) to the X50 layout. Lumen maps the
first three to TV_ZOOM_MODE, TV_TIMER_PROGRAMMING and TV_NUMBER_ENTRY (labels of the Lineage 21 InputEventLabels.cpp,
no PhoneWindowManager handling, sent by no other Lumen layout), routed by X50PowerOverlay to X50Projector KeyReceiver:
the picture mode popup, the whole picture page above it (MenuController.showPage), the light level bar. 135 / 136
stay unmapped.

## Game menu
xhplayer Product_athena35ultramax = BaseConfigKt.getDianaConfigList (the X50 items). feature.xml
`support_vrr_only_at_top_speed` true: MutexManager refuses VRR on (and AMD FreeSync Premium) with the toast
vrr_requires_top_speed while isTopSpeedMode() (getGameModeOption() in 2 / 3, the menu's Turbo) is false. It never
switches VRR off on leaving Turbo. Lumen: profile `game_menu.vrr_top_speed`, GmModel.refuse R_VRR_SPEED, nothing else.

## Not in the profile yet (owner 2026-11-12: later)
colour temperature, local contrast, Dolby curtain pages and the remaining gmpf calls new against the X50
(`new_vs_x50.tsv`).
