// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** The field behind everything: the base tone, soft light in the top right, a lower left edge. */
public class FieldView extends View {
    private final Paint mP = new Paint(Paint.DITHER_FLAG);

    public FieldView(Context c) {
        super(c);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        c.drawColor(Theme.BG);
        mP.setShader(new RadialGradient(w * 0.92f, -h * 0.05f, w * 0.62f, Theme.BG_GLOW, Theme.BG_GLOW & 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, mP);
        mP.setShader(new LinearGradient(0, h, w * 0.45f, h * 0.45f, Theme.BG_LOW, Theme.BG_LOW & 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, mP);
        mP.setShader(null);
    }
}
