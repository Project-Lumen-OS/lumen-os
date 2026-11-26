// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

/**
 * How the projector is set up now, as the Projection hint picture shows it: the read-back of projection.mode (table,
 * ceiling, auto) and projection.rear. Every adapter reads the stock put mode the same way (0 front table, 1 front
 * ceiling, 2 rear table, 3 rear ceiling, stock ConsProjection), Auto = the stock auto flip on. Under Auto the picture
 * shows no orientation, as stock leaves both of its radios unchecked then.
 */
public enum ProjectionSetup {
    FRONT_TABLE(false, ProjectionSetup.TABLE), FRONT_CEILING(false, ProjectionSetup.CEILING),
    REAR_TABLE(true, ProjectionSetup.TABLE), REAR_CEILING(true, ProjectionSetup.CEILING),
    AUTO_FRONT(false, ProjectionSetup.AUTO), AUTO_REAR(true, ProjectionSetup.AUTO);

    /** The profile hint whose picture this is (stock tipsImg rear_project_tips_img of projection_settings.rearProject). */
    public static final String HINT = "rear_projection";
    /** projection.mode tokens. */
    public static final String TABLE = "table", CEILING = "ceiling", AUTO = "auto";

    public final boolean rear;
    /** The projection.mode token: table, ceiling or auto. */
    public final String mount;

    ProjectionSetup(boolean rear, String mount) {
        this.rear = rear;
        this.mount = mount;
    }

    public boolean ceiling() { return CEILING.equals(mount); }

    public boolean auto() { return AUTO.equals(mount); }

    /** front or rear: the viewer's side of the screen. */
    public String side() { return rear ? "rear" : "front"; }

    /** The setup of a read-back; null while either value is unknown (the picture then shows nothing). */
    public static ProjectionSetup of(MenuState st) {
        return st == null ? null : of(st.get(FeatureId.PROJECTION_MODE), st.on(FeatureId.PROJECTION_REAR));
    }

    public static ProjectionSetup of(String mode, Boolean rear) {
        if (mode == null || rear == null) return null;
        for (ProjectionSetup s : values()) if (s.rear == rear && s.mount.equals(mode)) return s;
        return null;
    }
}
