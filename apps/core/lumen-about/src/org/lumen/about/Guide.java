// SPDX-License-Identifier: Apache-2.0
package org.lumen.about;

import java.util.Arrays;
import java.util.List;

/** The project's install guide, section Back to stock: Russian for the languages the Russian guide serves. */
public final class Guide {
    static final String BASE = "https://github.com/Project-Lumen-OS/lumen-os/blob/main/docs/install/";
    static final String SECTION = "#stock";
    private static final List<String> RUSSIAN = Arrays.asList("ru", "uk", "be", "kk");

    private Guide() {}

    /** the Back to stock section of the guide in this language (an ISO 639 code) */
    public static String backToStock(String language) {
        return BASE + (RUSSIAN.contains(language) ? "ru" : "en") + ".md" + SECTION;
    }
}
