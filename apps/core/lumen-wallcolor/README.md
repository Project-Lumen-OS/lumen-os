# lumen-wallcolor

The stock wall colour adaptation ("Адаптация к цвету стены", stock node `wall_color_calibration`) on every projector
whose stock has it: Z9X G0082, Z9X HB G0130 / G0148, X50 Ultra G0111, X50 Ultra Max G0100, RS30 Ultra Max G0155,
TITAN H0062 / H0063 (all with `support_wall_color_adjust=true`; the Play 6 has no such row). Owner decision 2026-11-19:
free, exactly like stock.

## How the stock does it

| step | stock | Lumen |
|---|---|---|
| menu | newsettings `brightnessWallColorCalibration`: a card with title, picture, text (`support_ambient_light` picks the variant), Start and Restore default | `WallColorWindows.showIntro`, the text variant from `model.conf` |
| start | `GmDisplayManager.setAEOnOff(false)`, then `startAutoColor()` | `WallColorFlow.start`: AE_OFF, then START (the AE answer is not judged, as stock) |
| measure | the vendor HAL (libxgimi `AC_Process` / `AC_FusionMode`) opens the camera itself through V4L2 (`camera/camera.ini` node, `xlab/AutoColor.ini` exposure and gains), starts `/vendor/bin/ac_server` (OpenCV + ncnn, shared memory `/data/vendor/xlab/ac.fifo`), captures the projected pattern, computes the wall's XYZ and the target colour point | nothing: all inside the vendor service the image already runs |
| pattern | focus event 11001 `{"cmd":"show",...,"trans":N}`: SystemUI `WallColorCore` shows a black full screen dialog, a disc growing from the centre (500 ms), then the loop (six swatches, four blinking white corner marks of the sampled area, the running pill); `trans` is its opacity; `close` fades it (500 ms) | `PatternView` draws the same geometry and timing (`WallColorPattern`), no XGIMI file shipped |
| result | `{"cmd":"result","status":true,"code":0}`: OverWindowV2, a bar with Use Matched Color / Use Original Color; focusing one applies it (`setAutoColorCalibration` = select the effect after AC, `setAutoColorDefault` = before AC), OK closes, BACK asks to save, HOME and screen on/off keep the matched colour | `WallColorFlow` + `WallColorWindows.showResult` / `showSaveAsk` |
| failure | `status:false` codes 1 camera, 2 init, 3 distance or angle, 4 ambient light changed, 5 custom colour mode, 6 performance mode, 7 brightness auto adjustment on, 8 3D, 12 giant screen, other = out of range; 100 closes everything | `WallColorMessage` + 18 locales |
| storage | the vendor keeps the matched colour point itself (libxgimi `ac_current_coordinate`) and re-applies it; Lumen writes nothing | |
| reset | Restore default on the card = `setAutoColorDefault()` | the card's second button |

The apply path is the vendor's own: the matched colour point goes to the display module (DLP light colour balance).
Lumen sends only the four calls of `WallColorStep`; the factory calibration (`startAutoColorCalibration`, its check,
its data) is never wired (`test/run.sh` checks every projector app and code table).

## Per model

| model | HAL calls (codes.tsv) | events | camera (vendor) | pattern |
|---|---|---|---|---|
| Z9X G0082 | IGmpf2 53, IGmpf 106, 99, 100 | IGmpf 306 focus listener (the app's) | CameraType 8, icSpring 32e6:9005 640x480, usb 1-3 | type 3 |
| HB G0130 / G0148 | DisplayManager 150, 108, 115, 114 | ProjectorFocusManager 1 (the app's) | CameraType 8, usb 1-2.1 | type 3 |
| X50, X50 Max, TITAN | DisplayManager 150, 108, 115, 114 | ProjectorFocusManager 1 setFocusCallback, sent before the first start | CameraType 5, 1280x720, usb 1-3 | type 3 |
| RS30 G0155 | same | same | CameraType 6, 1280x720, usb 1-3 | type 5 (stock falls back to the type 1 picture: white disc) |

Data: `devices/<c>/wallcolor.conf`, in the app as `assets/lumen_wallcolor/model.conf`.

## Use from an app

    WallColor.install(app, hal);              // App.onCreate, hal = the app's WallColorHal
    WallColor.onFocusEvent(type, value);      // from the app's focus event dispatch (true for 11001)
    WallColor.open(context);                  // the menu row (lumen-menu FeatureId.KEYSTONE_WALL_COLOR, a module screen)

Z9X and HB: `org.lumen.projector.GmpfWallColor` (ProjectorBase). X50 family: `X50WallColor`.

## Tests

`sh apps/core/lumen-wallcolor/test/run.sh`: the vendor's messages in their printf formats, the flow and its HAL order,
the pattern maths, every model file, texts in every locale, no vendor call inside the module, no factory calibration
call anywhere. There are no recorded camera frames: the measurement maths runs in the vendor (`ac_server`), so the
first real check is live on a unit.
