// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

import android.util.Log;

import org.lumen.menu.FeatureId;
import org.lumen.menu.ProjectorFeatures.Result;
import org.lumen.projector.hal.PutMode;

import java.util.function.Consumer;

/** Projection mode (Auto, table, ceiling) and rear projection. */
final class Z9xProjectionRows extends Z9xRows {
    Z9xProjectionRows(Z9xFeatures menu) { super(menu); }

    @Override boolean set(FeatureId f, String v, boolean confirmed, Consumer<Result> cb) {
        switch (f) {
            case PROJECTION_MODE:
                setProjection(v, cb);
                return true;
            case PROJECTION_REAR: {                                       // QPC:2042
                Z9xMenuRules.In in = menu.last();
                if (in == null || in.putMode == null) { cb.accept(Result.done(null)); return true; }
                int target = Z9xMenuRules.rearTarget(in.putMode, "on".equals(v));
                if (target == in.putMode) { cb.accept(Result.done(null)); return true; }
                putMode(in.putMode, target, cb);
                return true;
            }
            default:
                return false;
        }
    }

    /** QPC:2022 projection: Auto = 580 on; Table / Ceiling keep front / rear; the same put mode only turns Auto off. */
    private void setProjection(String v, Consumer<Result> cb) {
        Z9xMenuRules.In in = menu.last();
        if (in == null || in.putMode == null || in.autoReverse == null) { cb.accept(Result.done(null)); return; }
        if ("auto".equals(v)) {
            if (in.autoReverse) { cb.accept(Result.done(null)); return; }
            write("580 auto flip on", (g, g2) -> { g.setAutoReverse(true); return null; }, cb);
            return;
        }
        int target = Z9xMenuRules.projectionTarget(in.putMode, v);
        if (target == in.putMode) {
            if (in.autoReverse) write("580 auto flip off", (g, g2) -> { g.setAutoReverse(false); return null; }, cb);
            else cb.accept(Result.done(null));
            return;
        }
        putMode(in.putMode, target, cb);
    }

    /** QPC:2058: 580 off, 172, and to ceiling the push-pull screen off (stock). */
    private void putMode(int cur, int target, Consumer<Result> cb) {
        final PutMode m = PutMode.fromWire(target);
        if (m == null) { cb.accept(Result.failed(null)); return; }
        final boolean toCeiling = Z9xMenuRules.toCeiling(cur, target);
        write("172 put mode " + m, (g, g2) -> {
            g.setAutoReverse(false);
            g.setPutMode(m);
            if (toCeiling) {
                try { g2.disablePushPullScreenForCeiling(); } catch (Throwable t) { Log.w(TAG, "IGmpf2 201: " + t); }
            }
            return null;
        }, cb);
    }
}
