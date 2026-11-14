# X50 feature table for the shared menu (quick menu D + game menu), devices/g0111/menu.json + g0100/menu.json

Facts only, each with its source. Code: branch `x50-beta` 45559d0 (newest X50Projector + X50EyeGuard; `arch`/`arch-next`
fcec6f0 + c33dd0f + 45559d0). Stock: X50 dump on xgimi-builder `~/devices/g0111_mt9681/work/` (jadx `dec/` incl. `release_838_xhplayer`, the newsettings
APK with its tree `res/k0.json` + 146 node files, `fs/mnt/vendor/xgimiconfig/G01xx/feature/feature.xml`).
Codes: AIDL gmpf V1, hash 715affd0, `devices/g0111/research/aidl_codes.tsv`. Abbreviations: DM DisplayManager, PF
ProjectorFocusManager, MD MotionDetectionManager, GF GmFactoryManager, TV GmTvManager, GA GmAudioManager, SM SystemManager.

## 0. Models and the binding rules

| | X50 Ultra | X50 Ultra Max |
|---|---|---|
| `ro.boot.xgimi.modelname` | G0111 | G0100 |
| Lumen codename / image | g0111 | g0100 (own keys, trial only) |
| stock product | dianapro | dianamax |
| firmware | same build (XR04E = XR03E) | same |
| feature.xml | identical (diffed 2026-11-11, 14238 B both) | identical |
| settings tree | `k0.json` | `rF.json`, byte-identical (gsi-research/X50_ULTRA_MAX_RU.md) |
| stock game menu list | `Product_dianapro` = `getDianaConfigListV2()` | `Product_dianamax` = the same list |
| differs | | DLP/laser current tables, DLP image 9.3.130, FPGA file, dynamicBlack.ini, PQ, WB, calibrations |

Binding rules (owner, verbatim): "Never put a Z9X image or OTA on it. Ambient light getter and laser currents for X50
only after the owner's decision." t20 decision: light level, zoom, lens shift, full stock keystone reset, eye switch and
modes from Lumen are allowed.

What that means in code today (45559d0):
- Light level DM 6/7 + boost read DM 152 and DLBE DM 172/174: ONLY G0111 (`Menu.laserRows(bootModel)`, `Gmpf.laserGuard`
  refuses before the transact on any other or unreadable model). Ultra Max: these rows and calls are absent.
- Never on any X50: DM 59 setALOnOff (BANNED), DM 60 getALOnOff, PF 50 getAmbientLight, laser current / RGB lumens
  tables (setDlpLumensComponentRGBLevel and friends), DM 151 setLensDispMode (boost setter), DM 177/178 lumens,
  DM 101 / 66 / 99 / 100 (eye), autoFocus 3 except (47) after optical zoom.
- Eye safety policy stays in X50EyeGuard (`org.lumen.x50.eyeguard`, own allow list, below). The menu only reads 62/127
  and writes 61/126 from user rows.
- Both models: every app checks the exact pair (image codename, boot model) = (g0111, G0111) or (g0100, G0100).

## 1. Quick menu D (Z9X 1.0.10 layout) mapped to the X50

Z9X layout (release-prep/z9x-1.0.10 54f45de `QuickPanelController.buildRoot`): round Eye + All settings; rows
[AK wide, Keystone, Zoom], [Picture wide, Sound, Focus], [Input wide, Bluetooth, Network], [Game wide, Projection,
Mirroring|USB]; brightness slider.

| D element | X50 has it | Lumen X50 code (45559d0) | get (code) | set (code, args) | Ultra Max | stock hint picture |
|---|---|---|---|---|---|---|
| Eye (round) | yes: Off / follow / be at ease | Menu.EYE + EYE_MODE, Sequences.eyeSet / eyeMode, confirm via Menu.eyeAfter | DM 62 getHumanDetectOnOff, DM 127 getHumanDetectMode | DM 61 setHumanDetectOnOff(0..1) then prop persist.sys.humandetect.enable; DM 126 setHumanDetectMode(0 follow, 1 full) | same | follow `more_eye_follow_v5`, be at ease `more_be_at_ease` |
| All settings (round) | yes | MenuActivity (Menu.rows(model)), section list section 3 | | | rows minus LIGHT, DYNAMIC_BLACK | |
| Auto correction (wide) | yes | Sequences.autoKeystone, value text Menu.akResultKey (1 = running) | | PF 27 autoKst() | same | `tips_one_key_correction_diana` (node auto_keystone_ak3_diana) |
| Keystone popup: manual corners | yes | CornerActivity, Keystone.move | DM 16 getCorrectKeystoneFullPiont(0), DM 64 checkTrapezoidCoordinate | DM 15 setCorrectKeystoneFullPiont(0, KstPoint) | same | |
| Keystone popup: reset | yes (full stock reset) | Sequences.keystoneReset | DM 84 getCurrentKeystoneMode | DM 38(0), DM 85(0,0) only if 84 = 6, DM 11 correctKeystone(50), DM 63 zoomControl(5,10), PF 88 setTiltShiftMove(13, 0f) | same | |
| Keystone popup: power-on AK | yes | Row POWER_ON_AK | GF 46 | GF 45 savePowerOnAKFlag(bool) | same | |
| Keystone popup: AK when moved | yes | Row MOVE_AK | MD 7 | MD 6 setAccTriggerAK(bool) | same | `tips_real_time_correction` (keystone_non_inductive_real_time) |
| Keystone popup: obstacle | yes | Row OBSTACLE | PF 34 | PF 33 | same | |
| Keystone popup: screen align | yes | Row SCREEN_ALIGN | PF 36 | PF 35 | same | `curtain_projection_self_adapt_tips` (curtain_projection_self_adapt_diana) |
| Keystone popup: level angles | yes (info) | Sequences.AngleWatch, 500 ms | MD 25 getMotionAttitude, MD 7 | MD 24(true every 5 s / false), MD 15(2,false/true) only if MD 7 on | same | |
| Zoom (small) | yes: optical + digital zoom AND lens shift | LensActivity, Zoom, LensHold | DM 97, 98, 190, 63(11/12/13,10), 63(18,1), PF 94, PF 112(0..1) | DM 93(0), 94(0), 95(0..64), 96(-200..200), 189(-16..0), 185(0..1,1..2), PF 88(1..5,0f), PF 104, PF 105, SM 147(0..1), PF 3(47) after an optical change | same calls (vendor zoom math differs: libxgimi zoomTof list has no G0100) | none |
| Picture (wide) popup: mode radios | yes, X50 list AI 10, sport 9, soft 24, Filmmaker 27, ISF day 38, ISF night 39, colour accuracy 29, office 25 | Row PICTURE_MODE (LIST), Sequences.PICTURE_MODES | TV 34 getPictureMode(0) | TV 33 setPictureMode(0, id) | same | `picture_mode_tips_img` (image_mode_4_state) |
| Picture "more": brightness / contrast / saturation / sharpness | yes | Rows BRIGHTNESS..SHARPNESS | TV 8 / 6 / 10 / 12 (0) | TV 7 / 5 / 9 / 11 (0, 0..100) | same | |
| Picture "more": dynamic contrast | yes | Row DYNAMIC_CONTRAST | TV 114(0) | TV 113(0, bool) | same | |
| Picture "more": dynamic black (DLBE) | yes | Row DYNAMIC_BLACK; not in PC 6 / office 25 / HDR Vivid 26 | DM 174 | DM 172(1/0) | ABSENT (laserRows) | `img_dynamic_black_bit_enhancement_tips` |
| Sound (small) popup: mode radios | yes: AI 3, music 2, movie 1, karaoke 4, sports 12, standard 20 (tree order) | Row SOUND_MODE, Sequences.SOUND_MODES | GA 7 getSoundeffect | GA 6 setSoundeffect(byte); never 5/21/22 | same | |
| Sound "more": output / format / eARC | yes | SOUND_OUTPUT, AUDIO_FORMAT, EARC (AudioPath) | GA 3, TV 66, TV 79 | GA 2 (0,2,3,4,6,100; never 1), TV 65 | same | |
| Focus (small) popup | yes: auto focus, manual focus, smart AF (only these three in the X50 tree) | AUTO_FOCUS, MANUAL_FOCUS (FocusActivity), SMART_AF | PF 86 | PF 8 sendFocusStatus(44); PF 2 manualFocus(17 open, 1, 0, 2 stop); PF 85 + MD 4 together | same | none |
| Input (wide) | yes (HDMI 1..3) | X50TvInput source picker + HdmiSource | TV 4 getCurrentInputSource | TV 3 switchInputSource(0..3) | same | |
| Bluetooth / Network | yes | Android pages, no HAL | | | same | |
| Game (wide) popup | yes, HDMI only | Rows GAME_MODE, ALLM (only in Auto), GAME_SPEED | DM 148 getGameModeProp, DM 77, TV 130 | DM 146(0 on / 1 off), DM 147(0 manual / 1 auto), DM 76(0..3) (+ DM 38(0) after top speed), TV 129(bool) | same | none on X50 (Z9X/HB `picture_game_tips_img` node game_mode_state is not in the X50 tree) |
| Projection (small) popup | yes: Auto / desk / ceiling + rear | PLACEMENT (LIST), REAR, Sequences.autoTurn | DM 3, MD 9 | DM 2(0..3), MD 8(false; true only from Auto), SM 140(false) on the ceiling confirm | same | `rear_project_tips_img` (projection_settings.rearProject) |
| Mirroring / USB (small) | no HAL; tile only if the receiver app / Home files exist in the X50 image | | | | same | |
| Brightness slider | light source level 1..10 | Row LIGHT, Sequences.lightRead/lightNext/lightSend (send 200 ms after the last key) | DM 7 getDlpLumensLevel, DM 152 getLensDispMode (1 = boost on: shows Boost, untouched) | DM 6 setDlpLumensLevel(1..10), never while 152 reads boost; NO Boost step (151 not allowed) | ABSENT: no slider row on G0100 | `brightness_enhanced_tips_img` belongs to env_adapt_settings_kirin = ambient light: OUT |

Extra X50 rows with no D tile (go to All settings): HDMI_VERSION (TV 39/40: 1|2), HDMI_RANGE (TV 63/64: 0|1|2),
HDMI_INPUTS, SLEEP_TIMER (no HAL, AlarmManager + power key), REMOTE (pairing, no HAL), PERMISSIONS (info),
ANDROID_SETTINGS.

## 2. Game menu (Z9X design A dock) mapped to the X50

X50 stock game menu (xhplayer `gamemenu/config/BaseConfigKt.getDianaConfigListV2`, both models, filtered at run time
only for GAME_SOUND_EFFECT by `support_gaming_audio_effect` and AMD by `support_amd_free_sync_premium`, both `true`):
GAME_MODE, VRR, HIGH_FRAME_RATE, PICTURE_MODE, COLOR_SPACE_V2, ENHANCE_DARK, GAME_SOUND_EFFECT, VIRTUAL_CROSS_HAIR,
GAME_SUPER_RESOLUTION, PICTURE_ASPECT_RATIO, SCREEN_OFFSET, WINDOW_FRAME_RATE, AMD_FREE_SYNC_PREMIUM, ZOOM_MAP,
HDR_SETTINGS, MENU_THEME. Calls traced through xhplayer `OtherViewModel` / `api/GameModeApi` -> `com.xgimi.device.
GmDisplayManager` (gmuiapi copy in xhplayer) -> gmpf method name -> X50 code. "src" = TV.inputToInt(inputId), HDMI1..3 = 1..3.

| Z9X item | X50 stock item | X50 get | X50 set (stock values) | in X50 allow list today | note |
|---|---|---|---|---|---|
| lat (response speed) | GAME_MODE basic / fast | DM 77 getGameModeOption | DM 76 setGameModeOption: top speed 2 (HFR on) / 3; standard 1 (HFR on) / 0 | YES (76 args 0..3, 77) | t19 GAME_SPEED row already does 2->1, 3->0, 1->2, 0->3 + DM 38(0) after top speed; Z9X 647/648 semantics equal |
| hfr | HIGH_FRAME_RATE off / auto | DM 77 (option 1 or 2 = on) | DM 76: 0<->1, 3<->2 (GameModeApi.setHighFrameRate) | YES | feature support_hfr=true |
| vrr | VRR off / on | TV 146 getVRREnableState(src) | TV 145 setVRRModeEnable(0/1, src) | NO, new | stock mutex: VRR not with 21:9 / 32:9; feature support_vrr_only_at_top_speed absent = false |
| pic (game picture) | PICTURE_MODE standard 0, FPS 1, RPG 2, RTS 3, sports 4 | TV 148 getGamePictureMode(src) | TV 147 setGamePictureMode(0..4, src) | NO, new | |
| dark (dark scenes) | ENHANCE_DARK off / on | TV 150 getDarkBoostState(gamePic, src) | TV 149 setDarkBoostOnoff(bool, gamePic, src) | NO, new | gamePic = the TV 148 value |
| sr (super resolution) | GAME_SUPER_RESOLUTION off / on | TV 121 getAisrType (1 = on) | TV 120 setAisrType(1/0) | NO, new | Z9X used IGmpf2 321/322; X50 path is AISR |
| ar (aspect) | PICTURE_ASPECT_RATIO auto 3, 16:9 1, 21:9 6, 32:9 7 | GmDisplayManager.getVideoRatioCommon -> MstPictureManager.getVideoArcType (XgimiService) | setVideoRatioCommon -> MstPictureManager.setZoomMode (XgimiService) | NO | gmpf code NOT traced (goes through XgimiService, not a direct gmpf call): keep hidden until traced; TV 35/36 setAspectRatio/getAspectRatio are candidates, unproven |
| snd (game sound) | GAME_SOUND_EFFECT surround 191, story 192, clarity 193, balance 194, off 190 | GA 76 getGamingSoundEffectMode | GA 75 setGamingSoundEffectMode(190..194) | NO, new | X50 has 5 values (Z9X 3). Z9X 0-as-off mapping applies |
| aim (crosshair) | VIRTUAL_CROSS_HAIR | none | none (own window) | n/a | |
| fps (frame counter) | WINDOW_FRAME_RATE off / left / centre / right | VRR on: DM 179 getDispOutputFps; else TV 122 getVideoInfo frameRate/100 | none | NO (read only, new) | stock polls every 2 s |
| (none on Z9X) | COLOR_SPACE_V2 native 4, BT.2020 1, DCI-P3 2, Rec.709 3 | DM 103 getDispColorSpace(src, pictureMode) | DM 102 switchDispColorSpace(v) | NO, new | X50-only item |
| (none) | AMD_FREE_SYNC_PREMIUM | TV 173 | TV 172(bool) | NO, new | X50-only item |
| (none) | HDR_SETTINGS brightness mapping 1 / HGIG 2 / off 0 | TV 192 getHGiGMode, TV 175 GetHDRDyToneMappingStatus(0) | TV 174 ToggleHDRDyToneMapping(0, 0/1) + TV 191 setHGiGMode(bool) | NO, new | X50-only item |
| (none) | SCREEN_OFFSET (only with 21:9 / 32:9) | TV 151 getDisplayArea, TV 36 | TV 41 setDisplayArea, TV 171 restoreWinByAspRatio | NO | depends on ar; leave out with ar |
| (none) | ZOOM_MAP (mini map enlarge), MENU_THEME | | | | MapActivity overlay / UI only; not traced |

Game state on the X50: running = DM 148 getGameModeProp state == 0 (stock GameModeApi.isGameModeRunning). Stock opens the
game menu with the held gear = KEYCODE_MENU in xhplayer InputActivity. On Lumen X50 the held gear arrives as 3840
scancode 127 (IR / keypad 139), TV_MEDIA_CONTEXT_MENU after the X50 key edit (x50_power_keys.py GEAR_HOLD: ALL_APPS in the 1.0.9 base) -> KeyReceiver.gear(long): the game menu
while game mode runs on HDMI, else the corner reset on the corner screen, else Android settings (X50_GAME_CALLS.md 7).

None of the new game calls is BANNED in `devices/goat_elegant/research/banned.tsv` (checked: TV 120/121, 122, 145-150,
172-175, 191/192, DM 102/103, 179, GA 75/76). Every new one is a vendor call Lumen never sent on the X50: follow the
Z9X GATED pattern (shown only after its getter answers in range on this unit, every write read back, hide on mismatch)
and add each row to `allowed_x50projector.tsv` with its stock caller and an args range.

## 3. All settings (the whole X50 menu, MenuRow order, 45559d0)

FOCUS: AUTO_FOCUS (PF 8(44)), MANUAL_FOCUS (PF 2), SMART_AF (PF 85/86 + MD 4).
KEYSTONE: AUTO_KEYSTONE (PF 27), MANUAL_KEYSTONE (DM 15/16/64), ZOOM_SHIFT (zoom/shift calls above), KEYSTONE_RESET,
HORIZONTAL_ANGLE / VERTICAL_ANGLE (MD 25, info), POWER_ON_AK (GF 45/46), MOVE_AK (MD 6/7), OBSTACLE (PF 33/34),
SCREEN_ALIGN (PF 35/36).
PROJECTION: PLACEMENT (DM 2/3, MD 8/9, SM 140), REAR.
PICTURE: LIGHT (G0111 only), PICTURE_MODE, BRIGHTNESS, CONTRAST, SATURATION, SHARPNESS, DYNAMIC_CONTRAST, DYNAMIC_BLACK
(G0111 only).
SOUND: SOUND_MODE, SOUND_OUTPUT, AUDIO_FORMAT, EARC.
HDMI: HDMI_VERSION, HDMI_RANGE, GAME_MODE, ALLM, GAME_SPEED, HDMI_INPUTS.
EYE: EYE, EYE_MODE. SYSTEM: SLEEP_TIMER, REMOTE, PERMISSIONS (info), ANDROID_SETTINGS.

Allow list `devices/g0111/research/allowed_x50projector.tsv`: 89 rows (2 meta + 87 calls: DM 33, TV 23, PF 15, MD 8,
GA 4, GF 2, SM 2), unban file `unbanned_x50projector.tsv` (DM 11 args 50, PF 88 args 1..5,f:0;13,f:0, PF 3 args 47),
mirrored in `verify_tree_x50.py` MENU_APP (4-field rows) and checked on the dex by `x50_menu_apk_check_test.sh`.
g0100 reuses the g0111 hal rules (`devices/g0100/research/unbanned_x50projector.tsv` must stay row-equal).
"43 calls" was the t12 count; t20 is 87.

## 4. X50EyeGuard (safety, stays outside the menu module)

Allow list `devices/g0111/research/allowed_x50eyeguard.tsv`: meta 2 + DM 127 getHumanDetectMode, DM 62
getHumanDetectOnOff, SM 32 setBootAnimState(1|2), SM 33 getBootAnimState, SM 36 setSystemUIState(1), PowerManager 2
goToBootCompleted(0|1). NEVER_SEND (EyeGuardTest): 61, 66, 101, 126. Policy: covers the picture with its own window
whenever detection is not armed (switch off by 62, hd_server2 not running, 33 != 2, unknown); mode 1 needs nothing;
the menu opens over the cover on the EYE row (Menu.rowOver), long gear over the cover never opens Android settings.
The menu's eye rows only call DM 61/126 and read 62/127; the cover follows from X50EyeGuard's own reads.

## 5. Stock hint pictures that exist on the X50 (tree k0.json nodes with tipsImg, drawable present in the APK)

| node | drawable | used by |
|---|---|---|
| auto_keystone_ak3_diana | tips_one_key_correction_diana (KW.webp) | AK tile |
| keystone_non_inductive_real_time | tips_real_time_correction (zX.webp) | move AK row |
| curtain_projection_self_adapt_diana | curtain_projection_self_adapt_tips (Qt.webp) | screen align row |
| image_mode_4_state | picture_mode_tips_img (5l.webp) | picture mode |
| image_dynamic_black_bit_enhancement | img_dynamic_black_bit_enhancement_tips (7P.png) | DLBE (G0111 only) |
| projection_settings.rearProject | rear_project_tips_img (i9.webp) | rear projection |
| more_eye_project_follow_img | more_eye_follow_v5 (v5.webp) | eye follow |
| more_eye_project_be_at_ease_img | more_be_at_ease (7k.webp) | eye be at ease |
| env_adapt_settings_kirin | brightness_enhanced_tips_img | ambient light: OUT |
| image_mode_imax_enhanced, picture_enhanced_hdr_mapping, wall_color_calibration | icon_imax_enhanced, img_enhanced_hdr_mapping_tips, wall_color_calibration_tips_img | not ported |

No game, focus, zoom or sound hint picture in the X50 tree. The shipped image draws Lumen's own pictures (QpdTip) for
these nodes only; no XGIMI artwork in the image.

## 6. Gating mutants the port must kill

- LIGHT / DYNAMIC_BLACK row or slider built on G0100, unknown or empty model; laserGuard removed or inverted.
- Any Call for DM 59, DM 60, PF 50, DM 151, DM 177/178 or a laser-current method; any Call/row in the menu module for
  DM 101 / 66 / 99 / 100.
- Eye policy moved into the menu (menu sends 61 outside the user row, or covers / uncovers by itself).
- X50 profile accepted on a non-X50 pair (g0111+G0100, g0100+G0111, Z9X device), Ultra Max getting the Ultra light rows.
- New game calls sent before their getter answered in range; write not read back.

## 7. Not traced / open
- Aspect ratio gmpf path (XgimiService MstPictureManager.setZoomMode / getVideoArcType); screen offset depends on it.
- ZOOM_MAP (mini map) and MENU_THEME in the stock game menu.
- Stock X50 SystemUI quick-menu tile list (only the newsettings tree and xhplayer were walked here).
- Everything in sections 1-2 is host knowledge; nothing of the game items is live-proven on an X50.
