// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu.quick;

/** What an item of the quick menu is, the key of the one presentation table ({@link Presentations}). */
public enum ItemKind {
    /** a number or a stepped choice that can slide inline (light level, iris, volume, picture sliders) */
    SLIDER,
    /** a section of the menu: its panel opens on the right */
    SECTION,
    /** a choice (eye protection, picture mode, sound mode, output, aspect) */
    CHOICE,
    /** a switch */
    TOGGLE,
    /** something that runs or opens at once (auto keystone, input, Bluetooth, network, mirroring, autofocus) */
    ACTION,
    /** the theme button (Светлая, Тёмная, Авто) */
    THEME,
    /** All settings and the pencil: always in the menu, never removed (else the user locks himself out) */
    PINNED
}
