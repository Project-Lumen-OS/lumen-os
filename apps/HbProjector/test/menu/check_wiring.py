#!/usr/bin/env python3
"""HB menu wiring (source level): the HB adapter of the shared menu (panel/HbMenuFeatures) gates every entry with
implemented() = HbMenuGates read against HbFeatures, refuses Boost without a vendor call where the picture mode holds
it, never sends the Boost step as a light level, re-checks HDMI before the HDMI-only setters, gives only refusal notes
that exist as strings; the game menu io (panel/HbGameIo) never writes super resolution or game sound and puts every
gated item behind its HbFeatures key; HbMenu installs the game menu only with the profile and the unit; the keys, App,
the recipe and the dropped HB menu code.  python3 test/menu/check_wiring.py <app root>"""
import glob, os, re, sys

HB = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GSI = os.environ.get("LUMEN_GSI") or os.path.abspath(os.path.join(HB, "..", ".."))
P = os.path.join(HB, "src/org/lumen/projector")
n = bad = 0


def ok(what, cond):
    global n, bad
    n += 1
    if not cond:
        bad += 1
        print("FAIL " + what)


def body(src, sig):
    i = src.find(sig)
    if i < 0: return ""
    j = src.find("{", i); depth = 0
    for k in range(j, len(src)):
        if src[k] == "{": depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0: return src[i:k + 1]
    return ""


def read(p):
    return open(p, encoding="utf-8").read() if os.path.exists(p) else ""


mf = read(os.path.join(P, "panel/HbMenuFeatures.java"))
gi = read(os.path.join(P, "panel/HbGameIo.java"))
hm = read(os.path.join(P, "panel/HbMenu.java"))
qp = read(os.path.join(P, "panel/QuickPanelController.java"))
kr = read(os.path.join(P, "KeyReceiver.java"))
ap = read(os.path.join(P, "App.java"))
ok("adapter present", bool(mf) and bool(gi) and bool(hm))

# ---- gates at every entry
imp = body(mf, "public Set<String> implemented()")
ok("implemented() = HbMenuGates read against HbFeatures", "HbMenuGates.implemented(" in imp and "HbFeatures.on(" in imp)
for sig, guard in (("public void set(String feature, String value, Consumer<Result> cb)", "if (!implemented().contains(feature))"),
                   ("public void run(String action, Consumer<Result> cb)", "if (!implemented().contains(action))"),
                   ("public boolean open(String screen, Runnable back)", "if (!implemented().contains(screen)) return false;")):
    b = body(mf, sig)
    ok(sig.split("(")[0].split()[-1] + ": the gate comes first", b and b.find(guard) >= 0 and b.find(guard) < b.find("switch ("))
gm = body(mf, "public static Set<String> gameItems()")
ok("gameItems() = HbMenuGates read against HbFeatures", "HbMenuGates.gameItems(" in gm and "HbFeatures.on(" in gm)

# ---- Boost: refused without a vendor call where the picture mode holds it; judged by the 152 read-back
sb = body(mf, "private String setBoost(boolean on, GmpfClient g, Gmpf2Client g2)")
i_explain = sb.find("BoostRules.Leave.EXPLAIN_PICTURE_MODE) return boostLocked(pm);")
ok("Boost: explain before 151", 0 <= i_explain < sb.find("g2.setBoost(on)"))
ok("Boost: judged by BoostRules with the read-back", "BoostRules.judge(on, ret, lensAfter, pmAfter)" in sb and "g2.isBoost()" in sb)
ok("Boost: picture mode read after", "pmAfter = g.getPictureModeRaw()" in sb)
ok("Boost: lock after 151 explained", "LOCKED_BY_PICTURE_MODE) return boostLocked(pmAfter);" in sb)
ok("Boost: fails only on FAILED", "Outcome.FAILED) throw" in sb and not re.search(r"if \(!ret\)\s*throw", sb))
bl = body(mf, "private String boostLocked(Integer pictureModeWire)")
ok("Boost lock: the stock HB text with the mode", "app.getString(R.string.hb_boost_locked, mode)" in bl and "return EXPLAINED;" in bl)
lamp = mf[mf.find("case Feature.LAMP: {"):mf.find("case Feature.BOOST:")]
ok("light: 1..10 only (never the Boost step)", "if (!HbMenuGates.lampValue(v)) throw" in lamp)
ok("light in Boost: leaves Boost first by the same rules", lamp.find("setBoost(false, g, g2)") >= 0
   and lamp.find("setBoost(false, g, g2)") < lamp.find("g.setLampLevel(v)"))
pm = body(mf, "private String setPictureMode(String value, GmpfClient g, Gmpf2Client g2)")
ok("colour accuracy: BoostRules", "BoostRules.blocksColorAccuracy(safeBoost(g2), raw)" in pm)
ok("picture mode: refused in game mode", 'if (gameModeOn(g, raw)) return "blocked_game";' in pm)
ok("picture mode: stock visibility for the content", "if (!offered(w, hdr)) return EXPLAINED;" in pm)

# ---- HDMI-only setters re-check the input
for case in ("case Feature.HDMI_RANGE: {", "case Feature.HDMI_EDID: {", "case Feature.PICTURE_ASPECT: {", "case Feature.GAME_ALLM: {"):
    seg = mf[mf.find(case):]
    seg = seg[:seg.find("case Feature.", 10)]
    ok(case + " re-checks HDMI", 'if (!hdmiNow(g)) return "panel_game_hdmi_only";' in seg)
for sig in ("private String setGameMode(", "private String setGameSpeed("):
    ok(sig + " re-checks HDMI", 'if (!hdmiNow(g)) return "panel_game_hdmi_only";' in body(mf, sig))

# ---- top-speed game refusals and the keystone coupling
ok("zoom refused in top-speed game", re.search(r"case Feature\.ZOOM:\s*if \(topSpeed\) \{ Notify\.show\(app, app\.getString\(R\.string\.panel_kst_game_mode\)\); return false; \}\s*OpticalZoomPanel\.open\(app, back\);", mf) is not None)
ok("auto keystone refused in top-speed game", 'if (topSpeedGame(g, g2)) return "panel_kst_game_mode";' in mf)
ok("restore refused in top speed", 'if (o != null && o.isTopSpeed()) return "panel_kst_game_mode";' in body(mf, "private String restore("))
ok("fit switch coupled", "Hal.setCurtainFitCoupled(g, on)" in mf)
ok("obstacle switch coupled", "Hal.setObstacleAvoidCoupled(g, on)" in mf)
ok("no direct fit / obstacle writes", re.search(r"\bg\.set(CurtainFit|ObstacleAvoid)\(", mf) is None)
ok("eye: no NUI reader, nothing sent", 'if (!GmpfClient.nuiReaderReady()) return "eye_unavailable";' in mf)

# ---- refusal notes exist as strings (the menu shows them by name)
res = set()
for d in (os.path.join(HB, "res"), os.path.join(GSI, "apps/Z9xProjector/res"), os.path.join(GSI, "apps/core/lumen-menu/res")):
    for f in glob.glob(os.path.join(d, "values", "*.xml")):
        res.update(re.findall(r'<string name="([a-z0-9_]+)"', read(f)))
reasons = set(re.findall(r'return "([a-z0-9_]+)";', mf)) | set(re.findall(r'refuse\(cb, "([a-z0-9_]+)"\)', mf)) \
    | set(re.findall(r'\? null : "([a-z0-9_]+)"', mf))
ok("refusal notes found", len(reasons) >= 6)
for r in sorted(reasons):
    ok("refusal note %s is a string" % r, r in res)

# ---- game menu io
ap_ = body(gi, "static int applyOnHal(")
ok("game io: the input is checked first", ap_.find("onPort(g, port);") >= 0 and ap_.find("onPort(g, port);") < ap_.find("switch (key)"))
ok("game io: no super resolution / game sound call", re.search(r"SuperResolution|GameSound", gi) is None)
ok("game io: sr / snd refused", 'throw new IllegalArgumentException("item " + key + " is not on the HB");' in ap_)
for case, key in (("case GmModel.VRR:", "GAME_VRR"), ("case GmModel.PIC:", "GAME_PICTURE"), ("case GmModel.DARK:", "GAME_DARK_BOOST")):
    seg = ap_[ap_.find(case):]
    seg = seg[:seg.find("case GmModel.", 5) if seg.find("case GmModel.", 5) > 0 else len(seg)]
    ok("game io: %s behind %s" % (case, key), "if (!HbFeatures.on(HbFeatures.F.%s)) throw" % key in seg)
ws = body(gi, "private int writeSpeed(")
ok("game io: VRR off before leaving top speed", ws.find("g2.setVrr(false, port)") >= 0 and ws.find("g2.setVrr(false, port)") < ws.find("g.setGameModeOption(o)"))
ok("game io: 38 only once 77 reads top speed", "if (o.isTopSpeed()) topSpeedKeystone(" in ws and "if (now == o) {" in ws)
ok("game io: running = 148 state 0", "p[0] == 0" in body(gi, "public void gameRunning("))

# ---- owner, keys, App, the old code gone
ins = body(hm, "public static void install(Context c)")
ok("game menu only with the profile and the unit", "p.gameMenu()" in ins and "!HbMenuFeatures.gameItems().isEmpty()" in ins
   and "GameMenu.install(c, on ? new HbGameIo(c) : null)" in ins)
ok("gear long through the profile", "get(c).onGearLong(fallback)" in body(hm, "public static void gearLong("))
ok("KeyReceiver: gear long = HbMenu.gearLong", "org.lumen.projector.panel.HbMenu.gearLong(ctx," in kr)
ok("KeyReceiver: no game bar", "GameBar." not in kr and "game.GameBar" not in kr)
ok("App installs the game menu after GameProfile", 0 <= ap.find("GameProfile.install(this)") < ap.find("HbMenu.install(this)"))
ok("QuickPanel controller is only a delegate (no HAL)", qp and "Hal." not in qp and "GmpfClient" not in qp and "HbMenu.get(app)" in qp)
for gone in ("game/GameBar.java", "game/FpsOverlay.java"):
    ok(gone + " removed", not os.path.exists(os.path.join(P, gone)))
src_all = "".join(read(f) for f in glob.glob(os.path.join(P, "**", "*.java"), recursive=True))
ok("no GameBar reference left", "GameBar." not in src_all and "FpsOverlay." not in src_all)
recipe = read(os.path.join(GSI, "devices/goat_elegant/app/HbProjector.layers"))
if recipe:
    ok("recipe: module lumen-menu", re.search(r"^module\s+lumen-menu$", recipe, re.M) is not None)
    ok("recipe: the HB profile", re.search(r"^file\s+devices/goat_elegant/menu\.json assets/lumen_menu/menu\.json$", recipe, re.M) is not None)
    ok("recipe: the Z9X controller dropped", re.search(r"^drop\s+src/org/lumen/projector/panel/QuickPanelController\.java$", recipe, re.M) is not None)
forks = read(os.path.join(GSI, "devices/goat_elegant/forks.tsv"))
if forks:
    ok("forks: no QuickPanelController row", "apps/HbProjector/src/org/lumen/projector/panel/QuickPanelController.java\t" not in forks)

if bad:
    print("check_wiring (menu): %d of %d FAILED" % (bad, n)); sys.exit(1)
print("check_wiring (menu): %d checks passed" % n)
