#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of Lumen Setup's pointer and "other" page wiring: each must turn test/pointer red.
python3 test/pointer/mutate.py [NAME...]"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, '..', '..'))
ROOT = os.path.normpath(os.path.join(APP, '..', '..'))
S = 'apps/LumenSetup/src/org/lumen/setup/'
SYS = 'apps/core/lumen-system/src/org/lumen/system/'

MUTANTS = [
    ('other remote by the raw intent', S + 'steps/RemoteStep.java', 'if (!SystemPages.open(host, SettingsPage.BLUETOOTH)) L.w(',
     'if (!SystemPages.open(host, SettingsPage.NETWORK)) L.w('),
    ('other remote button not shown', S + 'steps/RemoteStep.java', '        btns.addView(other, ', '        if (false) btns.addView(other, '),
    ('wifi other options raw', S + 'steps/NetworkStep.java', 'if (SystemPages.open(host, SettingsPage.NETWORK))', 'if (SystemPages.open(host, SettingsPage.SYSTEM))'),
    ('pill not clickable', S + 'ui/Pill.java', '        setClickable(true);', '        setClickable(false);'),
    ('pill wants a first tap', S + 'ui/Pill.java', 'setFocusableInTouchMode(false);', 'setFocusableInTouchMode(true);'),
    ('row not clickable', S + 'ui/Row.java', '        setClickable(true);', '        setClickable(false);'),
    ('busy row swallows every click', S + 'ui/Row.java', '        return super.performClick();', '        return true;'),
    ('touch eaten by the activity', S + 'SetupActivity.java', '    // ------------------------------------------------------------------ keys\n',
     '    // ------------------------------------------------------------------ keys\n    @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) { return true; }\n'),
    ('accessories slice dropped', SYS + 'SystemIntent.java', 'return Arrays.asList(new SystemIntent(SLICE_SETTINGS, TV_SETTINGS, SLICE_URI, ACCESSORIES),\n                        of(BLUETOOTH), of(ROOT));',
     'return Arrays.asList(of(BLUETOOTH), of(ROOT));'),
    ('wrong slice', SYS + 'SystemIntent.java', 'accessories.sliceprovider/general', 'accessories.sliceprovider/connected'),
    ('unresolved way started', SYS + 'SystemPages.java', '                if (i.resolveActivity(ctx.getPackageManager()) == null) continue;\n', ''),
    ('RS30 recipe without the module', 'devices/g0155/app/Z9xSetup.layers', 'module    lumen-system\n', ''),
]


def tree(tmp):
    root = os.path.join(tmp, 'r')
    shutil.copytree(os.path.join(ROOT, 'apps', 'LumenSetup', 'src'), os.path.join(root, 'apps', 'LumenSetup', 'src'))
    shutil.copytree(os.path.join(ROOT, 'apps', 'LumenSetup', 'test', 'pointer'), os.path.join(root, 'apps', 'LumenSetup', 'test', 'pointer'))
    shutil.copy(os.path.join(ROOT, 'apps', 'LumenSetup', 'AndroidManifest.xml'), os.path.join(root, 'apps', 'LumenSetup'))
    shutil.copytree(os.path.join(ROOT, 'apps', 'core', 'lumen-system', 'src'), os.path.join(root, 'apps', 'core', 'lumen-system', 'src'))
    for c in os.listdir(os.path.join(ROOT, 'devices')):
        d = os.path.join(ROOT, 'devices', c, 'app')
        if os.path.isdir(d):
            shutil.copytree(d, os.path.join(root, 'devices', c, 'app'))
    return root


def run(root):
    r = subprocess.run(['sh', os.path.join(root, 'apps', 'LumenSetup', 'test', 'pointer', 'run.sh')], capture_output=True, text=True,
                       timeout=120, env=dict(os.environ, LUMEN_ROOT=root))
    return r.returncode


def main(names):
    todo = [m for m in MUTANTS if not names or m[0] in names]
    tmp = tempfile.mkdtemp()
    try:
        root = tree(tmp)
        if run(root) != 0:
            print('BROKEN: the unmutated tree is red')
            return 2
        alive = 0
        for name, f, old, new in todo:
            p = os.path.join(root, f)
            text = open(p, encoding='utf-8').read()
            if text.count(old) != 1:
                print('%-36s ERROR (anchor found %d times)' % (name, text.count(old)))
                alive += 1
                continue
            open(p, 'w', encoding='utf-8').write(text.replace(old, new))
            try:
                res = 'killed' if run(root) != 0 else 'ALIVE'
            finally:
                open(p, 'w', encoding='utf-8').write(text)
            alive += res != 'killed'
            print('%-36s %s' % (name, res))
        print('mutants: %d, killed %d, not killed %d' % (len(todo), len(todo) - alive, alive))
        return 1 if alive else 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
