#!/usr/bin/env python3
"""Mutants of the quick panel layout D logic and the controller wiring: each one must turn test/qpd red (scratch
copies, never in place: test/scratch.py).

  python3 apps/core/lumen-menu/test/qpd/mutate.py [-k substring]
"""
import os, sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import scratch  # noqa: E402

PJ = 'apps/core/lumen-menu/src/org/lumen/projector/'
SRC = PJ + 'panel/QpdLayout.java'
CTL = PJ + 'panel/MenuController.java'
FRM = PJ + 'panel/QpdRowFrame.java'
MIR = PJ + 'panel/QpdMirror.java'
PA = PJ + 'ui/PageAccess.java'
SRCP = 'apps/Z9xProjector/src/org/lumen/projector/panel/QpdSource.java'

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
    ('level not clamped to the range on the track', SRC, 'return (clamp(value, min, max) - min) / (float) (max - min);',
     'return (Math.min(max, value) - min) / (float) (max - min);'),
    ('track from 0, not from the range minimum', SRC, 'return (clamp(value, min, max) - min) / (float) (max - min);',
     'return clamp(value, min, max) / (float) max;'),
    ('track over a fixed 1..10', SRC, 'return (clamp(value, min, max) - min) / (float) (max - min);',
     'return (clamp(value, min, max) - 1) / 9f;'),
    ('Boost on a model without it', SRC, 'return boostLevel > 0 && value >= boostLevel;', 'return value >= boostLevel;'),
    ('Boost shown as a number', SRC, 'return isBoost(value, boostLevel) ? boostWord :', 'return false ? boostWord :'),
    ('slider fill on a fixed range', PJ + 'panel/QpdLampRow.java', 'QpdLayout.fill(lv, getMin(), getMax())',
     'QpdLayout.fill(lv, 1, 10)'),
    ('slider without the profile Boost level', CTL, '            lamp.setBoostLevel(bl);\n', ''),
    ('Boost notice when it was already on at open', SRC, 'Boolean.TRUE.equals(now) && Boolean.FALSE.equals(before)',
     'Boolean.TRUE.equals(now) && !Boolean.TRUE.equals(before)'),
    ('Boost step without the 51 read', CTL, '!boostOn() && state != null && state.on(FeatureId.BOOST) != null', '!boostOn()'),
    ('USB tile without the layout\'s choice', CTL, 'QpdLayout.TILE_USB.equals(l) ? FeatureId.USB_FILES.id : null;',
     'FeatureId.USB_FILES.id;'),
    # 2026-11-11 screen mirroring tile in place of USB (decision 4)
    ('USB kept over mirroring', SRC, 'if (mirroringInstalled) return TILE_MIRROR;', 'if (false) return TILE_MIRROR;'),
    ('mirroring tile only with USB files', SRC, 'if (mirroringInstalled) return TILE_MIRROR;',
     'if (mirroringInstalled && usbFiles) return TILE_MIRROR;'),
    ('USB tile without Files', SRC, 'return usbFiles ? TILE_USB : null;', 'return TILE_USB;'),
    ('switch trusts the wish when nobody answered', SRC, 'return readBack != null ? readBack : before;',
     'return readBack != null ? readBack : Boolean.TRUE;'),
    ('switch ignores the read back', SRC, 'return readBack != null ? readBack : before;', 'return before;'),
    ('mirroring tile key changed', SRC, 'TILE_MIRROR = "mirroring"', 'TILE_MIRROR = "miracast"'),
    ('mirroring popup without the receiver check', CTL, 'if (has(FeatureId.MIRRORING) && QpdMirror.installed(app)) {',
     'if (has(FeatureId.MIRRORING)) {'),
    ('mirroring tile without its popup', CTL, '        if (FeatureId.MIRRORING.id.equals(key)) return mirror == null ? null : mirror.page;\n', ''),
    ('mirroring tile chosen without the receiver', CTL, 'QpdLayout.lastSmallTile(mirror != null,', 'QpdLayout.lastSmallTile(true,'),
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
    ('Input tile without its popup', CTL,
     '        if (FeatureId.INPUT.id.equals(key)) return devicePages == null ? null : devicePages.page(FeatureId.INPUT, this);\n', ''),
    ('gear opens a popup', CTL, 'return !MenuSpec.GENERAL.equals(key) && popupPage(key) != null;', 'return popupPage(key) != null;'),
    ('gear tile not All settings', CTL, '        if (MenuSpec.GENERAL.equals(id)) { openAll(null, null); return; }\n', ''),
    ('radio rows drawn as plain rows', FRM, 'if (c == CheckRow.class) return RADIO;', 'if (c == CheckRow.class) return NAV;'),
    ('source popup opens HDMI its own way', SRCP, 'SourceOverlay.openPort(app, r.port, "quick_panel")', 'false'),
    ('source popup writes the vendor', SRCP, 'src = gm.getCurrentInputSource();', 'src = gm.getCurrentInputSource(); gm.setInputSource(src);'),
    ('PageAccess reads a field the rows do not have', PA, '"NavRow.chevron", ', '"NavRow.chevronShown", '),
]


if __name__ == '__main__':
    scratch.main('apps/core/lumen-menu/test/qpd/run.sh', MUTANTS)
