// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Options the content on screen does not allow (stock: a tree node with a {@code support} list is shown only for those
 * kinds of content, e.g. HDR Vivid only with HDR Vivid content). The menu shows them greyed and never chooses them;
 * the value set now always stays usable. Content not read yet counts as SDR, as on stock.
 */
public final class SignalRule {
    private SignalRule() {}

    public static Set<String> greyed(MenuProfile profile, FeatureId feature, MenuState state) {
        if (profile == null || !profile.hasSupport(feature)) return Collections.emptySet();
        Signal now = state == null || state.signal() == null ? Signal.SDR : state.signal();
        String current = state == null ? null : state.get(feature);
        Set<String> out = new LinkedHashSet<>();
        for (String o : profile.options(feature)) {
            List<Signal> on = profile.support(feature, o);
            if (!on.isEmpty() && !on.contains(now) && !o.equals(current)) out.add(o);
        }
        return Collections.unmodifiableSet(out);
    }
}
