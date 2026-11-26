// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.testkit;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The HAL call log of a host test's fake HAL. With -Dlumen.halt=FILE every entry is also appended to FILE in the order
 * the calls happen, across the test programs of one run: the wire golden of a family whose transport has no recording
 * stubs (the X50 family, apps/LumenProjector/host/activity/test/run.sh, golden/x50_hal.txt).
 */
public final class HalTrace {
    private static PrintWriter sOut;

    private HalTrace() {}

    /** A plain list for the fake HAL's log; each add is also traced. */
    public static List<String> list() {
        return new ArrayList<String>() {
            @Override public boolean add(String s) {
                record(s);
                return super.add(s);
            }
        };
    }

    private static synchronized void record(String s) {
        String file = System.getProperty("lumen.halt", "");
        if (file.isEmpty()) return;
        if (sOut == null) {
            try {
                sOut = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalStateException("lumen.halt " + file + ": " + e);
            }
            final PrintWriter out = sOut;
            Runtime.getRuntime().addShutdownHook(new Thread(out::flush));
        }
        sOut.println(s);
    }
}
