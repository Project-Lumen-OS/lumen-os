// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.panel;

/**
 * The HAL steps of the screen rotation card (RotatePanel), one implementation per HAL family that has the card
 * (Family.rotateIo, one class in each family adapter), called on that family's HAL
 * thread. open brackets the card's reads and steps, close undoes it; rotation reads {min, max, cur, start} in
 * degrees, rotate turns by deg.
 */
public interface RotateIo {
    /** The card opens (the bracket of the family's adjustment calls): false = not delivered. */
    boolean open(String who) throws Exception;

    /** The card closes ({@code moved}: a step was sent); false = incomplete, left to the connect hook. */
    boolean close(String who, boolean moved);

    float[] rotation() throws Exception;

    boolean rotate(boolean clockwise, float deg) throws Exception;

    /** A read gave a plausible reply: the model has the card. */
    void supported();

    /** The HAL refused the call (an implausible or an absent reply): the card marks the model unsupported. */
    boolean refused(Throwable t);
}
