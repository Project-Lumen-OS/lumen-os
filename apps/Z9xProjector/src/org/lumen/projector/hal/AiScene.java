// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** AI picture scene sub-modes (stock XRMService defaultvideoinfo, FS 3.3). Only while in AI mode. *
 * <p>Values of GmpfClient (the Z9X HIDL client and the HB AIDL client send the same wire values; one file for both apps).
 */
public enum AiScene {
    TV(10), VARIETY(11), CHILDREN(12), CONCERT(13), DOCUMENTARY(14), ANIME(15), FILM(16), FOOTBALL(17);
    public final int wire;
    AiScene(int v) { wire = v; }
}
