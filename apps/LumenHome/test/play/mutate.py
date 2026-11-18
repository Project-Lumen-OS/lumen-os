#!/usr/bin/env python3
"""Mutants of Lumen Home's Google Play gate (PlayGate, its call in Launch.start, the permission in the manifest and in
both privapp lists): each must turn test/play red. python3 test/play/mutate.py"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, '..', '..'))
ROOT = os.path.normpath(os.path.join(APP, '..', '..'))
H = 'src/org/lumen/home/'
PG = H + 'PlayGate.java'
LA = H + 'Launch.java'
MAN = 'AndroidManifest.xml'
PRIV = 'image/privapp-permissions-z9xhome.xml'
OV = '../../overlay/v1/privapp-permissions-z9xhome.xml'
RULE = 'return PLAY.equals(pkg) && canRead && accounts == 0 && signInThere;'
PERM_LINE = '        <permission name="android.permission.GET_ACCOUNTS_PRIVILEGED" />\n'

MUTANTS = [
    ('every app gated', PG, RULE, 'return canRead && accounts == 0 && signInThere;'),
    ('gated with an account', PG, RULE, 'return PLAY.equals(pkg) && canRead && accounts <= 1 && signInThere;'),
    ('gated when the accounts are unreadable', PG, RULE, 'return PLAY.equals(pkg) && accounts == 0 && signInThere;'),
    ('gated without a sign-in screen', PG, RULE, 'return PLAY.equals(pkg) && canRead && accounts == 0;'),
    ('never gated', PG, RULE, 'return false;'),
    ('wrong Play package', PG, 'PLAY = "com.android.vending";', 'PLAY = "com.google.android.tvlauncher";'),
    ('work accounts ignored', PG, '{"com.google", "com.google.work"}', '{"com.google"}'),
    ('wrong sign-in type', PG, 'SIGN_IN_TYPE = "com.google";', 'SIGN_IN_TYPE = "com.google.work";'),
    ('the type picker instead of the Google sign-in', PG, 'EXTRA_TYPE = "accountType";', 'EXTRA_TYPE = "account_type";'),
    ('wrong permission', PG, 'PERMISSION = "android.permission.GET_ACCOUNTS_PRIVILEGED";',
     'PERMISSION = "android.permission.GET_ACCOUNTS";'),
    ('the account settings instead of the sign-in', PG, 'return new Intent(Settings.ACTION_ADD_ACCOUNT)',
     'return new Intent(Settings.ACTION_SYNC_SETTINGS)'),
    ('the sign-in without a new task', PG, '                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);\n    }',
     '                ;\n    }'),
    ('accounts read without the permission', PG,
     '            if (canRead) {\n                AccountManager am = AccountManager.get(c);\n'
     '                for (String t : PLAY_ACCOUNT_TYPES) n += am.getAccountsByType(t).length;\n            }\n',
     '            AccountManager am = AccountManager.get(c);\n'
     '            for (String t : PLAY_ACCOUNT_TYPES) n += am.getAccountsByType(t).length;\n'),
    ('a failure blocks the launch', PG, '            Log.w(TAG, "play gate: " + t);\n            return null;',
     '            Log.w(TAG, "play gate: " + t);\n            return signInIntent();'),
    ('no gate in Launch.start', LA, '        Intent signIn = PlayGate.signInInstead(c, i);\n        if (signIn != null) {\n'
     '            i = signIn;\n            what = what + " (no Google account: Google sign-in first)";\n        }\n', ''),
    ('the gate result ignored', LA, '            i = signIn;\n', ''),
    ('Play started past Launch.start', H + 'Launch.java', '                    Intent i = IntentGuard.appLaunch(c, AppSource.PKG_PLAY);\n'
     '                    if (i != null) start(c, i, "play store");\n',
     '                    Intent i = IntentGuard.appLaunch(c, AppSource.PKG_PLAY);\n'
     '                    if (i != null) c.startActivity(IntentGuard.appLaunch(c, AppSource.PKG_PLAY));\n'),
    ('no permission in the manifest', MAN, '    <uses-permission android:name="android.permission.GET_ACCOUNTS_PRIVILEGED" />\n', ''),
    ('no permission in the privapp list', PRIV, PERM_LINE, ''),
    ('no permission in the image copy', OV, PERM_LINE, ''),
]


def tree(tmp):
    root = os.path.join(tmp, 'r')
    app = os.path.join(root, 'apps', 'LumenHome')
    shutil.copytree(os.path.join(APP, 'src'), os.path.join(app, 'src'))
    os.makedirs(os.path.join(app, 'image'))
    shutil.copy(os.path.join(APP, PRIV), os.path.join(app, 'image'))
    shutil.copy(os.path.join(APP, MAN), app)
    shutil.copytree(os.path.join(APP, 'test', 'play'), os.path.join(app, 'test', 'play'))
    os.makedirs(os.path.join(root, 'apps', 'sdk'))
    os.symlink(os.path.join(ROOT, 'apps', 'sdk', 'framework-full-headers.jar'), os.path.join(root, 'apps', 'sdk', 'framework-full-headers.jar'))
    ov = os.path.join(ROOT, 'overlay', 'v1', 'privapp-permissions-z9xhome.xml')
    if os.path.isfile(ov):
        os.makedirs(os.path.join(root, 'overlay', 'v1'))
        shutil.copy(ov, os.path.join(root, 'overlay', 'v1'))
    return app


def run(app):
    r = subprocess.run(['sh', os.path.join(app, 'test/play/run.sh')], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def main():
    tmp = tempfile.mkdtemp(prefix='home_play_mut_')
    try:
        rc, out = run(tree(tmp))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if rc != 0:
        print('BASELINE RED: ' + out[-800:])
        sys.exit(1)
    killed, survived = 0, []
    for name, rel, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix='home_play_mut_')
        try:
            app = tree(tmp)
            target = os.path.normpath(os.path.join(app, rel))
            if not os.path.isfile(target):
                print('skip     %s (no %s in this tree)' % (name, rel))
                continue
            text = open(target, encoding='utf-8').read()
            if text.count(old) != 1:
                print('BROKEN %s: anchor found %d times' % (name, text.count(old)))
                survived.append(name)
                continue
            open(target, 'w', encoding='utf-8').write(text.replace(old, new))
            rc, out = run(app)
            if rc != 0:
                killed += 1
                print('killed   %s' % name)
            else:
                survived.append(name)
                print('SURVIVED %s' % name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print('home play gate mutants: %d of %d killed' % (killed, len(MUTANTS)))
    sys.exit(0 if not survived else 1)


if __name__ == '__main__':
    main()
