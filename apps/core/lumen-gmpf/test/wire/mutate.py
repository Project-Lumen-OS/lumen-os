#!/usr/bin/env python3
"""Mutants of the shared gmpf clients (apps/core/lumen-gmpf and each device's wire, codes and rules): each must turn
the wire golden (test/wire/run.sh) red. python3 apps/core/lumen-gmpf/test/wire/mutate.py [-k substring]

Every mutant runs on a tree of symlinks with the one mutated file copied (the repo itself is never written), traced
through run.sh with TREE= and compared with the committed golden."""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.normpath(os.path.join(HERE, '..', '..', '..', '..', '..'))
CORE = 'apps/core/lumen-gmpf/src/org/lumen/'
BASE = CORE + 'projector/hal/GmpfBase.java'
BASE2 = CORE + 'projector/hal/Gmpf2Base.java'
ZOOM = CORE + 'hal/gmpf/ZoomGuard.java'
BUDGET = CORE + 'hal/gmpf/utils/ReadBudget.java'
ZH = 'apps/Z9xProjector/src/org/lumen/projector/hal/'
HH = 'apps/HbProjector/src/org/lumen/projector/hal/'
PH = 'apps/Play6Projector/src/org/lumen/projector/hal/'
Z, H, P = 'horse_popular', 'goat_elegant', 'dragon_healthy'

MUTANTS = [
    # name, device, file, old, new
    ('Z9X code row of another method', Z, ZH + 'Z9xCodes.java', '.put(GET_BRIGHTNESS, HalCodes.GMPF_GET_BRIGHTNESS)',
     '.put(GET_BRIGHTNESS, HalCodes.GMPF_SET_BRIGHTNESS)'),
    ('HB code row of another method', H, HH + 'HbCodes.java', '.put(GET_BRIGHTNESS, GmpfCall.GET_BRIGHTNESS)',
     '.put(GET_BRIGHTNESS, GmpfCall.SET_BRIGHTNESS)'),
    ('HB call dropped from its table', H, HH + 'HbCodes.java', '            .put(SET_OPTICAL_ZOOM_STEP, GmpfCall.SET_OPTICAL_ZOOM_STEP)\n', ''),
    ('HIDL int8 sent as int32', Z, ZH + 'HidlWire.java', 'Req withI8(int v) { p.writeInt8((byte) v); return this; }',
     'Req withI8(int v) { p.writeInt32(v); return this; }'),
    ('HIDL false reply accepted', Z, ZH + 'HidlWire.java',
     'if (!r.readBool()) throw new IllegalStateException(code + " returned false");', 'r.readBool();'),
    ('AIDL motion axes swapped', H, HH + 'AidlWire.java', 'return new int[] {acc[0], acc[1], acc[2], ang[0], ang[1], ang[2], type};',
     'return new int[] {ang[0], ang[1], ang[2], acc[0], acc[1], acc[2], type};'),
    ('focus status value changed', Z, BASE, 'FOCUS_STATUS_AF_UI_OPEN = 4;', 'FOCUS_STATUS_AF_UI_OPEN = 6;'),
    ('Turn off screen with another action', Z, BASE2, 'SHUT_SCREEN_ACTION = 1;', 'SHUT_SCREEN_ACTION = 0;'),
    ('Turn off screen on the HB another code', H, HH + 'HbCodes.java',
     '.put(SET_SCREEN_ON_OFF_WITH_ACTION, GmpfCall.SET_SCREEN_ON_OFF_WITH_ACTION)',
     '.put(SET_SCREEN_ON_OFF_WITH_ACTION, GmpfCall.SET_SCREEN_ON_OFF)'),
    ('feature gate skipped', H, BASE, '        gate(GmpfGate.CURTAIN_FIT, "setProjCurtainAdp");\n', ''),
    ('optical zoom step not next to the read one', Z, ZOOM, 'if (Math.abs(step - sOptCur) != 1) {',
     'if (Math.abs(step - sOptCur) > 2) {'),
    ('sensor read budget gap ignored', Z, BUDGET, 'if (last != 0 && now - last < gapMs)', 'if (false)'),
    ('HB EDID 2.0 refused', H, HH + 'HbRules.java', '{GmpfBase.EDID_14, GmpfBase.EDID_20}', '{GmpfBase.EDID_14}'),
    ('HDMI mode byte misread', Z, ZH + 'GmpfClient.java', 'HDMI_MODE_AT = 9', 'HDMI_MODE_AT = 8'),
    ('game menu HDMI 3 allowed', H, BASE2, 'HDMI_1 = 1, HDMI_2 = 2;', 'HDMI_1 = 1, HDMI_2 = 3;'),
    # the Play 6: its codes, its IGmpf2 identity guard and vendor tables, its own calls and value rules
    ('Play 6 code row of another method', P, PH + 'Play6Codes.java', '.put(GET_BRIGHTNESS, HalCodes.GMPF_GET_BRIGHTNESS)',
     '.put(GET_BRIGHTNESS, HalCodes.GMPF_SET_BRIGHTNESS)'),
    ('Play 6 v1.1.67 row off by one', P, PH + 'Gmpf2Vendor.java', '{HalCodes.GMPF2_SET_SLIDE_FUNC, 68}',
     '{HalCodes.GMPF2_SET_SLIDE_FUNC, 67}'),
    ('Play 6 v1.1.67 gains getPowerSupplyVoltage', P, PH + 'Gmpf2Vendor.java', '{HalCodes.GMPF2_GET_GAME_MODE_PROP, 33},',
     '{HalCodes.GMPF2_GET_POWER_SUPPLY_VOLTAGE, 32}, {HalCodes.GMPF2_GET_GAME_MODE_PROP, 33},'),
    ('Play 6 guard opens on any hash', P, PH + 'Play6Gmpf2Wire.java',
     'Gmpf2Vendor v = chain.length > 0 ? Gmpf2Vendor.of(chain[0]) : null;', 'Gmpf2Vendor v = Gmpf2Vendor.V1_7_116;'),
    # (drop() resetting the guard is the same as need() resetting it on a lost binder, and c()'s own need() only
    # opens the guard for a Play 6 float read that comes first after a reconnect, failing closed without it: both
    # are pinned by tools/play6/check_play6_hal.py rule 7)
    ('Play 6 guard kept over a dropped binder', P, PH + 'Play6Gmpf2Wire.java',
     '        if (!hidl.connected()) identity = Gmpf2Identity.UNKNOWN;\n', ''),
    ('Play 6 hash chain read on the IGmpf2 token', P, PH + 'Play6Gmpf2Wire.java', 'q.writeInterfaceToken(IBASE);',
     'q.writeInterfaceToken(IGMPF2);'),
    ('Play 6 sound mode sent as int32', P, PH + 'Play6Gmpf.java', 'wire.u8(key(SET_SOUND_EFFECT), e.wire)',
     'wire.i32(key(SET_SOUND_EFFECT), e.wire)'),
    ('Play 6 second HDMI source allowed', P, PH + 'Play6Gmpf.java', 'if (!oneOf(source, SOURCE_MEDIA, SOURCE_HDMI1))',
     'if (!oneOf(source, SOURCE_MEDIA, SOURCE_HDMI1, SOURCE_HDMI2))'),
    ('Play 6 gamma index 9 allowed', P, PH + 'Play6Gmpf2.java', 'GAMMA_MAX = 8;', 'GAMMA_MAX = 9;'),
    ('Play 6 keystone read in another mode', P, PH + 'Play6Gmpf.java', 'KST_MODE_FOUR_POINT = 0;', 'KST_MODE_FOUR_POINT = 1;'),
    ('Play 6 EDID 2.0 refused', P, PH + 'Play6Rules.java', '{Play6Gmpf.EDID_V14, Play6Gmpf.EDID_V20}', '{Play6Gmpf.EDID_V14}'),
    ('Play 6 picture mode AI value changed', P, PH + 'PictureMode.java', 'AI(10)', 'AI(11)'),
    ('Play 6 vendor listener without our pid', P, PH + 'Play6Gmpf.java', 'q.writeInt32(Process.myPid());', 'q.writeInt32(0);'),
    ('Play 6 focus events not delivered', P, PH + 'Play6Gmpf.java', '        if (sink != null) HidlFocusCallback.attach(sink);\n', ''),
]


def tree(tmp, rel):
    """gsi/ as symlinks (apps, devices and tools as directories of file links), the mutated file a copy."""
    root = os.path.join(tmp, 'gsi')
    os.makedirs(root)
    for e in os.listdir(G):
        src = os.path.join(G, e)
        if e == '.git':
            continue
        if e not in ('apps', 'devices', 'tools') or not os.path.isdir(src):
            os.symlink(src, os.path.join(root, e))
            continue
        for d, dirs, files in os.walk(src):
            dirs[:] = [x for x in dirs if x not in ('build', '.gradle', '__pycache__')]
            out = os.path.join(root, os.path.relpath(d, G))
            os.makedirs(out, exist_ok=True)
            for f in files:
                os.symlink(os.path.join(d, f), os.path.join(out, f))
    p = os.path.join(root, rel)
    os.unlink(p)
    shutil.copy(os.path.join(G, rel), p)
    return root


def run(root, device):
    env = dict(os.environ, TREE=root)
    r = subprocess.run(['sh', os.path.join(HERE, 'run.sh'), device], capture_output=True, text=True, env=env)
    return r.returncode, r.stdout + r.stderr


def main():
    only = sys.argv[sys.argv.index('-k') + 1] if '-k' in sys.argv else None
    rc, out = subprocess.run(['sh', os.path.join(HERE, 'run.sh')], capture_output=True, text=True).returncode, ''
    if rc != 0:
        print('BASELINE RED: run.sh fails on the tree itself')
        sys.exit(1)
    killed = tried = 0
    for name, device, rel, old, new in MUTANTS:
        if only and only not in name:
            continue
        tried += 1
        tmp = tempfile.mkdtemp(prefix='gmpf_mut_', dir=os.environ.get('TMPDIR'))
        try:
            root = tree(tmp, rel)
            target = os.path.join(root, rel)
            s = open(target, encoding='utf-8').read()
            if s.count(old) != 1:
                print('ANCHOR %d: %s' % (s.count(old), name))
                continue
            open(target, 'w', encoding='utf-8').write(s.replace(old, new))
            rc, out = run(root, device)
            if rc != 0:
                killed += 1
                print('killed   ' + name)
            else:
                print('SURVIVED ' + name + '\n' + out[-400:])
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print('mutants: %d/%d killed' % (killed, tried))
    sys.exit(0 if killed == tried else 1)


if __name__ == '__main__':
    main()
