// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

/**
 * LockedRow: a row stock keeps closed now is drawn as a stock disabled cell (text alpha 110 of 255) from the adapter's
 * locked hook, nothing read and no feature never close a row. java org.lumen.menu.LockedRowTest
 */
public final class LockedRowTest {
    private static int pass, fail;

    private static void ok(String what, boolean cond) {
        if (cond) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    /** An adapter whose locked hook is the shared picture rule on fixed reads (the HB shape). */
    private static ProjectorFeatures dev(Boolean game, Integer mode, Integer hdr) {
        return new ProjectorFeatures() {
            @Override public Set<FeatureId> implemented() { return Collections.emptySet(); }
            @Override public boolean ready() { return true; }
            @Override public void read(Consumer<MenuState> cb) { }
            @Override public void set(FeatureId feature, String value, Consumer<Result> cb) { }
            @Override public void run(FeatureId action, Consumer<Result> cb) { }
            @Override public boolean open(FeatureId screen, Runnable back) { return false; }
            @Override public Note locked(FeatureId f, MenuState now) {
                return PictureSettingsRule.blocked(PictureSettingsRule.ONE_PAGE, f, game, mode, hdr);
            }
        };
    }

    public static void main(String[] a) {
        ok("the stock disabled alpha 110 of 255", Math.abs(LockedRow.ALPHA - 110f / 255f) < 1e-6);
        ok("an open row is drawn full", LockedRow.alpha(false) == 1f);
        ok("a closed row is drawn at the stock disabled alpha", LockedRow.alpha(true) == LockedRow.ALPHA);
        MenuState read = MenuState.builder().build();
        ProjectorFeatures ai = dev(false, 10, 0), sport = dev(false, 9, 0), game = dev(true, 9, 0);
        for (FeatureId f : new FeatureId[]{FeatureId.PQ_BRIGHTNESS, FeatureId.PQ_CONTRAST, FeatureId.PQ_SATURATION,
                FeatureId.PQ_SHARPNESS, FeatureId.PICTURE_COLOR_TEMP, FeatureId.PICTURE_MEMC, FeatureId.PICTURE_PRO,
                FeatureId.PICTURE_RESET}) {
            ok(f + " closed in AI", LockedRow.locked(ai, f, read));
            ok(f + " closed while the game mode runs", LockedRow.locked(game, f, read));
            ok(f + " open in sport", !LockedRow.locked(sport, f, read));
        }
        for (FeatureId f : new FeatureId[]{FeatureId.PICTURE_MODE, FeatureId.LAMP, FeatureId.PICTURE_RESET_ALL,
                FeatureId.PICTURE_ASPECT}) {
            ok(f + " open in AI (outside the stock page)", !LockedRow.locked(ai, f, read));
        }
        ok("nothing read: open", !LockedRow.locked(ai, FeatureId.PQ_BRIGHTNESS, null));
        ok("no adapter: open", !LockedRow.locked(null, FeatureId.PQ_BRIGHTNESS, read));
        ok("no feature: open", !LockedRow.locked(ai, null, read));
        ok("an adapter without the hook: open", !LockedRow.locked(new ProjectorFeatures() {
            @Override public Set<FeatureId> implemented() { return Collections.emptySet(); }
            @Override public boolean ready() { return true; }
            @Override public void read(Consumer<MenuState> cb) { }
            @Override public void set(FeatureId feature, String value, Consumer<Result> cb) { }
            @Override public void run(FeatureId action, Consumer<Result> cb) { }
            @Override public boolean open(FeatureId screen, Runnable back) { return false; }
        }, FeatureId.PQ_BRIGHTNESS, read));
        System.out.println("LockedRowTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
