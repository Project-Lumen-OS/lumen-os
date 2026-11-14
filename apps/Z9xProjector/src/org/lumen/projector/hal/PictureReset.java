// SPDX-License-Identifier: Apache-2.0
package org.lumen.projector.hal;

/** ResetPictureToDefault triples used by stock (FS 3.4/3.5); no other combination is sent.
 *
 * <p>The Z9X values of Gmpf2Client (each device app has its own value list of this name).
 */
public enum PictureReset {
    BASIC(0, 0, 1), PROFESSIONAL(0, 0, 2), ALL(0, -8, 0);
    final int a, b, c;
    PictureReset(int a, int b, int c) { this.a = a; this.b = b; this.c = c; }
}
