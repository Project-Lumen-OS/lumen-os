# X50 game menu: the stock vendor calls behind each item (branch core-menu-x50game)

Scope: the shared game menu (apps/core/lumen-menu GameMenu / GmModel, design A dock) on the X50 Ultra (G0111) and the
X50 Ultra Max (G0100). Every vendor call the X50 adapter (`apps/X50Projector/src/org/lumen/projector/panel/X50GameIo.java`
over `org.lumen.x50.projector.GameSequences` / `GameHal`) may send is proven here from the STOCK X50 code, with file and
line. Nothing here is live-tested on an X50.

Sources (decompiled on xgimi-builder, not copied into git):
- XP = `~/devices/g0111_mt9681/work/dec/release_838_xhplayer/sources/` (stock game menu app com.xgimi.xhplayer of the
  X50 v1.2.64 dump; jadx).
- MJ = `~/devices/g0111_mt9681/work/dec/com.xgimi.middle/sources/` (system_ext/framework/com.xgimi.middle.jar: the AIDL
  proxies `xgimi.hardware.gmpf.I*` with their `TRANSACTION_*` constants and parcel layouts).
- Codes by method name: `devices/g0111/research/aidl_codes.tsv` (X50 hash 715affd0..., the same table the allow list
  is checked against). The HB table `devices/goat_elegant/research/aidl_codes.tsv` has the same code and signature for
  each, so `devices/goat_elegant/research/banned.tsv` applies: none of the calls below is BANNED or REVIEW there
  (banned.tsv rows 120 / 122 are DisplayManager, not GmTvManager).

## 1. Which list the X50 shows

- XP `com/xgimi/xhplayer/gamemenu/config/Product_dianapro.java:11` and `Product_dianamax.java:11`:
  `list = BaseConfigKt.getDianaConfigListV2()` (G0111 = dianapro, G0100 = dianamax: the same list).
- XP `gamemenu/config/BaseConfigKt.java:145-146` getDianaConfigListV2 = GAME_MODE, VRR, HIGH_FRAME_RATE, PICTURE_MODE,
  COLOR_SPACE_V2, ENHANCE_DARK, GAME_SOUND_EFFECT, VIRTUAL_CROSS_HAIR, GAME_SUPER_RESOLUTION, PICTURE_ASPECT_RATIO,
  SCREEN_OFFSET, WINDOW_FRAME_RATE, AMD_FREE_SYNC_PREMIUM, ZOOM_MAP, HDR_SETTINGS, MENU_THEME.
- Option values (ValueBean value strings) in BaseConfigKt: VRR() :31-38 "0" off / "1" on; PICTURE_MODE() :55 balance
  "0", FPS "1", RPG "2", RTS "3", sport "4"; ENHANCE_DARK() :59 "false" / "true"; GAME_SOUND_EFFECT() :70 surround
  "191", story "192", clarity "193", balance "194", off "190"; GAME_SUPER_RESOLUTION() :78 "false" / "true";
  HIGH_FRAME_RATE() :40 "false" / "true"; GAME_MODE() :20 standard / fast; WINDOW_FRAME_RATE() :100 off / left /
  centre / right (no vendor call).

## 2. Item -> stock call chain -> X50 code

"src" = XP `utils/TV.java:341-362` inputToInt(inputId): HDMI1..4TVINPUTSERVICE -> 1..4; the X50 has three HDMI inputs
(`X50_HDMI_AUDIO.md`: tvinput HW1..HW3), so 1..3.

| menu item | stock click -> setter (XP) | stock getter (XP) | gmuiapi wrapper (XP `com/xgimi/device/`) | AIDL (MJ `xgimi/hardware/gmpf/`) | X50 code, wire |
|---|---|---|---|---|---|
| vrr | OtherFactory.postClick case "0"/"1" :1352-1377 -> C23712 :1659 `OtherViewModel.setVrr(parseInt(value))` -> OtherViewModel.java:182-190 -> GameModeApi.java:764-766 `GmDisplayManager.setVRRModeEnable(state, src)` | GameModeApi.java:768-774 getVrrState: `getVRREnableState(src)`, -1 read as 0 | GmDisplayManager.java:6535-6545 / 6555-6565 -> gmuiapi GmTvManager | IGmTvManager.java:1668 `TRANSACTION_setVRRModeEnable = 145`, proxy :6791 (int, int) -> int; :1582 `getVRREnableState = 146`, proxy :6814 (int) -> int | TV 145 (0..1, 1..3); TV 146 (1..3) |
| pic | OtherFactory :1314-1316 (sport), :1388-1400 (FPS, RPG, RTS), :1533-1535 (balance) `OtherViewModel.setPictureMode(parseInt(value))` -> OtherViewModel.java:196-200 -> GameModeApi.java:776-778 `GmDisplayManager.setGamePictureMode(mode, src)` | GameModeApi.java:780-786 getPictureMode: `getGamePictureMode(src)`, -1 read as 0 | GmDisplayManager.java:6575-6585 / 6595-6605 | IGmTvManager.java:1644 `setGamePictureMode = 147`, proxy :6836 (int, int) -> int; :1552 `getGamePictureMode = 148`, proxy :6859 (int) -> int | TV 147 (0..4, 1..3); TV 148 (1..3) |
| dark | OtherFactory :1408-1410 (on), :1559-1561 (off) `OtherViewModel.setDarkEnhance(parseBoolean(value))` -> OtherViewModel.java:225-228 `GmDisplayManager.setDarkBoostOnoff(isOpen, getPictureMode(), src)` | OtherViewModel.java:230-232 `getDarkBoostState(getPictureMode(), src)` | GmDisplayManager.java:6615-6625 / 6635-6645 | IGmTvManager.java:1632 `setDarkBoostOnoff = 149`, proxy :6881 (boolean, int, int) -> int; :1537 `getDarkBoostState = 150`, proxy :6905 (int, int) -> boolean | TV 149 (0..1, 0..4, 1..3); TV 150 (0..4, 1..3); the middle argument = the TV 148 game picture |
| sr | OtherFactory :1277-1279 (off), :1309-1311 (on) `OtherViewModel.setGameSuperResolution(parseBoolean(value))` -> OtherViewModel.java:239-242 `GmDisplayManager.setAisrType(isOpen ? 1 : 0)` | OtherViewModel.java:249-251 `getAisrType() == 1` | GmDisplayManager.java:5136-5146 / 5156-5161 -> gmuiapi GmTvManager.java:571-581 / 591-596 | IGmTvManager.java:1602 `setAisrType = 120`, proxy :6239 (int) -> boolean; :1507 `getAisrType = 121`, proxy :6261 () -> int | TV 120 (0..1); TV 121 () |
| snd | OtherFactory :1088-1093 (off), :1267-1269 (clarity), :1413-1415 (surround), :1521-1523 (story), :1538-1540 (balance) `OtherViewModel.setGameSoundEffect(parseInt(value))` -> OtherViewModel.java:206-209 -> GameModeApi.java:798-800 `GmAudioManager.setGamingSoundEffectMode(mode)` | GameModeApi.java:802-808 getGameSoundEffect: 0 read as 190 | GmAudioManager.java:1289-1299 / 1309-1314 -> gmuiapi GmAudioManager | IGmAudioManager.java:624 `setGamingSoundEffectMode = 75`, proxy :2962 (int) -> boolean; :588 `getGamingSoundEffectMode = 76`, proxy :2984 () -> int | GA 75 (Lumen sends 190..194, see 3); GA 76 () |
| fps | XP `gamemenu/widget/FpsCell.java:140-190` runFps every 2000 ms: `GameModeApi.getVrrState() == 1` ? `GmDisplayManager.getDispOutputFps()` : `GmTvManager.getInstance().getVideoInfo(info)` frameRate / 100 rounded (same in HdmiInfoCell.java:169) | (read only) | GmDisplayManager.java:7340-7345; gmpf api GmTvManager -> MJ `com/xgimi/middle/GmTvManagerWrapper.java:581-593` getVideoInfo(stVideoInfo) | IDisplayManager.java:1670 `getDispOutputFps = 179`, proxy :7806 () -> int; IGmTvManager.java:1583 `getVideoInfo = 122`, proxy :6282 () -> boolean + out stVideoInfo (MJ `IGmTvType/stVideoInfo.java`: int size, width, height, frameRate, bHdmiOrDvi, videoType) | DM 179 (); TV 122 () |
| lat / hfr | GameModeViewModel.setGameMode -> GameModeApi.seTopSpeedGameMode :207-266 (AK.unAKMode = setIChipReset, then 76(2 if 77 reads HFR else 3), then afterGameModeApi), setStandardMode :334-358 (76(1 or 0), afterGameModeApi); HIGH_FRAME_RATE -> GameModeApi.setHighFrameRate :289-307 (77 read, 0->1, 3->2 on; 1->0, 2->3 off) | GameModeApi.java:104-108 / 279-287 (77) | (already allowed) | DM 76 / 77 (rows since x50-hdmi / t19) | unchanged rows |
| running | GameModeApi.java:110 isGameModeRunning (148 state 0) | | | DM 148 (row since x50-hdmi) | unchanged |
| aim | VIRTUAL_CROSS_HAIR: OtherViewModel.java:234-237, no vendor call | | | | none |

GameModeApi.afterGameModeApi :425-439: 200 ms after a speed change (top speed and standard, not the HFR switch) the
stock sends `setVrr(getVrrState())` (TV 146 then 145 with the value read) and `setPictureMode(getPictureMode())`
(TV 148 then 147). The X50 adapter does the same, only for an item whose getter answered in range this boot.

Stock refusals the module already has (GmModel.refuse): HFR off while VRR is on (OtherFactory :1196-1206, string
close_high_frame_rate_under_vrr). AMD FreeSync (TV 172 / 173) is not ported, so its refusal never applies.
Leaving top speed with VRR on: the X50 stock sends nothing extra (GameModeFactory.postClick: only refreshVrrState 3 s
later); the HB-only "145(0) before 76" is not done on the X50.

## 3. Arguments Lumen may send (allow list args column = org.lumen.hal.ArgSpec, enforced before the transact)

| call | args | why exactly this |
|---|---|---|
| GmTvManager 145 setVRRModeEnable | `0..1,1..3` | VRR() values "0"/"1"; src HDMI 1..3 |
| GmTvManager 146 getVRREnableState | `1..3` | src |
| GmTvManager 147 setGamePictureMode | `0..4,1..3` | PICTURE_MODE() values 0..4; src |
| GmTvManager 148 getGamePictureMode | `1..3` | src |
| GmTvManager 149 setDarkBoostOnoff | `0..1,0..4,1..3` | boolean (one int32 word), game picture 0..4, src |
| GmTvManager 150 getDarkBoostState | `0..4,1..3` | game picture, src |
| GmTvManager 120 setAisrType | `0..1` | `isOpen ? 1 : 0` |
| GmTvManager 121 getAisrType | `-` | no argument |
| GmTvManager 122 getVideoInfo | `-` | out parcelable only |
| GmAudioManager 75 setGamingSoundEffectMode | `190..194` | the module offers the five stock values (profile game_menu.sound: off, surround, story, clarity 193 "Clarity" = stock game_sound_effects_clear, balanced 194 "Balanced" = stock balanced_mode; GmModel.sndWire); a unit left on 193 / 194 by stock keeps the tile |
| GmAudioManager 76 getGamingSoundEffectMode | `-` | no argument |
| DisplayManager 179 getDispOutputFps | `-` | no argument |

## 4. Gating (none of these calls was ever sent by Lumen on an X50)

- Read first: an item's setter is sent only after its getter answered a value inside the stock range on this unit
  this boot (GameSequences gate; the module's GmModel.Caps hides vrr / pic / dark / snd until then). `sr` is not a
  gated item in the module (Z9X proven), so the X50 adapter gates it itself: no TV 120 before TV 121 read 0 or 1.
- Read back: every write is polled with its getter (1.5 s, 150 ms steps, as HbGameIo); a read-back that disagrees
  closes that item for the rest of the boot (no further write; the module hides the gated ones).
- Refusals as HbGameIo: no HDMI port, TV 4 not reading that port, an item off the X50 list, a value outside its range,
  a closed gate: nothing is sent, the module gets no read-back and shows its "could not" note.
- Never in this path (owner rule: no ambient light getter, no laser currents): the ambient light switch / getters
  (DisplayManager 59 setALOnOff / 60 getALOnOff, ProjectorFocusManager 50 getAmbientLight), laser currents and lumens
  (GmFactoryManager 128 getCurHwCurrent, DisplayManager 8 / 9 setDlpLumensComponent_RGB_Level / get, 177 / 178
  setDispLumensLevel / getDispLumensLevel), the boost setter 151, AMD FreeSync 172 / 173, colour space 102 / 103,
  HDR 174 / 175 / 191 / 192, aspect ratio, screen offset. X50GameTest and game_mutate.py hold this.

## 5. Not offered on the X50 (left out of devices/g0111|g0100/menu.json game_menu.items)

- `ar` aspect ratio: stock goes through XgimiService MstPictureManager.setZoomMode / getVideoArcType, gmpf code not
  traced (X50_MENU_D.md section 7).
- COLOR_SPACE_V2, AMD_FREE_SYNC_PREMIUM, HDR_SETTINGS, SCREEN_OFFSET, ZOOM_MAP, MENU_THEME: no module item.
- Ultra Max (G0100): the same list; none of these items is laser related, so its profile equals the Ultra's.

## 6. HAL checker: the call set before and after (tools/devices/check_aidl_hal.py, check_hal.py --dex)

Before (core-menu 05b584d): `check_aidl_hal: OK: 89 calls on 7 interfaces`. After (core-menu-x50game):
`OK: 101 calls on 7 interfaces` on the sources and on the dex of X50Projector.apk built for g0111 and for g0100
(`check_hal.py --device g0111|g0100 --app apps/X50Projector --dex`). Same 7 interfaces; nothing removed; 12 added:

```
+ DisplayManager   179 getDispOutputFps         int()                   args:-
+ GmAudioManager    75 setGamingSoundEffectMode boolean(int)            args:190..194
+ GmAudioManager    76 getGamingSoundEffectMode int()                   args:-
+ GmTvManager      120 setAisrType              boolean(int)            args:0..1
+ GmTvManager      121 getAisrType              int()                   args:-
+ GmTvManager      122 getVideoInfo             boolean(stVideoInfo)    args:-
+ GmTvManager      145 setVRRModeEnable         int(int, int)           args:0..1,1..3
+ GmTvManager      146 getVRREnableState        int(int)                args:1..3
+ GmTvManager      147 setGamePictureMode       int(int, int)           args:0..4,1..3
+ GmTvManager      148 getGamePictureMode       int(int)                args:1..3
+ GmTvManager      149 setDarkBoostOnoff        int(boolean, int, int)  args:0..1,0..4,1..3
+ GmTvManager      150 getDarkBoostState        boolean(int, int)       args:0..4,1..3
```

Kept equal in three places: GmpfCall (4th argument = ArgSpec, enforced by the transport before the transact),
allowed_x50projector.tsv (args column, rule 8) and verify_tree_x50.py MENU_APP (4-field rows,
x50_menu_apk_check_test.sh). None is BANNED or REVIEW in banned.tsv; no unban row was needed
(unbanned_x50projector.tsv unchanged, g0100's copy stays row-equal).

## 7. Module requests G1-G5 (REQUESTS-x50.md): state on core-menu

- G1 host: DONE. App installs GameMenu with X50GameIo in X50Menu's activity host (X50Menu.installGameMenu:
  GameMenu.install(app, new X50GameIo(), host(app))), the same ActivityPanelHost and Gate as the quick menu D, so the
  game menu never opens over X50EyeGuard's cover. The held gear (SETTINGS held >= 800 ms, and the remote's own hold
  scancode: 3840 / 3838 / 3843 127 and IR / keypad 139 = stock MENU, KEYCODE_TV_MEDIA_CONTEXT_MENU after the X50 key edit (x50_power_keys.py, ALL_APPS in the 1.0.9 base) in the image key
  layouts) runs KeyReceiver.gear(long) -> MenuController.onGearLong -> GameMenu.onGearLong: the game menu while game
  mode runs (DM 148 state 0) on the HDMI input on screen, as stock xhplayer InputActivity.dispatchKeyEvent (MENU UP ->
  GameMenuActivity.show), else the corner reset on the four-corner screen (stock KeyStoneWind MENU), else Android
  settings. The watcher ticks every 3 s; it reads the HAL and CEC only while HdmiSource shows an HDMI session and the
  screen is on (GameBridge.activePort is a field, no call). The activity host has no layers over the game, so the
  crosshair and frame counter tiles are not offered on the X50 (GameMenu.items); they stay in the profile.
- G2 ports: DONE (Io.hdmiPorts, X50GameIo 3; the watcher's CEC log has a slot per port).
- G3 sr gate: DONE (profile game_menu.gated [sr]: hidden until 121 reads 0 / 1).
- G4 game sound: DONE. Profile game_menu.sound lists the five stock values; labels from the stock names
  (game_sound_effects_clear "Clarity", balanced_mode "Balanced"), Lumen texts lumen_gm_snd_3 / _4 in every locale.
  GA 75 args 190..194 (all five are sent by the stock X50 menu, OtherFactory :1093 / :1269 and the surround / story /
  balance branches); a unit left on 193 / 194 by stock keeps the tile and shows the value.
- G5 colour space and HDR (v3 items cs / hdr): the X50 stock list has COLOR_SPACE_V2 (DM 102 / 103) and HDR_SETTINGS
  (TV 174 / 175 / 191 / 192); not proven or allowed here, so not in the X50 items. A later step.
