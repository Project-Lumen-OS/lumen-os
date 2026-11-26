#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the screen mirroring control API (core/Control + StateProvider / manifest wiring): each one must turn
sh test/run_core.sh red. python3 test/mutate_control.py"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, '..'))
CTRL = 'src/org/lumen/miracast/core/Control.java'
PROV = 'src/org/lumen/miracast/StateProvider.java'
MAN = 'AndroidManifest.xml'
SET = 'src/org/lumen/miracast/SettingsActivity.java'
Q = 'src/org/lumen/miracast/core/WfdQuality.java'

MUTANTS = [
    ('set_ask changes enabled', CTRL, 'if (SET_ASK.equals(method)) return ASK;', 'if (SET_ASK.equals(method)) return ENABLED;'),
    ('state is a set call', CTRL, 'if (SET_ENABLED.equals(method)) return ENABLED;',
     'if (SET_ENABLED.equals(method) || STATE.equals(method)) return ENABLED;'),
    ('any value is on', CTRL, 'if ("1".equals(arg)) return Boolean.TRUE;', 'if (arg != null && !"0".equals(arg)) return Boolean.TRUE;'),
    ('null value is off', CTRL, 'if ("0".equals(arg)) return Boolean.FALSE;', 'if (arg == null || "0".equals(arg)) return Boolean.FALSE;'),
    ('off does not win', CTRL, '        if (!enabled) return ReceiverStatus.OFF;\n', ''),
    ('ready before the service runs', CTRL, 'if (!serviceUp) return ReceiverStatus.STARTING;', 'if (!serviceUp) return ReceiverStatus.READY;'),
    ('Wi-Fi state wins over a session', CTRL, '        if (mirroring) return ReceiverStatus.IN_USE;\n', ''),
    ('Wi-Fi off shown as ready', CTRL, 'if ("WIFI_OFF".equals(p2pState)) return ReceiverStatus.NO_WIFI;', 'if ("WIFI_OFF".equals(p2pState)) return ReceiverStatus.READY;'),
    ('unknown state shown as ready', CTRL, '        return ReceiverStatus.STARTING;\n    }\n}', '        return ReceiverStatus.READY;\n    }\n}'),
    ('call() without the permission check', PROV, '        c.enforceCallingOrSelfPermission(PERM_CONTROL, "screen mirroring control");\n',
     '        c.checkCallingOrSelfPermission(PERM_CONTROL);\n'),
    ('call() checks the permission after a change', PROV,
     '        c.enforceCallingOrSelfPermission(PERM_CONTROL, "screen mirroring control");\n        String key',
     '        String key'),
    ('bad value still saved', PROV, 'if (on == null) return null;', 'if (on == null) on = Boolean.FALSE;'),
    ('provider read open', MAN, 'android:readPermission="org.lumen.miracast.permission.CONTROL"', ''),
    ('CONTROL not signature', MAN, 'android:protectionLevel="signature" />', 'android:protectionLevel="normal" />'),
    ('old address opens nothing', SET, 'startActivity(castScreen(this, getIntent().getStringExtra(EXTRA_EDIT)));', ''),
    ('old address opens another screen', SET, 'new Intent(CAST_SCREEN)', 'new Intent("org.lumen.miracast.SETTINGS")'),
    ('redirect shows a window', MAN, 'android:theme="@android:style/Theme.NoDisplay"', 'android:theme="@style/Theme.Miracast.Settings"'),
    ('quality not in the state', PROV, '        b.putString("quality", StatusText.quality(c, Prefs.quality(c)));\n', ''),
    ('quality step stays', Q, 'return all[Math.floorMod(ordinal() + d, all.length)];', 'return this;'),
    ('quality step skips 720p', Q, 'return all[Math.floorMod(ordinal() + d, all.length)];', 'return all[Math.floorMod(ordinal() + d, 2)];'),
    ('quality back goes forward', Q, 'return all[Math.floorMod(ordinal() + d, all.length)];', 'return all[Math.floorMod(ordinal() + 1, all.length)];'),
]


def run(app):
    r = subprocess.run(['sh', os.path.join(app, 'test/run_core.sh')], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def tree(tmp):
    app = os.path.join(tmp, 'app')
    shutil.copytree(os.path.join(APP, 'src'), os.path.join(app, 'src'))
    shutil.copytree(os.path.join(APP, 'test'), os.path.join(app, 'test'))
    shutil.copytree(os.path.join(APP, 'res'), os.path.join(app, 'res'))
    shutil.copy(os.path.join(APP, MAN), os.path.join(app, MAN))
    return app


def main():
    tmp = tempfile.mkdtemp(prefix='mc_mut_')
    try:
        rc, out = run(tree(tmp))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if rc != 0:
        print('BASELINE RED: ' + out[-600:])
        sys.exit(1)
    killed = 0
    for name, rel, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix='mc_mut_')
        try:
            app = tree(tmp)
            p = os.path.join(app, rel)
            s = open(p, encoding='utf-8').read()
            if s.count(old) != 1:
                print('ANCHOR %d: %s' % (s.count(old), name))
                continue
            open(p, 'w', encoding='utf-8').write(s.replace(old, new))
            rc, out = run(app)
            if rc != 0:
                killed += 1
                print('killed  ' + name)
            else:
                print('SURVIVED ' + name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print('mutants: %d/%d killed' % (killed, len(MUTANTS)))
    sys.exit(0 if killed == len(MUTANTS) else 1)


if __name__ == '__main__':
    main()
