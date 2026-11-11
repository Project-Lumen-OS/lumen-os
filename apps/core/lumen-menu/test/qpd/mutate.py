#!/usr/bin/env python3
"""Mutants of the quick panel layout D logic: each one must turn test/qpd red. python3 test/qpd/mutate.py"""
import os, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.normpath(os.path.join(HERE, '..', '..'))
SRC = os.path.join(APP, 'src/org/lumen/projector/panel/QpdLayout.java')
CTL = os.path.join(APP, 'src/org/lumen/projector/panel/QuickPanelController.java')
TEST = os.path.join(HERE, 'QpdLayoutTest.java')
FRM = os.path.join(APP, 'src/org/lumen/projector/panel/QpdRowFrame.java')
SRCP = os.path.join(APP, 'src/org/lumen/projector/panel/QpdSource.java')
PA = os.path.join(APP, 'src/org/lumen/projector/ui/PageAccess.java')
MIR = os.path.join(APP, 'src/org/lumen/projector/panel/QpdMirror.java')
# files run.sh reads, copied into each mutant tree
FILES = ['src/org/lumen/projector/panel/' + f for f in
         ('QpdLayout.java', 'QuickPanelController.java', 'QpdSource.java', 'QpdRowFrame.java', 'QpdMirror.java')] + \
        ['src/org/lumen/projector/ui/' + f for f in
         ('PageAccess.java', 'NavRow.java', 'ChoiceRow.java', 'SliderRow.java', 'HeaderRow.java', 'TextRow.java')] + \
        ['AndroidManifest.xml', 'res/drawable/qpd_ic_mirror.xml']

MUTANTS = [
    ('header from small column goes to the eye', SRC, 'nc = c == 0 ? 0 : 1;', 'nc = 0;'),
    ('RIGHT opens a popup on any tile', SRC, 'return hasPopup != null && hasPopup.test(row.get(c)) ? new int[]{r, c, OPEN} : STAY;',
     'return new int[]{r, c, OPEN};'),
    ('slider RIGHT asks for a popup', SRC, "            case RIGHT:\n                if (r == last && isSliderRow(rows)) return STAY;\n", "            case RIGHT:\n"),
    ('three small tiles per wide row', SRC, 'for (int k = 0; k < 2 && s < smalls.size(); k++)', 'for (int k = 0; k < 3 && s < smalls.size(); k++)'),
    ('marquee at any overflow', SRC, 'return textW - boxW > 2f;', 'return textW - boxW > 0f;'),
    ('marquee timeline without the 600 ms rest', SRC, 'long u = (t - 600) % (2 * oneWayMs);', 'long u = t % (2 * oneWayMs);'),
    ('popup may leave the top margin', SRC, '        if (top < MARGIN) top = MARGIN;\n', ''),
    ('popup not pushed up at the bottom', SRC, '        if (top > max) top = max;\n', ''),
    ('level not clamped to 1 on the track', SRC, 'int v = Math.max(1, Math.min(10, value));', 'int v = Math.min(10, value);'),
    ('Boost shown as a number', SRC, 'return isBoost(value) ? boostWord :', 'return false ? boostWord :'),
    ('Boost notice when it was already on at open', SRC, 'Boolean.TRUE.equals(now) && Boolean.FALSE.equals(before)',
     'Boolean.TRUE.equals(now) && !Boolean.TRUE.equals(before)'),
    ('Boost step without the 51 read', CTL, 'if (dir > 0 && !boostOn && snap != null && snap.boost != null) safe("boost"',
     'if (dir > 0 && !boostOn) safe("boost"'),
    ('USB tile beside the mirroring tile', CTL, 'else if (TILE_USB.equals(lastSmall)) small(TILE_USB',
     'small(TILE_USB'),
    # 2026-11-11 screen mirroring tile in place of USB (decision 4)
    ('USB kept over mirroring', SRC, 'if (mirroringInstalled) return TILE_MIRROR;', 'if (false) return TILE_MIRROR;'),
    ('mirroring tile only with USB files', SRC, 'if (mirroringInstalled) return TILE_MIRROR;',
     'if (mirroringInstalled && usbFiles) return TILE_MIRROR;'),
    ('USB tile without Files', SRC, 'return usbFiles ? TILE_USB : null;', 'return TILE_USB;'),
    ('switch trusts the wish when nobody answered', SRC, 'return readBack != null ? readBack : before;',
     'return readBack != null ? readBack : Boolean.TRUE;'),
    ('switch ignores the read back', SRC, 'return readBack != null ? readBack : before;', 'return before;'),
    ('mirroring tile key changed', SRC, 'TILE_MIRROR = "mirroring"', 'TILE_MIRROR = "miracast"'),
    ('mirroring popup without the receiver check', CTL, 'if (QpdMirror.installed(app)) {', 'if (true) {'),
    ('mirroring tile without its popup', CTL, 'if (TILE_MIRROR.equals(key)) return mirrorD == null ? null : mirrorD.page;', ''),
    ('mirroring tile chosen without the receiver', CTL, 'QpdLayout.lastSmallTile(mirrorD != null, usbFilesAvailable())',
     'QpdLayout.lastSmallTile(true, usbFilesAvailable())'),
    ('mirroring switch shows the wish on no answer', MIR, 'else row.setChecked(QpdLayout.settled(null, before));',
     'else row.setChecked(true);'),
    ('mirroring tile without the provider check', MIR, 'resolveContentProvider(AUTHORITY, 0) != null',
     'getPackageInfo(PKG, 0) != null'),
    ('Name row opens the plain settings', MIR, '                if (editName) i.putExtra("edit", "name");\n', ''),
    # 2026-11-11 polish: popups, rows, All settings, Source popup
    ('popup wider than 520', SRC, 'return Math.max(POP_W, Math.min(POP_W_MAX, w));', 'return Math.max(POP_W, w);'),
    ('popup narrower than 440', SRC, 'return Math.max(POP_W, Math.min(POP_W_MAX, w));', 'return Math.min(POP_W_MAX, w);'),
    ('popup pivot outside the popup', SRC, 'return Math.max(0f, Math.min(popH, tileCentre - popTop));', 'return tileCentre - popTop;'),
    ('long titles cut to one line', SRC, '        if (textW <= avail) return 1;\n', '        if (true) return 1;\n'),
    ('three-line titles wrap instead of running', SRC, 'return wrappedLines <= TITLE_LINES ? 2 : 0;', 'return 2;'),
    ('rail wraps around', SRC, 'if (n < 0 || n >= count || n == cur) return -1;', 'n = (n + count) % count; if (n == cur) return -1;'),
    ('Input tile without its popup', CTL, 'if (QuickPanel.SECTION_INPUT.equals(key)) return sourceD == null ? null : sourceD.page;', ''),
    ('gear opens a popup', CTL, 'return !QuickPanel.SECTION_GENERAL.equals(key) && popupPage(key) != null;', 'return popupPage(key) != null;'),
    ('gear tile not All settings', CTL, '                openAll(null, null);\n                return;', '                break;'),
    ('radio rows drawn as plain rows', FRM, 'if (c == CheckRow.class) return RADIO;', 'if (c == CheckRow.class) return NAV;'),
    ('source popup opens HDMI its own way', SRCP, 'SourceOverlay.openPort(app, r.port, "quick_panel")', 'false'),
    ('source popup writes the vendor', SRCP, 'src = gm.getCurrentInputSource();', 'src = gm.getCurrentInputSource(); gm.setInputSource(src);'),
    ('PageAccess reads a field the rows do not have', PA, '"NavRow.chevron", ', '"NavRow.chevronShown", '),
]


def run(tree_app):
    r = subprocess.run(['sh', os.path.join(tree_app, 'test/qpd/run.sh')], capture_output=True, text=True)
    return r.returncode, r.stdout + r.stderr


def tree(tmp):
    app = os.path.join(tmp, 'app')
    for f in FILES:
        os.makedirs(os.path.dirname(os.path.join(app, f)), exist_ok=True)
        shutil.copy(os.path.join(APP, f), os.path.join(app, f))
    os.makedirs(os.path.join(app, 'test/qpd'), exist_ok=True)
    for f in ('QpdLayoutTest.java', 'run.sh'):
        shutil.copy(os.path.join(HERE, f), os.path.join(app, 'test/qpd', f))
    return app


def main():
    # the unmutated copy must be green, or every "kill" below means nothing
    tmp = tempfile.mkdtemp(prefix='qpd_mut_')
    try:
        rc, out = run(tree(tmp))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    if rc != 0:
        print('BASELINE RED: ' + out[-600:])
        sys.exit(1)
    killed = 0
    for name, path, old, new in MUTANTS:
        tmp = tempfile.mkdtemp(prefix='qpd_mut_')
        try:
            app = tree(tmp)
            target = os.path.join(app, os.path.relpath(path, APP))
            s = open(target).read()
            if s.count(old) != 1:
                print('ANCHOR %d: %s' % (s.count(old), name)); continue
            open(target, 'w').write(s.replace(old, new))
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
