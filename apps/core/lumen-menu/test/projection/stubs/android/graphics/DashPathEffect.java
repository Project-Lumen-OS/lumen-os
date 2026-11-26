// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.graphics;

public class DashPathEffect extends PathEffect {
    final float[] intervals;
    final float phase;

    public DashPathEffect(float[] intervals, float phase) {
        this.intervals = intervals.clone();
        this.phase = phase;
    }
}
