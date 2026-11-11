# lumen-ui

The TV look of every Lumen app that is not frozen: `org.lumen.ui.TvFocus` (D-pad focus of buttons, dark dialogs,
own toast view, focusable panels, unclip of a scaled button) and `org.lumen.ui.Tokens` (the colours; accent #3B78E7,
owner decision D3).

A source module (tools/arch/README.md): an app names it in its recipe with `module lumen-ui` and
`tools/devices/assemble_app.py` copies `src/` (and `res/`, if any) into the app tree before `apps/build_apk.sh` runs.
Modules are closed: an app never shadows or drops a module file. Resources of a module are named `lumen_*`.

Users: LumenInstaller, LumenFlasher, LumenDsuInstall (apps/<App>/app.layers), X50EyeGuard, X50Projector, X50TvInput
(devices/g0111/app/*.layers). Not used by the frozen Z9X / HB / Play 6 apps (owner decision D4: the 1.1 seam).
