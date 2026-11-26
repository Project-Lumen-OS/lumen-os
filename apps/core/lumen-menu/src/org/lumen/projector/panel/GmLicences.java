// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;


/**
 * "Licences" in Projector settings, Help (2026-11-11): the credits that CC BY 4.0 requires for the controller pictures
 * of the game menu (qp_design/models/README.md, CREDITS). The credit lines stay in English as published by the
 * authors; the line above them is translated. A device app without a Projector settings screen (the X50) shows the
 * same page from its menu: profile feature settings.licences, LicencesPage.
 */
public final class GmLicences {
    private GmLicences() {}

    /**
     * The written offer of the GPL-3.0 AirPlay receiver (org.lumen.airplay, UxPlay based) that every image ships: its
     * Corresponding Source is attached to every Lumen OS release (tools/devices/airplay_source.py) at the same place as
     * the image, and its licence texts are in the app (Screen casting, Licences).
     */
    static final String AIRPLAY_SOURCE = "The AirPlay receiver (org.lumen.airplay) is free software under the GNU General "
            + "Public License version 3 (GPL-3.0). It is built on UxPlay (https://github.com/FDH2/UxPlay, GPL-3.0) and "
            + "android-airplay-server (GPL-3.0), with libplist (LGPL-2.1), BoringSSL and ALAC (Apache-2.0). Its complete "
            + "source code is attached to every Lumen OS release as lumen-os-<version>-airplay-source.zip at "
            + "https://github.com/Project-Lumen-OS/lumen-os/releases, next to the image, and stays there for at least "
            + "three years. The full licence texts are in Screen casting, Licences.";

    /** The CREDITS block of the models README, word for word (only the models whose frames ship). */
    static final String CREDITS =
            "\"PS5 Controller\" by Taohid Animation, https://sketchfab.com/3d-models/ps5-controller-b7bb9c5102a04cb0b1966c6d02bad7d6, CC BY 4.0. Changed: logos and symbols removed, reduced and converted to a real-time model, rendered to images.\n\n"
            + "\"Xbox One Controller Free\" by paxillop, https://sketchfab.com/3d-models/xbox-one-controller-free-97fb54c001f84a6896b6ce8eb7a1814d, CC BY 4.0. Changed: logo and letters removed, recoloured, reduced and converted to a real-time model, rendered to images.\n\n"
            + "\"Nintendo Switch OLED\" by Wille Evensson, https://sketchfab.com/3d-models/nintendo-switch-oled-aa2c7d815a104accae7709eb39ac2184, CC BY 4.0. Changed: only the controllers, recoloured, markings removed, reduced and converted to a real-time model, rendered to images.\n\n"
            + "Licence: https://creativecommons.org/licenses/by/4.0/. These are independent fan models, not official Sony, Microsoft or Nintendo material, and do not imply endorsement.\n\n"
            + "Inter typeface by Rasmus Andersson, SIL Open Font License 1.1.\n\n"
            + AIRPLAY_SOURCE + "\n\n"
            + "The home screen preview in Lumen Setup shows stills from the Blender open movies Cosmos Laundromat, Spring, "
            + "Big Buck Bunny, Sintel, Caminandes, Sprite Fright, Tears of Steel and Coffee Run, (c) Blender Foundation, "
            + "https://www.blender.org, CC BY 3.0 and CC BY 4.0.";
}
