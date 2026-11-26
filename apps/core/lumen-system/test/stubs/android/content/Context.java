// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.content;

/** Host stub: only what ColourVision touches. */
public class Context {
    private final ContentResolver resolver = new ContentResolver();

    public ContentResolver getContentResolver() { return resolver; }
}
