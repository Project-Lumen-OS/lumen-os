// SPDX-License-Identifier: Apache-2.0
package org.lumen.system;

/** Why a hotspot name or password is refused (HotspotRules), each with its own message on the edit screen. */
public enum HotspotProblem {
    /** no name (stock: the name must not be empty) */
    NAME_EMPTY,
    /** more than 32 bytes in UTF-8, the Wi-Fi limit of a network name */
    NAME_LONG,
    /** fewer than 8 characters (stock toast network_wifiap_pwdshort) */
    PASSWORD_SHORT,
    /** more than 63 characters, the WPA2 limit of a pass phrase */
    PASSWORD_LONG,
    /** a character outside printable ASCII, which WPA2 pass phrases cannot carry */
    PASSWORD_CHARS
}
