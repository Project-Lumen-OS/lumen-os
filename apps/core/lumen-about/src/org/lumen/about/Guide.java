// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.about;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The Back to the XGIMI system page of lumen-os.net: every model with its official files, sizes, checksums and steps,
 * built from the signed catalog. Russian for the languages the Russian site serves, English for the rest.
 */
public final class Guide {
    static final String RU = "https://lumen-os.net/restore/";
    static final String EN = "https://lumen-os.net/en/restore/";
    private static final List<String> RUSSIAN = Arrays.asList("ru", "uk", "be", "kk");

    private Guide() {}

    /** the page in this language (an ISO 639 code), opened at the model (G0111 = #g0111), the model list without one */
    public static String backToStock(String language, String model) {
        String anchor = model == null ? "" : model.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return (RUSSIAN.contains(language) ? RU : EN) + (anchor.isEmpty() ? "" : "#" + anchor);
    }
}
