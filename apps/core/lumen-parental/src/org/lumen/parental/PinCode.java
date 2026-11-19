// SPDX-License-Identifier: Apache-2.0
package org.lumen.parental;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * The stock child lock password: four presses of the remote's arrow keys (方向键密码, keys 19 up, 20 down, 21 left,
 * 22 right). Kept only as a salted SHA-256 of the key letters.
 */
public final class PinCode {
    public static final int LENGTH = 4;
    public static final char UP = 'U', DOWN = 'D', LEFT = 'L', RIGHT = 'R';

    private PinCode() {}

    /** The letter of an arrow key code, 0 for any other key. */
    public static char letter(int keyCode) {
        switch (keyCode) {
            case 19: return UP;
            case 20: return DOWN;
            case 21: return LEFT;
            case 22: return RIGHT;
            default: return 0;
        }
    }

    public static boolean complete(String keys) {
        if (keys == null || keys.length() != LENGTH) return false;
        for (char c : keys.toCharArray()) if (c != UP && c != DOWN && c != LEFT && c != RIGHT) return false;
        return true;
    }

    public static String newSalt() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return hex(b);
    }

    public static String hash(String salt, String keys) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            return hex(d.digest((salt + ":" + keys).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean matches(String salt, String hash, String keys) {
        return complete(keys) && salt != null && hash != null && MessageDigest.isEqual(
                hash.getBytes(StandardCharsets.US_ASCII), hash(salt, keys).getBytes(StandardCharsets.US_ASCII));
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }
}
