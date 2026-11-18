package org.lumen.projector.panel;

import org.lumen.menu.ProjectionSetup;

/** The hint pictures QpdTip draws, by the profile's hint token (MenuProfile.HINT_PICTURES). */
enum HintPicture {
    /** Three takes of one landscape: the picture modes. */
    PICTURE_MODE,
    /** The projection setup read back now (ProjectionSetup), redrawn on every change. */
    PROJECTION,
    /** A remote with the focus key on its front (devices/_remotes/focus_guide.tsv, stock RemoteFocusCell). */
    FOCUS_FRONT,
    /** A remote with the focus key on its side (the B990C remote of the same table). */
    FOCUS_SIDE;

    /** The picture of a profile hint, or null while the module cannot draw it yet. */
    static HintPicture of(String hint) {
        if ("picture_mode".equals(hint)) return PICTURE_MODE;
        if (ProjectionSetup.HINT.equals(hint)) return PROJECTION;
        if ("focus_guide_front".equals(hint)) return FOCUS_FRONT;
        if ("focus_guide_side".equals(hint)) return FOCUS_SIDE;
        return null;
    }
}
