// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.projector.voice;

import java.util.Arrays;
import java.util.List;

/** Plain JVM test of the assistant's offline parts: sh apps/common/projector/test/run_voice_test.sh */
public final class VoiceTest {
    private static int pass, fail;

    private static void eq(Object got, Object want, String what) {
        if (String.valueOf(got).equals(String.valueOf(want))) pass++;
        else { fail++; System.out.println("FAIL " + what + ": got " + got + ", want " + want); }
    }

    private static void cmd(String said, String want) { eq(Commands.parse(said), want, "\"" + said + "\""); }

    public static void main(String[] a) {
        cmd("Сделай погромче", "VOLUME_UP[2]");
        cmd("сделай намного громче", "VOLUME_UP[5]");
        cmd("Тише, пожалуйста", "VOLUME_DOWN[2]");
        cmd("громкость 30", "VOLUME_SET[30]");
        cmd("Поставь громкость на 50%", "VOLUME_SET[50]");
        cmd("volume 20 percent", "VOLUME_SET[20]");
        cmd("выключи звук", "MUTE");
        cmd("пауза", "PAUSE");
        cmd("Продолжи", "PLAY");
        cmd("следующая серия", "NEXT");
        cmd("на главную", "HOME");
        cmd("Сделай автофокус", "AUTOFOCUS");
        cmd("выровняй картинку", "KEYSTONE");
        cmd("настройки звука", "SECTION(sound)");
        cmd("убавь яркость", "SECTION(lamp)");
        cmd("Который час?", "TIME");
        cmd("открой ютуб", "OPEN_APP(ютуб)");
        cmd("Запусти Кинопоиск", "OPEN_APP(кинопоиск)");
        cmd("open netflix", "OPEN_APP(netflix)");
        cmd("включи вк видео", "OPEN_APP(вк видео)[1]");
        cmd("найди интерстеллар", "SEARCH(интерстеллар)");
        cmd("хочу посмотреть Кухню", "SEARCH(кухню)");
        cmd("найди котиков на ютубе", "YOUTUBE(котиков)");
        cmd("ютуб лофай музыка", "YOUTUBE(лофай музыка)");
        cmd("play lofi on youtube", "YOUTUBE(lofi)");
        cmd("Люмен, расскажи анекдот", "UNKNOWN");
        cmd("какая погода завтра", "UNKNOWN");
        cmd("открой тв-каналы", "OPEN_APP(тв каналы)");
        cmd("включи IPTV", "OPEN_APP(iptv)[1]");

        List<AppMatcher.App> apps = Arrays.asList(new AppMatcher.App("com.google.android.youtube.tv", "YouTube"),
                new AppMatcher.App("ru.kinopoisk.tv", "Кинопоиск"), new AppMatcher.App("com.vk.tv", "VK Видео"),
                new AppMatcher.App("ru.rutube.app.tv", "RUTUBE"), new AppMatcher.App("top.rootu.lampa", "LAMPA"),
                new AppMatcher.App("com.xgimiru.player", "Lumen Play"), new AppMatcher.App("com.netflix.ninja", "Netflix"),
                new AppMatcher.App("org.videolan.vlc", "VLC"), new AppMatcher.App("com.plexapp.android", "Plex"));
        eq(pkg(AppMatcher.match("ютуб", apps)), "com.google.android.youtube.tv", "ютуб");
        eq(pkg(AppMatcher.match("кинопоиск", apps)), "ru.kinopoisk.tv", "кинопоиск");
        eq(pkg(AppMatcher.match("вк видео", apps)), "com.vk.tv", "вк видео");
        eq(pkg(AppMatcher.match("рутуб", apps)), "ru.rutube.app.tv", "рутуб");
        eq(pkg(AppMatcher.match("лампу", apps)), "top.rootu.lampa", "лампу");
        eq(pkg(AppMatcher.match("нетфликс", apps)), "com.netflix.ninja", "нетфликс");
        eq(pkg(AppMatcher.match("плекс", apps)), "com.plexapp.android", "плекс (translit)");
        eq(pkg(AppMatcher.match("влц", apps)), "org.videolan.vlc", "влц");
        eq(pkg(AppMatcher.match("люмен плей", apps)), "com.xgimiru.player", "люмен плей");
        eq(pkg(AppMatcher.match("интерстеллар", apps)), "null", "not an app");
        tvChannels(apps);
        System.out.println("voice tests passed=" + pass + " failed=" + fail);
        if (fail > 0) System.exit(1);
    }

    /** TV channel words open a TV app or nothing (the caller searches), never Lumen Play: 2.0 has no IPTV. */
    private static void tvChannels(List<AppMatcher.App> apps) {
        String[] said = {"iptv", "IP TV", "айпи тв", "тв каналы", "тв-каналы", "телеканалы", "телевизор", "тв",
                "приложение iptv", "live TV", "TV channels", "Télévision", "chaînes TV", "kanały TV", "TV kanalları",
                "Fernsehen", "телебачення", "テレビ", "电视", "電視", "티비", "قنوات"};
        for (String w : said) eq(pkg(AppMatcher.match(w, apps)), "top.rootu.lampa", w + ": Lampa, not Lumen Play");
        List<AppMatcher.App> playOnly = Arrays.asList(new AppMatcher.App("com.xgimiru.player", "Lumen Play", true),
                new AppMatcher.App("com.phlox.tvwebbrowser", "TV Bro"), new AppMatcher.App("com.google.android.youtube.tv", "YouTube"));
        for (String w : said) eq(pkg(AppMatcher.match(w, playOnly)), "null", w + ": no TV app, search");
        List<AppMatcher.App> rich = Arrays.asList(new AppMatcher.App("com.xgimiru.player", "Lumen Play"),
                new AppMatcher.App("top.rootu.lampa", "LAMPA"), new AppMatcher.App("ar.tvplayer.tv", "TiviMate"),
                new AppMatcher.App("com.google.android.tv", "Live Channels"));
        eq(pkg(AppMatcher.match("тв каналы", rich)), "ar.tvplayer.tv", "IPTV player before Live Channels and Lampa");
        eq(pkg(AppMatcher.match("тв каналы", Arrays.asList(new AppMatcher.App("top.rootu.lampa", "LAMPA"),
                new AppMatcher.App("com.google.android.tv", "Live Channels")))), "com.google.android.tv", "Live Channels before Lampa");
        eq(pkg(AppMatcher.match("iptv", Arrays.asList(new AppMatcher.App("top.rootu.lampa", "LAMPA"),
                new AppMatcher.App("com.example.channels", "Каналы", true)))), "com.example.channels", "an app that opens TV channels");
        eq(pkg(AppMatcher.match("live tv", Arrays.asList(new AppMatcher.App("com.xgimiru.player", "Lumen Play"),
                new AppMatcher.App("com.example.m3u", "Smart IPTV")))), "com.example.m3u", "IPTV in the app name");
        eq(pkg(AppMatcher.match("лампу", rich)), "top.rootu.lampa", "Lampa by its name");
        for (String w : new String[] {"кино", "плеер", "видеоплеер", "торренты", "люмен плей", "lumen play", "video player", "torrents"}) {
            eq(pkg(AppMatcher.match(w, rich)), "com.xgimiru.player", w + ": Lumen Play");
        }
    }

    private static String pkg(AppMatcher.App a) { return a == null ? "null" : a.pkg; }
}
