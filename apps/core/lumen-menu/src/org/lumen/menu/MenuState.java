// SPDX-License-Identifier: Apache-2.0
package org.lumen.menu;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The values the device read, by feature ({@link FeatureId}, kept by its id text: the design notes). Plain data,
 * immutable once built; a feature the device could not read is simply absent.
 *
 * <p>v3 runtime parts (the profile says the MODEL has a feature, these say what is usable
 * NOW): {@link #hidden} rows (the Lumen rule: hide, never a disabled row: HDMI rows off HDMI, a row whose getter
 * failed), {@link #options} subsets of a choice (picture modes of this content, the connected sound outputs),
 * {@link #label} texts of runtime tokens (an output's device name, "AI · Music"), a {@link #placeholder} for a choice
 * whose value matches no option, {@link #tile} value lines of the card and {@link #note} lines under a row.
 */
public final class MenuState {
    private final Map<String, String> values;
    private final Set<String> hidden;
    private final Map<String, List<String>> options;
    private final Map<String, String> labels;
    private final Map<String, String> placeholders;
    private final Map<String, String> tiles;
    private final Map<String, String> notes;
    private final Set<String> disabled;

    private MenuState(Builder b) {
        values = Collections.unmodifiableMap(new TreeMap<>(b.v));
        hidden = Collections.unmodifiableSet(new TreeSet<>(b.hidden));
        options = Collections.unmodifiableMap(new TreeMap<>(b.options));
        labels = Collections.unmodifiableMap(new TreeMap<>(b.labels));
        placeholders = Collections.unmodifiableMap(new TreeMap<>(b.placeholders));
        tiles = Collections.unmodifiableMap(new TreeMap<>(b.tiles));
        notes = Collections.unmodifiableMap(new TreeMap<>(b.notes));
        disabled = Collections.unmodifiableSet(new TreeSet<>(b.disabled));
    }

    public static Builder builder() { return new Builder(); }

    private static String key(FeatureId f) { return f == null ? null : f.id; }

    public boolean has(FeatureId feature) { return values.containsKey(key(feature)); }

    /** The raw text value, or null when not read. */
    public String get(FeatureId feature) { return values.get(key(feature)); }

    /** TOGGLE value: TRUE "on", FALSE "off", null not read. */
    public Boolean on(FeatureId feature) {
        String v = values.get(key(feature));
        return v == null ? null : "on".equals(v) ? Boolean.TRUE : "off".equals(v) ? Boolean.FALSE : null;
    }

    /** SLIDER value, or null when not read or not a number. */
    public Integer number(FeatureId feature) {
        String v = values.get(key(feature));
        if (v == null) return null;
        try {
            return Integer.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** True: the row of {@code feature} (or a picture slider) is not shown now. */
    public boolean hidden(FeatureId feature) { return hidden.contains(key(feature)); }

    /** The options usable now (a subset of the profile's, in its order), or null = all of the profile's. */
    public List<String> options(FeatureId feature) { return options.get(key(feature)); }

    /** The text of a runtime option token (key "feature|token"), or null = the module's label. */
    public String label(FeatureId feature, String token) { return labels.get(feature + "|" + token); }

    /** Text a choice shows when its value matches none of the options (stock "Colour temp 3"), or null. */
    public String placeholder(FeatureId feature) { return placeholders.get(key(feature)); }

    /** The value line of a card tile ({@link MenuSpec.Tile#id}: input and HDR type, the game mode), or null. */
    public String tile(String id) { return tiles.get(id); }

    /** A text line shown under the row of {@code feature} now (why a choice does not run yet), or null. */
    public String note(FeatureId feature) { return notes.get(key(feature)); }

    /** A text line at the top of a section page ({@link MenuSpec} section id), or null. */
    public String note(String section) { return notes.get(section); }

    /** True: the row is shown but cannot be used now (only where stock greys it too, e.g. the equalizer entry). */
    public boolean disabled(FeatureId feature) { return disabled.contains(key(feature)); }

    /** The values by id text. */
    public Map<String, String> asMap() { return values; }

    public Set<String> hiddenSet() { return hidden; }

    @Override public String toString() {
        return values + (hidden.isEmpty() ? "" : " hidden " + hidden) + (options.isEmpty() ? "" : " options " + options)
                + (tiles.isEmpty() ? "" : " tiles " + tiles) + (notes.isEmpty() ? "" : " notes " + notes);
    }
    /**
     * This state with {@code part} on top (a live update, ProjectorFeatures.Live: only the values and runtime parts
     * {@code part} names change; its hidden and disabled rows are added).
     */
    public MenuState with(MenuState part) {
        if (part == null) return this;
        Builder b = new Builder();
        b.v.putAll(values);
        b.v.putAll(part.values);
        b.hidden.addAll(hidden);
        b.hidden.addAll(part.hidden);
        b.options.putAll(options);
        b.options.putAll(part.options);
        b.labels.putAll(labels);
        b.labels.putAll(part.labels);
        b.placeholders.putAll(placeholders);
        b.placeholders.putAll(part.placeholders);
        b.tiles.putAll(tiles);
        b.tiles.putAll(part.tiles);
        b.notes.putAll(notes);
        b.notes.putAll(part.notes);
        b.disabled.addAll(disabled);
        b.disabled.addAll(part.disabled);
        return b.build();
    }


    public static final class Builder {
        private final TreeMap<String, String> v = new TreeMap<>();
        private final TreeSet<String> hidden = new TreeSet<>();
        private final TreeMap<String, List<String>> options = new TreeMap<>();
        private final TreeMap<String, String> labels = new TreeMap<>();
        private final TreeMap<String, String> placeholders = new TreeMap<>();
        private final TreeMap<String, String> tiles = new TreeMap<>();
        private final TreeMap<String, String> notes = new TreeMap<>();
        private final TreeSet<String> disabled = new TreeSet<>();

        public Builder put(FeatureId feature, String value) {
            if (feature != null && value != null) v.put(feature.id, value);
            return this;
        }

        public Builder on(FeatureId feature, Boolean value) {
            return value == null ? this : put(feature, value ? "on" : "off");
        }

        public Builder number(FeatureId feature, Integer value) {
            return value == null ? this : put(feature, Integer.toString(value));
        }

        /** The row (or tile) of {@code feature} is not shown now. */
        public Builder hide(FeatureId feature) {
            if (feature != null) hidden.add(feature.id);
            return this;
        }

        /** {@link #hide} when {@code yes}. */
        public Builder hideIf(FeatureId feature, boolean yes) { return yes ? hide(feature) : this; }

        /** Only these options are usable now (tokens of the profile, or runtime tokens with a {@link #label}). */
        public Builder options(FeatureId feature, List<String> tokens) {
            if (feature != null && tokens != null) options.put(feature.id, Collections.unmodifiableList(new java.util.ArrayList<>(tokens)));
            return this;
        }

        public Builder label(FeatureId feature, String token, String text) {
            if (feature != null && token != null && text != null) labels.put(feature + "|" + token, text);
            return this;
        }

        public Builder placeholder(FeatureId feature, String text) {
            if (feature != null && text != null) placeholders.put(feature.id, text);
            return this;
        }

        public Builder tile(String id, String text) {
            if (id != null && text != null) tiles.put(id, text);
            return this;
        }

        public Builder tile(FeatureId feature, String text) { return tile(feature == null ? null : feature.id, text); }

        public Builder note(FeatureId feature, String text) { return note(feature == null ? null : feature.id, text); }

        /** A line at the top of a section page (a {@link MenuSpec} section id). */
        public Builder note(String section, String text) {
            if (section != null && text != null) notes.put(section, text);
            return this;
        }

        public Builder disable(FeatureId feature) {
            if (feature != null) disabled.add(feature.id);
            return this;
        }

        public MenuState build() { return new MenuState(this); }
    }
}
