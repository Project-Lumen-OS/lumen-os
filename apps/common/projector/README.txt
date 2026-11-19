apps/common/projector: single source of the code and resources that the projector apps of the Lumen OS
devices share (Z9xProjector and the projector app of any other device).

What is shared: every path listed in FILES (relative to the app dir). No app keeps a copy: the recipe of
every projector app (devices/<codename>/app/*.layers) takes this folder as a base layer
(`base apps/common/projector apps/common/projector/FILES`, the X50 apps a subset list), and
tools/devices/assemble_app.py refuses an app file that shadows one of these paths without a fork row. The
device HAL clients (src/org/lumen/projector/hal/) are never shared, each device has its own code table;
hal/HidlCaller.java is a `file` layer of its own (apps/common/hal).

Rules
- The shared files are owned by the Z9X app. A shared file changes only together with a
  Z9xProjector change: edit it here. Every projector app picks the change up at its next build.
- A need of another device alone never edits a shared file: that device forks the file into its own app at
  that moment, the path leaves FILES, and that device tracks its fork against the Z9X file.
- A Z9X change that breaks another device's app is fixed on that device's side. It never blocks or changes a
  Z9X release.
- Release preflight: python3 -B tools/devices/assemble_app.py --release on the Z9X, HB and Play 6
  projector recipes (tools/play6/z9x_gate.sh runs it).

Languages
- The strings files listed in FILES belong to the `common` target of apps/i18n/gen.py: their translations
  are the @common/<file> sections of apps/i18n/langs (never @projector/<file>), and the `projector` target
  skips them. gen.py --write writes their locale files here.
- Hand-written locale files (values-ru/strings.xml, strings_power.xml, strings_source.xml; the remote pairing texts
  live in apps/core/lumen-remote since the shared remote module) are plain files in FILES:
  edit them here.
