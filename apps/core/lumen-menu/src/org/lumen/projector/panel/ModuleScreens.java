// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

import android.content.Context;

import org.lumen.audio.BootSound;
import org.lumen.parental.ParentalClient;
import org.lumen.hdmiports.HdmiPorts;
import org.lumen.menu.FeatureId;
import org.lumen.menu.MenuProfile;
import org.lumen.menu.MenuSpec;
import org.lumen.menu.MenuState;
import org.lumen.projector.R;
import org.lumen.report.ReportScreen;
import org.lumen.sensors.SensorPanel;
import org.lumen.signal.SignalPanel;
import org.lumen.system.ColourVision;
import org.lumen.system.DeviceFacts;
import org.lumen.system.Hotspot;
import org.lumen.system.KeyTone;
import org.lumen.system.NetworkAdb;
import org.lumen.system.ScreensaverDelay;
import org.lumen.system.ScreensaverStart;
import org.lumen.system.SettingsPage;
import org.lumen.system.SystemPages;
import org.lumen.ui.TvFocus;
import org.lumen.wallcolor.WallColor;

/**
 * The screens and settings of shared modules the menu handles itself (MenuSpec.MODULE_SCREENS, MODULE_SETTINGS,
 * MODULE_INFO): one place, no device code.
 */
final class ModuleScreens {
    private static final String ON = "on", OFF = "off";

    /** The system details app (apps/LumenInfo). */
    static final String INFO_PACKAGE = "org.lumen.info";

    private ModuleScreens() {}

    /** The Android settings screen a General or For developers row opens, null for another row. */
    private static SettingsPage page(FeatureId f) {
        switch (f) {
            case SETTINGS_LANGUAGE: return SettingsPage.LANGUAGE;
            case SETTINGS_DATE_TIME: return SettingsPage.DATE_TIME;
            case SETTINGS_STORAGE: return SettingsPage.STORAGE;
            case SETTINGS_UPDATE: return SettingsPage.UPDATE;
            case SETTINGS_ABOUT: return SettingsPage.ABOUT;
            case DEV_ANDROID: return SettingsPage.DEVELOPER;
            default: return null;
        }
    }

    private static android.content.Intent info(Context c) {
        return c.getPackageManager().getLeanbackLaunchIntentForPackage(INFO_PACKAGE);
    }

    private static android.media.AudioManager audio(Context c) {
        return (android.media.AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
    }

    /** The top step of a number setting of the module (the volume: the music stream's maximum). */
    static int max(Context c, FeatureId f) {
        android.media.AudioManager a = f == FeatureId.SOUND_VOLUME ? audio(c) : null;
        return a == null ? 100 : Math.max(1, a.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
    }

    /**
     * The module row is there on this model: the Network popup's rows only where Android tethers the profile's vendor
     * hotspot interface (Hotspot.supported), so an image or a chip without it shows the Wi-Fi page at once, as before.
     */
    static boolean available(Context c, MenuProfile p, FeatureId f) {
        if (f == FeatureId.NETWORK_WIFI) return true;
        if (MenuSpec.HOTSPOT_ROWS.contains(f)) return Hotspot.supported(c, p.text(FeatureId.NETWORK_HOTSPOT, "iface"));
        return available(c, f);
    }

    static boolean available(Context c, FeatureId f) {
        if (f == FeatureId.SOUND_VOLUME) return audio(c) != null;
        if (page(f) != null) return SystemPages.has(c, page(f));       // the row only where the screen is there
        if (f == FeatureId.DEV_SYSTEM_INFO) return info(c) != null;
        if (f == FeatureId.DEV_NETWORK_ADB) return true;
        if (f == FeatureId.SOURCE_LIST) return true;                   // the Input tile's sources (MenuController)
        if (f == FeatureId.SETTINGS_HDMI) {                            // HdmiPage: where the image has the HDMI app
            return c.getPackageManager().resolveContentProvider(HdmiPorts.AUTHORITY, 0) != null;
        }
        if (f == FeatureId.BLUETOOTH_ADD) return BtPage.available(c);
        if (f == FeatureId.SIGNAL_PANEL || f == FeatureId.SIGNAL_PIN) return SignalPanel.available(c);
        if (f == FeatureId.SIGNAL_REFRESH) return SignalPanel.receiverInfo(c);
        if (f == FeatureId.KEYSTONE_WALL_COLOR) return WallColor.available(c);
        if (f == FeatureId.SOUND_KEY_TONE || f == FeatureId.SETTINGS_COLOUR_VISION) return true;
        if (f == FeatureId.SETTINGS_SCREENSAVER_NOW || f == FeatureId.SETTINGS_SCREENSAVER_DELAY) return true;
        if (f == FeatureId.SETTINGS_MENU_SIDE || f == FeatureId.SETTINGS_MENU_DENSITY) return true;
        if (f == FeatureId.SETTINGS_POWER_KEY) return true;            // PowerKeyPrefs: the app has the power key
        if (f == FeatureId.POWER_ON_MUSIC) return true;                // BootSound: every image ships the sound and its rc
        if (f == FeatureId.SETTINGS_REMOTE || f == FeatureId.SETTINGS_REMOTE_KEYS) return true;
        if (MenuSpec.MODULE_PAGES.contains(f)) return true;           // the page row shows when a row of it is there
        if (MenuSpec.MODULE_INFO.contains(f)) return true;
        if (f == FeatureId.SENSORS_PANEL) return SensorPanel.available(c);
        if (f == FeatureId.MIRROR_DIAG) return MirrorDiagPage.available(c);
        if (f == FeatureId.SETTINGS_LICENCES) return true;                // LicencesPage: the menu's own page, every model
        if (f == FeatureId.SETTINGS_PARENTAL) return ParentalClient.present(c);   // ParentalPage: Home answers it
        if (f == FeatureId.SETTINGS_CASTING) return screen(f).resolveActivity(c.getPackageManager()) != null;
        return f == FeatureId.SETTINGS_REPORT && ReportScreen.available(c);
    }

    /** The screen of another Lumen app a module row opens: the screen casting page of the AirPlay receiver app. */
    private static android.content.Intent screen(FeatureId f) {
        return new android.content.Intent(CASTING_ACTION).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    /** The casting screen (apps/LumenAirPlay CastActivity): AirPlay, screen mirroring and DLNA on one page. */
    static final String CASTING_ACTION = "org.lumen.cast.SCREEN";

    /** Module screens that open over the menu (it steps aside and comes back after, as the stock text dialogs). */
    static boolean overMenu(FeatureId f) {
        return f == FeatureId.NETWORK_HOTSPOT_NAME || f == FeatureId.NETWORK_HOTSPOT_PASSWORD;
    }

    static boolean open(Context c, FeatureId f) {
        if (f == FeatureId.NETWORK_WIFI) return SystemPages.open(c, SettingsPage.NETWORK);
        if (page(f) != null) return SystemPages.open(c, page(f));
        if (f == FeatureId.DEV_SYSTEM_INFO) {
            android.content.Intent i = info(c);
            if (i == null) return false;
            c.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        }
        if (f == FeatureId.NETWORK_HOTSPOT_NAME) return TextEditActivity.open(c, TextFields.HOTSPOT_NAME);
        if (f == FeatureId.NETWORK_HOTSPOT_PASSWORD) return TextEditActivity.open(c, TextFields.HOTSPOT_PASSWORD);
        if (f == FeatureId.SIGNAL_PANEL) return SignalPanel.press(c);
        if (f == FeatureId.SENSORS_PANEL) return SensorPanel.press(c);
        if (f == FeatureId.SETTINGS_SCREENSAVER_NOW) return ScreensaverStart.now(c);
        if (f == FeatureId.KEYSTONE_WALL_COLOR) return WallColor.open(c);
        if (f == FeatureId.SETTINGS_REMOTE) {                    // 120 s fast LE scan with the module's own prompt
            org.lumen.remote.RemotePairing.startPairing(c);
            return true;
        }
        if (f == FeatureId.SETTINGS_REMOTE_KEYS) {               // give a remote key an app
            org.lumen.remote.keys.KeyBindActivity.open(c);
            return true;
        }
        if (f == FeatureId.SETTINGS_CASTING) {
            try {
                c.startActivity(screen(f));
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return f == FeatureId.SETTINGS_REPORT && ReportScreen.open(c);
    }

    /** A module action row: the hotspot's restore default (the first name and password again, stock network_ap_reset). */
    static boolean run(Context c, MenuProfile p, FeatureId f) {
        if (f != FeatureId.NETWORK_HOTSPOT_RESET) return false;
        if (!Hotspot.reset(c, p.text(FeatureId.NETWORK_HOTSPOT, "name"), hotspotNotes(c))) return false;
        TvFocus.toast(c, R.string.lumen_m_hotspot_saved, false);
        return true;
    }

    /** The stock notes at the end of a hotspot switch: on with its name, off, the unknown error. */
    static Hotspot.Done hotspotNotes(Context c) {
        Context app = c.getApplicationContext();
        return new Hotspot.Done() {
            @Override public void started(String name) { TvFocus.toast(app, app.getString(R.string.lumen_m_hotspot_on, name), false); }

            @Override public void stopped() { TvFocus.toast(app, R.string.lumen_m_hotspot_off, false); }

            @Override public void failed() { TvFocus.toast(app, R.string.lumen_m_hotspot_failed, false); }
        };
    }

    /** The module setting's value: "on" / "off" (TOGGLE) or an option token (CHOICE). */
    static String get(Context c, FeatureId f) {
        if (f == FeatureId.SIGNAL_PIN) return SignalPanel.pinned(c) ? ON : OFF;
        if (f == FeatureId.SIGNAL_REFRESH) return SignalPanel.refresh(c);
        if (f == FeatureId.SOUND_KEY_TONE) return KeyTone.on(c) ? ON : OFF;
        if (f == FeatureId.POWER_ON_MUSIC) return BootSound.on() ? ON : OFF;
        if (f == FeatureId.SETTINGS_COLOUR_VISION) return ColourVision.get(c);
        if (f == FeatureId.SETTINGS_SCREENSAVER_DELAY) return ScreensaverDelay.get(c);
        if (f == FeatureId.SETTINGS_MENU_SIDE || f == FeatureId.SETTINGS_MENU_DENSITY) return MenuLookPrefs.get(c, f);
        if (f == FeatureId.SETTINGS_POWER_KEY) return PowerKeyPrefs.get(c);
        if (f == FeatureId.DEV_NETWORK_ADB) return NetworkAdb.on() ? ON : OFF;
        if (f == FeatureId.SOUND_VOLUME) {
            android.media.AudioManager a = audio(c);
            return a == null ? null : Integer.toString(a.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        }
        return null;
    }

    /** Writes a module setting; false when {@code f} is not one or the setting did not take the value. */
    static boolean set(Context c, FeatureId f, String value) {
        if (f == FeatureId.SIGNAL_PIN) SignalPanel.setPinned(c, ON.equals(value));
        else if (f == FeatureId.SIGNAL_REFRESH) SignalPanel.setRefresh(c, value);
        else if (f == FeatureId.SOUND_KEY_TONE) {                  // stock VoiceRepository.setKeyTone, then the read
            KeyTone.set(c, ON.equals(value));
            return KeyTone.on(c) == ON.equals(value);
        }
        else if (f == FeatureId.POWER_ON_MUSIC) {                  // Lumen's power-on sound, then the read
            BootSound.set(c, ON.equals(value));
            return BootSound.on() == ON.equals(value);
        }
        else if (f == FeatureId.SETTINGS_COLOUR_VISION) {          // Android's colour correction, then the read
            return ColourVision.set(c, value) && value.equals(ColourVision.get(c));
        }
        else if (f == FeatureId.SETTINGS_SCREENSAVER_DELAY) {      // Android's idle time, then the read
            return ScreensaverDelay.set(c, value);
        }
        else if (f == FeatureId.SETTINGS_MENU_SIDE || f == FeatureId.SETTINGS_MENU_DENSITY) {   // the menu's look, read back
            return MenuLookPrefs.set(c, f, value) && value.equals(MenuLookPrefs.get(c, f));
        }
        else if (f == FeatureId.SETTINGS_POWER_KEY) {              // the power key's short press, read back
            return PowerKeyPrefs.set(c, value) && value.equals(PowerKeyPrefs.get(c));
        }
        else if (f == FeatureId.DEV_NETWORK_ADB) return NetworkAdb.set(ON.equals(value));   // the port, read back
        else if (f == FeatureId.NETWORK_HOTSPOT) {                 // stock startTethering / stopTethering, a note at the end
            if (ON.equals(value)) Hotspot.start(c, hotspotNotes(c));
            else Hotspot.stop(c, hotspotNotes(c));
        }
        else if (f == FeatureId.SOUND_VOLUME) {               // the level the remote's volume keys move (the music stream)
            android.media.AudioManager a = audio(c);
            if (a == null) return false;
            try {
                a.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Integer.parseInt(value.trim()), 0);
            } catch (RuntimeException e) {
                return false;
            }
        }
        else return false;
        return true;
    }

    /** The start after list: the stock times, plus a stored time outside them with its own label. */
    private static void delayOptions(Context c, MenuState.Builder b, String current) {
        java.util.List<String> opts = ScreensaverDelay.options(current);
        b.options(FeatureId.SETTINGS_SCREENSAVER_DELAY, opts);
        if (!java.util.Arrays.asList(ScreensaverDelay.TOKENS).contains(current)) {
            b.label(FeatureId.SETTINGS_SCREENSAVER_DELAY, current, delayLabel(c, current));
        }
    }

    static String delayLabel(Context c, String token) {
        Integer m = ScreensaverDelay.minutes(token);
        if (m == null || m < 0) return c.getString(R.string.lumen_m_dream_never);
        return m % 60 == 0 ? c.getString(R.string.lumen_m_dream_hours, m / 60) : c.getString(R.string.lumen_m_dream_minutes, m);
    }

    /**
     * The Network popup's values: the Wi-Fi network, the switch, the name and password (stock shows both), greyed
     * name and password and no restore default while the hotspot is off (stock NetworkApViewModel). The first read
     * keeps the reset target, with the model's name in place of Android's made-up one.
     */
    private static void hotspot(Context c, MenuProfile p, MenuState.Builder b) {
        b.put(FeatureId.NETWORK_WIFI, Hotspot.wifiName(c));
        Hotspot.first(c, p.text(FeatureId.NETWORK_HOTSPOT, "name"));
        boolean on = Hotspot.on(c);
        b.put(FeatureId.NETWORK_HOTSPOT, on ? ON : OFF);
        String[] cfg = Hotspot.config(c);
        if (cfg != null) {
            b.put(FeatureId.NETWORK_HOTSPOT_NAME, cfg[0]);
            b.put(FeatureId.NETWORK_HOTSPOT_PASSWORD, cfg[1]);
        }
        if (on) return;
        b.disable(FeatureId.NETWORK_HOTSPOT_NAME).disable(FeatureId.NETWORK_HOTSPOT_PASSWORD);
        b.hide(FeatureId.NETWORK_HOTSPOT_RESET);
    }

    /**
     * {@code st} with the module settings' values on top (null stays null: no device state, no values); the hotspot
     * rows are read only when the menu built them (no Android hotspot calls where the rows are not there).
     */
    static MenuState merge(Context c, MenuProfile p, MenuState st, boolean hotspotRows) {
        if (st == null) return null;
        MenuState.Builder b = MenuState.builder();
        if (hotspotRows) hotspot(c, p, b);
        java.util.Set<FeatureId> mine = new java.util.LinkedHashSet<>(MenuSpec.MODULE_SETTINGS);
        mine.addAll(MenuSpec.EVERY_MODEL);
        mine.remove(FeatureId.NETWORK_HOTSPOT);                   // read with its rows above, only where the profile has it
        for (FeatureId f : mine) {
            String v = get(c, f);
            if (v != null) b.put(f, v);
        }
        String delay = get(c, FeatureId.SETTINGS_SCREENSAVER_DELAY);
        if (delay != null) delayOptions(c, b, delay);
        if (!NetworkAdb.usable(c)) b.disable(FeatureId.DEV_NETWORK_ADB);   // grey without USB debugging, as Android
        facts(c, b);
        return st.with(b.build());
    }

    /** The short values of General's Android screens: the language, the time source, free space, version, model. */
    private static void facts(Context c, MenuState.Builder b) {
        b.put(FeatureId.SETTINGS_LANGUAGE, DeviceFacts.language());
        b.put(FeatureId.SETTINGS_DATE_TIME, DeviceFacts.autoTime(c) ? c.getString(R.string.lumen_m_date_auto)
                : android.text.format.DateFormat.getMediumDateFormat(c).format(new java.util.Date()));
        long free = DeviceFacts.freeBytes();
        if (free >= 0) {
            b.put(FeatureId.SETTINGS_STORAGE, c.getString(R.string.lumen_m_storage_free,
                    android.text.format.Formatter.formatShortFileSize(c, free)));
        }
        String v = DeviceFacts.version();
        if (v != null) b.put(FeatureId.SETTINGS_UPDATE, v);
        b.put(FeatureId.SETTINGS_ABOUT, DeviceFacts.model());
    }
}
