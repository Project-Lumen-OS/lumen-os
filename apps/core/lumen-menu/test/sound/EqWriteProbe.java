// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.audio;

import java.util.function.BiConsumer;

/** EqWrite through its package (sh test/sound/run.sh): with a flat curve, stock's own calls only. */
public final class EqWriteProbe {
    private EqWriteProbe() {}

    public static void run(BiConsumer<Boolean, String> ok) {
        int T = EqWrite.TRIGGER, V = EqWrite.VENDOR, F = EqWrite.FORCE;
        ok.accept(EqWrite.plan(V, true, false, false) == EqWrite.NOTHING, "flat after 42 / 27 / 61, PEQ never ours: no call (stock)");
        ok.accept(EqWrite.plan(V, true, true, false) == EqWrite.OFF, "flat after a vendor call, PEQ ours: 51(false)");
        ok.accept(EqWrite.plan(F, true, false, false) == EqWrite.OFF, "flat by a user edit: 51(false)");
        ok.accept(EqWrite.plan(T, true, false, false) == EqWrite.NOTHING, "flat on a trigger: no call");
        ok.accept(EqWrite.plan(V, false, true, true) == EqWrite.CURVE, "a curve after a vendor call: full write, no dedup");
        ok.accept(EqWrite.plan(F, false, true, true) == EqWrite.CURVE, "a curve by a user edit: full write");
        ok.accept(EqWrite.plan(T, false, true, true) == EqWrite.NOTHING, "a trigger: same curve within the window skipped");
        ok.accept(EqWrite.plan(T, false, false, true) == EqWrite.CURVE, "a trigger: a curve the vendor turned off is written");
    }
}
