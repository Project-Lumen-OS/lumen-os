// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.Set;
import java.util.function.Consumer;

/**
 * The device side of the shared projector menu. One small class per
 * device implements it with that device's own HAL calls (Z9X HIDL IGmpf/IGmpf2, HB and X50 AIDL gmpf through
 * lumen-hal); the menu never calls a HAL itself and never asks which projector it runs on.
 *
 * <p>Features cross this interface as {@link FeatureId} (a picture slider is its own id, e.g.
 * {@link FeatureId#PQ_CONTRAST}); values as text: a toggle is {@code "on"} / {@code "off"}, a choice is one option token of
 * the profile (devices/&lt;c&gt;/menu.json, {@code "options"}), a slider is a decimal number. The adapter maps tokens to
 * its own wire values. Threads: {@link #ready()}, {@link #open} and the rule hooks run on the main thread;
 * {@link #read}, {@link #set} and {@link #run} return at once and call back on the main thread when the device
 * answered.
 *
 * <p>v3: the stock rules of a device reach the shared menu through the default hooks
 * {@link #blocked}, {@link #blockedPage}, {@link #confirm}, {@link #setConfirmed}, {@link #available}, {@link #setupDone} and
 * {@link #footer}, and through the runtime parts of {@link MenuState} (hidden rows, option subsets, labels, tile
 * values, notes). Every hook has a neutral default, so an adapter only implements the rules its stock has.
 */
public interface ProjectorFeatures {

    /**
     * Every feature ({@link FeatureId}) this adapter reads, writes, runs or opens. It must contain every feature of
     * the device's profile; the menu shows nothing the adapter does not implement (MenuProfile.check).
     */
    Set<FeatureId> implemented();

    /** True when the device link answers right now (cheap, main thread). False: the menu shows "not ready". */
    boolean ready();

    /**
     * Reads the current value of every feature of the profile in one go. A value the device could not read is left
     * out of the state (the row then shows no value), never invented.
     */
    void read(Consumer<MenuState> cb);

    /**
     * Writes one feature and reads it back. {@code value} is "on"/"off" (TOGGLE), an option token (CHOICE) or a
     * number (SLIDER). The result carries the read-back state; a refusal names the reason (a string resource name of
     * the app or of lumen-menu) so the menu can show it.
     */
    void set(FeatureId feature, String value, Consumer<Result> cb);

    /** Runs a one-shot action (kind ACTION: Auto keystone now, Autofocus now, picture reset, ...). */
    void run(FeatureId action, Consumer<Result> cb);

    /**
     * Opens a device screen (kind SCREEN: manual keystone corners, manual focus, optical zoom, picture expert page,
     * ...) that replaces the menu. {@code back} brings the menu back on BACK. False when it cannot open now.
     */
    boolean open(FeatureId screen, Runnable back);

    // ================================================================== v3 hooks (all optional)

    /**
     * The stock refusal of a press BEFORE anything moves (main thread, from the last read {@code now}, which may be
     * null): a slider step that stock does not allow in this state (3D, a running game), a row whose press stock
     * answers with a note. {@code value} is the wanted value, or null for a slider step or a screen. Null = allowed.
     */
    default Note blocked(FeatureId feature, String value, MenuState now) { return null; }

    /**
     * The stock refusal of a whole section page ({@link MenuSpec} section id: the game page off HDMI), or null. By
     * default the refusal of the feature of the same name (the eye and lamp pages), else allowed.
     */
    default Note blockedPage(String section, MenuState now) {
        FeatureId f = FeatureId.known(section);
        return f == null ? null : blocked(f, null, now);
    }

    /**
     * The stock question before {@code value} is written or the action runs (main thread), or null. When the user
     * says yes the menu calls {@link #setConfirmed} (or {@link #runConfirmed}); no leaves the row on its read-back.
     * A question that needs the device first (e.g. "is the keystone the full frame?") is asked by returning
     * {@link Result#ask} from {@link #set} instead.
     */
    default Confirm confirm(FeatureId feature, String value, MenuState now) { return null; }

    /** {@link #set} after the user answered yes to {@link #confirm} or to a {@link Result#ask}. */
    default void setConfirmed(FeatureId feature, String value, Consumer<Result> cb) { set(feature, value, cb); }

    /** {@link #run} after the user answered yes to {@link #confirm}. */
    default void runConfirmed(FeatureId action, Consumer<Result> cb) { run(action, cb); }

    /**
     * Whether a feature of the profile is usable on this unit (main thread, cheap), asked when the menu is built: the
     * home screen choice only when it can switch, the fit page only where a fit device is supported. False: no row,
     * no tile (never a disabled one).
     */
    default boolean available(FeatureId feature) { return true; }

    /**
     * False while the first-run setup runs: the menu then refuses everything that leaves it for another screen
     * (Android pages, the input list, recent apps, the home screen choice) with the "setup running" note.
     */
    default boolean setupDone() { return true; }

    /** A text line at the end of a section page (a string resource name), or null. */
    default String footer(String section) { return null; }

    // ================================================================== value types

    /** A note the menu shows (Notify card): a string resource name, its format arguments, an optional title. */
    final class Note {
        /** Title of the card: null = no title, {@link #ROW_TITLE} = the pressed row's title, "@name" = a resource. */
        public final String title;
        /** String resource name of the text, or null when {@link #text} is set. */
        public final String res;
        /** Ready text (the adapter formatted it with its own context), or null. */
        public final CharSequence text;
        /** Format arguments of {@link #res}; a String starting with "@" is the name of a string resource. */
        public final Object[] args;
        /** A short toast instead of the card (the stock "not now" toasts). */
        public final boolean toast;

        /** {@link #title} value: use the pressed row's title. */
        public static final String ROW_TITLE = "\u0000row";

        private Note(String title, String res, CharSequence text, Object[] args, boolean toast) {
            this.title = title;
            this.res = res;
            this.text = text;
            this.args = args == null ? new Object[0] : args;
            this.toast = toast;
        }

        /** A card with the text of string resource {@code res} (formatted with {@code args}), no title. */
        public static Note of(String res, Object... args) { return new Note(null, res, null, args, false); }

        /** A card titled with the pressed row's title. */
        public static Note titled(String res, Object... args) { return new Note(ROW_TITLE, res, null, args, false); }

        /** A card with the title of string resource {@code titleRes}. */
        public static Note titledBy(String titleRes, String res, Object... args) {
            return new Note("@" + titleRes, res, null, args, false);
        }

        /** A card with a text the adapter made itself; {@code rowTitle} = titled with the pressed row's title. */
        public static Note text(CharSequence text, boolean rowTitle) {
            return new Note(rowTitle ? ROW_TITLE : null, null, text, null, false);
        }

        /** A card with a ready title and text. */
        public static Note text(CharSequence title, CharSequence text) {
            return new Note(title == null ? null : "=" + title, null, text, null, false);
        }

        /** A toast with the text of string resource {@code res}. */
        public static Note toast(String res) { return new Note(null, res, null, null, true); }

        @Override public String toString() { return (res != null ? res : String.valueOf(text)) + (toast ? " (toast)" : ""); }
    }

    /** A stock question: title, message, the yes button (string resource names; the no button is Cancel). */
    final class Confirm {
        public final String title, message, ok;

        public Confirm(String title, String message, String ok) {
            this.title = title;
            this.message = message;
            this.ok = ok;
        }

        @Override public String toString() { return title + "/" + message + "/" + ok; }
    }

    // ================================================================== live values (optional, v3.2)

    /**
     * Optional, for an adapter whose values change by themselves while a page is open (v3.2
     * and 11): the menu says which section page is on screen, and the adapter may send fresh values of that page's rows to
     * {@code live} (a partial state: only the features it names change) until the next call. The X50 uses it for the
     * level angles (INFO rows): the stock angle dialog's calls run only while the keystone page is open, and the
     * angles are read again every 500 ms there. Main thread; {@code live} is null when {@code section} is null.
     */
    interface Live {
        /** {@code section} (a MenuSpec section) is on screen now; null: the card or no menu at all. */
        void onScreen(String section, Consumer<MenuState> live);
    }

    /** Outcome of {@link #set} and {@link #run}. */
    final class Result {
        public final ResultKind kind;
        /** For REFUSED: the string resource name of the note to show (null: no note). */
        public final String reason;
        /** The state read back after the write (null when nothing could be read). */
        public final MenuState after;
        /** A note to show (a refusal with arguments or a title, or what a DONE change did); null = none. */
        public final Note note;
        /** For ASK: the question; yes calls {@link #setConfirmed}. */
        public final Confirm confirm;

        public Result(ResultKind kind, String reason, MenuState after) { this(kind, reason, after, null, null); }

        private Result(ResultKind kind, String reason, MenuState after, Note note, Confirm confirm) {
            this.kind = kind;
            this.reason = reason;
            this.after = after;
            this.note = note;
            this.confirm = confirm;
        }

        public static Result done(MenuState after) { return new Result(ResultKind.DONE, null, after); }
        /** Done, and the menu shows {@code note} (what the change did, as stock tells it). */
        public static Result done(MenuState after, Note note) { return new Result(ResultKind.DONE, null, after, note, null); }
        public static Result refused(String reason, MenuState after) { return new Result(ResultKind.REFUSED, reason, after); }
        /** Refused with a formatted or titled note (stock notes with arguments). */
        public static Result refusedWith(Note note, MenuState after) {
            return new Result(ResultKind.REFUSED, note == null ? null : note.res, after, note, null);
        }
        public static Result failed(MenuState after) { return new Result(ResultKind.FAILED, null, after); }
        public static Result notReady() { return new Result(ResultKind.NOT_READY, null, null); }
        /** Ask {@code c} first (a question that needed the device); yes calls {@link #setConfirmed}. */
        public static Result ask(Confirm c, MenuState after) { return new Result(ResultKind.ASK, null, after, null, c); }

        /** The same outcome with another read-back. */
        public Result with(MenuState after) { return new Result(kind, reason, after, note, confirm); }

        @Override public String toString() {
            return kind + (reason == null ? "" : "(" + reason + ")")
                    + (note == null || note.res != null && note.res.equals(reason) ? "" : " note " + note)
                    + (confirm == null ? "" : " ask " + confirm);
        }
    }
}
