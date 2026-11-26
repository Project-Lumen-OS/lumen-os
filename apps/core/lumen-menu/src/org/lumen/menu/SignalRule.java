// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Options the content on screen does not allow (stock: a tree node with a {@code support} list is shown only for those
 * kinds of content, e.g. HDR Vivid only with HDR Vivid content). The menu shows them greyed and never chooses them;
 * the value set now always stays usable. Content not read yet counts as SDR, as on stock.
 *
 * <p>An option without a support list is offered for every kind but Dolby Vision: the stock trees give every normal
 * picture mode every kind except DOLBY, and PictureFactory.handleDolbyVideo closes the rest (user picture modes too)
 * while Dolby Vision plays. So with Dolby Vision only the options whose list names it stay open (the X50 family's
 * Dolby Vision Bright and Dark). A profile without support lists (a stock tree that does not follow the content,
 * Play 6) keeps every option.
 */
public final class SignalRule {
    private SignalRule() {}

    public static Set<String> greyed(MenuProfile profile, FeatureId feature, MenuState state) {
        if (profile == null || !profile.hasSupport(feature)) return Collections.emptySet();
        Signal now = state == null || state.signal() == null ? Signal.SDR : state.signal();
        String current = state == null ? null : state.get(feature);
        Set<String> out = new LinkedHashSet<>();
        for (String o : listed(profile, feature, state)) {
            if (!offered(profile.support(feature, o), now) && !o.equals(current)) out.add(o);
        }
        return Collections.unmodifiableSet(out);
    }

    /** Is an option with this support list (empty: none listed) offered for {@code now}? */
    static boolean offered(List<Signal> support, Signal now) {
        return support.isEmpty() ? now != Signal.DOLBY : support.contains(now);
    }

    /** The options the menu lists now: the device's runtime list (user picture modes included), else the profile's. */
    private static List<String> listed(MenuProfile profile, FeatureId feature, MenuState state) {
        List<String> runtime = state == null ? null : state.options(feature);
        return runtime != null ? runtime : profile.options(feature);
    }
}
