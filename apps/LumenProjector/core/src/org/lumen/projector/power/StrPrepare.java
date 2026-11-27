// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.power;

/**
 * The vendor half of the stock STR sleep, one rule for every family whose stock framework has it (plain Java, HAL
 * thread, host-tested by test/power StrPrepareTest and the X50 family's X50SleepTest). The stock PowerManagerService
 * runs a prepare step inside goToSleep(100), once per sleep: the vendor goToSleep(0, 0), then setScreenOnOff(false) when
 * getScreenOnOff reads true (the stock evidence per family: the family adapters' Family.strVendor and
 * devices/goat_elegant/research/unbanned_hbprojector.tsv). In the vendor service type 0 sets the standby stage (1 for
 * mode 0), runs its shutdown step, music, TV, eye protection and audio, and on the AIDL services releases
 * gmpf_wake_lock, which every vendor wakeUp takes (the power HAL sends one on each STR resume). No other argument pair
 * releases it: type 1 is the power HAL's own step at the suspend entry, type 2 the light standby. Without type 0 the
 * kernel never suspends again after the first STR wake (GitHub issue 5). The family adapter supplies {@link Vendor};
 * only these stock values are ever sent.
 */
public final class StrPrepare {
    private StrPrepare() {}

    /** goToSleep's mode in the stock prepare: 0 = sleep (STR); stock sends 1 only before a reboot. */
    public static final int MODE_STR = 0;
    /** goToSleep's type in the stock prepare: 0 = the framework's prepare (the power HAL sends 1, the light standby 2). */
    public static final int TYPE_PREPARE = 0;

    /** The three vendor calls of the stock prepare, in the family's own codes. */
    public interface Vendor {
        /** goToSleep(MODE_STR, TYPE_PREPARE). */
        void vendorSleep() throws Exception;

        /** getScreenOnOff(). */
        boolean screenOn() throws Exception;

        /** setScreenOnOff(on). */
        void setScreen(boolean on) throws Exception;
    }

    /** As the stock prepare: goToSleep(0, 0), then the light off when it still reads on. */
    public static void prepare(Vendor v) throws Exception {
        v.vendorSleep();
        if (v.screenOn()) v.setScreen(false);
    }

    /**
     * Once per Android sleep, as stock's isSuspending: the prepare goes out when the kernel is let suspend (no STR
     * blocker) and our own lamp-off is no longer running, never twice before the next wake. Thread-safe.
     */
    public static final class Once {
        private boolean mSent;

        /** True when the prepare must be sent now (and is then counted as sent until {@link #woke}). */
        public synchronized boolean take(boolean asleep, boolean strAllowed, boolean lampOffPending) {
            if (mSent || !asleep || !strAllowed || lampOffPending) return false;
            mSent = true;
            return true;
        }

        /**
         * Before a Lumen sleep (SleepSource.beforeSleep, stock order: inside goToSleep, before the screen goes off):
         * true when the prepare must be sent now. Same once-per-sleep count as {@link #take}.
         */
        public synchronized boolean takeBefore(boolean strAllowed, boolean lampOffPending) {
            if (mSent || !strAllowed || lampOffPending) return false;
            mSent = true;
            return true;
        }

        /** The prepare could not be delivered (HAL not connected, binder error): the next chance sends it again. */
        public synchronized void failed() {
            mSent = false;
        }

        /** Android woke: the next sleep sends its own prepare. */
        public synchronized void woke() {
            mSent = false;
        }

        public synchronized boolean sent() {
            return mSent;
        }
    }
}
