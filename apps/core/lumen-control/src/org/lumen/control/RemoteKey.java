// SPDX-License-Identifier: Apache-2.0
package org.lumen.control;

/**
 * The keys of the XGIMI remote a control input can press. A key goes in as the scancode of the XGIMI keypad device
 * (Vendor_3697_Product_0002: the projector's own buttons and the vendor phone-remote device ucontrol_key), so the
 * model's Lumen key layout gives it exactly the meaning the same remote button has; a key without such a scancode
 * uses the Android key code Lumen routes. Scancodes: stock /vendor/usr/keylayout/Vendor_3697_Product_0002.kl of
 * every model (the Z9X, HB, X50, RS30 and Titan files differ only in key 171).
 */
public enum RemoteKey {
    LEFT(50, 21),
    RIGHT(37, 22),
    UP(36, 19),
    DOWN(38, 20),
    OK(49, 23),
    BACK(48, 4),
    MENU(139, 82),
    HOME(35, 3),
    VOLUME_UP(115, 24),
    VOLUME_DOWN(114, 25),
    MUTE(113, 164),
    POWER(116, 26),
    /** The gear (stock MOVE_HOME). */
    GEAR(251, 122),
    FOCUS_LEFT(253, RemoteKey.NONE),
    FOCUS_RIGHT(254, RemoteKey.NONE),
    /** Autofocus: stock scancode 279 is past the 0..255 keys of the vendor device, so the Android FOCUS key. */
    AUTOFOCUS(RemoteKey.NONE, 80);

    /** No scancode or no Android key code. */
    public static final int NONE = -1;

    /** Keypad scancode, {@link #NONE} when the key has none. */
    public final int scancode;
    /** Android key code of the stock key layout (what stock vcontrol injected), {@link #NONE} when none. */
    public final int keyCode;

    RemoteKey(int scancode, int keyCode) {
        this.scancode = scancode;
        this.keyCode = keyCode;
    }
}
