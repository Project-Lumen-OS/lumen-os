# X50 stock menu call sites (evidence for allowed_x50projector.tsv)

Source: jadx output of the stock X50 v1.2.64 apps on xgimi-builder `~/devices/g0111_mt9681/work/dec/`
(release_838_newsettings, SystemUI_release_838, XgimiHome_release838, minitvfactory_release_838), grep of call sites
outside the bundled `com/xgimi/gmpf/api` wrappers (2026-11-08, script `grepcalls.py`). Paths are relative to each
app's `sources/`. Key sequences quoted in apps/X50Projector/README.md were read in full:
- newsettings `com/xgimi/focusing/FocusViewModel.java` 155-305: MANUAL_FOCUS_LEFT manualFocus(1), RIGHT manualFocus(0), each followed by a coroutine delay(400) and manualFocus(2); AUTO_FOCUS -> FocusFunctionManager.autoFocus -> GmDisplayManager.autoFocus(46) (ProjectorFocusManager 3, BANNED, not used).
- SystemUI `FocusUIV2.java` 499: autoFocus() = GmDeviceManager.sendFocusStatus(MusicPlayerManager.EN_LED_AIRPLAY_DISCON = 44).
- newsettings `com/xgimi/keystone/KeyStoneManager.java` 130-145: autoKeystone(): misckey caller newAutoKst(9), otherwise autoKst(); 255-272 and 318-334: reset = setIChipReset + setLowConfigureKeyStoneValue(50) + zoomControl(5, 10) with optical zoom + setTiltShiftMove(13, 0) with lens shift; 343 setBootAk = savePowerOnAKFlag; 354/362 setProjScreenAdp / setProjCurtainAdp.
- newsettings gmuiapi `V3DisplayManager.java` 183-200: setIChipReset = correctKeystoneReset(0); if getCurrentKeystoneMode() == 6: switchKeystoneMode(0, 0).
- newsettings `FocusRepository.java` 148-182 / `FocusFunctionManager.java` 53-75: setBootFocus -> setBootFocusAuto -> powerOnAFEnable(v == 1); setDisplacementFocus -> setFocusAutoOnOff (read getAngTriggerAF); setSmartAutoFocus = setPowerOnAFUseDistance(z) + setDisplacementFocus(z).
- newsettings `ProjectionViewModel.java` 245-330: setProjectorPutMode 0 / 1 / 2 and setProjectorPutModeV5(10 / 11) (not used: mapping unproven).
- t14, corner reset key (read in full, 2026-11-08): newsettings `com/xgimi/keystone/view/keystone/KeyStoneWind.java` 1093-1133 onKey: keyCode 158 -> setCorrectKeystoneReset (any action); ACTION_UP keyCode 82 (MENU) with FeatureUtils.getSupportLaser() false -> KeyStoneManager.setCorrectKeystoneReset() + onKeyEnter(false) (laser devices open the left menu instead); `KeyStoneManager.java` 389-391 setCorrectKeystoneReset = GmDisplayManager.setIChipReset() only; KeyStoneWind 612-655 freshMenuIcon: SystemUtils.isNewRc() (persist.xgimi.rc.version contains B990C / B992 / B995C_ / B940 / B970 / B972; the X50 reads `B970C_CY07_CY_V1.0.4`, full_20261108_1335/getprop.txt) and no laser = icon_zoom_icon_58 (a gear with menu lines) + long_press_reset "长按恢复默认画面"; old remotes keystone_ic_menu (menu lines) + reset_key_stone. X50 feature.xml (`work/fs/mnt/vendor/xgimiconfig/G0111/feature/feature.xml`) support_laser_device=false. Stock vendor `Vendor_000d_Product_3840.kl`: key 127 MENU, key 66 MOVE_HOME (menu_live_20261108/stock_live_vendor_kl.txt). `xgimi_action_config.xml` (vendor/etc, no G0111 copy) has no long-press action for 122, so XgimiWindowManager passes held MOVE_HOME on.
- t14, level angles (read in full): newsettings `com/xgimi/common/dialog/AngleMonitorDialog.java` 86-89 keepHeartBeat setMotionACalEnable(true) every 5 s, 247-266 show/dismiss (getAccTriggerAK, temporarySwitchTrigger(2, false/true), setMotionACalEnable(false)), 268-353 refreshAngle every 500 ms: GmDeviceManager.getMotionAttitude(), roll / pitch > 180 minus 360, "%.1f" + "°", tvHorizontalAngle = roll, tvVerticalAngle = pitch; title horizontal_level_installation_assistance (pantilt type 0). Path: `com/xgimi/gmuiapi/api3/V3MotionDetectionManager.java` 155-180 -> gmpf.api MotionDetectionManager 126 -> middle `com/xgimi/middle/MotionDetectionManagerWrapper.java` 148-159 -> `xgimi/hardware/gmpf/IMotionDetectionManager.java` Proxy 1053-1074 (code 25, no argument; reply exception, boolean, int non-null, stAttitudeInfo = int size, float roll, float pitch). MoreViewModel 1318-1321 ACTION_ANGLE_MONITOR "more.advance.angle.monitor" opens it; the X50 settings tree `raw/settings_tree_product_dianapro` (ro.product.name Product_dianapro) lists node angle_monitor right after reset_keystone.

### Manual focus and auto keystone (t13 live findings, read in full 2026-11-08)
- The remote's focus key HELD sends FOCUS_MAN_NEW (BT 000d:3838-3843 scancode 62, keypad / ucontrol 3697:0002 280;
  short press = FOCUS_AUTO_NEW 65 / FOCUS_AUTO 87). SystemUI `FocusUIV2.receiveIntent` AF_AK type 7 -> handler message 14
  -> `FocusAdjust.showFocusAdjust(true, "remoteControl")` when neither FocusAdjust nor AutoMaticFA is shown
  (FocusUIV2.java 488-490, 1400-1405): the overlay, never the settings menu. newsettings `MANUAL_FOCUS` ->
  `FocusFunctionManager.manualFocus()` -> AF_AK type 1 -> `FocusUIV2.manualFocus` -> the same overlay.
- `FocusAdjust` (SystemUI, 634 lines): `showFocusAdjust` posts `GmDeviceManager.manualFocus(17)` once (343);
  `newVontrolAf` (452-519): DOWN of 21 / 2097 / 2113 / 156 (left) -> manualFocus(1), of 22 / 2098 / 2114 / 157 (right)
  -> manualFocus(0), sent again on every repeat; UP of those -> manualFocus(2); BACK UP closes; DOWN of any other key
  (not 2095 / 2096 / BACK) closes; the first 2104 (FOCUS_MAN_NEW) key in the overlay is ignored (439); 210 / 2099 (focus
  key) closes it (432). Auto hide 3000 ms after the last key (`MicrophoneWindow.HIDE_MENU_DELAY_TIME = 3000`). Mouse
  wheel: 1 / 0 then 2 after 200 ms. So the lens moves while the key is held: a short press is a short step.
  newsettings `FocusViewModel` MANUAL_FOCUS_LEFT / RIGHT (1 or 0, delay 400, 2) has no caller in the X50 newsettings
  sources or resources (grep of `manualFocusLeft`); it is not what the stock remote or the focus page uses.
- X50 vendor `libxgimi.so` `FocusMotor::ManualFocus(EN_FOCUS_DIR)` (0x1985620): values 8..17 go through a jump table;
  case 17 only logs and returns 1 (no motor command).
- Auto keystone: newsettings `KeyStoneViewModel` ACTION_AUTO_KEYSTONE -> `KeyStoneManager.autoKeystone(packageName)` ->
  `GmDisplayManager.autoKst()` (misckey caller: newAutoKst(9)); nothing before or after, the returned byte is dropped.
  The auto focus seen during the AK is the vendor's own (HB, same stock code and AIDL gmpf: `autoKst() -> 1`, vendor
  AF events inside the AK, RESULTS_hb-t6 2.10). The byte: X50 `AK3_Process::startAutoKeystone(int)` (0x6461a4, the AK3
  family the X50 logs as XLAB_AUTO_KEYSTONE3) returns 1 on its normal path; the generic `AK_Process::autoKst(int)`
  returns 0; HB and the X50 t13 trial returned 1 for working AKs. Stock shows the AK outcome from vendor focus events
  (FocusUIV2.onProjectorFocusEventAkCall toasts), not from this byte.

## grep output (first 6 sites per method and app)

```
== autoKst SystemUI_relea (4)
   com/xgimi/device/GmDisplayManager.java:2168: return Byte.valueOf(ProjectorFocusManager.INSTANCE.autoKst());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:218: bAutoKst = com.xgimi.gmpf.api.ProjectorFocusManager.getInstance().autoKst();
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:154: return ProjectorFocusManagerService.getInstance().autoKst();
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:318: return this.service.autoKst();
== autoKst XgimiHome_rele (4)
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:87: GmDisplayManager.autoKst();
   com/xgimi/device/GmDisplayManager.java:4715: Byte bValueOf = Byte.valueOf(ProjectorFocusManager.INSTANCE.autoKst());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:2005: bAutoKst = com.xgimi.gmpf.api.ProjectorFocusManager.getInstance().autoKst();
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:135: return mVarC.f13346a.autoKst();
== autoKst minitvfactory_ (9)
   com/xgimi/device/GmDisplayManager.java:2169: return Byte.valueOf(ProjectorFocusManager.INSTANCE.autoKst());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:217: bAutoKst = com.xgimi.gmpf.api.ProjectorFocusManager.getInstance().autoKst();
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:154: return ProjectorFocusManagerService.getInstance().autoKst();
   com/xgimi/middleapi/ProjectorFocusApi.java:256: ProjectorFocusManager.getInstance().autoKst();
   com/xgimi/minitvfactory/auto/AutoData.java:1696: ProjectorFocusApi.autoKst();
   com/xgimi/minitvfactory/auto/AutoModel.java:2750: ProjectorFocusApi.autoKst();
== autoKst release_838_ne (6)
   com/xgimi/keystone/KeyStoneManager.java:140: GmDisplayManager.autoKst();
   com/xgimi/device/GmDisplayManager.java:2183: return Byte.valueOf(ProjectorFocusManager.INSTANCE.autoKst());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:217: bAutoKst = com.xgimi.gmpf.api.ProjectorFocusManager.getInstance().autoKst();
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:154: return ProjectorFocusManagerService.getInstance().autoKst();
   com/xgimi/mutex/module/manager/KeyStoneMutexManager.java:67: GmDisplayManager.autoKst();
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:318: return this.service.autoKst();
== checkTrapezoidCoordinate SystemUI_relea (5)
   com/xgimi/device/GmDisplayManager.java:2137: return Boolean.valueOf(DisplayManager.INSTANCE.checkTrapezoidCoordinate(keyStoneFullCoordinates));
   com/xgimi/gmuiapi/manager/DisplayManager.java:1802: return Boolean.valueOf(DisplayManager.INSTANCE.getManager().checkTrapezoidCoordinate(keyStoneFullCoordinates));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:352: return getDisplayManager().checkTrapezoidCoordinate(GmpfTrans.trans(keyStoneFullCoordinates));
   com/xgimi/middle/DisplayManagerWrapper.java:523: return DisplayManagerService.getInstance().checkTrapezoidCoordinate(kstPoint);
   xgimi/hardware/gmpf/DisplayManagerService.java:715: return this.service.checkTrapezoidCoordinate(kstPoint);
== checkTrapezoidCoordinate XgimiHome_rele (5)
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:559: return GmDisplayManager.checkTrapezoidCoordinate(newCoordinates2) ? 0 : -1;
   com/xgimi/device/GmDisplayManager.java:4897: Boolean boolValueOf = Boolean.valueOf(DisplayManager.INSTANCE.checkTrapezoidCoordinate(this.$keyStoneFullCoordinates));
   com/xgimi/gmuiapi/manager/DisplayManager.java:2175: Boolean boolValueOf = Boolean.valueOf(DisplayManager.access$getManager(DisplayManager.INSTANCE).checkTrapezoidCoordinate(this.$keyStoneFullCoordinates));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:217: boolean zCheckTrapezoidCoordinate = getDisplayManager().checkTrapezoidCoordinate(GmpfTrans.trans(keyStoneFullCoordinates));
   com/xgimi/middle/DisplayManagerWrapper.java:369: return cVarC.f13324a.checkTrapezoidCoordinate(kstPoint);
== checkTrapezoidCoordinate minitvfactory_ (5)
   com/xgimi/device/GmDisplayManager.java:2138: return Boolean.valueOf(DisplayManager.INSTANCE.checkTrapezoidCoordinate(keyStoneFullCoordinates));
   com/xgimi/gmuiapi/manager/DisplayManager.java:1819: return Boolean.valueOf(DisplayManager.INSTANCE.getManager().checkTrapezoidCoordinate(keyStoneFullCoordinates));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:353: return getDisplayManager().checkTrapezoidCoordinate(GmpfTrans.trans(keyStoneFullCoordinates));
   com/xgimi/middle/DisplayManagerWrapper.java:523: return DisplayManagerService.getInstance().checkTrapezoidCoordinate(kstPoint);
   xgimi/hardware/gmpf/DisplayManagerService.java:715: return this.service.checkTrapezoidCoordinate(kstPoint);
== checkTrapezoidCoordinate release_838_ne (6)
   com/xgimi/keystone/KeyStoneManager.java:386: return GmDisplayManager.checkTrapezoidCoordinate(getNewCoordinates()) ? 0 : -1;
   com/xgimi/device/GmDisplayManager.java:2152: return Boolean.valueOf(DisplayManager.INSTANCE.checkTrapezoidCoordinate(keyStoneFullCoordinates));
   com/xgimi/gmuiapi/manager/DisplayManager.java:1820: return Boolean.valueOf(DisplayManager.INSTANCE.getManager().checkTrapezoidCoordinate(keyStoneFullCoordinates));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:355: return getDisplayManager().checkTrapezoidCoordinate(GmpfTrans.trans(keyStoneFullCoordinates));
   com/xgimi/middle/DisplayManagerWrapper.java:523: return DisplayManagerService.getInstance().checkTrapezoidCoordinate(kstPoint);
   xgimi/hardware/gmpf/DisplayManagerService.java:715: return this.service.checkTrapezoidCoordinate(kstPoint);
== correctKeystoneReset SystemUI_relea (5)
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:185: getDisplayManager().correctKeystoneReset((byte) 0);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:193: getDisplayManager().correctKeystoneReset(type);
   com/xgimi/systemui/focus/keystonewind/FineTuneAutoKeyWind.java:283: DisplayManager.getInstance().correctKeystoneReset((byte) 0);
   com/xgimi/middle/DisplayManagerWrapper.java:320: DisplayManagerService.getInstance().correctKeystoneReset(b);
   xgimi/hardware/gmpf/DisplayManagerService.java:445: this.service.correctKeystoneReset(b);
== correctKeystoneReset XgimiHome_rele (3)
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1075: getDisplayManager().correctKeystoneReset(b8);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1425: getDisplayManager().correctKeystoneReset((byte) 0);
   com/xgimi/middle/DisplayManagerWrapper.java:408: cVarC.f13324a.correctKeystoneReset(b8);
== correctKeystoneReset minitvfactory_ (6)
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:186: getDisplayManager().correctKeystoneReset((byte) 0);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:194: getDisplayManager().correctKeystoneReset(type);
   com/xgimi/middle/DisplayManagerWrapper.java:320: DisplayManagerService.getInstance().correctKeystoneReset(b);
   com/xgimi/middleapi/DisplayApi.java:315: DisplayManager.getInstance().correctKeystoneReset(value);
   com/xgimi/minitvfactory/ui/BalanceControl.java:871: DisplayApi.correctKeystoneReset((byte) 0);
   xgimi/hardware/gmpf/DisplayManagerService.java:445: this.service.correctKeystoneReset(b);
== correctKeystoneReset release_838_ne (4)
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:188: getDisplayManager().correctKeystoneReset((byte) 0);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:196: getDisplayManager().correctKeystoneReset(type);
   com/xgimi/middle/DisplayManagerWrapper.java:320: DisplayManagerService.getInstance().correctKeystoneReset(b);
   xgimi/hardware/gmpf/DisplayManagerService.java:445: this.service.correctKeystoneReset(b);
== getCorrectKeystoneFullPiont SystemUI_relea (2)
   com/xgimi/middle/DisplayManagerWrapper.java:183: KstPoint correctKeystoneFullPiont = DisplayManagerService.getInstance().getCorrectKeystoneFullPiont(keyStoneFullCoordinates.kstMode);
   xgimi/hardware/gmpf/DisplayManagerService.java:231: return this.service.getCorrectKeystoneFullPiont(b);
== getCorrectKeystoneFullPiont XgimiHome_rele (1)
   com/xgimi/middle/DisplayManagerWrapper.java:2654: correctKeystoneFullPiont = cVarC.f13324a.getCorrectKeystoneFullPiont(b8);
== getCorrectKeystoneFullPiont minitvfactory_ (2)
   com/xgimi/middle/DisplayManagerWrapper.java:183: KstPoint correctKeystoneFullPiont = DisplayManagerService.getInstance().getCorrectKeystoneFullPiont(keyStoneFullCoordinates.kstMode);
   xgimi/hardware/gmpf/DisplayManagerService.java:231: return this.service.getCorrectKeystoneFullPiont(b);
== getCorrectKeystoneFullPiont release_838_ne (2)
   com/xgimi/middle/DisplayManagerWrapper.java:183: KstPoint correctKeystoneFullPiont = DisplayManagerService.getInstance().getCorrectKeystoneFullPiont(keyStoneFullCoordinates.kstMode);
   xgimi/hardware/gmpf/DisplayManagerService.java:231: return this.service.getCorrectKeystoneFullPiont(b);
== getCurrentKeystoneMode SystemUI_relea (8)
   com/xgimi/device/GmDisplayManager.java:4984: return Integer.valueOf(DisplayManager.INSTANCE.getCurrentKeystoneMode());
   com/xgimi/gmuiapi/manager/DisplayManager.java:2454: return Integer.valueOf(DisplayManager.INSTANCE.getManager().getCurrentKeystoneMode());
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:186: if (getDisplayManager().getCurrentKeystoneMode() == 6) {
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:194: if (getDisplayManager().getCurrentKeystoneMode() == 6) {
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:490: return getDisplayManager().getCurrentKeystoneMode();
   com/xgimi/systemui/utils/SystemUtils.java:353: if (GmDisplayManager.getCurrentKeystoneMode() == 1) {
== getCurrentKeystoneMode XgimiHome_rele (6)
   com/xgimi/device/GmDisplayManager.java:6131: Integer numValueOf = Integer.valueOf(DisplayManager.INSTANCE.getCurrentKeystoneMode());
   com/xgimi/gmuiapi/manager/DisplayManager.java:3163: Integer numValueOf = Integer.valueOf(DisplayManager.access$getManager(DisplayManager.INSTANCE).getCurrentKeystoneMode());
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:396: int currentKeystoneMode = getDisplayManager().getCurrentKeystoneMode();
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1076: if (getDisplayManager().getCurrentKeystoneMode() == 6) {
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1426: if (getDisplayManager().getCurrentKeystoneMode() == 6) {
   com/xgimi/middle/DisplayManagerWrapper.java:771: return cVarC.f13324a.getCurrentKeystoneMode();
== getCurrentKeystoneMode minitvfactory_ (7)
   com/xgimi/device/GmDisplayManager.java:4985: return Integer.valueOf(DisplayManager.INSTANCE.getCurrentKeystoneMode());
   com/xgimi/gmuiapi/manager/DisplayManager.java:2471: return Integer.valueOf(DisplayManager.INSTANCE.getManager().getCurrentKeystoneMode());
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:187: if (getDisplayManager().getCurrentKeystoneMode() == 6) {
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:195: if (getDisplayManager().getCurrentKeystoneMode() == 6) {
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:491: return getDisplayManager().getCurrentKeystoneMode();
   com/xgimi/middle/DisplayManagerWrapper.java:607: return DisplayManagerService.getInstance().getCurrentKeystoneMode();
== getCurrentKeystoneMode release_838_ne (9)
   com/xgimi/keystone/view/keystone/KeyStoneWind.java:734: KLog.e("userManuKeystoneType1", Boxing.boxInt(GmDisplayManager.getCurrentKeystoneMode()));
   com/xgimi/keystone/view/keystone/KeyStoneWind.java:736: if (GmDisplayManager.getCurrentKeystoneMode() != 1) {
   com/xgimi/device/GmDisplayManager.java:5039: return Integer.valueOf(DisplayManager.INSTANCE.getCurrentKeystoneMode());
   com/xgimi/gmuiapi/manager/DisplayManager.java:2472: return Integer.valueOf(DisplayManager.INSTANCE.getManager().getCurrentKeystoneMode());
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:189: if (getDisplayManager().getCurrentKeystoneMode() == 6) {
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:197: if (getDisplayManager().getCurrentKeystoneMode() == 6) {
== getPictureMode SystemUI_relea (14)
   com/xgimi/video/MstPictureManager.java:117: return mService.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:669: Integer pictureMode = MstPictureManager.INSTANCE.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:2339: Integer pictureMode = MstPictureManager.INSTANCE.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:2359: Integer pictureMode = MstPictureManager.INSTANCE.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:2365: Integer pictureMode2 = MstPictureManager.INSTANCE.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:2370: Integer pictureMode3 = MstPictureManager.INSTANCE.getPictureMode();
== getPictureMode XgimiHome_rele (14)
   com/xgimi/settingsdk/displaystrategy/BrightnessManager.java:65: int pictureMode = GmDisplayManager.getPictureMode();
   com/xgimi/settingsdk/displaystrategy/PictureManager.java:72: int pictureMode = GmDisplayManager.getPictureMode();
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:494: if (GmDisplayManager.getPictureMode() == 4) {
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:505: if (GmDisplayManager.getPictureMode() == 4) {
   com/xgimi/video/MstPictureManager.java:165: return mService.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:4779: Integer pictureMode = MstPictureManager.INSTANCE.getPictureMode();
== getPictureMode minitvfactory_ (13)
   com/xgimi/video/MstPictureManager.java:117: return mService.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:670: Integer pictureMode = MstPictureManager.INSTANCE.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:2340: Integer pictureMode = MstPictureManager.INSTANCE.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:2360: Integer pictureMode = MstPictureManager.INSTANCE.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:2366: Integer pictureMode2 = MstPictureManager.INSTANCE.getPictureMode();
   com/xgimi/device/GmDisplayManager.java:2371: Integer pictureMode3 = MstPictureManager.INSTANCE.getPictureMode();
== getPictureMode release_838_ne (69)
   com/xgimi/picture/PictureManager.java:96: return GmDisplayManager.getDispColorSpace(i, GmDisplayManager.getPictureMode());
   com/xgimi/picture/PictureViewModel.java:1830: getPictureRepository().setImagePerformanceVisible(GmDisplayManager.getPictureMode() == 5);
   com/xgimi/picture/PictureViewModel.java:1888: getPictureRepository().updateDataReporter(GmDisplayManager.getPictureMode(), items);
   com/xgimi/picture/PictureViewModel.java:1939: getPictureRepository().sendImageModeDataReporter(GmDisplayManager.getPictureMode(), items);
   com/xgimi/picture/PictureViewModel.java:3479: CustomizeDataManager.INSTANCE.deleteCustomizeDataByValue(GmDisplayManager.getPictureMode());
   com/xgimi/picture/PictureViewModel.java:3480: GmDisplayManager.DeleteCustomImageMode(GmDisplayManager.getPictureMode());
== manualFocus SystemUI_relea (9)
   com/xgimi/device/GmDeviceManager.java:1364: ProjectorFocusManager.INSTANCE.manualFocus(enDir);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:338: focusManager2.manualFocus(b.byteValue());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:361: manager2.manualFocus(b.byteValue());
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:80: getFocusManager().manualFocus(enDir);
   com/xgimi/systemui/focus/FocusAdjust.java:343: GmDeviceManager.manualFocus(Byte.valueOf("17"));
   com/xgimi/systemui/focus/FocusAdjust.java:601: ProjectorFocusManager.getInstance().manualFocus(Byte.parseByte(str));
== manualFocus XgimiHome_rele (5)
   com/xgimi/device/GmDeviceManager.java:5183: ProjectorFocusManager.INSTANCE.manualFocus(this.$enDir);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1476: projectorFocusManagerAccess$getFocusManager.manualFocus(b8.byteValue());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1506: baseProjectorFocusManagerAccess$getManager.manualFocus(b8.byteValue());
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:230: getFocusManager().manualFocus(b8);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:900: mVarC.f13346a.manualFocus(b8);
== manualFocus minitvfactory_ (13)
   com/xgimi/burnin/ui/activity/BurnBaseActivity.java:442: ProjectorFocusApi.manualFocus((byte) 7);
   com/xgimi/burnin/ui/activity/FirstStageV3Activity.java:290: ProjectorFocusApi.manualFocus((byte) 7);
   com/xgimi/burnin/ui/activity/FirstStageActivity.java:409: ProjectorFocusApi.manualFocus((byte) 7);
   com/xgimi/burnin/ui/activity/FirstStageActivity.java:949: ProjectorFocusApi.manualFocus((byte) 14);
   com/xgimi/device/GmDeviceManager.java:1369: ProjectorFocusManager.INSTANCE.manualFocus(enDir);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:337: focusManager2.manualFocus(b.byteValue());
== manualFocus release_838_ne (14)
   com/xgimi/focusing/FocusViewModel.java:164: GmDeviceManager.manualFocus((byte) 1);
   com/xgimi/focusing/FocusViewModel.java:197: FocusFunctionManager.INSTANCE.manualFocus();
   com/xgimi/focusing/FocusViewModel.java:205: GmDeviceManager.manualFocus((byte) 0);
   com/xgimi/focusing/FocusViewModel.java:259: GmDeviceManager.manualFocus(Boxing.boxByte((byte) 2));
   com/xgimi/focusing/FocusViewModel.java:301: GmDeviceManager.manualFocus(Boxing.boxByte((byte) 2));
   com/xgimi/calibration/HallCalibrationManualDialog.java:206: ProjectorFocusManager.getInstance().manualFocus(i != 22 ? (byte) 1 : (byte) 0);
== newAutoKst SystemUI_relea (11)
   com/xgimi/device/GmDisplayManager.java:2188: return Byte.valueOf(ProjectorFocusManager.INSTANCE.newAutoKst(mode));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:688: return getManager().newAutoKst(mode);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:102: return getFocusManager().newAutoKst(mode);
   com/xgimi/systemui/focus/keystonewind/AuxiliaryCurtainAlignDialog.java:230: GmDisplayManager.newAutoKst(12);
   com/xgimi/systemui/focus/curtain/CurtainAdaptManager.java:60: GmDisplayManager.newAutoKst(11);
   com/xgimi/systemui/focus/tiltshift/ak/AkShiftHelper.java:341: GmDisplayManager.newAutoKst(25);
== newAutoKst XgimiHome_rele (4)
   com/xgimi/device/GmDisplayManager.java:10046: Byte bValueOf = Byte.valueOf(ProjectorFocusManager.INSTANCE.newAutoKst(this.$mode));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:399: byte bNewAutoKst = getManager().newAutoKst(i5);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:238: byte bNewAutoKst = getFocusManager().newAutoKst(i5);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:910: return mVarC.f13346a.newAutoKst(i5);
== newAutoKst minitvfactory_ (5)
   com/xgimi/device/GmDisplayManager.java:2189: return Byte.valueOf(ProjectorFocusManager.INSTANCE.newAutoKst(mode));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:687: return getManager().newAutoKst(mode);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:102: return getFocusManager().newAutoKst(mode);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:266: return ProjectorFocusManagerService.getInstance().newAutoKst(i);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:571: return this.service.newAutoKst(i);
== newAutoKst release_838_ne (12)
   com/xgimi/keystone/KeyStoneManager.java:138: GmDisplayManager.newAutoKst(9);
   com/xgimi/keystone/KeyStoneViewModel.java:425: GmDisplayManager.newAutoKst(13);
   com/xgimi/keystone/KeyStoneViewModel.java:712: GmDisplayManager.newAutoKst(24);
   com/xgimi/keystone/view/AuxiliaryCurtainAlignDialog.java:135: GmDisplayManager.newAutoKst(12);
   com/xgimi/keystone/view/keystone/KeyStoneWind.java:1564: GmDisplayManager.newAutoKst(10);
   com/xgimi/device/GmDisplayManager.java:2203: return Byte.valueOf(ProjectorFocusManager.INSTANCE.newAutoKst(mode));
== powerOnAFEnable SystemUI_relea (6)
   com/xgimi/device/GmDeviceManager.java:852: ProjectorFocusManager.INSTANCE.powerOnAFEnable(Boolean.valueOf(value == 1));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:384: manager2.powerOnAFEnable(bool.booleanValue());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:407: manager2.powerOnAFEnable(bool.booleanValue());
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:39: getFocusManager().powerOnAFEnable(isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:38: ProjectorFocusManagerService.getInstance().powerOnAFEnable(z);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:92: this.service.powerOnAFEnable(z);
== powerOnAFEnable XgimiHome_rele (5)
   com/xgimi/device/GmDeviceManager.java:5510: ProjectorFocusManager.INSTANCE.powerOnAFEnable(Boolean.valueOf(this.$value == 1));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1536: baseProjectorFocusManagerAccess$getManager.powerOnAFEnable(bool.booleanValue());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1566: baseProjectorFocusManagerAccess$getManager.powerOnAFEnable(bool.booleanValue());
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:247: getFocusManager().powerOnAFEnable(z5);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:971: mVarC.f13346a.powerOnAFEnable(z5);
== powerOnAFEnable minitvfactory_ (9)
   com/xgimi/burnin/ui/activity/BurnResultsActivity.java:258: ProjectorFocusApi.powerOnAFEnable(false);
   com/xgimi/device/GmDeviceManager.java:857: ProjectorFocusManager.INSTANCE.powerOnAFEnable(Boolean.valueOf(value == 1));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:383: manager2.powerOnAFEnable(bool.booleanValue());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:406: manager2.powerOnAFEnable(bool.booleanValue());
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:39: getFocusManager().powerOnAFEnable(isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:38: ProjectorFocusManagerService.getInstance().powerOnAFEnable(z);
== powerOnAFEnable release_838_ne (6)
   com/xgimi/device/GmDeviceManager.java:856: ProjectorFocusManager.INSTANCE.powerOnAFEnable(Boolean.valueOf(value == 1));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:383: manager2.powerOnAFEnable(bool.booleanValue());
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:406: manager2.powerOnAFEnable(bool.booleanValue());
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:38: getFocusManager().powerOnAFEnable(isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:38: ProjectorFocusManagerService.getInstance().powerOnAFEnable(z);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:92: this.service.powerOnAFEnable(z);
== savePowerOnAKFlag SystemUI_relea (5)
   com/xgimi/device/GmDisplayManager.java:1931: GmFactoryManager.INSTANCE.savePowerOnAKFlag(isOnOff);
   com/xgimi/gmuiapi/manager/GmFactoryManager.java:365: GmFactoryManager.INSTANCE.getManager().savePowerOnAKFlag(isOnOff);
   com/xgimi/gmuiapi/api3/V3GmFactoryManager.java:59: getFactoryManager().savePowerOnAKFlag(isOnOff);
   com/xgimi/middle/GmFactoryManagerWrapper.java:226: GmFactoryManagerService.getInstance().savePowerOnAKFlag(z);
   xgimi/hardware/gmpf/GmFactoryManagerService.java:508: this.service.savePowerOnAKFlag(z);
== savePowerOnAKFlag XgimiHome_rele (5)
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:548: GmDisplayManager.savePowerOnAKFlag(z5);
   com/xgimi/device/GmDisplayManager.java:10192: GmFactoryManager.INSTANCE.savePowerOnAKFlag(this.$isOnOff);
   com/xgimi/gmuiapi/manager/GmFactoryManager.java:1733: GmFactoryManager.access$getManager(GmFactoryManager.INSTANCE).savePowerOnAKFlag(this.$isOnOff);
   com/xgimi/gmuiapi/api3/V3GmFactoryManager.java:416: getFactoryManager().savePowerOnAKFlag(z5);
   com/xgimi/middle/GmFactoryManagerWrapper.java:1136: eVarC.f13330a.savePowerOnAKFlag(z5);
== savePowerOnAKFlag minitvfactory_ (8)
   com/xgimi/burnin/ui/activity/BurnResultsActivity.java:260: GmFactoryApi.savePowerOnAKFlag(false);
   com/xgimi/device/GmDisplayManager.java:1932: GmFactoryManager.INSTANCE.savePowerOnAKFlag(isOnOff);
   com/xgimi/gmuiapi/manager/GmFactoryManager.java:365: GmFactoryManager.INSTANCE.getManager().savePowerOnAKFlag(isOnOff);
   com/xgimi/gmuiapi/api3/V3GmFactoryManager.java:60: getFactoryManager().savePowerOnAKFlag(isOnOff);
   com/xgimi/middle/GmFactoryManagerWrapper.java:226: GmFactoryManagerService.getInstance().savePowerOnAKFlag(z);
   com/xgimi/middleapi/GmFactoryApi.java:960: GmFactoryManager.getInstance().savePowerOnAKFlag(z);
== savePowerOnAKFlag release_838_ne (8)
   com/xgimi/keystone/KeyStoneManager.java:343: GmDisplayManager.savePowerOnAKFlag(z);
   com/xgimi/device/GmDisplayManager.java:1946: GmFactoryManager.INSTANCE.savePowerOnAKFlag(isOnOff);
   com/xgimi/gmuiapi/manager/GmFactoryManager.java:366: GmFactoryManager.INSTANCE.getManager().savePowerOnAKFlag(isOnOff);
   com/xgimi/gmuiapi/api3/V3GmFactoryManager.java:61: getFactoryManager().savePowerOnAKFlag(isOnOff);
   com/xgimi/middle/GmFactoryManagerWrapper.java:226: GmFactoryManagerService.getInstance().savePowerOnAKFlag(z);
   com/xgimi/signal/screenoverlay/OverlayUtils.java:119: GmDisplayManager.savePowerOnAKFlag(false);
== sendFocusStatus SystemUI_relea (9)
   com/xgimi/device/GmDeviceManager.java:1388: projectorFocusManager.sendFocusStatus(b);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:430: manager2.sendFocusStatus(b);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:453: manager2.sendFocusStatus(b);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:87: focusManager.sendFocusStatus(val.byteValue());
   com/xgimi/systemui/focus/FocusUIV2.java:439: ProjectorFocusManager.getInstance().sendFocusStatus((byte) 17);
   com/xgimi/systemui/focus/FocusUIV2.java:500: GmDeviceManager.sendFocusStatus(Byte.valueOf(MusicPlayerManager.EN_LED_AIRPLAY_DISCON));
== sendFocusStatus XgimiHome_rele (5)
   com/xgimi/device/GmDeviceManager.java:5402: projectorFocusManager.sendFocusStatus(b8);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1596: baseProjectorFocusManagerAccess$getManager.sendFocusStatus(b8);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1626: baseProjectorFocusManagerAccess$getManager.sendFocusStatus(b8);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:258: focusManager.sendFocusStatus(b8.byteValue());
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:1020: mVarC.f13346a.sendFocusStatus(b8);
== sendFocusStatus minitvfactory_ (6)
   com/xgimi/device/GmDeviceManager.java:1393: projectorFocusManager.sendFocusStatus(b);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:429: manager2.sendFocusStatus(b);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:452: manager2.sendFocusStatus(b);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:87: focusManager.sendFocusStatus(val.byteValue());
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:54: ProjectorFocusManagerService.getInstance().sendFocusStatus(b);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:130: this.service.sendFocusStatus(b);
== sendFocusStatus release_838_ne (8)
   com/xgimi/calibration/HallCalibrationDialog.java:88: GmDeviceManager.sendFocusStatus((byte) 46);
   com/xgimi/calibration/HallCalibrationFocusingDialog.java:149: GmDeviceManager.sendFocusStatus(Boxing.boxByte((byte) 46));
   com/xgimi/device/GmDeviceManager.java:1392: projectorFocusManager.sendFocusStatus(b);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:429: manager2.sendFocusStatus(b);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:452: manager2.sendFocusStatus(b);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:86: focusManager.sendFocusStatus(val.byteValue());
== setAccTriggerAK SystemUI_relea (5)
   com/xgimi/device/GmDisplayManager.java:1880: MotionDetectionManager.INSTANCE.setAccTriggerAK(trigger);
   com/xgimi/gmuiapi/manager/MotionDetectionManager.java:75: MotionDetectionManager.INSTANCE.getManager().setAccTriggerAK(trigger);
   com/xgimi/gmuiapi/api3/V3MotionDetectionManager.java:37: V3MotionDetectionManager.this.getMotionDetctionManager().setAccTriggerAK(trigger);
   com/xgimi/middle/MotionDetectionManagerWrapper.java:62: return MotionDetectionManagerService.getInstance().setAccTriggerAK(z);
   xgimi/hardware/gmpf/MotionDetectionManagerService.java:111: return this.service.setAccTriggerAK(z);
== setAccTriggerAK XgimiHome_rele (5)
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:543: GmDisplayManager.setAccTriggerAK(z5);
   com/xgimi/device/GmDisplayManager.java:10441: MotionDetectionManager.INSTANCE.setAccTriggerAK(this.$trigger);
   com/xgimi/gmuiapi/manager/MotionDetectionManager.java:347: MotionDetectionManager.access$getManager(MotionDetectionManager.INSTANCE).setAccTriggerAK(this.$trigger);
   com/xgimi/gmuiapi/api3/V3MotionDetectionManager.java:279: V3MotionDetectionManager.access$getMotionDetctionManager(V3MotionDetectionManager.this).setAccTriggerAK(this.$trigger);
   com/xgimi/middle/MotionDetectionManagerWrapper.java:354: return jVarC.f13337a.setAccTriggerAK(z5);
== setAccTriggerAK minitvfactory_ (8)
   com/xgimi/burnin/ui/activity/BurnResultsActivity.java:259: MotionDetectionApi.setAccTriggerAK(false);
   com/xgimi/device/GmDisplayManager.java:1881: MotionDetectionManager.INSTANCE.setAccTriggerAK(trigger);
   com/xgimi/gmuiapi/manager/MotionDetectionManager.java:74: MotionDetectionManager.INSTANCE.getManager().setAccTriggerAK(trigger);
   com/xgimi/gmuiapi/api3/V3MotionDetectionManager.java:36: V3MotionDetectionManager.this.getMotionDetctionManager().setAccTriggerAK(trigger);
   com/xgimi/middle/MotionDetectionManagerWrapper.java:62: return MotionDetectionManagerService.getInstance().setAccTriggerAK(z);
   com/xgimi/middleapi/MotionDetectionApi.java:316: MotionDetectionManager.getInstance().setAccTriggerAK(z);
== setAccTriggerAK release_838_ne (26)
   com/xgimi/focusing/FocusFunctionManager.java:94: GmDisplayManager.setAccTriggerAK(mTriggerAK);
   com/xgimi/focusing/FocusFunctionManager.java:101: GmDisplayManager.setAccTriggerAK(mTriggerAK);
   com/xgimi/focusing/FocusFunctionManager.java:107: GmDisplayManager.setAccTriggerAK(mTriggerAK);
   com/xgimi/focusing/FocusFunctionManager.java:128: GmDisplayManager.setAccTriggerAK(mTriggerAK);
   com/xgimi/calibration/CalibrationManager.java:109: GmDisplayManager.setAccTriggerAK(CalibrationManager.mTriggerAK);
   com/xgimi/calibration/CalibrationManager.java:119: GmDisplayManager.setAccTriggerAK(false);
== setAngTriggerAF SystemUI_relea (5)
   com/xgimi/device/GmDeviceManager.java:888: ProjectorFocusManager.INSTANCE.setAngTriggerAF(value);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:473: ProjectorFocusManager.INSTANCE.getManager().setAngTriggerAF(isOnOff);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:59: getMotionDetctionManager().setAngTriggerAF(isOnOff);
   com/xgimi/middle/MotionDetectionManagerWrapper.java:54: return MotionDetectionManagerService.getInstance().setAngTriggerAF(z);
   xgimi/hardware/gmpf/MotionDetectionManagerService.java:91: return this.service.setAngTriggerAF(z);
== setAngTriggerAF XgimiHome_rele (4)
   com/xgimi/device/GmDeviceManager.java:5987: ProjectorFocusManager.INSTANCE.setAngTriggerAF(this.$value);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1653: ProjectorFocusManager.access$getManager(ProjectorFocusManager.INSTANCE).setAngTriggerAF(this.$isOnOff);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:266: getMotionDetctionManager().setAngTriggerAF(z5);
   com/xgimi/middle/MotionDetectionManagerWrapper.java:365: return jVarC.f13337a.setAngTriggerAF(z5);
== setAngTriggerAF minitvfactory_ (8)
   com/xgimi/burnin/ui/activity/BurnResultsActivity.java:257: MotionDetectionApi.setAngTriggerAF(false);
   com/xgimi/device/GmDeviceManager.java:893: ProjectorFocusManager.INSTANCE.setAngTriggerAF(value);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:472: ProjectorFocusManager.INSTANCE.getManager().setAngTriggerAF(isOnOff);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:59: getMotionDetctionManager().setAngTriggerAF(isOnOff);
   com/xgimi/middle/MotionDetectionManagerWrapper.java:54: return MotionDetectionManagerService.getInstance().setAngTriggerAF(z);
   com/xgimi/middleapi/MotionDetectionApi.java:214: MotionDetectionManager.getInstance().setAngTriggerAF(mode);
== setAngTriggerAF release_838_ne (5)
   com/xgimi/device/GmDeviceManager.java:892: ProjectorFocusManager.INSTANCE.setAngTriggerAF(value);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:472: ProjectorFocusManager.INSTANCE.getManager().setAngTriggerAF(isOnOff);
   com/xgimi/gmuiapi/api3/V3ProjectorFocusManager.java:58: getMotionDetctionManager().setAngTriggerAF(isOnOff);
   com/xgimi/middle/MotionDetectionManagerWrapper.java:54: return MotionDetectionManagerService.getInstance().setAngTriggerAF(z);
   xgimi/hardware/gmpf/MotionDetectionManagerService.java:91: return this.service.setAngTriggerAF(z);
== setCorrectKeystoneFullPiont SystemUI_relea (2)
   com/xgimi/middle/DisplayManagerWrapper.java:176: DisplayManagerService.getInstance().setCorrectKeystoneFullPiont(kstPoint.kstMode, kstPoint);
   xgimi/hardware/gmpf/DisplayManagerService.java:221: return this.service.setCorrectKeystoneFullPiont(b, kstPoint);
== setCorrectKeystoneFullPiont XgimiHome_rele (1)
   com/xgimi/middle/DisplayManagerWrapper.java:2687: cVarC.f13324a.setCorrectKeystoneFullPiont(b8, kstPoint);
== setCorrectKeystoneFullPiont minitvfactory_ (2)
   com/xgimi/middle/DisplayManagerWrapper.java:176: DisplayManagerService.getInstance().setCorrectKeystoneFullPiont(kstPoint.kstMode, kstPoint);
   xgimi/hardware/gmpf/DisplayManagerService.java:221: return this.service.setCorrectKeystoneFullPiont(b, kstPoint);
== setCorrectKeystoneFullPiont release_838_ne (2)
   com/xgimi/middle/DisplayManagerWrapper.java:176: DisplayManagerService.getInstance().setCorrectKeystoneFullPiont(kstPoint.kstMode, kstPoint);
   xgimi/hardware/gmpf/DisplayManagerService.java:221: return this.service.setCorrectKeystoneFullPiont(b, kstPoint);
== setKstPrepare SystemUI_relea (5)
   com/xgimi/device/GmDisplayManager.java:7712: return Boolean.valueOf(DisplayManager.INSTANCE.setKstPrepare(bPrepare, reserve));
   com/xgimi/gmuiapi/manager/DisplayManager.java:3235: return Boolean.valueOf(DisplayManager.INSTANCE.getManager().setKstPrepare(bPrepare, reserve));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:801: return getDisplayManager().setKstPrepare(bPrepare, reserve);
   com/xgimi/middle/DisplayManagerWrapper.java:1191: return DisplayManagerService.getInstance().setKstPrepare(z, i);
   xgimi/hardware/gmpf/DisplayManagerService.java:1958: return this.service.setKstPrepare(z, i);
== setKstPrepare XgimiHome_rele (4)
   com/xgimi/device/GmDisplayManager.java:12568: Boolean boolValueOf = Boolean.valueOf(DisplayManager.INSTANCE.setKstPrepare(this.$bPrepare, this.$reserve));
   com/xgimi/gmuiapi/manager/DisplayManager.java:5931: Boolean boolValueOf = Boolean.valueOf(DisplayManager.access$getManager(DisplayManager.INSTANCE).setKstPrepare(this.$bPrepare, this.$reserve));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1108: boolean kstPrepare = getDisplayManager().setKstPrepare(z5, i5);
   com/xgimi/middle/DisplayManagerWrapper.java:1989: return cVarC.f13324a.setKstPrepare(z5, i5);
== setKstPrepare minitvfactory_ (7)
   com/xgimi/device/GmDisplayManager.java:7713: return Boolean.valueOf(DisplayManager.INSTANCE.setKstPrepare(bPrepare, reserve));
   com/xgimi/gmuiapi/manager/DisplayManager.java:3252: return Boolean.valueOf(DisplayManager.INSTANCE.getManager().setKstPrepare(bPrepare, reserve));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:802: return getDisplayManager().setKstPrepare(bPrepare, reserve);
   com/xgimi/middle/DisplayManagerWrapper.java:1191: return DisplayManagerService.getInstance().setKstPrepare(z, i);
   com/xgimi/minitvfactory/ui/activity/ImageCheckActivity.java:116: GmDisplayManager.setKstPrepare(false, 0);
   com/xgimi/minitvfactory/ui/activity/ImageCheckActivity.java:232: GmDisplayManager.setKstPrepare(false, 0);
== setKstPrepare release_838_ne (6)
   com/xgimi/device/GmDisplayManager.java:7767: return Boolean.valueOf(DisplayManager.INSTANCE.setKstPrepare(bPrepare, reserve));
   com/xgimi/gmuiapi/manager/DisplayManager.java:3253: return Boolean.valueOf(DisplayManager.INSTANCE.getManager().setKstPrepare(bPrepare, reserve));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:804: return getDisplayManager().setKstPrepare(bPrepare, reserve);
   com/xgimi/middleware/MiddleWareApi.java:212: return Boolean.valueOf(GmDisplayManager.setKstPrepare(isEnter, type));
   com/xgimi/middle/DisplayManagerWrapper.java:1191: return DisplayManagerService.getInstance().setKstPrepare(z, i);
   xgimi/hardware/gmpf/DisplayManagerService.java:1958: return this.service.setKstPrepare(z, i);
== setOpticalZoomStep SystemUI_relea (4)
   com/xgimi/device/GmDisplayManager.java:3467: return Integer.valueOf(DisplayManager.INSTANCE.setOpticalZoomStep(step));
   com/xgimi/gmuiapi/manager/DisplayManager.java:1974: return Integer.valueOf(DisplayManager.INSTANCE.getDisplayManager().setOpticalZoomStep(step));
   com/xgimi/middle/DisplayManagerWrapper.java:651: return DisplayManagerService.getInstance().setOpticalZoomStep(i);
   xgimi/hardware/gmpf/DisplayManagerService.java:1020: return this.service.setOpticalZoomStep(i);
== setOpticalZoomStep XgimiHome_rele (5)
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:632: return GmDisplayManager.setOpticalZoomStep(curOpticalCurZoomStep);
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:640: return GmDisplayManager.setOpticalZoomStep(curOpticalCurZoomStep2);
   com/xgimi/device/GmDisplayManager.java:12901: Integer numValueOf = Integer.valueOf(DisplayManager.INSTANCE.setOpticalZoomStep(this.$step));
   com/xgimi/gmuiapi/manager/DisplayManager.java:6076: Integer numValueOf = Integer.valueOf(DisplayManager.access$getDisplayManager(DisplayManager.INSTANCE).setOpticalZoomStep(this.$step));
   com/xgimi/middle/DisplayManagerWrapper.java:2077: return cVarC.f13324a.setOpticalZoomStep(i5);
== setOpticalZoomStep minitvfactory_ (5)
   com/xgimi/device/GmDisplayManager.java:3468: return Integer.valueOf(DisplayManager.INSTANCE.setOpticalZoomStep(step));
   com/xgimi/gmuiapi/manager/DisplayManager.java:1991: return Integer.valueOf(DisplayManager.INSTANCE.getDisplayManager().setOpticalZoomStep(step));
   com/xgimi/middle/DisplayManagerWrapper.java:651: return DisplayManagerService.getInstance().setOpticalZoomStep(i);
   com/xgimi/middleapi/DisplayApi.java:1230: opticalZoomStep = GmDisplayManager.setOpticalZoomStep(step);
   xgimi/hardware/gmpf/DisplayManagerService.java:1020: return this.service.setOpticalZoomStep(i);
== setOpticalZoomStep release_838_ne (9)
   com/xgimi/keystone/KeyStoneManager.java:193: return GmDisplayManager.setOpticalZoomStep(getCurOpticalCurZoomStep() + 1);
   com/xgimi/keystone/KeyStoneManager.java:196: return GmDisplayManager.setOpticalZoomStep(getCurOpticalCurZoomStep() - 1);
   com/xgimi/device/GmDisplayManager.java:3482: return Integer.valueOf(DisplayManager.INSTANCE.setOpticalZoomStep(step));
   com/xgimi/gmuiapi/manager/DisplayManager.java:1992: return Integer.valueOf(DisplayManager.INSTANCE.getDisplayManager().setOpticalZoomStep(step));
   com/xgimi/middle/DisplayManagerWrapper.java:651: return DisplayManagerService.getInstance().setOpticalZoomStep(i);
   com/xgimi/more/batchsetting/BatchApiManager.java:352: BatchApiUtils.INSTANCE.setOpticalZoomStep(curOpticalCurZoomStep);
== setPictureMode SystemUI_relea (12)
   com/xgimi/video/MstPictureManager.java:127: return mService.setPictureMode(i);
   com/xgimi/device/GmDisplayManager.java:693: return MstPictureManager.INSTANCE.setPictureMode(Integer.valueOf(mode));
   com/xgimi/device/GmDisplayManager.java:713: return MstPictureManager.INSTANCE.setPictureMode(Integer.valueOf(mode), callback);
   com/xgimi/device/GmDisplayManager.java:5691: MstPictureManager.INSTANCE.setPictureMode(MstPictureManager.INSTANCE.getPictureMode());
   com/xgimi/gmuiapi/manager/MstPictureManager.java:300: pictureMode2 = mstPictureManager.setPictureMode(num.intValue());
   com/xgimi/gmuiapi/manager/MstPictureManager.java:685: return Boolean.valueOf(mstPictureManager.setPictureMode(num.intValue()));
== setPictureMode XgimiHome_rele (11)
   com/xgimi/video/MstPictureManager.java:284: return mService.setPictureMode(i5);
   com/xgimi/device/GmDisplayManager.java:13038: Boolean pictureMode = MstPictureManager.INSTANCE.setPictureMode(Integer.valueOf(this.$mode));
   com/xgimi/device/GmDisplayManager.java:13067: Boolean pictureMode = MstPictureManager.INSTANCE.setPictureMode(Integer.valueOf(this.$mode), this.$callback);
   com/xgimi/device/GmDisplayManager.java:15125: mstPictureManager.setPictureMode(mstPictureManager.getPictureMode());
   com/xgimi/gmuiapi/manager/MstPictureManager.java:1285: Boolean boolValueOf = Boolean.valueOf(mstPictureManagerAccess$getMstPictureManager.setPictureMode(num.intValue()));
   com/xgimi/gmuiapi/manager/MstPictureManager.java:1648: pictureMode = mstPictureManagerAccess$getMstPictureManager.setPictureMode(num.intValue());
== setPictureMode minitvfactory_ (17)
   com/xgimi/video/MstPictureManager.java:127: return mService.setPictureMode(i);
   com/xgimi/device/GmDisplayManager.java:694: return MstPictureManager.INSTANCE.setPictureMode(Integer.valueOf(mode));
   com/xgimi/device/GmDisplayManager.java:714: return MstPictureManager.INSTANCE.setPictureMode(Integer.valueOf(mode), callback);
   com/xgimi/device/GmDisplayManager.java:5692: MstPictureManager.INSTANCE.setPictureMode(MstPictureManager.INSTANCE.getPictureMode());
   com/xgimi/gmuiapi/manager/MstPictureManager.java:301: pictureMode2 = mstPictureManager.setPictureMode(num.intValue());
   com/xgimi/gmuiapi/manager/MstPictureManager.java:686: return Boolean.valueOf(mstPictureManager.setPictureMode(num.intValue()));
== setPictureMode release_838_ne (25)
   com/xgimi/picture/PictureViewModel.java:2581: GmDisplayManager.setPictureMode(4);
   com/xgimi/picture/PictureViewModel.java:3067: GmDisplayManager.setPictureMode(iCreateNewCustomImageMode);
   com/xgimi/picture/PictureViewModel.java:4354: GmDisplayManager.setPictureMode(intSafely$default2);
   com/xgimi/picture/PictureRepository.java:2134: GmDisplayManager.setPictureMode(this.$mode);
   com/xgimi/picture/PictureRepository.java:3098: GmDisplayManager.setPictureMode(10);
   com/xgimi/video/MstPictureManager.java:127: return mService.setPictureMode(i);
== setPowerOnAFUseDistance SystemUI_relea (4)
   com/xgimi/device/GmDisplayManager.java:5355: return Boolean.valueOf(ProjectorFocusManager.INSTANCE.setPowerOnAFUseDistance(onOff));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:792: return Boolean.valueOf(ProjectorFocusManager.INSTANCE.getFocusManager().setPowerOnAFUseDistance(onOff));
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:420: return ProjectorFocusManagerService.getInstance().setPowerOnAFUseDistance(z);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:887: return this.service.setPowerOnAFUseDistance(z);
== setPowerOnAFUseDistance XgimiHome_rele (3)
   com/xgimi/device/GmDisplayManager.java:13148: Boolean boolValueOf = Boolean.valueOf(ProjectorFocusManager.INSTANCE.setPowerOnAFUseDistance(this.$onOff));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1698: Boolean boolValueOf = Boolean.valueOf(ProjectorFocusManager.access$getFocusManager(ProjectorFocusManager.INSTANCE).setPowerOnAFUseDistance(this.$onOff));
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:1146: return mVarC.f13346a.setPowerOnAFUseDistance(z5);
== setPowerOnAFUseDistance minitvfactory_ (4)
   com/xgimi/device/GmDisplayManager.java:5356: return Boolean.valueOf(ProjectorFocusManager.INSTANCE.setPowerOnAFUseDistance(onOff));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:791: return Boolean.valueOf(ProjectorFocusManager.INSTANCE.getFocusManager().setPowerOnAFUseDistance(onOff));
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:420: return ProjectorFocusManagerService.getInstance().setPowerOnAFUseDistance(z);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:887: return this.service.setPowerOnAFUseDistance(z);
== setPowerOnAFUseDistance release_838_ne (5)
   com/xgimi/focusing/FocusRepository.java:179: GmDisplayManager.setPowerOnAFUseDistance(z);
   com/xgimi/device/GmDisplayManager.java:5410: return Boolean.valueOf(ProjectorFocusManager.INSTANCE.setPowerOnAFUseDistance(onOff));
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:791: return Boolean.valueOf(ProjectorFocusManager.INSTANCE.getFocusManager().setPowerOnAFUseDistance(onOff));
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:420: return ProjectorFocusManagerService.getInstance().setPowerOnAFUseDistance(z);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:887: return this.service.setPowerOnAFUseDistance(z);
== setProjCurtainAdp SystemUI_relea (5)
   com/xgimi/device/GmDeviceManager.java:1306: ProjectorFocusManager.INSTANCE.setProjCurtainAdp(isOnOff);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:555: ProjectorFocusManager.INSTANCE.getFocusManager().setProjCurtainAdp(isOnOff);
   com/xgimi/systemui/focus/ui/EnvDonePageCell$setOption$1$onServiceConnected$1.java:58: GmDeviceManager.setProjCurtainAdp(Boolean.parseBoolean(data.getValue()));
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:194: ProjectorFocusManagerService.getInstance().setProjCurtainAdp(z);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:397: this.service.setProjCurtainAdp(z);
== setProjCurtainAdp XgimiHome_rele (4)
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:588: GmDeviceManager.setProjCurtainAdp(z5);
   com/xgimi/device/GmDeviceManager.java:6553: ProjectorFocusManager.INSTANCE.setProjCurtainAdp(this.$isOnOff);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1734: ProjectorFocusManager.access$getFocusManager(ProjectorFocusManager.INSTANCE).setProjCurtainAdp(this.$isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:1168: mVarC.f13346a.setProjCurtainAdp(z5);
== setProjCurtainAdp minitvfactory_ (4)
   com/xgimi/device/GmDeviceManager.java:1311: ProjectorFocusManager.INSTANCE.setProjCurtainAdp(isOnOff);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:554: ProjectorFocusManager.INSTANCE.getFocusManager().setProjCurtainAdp(isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:194: ProjectorFocusManagerService.getInstance().setProjCurtainAdp(z);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:397: this.service.setProjCurtainAdp(z);
== setProjCurtainAdp release_838_ne (7)
   com/xgimi/keystone/KeyStoneManager.java:363: GmDeviceManager.setProjCurtainAdp(isCurtainAdp);
   com/xgimi/keystone/KeyStoneRepository.java:512: KeyStoneManager.INSTANCE.setProjCurtainAdp(z);
   com/xgimi/keystone/KeyStoneRepository.java:633: KeyStoneManager.INSTANCE.setProjCurtainAdp(z);
   com/xgimi/device/GmDeviceManager.java:1310: ProjectorFocusManager.INSTANCE.setProjCurtainAdp(isOnOff);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:554: ProjectorFocusManager.INSTANCE.getFocusManager().setProjCurtainAdp(isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:194: ProjectorFocusManagerService.getInstance().setProjCurtainAdp(z);
== setProjScreenAdp SystemUI_relea (5)
   com/xgimi/device/GmDeviceManager.java:1270: ProjectorFocusManager.INSTANCE.setProjScreenAdp(isOnOff);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:521: ProjectorFocusManager.INSTANCE.getFocusManager().setProjScreenAdp(isOnOff);
   com/xgimi/systemui/focus/ui/EnvDonePageCell$setOption$1$onServiceConnected$1.java:55: GmDeviceManager.setProjScreenAdp(Boolean.parseBoolean(data.getValue()));
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:186: ProjectorFocusManagerService.getInstance().setProjScreenAdp(z);
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:378: this.service.setProjScreenAdp(z);
== setProjScreenAdp XgimiHome_rele (4)
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:593: GmDeviceManager.setProjScreenAdp(z5);
   com/xgimi/device/GmDeviceManager.java:6580: ProjectorFocusManager.INSTANCE.setProjScreenAdp(this.$isOnOff);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:1761: ProjectorFocusManager.access$getFocusManager(ProjectorFocusManager.INSTANCE).setProjScreenAdp(this.$isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:1178: mVarC.f13346a.setProjScreenAdp(z5);
== setProjScreenAdp minitvfactory_ (6)
   com/xgimi/device/GmDeviceManager.java:1275: ProjectorFocusManager.INSTANCE.setProjScreenAdp(isOnOff);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:520: ProjectorFocusManager.INSTANCE.getFocusManager().setProjScreenAdp(isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:186: ProjectorFocusManagerService.getInstance().setProjScreenAdp(z);
   com/xgimi/middleapi/ProjectorFocusApi.java:225: ProjectorFocusManager.getInstance().setProjScreenAdp(z);
   com/xgimi/minitvfactory/auto/AutoModel.java:2818: ProjectorFocusApi.setProjScreenAdp(Boolean.parseBoolean(autoCommonBean.getValue()));
   xgimi/hardware/gmpf/ProjectorFocusManagerService.java:378: this.service.setProjScreenAdp(z);
== setProjScreenAdp release_838_ne (7)
   com/xgimi/keystone/KeyStoneManager.java:355: GmDeviceManager.setProjScreenAdp(isScreenAdp);
   com/xgimi/keystone/KeyStoneRepository.java:503: KeyStoneManager.INSTANCE.setProjScreenAdp(z);
   com/xgimi/keystone/KeyStoneRepository.java:608: KeyStoneManager.INSTANCE.setProjScreenAdp(z);
   com/xgimi/device/GmDeviceManager.java:1274: ProjectorFocusManager.INSTANCE.setProjScreenAdp(isOnOff);
   com/xgimi/gmuiapi/manager/ProjectorFocusManager.java:520: ProjectorFocusManager.INSTANCE.getFocusManager().setProjScreenAdp(isOnOff);
   com/xgimi/middle/ProjectorFocusManagerWrapper.java:186: ProjectorFocusManagerService.getInstance().setProjScreenAdp(z);
== setProjectorPutMode SystemUI_relea (6)
   com/xgimi/device/GmDisplayManager.java:2557: DisplayManager.INSTANCE.setProjectorPutMode(mode);
   com/xgimi/gmuiapi/manager/DisplayManager.java:549: DisplayManager.INSTANCE.getManager().setProjectorPutMode(enMode);
   com/xgimi/gmuiapi/manager/DisplayManager.java:586: DisplayManager.INSTANCE.getManager().setProjectorPutMode(enMode);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:79: getDisplayManager().setProjectorPutMode(enMode);
   com/xgimi/middle/DisplayManagerWrapper.java:76: DisplayManagerService.getInstance().setProjectorPutMode(b);
   xgimi/hardware/gmpf/DisplayManagerService.java:95: this.service.setProjectorPutMode(b);
== setProjectorPutMode XgimiHome_rele (5)
   com/xgimi/device/GmDisplayManager.java:13211: DisplayManager.INSTANCE.setProjectorPutMode(this.$mode);
   com/xgimi/gmuiapi/manager/DisplayManager.java:6166: DisplayManager.access$getManager(DisplayManager.INSTANCE).setProjectorPutMode(this.$enMode);
   com/xgimi/gmuiapi/manager/DisplayManager.java:6193: DisplayManager.access$getManager(DisplayManager.INSTANCE).setProjectorPutMode(this.$enMode);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1160: getDisplayManager().setProjectorPutMode(b8);
   com/xgimi/middle/DisplayManagerWrapper.java:2109: cVarC.f13324a.setProjectorPutMode(b8);
== setProjectorPutMode minitvfactory_ (11)
   com/xgimi/device/GmDisplayManager.java:2558: DisplayManager.INSTANCE.setProjectorPutMode(mode);
   com/xgimi/gmuiapi/manager/DisplayManager.java:553: DisplayManager.INSTANCE.getManager().setProjectorPutMode(enMode);
   com/xgimi/gmuiapi/manager/DisplayManager.java:590: DisplayManager.INSTANCE.getManager().setProjectorPutMode(enMode);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:80: getDisplayManager().setProjectorPutMode(enMode);
   com/xgimi/middle/DisplayManagerWrapper.java:76: DisplayManagerService.getInstance().setProjectorPutMode(b);
   com/xgimi/middleapi/DisplayApi.java:244: DisplayManager.getInstance().setProjectorPutMode(Byte.parseByte(mode));
== setProjectorPutMode release_838_ne (15)
   com/xgimi/pojector/ProjectionRepository.java:295: ProjectionManager.INSTANCE.setProjectorPutMode(mode);
   com/xgimi/pojector/ProjectionRepository.java:312: ProjectionManager.INSTANCE.setProjectorPutMode(b);
   com/xgimi/pojector/ProjectionRepository.java:326: ProjectionManager.INSTANCE.setProjectorPutMode(b);
   com/xgimi/pojector/ProjectionViewModel.java:256: getProjectionRepository().setProjectorPutMode((byte) 1);
   com/xgimi/pojector/ProjectionViewModel.java:319: getProjectionRepository().setProjectorPutMode((byte) 2);
   com/xgimi/pojector/ProjectionViewModel.java:324: getProjectionRepository().setProjectorPutMode((byte) 0);
== setSoundeffect SystemUI_relea (4)
   com/xgimi/gmuiapi/manager/GmAudioManager.java:261: GmAudioManager.INSTANCE.getAudioManager().setSoundeffect(mode);
   com/xgimi/gmuiapi/manager/GmAudioManager.java:281: GmAudioManager.INSTANCE.getAudioManager().setSoundeffect(mode);
   com/xgimi/middle/GmAudioManagerWrapper.java:36: GmAudioManagerService.getInstance().setSoundeffect(b);
   xgimi/hardware/gmpf/GmAudioManagerService.java:106: this.service.setSoundeffect(b);
== setSoundeffect XgimiHome_rele (3)
   com/xgimi/gmuiapi/manager/GmAudioManager.java:1847: GmAudioManager.access$getAudioManager(GmAudioManager.INSTANCE).setSoundeffect(this.$mode);
   com/xgimi/gmuiapi/manager/GmAudioManager.java:1874: GmAudioManager.access$getAudioManager(GmAudioManager.INSTANCE).setSoundeffect(this.$mode);
   com/xgimi/middle/GmAudioManagerWrapper.java:845: dVarC.f13327a.setSoundeffect(b8);
== setSoundeffect minitvfactory_ (4)
   com/xgimi/gmuiapi/manager/GmAudioManager.java:260: GmAudioManager.INSTANCE.getAudioManager().setSoundeffect(mode);
   com/xgimi/gmuiapi/manager/GmAudioManager.java:280: GmAudioManager.INSTANCE.getAudioManager().setSoundeffect(mode);
   com/xgimi/middle/GmAudioManagerWrapper.java:36: GmAudioManagerService.getInstance().setSoundeffect(b);
   xgimi/hardware/gmpf/GmAudioManagerService.java:106: this.service.setSoundeffect(b);
== setSoundeffect release_838_ne (4)
   com/xgimi/gmuiapi/manager/GmAudioManager.java:263: GmAudioManager.INSTANCE.getAudioManager().setSoundeffect(mode);
   com/xgimi/gmuiapi/manager/GmAudioManager.java:283: GmAudioManager.INSTANCE.getAudioManager().setSoundeffect(mode);
   com/xgimi/middle/GmAudioManagerWrapper.java:36: GmAudioManagerService.getInstance().setSoundeffect(b);
   xgimi/hardware/gmpf/GmAudioManagerService.java:106: this.service.setSoundeffect(b);
== switchKeystoneMode SystemUI_relea (8)
   com/xgimi/device/GmDisplayManager.java:5004: return Boolean.valueOf(DisplayManager.INSTANCE.switchKeystoneMode(kstMode, reserved));
   com/xgimi/gmuiapi/manager/DisplayManager.java:2474: return Boolean.valueOf(DisplayManager.INSTANCE.getManager().switchKeystoneMode(kstMode, reserved));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:187: getDisplayManager().switchKeystoneMode(0, 0);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:195: getDisplayManager().switchKeystoneMode(0, 0);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:495: return getDisplayManager().switchKeystoneMode(kstMode, reserved);
   com/xgimi/systemui/utils/SystemUtils.java:354: GmDisplayManager.switchKeystoneMode(0, -1);
== switchKeystoneMode XgimiHome_rele (6)
   com/xgimi/device/GmDisplayManager.java:14121: Boolean boolValueOf = Boolean.valueOf(DisplayManager.INSTANCE.switchKeystoneMode(this.$kstMode, this.$reserved));
   com/xgimi/gmuiapi/manager/DisplayManager.java:6666: Boolean boolValueOf = Boolean.valueOf(DisplayManager.access$getManager(DisplayManager.INSTANCE).switchKeystoneMode(this.$kstMode, this.$reserved));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1077: getDisplayManager().switchKeystoneMode(0, 0);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1323: boolean zSwitchKeystoneMode = getDisplayManager().switchKeystoneMode(i5, i8);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:1427: getDisplayManager().switchKeystoneMode(0, 0);
   com/xgimi/middle/DisplayManagerWrapper.java:2412: return cVarC.f13324a.switchKeystoneMode(i5, i8);
== switchKeystoneMode minitvfactory_ (7)
   com/xgimi/device/GmDisplayManager.java:5005: return Boolean.valueOf(DisplayManager.INSTANCE.switchKeystoneMode(kstMode, reserved));
   com/xgimi/gmuiapi/manager/DisplayManager.java:2491: return Boolean.valueOf(DisplayManager.INSTANCE.getManager().switchKeystoneMode(kstMode, reserved));
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:188: getDisplayManager().switchKeystoneMode(0, 0);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:196: getDisplayManager().switchKeystoneMode(0, 0);
   com/xgimi/gmuiapi/api3/V3DisplayManager.java:496: return getDisplayManager().switchKeystoneMode(kstMode, reserved);
   com/xgimi/middle/DisplayManagerWrapper.java:611: return DisplayManagerService.getInstance().switchKeystoneMode(i, i2);
== switchKeystoneMode release_838_ne (13)
   com/xgimi/keystone/view/keystone/KeyStoneWind.java:917: if (GmDisplayManager.switchKeystoneMode(1, -1)) {
   com/xgimi/keystone/view/keystone/KeyStoneWind.java:1533: GmDisplayManager.switchKeystoneMode(0, -1);
   com/xgimi/keystone/view/keystone/KeyStoneWind.java:1540: boolean zSwitchKeystoneMode = GmDisplayManager.switchKeystoneMode(1, -1);
   com/xgimi/keystone/view/keystone/KeyStoneWind.java:1541: boolean zSwitchKeystoneMode2 = GmDisplayManager.switchKeystoneMode(1, -1);
   com/xgimi/keystone/morepointkeystone/MorePointUtils.java:41: GmDisplayManager.switchKeystoneMode(7, -1);
   com/xgimi/keystone/morepointkeystone/MorePointUtils.java:60: GmDisplayManager.switchKeystoneMode(0, -1);
== zoomControl SystemUI_relea (6)
   com/xgimi/device/GmDisplayManager.java:3172: return Integer.valueOf(DisplayManager.INSTANCE.zoomControl(zoomMode, zoomSteps));
   com/xgimi/gmuiapi/manager/DisplayManager.java:1754: return Integer.valueOf(DisplayManager.INSTANCE.getDisplayManager().zoomControl(zoomMode, zoomSteps));
   com/xgimi/systemui/focus/keystonewind/AuxiliaryCurtainAlignDialog.java:305: GmDisplayManager.zoomControl(5, 10);
   com/xgimi/systemui/utils/KeyStoneManager.java:71: GmDisplayManager.zoomControl(5, 10);
   com/xgimi/middle/DisplayManagerWrapper.java:507: return DisplayManagerService.getInstance().zoomControl(i, i2);
   xgimi/hardware/gmpf/DisplayManagerService.java:705: return this.service.zoomControl(i, i2);
== zoomControl XgimiHome_rele (4)
   com/xgimi/settingsdk/displaystrategy/KeyStoneManager.java:636: return GmDisplayManager.zoomControl(i5, i8);
   com/xgimi/device/GmDisplayManager.java:14386: Integer numValueOf = Integer.valueOf(DisplayManager.INSTANCE.zoomControl(this.$zoomMode, this.$zoomSteps));
   com/xgimi/gmuiapi/manager/DisplayManager.java:6815: Integer numValueOf = Integer.valueOf(DisplayManager.access$getDisplayManager(DisplayManager.INSTANCE).zoomControl(this.$zoomMode, this.$zoomSteps));
   com/xgimi/middle/DisplayManagerWrapper.java:2515: return cVarC.f13324a.zoomControl(i5, i8);
== zoomControl minitvfactory_ (11)
   com/xgimi/device/GmDisplayManager.java:3173: return Integer.valueOf(DisplayManager.INSTANCE.zoomControl(zoomMode, zoomSteps));
   com/xgimi/gmuiapi/manager/DisplayManager.java:1771: return Integer.valueOf(DisplayManager.INSTANCE.getDisplayManager().zoomControl(zoomMode, zoomSteps));
   com/xgimi/middle/DisplayManagerWrapper.java:507: return DisplayManagerService.getInstance().zoomControl(i, i2);
   com/xgimi/middleapi/DisplayApi.java:710: iZoomControl = DisplayManager.getInstance().zoomControl(zoomMode, zoomSteps);
   com/xgimi/minitvfactory/util/FactoryUtils.java:516: DisplayApi.zoomControl(5, 0);
   com/xgimi/minitvfactory/ui/activity/LensShiftCorrectHallActivity.java:59: GmDisplayManager.zoomControl(16, 0);
== zoomControl release_838_ne (31)
   com/xgimi/calibration/CalibrationManager.java:77: GmDisplayManager.zoomControl(5, 10);
   com/xgimi/keystone/KeyStoneManager.java:198: return GmDisplayManager.zoomControl(zoomMode, zoomSteps);
   com/xgimi/keystone/KeyStoneManager.java:267: KeyStoneManager.INSTANCE.zoomControl(5, 10);
   com/xgimi/keystone/KeyStoneManager.java:332: KeyStoneManager.INSTANCE.zoomControl(5, 10);
   com/xgimi/keystone/KeyStoneViewModel.java:871: KeyStoneManager.INSTANCE.zoomControl(3, 101);
   com/xgimi/keystone/KeyStoneViewModel.java:873: KeyStoneManager.INSTANCE.zoomControl(3, 104);
```

## t14 picture page and the angle dialog calls (2026-11-08)
- X50 settings tree: newsettings resource `raw/settings_tree_product_dianapro` (file `res/k0.json` of
  `release_838_newsettings.apk`, found with `aapt2 dump resources`; node data in `raw/picture` = `res/T3.json`). Picture
  mode node `image_mode_4_state` children with their `other` ids and `support` source types: image_mode_ai 10, _sport 9,
  _soft_athena 24, picture_filmmaker_mode 27, image_mode_isf_light 38, _isf_night 39, _prof_color_accuracy 29 (SDR /
  HDR / HDR10), _office_platform_9270 25, _performance 5 (all SDR ... Filmmaker), image_mode_imax_enhanced (IMAX,
  HDR10), image_mode_hdr_vivid 26 (HdrVivid), picture dolby bright / soft (DOLBY). No standard / movie node on the X50.
  `image_mode_setting` lists image_mode_custom_dynamic_contrast, image_ai_contrast and image_dynamic_black_bit_enhancement
  without a `support` filter; there is no contrast-enhancement node in the X50 tree.
- Picture mode: `com/xgimi/picture/PictureRepository.java` setImageMode 2077-2099 (skip when other == getPictureMode,
  performance via choiceImageModeWithWarning), choiceLedMode 2141 -> C29121 GmDisplayManager.setPictureMode ->
  MstPictureManager (XgimiService) -> `XgimiService .../wrapper/mtk/MtkVideo.java` 17-25 GmTvManager.getPictureMode(0) /
  setPictureMode(0, i). PictureViewModel.dealGameMode 4048 does nothing for these actions (game / casting only).
- Dynamic black: PictureViewModel 3558-3562 (case 1574764486: setDynamicBlackType(isCheck ? 1 : 0), getDynamicBlackType ==
  1), PictureRepository.refreshBlackEnhance 1994-2001, PictureFactory 4651 (switch), 1759-1784 (level row hidden while 174
  reads 0). Mutex strings (`office_mode_not_support_black_enhancement`, `mutex_dynamic_black_bit_enhancement`) have no
  Java caller: vendor events and toasts.
- Dynamic contrast: PictureViewModel 3552-3555 setTvDynamicContrastEnable(0, isCheck), getTvDynamicContrastEnable(0).
- Contrast enhancement: PictureRepository.enableContrastEnhance 3301-3307 (167 read, 166(enable ? 1 : 0)),
  refreshDisplayContrast 2027-2028 (on when 167 == 1); DisplayMutexManager.contrastEnhance 455.
- Angle dialog calls: `com/xgimi/common/dialog/AngleMonitorDialog.java` keepHeartBeat 86-89 (setMotionACalEnable(true),
  again after 5000 ms), show() 249-257 (getAccTriggerAK; if on temporarySwitchTrigger(2, false); heartbeat; refresh at
  500 ms), dismiss() 259-266 (setMotionACalEnable(false); temporarySwitchTrigger(2, true) if paused). Wire formats checked
  in `com.xgimi.middle` IDisplayManager / IGmTvManager / IMotionDetectionManager proxies (ints, booleans, boolean / int
  replies, no out parameters).

## t15: stock checks for the t14 live findings (2026-11-09, decompile on xgimi-builder `~/devices/g0111_mt9681/work/dec`)
- Manual focus from the menu (`release_838_newsettings/.../com/xgimi/focusing/FocusViewModel.java` 196-201): MANUAL_FOCUS =
  `FocusFunctionManager.manualFocus()` (startService `com.xgimi.systemui.action.AF_AK`, type 1, from newsettings) and
  `SettingActionContract.closeSettingMain` ONLY when `DeviceManager.isCommercial()`. So on a consumer X50 the menu stays
  open. SystemUI `com/xgimi/systemui/focus/FocusAdjust.java` showFocusAdjust 333-371: from "com.android.newsettings" the
  arrows get rightMargin / leftMargin 600 px (180 rotated), i.e. the overlay is drawn clear of the open side panel.
  Lumen t15: menu stays, the pill is centred left of the 400 dp panel.
- Four-corner keystone (`com/xgimi/keystone/view/keystone/KeyStoneWind.java`): showKstWind 467-470 closes the menu only
  on commercial units (`justCloseSettingMenu` under `isCommercial`), then adds its own window over the open menu;
  onKey 1094-1131: BACK (4) on ACTION_UP = hideKstWind (987-1013: removes the window, broadcast `keystore_wind_close`
  with no receiver in newsettings or SystemUI), so the menu is in front again. Lumen t15: BACK reopens the menu on the
  corner row when the corner screen came from it.
- Picture mode (`com/xgimi/picture/PictureRepository.java`): setImageMode 2077-2099 (skip when equal; performance via the
  warning dialog), choiceLedMode 2141-2160: setPictureMode on an IO coroutine, then one getPictureMode read for the UI and
  `MiddlewareManager.syncPictureMode` (RS232 console command 3 only). The list is a sub-page of node image_mode_4_state.
- Dynamic black per mode: no stock code stores or re-sends the DLBE switch per picture mode. refreshBlackEnhance
  1994-2001 re-reads 174 (callers PictureViewModel 1850 / 4503 after a colour-space change, BrightFactory 1332); the
  vendor keeps the state per source / mode (libxgimi string "currPicMode=%d, targetPicMode=%d; check and %s
  dynamicblack state"; t14 live: after 10 -> 9 the vendor log showed m_bEnable:0). Lumen: re-read after a change, send
  nothing.
- Contrast enhancement: the X50 tree has no node (above), so it is never shown on stock X50; Lumen t15 drops the row and
  166 / 167.

## t16: ALLM row (t15 live BUG A; 2026-11-09, decompile on xgimi-builder `~/devices/g0111_mt9681/work/dec`)
- t15 live: the Lumen switch row "Auto game mode" sent `setAllmEnable(1)` on every press and always read Off: it
  computed the new value from TV 130, and the vendor answers 130 false for any game type but auto (libxgimi
  `[%s:%d] gameType:%d(not auto), return false`).
- Stock is not a switch. The X50 tree node `game_enhance_mode_entrance` (k0.json, picture page) opens a page with
  `game_enhance_mode` (Off 0 / On 1 / Auto 2 = `gmuiapi/manager/DisplayManager.getGameMode`: 148 type 1 -> 2, state 0 ->
  1, else 0), the title `game_enhance_mode_switch_when` and two radio cells (T3.json): `game_enhance_mode_switch_game_content`
  ("when game content is detected (ALLM)", other 0) and `game_enhance_mode_switch_game_device` ("when a game device is
  detected", other 1).
- Visibility (`com/xgimi/newsettings/ui/PictureFactory.java` bindPicture, case ACTION_GAME_ENHANCE_WHEN_GAME_CONTENT, the
  title and both cells share it): `cell.setVisibility(8)` when getGameEnhanceMode is 0 or 1, visible otherwise, i.e. only
  in Auto. Checked cell = the one whose `other` equals getGameEnhanceSwitchTiming.
- Read (`com/xgimi/picture/PictureViewModel.java` refreshGameModeState, and after every game mode step in postArrowAbout):
  switch timing = `!GmDisplayManager.getAllmEnableState() ? 1 : 0` (TV 130 getAllmEnableState).
- Write (`com/xgimi/picture/PictureRepository.java` setGameEnhanceTiming 3415-3417, called with 0 by
  ACTION_GAME_ENHANCE_WHEN_GAME_CONTENT and 1 by ACTION_GAME_ENHANCE_WHEN_GAME_DEVICE in PictureViewModel):
  `GmDisplayManager.setAllmEnable(type == 0)` = TV 129, then the timing is posted. No other stock writer: XRMService,
  SystemUI, XgimiTvInputService and xhplayer only read 130 / 131 (xhplayer GameMode, InputActivity, GameModeService
  decide the auto switch with getAllmEnableState + getAllmStatus).
- Vendor store: libxgimi `Msrv_DataBase_Manage::saveAllmEnableState(bool, EN_XGIMI_INPUT_SOURCE)`, Env key
  `allm_enable_state` per source. Stock default `mnt/vendor/xgimiconfig/G0111/env/CustomerEnvTbl.xml` line 87:
  `DEFAULT="1:1:1:1" FACTORY_RESET="1:1:1:1" USER_RESET="1:1:1:1" OTA_RESET="@xgimi_skip"` (= ALLM, all sources). The
  user's xgimicri dumps: 11-01 `dump/xgimicri.img`, 11-08 13:35 `full_20261108_1335/xgimicri.img` (before any trial) and
  16:13 `cmp_1613/now_xgimicri.img` all read `allm_enable_state=1:1:1:1`. t15 sent only 129(1), so the unit still holds
  the stock value.
- Lumen t16: row ALLM is a CHOICE "Auto game mode: On ALLM signal / On game console" (texts = Z9xProjector
  panel_allm_signal / panel_allm_device), hidden until the game mode reads Auto (on open and after every game mode step;
  no HDMI or another mode hides it), a step (`HdmiSequences.allmStep`) re-checks HDMI + Auto, reads 130 and sends 129 with
  the OTHER value, then shows the read-back, so the stock default (ALLM) can always be chosen again.

## t17: parity fixes (gsi-research/X50_T16_PARITY.md B1-B4, B6, B7) and the t16 live bugs B / C (2026-11-09)
- Sound modes (B1). X50 tree `res/k0.json` (raw settings_tree_product_dianapro) node `sound_effect` = sound_effect_text,
  sound_mode_ai, sound_mode_music, sound_mode_movie (sub-page 3D surround, sky sound), sound_mode_karaoke,
  sound_mode_sports, sound_mode_standard. newsettings `sound/SoundEnum.java`: MODE_AUTO 3 (action sound.mode.auto, text
  sound_mode_ai), MUSIC 2, MOVIE 1, KARAOKE 4, SPORTS 12, NEWS 5, VOCAL 21, STANDARD 20, GAME 22. Lumen t17
  `Sequences.SOUND_MODES = {3, 2, 1, 4, 12, 20}` in tree order; 3 is named with Z9xProjector panel_sound_ai ("AI sound",
  18 locales). News / vocal / game are only named when the vendor reports them (an older build may have written them);
  a step from such a value goes to the first / last X50 mode. Gmpf refuses any other value before sending.
- Focus rows (B2). The tree's focus_tab = auto_focus, manual_focus, smart_auto_focus (+ remote guide). FocusRepository
  setSmartAutoFocus = setPowerOnAFUseDistance(z) (PF 85) + setDisplacementFocus(z) (MD 4). Lumen t17 has only the smart
  AF switch; powerOnAFEnable / isPowerOnAFEnabled (PF 4 / 5) and getAngTriggerAF (MD 5) left the allow list (67 rows);
  MD 4 is sent only inside smart AF (`Hal.moveAfSet`).
- Setup (B3). apps/Z9xSetup PictureStep shows the POWER_ON_AF switch only when toggles_get returns that name; the X50
  bridge returns MOVE_AK only and refuses toggle_set POWER_ON_AF, so the shared Setup (unchanged) hides the row on the
  X50. Smart AF was not put behind Setup's "auto focus at power on" label: it also switches AF-when-moved on.
- Key 64 (B4). Stock 3840.kl 64 MUSIC_SOUNDERMODE -> com.xgimi.soundermodeservice (NewSounderMode) = Bluetooth speaker
  mode, not ported. t17 KeyReceiver: a "Not available" notice, no vendor call (t16 stepped GA 6).
- Focus key over the manual focus overlay (B6). Stock SystemUI FocusAdjust hides on the focus key and consumes it. t17
  KeyReceiver: `FocusActivity.closeOnFocusKey()` closes a shown overlay and returns; only without an overlay the key is
  the auto focus (PF 8(44)). Not seen live yet.
- Home picture tile (B7): opens the menu on PICTURE_MODE (the tree's image page starts with image_mode_4_state).
- Logcat buffer (t16 live BUG B). The image's /system/bin/logd (A14 system/logging, LogSize.cpp
  GetBufferSizeFromProperties, b/196856709) reads persist.logd.size* / ro.logd.size* only when ro.hardware.type is
  "automotive" on a debuggable build (the image's logd carries those strings; X50 vendor/build.prop has no
  ro.hardware.type). Every SerializedLogBuffer::Init() (logd start, every --reinit) sets 256 KiB: that is the 256 KiB seen
  in t14 / t15 / t16, and why the vendor's 64 KiB (init.fusion.rc post-fs-data) never showed either. t17
  x50_netadb.rc: three one-shot `logcat -b <buf> -G <size>` services (user logd, group log = privileged for logd's
  setLogSize; no seclabel: plat_sepolicy.cil `typetransition init logcat_exec process logpersist` + logpersist connectto
  logd) started on sys.boot_completed=1; no setprop, no --reinit. verify_tree_x50.py checks the block, the logd gate
  strings, the policy rules and that no rc of the image restarts logd-reinit from a boot-completed trigger (init.rc
  starts it only at load_persist_props_action).
- Source picker (t16 live BUG C). The X50 image has no org.lumen.projector, so the SOURCE key always lands on
  org.lumen.tvinput InputPickerActivity (DeviceDefault dialog buttons: focus not visible). t17 builds org.lumen.tvinput
  from devices/g0111/app/X50TvInput.layers (apps/Z9xTvInput 1.0.8 = the pinned Z9X 1.0.9 APK's sources + the X50
  layer: the picker with TvFocus, a copy of the X50 menu's) as 109 / 1.0.8 on the g0111 platform key, in place of the
  base's Z9xTvInput.apk (compose_x50.sh, verify_tree_x50.py TVIN_APP). Z9X and HB images keep their APKs.
- B5 projection "Auto" (MD 8 true): done in t18 with the owner's OK, see the next section.

## t18: projection "Auto" (X50_T16_PARITY.md B5, owner OK 2026-11-09)

Stock evidence (newsettings release_838, decompiled; X50 tree res `k0.json` = raw settings_tree_product_dianapro, node
data `L3.json` of the same APK; feature.xml from `work/fs/mnt/vendor/xgimiconfig/G0111/feature/feature.xml`, sha256
651cd1e9...c167):

- Tree: `projection_settings` page = setting_common_back, `projection_settings.autoVerticalTurn_v5`,
  `projection_settings.dress`, `projection_settings.hoist`, `projection_settings.rearProject` (in that order). Nodes
  (L3.json): autoVerticalTurn_v5 text `auto_turn`, type 5 (radio), tips `auto_vertical_turn_tip`, action
  `projection.projection_settings.autoTurn`; dress / hoist type 5; rearProject type 2 (switch). No node key and no code
  gate hides the Auto radio (grep of newsettings for the action / node id: only OtherFactory, ProjectionViewModel,
  ProjectionRepository; HideFactory handles action "hide" only); X50 feature.xml has no auto-reverse key. Shown always.
- Radio click (CellFactory.buildPage2Item / buildSinglePageItemCell): type 5 = ChoiceCell (ItemsWidget case 5), the click
  sets `isCheck = !cell.getIsCheck()` and calls postAction.
- ProjectionViewModel.postAction: `autoTurn` -> ProjectionRepository.setAutoVerticalTurnV5(data.isCheck());
  `dress` -> setProjectorPutModeV5(10) (isCheck ignored); `hoist` -> the X50 rule (getHasNotSupportFunUnderHoisting true:
  unsupported_inversion_functions = 1_1_1_0_1_1 has a 0): put mode 1 / 3 -> V5(11) at once, else ExpandDialog
  dialog_title_switch_to_hoist / dialog_desc_switch_to_hoist (support_keystone_21_9 = 1), "change" = V5(11), then
  setAccTriggerAK(false) only if !getSupportTimelyAKUnderHoisting (first value 1: skipped), then
  setPushPullScreenOnOff(false); "cancel" = nothing.
- setAutoVerticalTurnV5(isTurn): ProjectionManager.setAutoVerticalTurn(isTurn) = GmDeviceManager.setAutoReverse ->
  gmuiapi MotionDetectionManager -> V3MotionDetectionManager.setAutoReverse = MotionDetectionManager 8; then only
  LiveData: isAutoTurn = isTurn; on: isDress = isHoist = false; off: isDress = put 0 / 2, isHoist = put 1 / 3 (DM 3
  reads). No put mode write, no read-back of 9.
- setProjectorPutModeV5(10 | 11): DM 3, b keeps front / rear, ProjectionManager.setAutoVerticalTurn(false) (MD 8 false),
  DM 2(b), isAutoTurn = false: this is how stock leaves Auto through the desk / ceiling radios.
- Checked radios (OtherFactory.bindProjection, ProjectionRepository): autoTurn <- isAutoTurn = getAutoVerticalTurn()
  (GmDeviceManager.isAutoReverse -> V3MotionDetectionManager.isAutoReverse -> getAutoReverse = MotionDetectionManager 9);
  dress <- !auto && put 0 / 2; hoist <- !auto && put 1 / 3. Under Auto both are unchecked: stock shows no orientation.
- Rear (setRearProjection): put mode only, MD 8 untouched, also under Auto.
- Not on this path: gmuiapi GmDisplayManager.setProjectorVerticalPutMode / getProjectorVerticalPutMode (0 auto, 1 desk,
  2 ceiling) has no newsettings caller for the X50 page.

Lumen t18 (apps/X50Projector): PLACEMENT is a LIST row; OK / RIGHT read MD 9 + DM 3 and open the radio page Auto / desk /
ceiling (Sequences.PLACEMENTS), the stock-checked radio marked (Sequences.shownPlacement). Auto = Sequences.autoTurn(
Sequences.autoTurnTarget(auto before)) = MD 8(true), or MD 8(false) when Auto was checked; then MD 9 read back up to 8
times 150 ms apart (Lumen only). Desk = Sequences.placement(false); ceiling = the put-mode rule above with the existing
confirm dialog; after a V5 the row shows desk / ceiling (Auto off, as stock posts it). The row value is "Auto" while the
flip is on (put_auto = Z9xProjector panel_proj_auto, 18 locales). Allow list 68 rows: MD 8 (false | true; true only from
the Auto radio, `Hal.autoReverseOn` called only in Sequences.autoTurn), MD 9 getAutoReverse read only. Not live-tested.

## t19: the open items of X50_T16_PARITY.md and the t13 / t14 live notes (2026-11-10, branch arch)

Stock sources: newsettings / SystemUI decompile (release_838), the X50 settings tree (`res/k0.json`, node data in the
other `res/*.json`), vendor `xgimi_action_config.xml`, `mnt/vendor/xgimiconfig/G0111/feature/feature.xml`; framework.jar
and services.jar decompiled on xgimi-builder (`/tmp/t19r`, jadx 1.5.6 CLI, `~/tools/jre`).

### Game speed (MISSING in t16, ported)
- Tree page `game_enhance_mode_entrance`: game_enhance_mode, switch_when + the two ALLM radios, then `game_mode_level_title`,
  `image_mode_game_basic` (action picture.image_mode.game.basic) and `image_mode_game_low_latency`
  (picture.image_mode.game.low_latency). Only the ALLM radios have a visibility rule (PictureFactory: hidden while the game
  mode reads 0 / 1); the level radios are always on the page.
- Shown: PictureViewModel.refreshImageGameMode / dealGameModeChange: getGameModeOption (DM 77) 2 or 3 = low latency, else
  basic.
- Basic: setGameModeOptionHrrNormal: nothing when 77 reads 0 / 1 ("no need to switch"); else isGameMode4 (feature
  display_gamemode_version = 3 on G0111) and support_hfr (true on G0111) and isHighFrameRate (77 reads 1 or 2) ? 76(1) : 76(0),
  then MiddlewareManager.syncSuperFrame(1).
- Low latency: PictureViewModel case PICTURE_IMAGE_MODE_GAME_LOW_LATENCY_ACTION: when 77 reads 2 / 3 or the keystone is
  at the full frame (isAccurateKeyStone = GmTrapzoidUtils.isTrapzoid: the four corners equal the panel) ->
  setGameModeOptionTopSpeed at once; else the dialog PictureFactory.gameDialog ("switch to top speed to reduce latency?
  1. the picture returns to its default shape 2. size, shape and position cannot be adjusted in top speed", Switch /
  Cancel; Cancel = ResetAkUtils.resetKeyStoneAk). setGameModeOptionTopSpeed: nothing when 77 reads 2 / 3; else
  switchGameModeSpeed: isHighFrameRate ? 76(2) : 76(3), then setIChipReset (DM 38(0), 84, 85(0, 0) if 6), then
  syncSuperFrame(2).
- Lumen t19: row GAME_SPEED (Standard / Top speed, Z9xProjector texts panel_game_speed, panel_speed_standard,
  panel_speed_top; dialog panel_speed_top + game_top_speed_confirm + panel_switch) after the ALLM row, HDMI only (TV 4).
  HdmiSequences.gameSpeedBasic / gameSpeedTop send exactly the stock 76 values and the corner reset; the dialog is shown
  every time before top speed (stock skips it when the corners are already at the full frame: Lumen has no allowed read of
  the panel size, so it asks; same end states). Not sent: syncSuperFrame (vendor middleware bookkeeping inside
  com.xgimi.middle, no gmpf call) and the Cancel path's resetKeyStoneAk (MD 15(0, true) / setAccTriggerAK: it undoes an AK
  pause Lumen never makes). No new allow-list code (76 / 77 / 38 / 84 / 85 were allowed; reasons extended).
- Not ported: stock blocks keystone / zoom / shift changes while top speed is on (cannotKeystoneUnderGameMode); Lumen's
  corner screen and AK row do not check 77 yet (the vendor's answer decides).

### Secondary power key (3840.kl scancode 185 SECONDARY_POWER): no stock handler
- framework.jar KeyEvent: KEYCODE_SECONDARY_POWER = 2130 (KEYCODE_MOVE_HOME = 122, KEYCODE_MUSIC_SOUNDERMODE = 2102).
- No `action-keys keycode="2130"` in vendor xgimi_action_config.xml; no reference to 2130 / SECONDARY_POWER in
  services.jar (3463 classes incl. PhoneWindowManager), SystemUI_release_838, XgimiService, XRMService, newsettings,
  misckey (the only `case 2130:` hits are an exoplayer bandwidth table and an XgimiHome country-code switch). Lumen leaves
  185 unmapped = the same result (nothing happens). EQUIVALENT, nothing to port.

### Gear key held (B9)
- The gear is KEYCODE_MOVE_HOME 122 in the stock framework; xgimi_action_config.xml has two short-press entries for 122
  (version 1 com.xgimi.settings.SETTINGS, version 2 com.android.newsettings.MISCKEY, pressBehaver 0) and no long-press
  entry (pressBehaver 1). So stock does nothing extra on a held gear except what the remote itself sends (the B970C hint
  long_press_reset says it sends MENU 127, which only the four-corner screen uses). Lumen: SETTINGS held >= 800 ms opens
  Android settings and MENU 127 outside the corner screen opens Lumen Home's app list: both EXTRA, kept (low risk, owner
  decides). Which code the remote sends on a hold is still to be captured live (`getevent -lt`).

### Manual focus step size (t13 live finding 2)
- SystemUI FocusAdjust.newVontrolAf (B970C is isNewRc): LEFT / RIGHT DOWN (every repeat) = manualFocus(1 / 0), UP =
  manualFocus(2); there is no timed step for remote keys (the 200 ms pulse, handler messages 3 / 4, comes only from
  onGenericMotion = a mouse wheel). Lumen FocusHold since t14: DOWN 1 / 0, UP 2 at once, 700 ms watchdog. A short press is
  the finest step, as on stock. Nothing to change.

### Audio format value 2 (t16 parity open point)
- Tree node voice_arc_format_auto_sound_v5 map {0 voice_arc_pcm_text, 1 voice_arc_passthrough_text, 2 voice_arc_raw_text};
  VoiceViewModel -> VoiceRepository.setAutoFormat(i) -> GmAudioManager.setAudioFormat: 0 PCM, 1 AUDIO_DATA_FORMAT_RAW,
  2 AUDIO_DATA_FORMAT_AUTO. Lumen sends spdif_mode 0 / 1 / 2 = pcm / bypass / auto: the same wire values; only the label of
  2 differs (stock resource voice_arc_raw_text, Lumen "Auto").

### Smart auto focus (tester question)
- FocusRepository.setSmartAutoFocus(z) = setPowerOnAFUseDistance(z) (PF 85: at power on, focus again only when the
  measured distance changed) then setDisplacementFocus(z) (MD 4: focus again after the projector was moved). Owner: no
  explanatory subtitle in the menu yet.
