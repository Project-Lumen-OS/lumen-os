# lumen-ui

The TV look of every Lumen app that is not frozen: `org.lumen.ui.TvFocus` (D-pad focus of buttons, dark dialogs,
own toast view, focusable panels, unclip of a scaled button) and `org.lumen.ui.Tokens` (the colours; accent #3B78E7,
owner decision D3).

A source module (tools/arch/README.md): an app names it in its recipe with `module lumen-ui` and
`tools/devices/assemble_app.py` copies `src/` (and `res/`, if any) into the app tree before `apps/build_apk.sh` runs.
Modules are closed: an app never shadows or drops a module file. Resources of a module are named `lumen_*`.

Users: LumenInstaller, LumenFlasher, LumenDsuInstall (apps/<App>/app.layers), X50EyeGuard, X50Projector, X50TvInput
(devices/g0111/app/*.layers). The Z9X, HB and Play 6 projector apps take it with lumen-menu, whose sound output page
(2026-11-18) is drawn in the 2.0 palette.
Lumen Setup (org.lumen.setup, every Setup recipe: horse_popular, g0155, goat_elegant) uses the 2.0 look since
2026-11-18 at the owner's request (Setup 2.0, same tokens as Home 2.0): its arch baseline rows change on purpose.

## Lumen 2.0 look (FINAL_A.md, owner 2026-11-18)

One token set for Home, Setup and the menus: `Palette` (two colour sets, `LumenTheme.LIGHT` the default and
`LumenTheme.DARK`, the same numbers as `lumen_light_*` / `lumen_dark_*` in `apps/common/tokens.xml`), `Tokens`
(type, radii, spacing, focus scales, motion times), `Motion` (curves, the system "remove animations" switch).

The theme switch is ONE mechanism for every app: `LumenTheme` stores the choice in Settings.Secure `lumen_ui_theme`
(`light` default, `dark`, `auto`) and the last known place in `lumen_ui_geo` (`lat,lon`, written by whoever learns it).
Авто is light from sunrise to sunset at that place (`ThemeRule` with `SolarCalc`), 07:00 to 19:00 without one.
`LumenTheme.palette(ctx)` gives the set on screen now, `ThemeWatcher` calls back when it changes (choice, place, clock,
the next sunrise or sunset). Writing needs WRITE_SECURE_SETTINGS (every platform-signed Lumen app holds it).

Host tests: `sh apps/core/lumen-ui/test/run.sh` (rule, tokens.xml equal to Palette, WCAG AA of every text pair).
