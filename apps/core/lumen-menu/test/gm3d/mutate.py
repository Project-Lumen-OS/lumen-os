#!/usr/bin/env python3
"""Mutants of the real-time game menu controller and its fallback to the frame strip: each must turn test/gm3d red.
python3 test/gm3d/mutate.py [-k substring]"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.normpath(os.path.join(HERE, '..', '..'))
P = 'src/org/lumen/projector/panel/'
GATE, MODEL, MOTION, VIEW, STAGE, GLV, REND = (P + f for f in (
    'Gm3dGate.java', 'Gm3dModel.java', 'Gm3dMotion.java', 'GmView.java', 'GmStage.java', 'Gm3dView.java', 'Gm3dRenderer.java'))

MUTANTS = [
    # ---- gate: the strip takes over on every failure, the model never draws over a strip
    ('model tried without GPU drawing', GATE, 'if (!enabled || !gpu || failedHere || sBroken) {', 'if (!enabled || failedHere || sBroken) {'),
    ('model retried in a menu that failed', GATE, 'if (!enabled || !gpu || failedHere || sBroken) {', 'if (!enabled || !gpu || sBroken) {'),
    ('model retried after a hard GL failure', GATE, 'if (!enabled || !gpu || failedHere || sBroken) {', 'if (!enabled || !gpu || failedHere) {'),
    ('model used although switched off', GATE, 'if (!enabled || !gpu || failedHere || sBroken) {', 'if (!gpu || failedHere || sBroken) {'),
    ('hard failure not process wide', GATE, '        if (hard) sBroken = true;\n', ''),
    ('a failure forgotten for the next theme', GATE, '        failedHere = true;\n        if (hard) sBroken = true;', '        if (hard) sBroken = true;'),
    ('timeout one ms late', GATE, 'if (state != PENDING || now - since < READY_MS) return false;', 'if (state != PENDING || now - since <= READY_MS) return false;'),
    ('timeout retried in the menu', GATE, '        failedHere = true;\n        state = FRAMES;\n        return true;', '        state = FRAMES;\n        return true;'),
    ('timeout never fires', GATE, 'if (state != PENDING || now - since < READY_MS) return false;', 'if (true) return false;'),
    ('ready of another theme', GATE, 'if (state == PENDING && t != null && t.equals(theme)) state = LIVE;', 'if (state == PENDING) state = LIVE;'),
    ('second failure restarts the strip', GATE, ' || state == FRAMES) return false;', ') return false;'),
    ('failure after a computer starts the strip', GATE, '        state = OFF;\n        theme = null;', '        state = OFF;'),
    ('strip drawn while pending', GATE, 'boolean frames() { return state == FRAMES; }', 'boolean frames() { return state == FRAMES || state == PENDING; }'),
    ('timeout too long', GATE, 'static final long READY_MS = 2500;', 'static final long READY_MS = 25000;'),
    # ---- reader: a damaged file never reaches GL
    ('magic not checked', MODEL, 'need(b.getInt() == MAGIC, "magic");', 'b.getInt();'),
    ('size field not checked', MODEL, 'need(b.getInt() == b.limit(), "file size");', 'b.getInt();'),
    ('index past the vertices', MODEL, 'if (max >= d.vertexCount) throw', 'if (max > d.vertexCount) throw'),
    ('indices not checked', MODEL, '            long max = maxIndex(d);\n            if (max >= d.vertexCount)', '            long max = 0;\n            if (max >= d.vertexCount)'),
    ('level size not checked', MODEL, 'need(size == levelBytes(', 'need(size >= 0 || levelBytes('),
    ('any texture format', MODEL, 'need(knownFormat(t.format), ', 'need(t.format != 0, '),
    ('texture reference off by one', MODEL, 'need(t >= -1 && t < nt, "material texture");', 'need(t >= -1 && t <= nt, "material texture");'),
    ('NaN accepted', MODEL, 'need(!Float.isNaN(v) && !Float.isInfinite(v), what + " is not a number");', ''),
    ('vertex range not checked', MODEL, 'need(voff >= 0 && (long) voff + vsize <= vblob && ', 'need(voff >= 0 && '),
    ('version not checked', MODEL, 'need(u16(b) == VERSION, "version");\n        u16(b);\n        need(b.getInt()', 'u16(b);\n        u16(b);\n        need(b.getInt()'),
    ('environment order not checked', MODEL, ' && (i == 0 || e.roughness[i] > e.roughness[i - 1]),', ','),
    ('environment size not checked', MODEL, 'need(b.remaining() == bytes, "environment data");', 'need(b.remaining() >= 0, "environment data");'),
    ('16-bit index of a large draw', MODEL, 'return size == 4 || (size == 2 && vertices <= 65535);', 'return size == 4 || size == 2;'),
    ('index size rule not used', MODEL, 'need(indexSizeOk(d.indexSize, d.vertexCount), "index size");', ''),
    # ---- motion and camera
    ('turn easing off the strip', MOTION, 'return (float) (360.0 * (1 - Math.pow(1 - k, 2.2)));', 'return (float) (360.0 * (1 - Math.pow(1 - k, 1.6)));'),
    ('turn linear', MOTION, 'return (float) (360.0 * (1 - Math.pow(1 - k, 2.2)));', 'return (float) (360.0 * k);'),
    ('sway without ease-in', MOTION, 'return (float) (SWAY_DEG * easeIn(u) * Math.sin(', 'return (float) (SWAY_DEG * Math.sin('),
    ('sway too wide', MOTION, 'static final float SWAY_DEG = 7f;', 'static final float SWAY_DEG = 9f;'),
    ('sway period off the strip', MOTION, 'static final long SWAY_MS = GmModel.IDLE_FRAMES * GmModel.IDLE_STEP_MS;', 'static final long SWAY_MS = 3000;'),
    ('float without ease-in', MOTION, 'return (float) (BOB * easeIn(u) * Math.sin(', 'return (float) (BOB * Math.sin('),
    ('distance does not wrap below 0', MOTION, 'a = ((a % n) + n) % n;', 'a = a % n;'),
    ('distance table ignored', MOTION, 'return s.dist[i % n] * (1 - f) + s.dist[(i + 1) % n] * f;', 'return s.distHero;'),
    ('hero centre shift mirrored', MOTION, 'proj[8] = s.centerX * k;', 'proj[8] = -s.centerX * k;'),
    ('hero centre not scaled with distance', MOTION, 'float k = s.distHero / d;', 'float k = 1;'),
    ('tilt the other way', MOTION, 'float[] rx = {1, 0, 0, 0, 0, ct, st, 0, 0, -st, ct, 0, 0, 0, 0, 1};', 'float[] rx = {1, 0, 0, 0, 0, ct, -st, 0, 0, st, ct, 0, 0, 0, 0, 1};'),
    ('yaw the other way', MOTION, 'float[] ry = {cy, 0, -sy, 0, 0, 1, 0, 0, sy, 0, cy, 0, 0, 0, 0, 1};', 'float[] ry = {cy, 0, sy, 0, 0, 1, 0, 0, -sy, 0, cy, 0, 0, 0, 0, 1};'),
    ('camera not elevated', MOTION, 'float ex = 0, ey = (float) (Math.sin(el) * d), ez = (float) (Math.cos(el) * d);', 'float ex = 0, ey = 0, ez = d;'),
    ('field of view as horizontal', MOTION, 'fx = fy / s.aspect;', 'fx = fy;'),
    # ---- wiring of the fallback (GmView, GmStage, Gm3dView)
    ('strip never drawn after a failure', VIEW, '(model == null || gate.frames() || gate.pending())', '(model == null || gate.pending())'),
    ('timeout ignored by the card', VIEW, '        if (gate.timedOut(now)) toFrames("no first frame in " + Gm3dGate.READY_MS + " ms");\n', ''),
    ('failure ignored by the card', VIEW, 'if (gate.failed(theme, hard)) toFrames(why);', 'gate.failed(theme, hard);'),
    ('model started in a software window', VIEW, 'gate.begin(theme, true, isHardwareAccelerated(), now)', 'gate.begin(theme, true, true, now)'),
    ('strip not started on fallback', VIEW, '        if (model != null) model.hide();\n        frames.start(framesTheme);', '        if (model != null) model.hide();'),
    ('whole strip decoded under the model', VIEW, 'frames.startHero(theme);           // only', 'frames.start(theme);           // only'),
    ('no GL release on close', VIEW, '        if (model != null) model.release();\n', ''),
    ('model over a computer icon', VIEW, '            gate.off();\n            if (model != null) model.hide();', '            gate.off();'),
    ('model built when broken', STAGE, 'if (!Gm3dView.enabled() || Gm3dGate.sBroken) return v;', 'if (!Gm3dView.enabled()) return v;'),
    ('kill switch dead', GLV, 'return !"0".equals(v);', 'return true;'),
    ('GL set-up failure soft', GLV, 'fail("gl: " + e, true);', 'fail("gl: " + e, false);'),
    ('failure not reported', GLV, 'main.post(() -> listener.onModelFailed(th, why, hard));', 'Log.w(TAG, why);'),
    ('session not destroyed on release', GLV, 'gl().post(s::destroy);', 'gl().post(s::stop);'),
    ('context kept', GLV, '                EGL14.eglDestroyContext(dpy, ctx);\n', ''),
    ('hidden model keeps drawing', GLV, 'gl().post(() -> s.setRunning(visible));', 'gl().post(() -> s.setRunning(true));'),
    ('surface freed under the GL thread', GLV, '        gl().post(() -> s.surfaceGone(st));\n        return false;', '        gl().post(() -> s.surfaceGone(st));\n        return true;'),
]


def run(tree):
    r = subprocess.run(['sh', os.path.join(tree, 'test/gm3d/run.sh')], capture_output=True, text=True, timeout=300)
    return r.returncode, (r.stdout + r.stderr)


def main():
    only = sys.argv[sys.argv.index('-k') + 1] if '-k' in sys.argv else None
    tmp = tempfile.mkdtemp(prefix='gm3d_mut_', dir=os.environ.get('TMPDIR'))
    try:
        tree = os.path.join(tmp, 'lumen-menu')
        os.makedirs(os.path.join(tree, 'assets'))
        os.symlink(os.path.join(MOD, 'assets', 'gm3d'), os.path.join(tree, 'assets', 'gm3d'))
        shutil.copytree(os.path.join(MOD, 'src'), os.path.join(tree, 'src'))
        shutil.copytree(os.path.join(MOD, 'test', 'gm3d'), os.path.join(tree, 'test', 'gm3d'))
        rc, out = run(tree)
        if rc != 0:
            print('FAIL unmutated tree is red:\n' + out[-2000:])
            return 1
        killed, survived = 0, []
        for name, rel, old, new in MUTANTS:
            if only and only not in name:
                continue
            p = os.path.join(tree, rel)
            src = open(p).read()
            if src.count(old) != 1:
                print('BROKEN %s: anchor found %d times' % (name, src.count(old)))
                survived.append(name)
                continue
            open(p, 'w').write(src.replace(old, new))
            try:
                rc, out = run(tree)
            finally:
                open(p, 'w').write(src)
            if rc != 0:
                killed += 1
                why = next((l for l in out.splitlines() if l.startswith('FAIL') or 'error' in l), '?')
                print('killed   %-45s %s' % (name, why[:110]))
            else:
                survived.append(name)
                print('SURVIVED %s' % name)
        print('gm3d mutants: %d killed, %d survived%s' % (killed, len(survived), (': ' + ', '.join(survived)) if survived else ''))
        return 1 if survived else 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main())
