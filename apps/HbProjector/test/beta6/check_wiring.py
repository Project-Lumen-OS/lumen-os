#!/usr/bin/env python3
"""HB beta6 wiring checks (source level, the HB app tree): Boost rules used where the vendor refuses 52(0), the stock
HB Boost line in every locale, the optical zoom tile, the zoom screen on ZoomScale, and the one-way fit / obstacle
coupling (stock spec 2.3, stock KeyStoneRepository.java:506-521) in all four directions, run on the real Hal source."""
import os, re, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
HB = os.path.abspath(os.path.join(HERE, "..", ".."))
APPS = os.path.dirname(HB)
QP = os.path.join(HB, "src/org/lumen/projector/panel/QuickPanelController.java")
OZ = os.path.join(HB, "src/org/lumen/projector/panel/OpticalZoomPanel.java")
SA = os.path.join(HB, "src/org/lumen/projector/SettingsActivity.java")
HAL = os.path.join(HB, "src/org/lumen/projector/Hal.java")
if not os.path.exists(HAL):                       # the HB app takes Hal.java from its recipe base
    HAL = os.path.join(APPS, "Z9xProjector/src/org/lumen/projector/Hal.java")
LOCALES = ["values", "values-ar", "values-be", "values-de", "values-es", "values-fr", "values-it", "values-ja",
           "values-kk", "values-ko", "values-pl", "values-pt", "values-pt-rBR", "values-ru", "values-tr", "values-uk",
           "values-zh-rCN", "values-zh-rTW"]
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

qp = open(QP, encoding="utf-8").read()
# ---- Boost (live beta5 bug)
onkey = body(qp, "boolean onKey(KeyEvent ev)")
ok("onKey asks BoostRules before arming", "BoostRules.onLeavePress(" in onkey and "EXPLAIN_PICTURE_MODE" in onkey)
ok("onKey explains without a vendor call", re.search(r"EXPLAIN_PICTURE_MODE\)\s*\{[^}]*Notify\.show", onkey, re.S) is not None
   and "boostPress(-1)" in onkey)
sb = body(qp, "private void setBoost(boolean on)")
ok("setBoost judges by the 152 read-back", "BoostRules.judge(on, ret, lensAfter, pmAfter)" in sb and "g2.isBoost()" in sb)
ok("setBoost reads the picture mode after", "g.getPictureModeRaw()" in sb)
ok("setBoost explains a picture mode lock", "LOCKED_BY_PICTURE_MODE" in sb and "boostLockedText(pmAfter)" in sb)
ok("setBoost fails only on FAILED", "Outcome.FAILED) throw" in sb)
ok("setBoost no longer throws on the boolean", 'throw new IllegalStateException("52 returned false")' not in sb)
pmc = body(qp, "private void onPictureModeChosen(int i)")
ok("colour accuracy uses BoostRules", "BoostRules.blocksColorAccuracy(snap.boost, snap.pictureMode)" in pmc)
ok("no raw boost block for colour accuracy", "COLOR_ACCURACY && Boolean.TRUE.equals(snap.boost)" not in pmc)
ok("brightness page shows the lock line", "boostLockHint" in qp and "BoostRules.lockedByPictureMode(sn.pictureMode)" in qp)
ok("Boost line is the HB stock text", "R.string.hb_boost_effect" in qp and "R.string.boost_effect" not in qp)
# ---- strings in every locale
for loc in LOCALES:
    f = os.path.join(HB, "res", loc, "strings_hb.xml")
    x = open(f, encoding="utf-8").read() if os.path.exists(f) else ""
    eff = re.search(r'<string name="hb_boost_effect">([^<]+)</string>', x)
    lock = re.search(r'<string name="hb_boost_locked">([^<]+)</string>', x)
    ok(loc + " hb_boost_effect", eff is not None and "Boost" in eff.group(1))
    ok(loc + " hb_boost_locked has the mode", lock is not None and "%1$s" in lock.group(1) and "Boost" in lock.group(1))
    if loc == "values" and eff:
        t = eff.group(1).lower()
        ok("en Boost line: higher brightness", "brightness" in t)
        ok("en Boost line: no paler-colours claim", "pale" not in t and "colour space" not in t and "color space" not in t)
    if loc == "values-ru" and eff:
        t = eff.group(1)
        ok("ru Boost line: no old claims", "бледн" not in t and "Цветовое пространство" not in t)
# ---- zoom tile (stock quick menu row 102: scale, then brightness)
root = body(qp, "private void buildRoot(")
if not root:
    m = re.search(r"grid = p\.add\(new TileGrid\(app\)\);(.*?)addTile\(QuickPanel\.SECTION_GENERAL", qp, re.S)
    root = m.group(1) if m else ""
iz = root.find("addTile(SECTION_ZOOM_TILE"); il = root.find("addTile(QuickPanel.SECTION_LAMP"); ik = root.find("addTile(QuickPanel.SECTION_KEYSTONE")
ok("zoom tile exists", iz >= 0)
ok("zoom tile after keystone, before brightness", 0 <= ik < iz < il)
ok("zoom tile behind OPTICAL_ZOOM", re.search(r"if \(HbFeatures\.on\(F\.OPTICAL_ZOOM\)\) addTile\(SECTION_ZOOM_TILE", root) is not None)
ot = body(qp, "private void onTile(String section)")
zt = ot[ot.find("SECTION_ZOOM_TILE.equals(section)"):]
ok("zoom tile opens the optical zoom screen", "OpticalZoomPanel.open(app" in zt[:600])
ok("zoom tile refused in top-speed game", "isTopSpeedGame()" in zt[:600])
ok("zoom tile icon", os.path.exists(os.path.join(HB, "res/drawable/ic_tile_zoom.xml")))
# ---- zoom screen on ZoomScale
oz = open(OZ, encoding="utf-8").read()
ok("zoom: scale from 97 + 63(12,10)", "new ZoomScale(range[2], lim)" in oz)
ok("zoom: moves from ZoomScale", "scale.move(now, dir)" in oz)
ok("zoom: refused end dropped", "scale.refusedAtEnd(now, dir)" in oz and "r != 0 && after == now" in oz)
ok("zoom: counter from ZoomScale", "sc.sizeNumber(p), sc.positions()" in oz)
ok("zoom: progress from ZoomScale", "scale.progress(cur, lim)" in oz)
# ---- manual focus chart (stock FocusAdjust full-screen cp01_focus_bk)
MFA = os.path.join(HB, "src/org/lumen/projector/ManualFocusActivity.java")
mf = open(MFA, encoding="utf-8").read()
oc = body(mf, "protected void onCreate(Bundle b)")
ok("manual focus: chart added behind the panel", "addView(new FocusPatternView(this), 0," in oc and "MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT" in oc)
ok("manual focus: chart after setContentView", oc.find("setContentView(R.layout.activity_manual_focus)") < oc.find("new FocusPatternView"))
dk = body(mf, "public boolean dispatchKeyEvent(KeyEvent ev)")
ok("manual focus: other keys still close (stock)", "motor.stop();" in dk and "finish();" in dk and "KEYCODE_DPAD_LEFT" in dk)
fv = open(os.path.join(HB, "src/org/lumen/projector/FocusPatternView.java"), encoding="utf-8").read()
ok("chart view draws every wheel and the line", "FocusPattern.wheels(w, h)" in fv and "FocusPattern.line(w, h)" in fv and "c.drawRect(l[0], l[1], l[2], l[3], ink)" in fv and "drawColor(FocusPattern.BACKGROUND)" in fv)
ok("no XGIMI chart asset copied", not any(os.path.exists(os.path.join(HB, "res", d, "cp01_focus_bk.webp")) for d in ("drawable", "drawable-nodpi")))
# ---- deep sleep blocked notice (StrNotice)
SN = open(os.path.join(HB, "src/org/lumen/projector/power/StrNotice.java"), encoding="utf-8").read()
tr = body(SN, "static int textRes(StrNoticeRules.Kind k)")
for kind, res in (("USB", "hb_str_blocked_usb"), ("UPDATE", "hb_str_blocked_update"), ("UPDATE_CHECK", "hb_str_blocked_update_check")):
    ok("str notice: " + kind + " text", re.search(r"case " + kind + r": return R\.string\." + res + ";", tr) is not None)
ok("str notice: kill switch text", "default: return R.string.hb_str_blocked_off;" in tr)
shw = body(SN, "private static void show(Context app, StrNoticeRules.Kind k, String why)")
ok("str notice: never in standby", "if (StandbyController.isActive()) return;" in shw)
ok("str notice: never with the screen off", "!pm.isInteractive()) return;" in shw)
ok("str notice: Notify title + reason", "Notify.show(app, app.getString(R.string.hb_str_blocked_title), app.getString(textRes(k)))" in shw)
ok("str notice: usb once per connection", "StrNoticeRules.usbNoticeDue(k, sUsbShown)" in SN and "sUsbShown = k == StrNoticeRules.Kind.USB;" in SN)
ok("str notice: power menu by PowerKey.LONG_PRESS_MS", "PowerKey.LONG_PRESS_MS" in SN)
kr = open(os.path.join(HB, "src/org/lumen/projector/KeyReceiver.java"), encoding="utf-8").read()
i1 = kr.find("org.lumen.projector.power.StrNotice.onGlobalKey(ctx, ev);"); i2 = kr.find("org.lumen.projector.power.PowerUi.onGlobalKey(ctx, intent, ev)")
ok("str notice: KeyReceiver before PowerUi", 0 <= i1 < i2)
apsrc = open(os.path.join(HB, "src/org/lumen/projector/App.java"), encoding="utf-8").read()
ok("str notice: installed after PowerPolicy", 0 <= apsrc.find("PowerPolicy.install(this)") < apsrc.find("StrNotice.install(this)"))
for loc in LOCALES:
    f = os.path.join(HB, "res", loc, "strings_hb.xml")
    x = open(f, encoding="utf-8").read() if os.path.exists(f) else ""
    for nm in ("hb_str_blocked_title", "hb_str_blocked_usb", "hb_str_blocked_update", "hb_str_blocked_update_check", "hb_str_blocked_off"):
        m = re.search(r'<string name="%s">([^<]+)</string>' % nm, x)
        ok(loc + " " + nm, m is not None and len(m.group(1).strip()) > 3)
    m = re.search(r'<string name="hb_str_blocked_usb">([^<]+)</string>', x)
    ok(loc + " usb text names USB", m is not None and "USB" in m.group(1))
# ---- fit / obstacle coupling: the panel rows and SettingsActivity use only the coupled setters
ok("panel obstacle row coupled", "Hal.setObstacleAvoidCoupled(g, w)" in qp)
ok("panel fit row coupled", "Hal.setCurtainFitCoupled(g, w)" in qp)
for name, path in (("panel", QP), ("settings", SA)):
    s = open(path, encoding="utf-8").read()
    ok(name + ": no direct 290 / 292 writes", re.search(r"\.set(CurtainFit|ObstacleAvoid)\(", s) is None)
hal = open(HAL, encoding="utf-8").read()
fit = body(hal, "public static void setCurtainFitCoupled(GmpfClient g, boolean on)")
obs = body(hal, "public static void setObstacleAvoidCoupled(GmpfClient g, boolean on)")
ok("Hal coupled setters found", bool(fit) and bool(obs))
java = """import java.util.*;
class GmpfClient { List<String> log = new ArrayList<>();
  void setCurtainFit(boolean on) { log.add("290 " + on); }
  void setObstacleAvoid(boolean on) { log.add("292 " + on); } }
public class CouplingRun {
  %s
  %s
  public static void main(String[] a) throws Exception {
    String[][] cases = { {"fit", "true", "[290 true, 292 true]"}, {"fit", "false", "[290 false]"},
                         {"obstacle", "false", "[292 false, 290 false]"}, {"obstacle", "true", "[292 true]"} };
    int bad = 0;
    for (String[] c : cases) {
      GmpfClient g = new GmpfClient(); boolean on = Boolean.parseBoolean(c[1]);
      if (c[0].equals("fit")) setCurtainFitCoupled(g, on); else setObstacleAvoidCoupled(g, on);
      String got = g.log.toString();
      if (!got.equals(c[2])) { bad++; System.out.println("FAIL coupling " + c[0] + " " + c[1] + ": " + got + " want " + c[2]); }
      else System.out.println("coupling " + c[0] + " " + c[1] + " -> " + got);
    }
    System.exit(bad == 0 ? 0 : 1);
  } }
""" % (fit, obs)
d = tempfile.mkdtemp()
open(os.path.join(d, "CouplingRun.java"), "w").write(java)
jh = os.environ.get("JAVA_HOME") or subprocess.run(["/usr/libexec/java_home"], capture_output=True, text=True).stdout.strip()
r = subprocess.run([os.path.join(jh, "bin/javac"), "-nowarn", "-d", d, os.path.join(d, "CouplingRun.java")], capture_output=True, text=True)
ok("coupling compiles: " + r.stderr.strip()[:200], r.returncode == 0)
if r.returncode == 0:
    r = subprocess.run([os.path.join(jh, "bin/java"), "-cp", d, "CouplingRun"], capture_output=True, text=True)
    sys.stdout.write(r.stdout)
    ok("coupling: all four directions as FEATURE_SPEC 2.3 / stock", r.returncode == 0)
print("check_wiring: %d/%d passed" % (n - bad, n))
sys.exit(1 if bad else 0)
