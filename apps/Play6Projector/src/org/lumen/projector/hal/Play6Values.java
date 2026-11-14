package org.lumen.projector.hal;

import java.util.function.ToIntFunction;

/**
 * The menu's integer values as the typed vendor values of the shared calls: a value the Play 6 does not send throws
 * IllegalArgumentException, nothing is sent.
 */
public final class Play6Values {
    private Play6Values() {}

    public static PictureMode pictureMode(int v) { return of(PictureMode.values(), v, m -> m.wire, "picture mode"); }
    public static ColorTemp pictureStyle(int v) { return of(ColorTemp.values(), v, m -> m.wire, "picture style"); }
    public static MemcLevel memc(int v) { return of(MemcLevel.values(), v, m -> m.wire, "memc level"); }
    public static NoiseReduction noiseReduction(int v) { return of(NoiseReduction.values(), v, m -> m.wire, "noise reduction"); }
    public static HdmiRange hdmiRange(int v) { return of(HdmiRange.values(), v, m -> m.wire, "hdmi range"); }
    public static AspectRatio aspectRatio(int v) { return of(AspectRatio.values(), v, m -> m.wire, "aspect ratio"); }
    public static SoundEffect soundEffect(int v) { return of(SoundEffect.values(), v, m -> m.wire, "sound mode"); }
    public static PutMode putMode(int v) { return of(PutMode.values(), v, m -> m.wire, "put mode"); }
    public static AudioPath audioPath(int v) { return of(AudioPath.values(), v, m -> m.wire, "audio path"); }
    public static ShiftDir shift(int v) { return of(ShiftDir.values(), v, m -> m.wire, "shift direction"); }
    public static GameModeType gameModeType(int v) { return of(GameModeType.values(), v, m -> m.wire, "game mode type"); }
    public static ManualFocus manualFocus(int v) { return of(ManualFocus.values(), v, m -> m.wire, "manual focus"); }
    public static BootStage bootStage(int v) { return of(BootStage.values(), v, m -> m.wire, "stage"); }

    private static <E> E of(E[] all, int v, ToIntFunction<E> wire, String what) {
        for (E e : all) if (wire.applyAsInt(e) == v) return e;
        throw new IllegalArgumentException(what + " " + v + " not allowed");
    }
}
