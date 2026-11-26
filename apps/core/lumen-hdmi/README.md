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
- `HdmiAudio` (Android): sends those keys with AudioManager.setParameters at the vendor event, as stock does inside its
  GmTvManager listener, on its own thread "lumen-hdmi-audio" in the vendor's order, never behind the model's HAL thread
  (report LR-5WQ7M9). An app installs it with a reader of getCurrentInputSource that works off its HAL thread (Z9X and
  HB: `Hal.readNow`, X50 family: `X50RelinkHost.audioSource`) and hands it the vendor events. Every family: the stock
  XRMService of the Z9X, HB, X50, RS30 and Titan sends the same keys (Play 6: Lumen has no GmTvManager listener there).

Sessions: Z9X and HB hear tvinput's HDMI_EVENT broadcast (`HdmiRelink.onSessionEvent`), the X50 family polls
tvinput's port provider in `HdmiSource` (`HdmiRelink.onShownPort`). The Z9X / HB GameProfile asks `HdmiLink.gameReady`.

Tests: `sh apps/core/lumen-hdmi/test/run.sh` (rules, EdidModes, HdmiAudioKeys, HdmiAudioReplayTest: HdmiAudio in virtual time over the android stubs of `test/sim`, a recorded Z9X session with slow HAL calls injected, the relink and HdmiAudio compiled against the platform headers, the wiring of
every model), mutants: `python3 apps/core/lumen-hdmi/test/mutate.py`.

## The projector follows the watched device (SourceOff, SourceFollow)

While tvinput shows an HDMI port (the same session events as the relink), `SourceFollow` reads the framework's CEC
power status cache every 5 s and `SourceOff` decides: a source behind that port (recorder, tuner, playback; never the
TV, an audio system or a switch) that was on or turning on during this viewing and then reports standby on two reads
in a row puts the projector to sleep with `GO_TO_SLEEP_REASON_HDMI`, the call an incoming `<Standby>` makes, so the
sleep path is the same. Only while `tv_send_standby_on_sleep` is on: XGIMI's "HDMI devices control the projector"
switch writes it together with the wake on every stock family, Lumen's "Turn off HDMI devices with the projector" row
writes the same setting. Why the cache and not the message: the owner's PS5 sends `<Inactive Source>` (0x9D), never
`<Standby>`, in rest mode and at power off; Android 14's TV does nothing with that message while the source's port is
the previous port, and apps never see it, while `<Active Source>` and `<Report Power Status>` (the TV asks every
source once a minute) land in the cache. Delay: 23 s (power off) and 43 s (rest mode) after its `<Inactive Source>`
in the live traces, i.e. the next minute poll plus two reads. Replays:
`test/source_off_traces.tsv` (PS5 rest, PS5 power off, Yandex Station `<Standby>` and `<Inactive Source>`, a soundbar
and a console behind one port, a source asleep when opened, another port).

## Before the sleep: the TV is the active source again (SleepCec, SleepSource)

Stock goes Home on every sleep (XgimiWindowManagerDomestic.reallyGoToSleep: launchHome, then goToSleep), so the HDMI
picture leaves first and the bus shows `<Active Source>` 0F:82:00:00 274 ms before the framework's ARC termination
and `<Standby>` (Slava's sniffer, `test/cec_stock_x50_sleep.txt`). Lumen slept with the picture up and that frame was
missing (`test/cec_x50_live_sleep.txt`). `SleepSource.goToSleep` is the one Android sleep of every Lumen path
(standby, power key, idle, CEC veto, the watched device turning off): with CEC on and an HDMI port on screen (or on
screen when the standby began, `standbyBegins`), `HdmiTvClient.selectDevice(ADDR_INTERNAL)` first (the framework
sends the frame only while a device is the active source), then goToSleep at once; the framework's standby frames
follow when the display is off. The X50 family presses the system power key for its standby: `tvTakesSource` before
the key. A sleep the framework starts itself (the remote's KEYCODE_POWER on the X50 family) is covered by LumenTvInput
(ExitSelect: the internal source at once when the session ends while Android is not interactive). Independent of the
standby switch, as stock. run.sh fails on any `goToSleep(SystemClock...)` outside SleepSource. Tests: `SleepCecTest`
(both traces, the AOSP standby model checked on the live trace).

## The <Inactive Source> rule (InactiveSleep), off on every family

Owner direction pending: the watched device says `<Inactive Source>` and is not back (`<Active Source>`, its `<Text
View On>` / `<Image View On>`) within 5 s, no key (a key sent to a device in the history, or a projector key),
standby switch on: the projector sleeps. Behind `Family.INACTIVE_SOURCE_SLEEP` (RelinkHost.inactiveSourceSleep),
false everywhere: with it off nothing is read. With it on, SourceFollow reads the CEC history of dumpsys hdmi_control
every 2.5 s while a port is shown and the switch is on (no HAL call, nothing sent); sleep 5 to 7.5 s after the
message. Replays: `InactiveSleepTest` on both live PS5 histories.

