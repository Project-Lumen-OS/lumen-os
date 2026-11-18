package org.lumen.projector.audio;

/** Plain rules of {@link AudioPorts} (host tested by sh test/sound/run.sh). */
final class AudioPortRules {
    private AudioPortRules() {}

    static final long FIRST_MS = 2_000L, MAX_MS = 300_000L;

    /** Delay before probe number {@code failures + 1}: 2 s doubling, at most 5 min (never the framework's 100 ms). */
    static long backoffMs(int failures) {
        if (failures < 0) failures = 0;
        long d = FIRST_MS << Math.min(failures, 20);
        return Math.min(MAX_MS, d);
    }

    /** The audio policy answers when the output list is not empty (the speaker is always listed then). */
    static boolean policyUp(int outputs) { return outputs > 0; }
}
