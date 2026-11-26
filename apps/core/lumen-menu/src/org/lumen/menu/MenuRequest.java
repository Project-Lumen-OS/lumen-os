// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * Requests that open a place of the menu from outside it: Lumen Home's SHOW_PANEL tiles name a section, the remote's
 * 3D key (lumen-remote key action "3d", devices/_remotes/key_actions.tsv: it sends {@link #QUICK_3D} to the projector
 * app) opens the 3D popup beside its tile. A device's request receiver asks {@link #place} and shows that place.
 */
public final class MenuRequest {
    /** The intent action of the remote's 3D key: the small 3D window (the 3D popup on the card). */
    public static final String QUICK_3D = "org.lumen.projector.action.QUICK_3D";

    private MenuRequest() {}

    /** The menu place to show: the 3D popup for {@link #QUICK_3D}, else the request's section (null = the card). */
    public static String place(String action, String section) {
        if (QUICK_3D.equals(action)) return FeatureId.PICTURE_3D.id;
        return section == null || section.isEmpty() ? null : section;
    }

    /** True for an action a request receiver of the menu answers besides its own. */
    public static boolean ours(String action) { return QUICK_3D.equals(action); }
}
