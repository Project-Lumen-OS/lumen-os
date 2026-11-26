// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.miracast.core;

/** What the receiver is doing, for the status line. FAULT: it cannot receive, the status line names the cause. */
public enum ReceiverStatus { OFF, STARTING, READY, IN_USE, NO_WIFI, UNSUPPORTED, FAULT }
