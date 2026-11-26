// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.voice;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Finds the installed app a spoken name means ("ютуб", "кинопоиск", "вк видео", "open netflix"). Pure Java: the
 * caller passes the launchable apps (Assistant lists them). Known names first (ALIASES, by package), then the
 * app labels, compared in Latin letters (Cyrillic speech is transliterated) with a small edit distance.
 * TV channel words ("iptv", "тв каналы", "live tv") open a TV app or nothing, never Lumen Play (no IPTV since 2.0).
 */
public final class AppMatcher {
    /** One launchable app: its package, label and whether it opens Android's TV channels (as Live Channels). */
    public static final class App {
        public final String pkg;
        public final String label;
        public final boolean tv;

        public App(String pkg, String label) {
            this(pkg, label, false);
        }

        public App(String pkg, String label, boolean tv) {
            this.pkg = pkg;
            this.label = label == null ? "" : label;
            this.tv = tv;
        }
    }

    static final String LUMEN_PLAY = "com.xgimiru.player", LAMPA = "top.rootu.lampa";

    /** TV apps, the first installed one wins: IPTV players, then the system Live Channels apps. */
    private static final String[] TV_APPS = {"ar.tvplayer.tv", "studio.scillarium.ottnavigator", "com.niklabs.pp",
            "ru.iptvremote.android.iptv.pro", "ru.iptvremote.android.iptv", "com.ottplay.ottplay", "com.infolink.limeiptv",
            "ru.rt.video.app.tv", "com.google.android.tv", "com.android.tv"};

    /** Spoken TV channel words (normalised) in the languages of the app, kept folded (see fold). */
    private static final Set<String> TV_WORDS = new HashSet<>();

    private static final List<String> TV_SAID = Arrays.asList(
            // ru
            "iptv", "ip tv", "иптв", "ип тв", "айпи тв", "ай пи тв", "айпитиви", "айпи тиви", "тв", "тиви", "ти ви",
            "тв каналы", "тв канал", "телеканалы", "телеканал", "каналы", "телевизор", "телевидение", "телик",
            "тв онлайн", "онлайн тв", "цифровое тв", "интерактивное тв", "прямой эфир", "эфир", "эфирные каналы",
            // uk, be, kk
            "тб", "тб канали", "тв канали", "телеканали", "канали", "телевізор", "телебачення", "ефір", "тэлебачанне",
            "тэлеканалы", "тэлевізар", "тб каналы", "теледидар", "телеарналар", "тв арналары", "арналар",
            // en
            "tv", "live tv", "tv channels", "tv channel", "channels", "live channels", "television", "tv online",
            // de, fr, es, it, pt, pl, tr
            "fernsehen", "fernseher", "fernsehsender", "tv sender", "sender", "tele", "chaines", "chaines tv",
            "chaines de tele", "tv en direct", "canales", "canales de tv", "canales de television", "tv en vivo",
            "televisione", "canali", "canali tv", "tv in diretta", "televisao", "canais", "canais de tv",
            "canais de televisao", "tv ao vivo", "telewizja", "telewizor", "kanaly", "kanaly tv", "televizyon",
            "tv kanallari", "kanallar", "canli tv",
            // ar, zh, ja, ko
            "تلفزيون", "التلفزيون", "تلفاز", "قنوات", "قنوات تلفزيونية", "电视", "电视直播", "电视频道", "频道", "直播",
            "電視", "電視直播", "電視頻道", "頻道", "テレビ", "テレビチャンネル", "ライブテレビ", "티비", "텔레비전",
            "채널", "tv 채널");

    /** Spoken names (normalised) -> packages, the first installed one wins. */
    private static final Map<String, String[]> ALIASES = new HashMap<>();

    private static void alias(String pkgs, String... names) {
        String[] p = pkgs.split(",");
        for (String n : names) ALIASES.put(n, p);
    }

    static {
        for (String w : TV_SAID) TV_WORDS.add(fold(w));
        alias("com.google.android.youtube.tv,com.google.android.youtube.tvkids,com.teamsmart.videomanager.tv",
                "ютуб", "ютьюб", "ютюб", "youtube", "you tube", "ютубчик");
        alias("ru.kinopoisk.tv,ru.kinopoisk", "кинопоиск", "кино поиск", "kinopoisk", "яндекс кинопоиск");
        alias("com.vk.tv,com.vk.vkvideo", "вк видео", "вк", "vk видео", "vk video", "вконтакте", "вконтакте видео", "вк видос");
        alias("ru.rutube.app.tv,ru.rutube.app", "рутуб", "рутюб", "rutube", "ру туб");
        alias(LAMPA, "лампа", "lampa", "лампу");
        alias(LUMEN_PLAY, "кино", "люмен плей", "lumen play", "люмен плеер", "плеер", "видеоплеер", "видео плеер",
                "торренты", "торрент", "player", "video player", "torrent", "torrents");
        alias("ru.ivi.client", "иви", "ivi");
        alias("ru.more.play,ru.okko.tv", "окко", "okko");
        alias("ru.start.androidmobile,ru.start.tv", "старт", "start");
        alias("com.netflix.ninja,com.netflix.mediaclient", "нетфликс", "netflix");
        alias("com.spotify.tv.android,com.spotify.music", "спотифай", "spotify");
        alias("com.yandex.music,ru.yandex.music", "яндекс музыка", "yandex music");
        alias("ru.vk.store.tv,ru.vk.store", "рустор", "ру стор", "rustore");
        alias("com.android.vending", "гугл плей", "плей маркет", "плей стор", "google play", "play store");
        alias("com.android.tv.settings", "настройки андроид", "настройки системы", "android settings", "system settings");
        alias("org.videolan.vlc", "влц", "вэлси", "vlc", "влс");
        alias("org.lumen.airplay", "эйрплей", "airplay", "air play");
        alias("com.google.android.apps.tv.launcherx,org.lumen.home", "лаунчер", "launcher");
    }

    private AppMatcher() {}

    /** The best match for {@code spoken}, or null when nothing is close enough. */
    public static App match(String spoken, List<App> apps) {
        String s = Commands.normalize(spoken).replaceAll("^(приложение|app|the app|игру|игра) ", "");
        if (s.isEmpty() || apps == null || apps.isEmpty()) return null;
        if (tvWords(s)) return tvApp(apps);
        String[] pkgs = ALIASES.get(s);
        if (pkgs != null) {
            for (String p : pkgs) for (App a : apps) if (a.pkg.equals(p)) return a;
        }
        String ls = latin(s);
        App best = null;
        int bestScore = Integer.MAX_VALUE;
        for (App a : apps) {
            String la = latin(Commands.normalize(a.label));
            if (la.isEmpty()) continue;
            if (la.equals(ls)) return a;
            int d;
            if (la.startsWith(ls) || ls.startsWith(la)) d = Math.abs(la.length() - ls.length()) / 3;
            else if (la.contains(ls) && ls.length() >= 4) d = 1 + (la.length() - ls.length()) / 4;
            else d = distance(la, ls);
            int allowed = Math.max(1, Math.min(la.length(), ls.length()) / 4);
            if (d <= allowed && d < bestScore) {
                best = a;
                bestScore = d;
            }
        }
        return best;
    }

    /** {@code s} (normalised) names TV channels in some language. */
    static boolean tvWords(String s) {
        return TV_WORDS.contains(fold(s));
    }

    /** Without accents (télé = tele, kanały = kanaly, kanalları = kanallari), the same for the speech and the list. */
    private static String fold(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('ł', 'l').replace('ı', 'i');
    }

    /**
     * The TV app for TV channel words: a known IPTV or Live Channels app, else any app that opens TV channels or has
     * IPTV in its name, else Lampa (its IPTV plugin), else null (the caller searches). Never Lumen Play.
     */
    static App tvApp(List<App> apps) {
        for (String p : TV_APPS) for (App a : apps) if (a.pkg.equals(p)) return a;
        for (App a : apps) {
            if (!a.pkg.equals(LUMEN_PLAY) && (a.tv || latin(Commands.normalize(a.label)).contains("iptv"))) return a;
        }
        for (App a : apps) if (a.pkg.equals(LAMPA)) return a;
        return null;
    }

    private static final String CYR = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя";
    private static final String[] LAT = {"a", "b", "v", "g", "d", "e", "e", "zh", "z", "i", "i", "k", "l", "m", "n", "o", "p",
            "r", "s", "t", "u", "f", "h", "ts", "ch", "sh", "sch", "", "y", "", "e", "yu", "ya"};

    /** Latin letters and digits only, Cyrillic transliterated, and a few spellings folded (you->yu, ph->f, w->v). */
    static String latin(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            int i = CYR.indexOf(c);
            if (i >= 0) b.append(LAT[i]);
            else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) b.append(c);
        }
        return b.toString().replace("you", "yu").replace("ph", "f").replace("w", "v").replace("ck", "k")
                .replace("x", "ks").replace("q", "k").replace("ee", "i").replace("oo", "u").replace("y", "i")
                .replace("c", "k");
    }

    static int distance(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int c = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + c);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }
}
