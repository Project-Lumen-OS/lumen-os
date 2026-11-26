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

One token set for Home, Setup and the menus: `Palette` (two colour sets, `LumenTheme.LIGHT` the default, the warm
Sand set Песок the owner picked on 2026-11-19 because a white field is full laser power, and `LumenTheme.DARK`, the same numbers as `lumen_light_*` / `lumen_dark_*` in `apps/common/tokens.xml`), `Tokens`
(type, radii, spacing, focus scales, motion times), `Motion` (curves, the system "remove animations" switch).

The theme switch is ONE mechanism for every app: `LumenTheme` stores the choice in Settings.Secure `lumen_ui_theme`
(`light` default, `dark`, `auto`) and the last known place in `lumen_ui_geo` (`lat,lon`, written by whoever learns it).
Авто is light from sunrise to sunset at that place (`ThemeRule` with `SolarCalc`), 07:00 to 19:00 without one.
`LumenTheme.palette(ctx)` gives the set on screen now, `ThemeWatcher` calls back when it changes (choice, place, clock,
the next sunrise or sunset). Writing needs WRITE_SECURE_SETTINGS (every platform-signed Lumen app holds it).

Live switch (2026-11-19): every screen that follows the theme does it through `ThemeFade` (Home and Setup with
`ThemeFade.activity` + start/stop in onStart/onStop, the quick menu window, the signal panel and the source card with
`ThemeFade.follow(root, ...)`). It observes the setting, freezes what is on screen into the root's overlay (a recorded
Picture turned into a HARDWARE bitmap, so Home's hardware art is captured too), lets the app re-style in place under
it and fades the picture out over `Tokens.MS_THEME` (350 ms) on `Motion.STANDARD`. Hidden or with animations removed
it switches at once. The rules are plain Java in `ThemeFollow`; the app that stores a choice calls `apply()`. Not yet
live: Lumen Installer (own dark look), the remote pairing prompt and the sound output sheet take the theme when opened.

Apps take colours only from here: the light quick menu (lumen-menu QpdStyle.light) is made from the palette, the
signal and source cards read it, and the one static copy (the package installer overlay, an RRO without code) is
checked equal by ThemeTest. The focus ring is `Palette.focusRing` (#2F62D0 light for 3:1 on the field, #3B78E7 dark).

Host tests: `sh apps/core/lumen-ui/test/run.sh` (rule, tokens.xml equal to Palette, WCAG AA of every text pair, the
ring at 3:1, the Sand values, no light copies in apps,
ThemeFadeTest: the cross fade rules and the wiring of every follower).

## Two-pane page (org.lumen.ui.page)

The settings page shape of Lumen (Android TV settings, owner pick 2026-11-26 for the problem report): `TwoPane` is the
guide on the left (path, title, explanation, one fact, a privacy line) and the pane on the right with `PaneRow`s
(icon, title, second line, a switch or a chevron; focused = the palette's filled pill, 1.03, lifted), list pages
(`section`, `item`), a `PaneProgress` (title, a gliding bar that keeps a slow pulse, step line) and a `code` box.
Sizes are design px of the approved mock in `PaneStyle`, colours only from `Palette`, the font from `Inter` (the
`fonts/InterVariable.ttf` asset of the app, else the system sans). A theme change builds the page again in the new
palette (ThemeFade). User: lumen-report ReportActivity; a new page of this shape uses these classes, never a copy.

