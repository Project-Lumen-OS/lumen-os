// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.panel;

/**
 * The menu's own wire-to-token tables for the rows the signal panel shares with the menu (picture mode, motion
 * smoothing): {@link GmpfSignalReader} reads the same getters as the menu and names the values with the menu's tokens,
 * so the panel shows what the menu shows. One table per model family: Z9X {@code Z9xSignalTokens}, HB
 * {@code HbSignalTokens}.
 */
public interface SignalMenuTokens {
    /** The menu.json "picture.mode" token of a picture mode wire value, null when the menu has none for it. */
    String pictureMode(int wire);

    /** The "picture.memc" token of a MEMC level, null when the menu has none for it. */
    String memc(int wire);
}
