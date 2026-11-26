// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.fwcopy;

/** What the firmware copy does with one partition of a model's stock update list. */
public enum Verdict {
    /** part of the XGIMI set: copied unchanged from the running half when the other half differs */
    COPY,
    /** part of the XGIMI set, never written on this model: must already be the same in both halves */
    KEEP_EQUAL,
    /** never copied by this module (the AVB root, logical partitions in super, single copies, factory data) */
    LEFT_OUT,
    /** the model has it in both halves, this projector does not: the copy refuses */
    MISSING
}
