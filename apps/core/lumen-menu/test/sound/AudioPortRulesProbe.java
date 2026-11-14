package org.lumen.projector.audio;

import java.util.function.BiConsumer;

/** Test probe in the audio package (test/sound): AudioPortRules is package private. */
public final class AudioPortRulesProbe {
    private AudioPortRulesProbe() {}

    public static void run(BiConsumer<Boolean, String> ok) {
        ok.accept(AudioPortRules.backoffMs(0) == 2_000L, "first probe after 2 s");
        ok.accept(AudioPortRules.backoffMs(1) == 4_000L, "then 4 s");
        ok.accept(AudioPortRules.backoffMs(3) == 16_000L, "then doubling");
        ok.accept(AudioPortRules.backoffMs(7) == 256_000L, "256 s");
        ok.accept(AudioPortRules.backoffMs(8) == 300_000L, "capped at 5 min");
        ok.accept(AudioPortRules.backoffMs(1000) == 300_000L, "no overflow");
        ok.accept(AudioPortRules.backoffMs(-3) == 2_000L, "negative count");
        long sum = 0;
        for (int i = 0; i < 20; i++) sum += AudioPortRules.backoffMs(i);
        ok.accept(sum > 60L * 60_000L, "20 probes take more than an hour (was 10 calls a second)");
        for (int i = 0; i < 50; i++) ok.accept(AudioPortRules.backoffMs(i) >= 1_000L, "never faster than 1 s (" + i + ")");
        ok.accept(!AudioPortRules.policyUp(0), "no outputs: policy down");
        ok.accept(AudioPortRules.policyUp(1), "the speaker: policy up");
        ok.accept(AudioPortRules.trialNoSound("1", "missing", false), "trial, blobs missing, no policy: notice");
        ok.accept(AudioPortRules.trialNoSound("1", "", false), "trial, blobs prop unset: notice");
        ok.accept(!AudioPortRules.trialNoSound("1", "ok", false), "trial with the blobs: no notice");
        ok.accept(!AudioPortRules.trialNoSound("", "missing", false), "installed system: no notice");
        ok.accept(!AudioPortRules.trialNoSound("0", "missing", false), "gsid 0: no notice");
        ok.accept(!AudioPortRules.trialNoSound("1", "missing", true), "policy answers: no notice");
    }
}
