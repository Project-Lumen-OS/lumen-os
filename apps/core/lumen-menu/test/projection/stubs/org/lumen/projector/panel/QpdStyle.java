package org.lumen.projector.panel;

import android.content.Context;
import android.text.TextPaint;

import org.lumen.projector.ui.Theme;

import java.awt.Font;
import java.io.File;

/** Host stub of QpdStyle: the colours QpdTip uses and its text paint, Inter from -Dfont.inter when given. */
final class QpdStyle {
    static final int INK = 0xFFF4F4F6;
    static final int MUTE = 0x8FF4F4F6;
    static final int PANEL = 0xF016171A;
    static final int ACCENT = 0xFF3B78E7;

    private QpdStyle() {}

    private static Font base;

    static synchronized Font font() {
        if (base != null) return base;
        String path = System.getProperty("font.inter");
        try {
            base = path == null ? null : Font.createFont(Font.TRUETYPE_FONT, new File(path));
        } catch (Exception e) {
            base = null;
        }
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        return base;
    }

    static TextPaint text(Context c, float designSize, int weight, int color) {
        TextPaint p = new TextPaint(0);
        p.setTextSize(Theme.pxf(c, designSize));
        p.setColor(color);
        p.setAwtFont(font().deriveFont(weight >= 600 ? Font.BOLD : Font.PLAIN));
        return p;
    }
}
