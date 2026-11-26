// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.keyboard;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * A shipped page (res/xml) laid out as android.inputmethodservice.Keyboard does it at 320 dpi (density 2, pixel
 * offsets cut like getDimensionPixelOffset), and the D-pad as LeanbackKeyboardContainer steps on it: KeyGrid.aim,
 * then KeyGrid.nearest (test only).
 */
final class PageModel {
    static final float DENSITY = 2f;
    /** A step result off the keys: right of the right edge (the action button) and above the top row. */
    static final int ACTION = -2, NORTH = -3;

    final String name;
    final List<int[]> rows = new ArrayList<>();
    int[] left, top, width, height, popupCount;
    boolean[] leftEdge, rightEdge;
    String[] label;
    int totalWidth, totalHeight;

    PageModel(File xmlDir, String name, Map<String, Float> dimens) throws Exception {
        this.name = name;
        Element kb = parse(new File(xmlDir, name));
        int keyW = px(dimens, kb.getAttribute("android:keyWidth"));
        int keyH = px(dimens, kb.getAttribute("android:keyHeight"));
        int gapH = px(dimens, kb.getAttribute("android:horizontalGap"));
        int gapV = px(dimens, kb.getAttribute("android:verticalGap"));
        List<int[]> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>(), popups = new ArrayList<>(), edges = new ArrayList<>();
        NodeList rowNodes = kb.getElementsByTagName("Row");
        int y = 0;
        for (int r = 0; r < rowNodes.getLength(); r++) {
            Element row = (Element) rowNodes.item(r);
            int rowGap = row.hasAttribute("android:horizontalGap") ? px(dimens, row.getAttribute("android:horizontalGap")) : gapH;
            NodeList keyNodes = row.getElementsByTagName("Key");
            int x = 0, first = keys.size();
            for (int k = 0; k < keyNodes.getLength(); k++) {
                Element key = (Element) keyNodes.item(k);
                int w = key.hasAttribute("android:keyWidth") ? px(dimens, key.getAttribute("android:keyWidth")) : keyW;
                x += rowGap;
                keys.add(new int[]{x, y, w, keyH});
                x += w;
                labels.add(key.getAttribute("android:keyLabel"));
                popups.add(key.getAttribute("android:popupKeyboard"));
                edges.add(key.getAttribute("android:keyEdgeFlags"));
            }
            totalWidth = Math.max(totalWidth, x);
            rows.add(new int[]{first, keys.size()});
            y += keyH + gapV;
        }
        totalHeight = y;
        int n = keys.size();
        left = new int[n];
        top = new int[n];
        width = new int[n];
        height = new int[n];
        popupCount = new int[n];
        leftEdge = new boolean[n];
        rightEdge = new boolean[n];
        label = labels.toArray(new String[0]);
        for (int i = 0; i < n; i++) {
            int[] k = keys.get(i);
            left[i] = k[0];
            top[i] = k[1];
            width[i] = k[2];
            height[i] = k[3];
            leftEdge[i] = edges.get(i).contains("left");
            rightEdge[i] = edges.get(i).contains("right");
            String p = popups.get(i);
            if (p.startsWith("@xml/")) {
                popupCount[i] = parse(new File(xmlDir, p.substring(5) + ".xml")).getElementsByTagName("Key").getLength();
            }
        }
    }

    int rowOf(int i) {
        for (int r = 0; r < rows.size(); r++) if (i >= rows.get(r)[0] && i < rows.get(r)[1]) return r;
        return -1;
    }

    /** Where the start focus lands (resetFocusCursor: 0.45 of the width, 0.375 of the height). */
    int start() {
        return KeyGrid.nearest(left, top, width, height, 0.45f * totalWidth, 0.375f * totalHeight);
    }

    /** The key one D-pad step from key {@code i} reaches, ACTION or NORTH; x from the key's middle. */
    int step(int i, int dx, int dy) {
        if (dx < 0 && leftEdge[i]) dx = 0;
        if (dx > 0 && rightEdge[i]) return ACTION;
        int centerX = (left[i] + left[i] + width[i]) >> 1, centerY = (top[i] + top[i] + height[i]) >> 1;
        float[] p = KeyGrid.aim(left[i], left[i] + width[i], centerY, height[i], centerX, dx, dy);
        if (p[1] < 0) return NORTH;
        return KeyGrid.nearest(left, top, width, height, p[0], p[1]);
    }

    /** Keys the D-pad reaches from the start focus. */
    boolean[] reachable() {
        boolean[] seen = new boolean[left.length];
        ArrayDeque<Integer> todo = new ArrayDeque<>();
        int s = start();
        seen[s] = true;
        todo.add(s);
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!todo.isEmpty()) {
            int i = todo.poll();
            for (int[] d : dirs) {
                int j = step(i, d[0], d[1]);
                if (j >= 0 && !seen[j]) {
                    seen[j] = true;
                    todo.add(j);
                }
            }
        }
        return seen;
    }

    /** The bottom row as label@left+width: the same on every page, so a page change keeps the focused key. */
    String bottom() {
        StringBuilder b = new StringBuilder();
        int[] r = rows.get(rows.size() - 1);
        for (int i = r[0]; i < r[1]; i++) b.append(label[i]).append('@').append(left[i]).append('+').append(width[i]).append(' ');
        return b.toString();
    }

    int indexOf(String l) {
        return Arrays.asList(label).indexOf(l);
    }

    static Map<String, Float> dimens(File f) throws Exception {
        Map<String, Float> m = new HashMap<>();
        String s = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        Matcher mt = Pattern.compile("<dimen name=\"(\\w+)\">([0-9.]+)dp</dimen>").matcher(s);
        while (mt.find()) m.put(mt.group(1), Float.parseFloat(mt.group(2)));
        return m;
    }

    private static int px(Map<String, Float> dimens, String v) {
        Float dp = v.startsWith("@dimen/") ? dimens.get(v.substring(7)) : null;
        if (dp == null) throw new IllegalArgumentException("unknown size " + v);
        return (int) (dp * DENSITY);
    }

    private static Element parse(File f) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).getDocumentElement();
    }
}
