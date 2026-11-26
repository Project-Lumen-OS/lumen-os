// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/** DIDL-Lite (the list a DLNA media server returns for one folder): folders and items with their http resource. */
public final class Didl {
    private Didl() {}

    public static List<Entry> parse(String didl) throws Exception {
        List<Entry> out = new ArrayList<>();
        if (didl == null || didl.trim().isEmpty()) return out;
        for (Element el : Xml.children(Xml.parse(didl).getDocumentElement())) {
            String tag = Xml.local(el);
            boolean folder = tag.equals("container");
            if (!folder && !tag.equals("item")) continue;
            String id = el.getAttribute("id"), title = Xml.text(el, "title"), cls = Xml.text(el, "class");
            if (id.isEmpty()) continue;
            String url = "", mime = "";
            long size = -1;
            for (Element r : Xml.children(el)) {
                if (!Xml.local(r).equals("res")) continue;
                String info = r.getAttribute("protocolInfo");
                if (!url.isEmpty() && !info.startsWith("http-get")) continue;
                url = r.getTextContent().trim();
                String[] p = info.split(":");
                mime = p.length > 2 ? p[2] : "";
                try {
                    size = Long.parseLong(r.getAttribute("size").trim());
                } catch (NumberFormatException e) {
                    size = -1;
                }
                if (info.startsWith("http-get")) break;
            }
            if (!folder && url.isEmpty()) continue;
            Kind k = folder ? Kind.FOLDER : cls.contains(".videoItem") ? Kind.VIDEO : cls.contains(".audioItem") ? Kind.MUSIC
                    : cls.contains(".imageItem") ? Kind.PHOTO : Kinds.ofMime(mime);
            if (k == Kind.OTHER) k = Kinds.of(title);
            String name = title.isEmpty() ? id : title;
            if (!folder && Kinds.ext(name).isEmpty() && Kinds.extOf(mime) != null) name = name + "." + Kinds.extOf(mime);
            Entry e = new Entry(name, id, k, folder ? 0 : Math.max(0, size), 0);
            e.url = url;
            e.mime = mime;
            out.add(e);
        }
        return out;
    }
}
