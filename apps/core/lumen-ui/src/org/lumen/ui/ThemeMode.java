package org.lumen.ui;

/** The user's theme choice, shared by Home, Setup and the menus: Светлая (default), Тёмная, Авто. */
public enum ThemeMode {
    LIGHT("light"), DARK("dark"), AUTO("auto");

    public final String id;

    ThemeMode(String id) {
        this.id = id;
    }

    /** The next mode of the gear menu's theme button: Светлая, Тёмная, Авто, then Светлая again. */
    public ThemeMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** The stored value back to a mode: anything unknown or missing is LIGHT, the default. */
    public static ThemeMode parse(String s) {
        if (s != null) for (ThemeMode m : values()) if (m.id.equals(s.trim())) return m;
        return LIGHT;
    }
}
