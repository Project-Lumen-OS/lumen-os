// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import org.lumen.home.proj.ProjectorTarget;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Which projector app Lumen Home talks to (proj/ProjectorTarget) and the contract both projector apps serve: an X50
 * family image (ro.z9x.device=x50) talks to org.lumen.x50.projector, every other image to org.lumen.projector as before; each of the two apps
 * defines the permissions and serves the SHOW_PANEL / SHOW_SOURCE broadcasts and the standby-state provider Home uses,
 * and the X50 app's mic key reaches Home's voice search. Reads apps/X50Projector and apps/ProjectorBase next to Home.
 */
public final class ProjectorTargetTest {
    static final String A = "http://schemas.android.com/apk/res/android";
    static final String P = "org.lumen.projector.permission.";

    public static void run() throws Exception {
        // by the image (ro.z9x.device) only: every X50 family image, never a model code or a device name
        T.eq(ProjectorTarget.packageFor("x50", ""), "org.lumen.x50.projector", "an X50 family image (X50 Ultra, Ultra Max, TITAN Noir)");
        T.eq(ProjectorTarget.packageFor(" x50\n", ""), "org.lumen.x50.projector", "X50 image with blanks");
        T.eq(ProjectorTarget.packageFor("X50", ""), "org.lumen.projector", "exact value only (case)");
        T.eq(ProjectorTarget.packageFor("x50max", ""), "org.lumen.projector", "an image token is not the family value");
        T.eq(ProjectorTarget.packageFor("z9x", ""), "org.lumen.projector", "Z9X image unchanged");
        T.eq(ProjectorTarget.packageFor("G0111", ""), "org.lumen.projector", "a model code never selects the X50 app");
        T.eq(ProjectorTarget.packageFor("H0062", ""), "org.lumen.projector", "a model code never selects the X50 app (TITAN)");
        T.eq(ProjectorTarget.packageFor("", ""), "org.lumen.projector", "unset (HB, Play 6)");
        T.eq(ProjectorTarget.packageFor(null, ""), "org.lumen.projector", "unreadable");
        T.eq(ProjectorTarget.packageFor("horse_popular", ""), "org.lumen.projector", "a device name never selects the X50");
        // the RS30 Ultra Max image: its own recipe without ro.z9x.device, the X50 family app by its ro.lumen.device
        T.eq(ProjectorTarget.packageFor("", "g0155"), "org.lumen.x50.projector", "RS30 Ultra Max image (ro.lumen.device g0155)");
        T.eq(ProjectorTarget.packageFor(null, " g0155\n"), "org.lumen.x50.projector", "RS30 image with blanks");
        T.eq(ProjectorTarget.packageFor("", "G0155"), "org.lumen.projector", "exact ro.lumen.device only (case)");
        T.eq(ProjectorTarget.packageFor("", "g01550"), "org.lumen.projector", "exact RS30 image only");
        T.eq(ProjectorTarget.packageFor("z9x", "horse_popular"), "org.lumen.projector", "Z9X image unchanged");
        T.eq(ProjectorTarget.packageFor("", "goat_elegant"), "org.lumen.projector", "HB image unchanged");
        T.eq(ProjectorTarget.packageFor("x50", "h0062"), "org.lumen.x50.projector", "TITAN image by its ro.z9x.device");
        File g = new File(System.getProperty("home.dir", ".")).getParentFile().getParentFile();
        for (String pf : new String[] {"x50_prop.txt", "x50_prop_pub.txt", "x50max_prop_pub.txt"}) {
            File f = new File(g, "devices/g0111/image/" + pf);
            if (f.isFile()) T.ok(read(f).contains("\nro.z9x.device=x50\n"), pf + ": the X50 family image says ro.z9x.device=x50");
        }

        File home = new File(System.getProperty("home.dir", "."));
        File apps = home.getParentFile();
        String bridge = read(new File(home, "src/org/lumen/home/proj/ProjectorBridge.java"));
        T.ok(!bridge.contains("setPackage(PROJECTOR)"), "bridge: no broadcast pinned to org.lumen.projector");
        T.eq(count(bridge, ".setPackage(projector())"), 2, "bridge: SHOW_PANEL, SET_LAUNCHER to the device's projector app (inputs: Home's own source menu)");
        T.ok(bridge.contains("projector().equals(pi.packageName)"), "bridge: device-state provider owned by the device's projector app");
        T.ok(bridge.contains("ProjectorTarget.packageFor(image, lumen)") && bridge.contains("\"ro.z9x.device\"")
                && bridge.contains("\"ro.lumen.device\"")
                && !bridge.contains("modelname"), "bridge: projector app chosen by the image's ro.z9x.device and ro.lumen.device, never the model");
        T.ok(bridge.contains("PROJECTOR = ProjectorTarget.DEFAULT"), "bridge: default stays org.lumen.projector");

        Document hm = xml(new File(home, "AndroidManifest.xml"));
        for (String perm : new String[] {"SHOW_PANEL", "SHOW_SOURCE", "STANDBY_STATE"}) {
            T.ok(names(hm, "uses-permission").contains(P + perm), "home uses " + perm);
        }
        T.ok(queries(hm).contains("org.lumen.x50.projector") && queries(hm).contains("org.lumen.projector"),
                "home sees both projector apps");

        if (!new File(apps, "X50Projector/AndroidManifest.xml").isFile() || !new File(apps, "ProjectorBase/AndroidManifest.xml").isFile()) {
            System.out.println("SKIP projector contract: apps/X50Projector or apps/ProjectorBase not in this tree");
            return;
        }
        // both projector apps serve the same contract
        for (String app : new String[] {"ProjectorBase", "X50Projector"}) {
            Document m = xml(new File(apps, app + "/AndroidManifest.xml"));
            for (String perm : new String[] {"SHOW_PANEL", "SHOW_SOURCE", "STANDBY_STATE"}) {
                Element d = byName(m, "permission", P + perm);
                T.ok(d != null && "signature".equals(d.getAttributeNS(A, "protectionLevel")), app + " defines " + perm + " (signature)");
            }
            T.ok(receiverFor(m, "org.lumen.projector.action.SHOW_PANEL", P + "SHOW_PANEL"), app + ": SHOW_PANEL receiver guarded");
            T.ok(receiverFor(m, "org.lumen.projector.action.SHOW_SOURCE", P + "SHOW_SOURCE"), app + ": SHOW_SOURCE receiver guarded");
            Element sp = provider(m, "org.lumen.projector.standbystate");
            T.ok(sp != null && (P + "STANDBY_STATE").equals(sp.getAttributeNS(A, "permission"))
                    && "true".equals(sp.getAttributeNS(A, "exported")), app + ": standby-state provider guarded");
        }
        Document x50 = xml(new File(apps, "X50Projector/AndroidManifest.xml"));
        T.eq(((Element) x50.getDocumentElement()).getAttribute("package"), "org.lumen.x50.projector", "X50 app package");
        File xs = new File(apps, "X50Projector/src/org/lumen/x50/projector");
        String standby = read(new File(xs, "StandbyStateProvider.java"));
        T.ok(standby.contains("checkCallingPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED")
                && standby.contains("METHOD = \"standby_state\"") && standby.contains("b.putBoolean(\"standby\", false)"),
                "X50 standby provider: permission checked in call(), standby_state -> false");
        String panel = read(new File(xs, "PanelReceiver.java"));
        T.ok(panel.contains("i.getStringExtra(\"section\"), i.getStringExtra(\"action\")") && panel.contains("KeyReceiver.openMenu(c, t.row)"),
                "X50 panel receiver: Home's section / action extras open the menu past the eye-protection check");
        String source = read(new File(xs, "SourceReceiver.java"));
        T.ok(source.contains("SourceKey.press(c, why)") && source.contains("Menu.mayOpenOver(top)"),
                "X50 source receiver: the Source key's step (lumen-source), past the eye-protection check");
        T.ok(new File(apps, "LumenTvInput/src/org/lumen/tvinput/InputPickerActivity.java").isFile(), "tvinput picker class exists");

        // the mic key: the X50 app starts Home's voice search and sends the release, Home guards both with MIC
        T.ok(names(x50, "uses-permission").contains("org.lumen.home.permission.MIC"), "X50 app holds Home's MIC permission");
        String key = read(new File(xs, "KeyReceiver.java"));
        T.ok(key.contains("\"org.lumen.home.action.VOICE_SEARCH\"") && key.contains("\"org.lumen.home.action.MIC_UP\"")
                && key.contains("HOME = \"org.lumen.home\""), "X50 key receiver uses Home's voice actions");
        Element alias = byName(hm, "activity-alias", ".search.VoiceSearchAlias");
        T.ok(alias != null && "org.lumen.home.permission.MIC".equals(alias.getAttributeNS(A, "permission"))
                && actions(alias).contains("org.lumen.home.action.VOICE_SEARCH"), "home voice search alias guarded by MIC");
        Element mic = byName(hm, "receiver", ".search.MicUpReceiver");
        T.ok(mic != null && "org.lumen.home.permission.MIC".equals(mic.getAttributeNS(A, "permission"))
                && actions(mic).contains("org.lumen.home.action.MIC_UP"), "home MIC_UP receiver guarded by MIC");
        System.out.println("projector target tests done");
    }

    static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + 1)) n++;
        return n;
    }

    static Document xml(File f) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(f);
    }

    static List<Element> all(Document d, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nl = d.getElementsByTagName(tag);
        for (int i = 0; i < nl.getLength(); i++) out.add((Element) nl.item(i));
        return out;
    }

    static List<String> names(Document d, String tag) {
        List<String> out = new ArrayList<>();
        for (Element e : all(d, tag)) out.add(e.getAttributeNS(A, "name"));
        return out;
    }

    static Element byName(Document d, String tag, String name) {
        for (Element e : all(d, tag)) if (name.equals(e.getAttributeNS(A, "name"))) return e;
        return null;
    }

    static List<String> queries(Document d) {
        List<String> out = new ArrayList<>();
        for (Element q : all(d, "queries")) {
            NodeList nl = q.getElementsByTagName("package");
            for (int i = 0; i < nl.getLength(); i++) out.add(((Element) nl.item(i)).getAttributeNS(A, "name"));
        }
        return out;
    }

    static List<String> actions(Element e) {
        List<String> out = new ArrayList<>();
        NodeList nl = e.getElementsByTagName("action");
        for (int i = 0; i < nl.getLength(); i++) out.add(((Element) nl.item(i)).getAttributeNS(A, "name"));
        return out;
    }

    static boolean receiverFor(Document d, String action, String perm) {
        for (Element r : all(d, "receiver")) {
            if (actions(r).contains(action)) return perm.equals(r.getAttributeNS(A, "permission")) && "true".equals(r.getAttributeNS(A, "exported"));
        }
        return false;
    }

    static Element provider(Document d, String authority) {
        for (Element p : all(d, "provider")) if (authority.equals(p.getAttributeNS(A, "authorities"))) return p;
        return null;
    }
}
