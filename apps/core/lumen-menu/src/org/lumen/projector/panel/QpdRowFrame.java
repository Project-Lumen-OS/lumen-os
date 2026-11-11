package org.lumen.projector.panel;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.lumen.projector.ui.CheckRow;
import org.lumen.projector.ui.ChoiceRow;
import org.lumen.projector.ui.HeaderRow;
import org.lumen.projector.ui.NavRow;
import org.lumen.projector.ui.PageAccess;
import org.lumen.projector.ui.Row;
import org.lumen.projector.ui.SliderRow;
import org.lumen.projector.ui.TextRow;
import org.lumen.projector.ui.Theme;
import org.lumen.projector.ui.ToggleRow;

import java.util.Objects;

/**
 * One row of a layout D popup or of the All settings list, drawn in the prototype look (.po in a popup, .r in the
 * list) around a shared row (ui/*Row). The shared row stays the focusable view inside this frame: it gets every key and
 * runs its own action, so every rule and vendor call is the one it always was; the frame only draws it: Inter, the
 * title on one or two lines (never cut; a title longer than two lines runs on focus with soft edges), the radio dot
 * on the left (filled ring for the chosen one), the 56 x 32 switch with a spring knob, value and chevron on the right,
 * the accent fill on focus. Rows of a type it does not know (equalizer bands, extension rows) draw themselves at 80 %.
 */
final class QpdRowFrame extends ViewGroup {
    static final int NATIVE = 0, RADIO = 1, TOGGLE = 2, NAV = 3, CHOICE = 4, SLIDER = 5, HEADER = 6, TEXT = 7;
    /** Scale of a row drawn by itself (its 30 px title becomes 24 px). */
    static final float NATIVE_SCALE = 0.8f;
    private static final CharSequence UNKNOWN = "\u2026";

    /** A radio row of layout D's own (Source popup): selected state, a value on the right, a status dot. */
    interface Radio {
        boolean isSelected();
        /** Text on the right (state), or null. */
        CharSequence radioValue();
        /** Status dot before the value: 1 = signal (green), 0 = none (grey), -1 = no dot. */
        int radioDot();
    }

    /** Sizes of one look, design px. */
    static final class Look {
        final float title, value, minH, header, desc, descSpacing;
        final boolean list;

        Look(float title, float value, float minH, float header, float desc, float descSpacing, boolean list) {
            this.title = title;
            this.value = value;
            this.minH = minH;
            this.header = header;
            this.desc = desc;
            this.descSpacing = descSpacing;
            this.list = list;
        }
    }

    static final Look POPUP = new Look(QpdLayout.ROW_TITLE, QpdLayout.ROW_VALUE, QpdLayout.ROW_MIN_H, 18, 18, 1.45f, false);
    static final Look LIST = new Look(QpdLayout.LIST_ROW_TITLE, QpdLayout.LIST_ROW_VALUE, QpdLayout.LIST_ROW_MIN_H, 17, 19,
            1.5f, true);

    final View row;
    final int kind;
    private final Look look;
    private final TextPaint titleP, valueP, headP, descP;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path chev = new Path();
    private boolean divider;

    private float focusT;
    private boolean wasFocused;
    private ValueAnimator focusAnim;
    private float knobT = -1f;
    private Boolean wasOn;
    private ValueAnimator knobAnim;

    // text layouts (rebuilt when the text or the width changes)
    private StaticLayout titleL;
    private CharSequence titleText;
    private int titleW = -1, titleMode = 1;
    private float titleNeed;
    private StaticLayout descL;
    private CharSequence descText;
    private int descW = -1;
    private long runStart = -1;
    private String measuredKey;

    QpdRowFrame(Context c, View row, Look look) {
        super(c);
        this.row = row;
        this.look = look;
        this.kind = kindOf(row);
        setWillNotDraw(false);
        setFocusable(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        // no clipChildren: a clipping parent lets View.draw quick-reject the empty-clipped row (drawChild), the row is
        // then never marked drawn and its later invalidate() calls (a switch read back) stop reaching this frame
        setClipChildren(false);
        titleP = QpdStyle.text(c, look.title, 500, QpdStyle.INK);
        valueP = QpdStyle.text(c, look.value, 450, QpdStyle.MUTE);
        headP = QpdStyle.text(c, look.header, 600, QpdStyle.MUTE);
        headP.setLetterSpacing((look.list ? 0.3f : 0.2f) / look.header);
        descP = QpdStyle.text(c, look.desc, 450, QpdStyle.MUTE);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        knobP.setShadowLayer(px(4), 0, px(1), QpdStyle.KNOB_SHADOW);
        if (kind == NATIVE) {
            row.setPivotX(0f);
            row.setPivotY(0f);
            row.setScaleX(NATIVE_SCALE);
            row.setScaleY(NATIVE_SCALE);
        }
        bind();
    }

    static int kindOf(View v) {
        if (v instanceof Radio) return RADIO;
        Class<?> c = v.getClass();
        if (c == CheckRow.class) return RADIO;
        if (c == ToggleRow.class) return TOGGLE;
        if (c == NavRow.class) return NAV;
        if (c == ChoiceRow.class) return CHOICE;
        if (c == SliderRow.class) return SLIDER;
        if (c == HeaderRow.class) return HEADER;
        if (c == TextRow.class) return TEXT;
        return NATIVE;
    }

    /** Takes the row back into this frame (it may sit in another container's frame since the last show). */
    void bind() {
        if (row.getParent() == this) return;
        if (row.getParent() instanceof ViewGroup) ((ViewGroup) row.getParent()).removeView(row);
        if (kind != NATIVE) {
            row.setScaleX(1f);
            row.setScaleY(1f);
        } else {
            row.setScaleX(NATIVE_SCALE);
            row.setScaleY(NATIVE_SCALE);
        }
        row.setTranslationX(0f);
        addView(row);
        wasFocused = row.isFocused();
        focusT = wasFocused ? 1f : 0f;
        measuredKey = null;
    }

    /** Thin line above the row (prototype .ln: the radio list ends, a link row follows). */
    void setDivider(boolean on) {
        if (divider == on) return;
        divider = on;
        requestLayout();
    }

    boolean isFocusableRow() {
        return row.getVisibility() == VISIBLE && row.isFocusable() && kind != HEADER && kind != TEXT;
    }

    // ------------------------------------------------------------------ sizes
    private float px(float d) { return Theme.pxf(getContext(), d); }

    private int dividerH() { return divider ? Math.round(px(17)) : 0; }

    /** Width this row needs to show its title on one line (real px), for the popup width. */
    float naturalWidth() {
        if (kind == NATIVE || kind == HEADER || kind == TEXT || row.getVisibility() == GONE) return 0f;
        CharSequence t = title();
        return 2 * px(QpdLayout.ROW_PAD) + leftW() + titleP.measureText(t, 0, t.length()) + gapAndRight(Float.MAX_VALUE);
    }

    private CharSequence title() {
        if (row instanceof Row) return ((Row) row).getTitle();
        return "";
    }

    private float leftW() {
        return kind == RADIO ? px(QpdLayout.DOT + QpdLayout.DOT_GAP) : 0f;
    }

    private CharSequence valueText() {
        switch (kind) {
            case RADIO: return row instanceof Radio ? ((Radio) row).radioValue() : null;
            case NAV: return ((NavRow) row).getValue();
            case CHOICE: {
                CharSequence l = PageAccess.choiceLabel((ChoiceRow) row);
                return l == null ? UNKNOWN : l;
            }
            case SLIDER: {
                CharSequence l = PageAccess.sliderLabel((SliderRow) row);
                return l == null ? UNKNOWN : l;
            }
            case TOGGLE: return ((ToggleRow) row).isChecked() == null ? UNKNOWN : null;
            default: return null;
        }
    }

    private float valueW(float rowW, float maxFrac) {
        CharSequence v = valueText();
        if (v == null || v.length() == 0) return 0f;
        float w = valueP.measureText(v, 0, v.length());
        return rowW == Float.MAX_VALUE ? w : Math.min(w, rowW * maxFrac);
    }

    private float dotW() {
        return row instanceof Radio && ((Radio) row).radioDot() >= 0 ? px(14 + 8) : 0f;
    }

    /** Gap before the right part plus the right part, real px. */
    private float gapAndRight(float rowW) {
        float gap = px(QpdLayout.VALUE_GAP);
        switch (kind) {
            case TOGGLE: {
                Boolean on = ((ToggleRow) row).isChecked();
                return gap + (on == null ? valueW(rowW, 0.3f) : px(QpdLayout.SWITCH_W));
            }
            case NAV: {
                float v = valueW(rowW, 0.42f);
                boolean ch = PageAccess.navChevron((NavRow) row);
                float w = v + (ch ? px(QpdLayout.CHEVRON) + (v > 0 ? px(QpdLayout.CHEVRON_GAP) : 0) : 0);
                return w > 0 ? gap + w : 0f;
            }
            case CHOICE:
                return gap + valueW(rowW, 0.45f) + 2 * (px(18) + px(10));
            case SLIDER:
                return gap + valueW(rowW, 0.3f);
            case RADIO: {
                float v = valueW(rowW, 0.42f);
                return v > 0 ? gap + dotW() + v : 0f;
            }
            default:
                return 0f;
        }
    }

    private int titleAvail(int w) {
        return Math.max(1, Math.round(w - 2 * px(QpdLayout.ROW_PAD) - leftW() - gapAndRight(w)));
    }

    private void layoutTitle(int avail) {
        CharSequence t = title();
        if (titleL != null && avail == titleW && TextUtils.equals(t, titleText)) return;
        titleText = t;
        titleW = avail;
        titleNeed = titleP.measureText(t, 0, t.length());
        StaticLayout all = StaticLayout.Builder.obtain(t, 0, t.length(), titleP, avail)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build();
        titleMode = QpdLayout.titleMode(titleNeed, avail, all.getLineCount());
        // more than two lines (rare): the first two are shown, the second fading out at its end (no ellipsis);
        // with the focus the whole title runs on one line
        titleL = all.getLineCount() <= QpdLayout.TITLE_LINES ? all
                : StaticLayout.Builder.obtain(t, 0, t.length(), titleP, avail).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).setMaxLines(QpdLayout.TITLE_LINES).build();
    }

    private StaticLayout descLayout(int w, CharSequence t, TextPaint p, float spacing) {
        int inner = Math.max(1, Math.round(w - 2 * px(QpdLayout.ROW_PAD)));
        if (descL != null && inner == descW && TextUtils.equals(t, descText) && descL.getPaint() == p) return descL;
        descText = t;
        descW = inner;
        descL = StaticLayout.Builder.obtain(t, 0, t.length(), p, inner).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).setLineSpacing(0f, spacing)
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build();
        return descL;
    }

    private String key() {
        StringBuilder b = new StringBuilder();
        b.append(row.getVisibility()).append('|').append(title()).append('|').append(valueText());
        if (kind == HEADER) b.append(PageAccess.headerText((HeaderRow) row));
        if (kind == TEXT) b.append(PageAccess.text((TextRow) row));
        if (kind == TOGGLE) b.append(((ToggleRow) row).isChecked() == null);
        return b.toString();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        measuredKey = key();
        if (row.getVisibility() == GONE) {
            setMeasuredDimension(w, 0);
            return;
        }
        int h;
        switch (kind) {
            case NATIVE: {
                row.measure(MeasureSpec.makeMeasureSpec(Math.round(w / NATIVE_SCALE), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                h = Math.round(row.getMeasuredHeight() * NATIVE_SCALE);
                break;
            }
            case HEADER: {
                CharSequence t = PageAccess.headerText((HeaderRow) row);
                StaticLayout l = descLayout(w, t, headP, 1.2f);
                h = t.length() == 0 ? 0 : Math.round(px(look.list ? 20 : 16) + l.getHeight() + px(look.list ? 6 : 8));
                break;
            }
            case TEXT: {
                CharSequence t = PageAccess.text((TextRow) row);
                StaticLayout l = descLayout(w, t, descP, look.descSpacing);
                h = t.length() == 0 ? 0 : Math.round(px(4) + l.getHeight() + px(12));   // an empty hint takes no room
                break;
            }
            default: {
                layoutTitle(titleAvail(w));
                float textH = titleMode == 1 ? titleP.descent() - titleP.ascent() : titleL.getHeight();
                h = Math.round(QpdLayout.rowHeight(textH, px(look.minH), px(QpdLayout.ROW_VPAD)));
                if (kind == SLIDER) h += Math.round(px(26));
            }
        }
        if (kind != NATIVE) {
            row.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(w, h + dividerH());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int rr, int b) {
        int top = dividerH();
        if (kind == NATIVE) row.layout(0, top, row.getMeasuredWidth(), top + row.getMeasuredHeight());
        else row.layout(0, top, rr - l, b - t);
    }

    // ------------------------------------------------------------------ state from the row
    @Override
    public void onDescendantInvalidated(View child, View target) {
        super.onDescendantInvalidated(child, target);
        sync();
        invalidate();
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        sync();
    }

    private void sync() {
        boolean f = row.isFocused();
        if (f != wasFocused) {
            wasFocused = f;
            animateFocus(f);
        }
        if (kind == TOGGLE) {
            Boolean on = ((ToggleRow) row).isChecked();
            if (!Objects.equals(on, wasOn)) {
                boolean animate = wasOn != null && on != null;
                wasOn = on;
                animateKnob(on != null && on, animate);
            }
        }
        if (measuredKey != null && !measuredKey.equals(key())) requestLayout();
    }

    private void animateFocus(boolean on) {
        if (focusAnim != null) focusAnim.cancel();
        runStart = on ? SystemClock.uptimeMillis() + QpdLayout.ROW_FOCUS_MS : -1;
        if (!isAttachedToWindow()) {
            focusT = on ? 1f : 0f;
            return;
        }
        focusAnim = ValueAnimator.ofFloat(focusT, on ? 1f : 0f);
        focusAnim.setDuration(QpdLayout.ROW_FOCUS_MS);
        focusAnim.setInterpolator(QpdStyle.ease());
        focusAnim.addUpdateListener(a -> {
            focusT = (float) a.getAnimatedValue();
            invalidate();
        });
        focusAnim.start();
    }

    private void animateKnob(boolean on, boolean animate) {
        if (knobAnim != null) knobAnim.cancel();
        float to = on ? 1f : 0f;
        if (!animate || !isAttachedToWindow() || knobT < 0f) {
            knobT = to;
            invalidate();
            return;
        }
        knobAnim = ValueAnimator.ofFloat(knobT, to);
        knobAnim.setDuration(QpdLayout.SWITCH_MS);
        knobAnim.setInterpolator(QpdStyle.spring());
        knobAnim.addUpdateListener(a -> {
            knobT = (float) a.getAnimatedValue();
            invalidate();
        });
        knobAnim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (focusAnim != null) focusAnim.cancel();
        if (knobAnim != null) knobAnim.cancel();
        focusT = row.isFocused() ? 1f : 0f;
        if (kind == TOGGLE) {
            Boolean on = ((ToggleRow) row).isChecked();
            knobT = on != null && on ? 1f : 0f;
        }
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------ drawing
    /**
     * A drawn row is "drawn" with an empty clip: the framework marks it drawn (so its next invalidate() reaches
     * onDescendantInvalidated again; a child never drawn reports only its first one) and it paints nothing.
     * The frame itself never clips its children for that reason (setClipChildren(false) in the constructor).
     */
    @Override
    protected boolean drawChild(Canvas c, View child, long drawingTime) {
        if (kind == NATIVE) return super.drawChild(c, child, drawingTime);
        int save = c.save();
        c.clipRect(0, 0, 0, 0);
        boolean more = super.drawChild(c, child, drawingTime);
        c.restoreToCount(save);
        return more;
    }

    @Override
    protected void onDraw(Canvas c) {
        // state read at draw time too: a row invalidated twice in one frame (title, then focus) reports only once
        sync();
        if (kind == NATIVE || row.getVisibility() == GONE) return;
        int w = getWidth();
        int top = dividerH();
        if (divider) {
            fill.setColor(QpdStyle.DIVIDER);
            float y = Math.round(px(8));
            c.drawRect(px(QpdLayout.ROW_PAD), y, w - px(QpdLayout.ROW_PAD), y + Math.max(1f, px(1)), fill);
        }
        if (kind == HEADER) {
            c.save();
            c.translate(px(QpdLayout.ROW_PAD), top + px(look.list ? 20 : 16));
            descLayout(w, PageAccess.headerText((HeaderRow) row), headP, 1.2f).draw(c);
            c.restore();
            return;
        }
        if (kind == TEXT) {
            c.save();
            c.translate(px(QpdLayout.ROW_PAD), top + px(4));
            descLayout(w, PageAccess.text((TextRow) row), descP, look.descSpacing).draw(c);
            c.restore();
            return;
        }
        boolean enabled = !(row instanceof Row) || ((Row) row).isRowEnabled();
        int save = enabled ? c.save() : c.saveLayerAlpha(0, top, w, getHeight(), 102);
        c.translate(0, top);
        drawRow(c, w, getHeight() - top);
        c.restoreToCount(save);
    }

    private void drawRow(Canvas c, int w, int h) {
        float f = focusT;
        if (f > 0.001f) {
            fill.setColor(QpdStyle.lerp(QpdStyle.ACCENT & 0x00FFFFFF, QpdStyle.ACCENT, f));
            r.set(0, 0, w, h);
            float rad = px(QpdLayout.ROW_RADIUS);
            c.drawRoundRect(r, rad, rad, fill);
        }
        float pad = px(QpdLayout.ROW_PAD);
        float lineH = h - (kind == SLIDER ? px(26) : 0f);
        float cy = lineH / 2f;
        float x = pad;
        if (kind == RADIO) {
            drawDot(c, x + px(QpdLayout.DOT) / 2f, cy, f);
            x += leftW();
        }
        // right part
        float right = w - pad;
        float rightStart = right - (gapAndRight(w) - px(QpdLayout.VALUE_GAP));
        drawRight(c, rightStart, right, cy, f);
        // title
        int avail = titleAvail(w);
        layoutTitle(avail);
        titleP.setColor(QpdStyle.lerp(QpdStyle.INK, 0xFFFFFFFF, f));
        if (titleMode == 2) {
            c.save();
            c.translate(x, cy - titleL.getHeight() / 2f);
            titleL.draw(c);
            c.restore();
        } else {
            float base = cy - (titleP.descent() + titleP.ascent()) / 2f;
            CharSequence t = titleText;
            if (titleMode == 0 && runStart >= 0 && row.isFocused()) {
                long ms = SystemClock.uptimeMillis() - runStart;
                long oneWay = QpdLayout.marqueeMs(titleNeed, avail, Theme.scale(getContext()));
                float dx = QpdLayout.marqueeShift(titleNeed, avail) * QpdLayout.marqueePhase(Math.max(0, ms), oneWay);
                QpdStyle.drawFaded(c, t, titleP, x, avail, 0, lineH, base, dx, px(QpdLayout.FADE));
                postInvalidateOnAnimation();
            } else if (titleMode == 0) {
                float top = cy - titleL.getHeight() / 2f;
                int save = c.saveLayer(x, top, x + avail, top + titleL.getHeight(), null);
                c.translate(x, top);
                titleL.draw(c);
                int last = titleL.getLineCount() - 1;
                QpdStyle.fadeEnd(c, 0, titleL.getLineTop(last), avail, titleL.getLineBottom(last), px(QpdLayout.FADE) * 2);
                c.restoreToCount(save);
            } else {
                c.drawText(t, 0, t.length(), x, base, titleP);
            }
        }
        if (kind == SLIDER) drawTrack(c, w, h, f);
    }

    private void drawDot(Canvas c, float cx, float cy, float f) {
        boolean sel = row instanceof Radio ? ((Radio) row).isSelected() : ((CheckRow) row).isChecked();
        float d = px(QpdLayout.DOT);
        if (sel) {
            float ring = px(QpdLayout.DOT_SEL);
            stroke.setStrokeWidth(ring);
            stroke.setColor(QpdStyle.lerp(QpdStyle.ACCENT, 0xFFFFFFFF, f));
            c.drawCircle(cx, cy, d / 2f - ring / 2f, stroke);
        } else {
            float ring = px(QpdLayout.DOT_RING);
            stroke.setStrokeWidth(ring);
            stroke.setColor(QpdStyle.DOT_OFF);
            c.drawCircle(cx, cy, d / 2f - ring / 2f, stroke);
        }
    }

    private void drawRight(Canvas c, float left, float right, float cy, float f) {
        valueP.setColor(QpdStyle.lerp(QpdStyle.MUTE, QpdStyle.ROW_VALUE_FOCUSED, f));
        float vBase = cy - (valueP.descent() + valueP.ascent()) / 2f;
        switch (kind) {
            case TOGGLE: {
                ToggleRow t = (ToggleRow) row;
                if (t.isChecked() == null) {
                    drawValueRight(c, UNKNOWN, right, vBase, right - left);
                    return;
                }
                if (knobT < 0f) knobT = t.isChecked() ? 1f : 0f;
                if (wasOn == null) wasOn = t.isChecked();
                drawSwitch(c, right, cy, f, t.isPending());
                return;
            }
            case NAV: {
                float x = right;
                if (PageAccess.navChevron((NavRow) row)) {
                    drawChevron(c, x - px(QpdLayout.CHEVRON), cy, px(QpdLayout.CHEVRON), false, 1f, f);
                    x -= px(QpdLayout.CHEVRON) + px(QpdLayout.CHEVRON_GAP);
                }
                CharSequence v = valueText();
                if (v != null && v.length() > 0) drawValueRight(c, v, x, vBase, x - left);
                return;
            }
            case CHOICE: {
                ChoiceRow ch = (ChoiceRow) row;
                int sel = ch.getSelected(), n = PageAccess.choiceCount(ch);
                float s = px(18), g = px(10);
                drawChevron(c, left, cy, s, true, sel <= 0 ? 0.3f : 1f, f);
                drawChevron(c, right - s, cy, s, false, sel < 0 || sel >= n - 1 ? 0.3f : 1f, f);
                CharSequence v = valueText();
                float room = right - left - 2 * (s + g);
                float vw = valueP.measureText(v, 0, v.length());
                if (vw > room && row.isFocused() && runStart >= 0) {
                    // the focused value runs between its arrows, never cut (soft edges)
                    long ms = SystemClock.uptimeMillis() - runStart;
                    long oneWay = QpdLayout.marqueeMs(vw, room, Theme.scale(getContext()));
                    float dx = QpdLayout.marqueeShift(vw, room) * QpdLayout.marqueePhase(Math.max(0, ms), oneWay);
                    QpdStyle.drawFaded(c, v, valueP, left + s + g, room, cy - px(20), cy + px(20), vBase, dx,
                            px(QpdLayout.FADE));
                    postInvalidateOnAnimation();
                    return;
                }
                if (vw > room) {
                    // unfocused and too long: the end fades out (prototype mask), no ellipsis
                    QpdStyle.drawFaded(c, v, valueP, left + s + g, room, cy - px(20), cy + px(20), vBase, 0f,
                            px(QpdLayout.FADE));
                    return;
                }
                c.drawText(v, 0, v.length(), left + s + g + (room - vw) / 2f, vBase, valueP);
                return;
            }
            case SLIDER:
                drawValueRight(c, valueText(), right, vBase, right - left);
                return;
            case RADIO: {
                CharSequence v = valueText();
                if (v == null || v.length() == 0) return;
                float vw = drawValueRight(c, v, right, vBase, right - left - dotW());
                int dot = ((Radio) row).radioDot();
                if (dot >= 0) {
                    fill.setColor(dot == 1 ? 0xFF34C759 : QpdStyle.lerp(0x59FFFFFF, 0x80FFFFFF, f));
                    c.drawCircle(right - vw - px(8) - px(7), cy, px(7), fill);
                }
                return;
            }
            default:
        }
    }

    /**
     * Value text right-aligned at {@code right}, at most {@code room} wide; a longer one fades out at its end (no
     * ellipsis). Returns the width used.
     */
    private float drawValueRight(Canvas c, CharSequence v, float right, float base, float room) {
        if (v == null || v.length() == 0 || room <= 0) return 0f;
        float tw = valueP.measureText(v, 0, v.length());
        if (tw <= room) {
            c.drawText(v, 0, v.length(), right - tw, base, valueP);
            return tw;
        }
        QpdStyle.drawFaded(c, v, valueP, right - room, room, base + valueP.ascent() - px(4), base + valueP.descent() + px(4),
                base, 0f, px(QpdLayout.FADE));
        return room;
    }

    /** Prototype .tg: 56 x 32 track, 26 px knob 3 px in; on = accent (focused: white track, accent knob). */
    private void drawSwitch(Canvas c, float right, float cy, float f, boolean pending) {
        float tw = px(QpdLayout.SWITCH_W), th = px(QpdLayout.SWITCH_H);
        float k = Math.max(0f, Math.min(1f, knobT));
        int onCol = QpdStyle.lerp(QpdStyle.ACCENT, QpdStyle.SWITCH_ON_FOCUSED, f);
        fill.setColor(QpdStyle.lerp(QpdStyle.SWITCH_OFF, onCol, k));
        if (pending) fill.setAlpha(fill.getAlpha() * 6 / 10);
        r.set(right - tw, cy - th / 2f, right, cy + th / 2f);
        c.drawRoundRect(r, th / 2f, th / 2f, fill);
        float kr = px(QpdLayout.KNOB) / 2f, inset = px(QpdLayout.KNOB_INSET);
        float x0 = right - tw + inset + kr, x1 = right - inset - kr;
        float kx = x0 + (x1 - x0) * knobT;
        knobP.setColor(QpdStyle.lerp(0xFFFFFFFF, QpdStyle.lerp(0xFFFFFFFF, QpdStyle.ACCENT, f), k));
        if (pending) knobP.setAlpha(0x99);
        c.drawCircle(kx, cy, kr, knobP);
    }

    /** Lucide chevron (24 grid, 2.2 stroke) in a {@code size} box from {@code left}; left-pointing when asked. */
    private void drawChevron(Canvas c, float left, float cy, float size, boolean pointLeft, float alpha, float f) {
        float u = size / 24f;
        chev.rewind();
        if (pointLeft) {
            chev.moveTo(left + 14.5f * u, cy - 6 * u);
            chev.lineTo(left + 8.5f * u, cy);
            chev.lineTo(left + 14.5f * u, cy + 6 * u);
        } else {
            chev.moveTo(left + 9.5f * u, cy - 6 * u);
            chev.lineTo(left + 15.5f * u, cy);
            chev.lineTo(left + 9.5f * u, cy + 6 * u);
        }
        stroke.setStrokeWidth(2.2f * u);
        int col = QpdStyle.lerp(QpdStyle.MUTE, QpdStyle.ROW_VALUE_FOCUSED, f);
        stroke.setColor((col & 0x00FFFFFF) | (Math.round((col >>> 24) * alpha) << 24));
        c.drawPath(chev, stroke);
    }

    private void drawTrack(Canvas c, int w, int h, float f) {
        SliderRow s = (SliderRow) row;
        float pad = px(QpdLayout.ROW_PAD);
        float th = px(10), cy = h - px(26);
        fill.setColor(QpdStyle.lerp(QpdStyle.ROW_TRACK, QpdStyle.ROW_TRACK_FOCUSED, f));
        r.set(pad, cy - th / 2f, w - pad, cy + th / 2f);
        c.drawRoundRect(r, th / 2f, th / 2f, fill);
        if (!s.isKnown() || s.getMax() <= s.getMin()) return;
        float frac = (s.getValue() - s.getMin()) / (float) (s.getMax() - s.getMin());
        float x = pad + (w - 2 * pad) * frac;
        fill.setColor(QpdStyle.lerp(QpdStyle.ACCENT, 0xFFFFFFFF, f));
        r.set(pad, cy - th / 2f, Math.max(pad + th, x), cy + th / 2f);
        c.drawRoundRect(r, th / 2f, th / 2f, fill);
        if (f > 0.001f) {
            knobP.setColor(0xFFFFFFFF);
            c.drawCircle(Math.max(pad + th / 2f, x), cy, px(11) * f, knobP);
        }
    }
}
