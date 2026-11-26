// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

/** How a copy, move, delete or unpack ended. */
public enum Outcome { DONE, CANCELLED, NO_SPACE, INTO_ITSELF, READ_ONLY, UNSAFE, FAILED }
