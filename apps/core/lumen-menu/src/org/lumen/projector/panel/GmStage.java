package org.lumen.projector.panel;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * The game menu's window content: GmView (everything drawn on one canvas) with the real-time controller (Gm3dView)
 * laid exactly over the status card's picture slot. Without the model (debug.lumen.gm3d=0, or GL failed hard earlier
 * in this process) the content is GmView alone, as before.
 */
final class GmStage extends FrameLayout {
    /** The picture slot of the status card in design units of the 1920 wide canvas (GmView.drawStatus). */
    static final float SLOT_L = 50, SLOT_T = 54, SLOT_W = 250, SLOT_H = 150;

    final GmView view;
    final Gm3dView model;

    static View wrap(Context c, GmView v) {
        if (!Gm3dView.enabled() || Gm3dGate.sBroken) return v;
        return new GmStage(c, v);
    }

    private GmStage(Context c, GmView v) {
        super(c);
        view = v;
        setClipChildren(false);
        setClipToPadding(false);
        addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        model = new Gm3dView(c, v);
        model.setVisibility(INVISIBLE);
        addView(model, new LayoutParams(1, 1));
        v.attachModel(model);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        float k = MeasureSpec.getSize(wSpec) / 1920f;
        LayoutParams lp = (LayoutParams) model.getLayoutParams();
        lp.width = Math.max(1, Math.round(SLOT_W * k));
        lp.height = Math.max(1, Math.round(SLOT_H * k));
        lp.leftMargin = Math.round(SLOT_L * k);
        lp.topMargin = Math.round(SLOT_T * k);
        super.onMeasure(wSpec, hSpec);
    }
}
