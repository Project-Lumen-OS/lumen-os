#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""lumen-source wiring checks: check_source.py GSI. Exit 1 on any red line."""
import glob, os, re, sys

G = sys.argv[1]
M = os.path.join(G, "apps", "core", "lumen-source")
bad = []
n = 0


def ok(cond, what):
    global n
    n += 1
    if not cond:
        bad.append(what)


def read(*p):
    with open(os.path.join(G, *p), encoding="utf-8") as f:
        return f.read()


sys.path.insert(0, os.path.join(G, "tools", "devices"))
import assemble_app  # noqa: E402  (a model's recipe and data: its own, else its family's, devices/README.md)
import layered  # noqa: E402
# every model of the registry that builds a projector app (no hand list: a new model is checked by being added)
APPS = {c: a for c, a, _ in assemble_app.all_recipes(G) if a.endswith("Projector")}
ok(len(APPS) >= 10, "projector models in the registry: %d" % len(APPS))
for c, app in APPS.items():
    src = layered.find(os.path.join(G, "devices", c, "device.conf"), "source.tsv")
    r = assemble_app.recipe_text(G, c, assemble_app.find_recipe(G, c, app))
    ok(re.search(r"^module\s+lumen-source\s*$", r, re.M), c + ": recipe names module lumen-source")
    ok(src and re.search(r"^file\s+%s\s+assets/lumen_source/source\.tsv\s*$" % re.escape(os.path.relpath(src, G)), r, re.M),
       c + ": recipe ships its source.tsv")
    ok(src is not None, c + ": devices/%s/source.tsv (or its family's)" % c)
ok(sorted(c for c in os.listdir(os.path.join(G, "devices")) if os.path.isfile(os.path.join(G, "devices", c, "device.conf"))
          and layered.find(os.path.join(G, "devices", c, "device.conf"), "source.tsv")) == sorted(APPS),
   "source.tsv only for the projector models")

# the key: the first DOWN, as stock (pressBehaver 0, no long press action)
key = read("apps", "core", "lumen-source", "src", "org", "lumen", "source", "SourceKey.java")
ok("e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) press(c, \"key\")" in key, "SourceKey.onKey: first DOWN only")
for app in ("LumenProjector/core", "HbProjector", "Play6Projector"):
    k = read("apps", app, "src", "org", "lumen", "projector", "KeyReceiver.java")
    ok('if (firstDown && setupDone(ctx, "source")) org.lumen.source.SourceKey.press(ctx, "key");' in k,
       app + ": Source key -> SourceKey on the first DOWN after setup")
x50 = read("apps", "LumenProjector", "host", "activity", "src", "org", "lumen", "x50", "projector", "KeyReceiver.java")
ok('case KeyEvent.KEYCODE_TV_INPUT:' in x50 and 'if (down && e.getRepeatCount() == 0) SourceReceiver.press(c, "key");' in x50,
   "X50: Source key -> SourceReceiver.press on the first DOWN")
gk = read("apps", "X50PowerOverlay", "res", "xml", "global_keys.xml")
ok('<key keyCode="KEYCODE_TV_INPUT" component="org.lumen.x50.projector/.KeyReceiver" />' in gk, "X50 RRO: Source to the X50 app")
for f in ("Z9xFrameworkKeysOverlay", "HbFrameworkOverlay", "Play6FrameworkOverlay"):
    ok('<key keyCode="KEYCODE_TV_INPUT" component="org.lumen.projector/.KeyReceiver" />' in read("apps", f, "res", "xml", "global_keys.xml"),
       f + ": Source to org.lumen.projector")
for rel in ("apps/common/projector/src/org/lumen/projector/source/SourceRequestReceiver.java",):
    ok('org.lumen.source.SourceKey.press(ctx, "show_source");' in read(*rel.split("/")), rel + ": Source request = the key's step")
ok('SourceKey.press(c, why);' in read("apps", "LumenProjector", "host", "activity", "src", "org", "lumen", "x50", "projector", "SourceReceiver.java"),
   "X50 Source request = the key's step")

# an older HDMI viewer (no quiet mark) shows its own banner in the card's place: the card stands aside for HDMI steps
ok('<meta-data android:name="org.lumen.tvinput.source_key_quiet" android:value="true" />' in read("apps", "LumenTvInput", "AndroidManifest.xml"),
   "LumenTvInput: the quiet mark the card checks")
ok('final boolean card = s.shown <= 0 || SourceInputs.viewerQuiet(app);' in key and 'if (card) osd(app).show(' in key
   and 'else osd(app).hideNow();' in key, "SourceKey: the card stands aside for HDMI steps under an older viewer")
ok('static final String QUIET_MARK = "org.lumen.tvinput.source_key_quiet";' in read("apps", "core", "lumen-source", "src", "org", "lumen", "source", "SourceInputs.java"),
   "SourceInputs: the same mark name")
ok('static final String REASON_SOURCE_KEY = "source_key";' in read("apps", "LumenTvInput", "src", "org", "lumen", "tvinput", "PassthroughActivity.java")
   and 'public static final String REASON_KEY = "source_key";' in key, "viewer and module agree on the reason")

# the card slides in once; a press while it is up only changes the text and restarts the timer
osd = read("apps", "core", "lumen-source", "src", "org", "lumen", "source", "SourceOsd.java")
show = osd[osd.index("    void show("):osd.index("    void update(")]
ok(show.count(".translationX(0f).setDuration(MS_IN)") == 1 and show.index("if (fresh) {") < show.index(".translationX(0f).setDuration(MS_IN)")
   < show.index("} else {"), "SourceOsd.show: the slide runs only for a new card")
ok(show.count("card.animate().cancel();") == 1 and "card.setAlpha(1f);\n            card.setTranslationX(0f);" in show,
   "SourceOsd.show: an update stops a fade and stands still")
ok("main.removeCallbacks(hide);\n        main.postDelayed(hide, ms);" in show, "SourceOsd.show: every press restarts the timer")
ok("new WindowManager.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT," in osd, "SourceOsd: full width window, a longer text never resizes it")
ok("FLAG_NOT_FOCUSABLE" in osd and "FLAG_NOT_TOUCHABLE" in osd, "SourceOsd: never takes keys")
ok("LumenTheme.palette(c)" in osd, "SourceOsd: the palette of the one theme switch")

# nothing of the old right side list is left
for p in glob.glob(os.path.join(G, "apps", "**", "*.java"), recursive=True):
    s = open(p, encoding="utf-8").read()
    rel = os.path.relpath(p, G)
    ok(not re.search(r"\bSourceOverlay\.(toggle|showFromQuickPanel|openPort)\b|class SourceOverlay\b|class SourceRow\b", s),
       rel + ": the old source list is gone")

# texts: the old list's own words, every locale, plain
sys.path.insert(0, os.path.join(G, "tools", "i18n"))
import res_strings  # noqa: E402  (strings files as the APK resolves them)
base, mod = {}, {}
for d in sorted(os.listdir(os.path.join(G, "apps", "common", "projector", "res"))):
    if d.startswith("values"):
        base[d] = {k: v for k, v in res_strings.strings(os.path.join(G, "apps", "common", "projector", "res", d, "strings_source.xml")).items()
                   if k in ("source_home", "source_hdmi")}
        mod[d] = {k[len("lumen_source_"):]: v for k, v in res_strings.strings(os.path.join(M, "res", d, "lumen_source_strings.xml")).items()
                  if k in ("lumen_source_home", "lumen_source_hdmi")}
ok(sorted(mod) == sorted(base) and len(mod) >= 18, "every locale of the old list: %d" % len(mod))
for loc, v in mod.items():
    ok(v.get("home") == base.get(loc, {}).get("source_home") and v.get("hdmi") == base.get(loc, {}).get("source_hdmi"),
       loc + ": the old words, not translated again")
    ok(not re.search("[;\u2013\u2014]", "".join(v.values())), loc + ": no semicolon or long dash")

print(("ok   " if not bad else "FAIL ") + "check_source %d checks, %d red" % (n, len(bad)))
for b in bad:
    print("FAIL " + b)
sys.exit(1 if bad else 0)
