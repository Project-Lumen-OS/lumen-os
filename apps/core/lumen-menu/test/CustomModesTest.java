// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.Arrays;
import java.util.List;

/** CustomModes: the stock list, its names, its limits and the menu rows it gives. java org.lumen.menu.CustomModesTest */
public final class CustomModesTest {
    private static int pass, fail;

    private static void ok(String what, boolean cond) {
        if (cond) pass++;
        else { fail++; System.out.println("FAIL " + what); }
    }

    private static final List<String> HB = Arrays.asList("ai", "standard", "sport", "color_accuracy", "hdr_vivid", "performance");

    private static CustomModes of(Object... nameValue) {
        CustomModes m = CustomModes.NONE;
        for (int i = 0; i < nameValue.length; i += 2) m = m.plus(new CustomMode((String) nameValue[i], (Integer) nameValue[i + 1]));
        return m;
    }

    public static void main(String[] a) {
        // the stock setting, both ways
        CustomModes two = of("Кино ночью", 40, "Day \"2\"", 41);
        ok("json is the stock gson form", "[{\"pictureName\":\"Кино ночью\",\"value\":40},{\"pictureName\":\"Day \\\"2\\\"\",\"value\":41}]".equals(two.json()));
        ok("json reads back equal", two.equals(CustomModes.parse(two.json())));
        ok("stock gson with spaces and escapes", CustomModes.parse(" [ { \"value\" : 7 , \"pictureName\" : \"A\\u0042\\n\" } ] ")
                .equals(of("AB\n", 7)));
        ok("an unknown key is skipped", CustomModes.parse("[{\"pictureName\":\"x\",\"extra\":\"y\",\"n\":3,\"value\":5}]").equals(of("x", 5)));
        ok("empty and null are no modes", CustomModes.parse("").size() == 0 && CustomModes.parse(null).size() == 0
                && CustomModes.parse("[]").size() == 0);
        ok("a broken value is no modes", CustomModes.parse("[{\"pictureName\":\"x\",\"value\":}").size() == 0
                && CustomModes.parse("{\"a\":1}").size() == 0 && CustomModes.parse("[{\"pictureName\":\"x").size() == 0);
        ok("an entry without a name or value is dropped", CustomModes.parse("[{\"value\":3},{\"pictureName\":\"b\",\"value\":4}]").equals(of("b", 4)));
        ok("NONE writes []", "[]".equals(CustomModes.NONE.json()));
        ok("a control character is escaped", CustomModes.parse(of("a\u0001", 1).json()).equals(of("a\u0001", 1)));

        // stock add rules: id and name unique, order kept
        ok("same id twice is refused", two.plus(new CustomMode("other", 40)) == two);
        ok("same name twice is refused", two.plus(new CustomMode("Кино ночью", 50)) == two);
        ok("a negative id is refused", two.plus(new CustomMode("neg", -1)) == two);
        ok("added at the end", two.plus(new CustomMode("z", 3)).all().get(2).equals(new CustomMode("z", 3)));
        ok("minus removes only that id", two.minus(40).equals(of("Day \"2\"", 41)) && two.minus(99) == two);
        ok("of(value) and of(token)", two.of(41).name.equals("Day \"2\"") && two.of("custom_40").value == 40
                && two.of("custom_9") == null && two.of("sport") == null && two.of(null) == null);

        // tokens
        ok("token form", "custom_40".equals(CustomModes.token(40)));
        ok("value of a token", CustomModes.value("custom_40") == 40 && CustomModes.value("custom_0") == 0);
        ok("no value for other tokens", CustomModes.value("sport") == null && CustomModes.value("custom_") == null
                && CustomModes.value("custom_-1") == null && CustomModes.value("custom_4x") == null
                && CustomModes.value("custom_1234567890") == null && CustomModes.value(null) == null);

        // names: stock checks and the stock length rule
        ok("empty name", two.check("") == NameCheck.EMPTY && two.check(null) == NameCheck.EMPTY);
        ok("taken name", two.check("Кино ночью") == NameCheck.TAKEN);
        ok("new name", two.check("Кино") == NameCheck.OK);
        ok("24 latin letters fit, 25 do not", CustomModes.fits("abcdefghijklmnopqrstuvwx") && !CustomModes.fits("abcdefghijklmnopqrstuvwxy"));
        ok("a Chinese character counts 3", CustomModes.nameLength("影") == 3 && CustomModes.fits("影影影影影影影影")
                && !CustomModes.fits("影影影影影影影影a"));
        ok("a Cyrillic letter counts 3 and eats the next byte", CustomModes.nameLength("Ж") == 3 && CustomModes.nameLength("ЖЖ") == 6
                && CustomModes.nameLength("ЖЖЖ") == 6 && CustomModes.nameLength("Жa") == 3);
        ok("stock limit numbers", CustomModes.NAME_MAX == 24 && CustomModes.MAX == 10
                && "customize_data_key".equals(CustomModes.SETTING));

        // the count limit
        CustomModes nine = CustomModes.NONE;
        for (int i = 0; i < 9; i++) nine = nine.plus(new CustomMode("m" + i, 50 + i));
        ok("nine modes are not full", !nine.full());
        CustomModes ten = nine.plus(new CustomMode("m9", 59));
        ok("ten modes are full", ten.full() && ten.size() == 10);

        // the menu rows
        MenuState st = MenuState.builder().put(FeatureId.PICTURE_MODE, "custom_41").build();
        MenuState part = two.rows(HB, st);
        ok("user modes follow the stock modes", part.options(FeatureId.PICTURE_MODE).equals(Arrays.asList(
                "ai", "standard", "sport", "color_accuracy", "hdr_vivid", "performance", "custom_40", "custom_41")));
        ok("their names label them", "Кино ночью".equals(part.label(FeatureId.PICTURE_MODE, "custom_40")));
        ok("a user mode in use shows base and delete", !part.hidden(FeatureId.PICTURE_CUSTOM_BASE)
                && !part.hidden(FeatureId.PICTURE_CUSTOM_DELETE) && !part.hidden(FeatureId.PICTURE_CUSTOM_NEW));
        MenuState stock = two.rows(HB, MenuState.builder().put(FeatureId.PICTURE_MODE, "sport").build());
        ok("a stock mode hides base and delete", stock.hidden(FeatureId.PICTURE_CUSTOM_BASE) && stock.hidden(FeatureId.PICTURE_CUSTOM_DELETE));
        MenuState orphan = two.rows(HB, MenuState.builder().put(FeatureId.PICTURE_MODE, "custom_77").build());
        ok("an id the list does not name is no user mode", orphan.hidden(FeatureId.PICTURE_CUSTOM_BASE)
                && orphan.hidden(FeatureId.PICTURE_CUSTOM_DELETE));
        ok("a full list hides the Custom entry", ten.rows(HB, st).hidden(FeatureId.PICTURE_CUSTOM_NEW));
        MenuState sub = MenuState.builder().options(FeatureId.PICTURE_MODE, Arrays.asList("standard", "sport")).build();
        ok("the device's runtime subset stays first", two.rows(HB, sub).options(FeatureId.PICTURE_MODE)
                .equals(Arrays.asList("standard", "sport", "custom_40", "custom_41")));
        ok("no modes: only the stock modes", CustomModes.NONE.rows(HB, st).options(FeatureId.PICTURE_MODE).equals(HB));
        ok("merged over the read state the value stays", "custom_41".equals(st.with(part).get(FeatureId.PICTURE_MODE)));

        System.out.println("CustomModesTest: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
