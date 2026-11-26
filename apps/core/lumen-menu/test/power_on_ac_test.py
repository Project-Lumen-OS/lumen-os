#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Power On Auto Boot (power.on_ac, decision 2026-11-19), static checks over the tree.

  python3 apps/core/lumen-menu/test/power_on_ac_test.py GSI_ROOT

1. The row is model data: the seven stock models name it in their profile, Play 6 (dragon_healthy) does not.
2. The vendor call of each model is the stock one: Z9X IGmpf 543 / 544, HB and the X50 family GmFactoryManager 36 / 37,
   each a generated HalCodes constant mapped to the shared op, and nothing on Play 6.
3. No write without a user action: setPowerOnMode is sent only from the menu rows' set path of each adapter (the menu
   calls set only for the user's flip), never from a read, a boot receiver or a service.
"""
import os
import re
import sys

G = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), '..', '..', '..', '..')
FAILS = []


def ok(name, cond):
    print(('ok   ' if cond else 'FAIL ') + name)
    if not cond:
        FAILS.append(name)


def read(rel):
    with open(os.path.join(G, rel), encoding='utf-8') as f:
        return f.read()


def codes(dev):
    """{(interface, method): (NAME, code)} of devices/<dev>/hal/codes.tsv."""
    out = {}
    for line in read('devices/%s/hal/codes.tsv' % dev).splitlines():
        p = line.split('\t')
        if len(p) >= 4 and not line.startswith('#') and p[0] not in ('java', 'stock'):
            out[(p[0], p[3])] = (p[1], int(p[2], 0))
    return out


WITH = ['horse_popular', 'goat_elegant', 'g0111', 'g0100', 'g0155', 'h0061', 'h0062', 'h0063']
for d in WITH:
    ok('%s names power.on_ac' % d, '"power.on_ac": {}' in read('devices/%s/menu.json' % d))
ok('dragon_healthy (Play 6) has no power.on_ac', 'power.on_ac' not in read('devices/dragon_healthy/menu.json'))

z9x = codes('horse_popular')
ok('Z9X setPowerOnMode = IGmpf 543', z9x.get(('IGmpf', 'setPowerOnMode')) == ('GMPF_SET_POWER_ON_MODE', 543))
ok('Z9X getPowerOnMode = IGmpf 544', z9x.get(('IGmpf', 'getPowerOnMode')) == ('GMPF_GET_POWER_ON_MODE', 544))
for dev, app in (('goat_elegant', 'HB'), ('g0111', 'X50 family')):
    c = codes(dev)
    ok('%s setPowerOnMode = GmFactoryManager 36' % app,
       c.get(('GmFactoryManager', 'setPowerOnMode')) == ('FM_SET_POWER_ON_MODE', 36))
    ok('%s getPowerOnMode = GmFactoryManager 37' % app,
       c.get(('GmFactoryManager', 'getPowerOnMode')) == ('FM_GET_POWER_ON_MODE', 37))
p6 = read('devices/dragon_healthy/hal/codes.tsv')
ok('Play 6 codes carry no power on mode call', 'PowerOnMode' not in p6)

z9codes = read('apps/ProjectorBase/src/org/lumen/projector/hal/Z9xCodes.java')
hbcodes = read('apps/HbProjector/src/org/lumen/projector/hal/HbCodes.java')
p6codes = read('apps/Play6Projector/src/org/lumen/projector/hal/Play6Codes.java')
for op in ('GET_POWER_ON_MODE', 'SET_POWER_ON_MODE'):
    ok('Z9X maps %s to its HalCodes constant' % op, '.put(%s, HalCodes.GMPF_%s)' % (op, op) in z9codes)
    ok('HB maps %s to its GmpfCall' % op, '.put(%s, GmpfCall.%s)' % (op, op) in hbcodes)
    ok('Play 6 does not map %s (nothing sent there)' % op, op not in p6codes)
for rel in ('apps/HbProjector/src/org/lumen/projector/hal/GmpfCall.java', 'apps/X50Projector/src/org/lumen/x50/projector/GmpfCall.java'):
    s = read(rel)
    ok('%s: 36 / 37 by HalCodes name on the factory interface' % rel.split('/')[1],
       'SET_POWER_ON_MODE(GmpfIface.FACTORY, HalCodes.FM_SET_POWER_ON_MODE, "setPowerOnMode")' in s
       and 'GET_POWER_ON_MODE(GmpfIface.FACTORY, HalCodes.FM_GET_POWER_ON_MODE, "getPowerOnMode")' in s)

# 3. who sends the write
SET_CALL = re.compile(r'\.setPowerOnMode\(')
TOGGLE_SET = re.compile(r'HalToggle\.POWER_ON_AC')
writers, toggles = [], []
for root, dirs, files in os.walk(os.path.join(G, 'apps')):
    dirs[:] = [x for x in dirs if x not in ('test', 'build', 'out')]
    for fn in files:
        if not fn.endswith('.java'):
            continue
        p = os.path.join(root, fn)
        rel = os.path.relpath(p, G)
        with open(p, encoding='utf-8') as f:
            s = re.sub(r'/\*.*?\*/|//[^\n]*', '', f.read(), flags=re.S)   # code only, not the javadoc
        if SET_CALL.search(s):
            writers.append(rel)
        if TOGGLE_SET.search(s):
            toggles.append(rel)
ok('setPowerOnMode is sent only by the Z9X and HB General rows: %s' % sorted(writers), sorted(writers) == [
    'apps/HbProjector/src/org/lumen/projector/panel/HbGeneralRows.java',
    'apps/ProjectorBase/src/org/lumen/projector/panel/Z9xGeneralRows.java'])
ok('the X50 switch is used only by X50PowerRows: %s' % sorted(toggles), sorted(toggles) == [
    'apps/X50Projector/src/org/lumen/x50/projector/X50PowerRows.java'])
z9rows = read('apps/ProjectorBase/src/org/lumen/projector/panel/Z9xGeneralRows.java')
hbrows = read('apps/HbProjector/src/org/lumen/projector/panel/HbGeneralRows.java')
x50rows = read('apps/X50Projector/src/org/lumen/x50/projector/X50PowerRows.java')
ok('Z9X writes it only in set(POWER_ON_AC)', re.search(r'case POWER_ON_AC: \{[^}]*g\.setPowerOnMode\(on\);', z9rows) is not None
   and z9rows.count('setPowerOnMode(') == 1)
ok('HB writes it only in writeHal(POWER_ON_AC)', re.search(r'case POWER_ON_AC: \{[^}]*g\.setPowerOnMode\(on\);', hbrows) is not None
   and hbrows.count('setPowerOnMode(') == 1)
ok('X50 writes it only in set(POWER_ON_AC)', x50rows.count('Sequences.toggle(hal, HalToggle.POWER_ON_AC') == 1
   and 'hal.set(' not in x50rows)
snap = read('apps/ProjectorBase/src/org/lumen/projector/panel/Snapshot.java')
ok('the Z9X / HB menu read only reads it', 'g::getPowerOnMode' in snap and 'setPowerOnMode' not in snap)
for rel in ('apps/HbProjector/src/org/lumen/projector/BootReceiver.java', 'apps/ProjectorBase/src/org/lumen/projector/BootReceiver.java',
            'apps/X50Projector/src/org/lumen/x50/projector/BootReceiver.java'):
    if os.path.exists(os.path.join(G, rel)):
        ok('%s never touches it' % rel, 'PowerOnMode' not in read(rel) and 'POWER_ON_AC' not in read(rel))

print('power_on_ac: ' + ('all green' if not FAILS else 'RED (%d)' % len(FAILS)))
sys.exit(1 if FAILS else 0)
