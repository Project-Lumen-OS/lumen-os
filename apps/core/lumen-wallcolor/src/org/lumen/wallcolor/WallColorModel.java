// SPDX-License-Identifier: Apache-2.0
package org.lumen.wallcolor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * One model's wall colour data (devices/&lt;c&gt;/wallcolor.conf, in the app as assets/lumen_wallcolor/model.conf):
 * key=value lines, # comments. The module reads the card text variant (stock: support_ambient_light picks
 * wall_color_calibration_dialog_desc or _desc_no) and the stock pattern type (support_wall_color_adjust_type);
 * the camera and HAL rows are the model's record (the vendor opens the camera itself, the codes live in hal/codes.tsv).
 */
public final class WallColorModel {
    /** The pattern type the module draws (stock type 3, the dark pattern). */
    public static final int TYPE_DARK = 3;

    /** Stock support_ambient_light: the card says the picture goes to full brightness and auto brightness turns off. */
    public final boolean ambientLight;
    /** Stock support_wall_color_adjust_type. */
    public final int type;
    /** The model code the file names, for logs. */
    public final String model;

    WallColorModel(String model, boolean ambientLight, int type) {
        this.model = model;
        this.ambientLight = ambientLight;
        this.type = type;
    }

    /** No file: the plain card and the dark pattern. */
    public static final WallColorModel DEFAULT = new WallColorModel("", false, TYPE_DARK);

    public static WallColorModel read(Reader in) throws IOException {
        String model = "";
        boolean ambient = false;
        int type = TYPE_DARK;
        BufferedReader r = new BufferedReader(in);
        for (String line; (line = r.readLine()) != null; ) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String k = line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
            if (k.equals("model")) model = v;
            else if (k.equals("ambient_light")) ambient = v.equals("true");
            else if (k.equals("type")) {
                try {
                    type = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    type = TYPE_DARK;
                }
            }
        }
        return new WallColorModel(model, ambient, type);
    }
}
