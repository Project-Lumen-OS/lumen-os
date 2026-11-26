// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.menu;

import java.util.Arrays;
import java.util.HashSet;

/** GeneralChoices: the speech language round trip and the source at power-on tables of the shared settings rows. */
public final class GeneralChoicesTest {
    private static int pass, fail;

    private static void check(boolean ok, String what) {
        if (ok) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    public static void main(String[] a) {
        check(GeneralChoices.VOICE[0].equals("system") && GeneralChoices.voiceTag("system").isEmpty(), "system = the system language");
        check(new HashSet<>(Arrays.asList(GeneralChoices.VOICE)).size() == GeneralChoices.VOICE.length, "voice tokens unique");
        HashSet<String> tags = new HashSet<>();
        for (String t : GeneralChoices.VOICE) {
            String tag = GeneralChoices.voiceTag(t);
            check(tags.add(tag), "one speech language per token: " + t);
            check(GeneralChoices.voiceToken(tag).equals(t), "round trip " + t + " = " + tag);
            check(t.equals("system") || tag.startsWith(t + "-"), "token " + t + " is the language of " + tag);
        }
        check(GeneralChoices.voiceTag("en").equals("en-US") && GeneralChoices.voiceTag("pt").equals("pt-BR"), "en-US, pt-BR");
        check(GeneralChoices.voiceTag("xx").isEmpty() && GeneralChoices.voiceTag(null).isEmpty(), "unknown token = system language");
        check(GeneralChoices.voiceToken("xx-XX").equals("system") && GeneralChoices.voiceToken(null).equals("system"),
                "unknown stored language reads as system");
        check(Arrays.asList(GeneralChoices.BOOT_TWO_HDMI).equals(Arrays.asList("home", "hdmi1", "hdmi2")),
                "two HDMI inputs: Home 0, HDMI 1 1, HDMI 2 2");
        check(Arrays.asList(GeneralChoices.BOOT_ONE_HDMI).equals(Arrays.asList("home", "hdmi")), "one HDMI input: Home 0, HDMI 1");
        System.out.println("GeneralChoicesTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
