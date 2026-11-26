#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""lumen-signal checks that need the files, not the JVM (test/run.sh runs it):
1. texts: res/values and res/values-ru hold exactly the SignalText keys, with the same %N$s arguments; plain words
   (no semicolon, no long dash, never DSU, vbmeta or slot); the frame rate row's label apart from the menu's MEMC one;
2. data: every devices/*/signal.tsv names only known HAL items, once each, never the banned vendor calls
   (IGmpf 194 isPlayingHDRVideo, IGmpf 275 light sensor, IGmpf2 252 laser current); every app recipe with module
   lumen-signal ships its model's signal.tsv as assets/lumen_signal/items.tsv; every profile with signal.panel has
   a signal.tsv;
3. readers: the projector apps' SignalReader adapters never name a banned call.
"""
import glob
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
G = os.path.abspath(os.path.join(MOD, "..", "..", ".."))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", "tools", "devices"))
import menu_layers  # noqa: E402  (menu profiles have one reader, devices/README.md)
bad = []


def ok(what, cond):
    if cond:
        print("ok   " + what)
    else:
        print("FAIL " + what)
        bad.append(what)


def read(p):
    return menu_layers.read_text(p)


src = read(os.path.join(MOD, "src/org/lumen/signal/SignalText.java"))
all_block = src[src.index("ALL = {"):src.index("};", src.index("ALL = {"))]
names = re.findall(r"\b([A-Z][A-Z0-9_]+)\b", all_block)
consts = dict(re.findall(r"\b([A-Z][A-Z0-9_]+) = \"([a-z0-9_]+)\"", src))
keys = {consts[n] for n in names if n in consts}
ok("SignalText.ALL names every key constant (%d)" % len(consts), keys == set(consts.values()))

texts = {}
for loc in ("values", "values-ru"):
    x = read(os.path.join(MOD, "res", loc, "lumen_signal_strings.xml"))
    texts[loc] = dict(re.findall(r'<string name="lumen_signal_([a-z0-9_]+)">([^<]*)</string>', x))
    ok("%s: exactly the SignalText keys" % loc, set(texts[loc]) == keys)
    for k, v in texts[loc].items():
        if ";" in v or "\u2014" in v or "\u2013" in v or re.search(r"\b(dsu|vbmeta|slot)\b", v, re.I):
            bad.append("%s %s: not plain: %r" % (loc, k, v))
for k in keys & set(texts["values"]) & set(texts["values-ru"]):
    a = sorted(re.findall(r"%\d\$s", texts["values"][k]))
    b = sorted(re.findall(r"%\d\$s", texts["values-ru"][k]))
    if a != b:
        bad.append("arguments differ for %s: %s / %s" % (k, a, b))
ok("texts: plain words, same arguments in English and Russian", not [b for b in bad if "not plain" in b or "arguments" in b])

# the frame rate row (source rate against the chip rate) stands next to the menu's motion smoothing (MEMC) row: two
# labels starting with the same word (Плавность / Плавность движения) read as one setting
for loc in ("values", "values-ru"):
    m = re.search(r'<string name="lumen_m_panel_memc">([^<]*)</string>',
                  read(os.path.join(G, "apps/core/lumen-menu/res", loc, "lumen_strings_menu.xml")))
    cad = texts[loc].get("r_cadence", "")
    ok("%s: frame rate row %r apart from the MEMC row %r" % (loc, cad, m.group(1) if m else None),
       m is not None and cad != "" and cad.split()[0].lower() != m.group(1).split()[0].lower())

items_src = read(os.path.join(MOD, "src/org/lumen/signal/SignalItem.java"))
hal = {t for t, h, b in re.findall(r'\("([a-z_]+)", (true|false), ReadSpeed\.[A-Z_]+, (?:true|false)(?:, (true|false))?\)',
                                   items_src) if h == "true" or b == "true"}
ok("SignalItem: HAL items found (%d)" % len(hal), len(hal) >= 9)
BANNED = re.compile(r"IGmpf 194|IGmpf 275|IGmpf2 252|isPlayingHDRVideo|getCurHwCurrent|LightSensor", re.I)
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import layered  # noqa: E402  (a model's data: its own file, else its family's, devices/README.md)
models = sorted(c for c in os.listdir(os.path.join(G, "devices")) if os.path.isfile(os.path.join(G, "devices", c, "device.conf")))
have = {c: layered.find(os.path.join(G, "devices", c, "device.conf"), "signal.tsv") for c in models}
ok("signal.tsv of 8 models (%d)" % len([c for c in have if have[c]]), len([c for c in have if have[c]]) >= 8)
devs = sorted(set(p for p in have.values() if p))
for p in devs:
    c = os.path.relpath(os.path.dirname(p), os.path.join(G, "devices"))
    rows = [l.split("\t") for l in read(p).splitlines() if l and not l.startswith("#") and not l.startswith("item\t")]
    toks = [r[0] for r in rows]
    ok("%s: known HAL items, once each" % c, all(t in hal for t in toks) and len(toks) == len(set(toks)) and toks)
    ok("%s: no banned vendor call" % c, not BANNED.search(read(p)))
for j in glob.glob(os.path.join(G, "devices/*/menu.json")):
    c = os.path.basename(os.path.dirname(j))
    if '"signal.panel"' in read(j):
        ok("%s: profile has signal.panel and a signal.tsv" % c, bool(have.get(c)))
sys.path.insert(0, os.path.join(G, "tools", "devices"))
import assemble_app  # noqa: E402
for c, _, rec in assemble_app.all_recipes(G):
    r = assemble_app.recipe_text(G, c, rec)
    if not re.search(r"^module\s+lumen-signal\s*$", r, re.M):
        continue
    want = r"^file\s+%s\s+assets/lumen_signal/items\.tsv\s*$" % re.escape(os.path.relpath(have[c], G) if have.get(c) else "devices/%s/signal.tsv" % c)
    ok("%s: ships devices/%s/signal.tsv" % (os.path.relpath(rec, G), c), re.search(want, r, re.M) is not None)
    ok("%s: names lumen-icons (dependency)" % os.path.relpath(rec, G), re.search(r"^module\s+lumen-icons", r, re.M) is not None)
    ok("%s: names lumen-ui (dependency)" % os.path.relpath(rec, G), re.search(r"^module\s+lumen-ui\s*$", r, re.M) is not None)

readers = [p for p in glob.glob(os.path.join(G, "apps/*/src/**/*SignalReader.java"), recursive=True) + glob.glob(os.path.join(G, "apps/LumenProjector/**/src/**/*SignalReader.java"), recursive=True)]
ok("three readers (Z9X and HB, X50 family, Play 6): %d" % len(readers), len(readers) == 3)
for p in readers:
    s = read(p)
    calls = re.sub(r"/\*.*?\*/|//[^\n]*", "", s, flags=re.S)
    ok("%s: no banned call" % os.path.relpath(p, G),
       not BANNED.search(calls) and not re.search(r"\b(194|275|252)\b", calls))

for b in bad:
    if "not plain" in b or "arguments" in b:
        print("FAIL " + b)
# who has the keys (2026-11-21: OK on the gear of the quick menu over the full panel switched the panel's option)
def src(rel):
    return read(os.path.join(G, rel))


def body(text, start):
    i = text.index(start)
    j, depth = text.index("{", i), 0
    for k in range(j, len(text)):
        depth += {"{": 1, "}": -1}.get(text[k], 0)
        if depth == 0:
            return text[i:k + 1]
    return text[i:]


# HB live audit 2026-11-24: a signal.tsv row the model's HAL client refuses is polled every 2 s for nothing (the HB
# GmpfClient throws NotOnHbException for getOutputTimingInfo, so its chip row never showed and the log filled)
hbc = open(os.path.join(G, "apps/LumenProjector/vendor/xgimi/aidl-gmpf/src/org/lumen/projector/hal/GmpfClient.java"), encoding="utf-8").read()
m = re.search(r"OutputTimingInfo getOutputTimingInfo\(\)[^{]*\{([^}]*)\}", hbc)
hb_refuses = bool(m) and "NotOnHbException" in m.group(1)
hbsig = open(os.path.join(G, "devices/goat_elegant/signal.tsv"), encoding="utf-8").read()
ok("HB signal.tsv has a chip row only when HbProjector reads getOutputTimingInfo",
   bool(m) and (not hb_refuses or not re.search(r"^chip\t", hbsig, re.M)))
ok("signal.tsv notes carry no unmeasured picture stall (owner 2026-11-18: UNVERIFIED)",
   not any(re.search(r"stall the picture about", open(f, encoding="utf-8").read())
           for f in glob.glob(os.path.join(G, "devices", "*", "signal.tsv"))))

SIG = "apps/core/lumen-signal/src/org/lumen/signal/"
SEN = "apps/core/lumen-sensors/src/org/lumen/sensors/"
ch = src(SIG + "CardHold.java")
shown_now = body(ch, "private static OkHold.Shown shownNow()")
ok("CardHold asks who has the keys: OkHold.shown(mode, the panel's own focus, a window of this app)",
   "OkHold.shown(card.mode(), card.hasKeys(), KeyHook.appHasKeys())" in shown_now and "Shown.SHEET" not in shown_now)
ok("CardHold follows the activities of the app at start",
   "followActivities(sApp);" in body(ch, "public static void start(Context c)"))
ok("CardHold: the long press timer never before the filter saw the press",
   "postAtTime(TIMER, OkHold.timerAt(s.due, SystemClock.uptimeMillis()))" in ch)
ok("CardHold: BACK is followed, a held BACK dismisses the card",
   "KeyEvent.KEYCODE_BACK ? OkHold.Key.BACK" in ch and "case HIDE: card.dismiss();" in ch
   and "HOLD.key(key(e.getKeyCode())" in ch)
ow = src(SIG + "OverlayWindow.java")
ok("OverlayWindow: the one key rule, only the full sheet takes keys and never while a Lumen panel is in front",
   "private boolean takesKeys() { return shownMode == PanelMode.DETAIL && !folding && !KeyHook.appHasKeys(); }" in ow)
ok("OverlayWindow: the sheet has the keys only with its own window focus and by the rule",
   "public boolean hasKeys() { return shown() && takesKeys() && root.hasWindowFocus(); }" in ow)
ok("OverlayWindow: a sheet is added not focusable while a Lumen panel is in front, asks the focus only by the rule",
   "else flags = focusFlags(flags, takesKeys());" in body(ow, "private WindowManager.LayoutParams params(boolean detail)")
   and "if (takesKeys()) root.requestFocus();" in body(ow, "private boolean add(PanelMode mode)"))
ok("OverlayWindow: a Lumen panel coming or going makes every sheet give up or take back the keys",
   "KeyHook.watch(OverlayWindow::panelsChanged);" in ow
   and "int flags = focusFlags(p.flags, takesKeys());" in body(ow, "private void refreshKeys()")
   and "if (takesKeys()) root.requestFocus();" in body(ow, "private void refreshKeys()"))
ok("OverlayWindow: a held BACK on the card takes the keys through CardKeys", "CardKeys.take(wm, root, gone)" in ow)
ok("OverlayWindow: the card hint always, real icons for hold OK and hold BACK",
   "R.drawable.lumen_ic_radio_on, R.drawable.lumen_ic_arrow_back" in body(ow, "public View cardHint(SignalLook look)"))
for name, panel, window in (("signal", SIG + "SignalPanel.java", SIG + "SignalWindow.java"),
                            ("sensor", SEN + "SensorPanel.java", SEN + "SensorWindow.java")):
    p_, w = src(panel), src(window)
    ok("%s window: no window code of its own, it draws into the shared OverlayWindow" % name,
       "new OverlayWindow(c, " in w and "WindowManager" not in w and "addView(root" not in w
       and "boolean hasKeys() { return w.hasKeys(); }" in w and "void takeKeys(Runnable gone) { w.takeKeys(gone); }" in w)
    ok("%s card: hasKeys is the window's, a held BACK takes the keys then dismisses" % name,
       "public boolean hasKeys() { return sWindow != null && sWindow.hasKeys(); }" in p_
       and re.search(r"sWindow\.takeKeys\(\(\) -> \w+\(TOGGLE\.dismiss\(\)\)\)", p_) is not None)
    ok("%s card: the hint always" % name,
       "box.addView(w.cardHint(look)" in w and "boolean hint" not in w and "sHint" not in p_ and "CardHold.hint" not in p_)
ck = src(SIG + "CardKeys.java")
ok("CardKeys: the card window becomes focusable, goes on the press's UP or after GONE_MS",
   "p.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE" in ck and "KeyEvent.ACTION_UP) once.run()" in ck
   and "postDelayed(once, GONE_MS)" in ck)
sw = src(SIG + "SignalWindow.java")
ok("signal card: the pin mark when the option keeps it on screen",
   "if (pinned) top.addView(w.icon(R.drawable.lumen_ic_pin" in sw
   and "sWindow.show(mode, s, popupOn(sApp), pinned, held)" in src(SIG + "SignalPanel.java")
   and "boolean pinned = TOGGLE.pinned()" in src(SIG + "SignalPanel.java"))
ok("signal card: the pin mark follows the option while the card shows",
   "else if (now.visible() && sWindow != null) draw(true);" in body(src(SIG + "SignalPanel.java"), "private static void pinChanged"))
fa = body(ch, "private static void followActivities(Context c)")
oh = src("apps/common/projector/src/org/lumen/projector/ui/OverlayHost.java")
ok("CardHold: every activity of the app reports its window focus to KeyHook, gone on stop and destroy",
   "addOnWindowFocusChangeListener(" in fa and "has -> KeyHook.windowFocus(a, has)" in fa
   and "onActivityStopped(Activity a) { KeyHook.windowFocus(a, false); }" in fa
   and "KeyHook.windowFocus(a, false);\n            }" in fa)
ok("CardHold: a resumed activity of the app is a Lumen panel in front (KeyHook.front), gone on pause and destroy",
   "onActivityResumed(Activity a) { KeyHook.front(a, true); }" in fa
   and "onActivityPaused(Activity a) { KeyHook.front(a, false); }" in fa
   and "onActivityDestroyed(Activity a) {\n                KeyHook.front(a, false);" in fa)
ok("OverlayHost: a panel with the keys is in front from its show (before its focus) to its dismiss or aside",
   "if ((lp.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) KeyHook.front(p, true);" in body(oh, "public boolean show(Panel p)")
   and "KeyHook.front(p, false);" in body(oh, "void dismiss(Panel p, boolean animate)")
   and "KeyHook.front(p, !on && (asideFlags" in body(oh, "public void aside(boolean on)"))
# every overlay window: the shared developer OverlayWindow, a Lumen panel that reports itself in front (KeyHook.front),
# or a window that never takes a key. A new overlay window must be added here with its reason.
OVERLAY_WINDOWS = {
    "apps/core/lumen-signal/src/org/lumen/signal/OverlayWindow.java": "private boolean takesKeys()",
    "apps/common/projector/src/org/lumen/projector/ui/OverlayHost.java": "KeyHook.front(p, true);",
    "apps/core/lumen-wallcolor/src/org/lumen/wallcolor/WallColorWindows.java": "if (focusable) KeyHook.front(host, true);",
    "apps/core/lumen-source/src/org/lumen/source/SourceOsd.java": "int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE",
    "apps/LumenProjector/core/src/org/lumen/projector/game/PlayCueView.java": "OverlayHost.params(false,",
    "apps/LumenInfo/src/org/lumen/info/OverlayService.java": "| WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE",
    "apps/LumenMiracast/src/org/lumen/miracast/PromptOverlay.java": None,   # another app: its own phone prompt
}
found = set()
for dp, _dn, fn in os.walk(os.path.join(G, "apps")):
    for f in fn:
        if f.endswith(".java") and "/test" not in dp:
            rel = os.path.relpath(os.path.join(dp, f), G)
            t = open(os.path.join(dp, f), encoding="utf-8").read()
            if re.search(r"(?<!\* )WindowManager\.LayoutParams\.TYPE_APPLICATION_OVERLAY\s*,", t):
                found.add(rel)
ok("every overlay window is the shared OverlayWindow, a panel in front or never focusable: " + str(sorted(found - set(OVERLAY_WINDOWS))),
   found <= set(OVERLAY_WINDOWS))
ok("the listed overlay windows keep their reason in the code: " + str([r for r, n in OVERLAY_WINDOWS.items() if n and n not in src(r)]),
   all(n is None or n in src(r) for r, n in OVERLAY_WINDOWS.items()))
ok("no developer overlay keeps a window of its own (SignalWindow, SensorWindow draw into OverlayWindow)",
   not [r for r in found if r.endswith(("SignalWindow.java", "SensorWindow.java"))])
kh = src("apps/core/lumen-remote/src/org/lumen/remote/keys/KeyHook.java")
ok("KeyHook: the keys are a Lumen panel's while a window of the app has the focus or a panel is in front",
   "return !FOCUSED.isEmpty() || !FRONT.isEmpty();" in kh
   and "if (put(FOCUSED, window, focused)) changed();" in kh and "if (put(FRONT, panel, on)) changed();" in kh)
oh = src("apps/common/projector/src/org/lumen/projector/ui/OverlayHost.java")
root = body(oh, "private final class PanelRoot extends FrameLayout")
ok("OverlayHost: every panel window reports its focus (gained, attached with it, detached)",
   "KeyHook.windowFocus(this, hasFocus);" in body(root, "public void onWindowFocusChanged(boolean hasFocus)")
   and "if (hasWindowFocus()) KeyHook.windowFocus(this, true);" in body(root, "protected void onAttachedToWindow()")
   and "KeyHook.windowFocus(this, false);" in body(root, "protected void onDetachedFromWindow()"))
ok("KeyFilterService asks the module hook first", "KeyHook.take(event) ||" in
   src("apps/core/lumen-remote/src/org/lumen/remote/keys/KeyFilterService.java"))
left = [r for r in (SIG + "OkHold.java", SIG + "CardHold.java", SIG + "SignalPanel.java", SIG + "SignalReceiver.java",
                    SEN + "SensorPanel.java", SEN + "SensorReceiver.java",
                    "apps/core/lumen-remote/src/org/lumen/remote/keys/KeyActions.java")
        if re.search(r"HINT_TIMES|hold_hint_seen|hold_learned|EXTRA_BY_KEY|byKey", src(r))]
ok("no hint counters left (the hint shows always): %s" % left, not left)
for loc in ("values", "values-ru"):
    x = read(os.path.join(MOD, "res", loc, "lumen_signal_strings.xml"))
    hints = re.findall(r'name="lumen_signal_hint_(?:hold|more|gone)">([^<]*)<', x)
    ok("%s: card hint words, no symbol glyphs (icons draw the keys)" % loc,
       len(hints) == 3 and all(re.match(r"^[\w ]+$", h) for h in hints))
print(("ok   " if not bad else "FAIL ") + "check_signal: %d problem(s)" % len(bad))
sys.exit(1 if bad else 0)
