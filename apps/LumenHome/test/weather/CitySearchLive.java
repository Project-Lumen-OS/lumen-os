package org.lumen.home.weather;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Live check against the real Open-Meteo geocoding service with the searches Weather.search makes (network,
 * not part of run.sh): java CitySearchLive NAME LANG [NAME LANG ...]. Prints the first three places.
 */
public final class CitySearchLive {
    public static void main(String[] a) throws Exception {
        for (int i = 0; i + 1 < a.length; i += 2) {
            List<List<long[]>> found = new ArrayList<>();
            List<String> names = new ArrayList<>();
            List<Long> people = new ArrayList<>();
            StringBuilder asked = new StringBuilder();
            for (String[] s : CitySearch.queries(a[i], a[i + 1])) {
                asked.append(s[0]).append('/').append(s[1]).append(' ');
                found.add(one(s[0], s[1], names, people));
            }
            String[] more = CitySearch.needsMore(people) ? CitySearch.transliterated(a[i], a[i + 1]) : null;
            if (more != null) {
                asked.append(more[0]).append('/').append(more[1]).append(' ');
                found.add(one(more[0], more[1], names, people));
            }
            List<long[]> m = CitySearch.merge(found, x -> x[0], x -> x[1], 8);
            StringBuilder b = new StringBuilder();
            for (int k = 0; k < Math.min(3, m.size()); k++) b.append(names.get((int) m.get(k)[2])).append(" (").append(m.get(k)[1]).append(") ");
            System.out.println(a[i] + " [UI " + a[i + 1] + "] asked: " + asked + "=> " + (m.isEmpty() ? "NOTHING" : b.toString().trim()));
        }
    }

    private static List<long[]> one(String q, String lang, List<String> names, List<Long> people) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?name=" + URLEncoder.encode(q, StandardCharsets.UTF_8)
                .replace("+", "%20") + "&count=8&language=" + lang + "&format=json";
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "LumenHome-test");
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        try (InputStream in = c.getInputStream()) {
            in.transferTo(o);
        }
        String body = o.toString(StandardCharsets.UTF_8);
        List<long[]> out = new ArrayList<>();
        Matcher r = Pattern.compile("\\{\"id\":(\\d+),\"name\":\"([^\"]*)\"(.*?)\\}").matcher(body);
        while (r.find()) {
            Matcher p = Pattern.compile("\"population\":(\\d+)").matcher(r.group(3));
            long pop = p.find() ? Long.parseLong(p.group(1)) : 0;
            Matcher cc = Pattern.compile("\"country\":\"([^\"]*)\"").matcher(r.group(3));
            names.add(r.group(2) + ", " + (cc.find() ? cc.group(1) : "?"));
            people.add(pop);
            out.add(new long[]{Long.parseLong(r.group(1)), pop, names.size() - 1});
        }
        return out;
    }
}
