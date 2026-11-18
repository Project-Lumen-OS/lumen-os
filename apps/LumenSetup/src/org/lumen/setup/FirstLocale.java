package org.lumen.setup;

/**
 * The language of Lumen Setup's first screen: the one first-start setting of every image
 * (devices/_image/first_start_prop.txt, 2026-11-13: English on every model), which the image puts in
 * ro.product.locale. On fresh data zygote already starts in it. A /data that carries a language from before
 * (the stock firmware's, a value an older image saved, an unfinished trial) would show the remote step in
 * that language, and the language step only comes after it. So a run that has shown no step on this data
 * yet applies the image's language before the first screen is drawn. Once a step was shown the locale is
 * left alone: the language step and Settings are the user's. No language of its own here: without
 * ro.product.locale nothing is applied (first_start.py check refuses such an image).
 * Plain Java, no Android types: tools/devices/test/first_start_test.sh runs it on the host.
 */
public final class FirstLocale {
    private FirstLocale() {}

    /**
     * The language tag to apply before the first screen, or null to keep the current locale.
     *
     * @param storedStep    SetupState.step(): the id of the last step shown on this data, null before the first
     * @param currentTag    the language tag of the current configuration (first of its list)
     * @param productLocale ro.product.locale of the image
     */
    public static String toApply(String storedStep, String currentTag, String productLocale) {
        if (storedStep != null && !storedStep.isEmpty()) return null;
        String want = norm(productLocale);
        if (want.isEmpty() || want.equals(norm(currentTag))) return null;
        return productLocale.trim().replace('_', '-');
    }

    static String norm(String tag) {
        return tag == null ? "" : tag.trim().replace('_', '-').toLowerCase(java.util.Locale.ROOT);
    }
}
