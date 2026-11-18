# Z9X picture parity: stock V6.15.62 against Lumen OS

Scope: why the picture of a Z9X (G0082, board `horse_popular`) on Lumen OS could differ from the stock system, and
what is already proven equal. Audit date 2026-11-17, Lumen build `lumen-1.0.11-beta10-20261115bnp`.

Names of the chip: the platform is `mt5877` (`ro.board.platform`), the stock apps call it chip `mtk9681`
(`ro.xgimi.board.chip`, same value on stock and on Lumen), and the folder of the stock image on the build host is
named `g0082_mt9952`. All three are the same projector.

How to read the verdicts:

- SAME: both sides proven, from code or from data.
- DIFFERENT / MISSING: stock code does something that Lumen code does not. Proven from both code bases.
- UNKNOWN-needs-stock-unit: only a run of the dump tool on a stock projector settles it.

No stock projector was available. Nothing in this document was measured on a stock unit. Every stock statement is
read from the decompiled stock apps or from the stock file system image. Every Lumen statement is read from the
Lumen code or from one dump of the owner's projector.

Short result: no difference was found that changes the picture of ordinary SDR, HDR10 or HLG content at default
settings. The proven gaps are all limited to HDR Vivid and Dolby Vision content, and their visible size is not
known. Whether the resting state of the vendor picture engine is equal has to be confirmed with the tool on a stock
unit (list in section 4).

## 1. Evidence

Stock code: host `xgimi-llm`, `~/z9xsys/dec/<app>/sources/...` and `~/aimode/out/z9x/<app>/sources/...` (jadx output
of the V6.15.62 system apps) and `~/aimode/out/z9x/ns_res/resources/assets` (assets of the stock settings app).
Stock file system: host `xgimi-builder`, `~/devices/g0082_mt9952/v61562/fs`. Line numbers below are jadx line
numbers. Stock method codes: `research/play6/z9x_gmpf_codes.txt`.

Saved raw output, in `~/lumen-build-tmp/z9x-picture-parity/` on the build Mac (not in the repository):

| File | Content |
|---|---|
| `live/picture_state_lumen_playing_20261117_022416.txt` | dump of the owner's Z9X on Lumen OS |
| `ev/01_stock_light_setters.txt` | stock callers of the light setters |
| `ev/02_xgimiconfig_sha_diff.txt` | checksums of the vendor tables, stock against live |
| `ev/03`, `ev/04`, `ev/10` | stock `FeatureUtils` feature gates, ambient light getters and their users |
| `ev/05`, `ev/06`, `ev/19` | stock `MutexConfig` loader, the rules of `platform/common.json`, the asset lists |
| `ev/07` | stock callers of the picture setters outside the settings app |
| `ev/08`, `ev/09`, `ev/17` | stock SystemUI HDR Vivid, Dolby and IMAX helpers |
| `ev/11` | stock `feature.xml` of G0082 and the build properties |
| `ev/12`, `ev/13`, `ev/16` | stock `SmartTurboLightService` and `LaunchUtils` |
| `ev/14` | search for stock HB code on `xgimi-llm` (none found) |
| `ev/15` | stock callers of the colour gamut setters |
| `ev/18` | stock callers of the HDR Vivid getters |
| `ev/20` | stock mutex action handler, list of the decompiled stock apps |

The Lumen dump was taken while the owner was watching a film (SDR, built in player, input source 0), so it shows a
playing state, not the home screen. Only getters were sent. The tool ran as the adb shell user (uid 2000), without
root, and removed its file from the projector. No getter answered with an error.

## 2. Ranked findings

| # | Item | Stock | Lumen | Verdict | Visible effect | Fix |
|---|---|---|---|---|---|---|
| 1 | HDR Vivid content start and stop | SystemUI `global/video/hdr/HdrVividHelper.java:39-80` handles TV event 22. Message `HDR_VIVID_LOGO_ON` calls `switchDisplayVivid(true)` (line 74), `HDR_VIVID_LOGO_OFF` calls `switchDisplayVivid(false)` (line 78). Message `HDRVIVID_SWITCH_WINDOW` shows a notice, at most three times, and only on OK runs `openHdrVivid()` (84-90): `setHdrVividState(source, pictureMode, 1)` and `setHdrEnable(true)`. Stock table: IGmpf2 304, 308, IGmpf 650 | The only TV event sink is registered in `AiScenePolicy.java:41` and `apps/core/lumen-aipq/.../AiPq.java:113-114` drops every event that is not type 5. No row for 304, 308 or 650 in `codes.tsv`. The owner's unit already reads `getHdrVividState` = 1 and `getHdrEnable` = true, the end state of `openHdrVivid()` | MISSING (`switchDisplayVivid`) | Only with HDR Vivid content (`getHdrType` = 6). Size not known. The IMAX helper calls `switchDisplayImax(true)` for both logo on and logo off (`IMaxHelper.java:46-70`), which suggests these calls report the logo to the vendor and do not switch the picture. That is a reading of the code, not a measurement | Section 5, A |
| 2 | AI picture during HDR Vivid | XRMService `utils/AIPQ.java:217-218` and `288-289` read `getHdrVividState(source, pictureMode)` and `isHdrVividPlaying()`. AI picture keeps the scene while HDR Vivid is on auto (state 1) and playing. The rule is ported in `AiPq.java:117-120` and `170-171` | `AiScenePolicy.java:76-77` answers `false` to both questions without asking the HAL. This is a recorded choice, not an oversight: the comment at `AiScenePolicy.java:63-66` says the vendor reports neither for the content Lumen shows. That statement was not measured. The HAL of the owner's unit answers `getHdrVividState` = 1 (auto) and `isHdrVividPlaying` false during an SDR film | DIFFERENT, by choice | Only with HDR Vivid content and picture mode AI: Lumen may change the scene where stock keeps it | Section 5, A |
| 3 | Dolby Vision content start | SystemUI `global/video/dolby/DolbyHelper.java:89-121` handles TV event 13 `Hdrtype:N`. When the authentication mode version is not 0: `Hdrtype:2` runs `launchDolbyMode()`, which calls `setDolbyDefaultMode(1)` (line 62), any other type with default mode 1 calls `setDolbyDefaultMode(0)` (line 96). When it is 0: `Hdrtype:2` opens the Dolby mode window after 6 s, unless `dolby_vision_default_mode` is 1 and the default mode already reads 1. Both Dolby branches need `support_dolby_vision`. Stock table: IGmpf2 65 | No row for 65 in `codes.tsv`. The owner's unit answers `getDolbyDefaultMode` = 0. Lumen has its own Dolby Vision picture mode switch (IGmpf 624, `hal/GmpfClient.java:151`) | MISSING in code, effect UNKNOWN-needs-stock-unit | Only with Dolby Vision content. `feature.xml` of G0082 has `dolby_vision_default_mode=1` (line 22) but no `support_dolby_vision` key (G0000 has it, line 79). Whether G0082 takes the Dolby branch, and which authentication version it has, was not established | Stock dump during Dolby Vision playback first |
| 4 | Lamp level read during playback | Not applicable | The dump reads `getDlpLumensLevel` = 1 and `getDlpLumensMode` = 17 while a film plays. An earlier boot log of the same unit had level 10. Lumen writes the lamp level from the menu (`Z9xPictureRows.java:46`), the sleep timer (`power/TimerLamp.java:144`) and the screen saver (`dream/DreamLamp.java:344,386`) | Observation, cause not determined | Level 1 is the dimmest picture. It may simply be the owner's night setting | Ask the owner which level he set. No code change proposed |
| 5 | Resting state of the vendor picture engine after a factory reset | No stock app writes it at boot (rows 7 to 10), so it comes from the vendor HAL and its tables | Same vendor partition: 255 of 256 files of `/mnt/vendor/xgimiconfig/G0082` are identical, section 3 | SAME by construction, values UNKNOWN-needs-stock-unit | None expected | Run the tool on a stock unit, section 4 |
| 6 | HDR10 and HLG content start and stop | Outside the settings app no stock code reacts to `Hdrtype` 1 or 3. The only HDR writers are rows 1 and 3, the IMAX helper (row 14) and the AI picture scene switch (`ev/07`). No caller of the tone mapping, colour space or lamp setters is tied to an HDR event | No code either. AI picture is the shared port of the stock one | SAME in code | None expected | None |
| 7 | Brightness adaptation row and ambient light | Not shown on G0082. `FeatureUtils.getAmbientLight()` is true only for version 2 or 7 (`FeatureUtils.java:430-442`), `getAmbientLightAdaptation()` only for version 1 (444-450). G0082 has `support_ambient_light=false`, `support_ambient_light_version=3` (`feature.xml:103-105`). The stock settings tree of this product (`research/z9x_stock_settings/tree_annot.txt`) has no adaptation node under brightness | No such row | SAME | None | None. Same conclusion as `docs/z9x/parity/brightness.md` row 19 |
| 8 | Smart Turbo Light | XRMService starts `SmartTurboLightService` (`XRMApplication.java:58`) and G0082 has `support_smart_turbo_light=true` (`feature.xml:109`). It acts only when `Settings.System smart_turbo_light_enabled` is 1 (default 0, `DisplayHelper.java:143`), the lamp level is 10, the vendor adaptation level is at least 30 and the ambient light reading is at least 1200 (`SmartTurboLightService.java`, `check()`, `ev/16`). The settings tree has no Turbo node and the model has no light sensor (`brightness.md` row 19) | Not present | SAME at defaults | None at defaults | None |
| 9 | Adaptation, colour enhance, Turbo, Eco setters | `setALOnOff`, `setColorEnhanceType`, `setColorEnhance`, `setTurboLightOnOff`, `setEcoState` are called only from menus, the brightness key dialog and row 8 (`ev/01`): `LaunchUtils.java:342-374` pairs `setALOnOff(false)` with `setColorEnhanceType(1)` and `setALOnOff(true)` with `setColorEnhanceType(0)`, same in `BrightnessRepository.java:849-855`. No caller at boot in XgimiService, XRMService, SystemUI, SettingsProvider or the framework services (`ev/07`) | No code for these setters. The owner's unit reads `getALOnOff` false, `getColorEnhanceType` -1, `getColorEnhance` -1, `getTurboLightOnOff` false, `getEcoState` false | SAME in code, values UNKNOWN-needs-stock-unit | None expected | Section 4 |
| 10 | Lamp level and mode at boot and on source change | Outside the settings app the only writers are the Dolby mode window (`DolbyHorizonAnimView.java:77`), the phone remote API (`SmartConnect .../MiddlewareManager.java:174`) and auto keystone (`AutoKst .../NormalDevice.java:262`, mode 0). Inside the settings app all writers are menu or mutex actions (`ev/12`). Nothing writes at boot or on a source change | No writer at boot or on a source change. The vendor restores level and mode by itself (boot log `SetCri current 629/775/816` at level 10 mode 17) | SAME in code, laser current numbers UNKNOWN-needs-stock-unit | None expected | Compare the `SetCri` log line of a stock unit, section 4 |
| 11 | Mutex rule file | `MutexConfig.java:36-59` loads `platform/<chip>.json`, falls back to `platform/common.json`, then adds `device/<product name>.json`. The chip is `mtk9681` for board `horse_popular` (`GimiCommonService.java:196-198`) and the product name is `Product_dapeng`. The assets hold `common`, `mst848`, `mtk9270`, `mtk958`, `mtk9612`, `mtk9679` and no `product_dapeng.json` (`ev/19`), so G0082 uses `common.json` only (46 rules, `ev/06`) | The Lumen menu rules were written from `common.json` (`docs/z9x/parity/brightness.md`). Not audited again here | SAME source, Lumen side not re-checked | None known | None |
| 12 | `setHdrEnable` | Every stock caller with a literal argument passes `true` (`HdrVividHelper.java:88`, settings `middleware/MiddlewareManager.java:247`). The trigger of the second one was not traced. XgimiService only passes the call of other apps through (`GimiVideoService.java:213`) | Never called. The owner's unit reads `getHdrEnable` true | SAME at rest | None | None |
| 13 | Colour accuracy correction, contrast enhance, dual light | `setHighColorGamutMode(1 or 0, ...)` from the two correction dialogs of the brightness module and from the `masterColor` mutex (`DisplayMutexManager.java:109-128`). `setDispContrastEnhanceLvl` from `PictureRepository.java:3196` and the `contrastEnhance` mutex (`DisplayMutexManager.java:432`). `setDualLightMode` from `PictureRepository.java:2958-2963`. The settings tree of this product has none of these nodes | No code. The unit reads `getHightColorGamutFlag(0)` 1, `getDispContrastEnhanceLvl` 0, `getDualLightMode` 0 | SAME in code, values UNKNOWN-needs-stock-unit | None expected | Section 4 |
| 14 | IMAX Enhanced | `IMaxHelper.java:46-70` calls `switchDisplayImax` on TV event 18. G0082 has `support_imax=false` (`feature.xml:38`) | No code. The unit reads `isImaxVideoPlaying` false, `getImaxType` -1 | SAME for G0082 | None | None |

What the mutex rules of row 11 couple, picture part only (`ev/06`). Each rule names a function and the states
that stop it, with an action number. Action 2 refuses the change and shows a notice, action 4 asks the user to
confirm (`DispatchFun.java:117-130`, `ev/20`). The handling of action 5 was not read.

- Colour accuracy (`masterColor`, `masterColorMode`) is refused in 3D, in Dolby Vision, while Turbo Light is on and
  at brightness level Boost.
- Turbo Light is refused while adaptation is on and at brightness level Boost.
- Adaptation (`aLOnOff`) is refused in lumens modes custom, office and performance, at Boost and in 3D, and asks to
  confirm while Turbo Light is on.
- A brightness level change is refused in lumens modes custom, office and performance, and asks to confirm while
  Turbo Light is on. It carries action 5 for the wall colour adaptation.
- Contrast enhance is refused in lumens mode performance.
- A picture mode change is refused in game mode, Dolby Vision, IMAX and 3D.

Stock setters that are in the stock flow and have no row in `devices/horse_popular/hal/codes.tsv`:

| Setter | Stock table | Where stock calls it | Needed on G0082 |
|---|---|---|---|
| `switchDisplayVivid` | IGmpf2 304 | row 1 | yes, row 1 |
| `setHdrVividState` | IGmpf2 308 | row 1, after the user accepts the notice | only with a Lumen notice or menu switch |
| `setHdrEnable` | IGmpf 650 | rows 1 and 12 | same as above |
| `setDolbyDefaultMode` | IGmpf2 65 | row 3 | unknown, row 3 |
| `switchDisplayImax`, `setImaxState` | IGmpf2 317, 318 | row 14 | no, `support_imax=false` |
| `setALOnOff`, `setColorEnhanceType`, `setColorEnhance`, `setTurboLightOnOff`, `setEcoState` | IGmpf | rows 8 and 9 | no, not reachable in the stock menu of G0082 |
| `setHighColorGamutMode`, `setDispContrastEnhanceLvl`, `setDualLightMode` | IGmpf2 | row 13 | no, same reason |

Not covered by the stock code that was read: the first run wizard and the launcher are not in the decompiled set
(`ev/20`). A
write of a picture value by one of them at first boot cannot be ruled out from code. The stock dump of section 4
settles this too.

### Z9X HB (G0130, `goat_elegant`)

The stock code of the HB was not found on `xgimi-llm`: `~/hb-ref` and `~/hb-out` hold Lumen build output only
(`ev/14`). The stock side below is the method table `devices/goat_elegant/research/aidl_codes.tsv` and the feature
notes `devices/goat_elegant/research/features.md`. The Lumen side is a keyword search of `apps/HbProjector/src`.

| Row | HB |
|---|---|
| 1 | Same gap in code. The stock HB has `switchDisplayVivid` (GmTvManager 137), `setHdrVividState` (133) and `setHdrEnable` (50), and `support_hdr_vivid=true` (`features.md:83`). `apps/HbProjector/src` calls none of them. What the stock HB SystemUI does with them was not read |
| 2 | Different shape. No class under `apps/HbProjector` implements `AiPqVendor` (only the Z9X and the X50 do), so the AI picture rule does not run on the HB. The stock HB has both getters (GmTvManager 134, 135) |
| 3 | Probably not applicable. The stock HB has `setDolbyDefaultMode` (DisplayManager 138), but its picture mode list has no Dolby Vision mode (`features.md:70`) |
| 8 | Open. The stock HB has `support_smart_turbo_light=true` (`features.md:91`). No Lumen code |
| 4 to 7, 9 to 14 | Not checked for the HB |

## 3. Excluded

Checked again in this audit:

- Vendor picture tables. Earlier lists compared 168 regular files and skipped the 88 symbolic links of `G0082` into
  `G0000` (picture quality, HDR, 3D LUT, gamma, DLC, DLP files). With the links followed: 256 files on both sides,
  255 identical. The one different file is `system.ini`: the stock image has a `[SUPPORT_SLAVE_DEVICE]` block at
  the end that the owner's unit does not have. It is not a picture setting (`ev/02`).

Taken from the previous audit pass and not repeated here:

- Stored picture settings equal the factory defaults, except motion compensation set to 0.
- The per unit calibration directory is empty.
- The SurfaceFlinger colour mode is identical.
- The vendor daemons are identical.
- The default AI scene is 16 on both.

## 4. What only a stock unit can settle

Run the tool on a stock Z9X (V6.15.62, home screen, picture mode Standard, out of the box brightness) and compare
with a Lumen dump taken in the same state:

```
sh tools/devices/diag/picture_state/build.sh
sh tools/devices/diag/picture_state/picture_state.sh -s STOCK_IP:5555 stock.txt
sh tools/devices/diag/picture_state/picture_state.sh -s LUMEN_IP:5555 lumen.txt
sh tools/devices/diag/picture_state/picture_state_diff.sh stock.txt lumen.txt
```

The tester text in plain language is in `tools/devices/diag/picture_state/README.md` (English and Russian).

Not known before the first stock run: whether the stock system lets the adb shell user reach the vendor picture
service. If it refuses, the `[hal]` rows will show an error and only the properties, settings, SurfaceFlinger and
checksum sections of the stock file will be usable.

Values to compare, with the reading of the owner's Lumen unit (film playing, SDR):

| Getter | Lumen reading |
|---|---|
| IGmpf 154 `getALOnOff`, 79 `getALOnType` | false, 0 |
| IGmpf 77 `getColorEnhanceType`, 81 `getColorEnhance` | -1, -1 |
| IGmpf 277 `getTurboLightOnOff`, 327 `getEcoState`, IGmpf2 69 `getAutoEcoMode` | false, false, false |
| IGmpf 104 `getAutoColorStatus`, 103 `getAutoColorCalibrationStatus` | false, true |
| IGmpf 649 `getHdrEnable`, 634 `getHdmiRange`, 620 `getHdrType` | true, 0, 0 |
| IGmpf 166 `getDlpXPRMode`, 538 / 539 CCA / CCI, 145 `getDlpReduceCurrentFlag` | true, false / false, 0 |
| IGmpf 226 `getLightType`, 447 `getLensIrisLevel` | 20, 1000 |
| IGmpf 590 `getAisrLevel`, IGmpf2 321 `getAisrType` | 2, 1 |
| IGmpf 175 / 177 lumens mode / level | 17 / 1 (level is a user setting, compare at the same level) |
| IGmpf2 25 `getDispLumensLevel`, 51 `getLensDispMode` | 1000, 0 |
| IGmpf2 29 `getDynamicBlackType`, 28 `getDynamicBlackLevel` | 0, 1 |
| IGmpf2 36 `getDispContrastEnhanceLvl`, 43 `getDualLightMode` | 0, 0 |
| IGmpf2 47 `getHightColorGamutFlag(0)` | 1 |
| IGmpf2 64 `getDolbyDefaultMode`, 307 `getHdrVividState`, 306 `isHdrVividPlaying` | 0, 1, false |
| IGmpf 623 `getDolbyVisionPicMode`, 621 / 622 Dolby Vision Tmax gain / size | -1, -1 / -1 |
| IGmpf2 285 / 287 AI contrast level / status, 302 AI picture enable (type 3) | 2 / 1, false |
| IGmpf2 326 TV gamma level, 328 dynamic contrast, 330 HSY picture mode | 4, true, true |
| IGmpf2 269 HDR dynamic tone mapping, 283 FD boost, 294 FFM state | 0, 0, 0 |
| IGmpf2 309 `getImaxType`, 315 `getImaxState` | -1, 2 |
| IGmpf 666 picture mode, 692 / 694 / 690 / 688 / 686 brightness, contrast, saturation, sharpness, hue | 1, 50 each |
| IGmpf 684 colour temperature, 672 to 682 RGB gain and offset | 1, 50 each |
| IGmpf 111 colour space, 668 MEMC, 670 noise reduction, 651 UCD | 4, 0, 2, 2 |

A reading of -1 (colour enhance, Dolby Vision, IMAX type) is what the HAL answered. Whether it means "not
supported" or "not set" is not known, the stock file will show if stock answers the same.

A second stock file taken while an HDR10 film plays, and a third with Dolby Vision if the tester has such a film,
settle rows 3 and 6.

Laser currents (row 10). The tool does not read them, see below. The vendor prints them to the system log by
itself, so on a stock unit, right after boot:

```
adb -s STOCK_IP:5555 logcat -d | grep -i SetCri
```

Compare with the Lumen line `SetCri current 629/775/816` at level 10, mode 17. This reads the log only.

Four getters are in the table of the tool, are printed as `SKIPPED` and are never sent:

- IGmpf 194 `isPlayingHDRVideo`: marked forbidden in `docs/play6/inventory/C_play6_hal_capability.md:493`.
- IGmpf 275 `getAmbientLight`: no sensor on this model, banned in `apps/LumenSensors/.../hal/GmpfAllowlist.java:49`.
- IGmpf2 252 `getCurHwCurrent`: the project notes say the laser current getter is forbidden on the Z9X and the HB,
  while `GmpfAllowlist.java:30` allows it and `apps/LumenSensors/.../z9x/Z9xSchedule.java:124` polls it. The two do
  not agree, so the tool does not send it. It is one row of the table in `PictureState.java` once the owner decides.
- IGmpf 122 `getLumensAdjState`: no stock caller shows which argument to pass.

## 5. Fix plan

Nothing was implemented. No gap is proven to change the picture of ordinary content. The proven gaps cannot be
tested without HDR Vivid content, and row 2 is a recorded choice of the Lumen code that the owner has to reverse.

A. HDR Vivid (rows 1 and 2), shared code with the Z9X as data:

1. Add rows to `devices/horse_popular/hal/codes.tsv` for IGmpf2 306 `isHdrVividPlaying`, 307 `getHdrVividState`
   and 304 `switchDisplayVivid`. Check with `tools/hal/check_codes.py`.
2. In `apps/core/lumen-gmpf` add the two reads and the one switch. In `AiScenePolicy.Vendor` answer
   `hdrVividAuto` with `getHdrVividState(source, pictureMode) == 1` and `hdrVividPlaying` with `isHdrVividPlaying()`
   instead of `false`. The rule and its tests are already in `apps/core/lumen-aipq`. Both reads answered on the
   owner's unit for the shell user (section 4).
3. In the TV event sink handle event 22: message `HDR_VIVID_LOGO_ON` calls the switch with true,
   `HDR_VIVID_LOGO_OFF` with false (`HdrVividHelper.java:23-25`). Leave `HDRVIVID_SWITCH_WINDOW` out: it is a
   notice with a user choice, and the unit already has the state it would set.
4. Tests: a sink test for the two messages, and the existing guard test of `apps/core/lumen-aipq/test` with both
   answers true.
5. Verify on a unit with an HDR Vivid file before release. Without such a file this stays unverified. Step 2 alone
   only reads and is the safe first part.

B. Dolby Vision (row 3): no change before a stock dump during Dolby Vision playback shows `getDolbyDefaultMode` and
the picture mode stock ends in.

C. Rows 5, 9, 10, 13: no change. Wait for the stock dump.

D. HB: after A, the same event handling applies to the HB through its own method numbers. The missing AI picture
driver on the HB is a separate task.
