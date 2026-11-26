/*
 * Copyright (C) 2026 Lumen OS
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.lumen.airplay.cast;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/** Host checks of the casting screen's rules and model table. java CastTest &lt;models.tsv&gt; ; exit 0 = all passed. */
public final class CastTest {
    private static int fails, runs;

    private static void ok(String what, boolean cond) {
        runs++;
        if (!cond) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    public static void main(String[] a) throws Exception {
        // AirPlay card
        ok("airplay off wins", CastRules.airplay(false, "RUNNING", true, false) == CardState.OFF);
        ok("airplay error", CastRules.airplay(true, "ERROR", false, false) == CardState.ERROR);
        ok("airplay starting while stopped", CastRules.airplay(true, "STOPPED", false, false) == CardState.STARTING);
        ok("airplay starting while starting", CastRules.airplay(true, "STARTING", false, false) == CardState.STARTING);
        ok("airplay starting without a service", CastRules.airplay(true, null, false, false) == CardState.STARTING);
        ok("airplay connected", CastRules.airplay(true, "RUNNING", true, true) == CardState.CONNECTED);
        ok("airplay not visible", CastRules.airplay(true, "RUNNING", false, true) == CardState.NOT_VISIBLE);
        ok("airplay ready", CastRules.airplay(true, "RUNNING", false, false) == CardState.READY);
        // Miracast card
        ok("miracast absent", CastRules.miracast(false, true, true, "READY", true) == CardState.ABSENT);
        ok("miracast no answer", CastRules.miracast(true, false, true, "READY", false) == CardState.ERROR);
        ok("miracast mirroring", CastRules.miracast(true, true, true, "READY", true) == CardState.CONNECTED);
        ok("miracast in use", CastRules.miracast(true, true, true, "IN_USE", false) == CardState.CONNECTED);
        ok("miracast off by switch", CastRules.miracast(true, true, false, "READY", false) == CardState.OFF);
        ok("miracast fault is not working", CastRules.miracast(true, true, true, "FAULT", false) == CardState.ERROR);
        ok("mirroring card names the cause while starting",
                "cause".equals(CastRules.mirroringBody(CardState.STARTING, "cause", "how")));
        ok("mirroring card names the cause while not working",
                "cause".equals(CastRules.mirroringBody(CardState.ERROR, "cause", "how")));
        ok("mirroring card says how to connect when ready", "how".equals(CastRules.mirroringBody(CardState.READY, "cause", "how")));
        ok("mirroring card says how to connect without a cause", "how".equals(CastRules.mirroringBody(CardState.STARTING, "", "how")));
        ok("an old sink without detail keeps the how text", "how".equals(CastRules.mirroringBody(CardState.ERROR, null, "how")));
        ok("miracast off by code", CastRules.miracast(true, true, true, "OFF", false) == CardState.OFF);
        ok("miracast unsupported", CastRules.miracast(true, true, true, "UNSUPPORTED", false) == CardState.ABSENT);
        ok("miracast no wifi", CastRules.miracast(true, true, true, "NO_WIFI", false) == CardState.NO_WIFI);
        ok("miracast starting", CastRules.miracast(true, true, true, "STARTING", false) == CardState.STARTING);
        ok("miracast ready", CastRules.miracast(true, true, true, "READY", false) == CardState.READY);
        ok("miracast unknown code", CastRules.miracast(true, true, true, "WHAT", false) == CardState.ERROR);
        // DLNA card
        ok("dlna renderer answers", CastRules.dlna(true, true, true, true) == CardState.READY);
        ok("dlna without Lumen Play", CastRules.dlna(false, true, true, true) == CardState.ABSENT);
        ok("dlna app disabled", CastRules.dlna(true, false, true, true) == CardState.OFF);
        ok("dlna switched off in Lumen Play", CastRules.dlna(true, true, true, false) == CardState.OFF);
        ok("dlna without a network", CastRules.dlna(true, true, false, true) == CardState.NO_WIFI);
        // the renderer's answer on this unit (kino-player dlna/HttpServer + Descriptions)
        String desc = "HTTP/1.1 200 OK\r\nServer: Android/14 UPnP/1.0 LumenPlay/2.0\r\nConnection: close\r\n\r\n"
                + "<root><device><deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>"
                + "<friendlyName>Lumen Play on Tom &amp; Ann's</friendlyName></device></root>";
        ok("renderer answer", DlnaProbe.renderer(desc));
        ok("renderer name unescaped", "Lumen Play on Tom & Ann's".equals(DlnaProbe.name(desc)));
        ok("no answer", !DlnaProbe.renderer(null) && DlnaProbe.name(null) == null && !DlnaProbe.renderer(""));
        ok("error status is no renderer", !DlnaProbe.renderer(desc.replace("200 OK", "404 Not Found")));
        ok("status 2000 is no renderer", !DlnaProbe.renderer(desc.replace("200 OK", "2000 OK")));
        ok("another device type is no renderer", !DlnaProbe.renderer(desc.replace("MediaRenderer", "MediaServer")));
        ok("not http is no renderer", !DlnaProbe.renderer("SSH-2.0-x 200 urn:schemas-upnp-org:device:MediaRenderer:1"));
        ok("renderer without a name", DlnaProbe.name(desc.replaceAll("<friendlyName>.*</friendlyName>", "")) == null
                && DlnaProbe.name(desc.replaceAll("<friendlyName>.*</friendlyName>", "<friendlyName> </friendlyName>")) == null);
        ok("probe asks the fixed port", DlnaProbe.PORT == 49595 && DlnaProbe.REQUEST.startsWith("GET /description.xml HTTP/1.0\r\n")
                && DlnaProbe.REQUEST.endsWith("\r\n\r\n"));
        // cards and headline
        ok("three cards", CastRules.cards(CardState.READY, CardState.READY)
                .equals(Arrays.asList(Receiver.AIRPLAY, Receiver.MIRACAST, Receiver.DLNA)));
        ok("two cards without a sink", CastRules.cards(CardState.ABSENT, CardState.OFF).equals(Arrays.asList(Receiver.AIRPLAY, Receiver.DLNA)));
        ok("no dlna card without Lumen Play", CastRules.cards(CardState.READY, CardState.ABSENT)
                .equals(Arrays.asList(Receiver.AIRPLAY, Receiver.MIRACAST)));
        ok("off sink keeps its card", CastRules.cards(CardState.OFF, CardState.READY).contains(Receiver.MIRACAST));
        ok("in use airplay first", CastRules.inUse(CardState.CONNECTED, CardState.CONNECTED) == Receiver.AIRPLAY);
        ok("in use miracast", CastRules.inUse(CardState.READY, CardState.CONNECTED) == Receiver.MIRACAST);
        ok("in use none", CastRules.inUse(CardState.READY, CardState.OFF) == null);
        ok("all off", CastRules.allOff(CardState.OFF, CardState.OFF) && CastRules.allOff(CardState.OFF, CardState.ABSENT));
        ok("not all off", !CastRules.allOff(CardState.OFF, CardState.READY) && !CastRules.allOff(CardState.STARTING, CardState.OFF)
                && !CastRules.allOff(CardState.READY, CardState.ABSENT));
        ok("good ready", CastRules.good(CardState.READY) && CastRules.good(CardState.CONNECTED));
        ok("not good", !CastRules.good(CardState.OFF) && !CastRules.good(CardState.ERROR) && !CastRules.good(CardState.NO_WIFI)
                && !CastRules.good(CardState.STARTING) && !CastRules.good(CardState.NOT_VISIBLE));
        ok("mirroring rows follow the card", CastRules.mirroringRows(CardState.OFF) && !CastRules.mirroringRows(CardState.ABSENT));
        // the model table: the generated asset of the tree
        CastModels m = CastModels.parse(new String(Files.readAllBytes(Paths.get(a[0])), StandardCharsets.UTF_8));
        // every model code of a device with an image recipe (devices/<c>/build.conf, test/run.sh reads the registry)
        String[] every = a.length > 1 ? a[1].trim().split("\\s+") : new String[0];
        ok("the shipping models are given (test/run.sh)", every.length > 0 && !every[0].isEmpty());
        ok("every shipping model in the table (" + m.size() + ")", m.size() == every.length);
        for (String code : every) ok(code + " in the table", m.find(code) != CastModels.UNKNOWN && code.equals(m.find(code).code));
        ok("Play 6 (1080p) offers no 4K", !m.find("G0058").mirror4k);
        ok("Z9X offers 4K", m.find("G0082").mirror4k && "horse_popular".equals(m.find("G0082").codename));
        ok("X50 Ultra offers 4K", m.find("G0111").mirror4k);
        ok("HB is 32-bit", "arm32".equals(m.find("G0130").abi));
        ok("unknown model: no 4K", !m.find("G9999").mirror4k && m.find(null) == CastModels.UNKNOWN);
        ok("model code trimmed", m.find(" G0082 ").mirror4k);
        boolean threw = false;
        try {
            CastModels.parse("G0082\thorse_popular\tarm64\n");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        ok("a short line is refused", threw);
        threw = false;
        try {
            CastModels.parse("G0082\thorse_popular\tarm64\tyes\n");
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        ok("a bad 4K flag is refused", threw);
        ok("comments and blanks skipped", CastModels.parse("# x\n\nG1\tc\tarm64\t1\n").size() == 1);
        System.out.println((fails == 0 ? "OK" : "RED") + " cast: " + (runs - fails) + "/" + runs);
        if (fails != 0) System.exit(1);
    }
}
