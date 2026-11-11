package org.lumen.projector.panel;

import android.content.Context;
import android.graphics.Canvas;
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

import org.lumen.projector.ui.Theme;

/**
 * Hint block at the top of a popup: a picture and a short text. Only for the items the Z9X stock settings show a
 * hint picture for (settings_tree_product_dapeng: picture mode image_mode_3_state and rear projection
 * projection_settings.rearProject); every other popup is text rows only. The pictures are our own drawings (XGIMI
 * drawables are not shipped): simple shapes in Lumen colours, drawn once per size.
 */
final class QpdTip extends View {
    static final int PICTURE_MODE = 1, REAR_PROJECTION = 2;

    private final int kind;
    private final CharSequence text;
    /** All settings hint card (prototype #tip): the item's name between the picture and the text; null = popup. */
    private final CharSequence title;
    private final TextPaint tp, titleP;
    private StaticLayout layout, titleLayout;
    private int layoutW = -1;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    QpdTip(Context c, int kind, CharSequence text) {
        this(c, kind, text, null);
    }

    QpdTip(Context c, int kind, CharSequence text, CharSequence title) {
        super(c);
        this.kind = kind;
        this.text = text;
        this.title = title;
        // popup: .pdesc 18 px; All settings card: .td 19 px under a 24 px / 650 title (.tt)
        tp = QpdStyle.text(c, title == null ? 18 : 19, 450, QpdStyle.MUTE);
        titleP = QpdStyle.text(c, 24, 650, QpdStyle.INK);
        setFocusable(false);
    }

    int kind() { return kind; }

    CharSequence text() { return text; }

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
        if (kind == PICTURE_MODE) drawPictureModes(c, w, ih);
        else drawRear(c, w, ih);
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

    /** Seen from above: the viewer on the left, the screen in the middle, the projector behind it on the right. */
    private void drawRear(Canvas c, int w, int h) {
        p.setShader(new LinearGradient(0, 0, w, h, 0xFF1D2433, 0xFF10131A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        float cy = h / 2f, sx = w * 0.5f;
        // beam from the projector to the back of the screen
        p.setShader(new LinearGradient(w * 0.78f, 0, sx, 0, 0x553B78E7, 0xCC9CC3FF, Shader.TileMode.CLAMP));
        path.reset();
        path.moveTo(w * 0.78f, cy - h * 0.04f);
        path.lineTo(sx + px(4), cy - h * 0.34f);
        path.lineTo(sx + px(4), cy + h * 0.34f);
        path.lineTo(w * 0.78f, cy + h * 0.04f);
        path.close();
        c.drawPath(path, p);
        p.setShader(null);
        // screen
        p.setColor(0xFFEFF3FA);
        r.set(sx - px(5), cy - h * 0.36f, sx + px(5), cy + h * 0.36f);
        c.drawRoundRect(r, px(4), px(4), p);
        // projector
        p.setColor(0xFFB9C2D0);
        r.set(w * 0.78f, cy - h * 0.12f, w * 0.9f, cy + h * 0.12f);
        c.drawRoundRect(r, px(8), px(8), p);
        p.setColor(0xFF3B78E7);
        c.drawCircle(w * 0.78f + px(10), cy, h * 0.045f, p);
        // viewer: head and shoulders from above, facing the screen
        p.setColor(0xFFF4F4F6);
        float vx = w * 0.2f;
        r.set(vx - h * 0.09f, cy - h * 0.2f, vx + h * 0.05f, cy + h * 0.2f);
        c.drawRoundRect(r, h * 0.07f, h * 0.07f, p);
        c.drawCircle(vx + h * 0.1f, cy, h * 0.075f, p);
        // eyes line: looking right, at the screen
        p.setColor(0x80F4F4F6);
        p.setStrokeWidth(px(3));
        p.setPathEffect(new android.graphics.DashPathEffect(new float[]{px(8), px(8)}, 0));
        c.drawLine(vx + h * 0.2f, cy, sx - px(14), cy, p);
        p.setPathEffect(null);
    }
}
