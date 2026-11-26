// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.data;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * The SMB servers a user saved in Home's old Files panel (1.0.6 to 1.0.10) move to the Files app once: Files calls
 * "take", gets them with their passwords (only Home can read its keystore key), stores them under its own key, and
 * Home forgets them (prefs and key). Only org.lumen.files signed with Home's own key may call. Remove this class
 * with its manifest entry once every published build has Files.
 */
public final class ServersHandOff extends ContentProvider {
    static final String FILES = "org.lumen.files", METHOD = "take";
    static final String PREFS = "net_servers", LIST = "servers", KEY_ALIAS = "lumen_home_net_pw";
    private static final String TAG = "Z9xHome";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD.equals(method)) return null;
        Context c = getContext();
        if (c == null || !FILES.equals(getCallingPackage())
                || c.getPackageManager().checkSignatures(c.getPackageName(), FILES) != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException("only Files");
        }
        return take(c);
    }

    static synchronized Bundle take(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Bundle b = new Bundle();
        try {
            JSONArray a = new JSONArray(sp.getString(LIST, "[]"));
            int n = a.length();
            String[] id = new String[n], name = new String[n], host = new String[n], login = new String[n], pw = new String[n],
                    share = new String[n];
            int[] port = new int[n];
            for (int i = 0; i < n; i++) {
                JSONObject o = a.getJSONObject(i);
                id[i] = o.optString("id");
                name[i] = o.optString("name");
                host[i] = o.optString("host");
                port[i] = o.optInt("port", 445);
                login[i] = o.optString("login");
                share[i] = o.optString("share");
                pw[i] = o.optBoolean("pw0") ? "" : o.has("pwe") ? decrypt(o.optString("pwe")) : null;
            }
            b.putStringArray("id", id);
            b.putStringArray("name", name);
            b.putStringArray("host", host);
            b.putStringArray("login", login);
            b.putStringArray("password", pw);
            b.putStringArray("share", share);
            b.putIntArray("port", port);
            Log.i(TAG, "servers handed to Files: " + n);
        } catch (JSONException e) {
            Log.w(TAG, "servers unreadable: " + e);
        }
        sp.edit().clear().commit();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
        } catch (Exception e) {
            Log.w(TAG, "keystore: " + e);
        }
        return b;
    }

    static String decrypt(String b64) {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry e = ks.getEntry(KEY_ALIAS, null);
            if (!(e instanceof KeyStore.SecretKeyEntry)) return null;
            SecretKey k = ((KeyStore.SecretKeyEntry) e).getSecretKey();
            byte[] in = Base64.decode(b64, Base64.NO_WRAP);
            int ivLen = in[0] & 0xff;
            if (ivLen < 12 || 1 + ivLen >= in.length) return null;
            byte[] iv = new byte[ivLen];
            System.arraycopy(in, 1, iv, 0, ivLen);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, k, new GCMParameterSpec(128, iv));
            return new String(c.doFinal(in, 1 + ivLen, in.length - 1 - ivLen), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "keystore: password unreadable: " + e);
            return null;
        }
    }

    @Override
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public String getType(Uri u) {
        return null;
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        return 0;
    }
}
