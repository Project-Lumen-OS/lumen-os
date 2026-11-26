// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.system;

/** The Android settings screens Lumen apps open; {@link SystemIntent#chain} gives the ways to each. */
public enum SettingsPage {
    /** Remotes & Accessories: pairing a remote or another Bluetooth device. */
    BLUETOOTH,
    /** Wi-Fi. */
    NETWORK,
    /** The system language. */
    LANGUAGE,
    /** Date and time. */
    DATE_TIME,
    /** Storage. */
    STORAGE,
    /** System update (the Lumen updater answers it). */
    UPDATE,
    /** About the device. */
    ABOUT,
    /** Android's developer options. */
    DEVELOPER
}
