# lumen-system

The Android settings screens every Lumen app opens, in one place:

- `SettingsPage`: the screens (`BLUETOOTH` = Remotes & Accessories, `NETWORK` = Wi-Fi);
- `SystemIntent.chain(page)`: the ways to open a page, best first, the settings root last. LineageOS 21 TvSettings
  has no `android.settings.BLUETOOTH_SETTINGS` activity, so Bluetooth first opens the Remotes & Accessories slice of
  TvSettings (`android.settings.SLICE_SETTINGS` with `slice_uri`);
- `SystemPages.open(context, page)`: starts the first way some app handles, goes on when a way is missing or
  refuses, false when none starts (the caller shows its note).
- `KeyTone`: Android's key sounds (Settings.System `sound_effects_enabled`), the stock sound page's key tone switch;
- `ColourVision`: Android's colour correction (Settings.Secure `accessibility_display_daltonizer_enabled` and
  `accessibility_display_daltonizer`), the stock General page's colour vision row: off or one of the stock filters
  gray (0), red/green (11), green/red (12), blue/yellow (13). The stock strength slider writes
  `accessibility_display_daltonizer_saturation_level`, which the Android 14 ColorDisplayService never reads: no row.

A source module (tools/arch/README.md): an app names it in its recipe with `module lumen-system`. Users: the shared
projector menu (`apps/core/lumen-menu`, its tiles; every recipe with `module lumen-menu` names this module too) and the
first-run app (`devices/horse_popular/app/Z9xSetup.layers`, `devices/goat_elegant/app/HbSetup.layers`: the remote
step's "other remote"). Tests: `sh apps/core/lumen-system/test/run.sh` (ColourVisionTest runs on host stubs of android.* in `test/stubs`),
mutants `test/mutate.py`.
