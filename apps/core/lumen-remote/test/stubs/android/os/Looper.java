// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package android.os;
public final class Looper { static final Looper L = new Looper(); public static Looper getMainLooper() { return L; }
    public static Looper myLooper() { return L; } }
