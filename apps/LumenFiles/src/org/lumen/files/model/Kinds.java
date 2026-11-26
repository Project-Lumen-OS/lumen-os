// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.files.model;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Kinds and MIME types by file name ending (the types of the old Home Files plus archives and documents). */
public final class Kinds {
    public static final String APK_MIME = "application/vnd.android.package-archive";
    private static final Map<String, Kind> KINDS = new HashMap<>();
    private static final Map<String, String> MIME = new HashMap<>();
    private static final Map<String, String> EXT = new HashMap<>();

    static {
        kind(Kind.VIDEO, "mkv mp4 m4v avi mov ts m2ts mts webm wmv flv mpg mpeg vob 3gp rmvb rm divx ogv asf");
        kind(Kind.MUSIC, "mp3 flac aac m4a ogg oga opus wav wma ape ac3 eac3 dts mka aif aiff amr");
        kind(Kind.PHOTO, "jpg jpeg png webp gif bmp heic heif");
        kind(Kind.APK, "apk");
        kind(Kind.ARCHIVE, "zip tar tgz gz 7z rar xz bz2");
        kind(Kind.DOCUMENT, "pdf txt srt ass ssa sub doc docx xls xlsx ppt pptx odt ods odp rtf epub fb2 csv");
        mime("video/x-matroska", "mkv");
        mime("video/mp4", "mp4 m4v");
        mime("video/x-msvideo", "avi divx");
        mime("video/quicktime", "mov");
        mime("video/mp2t", "ts m2ts mts");
        mime("video/webm", "webm");
        mime("video/x-ms-wmv", "wmv");
        mime("video/x-flv", "flv");
        mime("video/mpeg", "mpg mpeg vob");
        mime("video/3gpp", "3gp");
        mime("video/x-pn-realvideo", "rmvb rm");
        mime("video/ogg", "ogv");
        mime("video/x-ms-asf", "asf");
        mime("audio/mpeg", "mp3");
        mime("audio/flac", "flac");
        mime("audio/aac", "aac");
        mime("audio/mp4", "m4a");
        mime("audio/ogg", "ogg oga opus");
        mime("audio/x-wav", "wav");
        mime("audio/x-ms-wma", "wma");
        mime("audio/x-ape", "ape");
        mime("audio/ac3", "ac3");
        mime("audio/eac3", "eac3");
        mime("audio/vnd.dts", "dts");
        mime("audio/x-matroska", "mka");
        mime("audio/x-aiff", "aif aiff");
        mime("audio/amr", "amr");
        mime("image/jpeg", "jpg jpeg");
        mime("image/png", "png");
        mime("image/webp", "webp");
        mime("image/gif", "gif");
        mime("image/bmp", "bmp");
        mime("image/heic", "heic");
        mime("image/heif", "heif");
        mime(APK_MIME, "apk");
        mime("application/zip", "zip");
        mime("application/x-tar", "tar");
        mime("application/gzip", "tgz gz");
        mime("application/x-7z-compressed", "7z");
        mime("application/vnd.rar", "rar");
        mime("application/pdf", "pdf");
        mime("text/plain", "txt csv");
        mime("application/x-subrip", "srt");
        mime("application/epub+zip", "epub");
    }

    private Kinds() {}

    private static void kind(Kind k, String exts) {
        for (String e : exts.split(" ")) KINDS.put(e, k);
    }

    private static void mime(String m, String exts) {
        for (String e : exts.split(" ")) MIME.put(e, m);
        EXT.putIfAbsent(m, exts.split(" ")[0]);
    }

    /** The ending after the last dot, lower case; "" without one. */
    public static String ext(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** The kind of a file (never FOLDER) from its name. */
    public static Kind of(String name) {
        Kind k = KINDS.get(ext(name));
        return k == null ? Kind.OTHER : k;
    }

    /** The kind of a server's MIME type (DLNA items), OTHER when it says nothing useful. */
    public static Kind ofMime(String mime) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (m.startsWith("video/")) return Kind.VIDEO;
        if (m.startsWith("audio/")) return Kind.MUSIC;
        if (m.startsWith("image/")) return Kind.PHOTO;
        if (m.equals(APK_MIME)) return Kind.APK;
        return Kind.OTHER;
    }

    /** The MIME type a player or viewer is asked with; null when only the system's table may know it. */
    public static String mime(String name) {
        return MIME.get(ext(name));
    }

    /** The usual ending of a MIME type (a DLNA title without one), null when unknown. */
    public static String extOf(String mime) {
        return mime == null ? null : EXT.get(mime.toLowerCase(Locale.ROOT));
    }

    /** The type when neither this table nor the system's knows the ending. */
    public static String fallback(Kind k) {
        switch (k) {
            case VIDEO: return "video/*";
            case MUSIC: return "audio/*";
            case PHOTO: return "image/*";
            default: return "application/octet-stream";
        }
    }
}
