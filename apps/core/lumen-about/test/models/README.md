# Per-model inputs of the firmware versions (AboutTest.modelTests)

Taken from the stock dumps on xgimi-builder `~/devices` (2026-11-18), trimmed to what the rows read, no serials:

- `<code>.props`: the properties (getprop of the unit's LumenDump or info dump; H0061, H0062 and H0063 from the build.prop
  files of the official V1.2.99 firmware, one image for the three codes) plus `kernel=` (the release in /proc/version).
- `<code>.version.txt`: the first line of /vendor/etc/version.txt of that firmware.
- `<code>.dlp.ini`, `<code>.flash.ini`: the enum, DlpType, extFPGAType and DlpFlashImageVersion lines of the model's
  dlp/dlp.ini and dlp/DlpFlashImageInfo.ini (xgimiconfig of the firmware).
- `stock_app.tsv`: what an ordinary app cannot read on the XGIMI system (the SELinux policy of each image).

| model | source |
|---|---|
| G0082 Z9X | V6.15.62 LumenDump + firmware tree |
| G0130 HB | V6.16.24 getprop, firmware tree (no /proc/version) |
| G0148 HB | V6.17.12 LumenDump + firmware tree |
| G0111 X50 Ultra | V1.2.64 info dump + firmware tree |
| G0100 X50 Ultra Max | only its dlp folder in the X50 firmware (no dump of the unit) |
| G0155 RS30 Ultra Max | V1.1.42 dump + firmware tree |
| H0061, H0062, H0063 TITAN | official V1.2.99 firmware tree (no dump of a unit; H0061 has no DlpFlashImageInfo.ini) |
| G0058 Play 6 | V1.8.127 LumenDump (no firmware tree, no dlp folder) |
