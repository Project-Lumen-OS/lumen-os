package org.lumen.projector;

import android.view.KeyEvent;

import org.lumen.menu.LampOffKey;

/**
 * Keycodes produced by the Z9X keylayouts (stock analysis) and routed to
 * org.lumen.projector/.KeyReceiver by the global-keys RRO (stock analysis).
 */
final class Z9xKeys {
    private Z9xKeys() {}
    /** Mic key (BT 63 = HID F5) and AI key (137): Google Assistant, push-to-talk. WAKE. */
    static final int VOICE = KeyEvent.KEYCODE_SEARCH;                       // 84
    /** Focus key short press: autofocus. */
    static final int AUTOFOCUS = KeyEvent.KEYCODE_FOCUS;                     // 80
    /** Focus key long press: manual-focus overlay. */
    static final int MANUAL_FOCUS = KeyEvent.KEYCODE_TV_CONTENTS_MENU;       // 256
    /** Dedicated focus step keys of other remotes / IR / keypad: stock LEFT -> manualFocus(1). */
    static final int FOCUS_STEP_LEFT = KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD; // 275
    /** Dedicated focus step keys of other remotes / IR / keypad: stock RIGHT -> manualFocus(0). */
    static final int FOCUS_STEP_RIGHT = KeyEvent.KEYCODE_MEDIA_STEP_FORWARD; // 274
    /** Ambient key: screensaver (Google TV Ambient mode). */
    static final int AMBIENT = KeyEvent.KEYCODE_TV_SATELLITE_SERVICE;        // 240
    /** IR KEYSTONE / LENS, keypad LENS: quick panel, projection section (v6: settings screen). */
    static final int PROJECTOR_PANEL = KeyEvent.KEYCODE_TV_NETWORK;          // 241
    /**
     * Gear key (BT 66, IR 192, BT 64/251/275 and keypad 9/4 in the .kl files): stays KEYCODE_SETTINGS.
     * The global_keys entry routes it here BEFORE LineageParts atv KeyHandler (which would open
     * TvSettings on UP): PhoneWindowManager handles global keys at :5263 / :4328, KeyHandler at
     * :5311 / :4334 (v6.1 design). Short press = quick panel, long press = TvSettings.
     */
    static final int GEAR = KeyEvent.KEYCODE_SETTINGS;                       // 176
    /**
     * The gear held, as the XGIMI remotes report it themselves: one short press of the stock MENU scancode (BT 127 =
     * KEY_COMPOSE on the "XGIMI RC Keyboard" 000d:3841 and its siblings, IR / keypad 139), stock KEYCODE_MENU = the
     * game menu. A key of its own in the .kl files (not MENU, which keyboards and other remotes keep for apps):
     * the gear long press action.
     */
    static final int GEAR_HOLD = KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU;     // 257
    /** Source key (BT 430, IR 88, keypad 225/268/466): input chooser overlay (v6.1, from org.lumen.tvinput). */
    static final int SOURCE = KeyEvent.KEYCODE_TV_INPUT;                     // 178
    /** App-key slots 1..5 (stock VIDEO0..VIDEO4). Z9X remotes: iQIYI key = slot 3 or 2, bilibili key = slot 4 or 5 (AppSlots.keySlot). */
    static final int[] APP_SLOTS = {
            KeyEvent.KEYCODE_TV_TERRESTRIAL_ANALOG,   // 235 slot 1
            KeyEvent.KEYCODE_TV_TERRESTRIAL_DIGITAL,  // 236 slot 2
            KeyEvent.KEYCODE_TV_SATELLITE,            // 237 slot 3 (iQIYI key)
            KeyEvent.KEYCODE_TV_SATELLITE_BS,         // 238 slot 4 (bilibili key)
            KeyEvent.KEYCODE_TV_SATELLITE_CS,         // 239 slot 5
    };
    /** What a key is to the shared "Turn off screen" (LampOff): the power key, a focus key or any other key. */
    static LampOffKey lampOffKey(int code) {
        if (code == KeyEvent.KEYCODE_STB_POWER) return LampOffKey.POWER;
        boolean focus = code == AUTOFOCUS || code == MANUAL_FOCUS || code == FOCUS_STEP_LEFT || code == FOCUS_STEP_RIGHT;
        return focus ? LampOffKey.FOCUS : LampOffKey.OTHER;
    }

    /** Input-device vendor ids of XGIMI remotes / IR receiver / keypad (InputDevice#getVendorId). */
    static final int[] XGIMI_VENDOR_IDS = {0x000d, 0x1d5a, 0x26e3, 0x3697, 0x0001, 0x9999};
}
