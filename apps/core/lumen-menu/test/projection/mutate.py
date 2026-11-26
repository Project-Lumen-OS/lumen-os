#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
"""Mutants of the Projection hint picture: each one must turn test/projection red (scratch copies, never in place:
test/scratch.py).

  python3 apps/core/lumen-menu/test/projection/mutate.py [-k substring]
"""
import os, sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import scratch  # noqa: E402

M = 'apps/core/lumen-menu/'
SET = M + 'src/org/lumen/menu/ProjectionSetup.java'
TXT = M + 'src/org/lumen/menu/utils/Texts.java'
TIP = M + 'src/org/lumen/projector/panel/QpdTip.java'
HP = M + 'src/org/lumen/projector/panel/HintPicture.java'
CTL = M + 'src/org/lumen/projector/panel/MenuController.java'
LAB = M + 'src/org/lumen/projector/panel/MenuLabels.java'
ALL = M + 'src/org/lumen/projector/panel/QpdAll.java'
STRIP = M + 'src/org/lumen/projector/panel/AdjustStrip.java'
PAN = M + 'src/org/lumen/projector/panel/QpdPanel.java'
EN = M + 'res/values/lumen_strings_qpd.xml'
RU = M + 'res/values-ru/lumen_strings_qpd.xml'
ZH = M + 'res/values-zh-rCN/lumen_strings_qpd.xml'

MUTANTS = [
    # the setup of a read-back
    ('rear read inverted', SET, 'if (s.rear == rear && s.mount.equals(mode)) return s;', 'if (s.rear != rear && s.mount.equals(mode)) return s;'),
    ('mode ignored', SET, 'if (s.rear == rear && s.mount.equals(mode)) return s;', 'if (s.rear == rear) return s;'),
    ('rear unknown taken as front', SET, 'if (mode == null || rear == null) return null;', 'if (mode == null) return null; if (rear == null) rear = false;'),
    ('ceiling from the table token', SET, 'public boolean ceiling() { return CEILING.equals(mount); }', 'public boolean ceiling() { return !TABLE.equals(mount); }'),
    ('side always rear', SET, 'public String side() { return rear ? "rear" : "front"; }', 'public String side() { return "rear"; }'),
    ('hint token not the projection picture', HP, '        if (ProjectionSetup.HINT.equals(hint)) return PROJECTION;\n', ''),
    # captions
    ('CJK sentences with a space', TXT, "boolean wide = last == '\\u3002'", "boolean wide = false && last == '\\u3002'"),
    ('sentences without a space', TXT, 'return a + (wide ? "" : " ") + b;', 'return a + "" + b;'),
    ('table caption for the ceiling', LAB, 'case "ceiling": return R.string.lumen_qpd_tip_ceiling;', 'case "ceiling": return R.string.lumen_qpd_tip_table;'),
    ('front side caption is the rear one', LAB, 'case "front": return R.string.lumen_qpd_tip_front;', 'case "front": return R.string.lumen_qpd_tip_rear;'),
    ('Auto caption missing', LAB, '            case "auto": return R.string.lumen_qpd_tip_auto;\n', ''),
    ('English front caption with a dash', EN, 'You and the projector are on the same side of the screen.',
     'You and the projector are on the same side \u2014 of the screen.'),
    ('Russian ceiling caption missing', RU, '<string name="lumen_qpd_tip_ceiling">', '<string name="lumen_qpd_tip_ceiling_x">'),
    ('Chinese auto caption equal to the table one', ZH, '投影仪自动识别正放还是倒挂，并翻转画面。', '投影仪放在桌面上。'),
    # the view: redraw, hide, copy
    ('no redraw on a change', TIP, '            requestLayout();\n            invalidate();\n', '            requestLayout();\n'),
    ('no relayout on a change', TIP, '            requestLayout();\n            invalidate();\n', '            invalidate();\n'),
    ('setup change not seen when the caption stays', TIP, 'if (s != setup || !now.contentEquals(text)) {', 'if (!now.contentEquals(text)) {'),
    ('caption not taken', TIP, '            text = now;\n', ''),
    ('redraw on every read-back', TIP, 'if (s != setup || !now.contentEquals(text)) {', 'if (true) {'),
    ('unknown setup still shown', TIP, 'setVisibility(s == null ? GONE : VISIBLE);', 'setVisibility(VISIBLE);'),
    ('copy does not follow', TIP, '        if (copy != null) copy.show(s, now);\n', ''),
    ('copy starts empty', TIP, '        t.setup = setup;\n', ''),
    # the picture
    ('always the table', TIP, 'drawProjector(c, w, h, s.rear, s.ceiling(), false);', 'drawProjector(c, w, h, s.rear, false, false);'),
    ('always the front', TIP, 'drawProjector(c, w, h, s.rear, s.ceiling(), false);', 'drawProjector(c, w, h, false, s.ceiling(), false);'),
    ('always the rear (the bug)', TIP, 'private static float dir(boolean rear) { return rear ? 1f : -1f; }', 'private static float dir(boolean rear) { return 1f; }'),
    ('table and ceiling heights swapped', TIP, 'return h * (ceiling ? 0.26f : 0.6f);', 'return h * (ceiling ? 0.6f : 0.26f);'),
    ('no beam', TIP, '            drawBeam(c, w, h, s.rear, s.ceiling());\n', ''),
    ('Auto drawn as a fixed placement', TIP, '        if (s.auto()) {', '        if (false) {'),
    ('Auto without the turn arrow', TIP, '            drawTurn(c, w, h, s.rear);\n', ''),
    ('Auto projectors solid', TIP, 'p.setColor(ghost ? BODY & GHOST : BODY);', 'p.setColor(BODY);'),
    ('lens on the far end', TIP, 'c.drawCircle(near + d * px(12), cy, h * 0.035f, p);', 'c.drawCircle(near - d * px(12), cy, h * 0.035f, p);'),
    ('no ceiling mount', TIP, '            c.drawRect(mid - px(3), h * 0.12f, mid + px(3), cy - half, p);\n', ''),
    ('no viewer', TIP, '        drawViewer(c, w, h);\n', ''),
    # controller wiring
    ('apply does not redraw the picture', CTL, '        for (QpdTip t : setupTips) showSetup(t, st);\n', ''),
    ('picture never registered for redraws', CTL, '        setupTips.add(t);\n', ''),
    ('picture built without the state', CTL, '        showSetup(t, state);\n', ''),
    ('fixed rear text again', CTL, 'if (kind != HintPicture.PROJECTION) {',
     'if (kind != null) { if (kind != null) return new QpdTip(app, kind, s(R.string.lumen_qpd_tip_rear));'),
    ('caption sentences swapped', CTL, 'Texts.sentences(s(MenuLabels.tipMount(s.mount)), s(MenuLabels.tipSide(s.side())))',
     'Texts.sentences(s(MenuLabels.tipSide(s.side())), s(MenuLabels.tipMount(s.mount)))'),
    ('All settings card a frozen copy', ALL, '        QpdTip t = src.copyFor(rowTitle);', '        QpdTip t = new QpdTip(getContext(), src.kind(), src.text(), rowTitle);'),
    ('All settings card for a hidden hint', ALL, 'if (!(popupTip instanceof QpdTip) || popupTip.getVisibility() == GONE) {',
     'if (!(popupTip instanceof QpdTip)) {'),
    # the stock picture adjustment strip (4PDA: OK on a picture setting hides the menu, three rows at the bottom centre)
    ('strip: the track is not filled', STRIP, 'box.set(l, ty - th / 2, x, ty + th / 2);', 'box.set(l, ty - th / 2, l, ty + th / 2);'),
    ('strip: a closed row in the accent colour', STRIP, 'int acc = it.enabled ? QpdStyle.ACCENT : alpha(QpdStyle.INK, 0x66);', 'int acc = QpdStyle.ACCENT;'),
    ('strip: no row motion', STRIP, 'float dy = shift * ROW_H * k;', 'float dy = 0;'),
    ('strip: a choice without its right arrow', STRIP, '            arrow(c, r - a / 2f, cy, 1, a);\n', ''),
    ('strip: a value not read shown as read', STRIP, 'return new Item(title, known ? text : null, min, max, val, true, enabled && known);',
     'return new Item(title, text, min, max, val, true, enabled);'),
    ('strip: square corners', STRIP, 'c.drawRoundRect(box, RADIUS * k, RADIUS * k, fill);', 'c.drawRect(box, fill);'),
    ('adjust: OK no longer opens the strip', CTL, '        if (adjust().onKey(ev, f)) return true;               // OK on a picture setting: the strip, its UP / DOWN / OK / BACK\n', ''),
    ('adjust: the scrim stays over the film', PAN, '        if (root.getBackground() != null) root.getBackground().setAlpha(on ? 0 : 255);\n', ''),
    ('adjust: the light slider adjusted in the strip', CTL, 'return f != null && f != FeatureId.LAMP && f.id.startsWith(MenuSpec.PICTURE + ".");',
     'return f != null && f.id.startsWith(MenuSpec.PICTURE + ".");'),
    ('lamp tile: its page without the light slider', CTL, '                    addLampRow(p);                            // the light slider (the lamp page: its tile look opens it)\n', ''),
]


if __name__ == '__main__':
    scratch.main('apps/core/lumen-menu/test/projection/run.sh', MUTANTS)
