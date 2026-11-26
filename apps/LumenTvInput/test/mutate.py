#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Targeted mutants of the boot window (BootWindow) and its wiring in HdmiWatcher: each one change must turn
test/run.sh red.
  python3 apps/LumenTvInput/test/mutate.py [-k substring]
Every mutant runs on a scratch copy of the files run.sh reads (the repo itself is never written)."""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
G = os.path.normpath(os.path.join(HERE, '..', '..', '..'))
B = 'apps/LumenTvInput/src/org/lumen/tvinput/BootWindow.java'
W = 'apps/LumenTvInput/src/org/lumen/tvinput/HdmiWatcher.java'
R = 'apps/LumenTvInput/src/org/lumen/tvinput/Route.java'
H = 'apps/LumenTvInput/src/org/lumen/tvinput/CecHelper.java'
S = 'apps/LumenTvInput/src/org/lumen/tvinput/HdmiSession.java'

MUTANTS = [
    ('route: deviceSelect for every child input again (LR-5MVYWT two Set Stream Path)', R,
     'if (deviceId < 0 || on && direct) return PORT;', 'if (deviceId < 0) return PORT;'),
    ('route: portSelect for a box in standby', R, 'if (deviceId < 0 || on && direct) return PORT;', 'if (deviceId < 0 || direct) return PORT;'),
    ('route: portSelect for a box behind a switch', R, 'if (deviceId < 0 || on && direct) return PORT;', 'if (deviceId < 0 || on) return PORT;'),
    ('route: deviceSelect for a port input', R, 'if (deviceId < 0 || on && direct) return PORT;', 'if (on && direct) return PORT;'),
    ('route: power read as standby', H, 'boolean on = d != null && d.getDevicePowerStatus() == HdmiControlManager.POWER_STATUS_ON;',
     'boolean on = d != null && d.getDevicePowerStatus() == HdmiControlManager.POWER_STATUS_STANDBY;'),
    ('session: deviceSelect wired back', S, 'post("route", () -> CecHelper.route(c, dev, port));',
     'post("deviceSelect", () -> CecHelper.deviceSelect(c, dev, port));'),
    # name, file, old (once in the file), new
    ('the settle window holds the power-on source', B, 'return !powerOnSource && now - startedAt < SETTLE_MS;',
     'return now - startedAt < SETTLE_MS;'),
    ('no settle window for plugs', B, 'return !powerOnSource && now - startedAt < SETTLE_MS;', 'return false;'),
    ('a 20 s settle window', B, 'SETTLE_MS = 30_000;', 'SETTLE_MS = 20_000;'),
    ('opens before the port input exists', B, 'if (decided || !portListed) return false;', 'if (decided) return false;'),
    ('opens again at every input', B, '        decided = true;\n', ''),
    ('the first ask without the input uses up the open', B,
     '        if (decided || !portListed) return false;\n        decided = true;\n',
     '        if (decided) return false;\n        decided = true;\n        if (!portListed) return false;\n'),
    ('a late port input still opens', B, 'return now - startedAt <= OPEN_BY_MS;', 'return true;'),
    ('the last moment no longer opens', B, 'return now - startedAt <= OPEN_BY_MS;', 'return now - startedAt < OPEN_BY_MS;'),
    ('the fixed wait of 32 s is back', B, 'if (decided || !portListed) return false;',
     'if (decided || !portListed || now - startedAt < OPEN_BY_MS) return false;'),
    ('a fixed wait of 3 s', B, 'if (decided || !portListed) return false;',
     'if (decided || !portListed || now - startedAt < 3_000) return false;'),
    ('the watcher does not ask at an input added', W, '                bootEvent("input added");\n', ''),
    ('the watcher does not ask at its start', W, '        bootEvent("start");\n', ''),
    ('the power-on source is held by the settle window in tryOpen', W,
     'commonSkipReason(REASON_PLUG.equals(reason), REASON_BOOT.equals(reason))',
     'commonSkipReason(REASON_PLUG.equals(reason), false)'),
    ('a plug passes as the power-on source', W, 'String skip = commonSkipReason(true, false);',
     'String skip = commonSkipReason(true, true);'),
    ('the watcher keeps a timer again', W, '        bootEvent("start");\n',
     '        bootEvent("start");\n        mHandler.postDelayed(Safe.wrap(TAG, "bootCheck", () -> bootEvent("timer")), BootWindow.OPEN_BY_MS);\n'),
    ('the settle window rule bypassed in the watcher', W, 'if (mBootWindow.blocks(now, powerOnSource)) return',
     'if (false) return'),
]


def main():
    k = sys.argv[sys.argv.index('-k') + 1] if '-k' in sys.argv else None
    files = sorted({m[1] for m in MUTANTS} | {'apps/LumenTvInput/test', 'apps/LumenTvInput/src'})
    alive = []
    n = 0
    for name, f, old, new in MUTANTS:
        if k and k not in name:
            continue
        n += 1
        with tempfile.TemporaryDirectory() as t:
            for p in files:
                src, dst = os.path.join(G, p), os.path.join(t, p)
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                (shutil.copytree if os.path.isdir(src) else shutil.copy)(src, dst)
            p = os.path.join(t, f)
            s = open(p, encoding='utf-8').read()
            if s.count(old) != 1:
                print('BAD  %s: the text is %d times in %s' % (name, s.count(old), f))
                alive.append(name)
                continue
            open(p, 'w', encoding='utf-8').write(s.replace(old, new))
            r = subprocess.run(['sh', os.path.join(t, 'apps/LumenTvInput/test/run.sh')], capture_output=True, text=True)
            ok = r.returncode != 0
            print(('killed ' if ok else 'ALIVE  ') + name)
            if not ok:
                alive.append(name)
    print('%d mutants, %d killed, %d alive' % (n, n - len(alive), len(alive)))
    sys.exit(1 if alive else 0)


if __name__ == '__main__':
    main()
