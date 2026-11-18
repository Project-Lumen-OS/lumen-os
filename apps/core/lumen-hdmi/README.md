# lumen-hdmi

The HDMI link of every projector app, written once (Z9X, Z9X HB, X50 Ultra, Ultra Max, RS30 Ultra Max, Titan):

- `HdmiLink` (plain Java): getSignalStatus 3 = a stable picture, the no-picture clock, the game profile gate, the
  relink decision and the EDID flip to another stock version and back.
- `HdmiRelink` (Android): the cable replug made by the projector. While tvinput shows an HDMI port, the vendor signal
  status is read every 2 s; after 10 s without a picture while a CEC source behind the port has reported power on
  for 10 s too, one EDID flip (the other stock version, the user's again 2 s later), one per session, three per boot.
  The user's version is saved first and written back after every HAL connect.
- `EdidModes` (plain Java): the stock HDMI mode row of every model, Compatible (HDMI 1.4) and Enhanced (HDMI 2.0), and
  the vendor value of each: newsettings SignalEnum 1 / 2 minus the step gmuiapi GmTvManager takes, so 0 / 1 (3 for
  Enhanced on a model whose feature edid_2_version_val is 3, none so far). Enhanced shows only for its own value. Model
  data: that feature value. The vendor picks the game files (edid21, edid20 with 120 Hz, FreeSync) itself from the
  game mode, its speed, VRR and FreeSync: no row writes them (devices/g0111/research/X50_EDID_STOCK.md).
- `RelinkHost`: what a model supplies, data and plumbing only: its HAL thread, getSignalStatus / get / setEdidVersion
  with its stock values, its lamp-only standby, tvinput's shown ports. Z9X and HB: `source/GmpfRelinkHost` (IGmpf 592 /
  TV 106, gmpf rules' EDID values 0 / 1), X50 family: `X50RelinkHost` (TV 106, TV 39 / 40 with HdmiHal.EDID, 0 / 1).
- `HdmiAudioKeys` (plain Java): the audio HAL keys stock XRMService sends for the HDMI input from the GmTvManager
  events. Signal status 3 sends `xgimi_source_hdmi=1`, any other status `xgimi_source_hdmi=0` and 500 ms later
  `xgimi_source_hdmi_delay=0`, event 4 and a cable out of the current source send `xgimi_source_hdmi=0`
  (devices/horse_popular/research/HDMI_PARITY.md section 4).
- `HdmiAudio` (Android): sends those keys with AudioManager.setParameters. An app installs it with its HAL thread
  and hands it the vendor events. Installed by the Z9X app only (the stock rule was read for the Z9X only).

Sessions: Z9X and HB hear tvinput's HDMI_EVENT broadcast (`HdmiRelink.onSessionEvent`), the X50 family polls
tvinput's port provider in `HdmiSource` (`HdmiRelink.onShownPort`). The Z9X / HB GameProfile asks `HdmiLink.gameReady`.

Tests: `sh apps/core/lumen-hdmi/test/run.sh` (rules, EdidModes, HdmiAudioKeys, the relink and HdmiAudio compiled against the platform headers, the wiring of
every model), mutants: `python3 apps/core/lumen-hdmi/test/mutate.py`.
