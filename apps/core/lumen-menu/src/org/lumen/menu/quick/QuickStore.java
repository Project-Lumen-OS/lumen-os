// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu.quick;

/**
 * Where the user's quick menu lives (the Android side: SecureQuickStore, Settings.Secure {@link QuickLayoutJson#KEY}
 * of the current user). Plain JSON in, plain JSON out; null when nothing is stored.
 */
public interface QuickStore {
    String load();

    /** True when written. */
    boolean save(String json);

    /** Deletes the stored menu (Вернуть как было); true when done. */
    boolean clear();
}
