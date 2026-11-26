/*
 * This projector's row of the casting model table (assets/cast/models.tsv, tools/devices/cast_models.py), keyed on
 * ro.boot.xgimi.modelname: the model data of the shared casting feature (no model checks in code).
 *
 * Copyright (C) 2026 Lumen OS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.lumen.airplay;

import android.content.Context;
import android.util.Log;

import org.lumen.airplay.cast.CastModels;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ModelData {
    private ModelData() {}

    static final String TABLE = "cast/models.tsv";
    private static CastModels.Model sModel;

    static synchronized CastModels.Model get(Context c) {
        if (sModel == null) {
            try (InputStream in = c.getAssets().open(TABLE)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                sModel = CastModels.parse(out.toString(StandardCharsets.UTF_8.name())).find(bootModel());
            } catch (Exception e) {
                Log.w(AirPlayService.TAG, "cast models: " + e);
                sModel = CastModels.UNKNOWN;
            }
        }
        return sModel;
    }

    /** ro.boot.xgimi.modelname, "" when unreadable (android.os.SystemProperties is hidden: read by reflection). */
    private static String bootModel() {
        try {
            Object v = Class.forName("android.os.SystemProperties").getMethod("get", String.class)
                    .invoke(null, "ro.boot.xgimi.modelname");
            return v == null ? "" : v.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }
}
