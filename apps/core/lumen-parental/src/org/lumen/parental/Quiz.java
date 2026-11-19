// SPDX-License-Identifier: Apache-2.0
package org.lumen.parental;

import java.util.Random;

/**
 * The stock forgotten password way (找回密码, 答题解锁): a sum an adult answers at once, four answers to pick from, a new
 * question on every try. A right answer lets the parent set a new password.
 */
public final class Quiz {
    public final int a, b, answer;
    public final int[] choices;

    private Quiz(int a, int b, int[] choices) {
        this.a = a;
        this.b = b;
        this.answer = a * b;
        this.choices = choices;
    }

    public static Quiz next(Random r) {
        int a = 12 + r.nextInt(8), b = 3 + r.nextInt(7);
        int right = a * b;
        int[] c = {right, right + b, right - a, right + 10};
        for (int i = c.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1), t = c[i];
            c[i] = c[j];
            c[j] = t;
        }
        return new Quiz(a, b, c);
    }

    public boolean right(int pick) { return pick == answer; }
}
