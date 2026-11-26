#!/usr/bin/env python3
# SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
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
ALL = PJ + 'panel/QpdAll.java'
PAN = PJ + 'panel/QpdPanel.java'
TILE = PJ + 'panel/QpdTile.java'
QEM = PJ + 'panel/QuickEditMode.java'
CARD = PJ + 'panel/QpdCard.java'
Z9XF = 'apps/ProjectorBase/src/org/lumen/projector/panel/Z9xFeatures.java'

MUTANTS = [
    ('section popup keeps the rows of the card tiles', CTL, '        for (String sec : spec.popups.keySet()) tilePopup(sec);\n', ''),
    ('popup built from the full section', CTL, '        List<FeatureId> keep = spec.popupRowsOf(sec);', '        List<FeatureId> keep = spec.rowsOf(sec);'),
    ('popup keeps every row', CTL, '            if (f != null && !keep.contains(f.parent != null ? f.parent : f)) continue;\n', ''),
    ('popup keeps an empty header', CTL, '            if (v instanceof HeaderRow && Collections.disjoint(headers.get(v), keep)) continue;\n', ''),
    ('extension rows miss the popup', CTL, '        tilePopup(sec);\n        Page shown =', '        Page shown ='),
    ('summary stays when its row leaves the popup', CTL, 'if (sum != 0) featureOf.put(p.add(new TextRow(app, s(sum))), f);',
     'if (sum != 0) p.add(new TextRow(app, s(sum)));'),
    ('header from small column goes to the eye', SRC, 'nc = c == 0 ? 0 : 1;', 'nc = 0;'),
    ('RIGHT opens a popup on any tile', SRC, 'return hasPopup != null && hasPopup.test(row.get(c)) ? new int[]{r, c, OPEN} : STAY;',
     'return new int[]{r, c, OPEN};'),
    ('right side: LEFT on the first cell opens nothing', SRC, 'return right ? opens(r, c, row, hasPopup) : STAY;', 'return STAY;'),
    ('right side: LEFT on the first cell opens on the left side too', SRC, 'return right ? opens(r, c, row, hasPopup) : STAY;',
     'return opens(r, c, row, hasPopup);'),
    ('right side: RIGHT on the last cell still opens', SRC, 'return right ? STAY : opens(r, c, row, hasPopup);',
     'return opens(r, c, row, hasPopup);'),
    ('open cell of the popup lost', SRC, '? new int[]{r, c, OPEN} : STAY;', '? new int[]{0, 0, OPEN} : STAY;'),
    ('the six argument move is a right side move', SRC, 'return move(rows, r, c, dir, hasPopup, slider, false);',
     'return move(rows, r, c, dir, hasPopup, slider, true);'),
    ('pane keys never mirrored', SRC, '        if (!right) return dir;\n', '        if (true) return dir;\n'),
    ('pane keys mirrored on the left side', SRC, '        if (!right) return dir;\n', ''),
    ('pane keys turn UP into LEFT', SRC, 'return dir == LEFT ? RIGHT : dir == RIGHT ? LEFT : dir;', 'return dir == LEFT ? RIGHT : LEFT;'),
    ('card move ignores the side', PAN, 'host::hasPopup, card::isSlider, right);', 'host::hasPopup, card::isSlider, false);'),
    ('side sheet placed from the left', PAN, '            side(lp, QpdLayout.SIDE_X);\n', '            lp.leftMargin = px(QpdLayout.SIDE_X);\n'),
    ('sheet page placed from the left', PAN, '            side(lp, isAllMode() ? QpdLayout.ALL_X : QpdLayout.SIDE_X);',
     '            lp.leftMargin = px(isAllMode() ? QpdLayout.ALL_X : QpdLayout.SIDE_X);'),
    ('popup slides in from the left on both sides', PAN, '        popup.setTranslationX(edge(Theme.pxf(ctx, QpdLayout.POP_SHIFT)));',
     '        popup.setTranslationX(-Theme.pxf(ctx, QpdLayout.POP_SHIFT));'),
    ('All settings slides in from the left on both sides', PAN, '        all.setTranslationX(edge(Theme.pxf(ctx, 30)));',
     '        all.setTranslationX(-Theme.pxf(ctx, 30));'),
    ('popup BACK key not mirrored', PAN, '                if (pane == QpdLayout.LEFT && down && ev.getRepeatCount() == 0) pop();',
     '                if (dir == QpdLayout.LEFT && down && ev.getRepeatCount() == 0) pop();'),
    ('sheet page BACK key not mirrored', PAN, '            if (pane == QpdLayout.LEFT && down && ev.getRepeatCount() == 0) pop();\n            return dir',
     '            if (dir == QpdLayout.LEFT && down && ev.getRepeatCount() == 0) pop();\n            return dir'),
    ('All settings keys not mirrored', PAN, 'return allKey(pane, down, ev.getRepeatCount());', 'return allKey(dir, down, ev.getRepeatCount());'),
    ('pane keys never computed for the side', PAN, 'int pane = QpdLayout.toward(dir, right);', 'int pane = dir;'),
    ('popup grows from the left edge on both sides', PAN, 'popup.setPivotX(right ? px(QpdLayout.SIDE_W) : 0f);', 'popup.setPivotX(0f);'),
    ('editor: the card reaches the sheet with RIGHT on both sides', QEM,
     'if (QpdLayout.toward(dir, right()) == QpdLayout.RIGHT) focusSheet(null);', 'if (dir == QpdLayout.RIGHT) focusSheet(null);'),
    ('editor: the sheet goes back with LEFT on both sides', QEM,
     'if (QpdLayout.toward(dir, right()) == QpdLayout.LEFT) {', 'if (dir == QpdLayout.LEFT) {'),
    ('editor: the side is always left', QEM, 'private boolean right() { return host.panel().isRight(); }',
     'private boolean right() { return false; }'),
    ('clock: never shrinks', SRC, 'return textW > boxW && boxW > 0 ? boxW / textW : 1f;', 'return 1f;'),
    ('clock: grows to the box', SRC, 'return textW > boxW && boxW > 0 ? boxW / textW : 1f;', 'return boxW > 0 ? boxW / textW : 1f;'),
    ('clock: fit measured but not applied', CARD, '            c.scale(fit, fit, 0, clockBase);\n', ''),
    ('All settings panes not mirrored', ALL, 'View[] order = panel.isRight() ? new View[]{tipCard, listCard, railCard} : new View[]{railCard, listCard, tipCard};',
     'View[] order = new View[]{railCard, listCard, tipCard};'),
    ('hint card slides from the left on both sides', ALL, 'tipCard.setTranslationX(panel.isRight() ? px(14) : -px(14));',
     'tipCard.setTranslationX(-px(14));'),
    ('slider RIGHT asks for a popup', SRC, "            case RIGHT:\n                if (slider.test(row.get(c))) return STAY;\n", "            case RIGHT:\n"),
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
    ('two-way slider fills from the left edge', PJ + 'panel/QpdLayout.java',
     'if (value == null || min >= 0 || max <= 0) return new float[]{0f, at};', 'if (true) return new float[]{0f, at};'),
    ('two-way slider unknown filled to 0', PJ + 'panel/QpdLayout.java',
     'if (value == null || min >= 0 || max <= 0) return new float[]{0f, at};', 'if (min >= 0 || max <= 0) return new float[]{0f, at};'),
    ('two-way slider fill without its knob side', PJ + 'panel/QpdLayout.java', 'return new float[]{Math.min(zero, at), Math.max(zero, at)};',
     'return new float[]{zero, Math.max(zero, at)};'),
    ('the row draws a two-way fill from the left edge', PJ + 'panel/QpdRowFrame.java',
     'float[] on = QpdLayout.fillSpan(s.getValue(), s.getMin(), s.getMax());', 'float[] on = {0f, QpdLayout.fill(s.getValue(), s.getMin(), s.getMax())};'),
    ('slider fill on a fixed range', PJ + 'panel/QpdSliderRow.java', 'QpdLayout.fill(lv, getMin(), getMax())',
     'QpdLayout.fill(lv, 1, 10)'),
    ('slider without the profile Boost level', CTL, '            lamp.setBoostLevel(spec.boostLevel());\n', ''),
    ('Boost notice when it was already on at open', SRC, 'Boolean.TRUE.equals(now) && Boolean.FALSE.equals(before)',
     'Boolean.TRUE.equals(now) && !Boolean.TRUE.equals(before)'),
    ('Boost step without the 51 read', CTL, '!boostOn() && state != null && state.on(FeatureId.BOOST) != null', '!boostOn()'),
    # 2026-11-13 eye protection tile, voice search button, no USB tile
    ('eye tile switch turns on what is on', SRC, 'if (!OFF.equals(now)) return OFF;', 'if (!OFF.equals(now)) return now;'),
    ('eye tile forgets the mode used last', SRC, 'return lastOn != null && !OFF.equals(lastOn) && options.contains(lastOn) ? lastOn : options.get(1);',
     'return options.get(1);'),
    ('eye tile turns on a mode the model lacks', SRC, 'return lastOn != null && !OFF.equals(lastOn) && options.contains(lastOn) ? lastOn : options.get(1);',
     'return lastOn != null && !OFF.equals(lastOn) ? lastOn : options.get(1);'),
    ('eye tile sends a guess on an unknown value', SRC, 'if (now == null || options.size() < 2 || !options.contains(now)) return null;',
     'if (options.size() < 2) return null;'),
    ('eye popup on a switch-only model', SRC, 'return options.size() > 2; }', 'return options.size() > 1; }'),
    ('eye tile OK opens the popup', CTL, '                case EYE: toggleEye(); return;\n', ''),
    ('eye tile past the row rules', CTL, '        choose(FeatureId.EYE, to);', '        set(FeatureId.EYE, to);'),
    ('eye RIGHT opens the switch page', CTL, '            if (FeatureId.EYE.id.equals(key) && !eyeHasPopup()) return false;   // a switch only: OK is all it does\n', ''),
    ('mirroring tile without the receiver', CTL, 'if (FeatureId.MIRRORING.id.equals(id)) return mirror != null;',
     'if (FeatureId.MIRRORING.id.equals(id)) return has(FeatureId.MIRRORING);'),
    ('held OK runs at once', PJ + 'panel/QpdTile.java', 'Runnable r = longOk ? onLongOk : onOk;', 'Runnable r = onOk;'),
    ('switch trusts the wish when nobody answered', SRC, 'return readBack != null ? readBack : before;',
     'return readBack != null ? readBack : Boolean.TRUE;'),
    ('switch ignores the read back', SRC, 'return readBack != null ? readBack : before;', 'return before;'),
    ('mirroring popup without the receiver check', CTL, 'if (has(FeatureId.MIRRORING) && QpdMirror.installed(app)) {',
     'if (has(FeatureId.MIRRORING)) {'),
    ('mirroring tile without its popup', CTL, '        if (FeatureId.MIRRORING.id.equals(key)) return mirror == null ? null : mirror.page;\n', ''),
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
    ('gear opens a popup', CTL, 'return !MenuSpec.allSettingsOnly(key) && popupPage(key) != null;', 'return popupPage(key) != null;'),
    ('gear tile not All settings', CTL, '        if (MenuSpec.GENERAL.equals(id)) { openAll(null, null); return; }\n', ''),
    ('radio rows drawn as plain rows', FRM, 'if (c == CheckRow.class) return RADIO;', 'if (c == CheckRow.class) return NAV;'),
    ('Z9X Input tile without the Source key step', Z9XF, 'case INPUT: org.lumen.source.SourceKey.press(app, "quick_menu"); return true;', 'case INPUT: return false;'),
    ('PageAccess reads a field the rows do not have', PA, '"NavRow.chevron", ', '"NavRow.chevronShown", '),
    # All settings list: greyed rows at the end come into view (owner photo: HB Picture, HDMI header without rows)
    ('list never shows its end', SRC, 'return listTarget(focusable, target, down) == target;', 'return false;'),
    ('list jumps to the end on every row', SRC, 'return listTarget(focusable, target, down) == target;', 'return true;'),
    ('list stops on greyed rows', SRC, 'if (focusable[i]) return i;', 'return i;'),
    ('list leaves at the end', SRC, 'if (focusable[i]) return i;\n        return at;', 'if (focusable[i]) return i;\n        return -1;'),
    ('All settings list without the end scroll', ALL,
     '        if (QpdLayout.listEnd(focusable, to, down)) scroll.smoothScrollTo(0, down ? list.getHeight() : 0);\n', ''),
    ('All settings list scrolls to the top at the bottom', ALL, 'scroll.smoothScrollTo(0, down ? list.getHeight() : 0);',
     'scroll.smoothScrollTo(0, 0);'),
    ('All settings list counts greyed rows as usable', ALL, 'focusable[i] = ((QpdRowFrame) c).isFocusableRow();',
     'focusable[i] = true;'),
    # popups land on the checked row, also when it is read after the open (the 3D key on a menu just built)
    ('popup never follows its checked row', SRC, 'return untouched && checked >= 0 ? checked : focused;', 'return focused;'),
    ('popup follows after a key', SRC, 'return untouched && checked >= 0 ? checked : focused;', 'return checked >= 0 ? checked : focused;'),
    ('popup focus to nothing when nothing is checked', SRC, 'return untouched && checked >= 0 ? checked : focused;',
     'return untouched ? checked : focused;'),
    ('a key keeps the follow', CTL, '        if (down) popupUntouched = false;', '        if (false) popupUntouched = false;'),
    ('direct open never follows', CTL, '        popupUntouched = target != null && focusRow == null;', '        popupUntouched = false;'),
    ('direct open follows a restored row', CTL, '        popupUntouched = target != null && focusRow == null;', '        popupUntouched = target != null;'),
    ('tile popup never follows', CTL, '        popupUntouched = true;\n        panel.push(p);', '        panel.push(p);'),
    ('apply keeps the focus where it is', CTL,
     'panel.refreshPage(QpdLayout.popupFocus(popupUntouched && !panel.isAllMode(), checkedIndex(panel.currentPage()), -1));',
     'panel.refreshPage();'),
    ('All settings follows the popup', CTL, '        popupUntouched = false;\n        List<QpdPanel.Cat> cats', '        List<QpdPanel.Cat> cats'),
    ('refreshPage loses the focus row', PAN, 'focusIndex >= 0 ? focusIndex : focusedIndex()', 'focusIndex'),
    # a small tile's label: smaller down to 11 px, else full size and running, never an ellipsis
    ('label shrinks below 11 px', SRC, 'return k * LABEL >= LABEL_MIN ? k : 1f;', 'return k;'),
    ('label never shrinks', SRC, 'return k * LABEL >= LABEL_MIN ? k : 1f;', 'return 1f;'),
    ('label never runs', SRC, 'return textW > room && room / textW * LABEL < LABEL_MIN;', 'return false;'),
    ('label runs where shrinking fits', SRC, 'return textW > room && room / textW * LABEL < LABEL_MIN;', 'return textW > room;'),
    ('label smallest size 10', SRC, 'static final int LABEL = 15, LABEL_MIN = 11;', 'static final int LABEL = 15, LABEL_MIN = 10;'),
    ('label ellipsized again', TILE, '            c.drawText(title, 0, title.length(), w / 2f, base, labelP);\n            return;',
     '            CharSequence e = TextUtils.ellipsize(title, labelP, room, TextUtils.TruncateAt.END);\n'
     '            c.drawText(e, 0, e.length(), w / 2f, base, labelP);\n            return;'),
    ('label size by a rule of its own', TILE, 'labelP.setTextSize(px(QpdLayout.LABEL) * QpdLayout.labelScale(tw, room));',
     'labelP.setTextSize(Math.max(px(11), px(15) * room / tw));'),
    ('running label stands still', TILE, 'dx = QpdLayout.marqueeShift(tw, room) * QpdLayout.marqueePhase(Math.max(0, t),',
     'dx = 0f * QpdLayout.marqueePhase(Math.max(0, t),'),
    ('running label without soft edges', TILE, 'QpdStyle.drawFaded(c, title, labelP, (w - room) / 2f, room, base + labelP.ascent() - px(4),\n'
     '                base + labelP.descent() + px(4), base, dx, px(QpdLayout.FADE));',
     'c.drawText(title, 0, title.length(), (w - room) / 2f + dx, base, labelP);'),
]


if __name__ == '__main__':
    scratch.main('apps/core/lumen-menu/test/qpd/run.sh', MUTANTS)
