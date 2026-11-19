// SPDX-License-Identifier: Apache-2.0
package org.lumen.parental;

/** Stock 管控范围: the controls act only in child mode, or everywhere (全局). */
public enum ControlRange {
    CHILD_ONLY("child"),
    GLOBAL("global");

    public final String token;

    ControlRange(String token) { this.token = token; }

    public static ControlRange of(String token) {
        for (ControlRange r : values()) if (r.token.equals(token)) return r;
        return CHILD_ONLY;
    }
}
