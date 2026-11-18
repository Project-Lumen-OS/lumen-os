package org.lumen.home.model;

/** The calm screen's greeting by the local hour. Pure. */
public final class Greeting {
    public static final int MORNING = 0, DAY = 1, EVENING = 2, NIGHT = 3;

    private Greeting() {}

    /** 05-11 morning, 12-17 day, 18-22 evening, 23-04 night. */
    public static int of(int hour) {
        if (hour >= 5 && hour < 12) return MORNING;
        if (hour >= 12 && hour < 18) return DAY;
        if (hour >= 18 && hour < 23) return EVENING;
        return NIGHT;
    }
}
