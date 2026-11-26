// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** Small DOM helpers for the UPnP answers: by local name (servers differ in prefixes), no DTD, no entities. */
public final class Xml {
    private Xml() {}

    public static Document parse(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setExpandEntityReferences(false);
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
        }
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml.trim().getBytes(StandardCharsets.UTF_8)));
    }

    public static String local(Node n) {
        String l = n.getLocalName();
        if (l != null) return l;
        String s = n.getNodeName();
        int c = s.indexOf(':');
        return c < 0 ? s : s.substring(c + 1);
    }

    /** Child elements of {@code e} (direct only). */
    public static List<Element> children(Element e) {
        List<Element> out = new ArrayList<>();
        NodeList l = e.getChildNodes();
        for (int i = 0; i < l.getLength(); i++) if (l.item(i) instanceof Element) out.add((Element) l.item(i));
        return out;
    }

    /** The first element named {@code name} below {@code e} (depth first), null without one. */
    public static Element first(Element e, String name) {
        for (Element c : children(e)) {
            if (local(c).equals(name)) return c;
            Element d = first(c, name);
            if (d != null) return d;
        }
        return null;
    }

    /** Every element named {@code name} below {@code e}. */
    public static List<Element> all(Element e, String name) {
        List<Element> out = new ArrayList<>();
        for (Element c : children(e)) {
            if (local(c).equals(name)) out.add(c);
            out.addAll(all(c, name));
        }
        return out;
    }

    /** The trimmed text of the first element named {@code name} below {@code e}, "" without one. */
    public static String text(Element e, String name) {
        Element c = e == null ? null : first(e, name);
        return c == null ? "" : c.getTextContent().trim();
    }
}
