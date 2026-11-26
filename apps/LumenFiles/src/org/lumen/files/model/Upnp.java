// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** DLNA media servers: the SSDP search, the device description, ContentDirectory Browse request and answer. */
public final class Upnp {
    public static final String ST = "urn:schemas-upnp-org:device:MediaServer:1";
    public static final String GROUP = "239.255.255.250";
    public static final int PORT = 1900;
    public static final String BROWSE = "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\"";
    public static final String ROOT = "0";

    private Upnp() {}

    public static String search() {
        return "M-SEARCH * HTTP/1.1\r\nHOST: " + GROUP + ":" + PORT + "\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: " + ST
                + "\r\n\r\n";
    }

    /** The description URL of an answer that names a media server, null for anything else. */
    public static String location(String answer) {
        if (answer == null) return null;
        String[] lines = answer.split("\r?\n");
        if (lines.length == 0 || !(lines[0].startsWith("HTTP/1.1 200") || lines[0].startsWith("NOTIFY"))) return null;
        String loc = null, st = "";
        for (String l : lines) {
            int c = l.indexOf(':');
            if (c <= 0) continue;
            String k = l.substring(0, c).trim().toUpperCase(Locale.ROOT), v = l.substring(c + 1).trim();
            if (k.equals("LOCATION")) loc = v;
            else if (k.equals("ST") || k.equals("NT")) st = v;
        }
        if (loc == null || !(st.contains("MediaServer") || st.contains("ContentDirectory"))) return null;
        return loc.startsWith("http://") || loc.startsWith("https://") ? loc : null;
    }

    /** A media server: its name, id and the ContentDirectory control address. */
    public static final class Device {
        public String name = "", udn = "", control = "";
    }

    /** The device of a description, null when it has no ContentDirectory. */
    public static Device describe(String xml, String location) throws Exception {
        Element root = Xml.parse(xml).getDocumentElement();
        Element dev = Xml.first(root, "device");
        if (dev == null) return null;
        Device d = new Device();
        d.name = Xml.text(dev, "friendlyName");
        d.udn = Xml.text(dev, "UDN");
        String base = Xml.text(root, "URLBase");
        if (base.isEmpty()) base = location;
        for (Element s : Xml.all(dev, "service")) {
            if (!Xml.text(s, "serviceType").contains(":ContentDirectory:")) continue;
            d.control = new URL(new URL(base), Xml.text(s, "controlURL")).toString();
            break;
        }
        if (d.udn.isEmpty()) d.udn = location;
        if (d.name.isEmpty()) d.name = new URL(location).getHost();
        return d.control.isEmpty() ? null : d;
    }

    public static String browse(String id, int start, int count) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" "
                + "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
                + "<u:Browse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\"><ObjectID>" + esc(id)
                + "</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag><Filter>*</Filter><StartingIndex>" + start
                + "</StartingIndex><RequestedCount>" + count + "</RequestedCount><SortCriteria></SortCriteria>"
                + "</u:Browse></s:Body></s:Envelope>";
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** One Browse answer: its rows, how many came and how many the folder has. */
    public static final class Page {
        public final List<Entry> entries = new ArrayList<>();
        public int returned, total;
    }

    public static Page result(String soap) throws Exception {
        Document d = Xml.parse(soap);
        Element root = d.getDocumentElement();
        Page p = new Page();
        p.entries.addAll(Didl.parse(Xml.text(root, "Result")));
        p.returned = num(Xml.text(root, "NumberReturned"), p.entries.size());
        p.total = num(Xml.text(root, "TotalMatches"), p.returned);
        return p;
    }

    static int num(String s, int dflt) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return dflt;
        }
    }
}
