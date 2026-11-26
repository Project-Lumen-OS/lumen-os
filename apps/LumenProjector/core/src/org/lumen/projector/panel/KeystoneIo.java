// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

/**
 * The HAL steps of the 4-point corner card (Play6KeystonePanel), one implementation per HAL family that has the card
 * (play6-hidl panel/Play6PanelIo.keystone), called on that family's HAL thread: the auto keystone pause (586, 573),
 * the adjustment bracket (IGmpf2 125), the game mode reads of its lock (666, IGmpf2 33, 647), the keystone mode (130)
 * and the corners (186 read, 150 check, 185 apply).
 */
interface KeystoneIo {
    boolean getMoveAk() throws Exception;

    boolean setRealtimeAkTemporarily(boolean on) throws Exception;

    boolean setAdjustScreenOpen(boolean open) throws Exception;

    int getPictureModeRaw() throws Exception;

    int[] getGameModeProp() throws Exception;

    int getGameModeOptionRaw() throws Exception;

    int getKeystoneMode() throws Exception;

    Corners getKeystonePoints() throws Exception;

    boolean checkKeystonePoints(Corners p) throws Exception;

    int applyKeystonePoints(Corners p) throws Exception;

    /** An empty set of corners of this family's kind (what the apply calls take). */
    Corners newCorners();

    /** The HAL delivered the call but its reply is unusable: the card says so and goes on or refuses. */
    boolean refused(Throwable t);
}
