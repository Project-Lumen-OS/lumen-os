# lumen-cec

The HDMI-CEC page of every Lumen projector, one implementation with one small driver per projector.

- `org.lumen.projector.cec.CecPage`: the page inside the shared menu (`settings.cec`). Rows in a fixed order: HDMI-CEC
  on, turn on with an HDMI device, night guard (with its notes), switch to a device when it turns on, turn devices off
  with the projector, soundbar, soundbar on with the projector, remote control of devices, tell the device when leaving
  HDMI, connected devices and the name on HDMI, HDMI input options. Every value is read back after each change.
- `org.lumen.projector.cec.CecSettings`: the single owners of the values (framework `HdmiControlManager`, the HDMI
  app's provider `content://org.lumen.tvinput.hdmistate`, the app's own prefs). `CecDevices`: the connected devices.
- `org.lumen.cec.CecDriver`: what differs per projector, data first: which rows its stock has (`CecRow`), the worker
  thread, how its two power switches are read and written (HDMI-CEC on, turn on with an HDMI device), the storm pause
  note and a hook after each read. No model check anywhere in the page.

Drivers:

| projector | driver | rows | power switches |
|---|---|---|---|
| Z9X, Z9X HB | `apps/LumenProjector/core/.../cec/CecPolicyDriver` (the HB gets it through its base) | all | framework only, the power policy `CecPolicy` (night guard, storm limit, PM51 wake source) behind them |
| X50 Ultra, X50 Ultra Max, RS30 Ultra Max | `apps/LumenProjector/vendor/xgimi/mt9681-aidl/.../X50Cec` | stock `signal_setting_control_cec`: HDMI-CEC on, turn on with an HDMI device, switch to a device, a note under each of the first two (stock tips), none greyed while HDMI-CEC is off | the stock calls of newsettings `SignalRepository.setCEC` / `setDeviceAutoStart` (framework settings and GmTvManager 52 setCecWakeUp, read 53) |

A projector app uses it with `module lumen-cec` in its recipe (after `module lumen-menu`) and returns
`CecPage.build(app, host, driver)` for `settings.cec` from its menu pages.

Defaults (stock on every model, owner 2026-11-15): one table for all images, `devices/_image/cec_defaults.tsv`, with
the stock evidence per model dump in `devices/_image/cec_stock.tsv`. No XGIMI stock sends `<Standby>` to the HDMI
devices when the projector sleeps, none wakes on `<Image View On>` (Play 6, an Android 11 stock, does), CEC is on.

- `tools/devices/cec_defaults.py write` puts the table into the framework RRO of every image
  (`res/values/lumen_cec.xml` of Z9xFrameworkKeysOverlay, HbFrameworkOverlay, Play6FrameworkOverlay, X50PowerOverlay)
  and `check` holds the RROs, the stock dumps and the recipes against it (`tools/devices/test/cec_defaults_test.sh`).
  A unit that never stored a value gets the stock value from the RRO, also after an update.
- Every recipe with this module ships the table as the asset `lumen_cec/defaults.tsv`. `CecStockDefaults.applyOnce`
  (Z9X / HB: CecPolicy's start, X50 family: the app start) runs the device's `reset` lines once per unit
  (`CecDefaults.VERSION`): such a switch goes back to stock unless the user switched its row in the Lumen menu
  (`CecPage` marks every row the user changes, since this version). Today only the HB wake switch: its old image had no
  CEC values (LineageOS: wake on) and its power policy stores that value itself, so a stored "on" is not known to be
  the user's. A stored "Turn off HDMI devices with the projector" is always the user's (nothing else writes it) and
  is kept.

Tests: `sh test/parity/run.sh` drives the page of every assembled projector app through fixed scenarios and compares
the transcript (rows, labels in every locale, values, every call with its thread) with `test/parity/golden/`. The Z9X
and HB goldens were recorded before the page moved here from the Z9X app. `sh test/defaults/run.sh`: the table, the
one-time step and its wiring. Mutants: `python3 test/parity/mutate.py`.
