package org.lumen.setup;

import org.lumen.setup.net.ListState;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lumen Setup 2.0 host tests (plain JDK, android.jar only to link): step order, the step counter, the remote step's
 * search and its 60 s "not found", the Wi-Fi list states and where each one puts the focus, the language order, the
 * QR code's whole-pixel grid, the install offer's gate, and the source rules (every Lumen icon the wizard draws exists,
 * no colour value of its own in the look, a wrong password comes back selected, spec line heights, unclipped rings).
 *   sh apps/LumenSetup/test/run.sh
 */
public final class FlowTest {
    private static int n, bad;

    private static void ok(boolean c, String what) {
        n++;
        if (!c) {
            bad++;
            System.out.println("FAIL " + what);
        }
    }

    private static String read(Path p) throws Exception {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    public static void main(String[] a) throws Exception {
        Path app = Paths.get(System.getProperty("setup.dir"));
        Path root = app.getParent().getParent();
        String src = "src/org/lumen/setup/";

        // ---- step order (the design notes: Remote, Language, Network, Google, Picture, Launcher, Alice, Done)
        String act = read(app.resolve(src + "SetupActivity.java"));
        Matcher m = Pattern.compile("mSteps\\.add\\(new (\\w+)Step\\(this\\)\\);").matcher(
                act.substring(act.indexOf("private void startSteps()")));
        List<String> order = new ArrayList<>();
        while (m.find() && order.size() < 8) order.add(m.group(1));
        ok(order.toString().equals("[Remote, Welcome, Network, Google, Picture, Launcher, Alice, Done]"),
                "step order " + order);

        // ---- the counter
        boolean[] all = {true, true, true, true, true, true, true, true};
        Progress p = Progress.of(all, 2);
        ok(p.count() == 8 && p.number() == 3, "all steps: Шаг 3 из 8");
        boolean[] noGoogleNoAlice = {true, true, true, false, true, true, false, true};
        p = Progress.of(noGoogleNoAlice, 4);
        ok(p.count() == 6 && p.number() == 4, "a step the unit lacks is not counted: picture = 4 of 6, got "
                + p.number() + " of " + p.count());
        p = Progress.of(noGoogleNoAlice, 7);
        ok(p.number() == 6 && p.count() == 6, "Done is the last of 6");
        p = Progress.of(all, 1);
        ok(p.opacity(0) == 0.75f && p.opacity(1) == 1f && p.opacity(2) == 0.22f,
                "segments: passed 0.75 (a remote that skipped itself counts as passed), current 1, coming 0.22");
        ok(p.width(36, 64, 8) == 7 * 44 + 64, "segment row width 36 x 7 + 64 + 8 gaps");
        p = new Progress(0, 5);
        ok(p.count() == 1 && p.current() == 0, "counter never below 1 of 1");

        // ---- remote search
        RemoteSearch r = new RemoteSearch(1000);
        ok(r.state() == RemoteSearch.State.SEARCHING, "starts searching");
        ok(!r.report(1000 + 59_999, false, false, "", 0), "nothing changes before 60 s");
        ok(r.report(1000 + 60_000, false, false, "", 0) && r.state() == RemoteSearch.State.NOT_FOUND,
                "60 s without a remote: not found");
        ok(!r.report(1000 + 61_000, false, false, "", 0) && r.state() == RemoteSearch.State.NOT_FOUND,
                "not found stays until something is found or Search again");
        r.retry(100_000);
        ok(r.state() == RemoteSearch.State.SEARCHING, "Search again: searching");
        ok(!r.report(159_999, false, false, null, 0), "a new 60 s after Search again");
        ok(r.report(160_000, false, false, null, 0) && r.state() == RemoteSearch.State.NOT_FOUND, "not found again");
        ok(r.report(161_000, false, true, "XGIMI RC", 0) && r.state() == RemoteSearch.State.FOUND
                && r.name().equals("XGIMI RC"), "a remote found while not found: found, with its name");
        r = new RemoteSearch(0);
        ok(r.report(10, false, false, "", 1) && r.state() == RemoteSearch.State.WAKE, "a paired remote asleep: press a key");
        ok(!r.report(70_000, false, false, "", 1) && r.state() == RemoteSearch.State.WAKE, "a paired remote never times out");
        r.connected();
        ok(!r.report(200_000, false, false, "", 0) && r.state() == RemoteSearch.State.CONNECTED, "connected stays");
        r.retry(300_000);
        ok(r.state() == RemoteSearch.State.CONNECTED, "Search again cannot undo connected");
        r = new RemoteSearch(0);
        ok(!r.report(5, false, true, "", 0) && r.state() == RemoteSearch.State.SEARCHING, "pairing without a name: searching");
        ok(r.report(6, true, false, "", 0) && r.state() == RemoteSearch.State.CONNECTED, "bridge connected");
        ok(RemoteSearch.TIMEOUT_MS == 60_000, "the not found time is 60 s");

        // ---- Wi-Fi list
        ok(ListState.of(false, 0, false) == ListState.WIFI_OFF, "Wi-Fi switching on");
        ok(ListState.of(true, 0, false) == ListState.SCANNING, "first scan");
        ok(ListState.of(true, 0, true) == ListState.NONE, "scan came back empty: No networks found");
        ok(ListState.of(true, 3, true) == ListState.LIST && ListState.of(false, 3, false) == ListState.LIST,
                "networks: the list");

        // ---- install offer gate (every model, only during a try, kill switch)
        ok(InstallOffer.gate("1", ""), "offer during a try");
        ok(InstallOffer.gate("1", "1"), "offer with the switch on");
        ok(!InstallOffer.gate("1", "0"), "kill switch ro.lumen.dsu_install=0");
        ok(!InstallOffer.gate("0", "") && !InstallOffer.gate("", ""), "no offer on a normal boot");
        String welcome = read(app.resolve(src + "steps/WelcomeStep.java"));
        int offer = welcome.indexOf("if (InstallOffer.available(host))"), langs = welcome.indexOf("for (int i : Langs.order(sel))");
        ok(offer > 0 && offer < langs, "the install row is the first row of the language step");
        ok(welcome.contains("offer.startIcon(new Icon(ctx(), Icon.INSTALL), 32)") && welcome.contains("\"offer_short\""),
                "the install row: the row look with its icon and the installer's one short line");

        // ---- focus on entry: a real row, never the scrolling container
        ok(ListState.focus(ListState.LIST) == ListState.FOCUS_FIRST_NETWORK, "networks: the first network is focused");
        ok(ListState.focus(ListState.NONE) == ListState.FOCUS_SEARCH_AGAIN, "no networks: Search again is focused");
        ok(ListState.focus(ListState.SCANNING) == ListState.FOCUS_ADD_MANUALLY
                && ListState.focus(ListState.WIFI_OFF) == ListState.FOCUS_ADD_MANUALLY, "first scan: Add network manually");
        String net = read(app.resolve(src + "steps/NetworkStep.java"));
        String box = read(app.resolve(src + "ui/ScrollBox.java"));
        ok(box.contains("scroll.setFocusable(false);") && !net.contains("new ScrollView"),
                "the Wi-Fi list scrolls in a ScrollBox whose scroll view never takes the focus");
        ok(net.contains("showPage(v, null);") && net.contains("private void autoFocus()") && net.contains("mUserMoved"),
                "the list page focuses its first row and follows new results until the user moves");
        String remote = read(app.resolve(src + "steps/RemoteStep.java"));
        ok(remote.contains("st == RemoteSearch.State.NOT_FOUND ? mRetry"), "remote not found: Search again is focused");
        ok(remote.contains("status(Icon.DONE, Ui.OK, s(R.string.remote_found, mSearch.name()), s(R.string.remote_press_ok));")
                && remote.contains("mOther.setVisibility(lost || ok ? View.GONE : View.VISIBLE);"),
                "remote found: the check, the name in the success colour, press OK to finish, only Skip");

        // ---- a wrong password comes back typed and selected, so typing replaces it
        ok(net.contains("showPassword(a, s(R.string.net_wrong_password), mAPw);"), "wrong password: the typed text goes back");
        int sp = net.indexOf("private void showPassword(");
        String pw = sp < 0 ? "" : net.substring(sp, net.indexOf("private void submitPassword()"));
        ok(net.contains("showPage(v, null);\n        // after the page's own focus request") && net.contains("v.post(this::autoFocus);"),
                "the list page corrects its own focus request once laid out: the first network, not a later row");
        ok(pw.contains("mPw.keepSelected(typed);") && net.contains("mManPw.keepSelected(mAPw);"),
                "the password field holds the typed text selected (list and hidden network)");
        String field = read(app.resolve(src + "ui/Field.java"));
        ok(field.contains("setSelectAllOnFocus(true);") && field.contains("selectAll();")
                && field.contains("count > 0 ? s.subSequence(start, start + count).toString() : \"\"")
                && field.contains("e.replace(0, e.length(), typed);"),
                "Field.keepSelected: selected, and the first key replaces the old text with any keyboard");

        // ---- language order: the current one first, then the rest
        int ru = Langs.indexOf(java.util.Locale.forLanguageTag("ru-RU"));
        int[] ord = Langs.order(ru);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < 6; i++) names.append(Langs.NAMES[ord[i]]).append(i < 5 ? ", " : "");
        ok(names.toString().equals("Русский, English, Українська, Қазақша, Беларуская, Deutsch"),
                "Russian first, then the spec order: " + names);
        ok(ord.length == Langs.TAGS.length && Langs.NAMES.length == Langs.TAGS.length, "every language once");
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int i : ord) seen.add(i);
        ok(seen.size() == Langs.TAGS.length, "the order is a permutation of the languages");
        ok(Langs.NAMES[Langs.order(Langs.indexOf(java.util.Locale.forLanguageTag("de-DE")))[0]].equals("Deutsch")
                && Langs.NAMES[Langs.order(-1)[0]].equals("English"), "German current: Deutsch first, unknown: English");
        ok(Langs.NAMES[Langs.indexOf(java.util.Locale.forLanguageTag("kk-KZ"))].equals("Қазақша"), "Kazakh: Қазақша");
        ok(Langs.TAGS[Langs.indexOf(java.util.Locale.forLanguageTag("zh-Hant-HK"))].equals("zh-TW")
                && Langs.TAGS[Langs.indexOf(java.util.Locale.forLanguageTag("zh-CN"))].equals("zh-CN"), "Chinese by script");

        // ---- QR geometry: whole-pixel modules in one path, the code fills the 400 card
        org.lumen.qr.QrCode q = org.lumen.qr.QrCode.encode("https://yandex.ru/quasar/lumen?code=482913");
        int qm = org.lumen.qr.QrView.module(352, q.size, 0);
        ok(q.size == 29 && qm == 12 && qm * q.size <= 352 && qm * q.size >= 0.84 * 400,
                "the demo code: 29 modules of 12 px, " + qm * q.size + " px of the 400 card");
        boolean fits = true;
        for (int v = 1; v <= 10; v++) {
            int size = v * 4 + 17, mm = org.lumen.qr.QrView.module(352, size, 0), off = org.lumen.qr.QrView.offset(352, size, mm);
            fits &= mm * size <= 352 && mm * size >= 0.78 * 400 && off >= 0 && off + mm * size <= 352;
        }
        ok(fits, "versions 1 to 10: whole modules inside 352 px, at least 78 % of the card");
        ok(org.lumen.qr.QrView.module(260, 25, 4) == 7, "with a 4 module quiet zone: 260 px / 33 = 7");
        String qv = read(root.resolve("apps/core/lumen-qr/src/org/lumen/qr/QrView.java"));
        ok(qv.contains("dark.setAntiAlias(false);") && qv.contains("cv.drawPath(path, dark);") && !qv.contains("drawRect((x"),
                "the shared QrView draws the dark modules as one path without anti-aliasing");
        ok(!Files.exists(app.resolve(src + "ui/Qr.java")) && !Files.exists(app.resolve(src + "ui/QrView.java")),
                "no QR copy of its own: the wizard uses apps/core/lumen-qr");
        String alice = read(app.resolve(src + "steps/AliceStep.java"));
        ok(alice.contains("public int rightTop() { return 300; }") && alice.contains("QR = 352") && alice.contains(".quiet(0)"),
                "Alice: the card level with the title, the code 352 in the 400 card");

        // ---- spec line heights (CSS sense) and focus rings that are never cut
        StringBuilder all2 = new StringBuilder();
        try (java.util.stream.Stream<Path> w = Files.walk(app.resolve("src"))) {
            for (Path f : (Iterable<Path>) w.filter(x -> x.toString().endsWith(".java"))::iterator) all2.append(read(f));
        }
        ok(!all2.toString().contains("setLineSpacing("), "no spacing multiplier: line heights are px (Ui.lineHeight)");
        String ui = read(app.resolve(src + "ui/Ui.java"));
        ok(ui.contains("t.setLineHeight(Math.round(t.getTextSize() * cssLineHeight));") && ui.contains("LH_TITLE = 1.08f, LH_BODY = 1.45f"),
                "Ui.lineHeight: baseline to baseline = size x the spec line height");
        String step = read(app.resolve(src + "steps/Step.java"));
        ok(step.contains("mPages.setClipChildren(false);") && step.contains("mPages.setClipToPadding(false);"),
                "sub-pages never clip a focus ring at the column edge");
        ok(box.contains("public static final int ROOM = 36, TOP = 8, BOTTOM = 28;") && box.contains("p.setMarginStart(-Ui.px(ROOM));"),
                "a scrolling list leaves room for the row scale and shadow past the column");
        String done = read(app.resolve(src + "steps/DoneStep.java"));
        ok(done.contains("ScrollBox.lp(ViewGroup.LayoutParams.WRAP_CONTENT, 1f)") && !done.contains("LayoutParams.MATCH_PARENT, 0, 1f"),
                "Done: Start right under the summary, not pinned to the bottom");

        // ---- every Lumen icon the wizard draws exists in apps/core/lumen-icons
        String icon = read(app.resolve(src + "ui/Icon.java"));
        Matcher im = Pattern.compile("R\\.drawable\\.(lumen_ic_\\w+)").matcher(icon);
        int icons = 0;
        while (im.find()) {
            icons++;
            ok(Files.isRegularFile(root.resolve("apps/core/lumen-icons/res/drawable/" + im.group(1) + ".xml")),
                    "icon " + im.group(1) + " in lumen-icons");
        }
        ok(icons >= 25, "Icon maps its types to Lumen icons: " + icons);

        // ---- the look takes its colours from the shared Palette only
        Pattern lit = Pattern.compile("0x[0-9A-Fa-f]{8}");
        for (String f : new String[] {"ui/Ui.java", "ui/FocusFill.java", "ui/Ring.java", "ui/Dots.java", "ui/Row.java",
                "ui/Pill.java", "ui/Field.java", "ui/Tile.java", "ui/Wordmark.java", "ui/AuroraView.java", "ui/GridArt.java",
                "ui/Sheet.java", "steps/NetworkStep.java", "steps/RemoteStep.java", "steps/WelcomeStep.java",
                "steps/GoogleStep.java", "steps/PictureStep.java", "steps/DoneStep.java"}) {
            Matcher lm = lit.matcher(read(app.resolve(src + f)));
            List<String> found = new ArrayList<>();
            // transparent, and pure white under a QR code (scanned on any theme)
            while (lm.find()) if (!lm.group().equalsIgnoreCase("0x00000000") && !lm.group().equalsIgnoreCase("0xFFFFFFFF")) found.add(lm.group());
            ok(found.isEmpty(), f + ": no colour value of its own " + found);
        }
        ok(read(app.resolve(src + "ui/Ui.java")).contains("P = LumenTheme.palette(c);"), "Ui reads the theme's Palette");

        System.out.println("setup flow: " + n + " checks, " + bad + " failed");
        System.exit(bad == 0 ? 0 : 1);
    }
}
