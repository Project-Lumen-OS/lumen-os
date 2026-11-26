// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.setup.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import org.lumen.ui.Palette;

/**
 * The field behind every step: the base tone running into bg_low at the bottom left (165 degrees)
 * and the soft light of bg_glow in the top right corner, the theme's colours only. The picture step adds its grid.
 */
public class AuroraView extends View {
    private final Paint mBase = new Paint();
    private final Paint mGlow = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AuroraView(Context c) {
        super(c);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        // linear-gradient(165deg, bg 0%, bg 55%, bg_low 100%)
        double a = Math.toRadians(165 - 90);
        float dx = (float) Math.cos(a) * h / 2f, dy = (float) Math.sin(a) * h / 2f;
        mBase.setShader(new LinearGradient(w / 2f - dx, h / 2f - dy, w / 2f + dx, h / 2f + dy,
                new int[]{Ui.BG, Ui.BG, Ui.BG_BOTTOM}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        // radial-gradient(1500px 900px at 92% -12%, bg_glow 0%, transparent 70%)
        float r = Ui.pxf(1500) * 0.7f;
        mGlow.setShader(new RadialGradient(w * 0.92f, -h * 0.12f, r,
                new int[]{Ui.P.bgGlow, Palette.alpha(Ui.P.bgGlow, 0f)}, null, Shader.TileMode.CLAMP));
    }

    /** Kept for the callers of the old animated field: nothing moves on the 2.0 field. */
    public void poke() {}

    public void setAuroraEnabled(boolean on) {}

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), mBase);
        c.save();
        c.scale(1f, 900f / 1500f, getWidth() * 0.92f, -getHeight() * 0.12f);
        c.drawRect(0, -getHeight(), getWidth(), getHeight() * 3, mGlow);
        c.restore();
    }
}
