#!/bin/sh
# Real-time game menu controller (Gm3dModel / Gm3dMotion / Gm3dGate are plain Java): sh test/gm3d/run.sh
# Gm3dTest reads the shipped models (assets/gm3d) and checks the reader, framing, motion and the model/strip gate.
# The checks below pin the Android wiring the gate relies on: the strip is drawn only when the gate says so, every
# failure and the first-frame timeout reach the strip, the model is off by the property, GL goes when the menu closes.
set -eu
T=$(cd "$(dirname "$0")" && pwd)
M=$(cd "$T/../.." && pwd)                           # apps/core/lumen-menu
P="$M/src/org/lumen/projector/panel"
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17)}
OUT=$(mktemp -d "${TMPDIR:-/tmp}/gm3d_test.XXXXXX")
trap 'rm -rf "$OUT"' EXIT
"$JAVA_HOME/bin/javac" -encoding UTF-8 -nowarn -proc:none -d "$OUT" \
  "$P/GmModel.java" "$P/Gm3dModel.java" "$P/Gm3dMotion.java" "$P/Gm3dGate.java" "$T/Gm3dTest.java"
bad=0
"$JAVA_HOME/bin/java" -cp "$OUT" org.lumen.projector.panel.Gm3dTest "$M/assets/gm3d" || bad=1
fail() { echo "FAIL $1"; bad=1; }
has() { grep -qF -- "$2" "$1" || fail "$3"; }
V="$P/GmView.java"; S="$P/GmStage.java"; G="$P/Gm3dView.java"; R="$P/Gm3dRenderer.java"; C="$P/GmController.java"
# ---- the card: the strip's bitmap only when the gate is on the strip (or there is no model)
has "$V" 'if (GmModel.isConsole(ctl.theme) && (model == null || gate.frames() || gate.pending())) {' "strip drawn while the model shows"
has "$V" 'if (model != null && gate.begin(theme, true, isHardwareAccelerated(), now)) {' "model started without the gate or the GPU check"
grep -A1 -F 'if (model != null && gate.begin(theme, true, isHardwareAccelerated(), now)) {' "$V" | grep -qF 'frames.startHero(theme);' \
  || fail "the whole strip decoded while the model draws (only the hero frame holds the card)"
grep -A3 -F 'void startHero(String theme) {' "$V" | grep -qF 'release();' || fail "hero frame load keeps the old strip"
grep -A2 -F '@Override public void onModelReady(String theme) {' "$V" | grep -qF 'if (gate.live()) frames.release();' || fail "hero frame kept under the model"
grep -A6 -F 'if (model != null && gate.begin(theme, true, isHardwareAccelerated(), now)) {' "$V" | grep -qF 'frames.start(theme);' \
  || fail "no strip when the gate refuses the model"
has "$V" 'if (gate.timedOut(now)) toFrames("no first frame in " + Gm3dGate.READY_MS + " ms");' "first-frame timeout does not start the strip"
has "$V" 'if (gate.failed(theme, hard)) toFrames(why);' "a model failure does not start the strip"
has "$V" '@Override public void onModelReady(String theme) {' "model ready not reported to the gate"
grep -A1 -F '@Override public void onModelReady(String theme) {' "$V" | grep -qF 'gate.ready(theme);' || fail "ready not passed to the gate"
grep -A4 -F 'private void toFrames(String why) {' "$V" | grep -qF 'frames.start(framesTheme);' || fail "toFrames does not start the strip"
has "$V" 'if (gate.pending()) postInvalidateDelayed(50);' "the first-frame wait is not checked while nothing else redraws"
has "$V" 'boolean strip = GmModel.isConsole(ctl.theme) && gate.frames();' "the strip timer runs while the model draws"
grep -A3 -F 'void release() {' "$V" | grep -qF 'if (model != null) model.release();' || fail "menu close does not release GL"
has "$V" 'if (!GmModel.isConsole(theme)) {' "computer theme starts the model"
grep -A4 -F 'if (!GmModel.isConsole(theme)) {' "$V" | grep -qF 'if (model != null) model.hide();' || fail "computer icon over a running model"
has "$V" 'model.dim(ctl.confirmOk != null);' "model not dimmed under the Turbo question"
has "$V" 'return getParent() instanceof GmStage ? (View) getParent() : this;' "model does not fade with the card"
# ---- the stage: no model when switched off or after a hard GL failure; the model sits on the picture slot
has "$S" 'if (!Gm3dView.enabled() || Gm3dGate.sBroken) return v;' "model built although switched off or broken"
has "$S" 'static final float SLOT_L = 50, SLOT_T = 54, SLOT_W = 250, SLOT_H = 150;' "model not on the card's picture slot"
grep -A2 -F 'RectF slot = new RectF(x0 + 10, y0 + 14, x0 + 260, y0 + 164);' "$V" >/dev/null || fail "card slot moved: update GmStage.SLOT_*"
grep -qF 'float x0 = 40, y0 = 40, w = 10 + 250 + 22 + textW + 30, h = 178;' "$V" || fail "card origin moved: update GmStage.SLOT_*"
has "$C" 'root = GmStage.wrap(app, view);' "menu content is not the stage"
# ---- the view: property, failures, pause, release
has "$G" 'static final String PROP = "debug.lumen.gm3d";' "kill switch property renamed"
has "$G" 'return !"0".equals(v);' "kill switch does not switch the model off"
has "$G" 'fail("model " + th + ": " + e, false);' "a bad model file is not a (soft) failure"
has "$G" 'fail("gl: " + e, true);' "a GL set-up failure is not a hard failure"
has "$G" 'fail("frame: " + e, true);' "a GL frame failure is not a hard failure"
grep -A5 -F 'private void fail(String why, boolean hard) {' "$G" | grep -qF 'main.post(() -> listener.onModelFailed(th, why, hard));' \
  || fail "a failure does not reach the menu"
grep -A2 -F 'public void onVisibilityAggregated(boolean visible) {' "$G" | grep -qF 's.setRunning(visible)' || fail "hidden model keeps drawing"
grep -A3 -F 'void release() {' "$G" | grep -qF 'gl().post(s::destroy);' || fail "release does not destroy the GL session"
grep -A12 -F 'private void releaseGl() {' "$G" | grep -qF 'EGL14.eglDestroyContext(dpy, ctx);' || fail "the EGL context outlives the menu"
grep -A12 -F 'private void releaseGl() {' "$G" | grep -qF 'if (r != null && current()) r.release();' || fail "GL objects outlive the menu"
grep -qF 'eglTerminate' "$G" && fail "eglTerminate would cut the UI's own EGL connection"
has "$G" 'if (sGl == null) {' "one GL thread per menu"
has "$G" 'return false;' "the TextureView frees the surface while the GL thread may still use it"
has "$R" 'GLES30.glInvalidateFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 2, DISCARD, 0);' "multisample data written back each frame"
# ---- assets: the shaders and every model in the APK when one is given
for f in model.vert model.frag env.lge ps.lgm xbox.lgm switch.lgm pad.lgm; do
  [ -s "$M/assets/gm3d/$f" ] || fail "assets/gm3d/$f missing"
done
if [ -n "${GM_APK:-}" ]; then
  for f in model.vert model.frag env.lge ps.lgm xbox.lgm switch.lgm pad.lgm; do
    unzip -p "$GM_APK" "assets/gm3d/$f" > "$OUT/apk_$f" 2>/dev/null || { fail "$GM_APK lacks assets/gm3d/$f"; continue; }
    cmp -s "$OUT/apk_$f" "$M/assets/gm3d/$f" || fail "assets/gm3d/$f in $GM_APK differs"
  done
fi
[ $bad = 0 ] && echo "gm3d wiring: ok"
exit $bad
