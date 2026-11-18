// SPDX-License-Identifier: Apache-2.0
package org.lumen.control.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** A stable id that does not reveal what it was made from: upper-case hex of SHA-256(salt + value), cut short. */
public final class HashedId {
    private HashedId() {}

    /** "" for an empty value. */
    public static String of(String salt, String value, int bytes) {
        if (value == null || value.isEmpty()) return "";
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest((salt + value).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < bytes && i < h.length; i++) b.append(String.format("%02X", h[i] & 0xFF));
            return b.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }
}
