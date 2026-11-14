package org.lumen.projector.hal;

/**
 * The output timing of getOutputTimingInfo (stock IGmpf::OutputTimingInfo: user timing, display size, current timing,
 * current size, vertical frequency). Read on the Z9X (Ultra 120 Hz on reads 4,3840,2160,4,1920,1080,120); a projector
 * without Ultra 120 Hz never builds one.
 */
public final class OutputTimingInfo {
    public final int userTiming, dispWidth, dispHeight, currTiming, currWidth, currHeight, vfreq;

    OutputTimingInfo(int userTiming, int dispWidth, int dispHeight, int currTiming, int currWidth, int currHeight, int vfreq) {
        this.userTiming = userTiming;
        this.dispWidth = dispWidth;
        this.dispHeight = dispHeight;
        this.currTiming = currTiming;
        this.currWidth = currWidth;
        this.currHeight = currHeight;
        this.vfreq = vfreq;
    }

    /** Stock V3DisplayManager: userTiming == 4 is "Ultra 120 Hz on". */
    public boolean isUltra120() { return userTiming == OutputTiming.ULTRA_120_ON.wire; }

    @Override
    public String toString() {
        return "user=" + userTiming + " out=" + currWidth + "x" + currHeight + "@" + vfreq + " timing=" + currTiming;
    }
}
