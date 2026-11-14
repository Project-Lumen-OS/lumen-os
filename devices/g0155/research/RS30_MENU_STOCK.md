# RS30 Ultra Max (G0155): stock menu facts behind devices/g0155/menu.json

Source: the stock newsettings app of v1.1.42 (`release_838_newsettings`, decompiled on xgimi-builder
`~/devices/g0155_mt9681/work/dec/`), its tree `res/raw/settings_tree_product_athena35ultramax.json` and the node tables
`res/raw/picture.json`, `res/raw/more.json`. Read only, 2026-11-12.

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
| image_mode_performance | 5 | image_set_mode_performance_text | SDR ... Filmmaker | (not offered, as on the X50) |
| image_mode_picture_dolby_bright / _soft | | Dolby Vision | DOLBY | (not offered) |

One vendor id names different modes on different trees of the same app (`picture.json`: id 1 is also
image_mode_standard and image_mode_movice, id 40 also image_mode_office_athena3 and image_mode_standard_athena3), so the
X50's names of a read-back id (1 = standard there) never apply here: the token table is per model.

## Eye protection (`eye`)
The RS30 tree has `more_eye` (`more.json`: type 2 = a switch, action `more.more.radiation_eye_protection`, no
`tipsImg`), not the X50's `more_eye_state` page with follow / be-at-ease modes. MoreViewModel sends
`MoreRepository.setHumanDetectOnOff(isCheck)` with the same confirmation dialog as the X50. Lumen: options off / on,
no hint picture, on = DisplayManager 61 + `persist.sys.humandetect.enable` (Sequences.eyeSet), no 126 / 127.

## Not in the profile yet (owner 2026-11-12: later)
Pan-tilt head aiming, iris ring (`picture_iris_ring_adjustment_athena3ultramax`), 3D, colour temperature, Dolby
curtain pages and the 29 gmpf calls new against the X50 (`new_vs_x50.tsv`).
