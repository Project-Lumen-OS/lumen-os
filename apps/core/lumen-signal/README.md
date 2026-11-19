# lumen-signal

The signal panel of every Lumen projector (Z9X, Z9X HB, Play 6, X50 Ultra, Ultra Max, RS30 Ultra Max, Titan), built
once. Design: `gsi-research/SIGNAL_PANEL.md` variant A, approved by the owner on 2026-11-18.

## Behaviour

- One button, three states (`PanelToggle`): first press the small card in the top right corner (keys stay with the
  film or the console), second press the full panel on the right side (keys are the panel's: BACK closes, OK switches
  the option row), third press hides it.
- Hold OK on the small card (owner 2026-11-19, `OkHold`, `CardHold`): held for 600 ms it opens the full panel, which
  grows out of the card (`PanelMotion`, 280 ms, none when Android's animations are removed); OK held again or BACK
  folds it back into the card it came from (a popup card gets its 5 s again). Only while a card is on screen does the
  key filter run (lumen-remote `KeyHook` over KeyFilterService, the accessibility key filter of the Remote buttons);
  with no card nothing sits between the remote and the app. A short OK on the card goes on at once and unchanged: its
  DOWN is not held back until the release, its UP follows, each exactly once (the filter answers "not mine" at once,
  Android passes the same event on). A held OK's DOWN has reached the app too (nothing can know at DOWN that the press
  will be long); when the panel takes the keys Android sends the app that press's UP as cancelled. In the full panel OK
  is the panel's: short = its own OK, held = fold back. The first three cards show "Hold OK to expand", never again
  once the user held OK. The pinned card follows the same rule. Host test `test/OkHoldTest.java`.
- A developer feature (owner 2026-11-18): opened by the remote action "signal" of "Remote buttons"
  (`devices/_remotes/key_actions.tsv`, any key the user gives it) and by its one row on the For developers page,
  the last page of All settings (lumen-menu `FeatureId.SIGNAL_PANEL`, a module screen). No round button on the quick menu.
- "Show when the signal changes": off by default, switched in the full panel. When on, a stable new HDMI signal
  (vendor event 0 `status:3`) shows the card for 5 s, only while an HDMI input is on screen (never over Home or apps).
- "Keep the signal card on screen" (All settings, For developers, off by default, owner 2026-11-18 for people
  who film the screen): the small card stays over every HDMI input, through signal changes and input switches (no HDMI
  input for 3 s takes it away, the next one brings it back); the panel's key or the option closes it (`PanelToggle.pin`).
- Values change in place: the card window stays, only the changed line is redrawn.
- Sizes and rates read `3840×2160 @ 23.97 Hz` everywhere (card, input row, chip row), one text, never two glued views.
- Place (`SignalPlace`): the card 440 px wide at the right edge of the title safe area, below an app's header (top
  136 px, the Lumen Home header ends at 112); the full panel 560 px wide over the title safe height, its list scrolls
  with up and down. Small, calm type (a 100 inch picture reads it well).
- Colours: the shared Lumen 2.0 palette (lumen-ui `Palette`, the one theme switch `LumenTheme`), `SignalLook`.

## What it shows (a row only where the model reads it, `devices/<c>/signal.tsv`)

| row | Z9X | HB | X50, Max, RS30, Titan | Play 6 |
|---|---|---|---|---|
| input size @ rate, scan, HDMI or DVI | 662 | TV 38 | TV 122 | 662 |
| HDR kind: SDR, HDR10, HDR10+, HLG, Dolby Vision, HDR Vivid | 620 | TV 78 | TV 78 | 620 |
| IMAX Enhanced, Filmmaker Mode | no (stock: none) | no (stock: none) | TV 163 | no |
| colour range setting, input mode (EDID) | 634, 661 | TV 64, 39 | TV 64, 39 | 634, 661 |
| port: CEC, ARC, physical address | no getter | TV 153 | TV 153 | no |
| ALLM, VRR, FreeSync | IGmpf2 310, 300, 271 | TV 131, 146, 173 | TV 131, 146, 173 | IGmpf2 212 |
| picture on the chip size @ rate, judder line | 157 | DM 57 | DM 179 (rate) | no |
| game mode, HGiG, HDR tone mapping | IGmpf2 55, no, 269 | DM 148, TV 192, 175 | DM 148, TV 192, 175 | no |
| Dolby Vision mode (with Dolby Vision only) | no (no Dolby Vision) | no (no Dolby Vision) | TV 75 | no |
| picture mode (on the small card too, last line), motion smoothing (MEMC), the menu's own reads and words | 666, 668 | TV 34, 32 | TV 34, 32 (MEMC with FD boost 161 for Original, where the menu has MEMC) | 666, 668 (MEMC only in Movie, TV, Custom) |
| light source temperature, temperature inside | 439 sensor 5, 438 | no, TEMPERATURE 6 | TEMPERATURE 7 sensor 5, 6 | no, 438 |
| sound output, sound format | Android | Android | Android | Android |
| colour format and chroma, colour depth, colorimetry, range in use with Auto | HDMI receiver bridge | HDMI receiver bridge | HDMI receiver bridge | no |
| HDR mastering display and MaxCLL / MaxFALL (HDR10, HDR10+) | HDMI receiver bridge | HDMI receiver bridge | HDMI receiver bridge | no |
| source device name and kind (SPD InfoFrame), HDMI link rate (FRL or TMDS) | HDMI receiver bridge | HDMI receiver bridge | HDMI receiver bridge | no |

### The HDMI receiver report (Z9X, Z9X HB and the X50 family, item `hdmi_format`)

- Source: the MStar receiver's debug node `/proc/utopia_mdb/HDMIRX` (0600 root) prints its info dump into the kernel
  log. The image's root oneshot `image/lumen_hdmirx.rc` + `.sh` (Z9X, HB and X50 family recipes, models G0082, G0130, G0148, G0111, G0100,
  G0155, H0062, H0063: the X50 family runs the Z9X's MT5877 platform and its vendor `utpa2k.ko` prints the same dump, `gsi-research/SIGNAL_X50.md`)
  asks it on request
  (`sys.lumen.hdmirx.req` = token): first the stock XGIMI log collector's plain read, only if no dump follows the exact
  bytes `help` (proven live), never any other command (`test/check_hdmirx.py`). It keeps only the info lines
  (`/dev/lumen_hdmirx/info.txt`, no EDID, no HDCP) and answers in `sys.lumen.hdmirx.done`. Floor 0.9 s between dumps.
- `HdmiRxInfo` decodes the active receiver block: "Color space", "Color depth", AVI InfoFrame bytes (dump Byte k = PB(k+1))
  per CTA-861: Y, C / EC (BT.601, BT.709, BT.2020, DCI-P3 only when PB14 is printed), Q / YQ with the format's default,
  VIC, picture aspect M. "No data" colorimetry shows the default with a small "by default". Size: the last
  "HDE: w  VDE: h" line of the active port's section, the IP block's only without one (the HB's IP block printed
  1919 x 9 for a 3840x2160 4:2:0 signal). Depth: the GC packet's deep colour field when it names one ("Color depth
  12bit"), else the receiver's `u8Colordepth` (the HB GC said "not indicated or default as 8bit", `u8Colordepth` 10 bit,
  and its TMDS clock 92 MHz x 4 / 297 MHz pixel = 1.25 proves 10). Both captures are host fixtures.
- `FormatWatch` (only while the panel is on screen): at once when it opens, 1 s after the HAL's signal (size, rate, HDR
  kind, port) changed and stayed, and every 5 s (Normal) or 1 s (Fast, a developer choice), because a colour format,
  depth or range change at the same timing raises no event and the dump has no change flag. A change, a failed read, an
  answer for another port (`signal.tsv` `ports=1:2,2:0`: HDMI 1 is MStar port 2 by its EDID address 1.0.0.0) or of
  another size hides the rows at once. A dump holds the receiver's lock 7-11 ms on the Z9X (may cost a frame now and
  then) and 359 + 393 ms on the HB (the picture can stall about 0.4 s per read). Same refresh on both models (owner
  2026-11-18), the HB map is `ports=1:0,2:1` (HDMI 1 = MStar port 0, EDID 1.0.0.0, eARC port). No cheaper read exists:
  the node's help lists only write commands and every read prints all ports and receivers.

- More of the same dump (`InfoFrames`, `PacketLayout`): the HDR packet (CTA-861.3 static metadata: EOTF, mastering
  primaries, peak and black, MaxCLL, MaxFALL), the SPD packet (the source's vendor and product name and its kind) and the
  receiver's link state (`u8FRLRate .. is FRL_8G_4L`, `LEGACY_20` = TMDS above 3.4 Gbit/s). The receivers print packets
  in different byte layouts (the HB its HDR packet as byte-swapped words from header byte 1, the Z9X its SPD packet from
  PB1 and its VS packet with the header in front). A packet counts only in a layout its header bytes, checksum (when
  every byte is printed) and field ranges prove. Mastering and light rows show only beside an HDR kind the HAL reports
  and a PQ packet. The HB capture proves BT.2020, 1000 and 0.005 cd/m2, the Z9X capture "SCEI PS5", a game console.
- Port maps (`signal.tsv` `ports=`): the stock `tbl_Hdmi_Hardware_Mapping` of each model (xgimiconfig `tv/tvconfig.sql`,
  HDMI index to MStar port): Z9X `1:2,2:0` (the same table gives the map proven live), X50 Ultra, Ultra Max, Titan
  `1:2,2:1,3:0`, RS30 `1:2,2:0,3:1`.

## Pieces

| file | role |
|---|---|
| `SignalItem` | the reads: HAL items (one vendor call each) and Android items (display mode, media output) |
| `SignalReader` | the only per-model part: the projector app's adapter over its own HAL client |
| `SignalBudget` | at most 2 vendor calls per second, none while hidden, card items every 2 s, the rest every 10 s or once (`ReadSpeed`) |
| `SignalPlace` | where the card and the full panel sit (title safe, below the header) |
| `Judder` | the judder line: chip rate a whole multiple of the source rate or not |
| `SteadyRate` | the input rate on the card: the nominal timing table rate once two reads agree (three outside the table), "VRR" while VRR reads move, the size alone while a new mode settles (a tester saw 133 Hz once in game mode on the HB) |
| `SignalSheet`, `SignalFormat`, `SignalText` | what to draw, numbers and texts, a row this model cannot read is not there |
| `SignalItems` | the model's rows as data: `devices/<c>/signal.tsv`, in the app as `assets/lumen_signal/items.tsv` |
| `SignalPanel`, `SignalWindow`, `SignalReceiver` | the Android side: controller, overlay window, the intent `SIGNAL_PANEL` |
| `OkHold`, `CardHold`, `PanelMotion` | holding OK on a card (pure rules, the key filter hook, the grow and fold motion), shared with lumen-sensors |

Readers (no new HAL client, the apps' own getters): `apps/ProjectorBase/.../panel/GmpfSignalReader` (Z9X and, through
the base layer, the HB), `apps/X50Projector/.../X50SignalReader` (X50 family), `apps/Play6Projector/.../panel/Play6SignalReader`.
LumenSensors and LumenInfo keep their own readers for now: they are separate APKs (LumenInfo only on the HB image)
with their own transports, the projector apps' clients are the shared ones (lumen-gmpf).

`bash apps/core/lumen-signal/test/run.sh` also runs the bridge on a fake root with the real Z9X and HB captures
(`test/hdmirx_bridge_test.sh`), the parser and refresh rules (`test/HdmiRxTest.java`) and `test/check_hdmirx.py`.

Never read: IGmpf 194, IGmpf 275, IGmpf2 252 on the Z9X (`test/check_signal.py` refuses them in data and readers).

## Tests

`bash apps/core/lumen-signal/test/run.sh` (host only, run it on a builder).
