// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.animation;

public class ObjectAnimator {
    public static ObjectAnimator ofFloat(Object target, Object property, float... values) { return new ObjectAnimator(); }

    public ObjectAnimator setDuration(long ms) { return this; }

    public void start() { }
}
