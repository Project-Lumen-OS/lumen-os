// SPDX-License-Identifier: Apache-2.0
package org.lumen.updater;

/** Which manifest an image reads and how its beta channel sits beside it. */
public enum ChannelRule {
    /**
     * A fixed address: the image's &lt;props&gt;.ota.manifest_url when it is https, else the model's default. A beta
     * channel exists only beside the one stable file the model names (UpdateTarget.beta), at a fixed URL; a URL
     * whose file is no manifest name reads as the model's fallback file. The Z9X and the Play 6.
     */
    FIXED {
        @Override
        String image(UpdateTarget t) {
            String u = Ota.prop(t.props + ".ota.manifest_url");
            return u.startsWith("https://") ? u : t.defaultManifest;
        }

        @Override
        String[] pair(UpdateTarget t, String image) {
            return t.betaUrl != null && t.stableFile.equals(Ota.channelFile(image)) ? new String[] {image, t.betaUrl} : null;
        }

        @Override
        boolean betaByDefault(String[] c, String image) { return c[1].equals(image); }

        @Override
        String file(UpdateTarget t, String f) { return f.startsWith("update-") && f.endsWith(".json") ? f : t.fallbackFile; }
    },
    /**
     * The image's own channel pair: ro.lumen.ota.manifest_url (https, and only with a wire id), and beside it
     * update-&lt;device&gt;-nogms.json (stable) and update-&lt;device&gt;-test.json (beta) when the image reads one of
     * the two; any other manifest file is read alone. No default: without a wire id or a URL nothing is checked.
     * The HB and the X50 family.
     */
    PAIR {
        @Override
        String image(UpdateTarget t) {
            String u = Ota.prop(t.props + ".ota.manifest_url").trim();
            return !t.device().isEmpty() && u.startsWith("https://") ? u : "";
        }

        @Override
        String[] pair(UpdateTarget t, String image) {
            String d = t.device(), f = Ota.channelFile(image);
            if (d.isEmpty() || f.isEmpty()) return null;
            String stable = "update-" + d + "-nogms.json", beta = "update-" + d + "-test.json";
            if (!f.equals(stable) && !f.equals(beta)) return null;
            int q = image.indexOf('?');
            String dir = (q >= 0 ? image.substring(0, q) : image);
            dir = dir.substring(0, dir.lastIndexOf('/') + 1);
            return new String[] {dir + stable, dir + beta};
        }

        @Override
        boolean betaByDefault(String[] c, String image) { return Ota.channelFile(c[1]).equals(Ota.channelFile(image)); }

        @Override
        String file(UpdateTarget t, String f) {
            return f.startsWith("update-") && f.endsWith(".json") && f.length() > 12 ? f : "";
        }
    };

    /** The manifest URL the image was built to read ("" = none). */
    abstract String image(UpdateTarget t);

    /** {stable, beta} URLs of the channel pair of an image URL, or null when it has no beta channel. */
    abstract String[] pair(UpdateTarget t, String image);

    /** Whether beta is on when the user never chose: the image itself reads the beta channel. */
    abstract boolean betaByDefault(String[] c, String image);

    /** The manifest file name of a URL's last path element {@code f}: f itself, or the rule's answer for others. */
    abstract String file(UpdateTarget t, String f);
}
