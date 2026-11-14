#!/usr/bin/env python3
"""Play 6 menu wiring (source level): the Play 6 adapter of the shared menu (panel/Play6Features) gates every entry
with implemented() = Play6MenuGates, takes the v3 hooks (blocked, confirm, available, setupDone) from Play6MenuGates
and asks nothing itself, refuses with Play6MenuGates.refusal before any p6-hal call, re-reads the mode or the input
on p6-hal right before every mode / input bound setter, names only notes and questions that exist as strings; the
device pages (Play6MenuPages) hide the rows of another mode and use the menu's pages API; the owner (Play6Menu)
builds the controller on use and drops it with its window unless a device screen comes back to it; QuickPanel,
KeyReceiver, the recipe and the dropped old panel.  python3 test/menu/check_wiring.py <app root>"""
import glob, json, os, re, sys

APP = os.path.abspath(sys.argv[1]) if len(sys.argv) > 1 else os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
GSI = os.environ.get("LUMEN_GSI") or os.path.abspath(os.path.join(APP, "..", ".."))
P = os.path.join(APP, "src/org/lumen/projector")
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


def case(src, label):
    """The text of one `case X:` arm (up to the next case / default at the same indent)."""
    i = src.find(label)
    if i < 0: return ""
    ind = src.rfind("\n", 0, i)
    pad = src[ind + 1:i]
    m = re.compile(r"\n" + re.escape(pad) + r"(case |default:)").search(src, i + len(label))
    return src[i:m.start() if m else len(src)]


def read(p):
    return open(p, encoding="utf-8").read() if os.path.exists(p) else ""


def before(src, a, b):
    """a occurs, and before the first b."""
    i, j = src.find(a), src.find(b)
    return i >= 0 and j >= 0 and i < j


# the adapter and its sections (panel/Play6*Rows) with their HAL reads, read as one text
mf = "\n".join(read(f) for f in [os.path.join(P, "panel/Play6Features.java")] + sorted(glob.glob(os.path.join(P, "panel/Play6*Rows.java")))
               + [os.path.join(P, "utils/Play6HalReads.java")])
mg = read(os.path.join(P, "panel/Play6MenuGates.java"))
mp = read(os.path.join(P, "panel/Play6MenuPages.java"))
mo = read(os.path.join(P, "panel/Play6Menu.java"))
qp = read(os.path.join(P, "panel/QuickPanel.java"))
kr = read(os.path.join(P, "KeyReceiver.java"))
ok("adapter present", bool(mf) and bool(mg) and bool(mp) and bool(mo))

# ---- gates at every entry
ok("implemented() = Play6MenuGates.implemented", "return Play6MenuGates.implemented();" in body(mf, "public Set<FeatureId> implemented()"))
ok("available() = Play6MenuGates.available with the NUI reader", "Play6MenuGates.available(feature, eyeReady," in body(mf, "public boolean available(FeatureId feature)"))
ok("setupDone() = the first-run setup", "KeyReceiver.isSetupComplete(app)" in body(mf, "public boolean setupDone()"))
ok("blocked() = Play6MenuGates.blocked on the last read", "Play6MenuGates.blocked(feature, value, last)" in body(mf, "public Note blocked(FeatureId feature, String value, MenuState now)"))
ok("confirm() = Play6MenuGates.confirm on the last read", re.search(r"now\)\s*\{\s*return Play6MenuGates\.confirm\(feature, value, last,",
   body(mf, "public Confirm confirm(FeatureId feature, String value, MenuState now)")) is not None)
sc = body(mf, "public void setConfirmed(FeatureId feature, String value, Consumer<Result> cb)")
ok("setConfirmed: power saving asked once, then the same write", before(sc, "putBoolean(", "set(feature, value, cb)"))
ok("the adapter asks nothing itself (the menu asks, CONTRACT.md 10)", "DialogPanel" not in mf and "confirmPage" not in mf and "OverlayHost" not in mf)
for sig, guard in (("public void set(FeatureId feature, String value, Consumer<Result> cb)", "if (!implemented().contains(feature)"),
                   ("public void run(FeatureId action, Consumer<Result> cb)", "if (!implemented().contains(action))"),
                   ("public boolean open(FeatureId screen, Runnable back)", "if (!implemented().contains(screen)) return false;")):
    b = body(mf, sig)
    first = "for (Play6Rows s : sections)" if "for (Play6Rows s : sections)" in b else "switch ("   # the sections or the switch
    ok(sig.split("(")[0].split()[-1] + ": the gate comes first", b and before(b, guard, first))
st = body(mf, "public void set(FeatureId feature, String value, Consumer<Result> cb)")
ok("set: Play6MenuGates.refusal before any write", before(st, "Play6MenuGates.refusal(feature, r)", "switch (")
   and before(st, "Play6MenuGates.refusal(feature, r)", "write("))
ok("set: not ready answers without a call", before(st, "if (!Hal.isReady())", "Play6MenuGates.refusal("))
ok("read: the state is Play6MenuGates.state", "cb.accept(state());" in body(mf, "public void read(Consumer<MenuState> cb)")
   and "Play6MenuGates.state(last, this::text)" in body(mf, "MenuState state()"))
ok("write: every write reads everything back", "Play6Snapshot.read(g, g2, PARTS)" in body(mf, "void write(String what, Consumer<Result> cb, Write w)"))

# ---- p6-hal re-checks right before the setter (the menu's read may be old)
# the HAL-thread writes: the router of the adapter and the writeHal of every section
WH = "String writeHal(FeatureId feature, String value, boolean on, Play6Gmpf g, Play6Gmpf2 g2)"
wh = "\n".join(body(mf[i:], WH) for i in [m.start() for m in re.finditer(re.escape(WH), mf)])
for label, check, call in (
        ("case EYE:", "Device.hasEyeProtection()", "g.setEyeProtection("),
        ("case FOCUS_SMART:", "g2.isIdentityOpen()", "Hal.setSmartAutofocus("),
        ("case LAMP:", "Play6MenuGates.lampValue(", "g.setLampLevel("),
        ("case PICTURE_COLOR_TEMP:", "Play6MenuGates.custom(g.getPictureModeRaw())", "g.setColorTemperature("),
        ("case PICTURE_MEMC:", "Play6MenuGates.memcMode(g.getPictureModeRaw())", "g.setMemcLevel("),
        ("case PICTURE_DYNAMIC_CONTRAST:", "Play6MenuGates.custom(g.getPictureModeRaw())", "g2.setDynamicContrast("),
        ("case PICTURE_DYNAMIC_CONTRAST:", "g2.isIdentityOpen()", "g2.setDynamicContrast("),
        ("case HDMI_RANGE:", "hdmiNow(g,", "g.setHdmiRange("),
        ("case PICTURE_ASPECT:", "hdmiNow(g,", "g.setAspectRatio("),
        ("case PICTURE_ASPECT:", "Play6MenuGates.originalFits(g.getHdmiInfo())", "g.setAspectRatio("),
        ("case GAME_MODE:", "hdmiNow(g,", "GameMode.set(")):
    ok(label + " " + check + " before " + call, before(case(wh, label), check, call))
pq = body(mf, "String setPq(String item, String value, Play6Gmpf g)")
ok("pq: Custom re-read before the setters", before(pq, "Play6MenuGates.custom(g.getPictureModeRaw())", "g.setPq(PqItem.BRIGHTNESS, "))
ok("pq: range checked", before(pq, "v < PQ_MIN || v > PQ_MAX", "g.setPq(PqItem.BRIGHTNESS, "))
pm = body(mf, "void setPictureMode(String value, Play6MenuGates.Read r, Consumer<Result> cb)")
ok("picture mode: Game re-read before 667", before(pm, "Play6MenuGates.gamePicture(g.getPictureModeRaw())", "g.setPictureMode("))
ed = body(mf, "void setEdid(String value, Play6MenuGates.Read r, Consumer<Result> cb)")
ok("HDMI mode: HDMI re-read before 660", before(ed, "hdmiNow(g,", "g.setEdidVersion("))
rs = case(mf, "case PICTURE_RESET:")                           # the run of the picture section
ok("picture reset: Custom re-read before 658", before(rs, "Play6MenuGates.custom(g.getPictureModeRaw())", "g.resetCustomPicture()"))
ok("power saving: battery model only", before(body(mf, "void setEco(boolean on, Consumer<Result> cb)"), "Device.hasBattery()", "g.setEcoState("))
ok("projection: 580(false) right before 172", re.search(r"g\.setAutoReverse\(false\);[^\n]*\n\s*g\.setPutMode\(Play6Values\.putMode\(target\)\);", body(mf, "void setProjection(")) is not None)

# ---- refusal notes are strings of the app
notes = set(re.findall(r'refuse\(cb, "([a-z0-9_]+)"\)', mf)) | set(re.findall(r'return "([a-z0-9_]+)";', mf))
notes |= set(re.findall(r'return "([a-z0-9_]+)";', body(mg, "static String refusal(FeatureId feature, Read r)")))
notes |= set(re.findall(r'\? null : "([a-z0-9_]+)"', mf + body(mg, "static String refusal(FeatureId feature, Read r)")))
names = set()
for d in [os.path.join(APP, "res")] + [os.path.join(GSI, x) for x in ("apps/Play6Projector/res", "apps/common/projector/res", "apps/core/lumen-menu/res")]:
    for f in glob.glob(os.path.join(d, "values", "*.xml")):
        names |= set(re.findall(r'<string name="([a-z0-9_]+)"', read(f)))
notes |= set(re.findall(r'Note\.of\("([a-z0-9_]+)"\)', mf + mg))
ok("refusal notes found", len(notes) >= 3)
for x in sorted(notes):
    ok("refusal note " + x + " is a string", x in names)
asks = re.findall(r'new Confirm\("([a-z0-9_]+)", "([a-z0-9_]+)", "([a-z0-9_]+)"\)', mg)
ok("stock questions found", len(asks) >= 8)
for q in asks:
    for x in q: ok("question text " + x + " is a string", x in names)
ok("the Game hint is a string", "p6_pic_game_hint" in names and 'text.apply("p6_pic_game_hint")' in mg)

# ---- the profile: every id is named by the adapter or its pages
prof = json.load(open(os.path.join(GSI, "devices/dragon_healthy/menu.json"))) if os.path.exists(os.path.join(GSI, "devices/dragon_healthy/menu.json")) \
    else json.load(open(os.path.join(APP, "assets/lumen_menu/menu.json")))
FID = "src/org/lumen/menu/FeatureId.java"
consts = dict(re.findall(r'^    ([A-Z0-9_]+)\("([a-z0-9_.]+)", FeatureKind', read(os.path.join(GSI, "apps/core/lumen-menu", FID))
                         or read(os.path.join(APP, FID)), re.M))
consts.update(re.findall(r'([A-Z_]+) = "([a-z0-9_.]+)"', mg))
by_id = {v: k for k, v in consts.items()}
ids = set(prof["features"]) | {x.split(":")[0] for x in prof.get("pending", [])}
for i in sorted(ids):
    k = by_id.get(i)
    ok(i + ": a constant", k is not None)
    if k: ok(i + ": named by the adapter or its pages", re.search(r"\b" + k + r"\b", mf + mp) is not None)
ok("no game menu on the Play 6", not prof.get("game_menu", {}).get("enabled") and prof.get("keys", {}).get("gear_long") == "none")
ok("Play 6 has no optical zoom / Boost", "zoom" not in prof["features"] and "boost" not in prof["features"])

# ---- pages: the device pages and their rule
pg = body(mp, "public Page page(FeatureId id, MenuController menu)")
for k in ("case PICTURE_PRO:", "case SOUND_EQ:", "case SETTINGS_AMBIENT_SPEAKER:"):
    ok("page " + k, k in pg)
    ob = body(mf, "public boolean open(FeatureId screen, Runnable back)")
    arm = ob[ob.find("case PICTURE_PRO:"):]
    ok("open " + k + " is not a screen", ob.find("case PICTURE_PRO:") >= 0 and k in arm[:arm.find("return false;")])
ap = body(mp, "private void applyPro()")
ok("pro rows of another mode are GONE (not disabled)", "visible(wbRows[k], v != null)" in ap and "choiceIf(nrRow, custom ? sn.nr : null, 4)" in ap
   and "choiceIf(lcRow, memcOk ? sn.localContrast : null, 3)" in ap and "visible(hdrRow, hdr != null)" in ap)
ok("pages use the menu's pages API", "menu.refreshPage(" in mp and "menu.closeThen(" in mp and "Play6Features.shown" not in mp)
ok("pro writes re-check the mode", body(mp, "private void onWb(int item, int value)").find("if (!custom()") >= 0
   and body(mp, "private void onRgb(int item, int value)").find("if (!customStyle()") >= 0)

# ---- built on use, dropped with its window, gear long from the profile
ok("owner builds the controller with the Play 6 adapter and pages", "new MenuController(app, new Play6Features(app, eye), new Play6MenuPages(app))" in mo)
rc = body(mo, "private static void releaseIfClosed(Context app)")
ok("owner drops the controller when its window is gone", "sMenu = null;" in rc and "onViewDetachedFromWindow" in body(mo, "private static void watch(Context app)"))
ok("owner keeps it while a device screen comes back to it", "sHeld" in rc.split("sMenu = null;")[0]
   and "sHeld = true;" in body(mo, "static Runnable holdFor(Context c, Runnable back)"))
ob = body(mf, "public boolean open(FeatureId screen, Runnable back)")
for k in ("ManualKeystonePanel.open(app, Play6Menu.holdFor(app, back))", "Play6RotatePanel.open(app, Play6Menu.holdFor(app, back))",
          "Play6ZoomShiftPanel.open(app, Play6Menu.holdFor(app, back))"):
    ok("screen comes back through the menu: " + k.split(".")[0], k in ob)
ok("owner closes the old menu on a language change", "sMenu.close();" in body(mo, "static MenuController get(Context c)"))
ok("owner: language / eye change builds again", "!now.equals(sLocale) || eye != sEye" in mo)
gl = body(mo, "public static void gearLong(Context c, Runnable fallback)")
ok("gear long: profile none = the fallback without building the menu", before(gl, '"none".equals(p.gearLong())', "get(c).onGearLong("))

# ---- callers
ok("QuickPanel: toggle and show go to the shared menu", "Play6Menu.toggle(ctx)" in body(qp, "public static void toggle(Context ctx)")
   and "Play6Menu.show(app, sectionOf(section))" in body(qp, "public static void show(Context ctx, String section)"))
ok("QuickPanel: refreshIfShowing re-reads the menu", "Play6Menu.refreshShown()" in body(qp, "public static void refreshIfShowing()"))
ok("QuickPanel: extension rows reach the shared menu", "MenuController.addExtension(sec, ext, Play6Menu.built())" in body(qp, "public static void addExtension(String section, Extension ext)")
   and "interface Extension extends MenuController.Extension" in qp)
ok("KeyReceiver: gear long through the profile", "Play6Menu.gearLong(ctx," in kr)
# screen mirroring: the module builds the tile only where the receiver's provider resolves (the receiver switches it off
# on a unit without Wi-Fi Direct); Lumen Home's tile follows the same provider through the caps of the device state
dv = read(os.path.join(P, "Device.java"))
ds = read(os.path.join(P, "device/DeviceStateProvider.java"))
ok("mirroring: implemented, the menu's own popup", "FeatureId.MIRRORING" in mg and "case MIRRORING:" in ob
   and "return false;" in case(ob, "case MIRRORING:"))
ok("Home caps: the mirroring tile goes with the receiver's provider",
   "if (!screenMirroring(c)) off.add(HOME_TILE_MIRRORING);" in dv and 'MIRRORING_AUTHORITY = "org.lumen.miracast"' in dv
   and "resolveContentProvider(MIRRORING_AUTHORITY, 0) != null" in dv and 'HOME_TILE_MIRRORING = "miracast"' in dv
   and "Device.unsupportedHomeTiles(getContext())" in ds)

# ---- the old panel is gone, the recipe takes the module
ok("old panel dropped", not os.path.exists(os.path.join(P, "panel/Play6PanelController.java")))
src_all = "".join(read(f) for f in glob.glob(os.path.join(P, "**/*.java"), recursive=True))
ok("nothing names the old panel class", re.search(r"\bPlay6PanelController\s*[.(]", src_all) is None and "new Play6PanelController" not in src_all)
lay = read(os.path.join(GSI, "devices/dragon_healthy/app/Play6Projector.layers"))
if lay:
    ok("recipe: module lumen-menu", re.search(r"^module\s+lumen-menu\s*$", lay, re.M) is not None)
    ok("recipe: the Play 6 profile", re.search(r"^file\s+devices/dragon_healthy/menu.json assets/lumen_menu/menu.json\s*$", lay, re.M) is not None)
    forks = read(os.path.join(GSI, "tools/play6/forks.tsv"))
    ok("forks.tsv: no row of the old panel", not any(l.split("\t")[0].endswith("/Play6PanelController.java") for l in forks.splitlines()))

print("play6 menu wiring: %d checks, %d failed" % (n, bad))
sys.exit(1 if bad else 0)
