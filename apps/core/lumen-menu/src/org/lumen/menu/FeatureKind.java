// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

/** What the menu builds for a feature ({@link FeatureId#kind}). */
public enum FeatureKind {
    /** a tile that acts by itself (opens a system page, the source popup) */
    TILE,
    /** a switch row, value "on" / "off" */
    TOGGLE,
    /** a radio list or choice row, value = one option token of the profile */
    CHOICE,
    /** a slider (or a group of sliders), value = a number inside min..max */
    SLIDER,
    /** a row that runs something once */
    ACTION,
    /** a row that opens a device screen in place of the menu */
    SCREEN,
    /** a row that only shows a value the device reads (text) */
    INFO
}
