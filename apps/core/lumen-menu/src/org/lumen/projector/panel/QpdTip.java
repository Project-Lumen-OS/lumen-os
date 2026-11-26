// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

import org.lumen.menu.ProjectionSetup;
import org.lumen.projector.R;
import org.lumen.projector.ui.Theme;

/**
 * Hint block at the top of a popup: a picture and a short text. Only for the items the stock settings show a hint
 * picture for (picture mode image_mode_3_state, rear projection projection_settings.rearProject, the X50 family focus_tab
 * remote_focus_img_guide: the remote's side focus key); every other popup is text rows only. The pictures are our own
 * drawings (XGIMI drawables are not shipped): simple shapes in Lumen colours. The projection picture is the setup read
 * back now, redrawn by {@link #show} on every change.
 */
final class QpdTip extends View {
    private static final int BODY = 0xFFB9C2D0, LENS = 0xFF3B78E7, STAND = 0xFF5A6476, ROOM = 0x14FFFFFF,
            ROOM_LINE = 0x33FFFFFF, SCREEN = 0xFFEFF3FA, PERSON = 0xFFF4F4F6, GAZE = 0x80F4F4F6, GHOST = 0x66FFFFFF;

    private final HintPicture kind;
    private CharSequence text;
    /** All settings hint card (prototype #tip): the item's name between the picture and the text; null = popup. */
    private final CharSequence title;
    /** PROJECTION: the setup drawn; null = not known, the hint is not shown. */
    private ProjectionSetup setup;
    /** The All settings card's copy of this hint: it follows every change. */
    private QpdTip copy;
    private final TextPaint tp, titleP;
    private StaticLayout layout, titleLayout;
    private int layoutW = -1;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    QpdTip(Context c, HintPicture kind, CharSequence text) {
        this(c, kind, text, null);
    }

    QpdTip(Context c, HintPicture kind, CharSequence text, CharSequence title) {
        super(c);
        this.kind = kind;
        this.text = text == null ? "" : text;
        this.title = title;
        // popup: .pdesc 18 px; All settings card: .td 19 px under a 24 px / 650 title (.tt)
        tp = QpdStyle.text(c, title == null ? 18 : 19, 450, QpdStyle.MUTE);
        titleP = QpdStyle.text(c, 24, 650, QpdStyle.INK);
        setFocusable(false);
    }

    HintPicture kind() { return kind; }

    CharSequence text() { return text; }

    ProjectionSetup setup() { return setup; }

    /** PROJECTION: draws {@code s} with its caption, null hides the hint; the All settings copy follows. */
    void show(ProjectionSetup s, CharSequence caption) {
        String now = caption == null ? "" : caption.toString();
        if (s != setup || !now.contentEquals(text)) {
            setup = s;
            text = now;
            layoutW = -1;
            requestLayout();
            invalidate();
        }
        setVisibility(s == null ? GONE : VISIBLE);
        if (copy != null) copy.show(s, now);
    }

    /** The All settings card's copy with the row's name; from now on it follows this hint. */
    QpdTip copyFor(CharSequence rowTitle) {
        QpdTip t = new QpdTip(getContext(), kind, text, rowTitle);
        t.setup = setup;
        t.setVisibility(getVisibility());
        copy = t;
        return t;
    }

    private float px(float d) { return Theme.pxf(getContext(), d); }

    private int imgH(int w) { return Math.round(w * 9f / 16f); }

    /** Text inset: 18 px in a popup (.pdesc), 6 px in the All settings card (.tt / .td). */
    private float inset() { return px(title == null ? 18 : 6); }

    private StaticLayout layoutFor(int w) {
        int tw = Math.max(1, w - Math.round(2 * inset()));
        if (layout == null || layoutW != tw) {
            layout = StaticLayout.Builder.obtain(text, 0, text.length(), tp, tw)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, title == null ? 1.45f : 1.5f)
                    .setIncludePad(false).build();
            if (title != null) {
                titleLayout = StaticLayout.Builder.obtain(title, 0, title.length(), titleP, tw)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();
            }
            layoutW = tw;
        }
        return layout;
    }

    private float titleBlock() { return titleLayout == null ? 0f : px(20) + titleLayout.getHeight() + px(8); }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        StaticLayout l = layoutFor(w);
        int h = imgH(w) + Math.round(title == null ? px(12) : titleBlock()) + l.getHeight()
                + Math.round(px(title == null ? 14 : 6));
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int ih = imgH(w);
        float rad = px(title == null ? 24 : 28);
        int save = c.save();
        path.reset();
        r.set(0, 0, w, ih);
        path.addRoundRect(r, rad, rad, Path.Direction.CW);
        c.clipPath(path);
        if (kind == HintPicture.PICTURE_MODE) drawPictureModes(c, w, ih);
        else if (kind == HintPicture.FOCUS_FRONT) drawRemoteFocus(c, w, ih, R.drawable.lumen_focus_guide_remote_front);
        else if (kind == HintPicture.FOCUS_SIDE) drawRemoteFocus(c, w, ih, R.drawable.lumen_focus_guide_remote_side);
        else drawProjection(c, w, ih);
        c.restoreToCount(save);
        StaticLayout l = layoutFor(w);
        float y = ih;
        if (titleLayout != null) {
            c.save();
            c.translate(inset(), y + px(20));
            titleLayout.draw(c);
            c.restore();
            y += titleBlock();
        } else {
            y += px(12);
        }
        c.save();
        c.translate(inset(), y);
        l.draw(c);
        c.restore();
    }

    /** Three takes of one landscape: cool and soft, natural, warm and vivid. */
    private void drawPictureModes(Canvas c, int w, int h) {
        int[][] grades = {
                {0xFF3C4E6B, 0xFF8BA3C2, 0xFFDDE6EE, 0xFF4A5E57, 0xFF2F3F3A},
                {0xFF2C5DA8, 0xFF7FB2E8, 0xFFFFE9A8, 0xFF3F8A5A, 0xFF27613E},
                {0xFF7A2E52, 0xFFF08A4B, 0xFFFFD36B, 0xFF6E8F2A, 0xFF3E5A16}};
        float pw = w / 3f;
        for (int i = 0; i < 3; i++) {
            int[] g = grades[i];
            float x0 = i * pw, x1 = x0 + pw;
            p.setShader(new LinearGradient(0, 0, 0, h, g[0], g[1], Shader.TileMode.CLAMP));
            c.drawRect(x0, 0, x1, h, p);
            p.setShader(new RadialGradient(x0 + pw * 0.62f, h * 0.36f, h * 0.16f, g[2], g[2] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(x0 + pw * 0.62f, h * 0.36f, h * 0.16f, p);
            p.setShader(null);
            p.setColor(g[3]);
            path.reset();
            path.moveTo(x0, h * 0.72f);
            path.quadTo(x0 + pw * 0.35f, h * 0.48f, x0 + pw * 0.7f, h * 0.66f);
            path.quadTo(x0 + pw * 0.86f, h * 0.74f, x1, h * 0.62f);
            path.lineTo(x1, h);
            path.lineTo(x0, h);
            path.close();
            c.drawPath(path, p);
            p.setColor(g[4]);
            path.reset();
            path.moveTo(x0, h * 0.86f);
            path.quadTo(x0 + pw * 0.5f, h * 0.7f, x1, h * 0.84f);
            path.lineTo(x1, h);
            path.lineTo(x0, h);
            path.close();
            c.drawPath(path, p);
        }
        p.setColor(0x66000000);
        p.setStrokeWidth(px(3));
        for (int i = 1; i < 3; i++) c.drawLine(i * pw, 0, i * pw, h, p);
    }

    /**
     * Side view of the room: the viewer on the left looking at the screen; the projector on the viewer's side of the
     * screen (front) or behind it (rear), on its table or upside down under the ceiling, its beam on the screen. Auto:
     * both places faint with a turn arrow and no beam (stock shows no orientation under Auto).
     */
    private void drawProjection(Canvas c, int w, int h) {
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF1D2433, 0xFF10131A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        ProjectionSetup s = setup;
        if (s == null) return;
        float floor = h * 0.86f, ceil = h * 0.12f, sx = w * 0.55f;
        p.setColor(ROOM);
        c.drawRect(0, floor, w, h, p);
        c.drawRect(0, 0, w, ceil, p);
        p.setColor(ROOM_LINE);
        c.drawRect(0, floor, w, floor + px(2), p);
        c.drawRect(0, ceil - px(2), w, ceil, p);
        if (s.auto()) {
            drawProjector(c, w, h, s.rear, false, true);
            drawProjector(c, w, h, s.rear, true, true);
            drawTurn(c, w, h, s.rear);
        } else {
            drawBeam(c, w, h, s.rear, s.ceiling());
            drawProjector(c, w, h, s.rear, s.ceiling(), false);
        }
        p.setColor(SCREEN);
        r.set(sx - px(5), h * 0.2f, sx + px(5), h * 0.74f);
        c.drawRoundRect(r, px(4), px(4), p);
        drawViewer(c, w, h);
    }

    /** Body centre height of the projector on the table or under the ceiling. */
    private static float bodyY(int h, boolean ceiling) { return h * (ceiling ? 0.26f : 0.6f); }

    /** -1: the projector is left of the screen (front, the viewer's side), +1: behind it (rear). */
    private static float dir(boolean rear) { return rear ? 1f : -1f; }

    /** x of the projector's end that faces the screen. */
    private static float nearEnd(int w, boolean rear) { return w * (0.55f + dir(rear) * 0.19f); }

    private void drawBeam(Canvas c, int w, int h, boolean rear, boolean ceiling) {
        float d = dir(rear), lx = nearEnd(w, rear), ly = bodyY(h, ceiling), face = w * 0.55f + d * px(5);
        p.setShader(new LinearGradient(lx, 0, face, 0, 0x553B78E7, 0xCC9CC3FF, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(lx, ly - h * 0.03f);
        path.lineTo(face, h * 0.22f);
        path.lineTo(face, h * 0.72f);
        path.lineTo(lx, ly + h * 0.03f);
        path.close();
        c.drawPath(path, p);
        p.setShader(null);
    }

    /** The projector on its table, or upside down on a ceiling mount; {@code ghost} = faint (Auto). */
    private void drawProjector(Canvas c, int w, int h, boolean rear, boolean ceiling, boolean ghost) {
        float d = dir(rear), near = nearEnd(w, rear), far = near + d * w * 0.16f;
        float left = Math.min(near, far), right = Math.max(near, far), mid = (left + right) / 2f;
        float cy = bodyY(h, ceiling), half = h * 0.06f;
        p.setColor(ghost ? STAND & GHOST : STAND);
        if (ceiling) {
            c.drawRect(mid - w * 0.04f, h * 0.12f, mid + w * 0.04f, h * 0.12f + px(5), p);
            c.drawRect(mid - px(3), h * 0.12f, mid + px(3), cy - half, p);
        } else {
            float top = cy + half, tl = left - w * 0.04f, tr = right + w * 0.04f;
            c.drawRect(tl, top, tr, top + h * 0.025f, p);
            c.drawRect(tl + w * 0.02f, top, tl + w * 0.02f + px(6), h * 0.86f, p);
            c.drawRect(tr - w * 0.02f - px(6), top, tr - w * 0.02f, h * 0.86f, p);
        }
        p.setColor(ghost ? BODY & GHOST : BODY);
        r.set(left, cy - half, right, cy + half);
        c.drawRoundRect(r, px(8), px(8), p);
        p.setColor(ghost ? LENS & GHOST : LENS);
        c.drawCircle(near + d * px(12), cy, h * 0.035f, p);
    }

    /** Auto: a two-way arrow between the table and the ceiling place, bowed towards the screen. */
    private void drawTurn(Canvas c, int w, int h, boolean rear) {
        float d = dir(rear), x = nearEnd(w, rear) - d * w * 0.04f;
        float top = bodyY(h, true), bottom = bodyY(h, false), rad = (bottom - top) / 2f;
        p.setColor(LENS);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(px(4));
        r.set(x - rad, top, x + rad, bottom);
        path.reset();
        path.arcTo(r, rear ? 110f : -70f, 140f);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        // the arc ends at 70 degrees from its middle: cos 70 = 0.342, 1 - sin 70 = 0.06
        float a = px(9), ex = x - d * rad * 0.342f, ty = top + rad * 0.06f, by = bottom - rad * 0.06f;
        path.reset();
        for (float y : new float[]{ty, by}) {
            path.moveTo(ex + d * a, y);
            path.lineTo(ex - d * a * 0.6f, y - a);
            path.lineTo(ex - d * a * 0.6f, y + a);
            path.close();
        }
        c.drawPath(path, p);
    }

    /** The viewer standing on the left (head and rounded shoulders), looking at the screen. */
    private void drawViewer(Canvas c, int w, int h) {
        float vx = w * 0.08f, head = h * 0.06f, hy = h * 0.42f, body = head * 1.3f;
        p.setColor(PERSON);
        r.set(vx - body, hy + head + px(3), vx + body, h * 0.86f);
        c.drawRoundRect(r, body, body, p);
        c.drawCircle(vx, hy, head, p);
        p.setColor(GAZE);
        p.setStrokeWidth(px(3));
        p.setPathEffect(new DashPathEffect(new float[]{px(8), px(8)}, 0));
        c.drawLine(vx + head + px(6), hy, w * 0.55f - px(14), hy, p);
        p.setPathEffect(null);
    }

    /** The model's remote with its focus key (res/drawable/lumen_focus_guide_remote_front / _side.xml), centred. */
    private void drawRemoteFocus(Canvas c, int w, int h, int picture) {
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF1D2433, 0xFF10131A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        android.graphics.drawable.Drawable d = getContext().getDrawable(picture);
        if (d == null) return;
        float s = Math.min(w / (float) d.getIntrinsicWidth(), h * 0.92f / d.getIntrinsicHeight());
        int dw = Math.round(d.getIntrinsicWidth() * s), dh = Math.round(d.getIntrinsicHeight() * s);
        int x = (w - dw) / 2, y = (h - dh) / 2;
        d.setBounds(x, y, x + dw, y + dh);
        d.draw(c);
    }
}
