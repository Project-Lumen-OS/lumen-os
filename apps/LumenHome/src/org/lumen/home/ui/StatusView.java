// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.home.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The Витрина's corner: weather (icon 32, 26/500) and the clock (34/600, tabular digits), right aligned on the
 * margin at y 56. White with a soft shadow over art, the theme's ink on the bare field.
 */
public class StatusView extends LinearLayout {
    private final ImageView mWxIcon, mNet, mVpn;
    private final TextView mWx, mClock;
    private boolean mOnArt = true;

    public StatusView(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        // the network mark (4PDA 2026-11-22): Wi-Fi bars, the cable or crossed Wi-Fi, a key beside it while a VPN is on
        mNet = new ImageView(c);
        addView(mNet, new LayoutParams(Theme.px(30), Theme.px(30)));
        mVpn = new ImageView(c);
        mVpn.setImageResource(org.lumen.icons.R.drawable.lumen_ic_vpn);
        mVpn.setVisibility(GONE);
        LayoutParams vp = new LayoutParams(Theme.px(24), Theme.px(24));
        vp.leftMargin = Theme.px(4);
        addView(mVpn, vp);
        View gap = new View(c);
        addView(gap, new LayoutParams(Theme.px(24), 1));
        mNet.setVisibility(GONE);
        mWxIcon = new ImageView(c);
        addView(mWxIcon, new LayoutParams(Theme.px(32), Theme.px(32)));
        mWx = new TextView(c);
        LayoutParams wp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        wp.leftMargin = Theme.px(10);
        wp.rightMargin = Theme.px(24);
        addView(mWx, wp);
        mClock = new TextView(c);
        addView(mClock);
        restyle();
    }

    public void setInk(boolean onArt) {
        if (onArt == mOnArt) return;
        mOnArt = onArt;
        restyle();
    }

    public void restyle() {
        int t1 = mOnArt ? 0xFFFFFFFF : Theme.TEXT1, t2 = mOnArt ? 0xE6FFFFFF : Theme.TEXT2;
        Theme.style(mWx, 26, 500, t2, 0);
        mWx.setFontFeatureSettings("tnum");
        Theme.style(mClock, 34, 600, t1, -0.6f);
        mClock.setFontFeatureSettings("tnum");
        mWxIcon.setColorFilter(t2);
        mNet.setColorFilter(t2);
        mVpn.setColorFilter(t2);
        float r = mOnArt ? Theme.pxf(5) : 0;
        mWx.setShadowLayer(r, 0, mOnArt ? Theme.pxf(1) : 0, mOnArt ? 0x59000000 : 0);
        mClock.setShadowLayer(r, 0, mOnArt ? Theme.pxf(1) : 0, mOnArt ? 0x59000000 : 0);
    }

    /** The network mark: {@link org.lumen.home.model.NetIcon} icon, and the VPN key beside it. */
    public void setNet(int icon, boolean vpn) {
        mNetIcon = icon;
        mVpnOn = vpn;
        int res;
        switch (icon) {
            case org.lumen.home.model.NetIcon.ETHERNET: res = org.lumen.icons.R.drawable.lumen_ic_lan; break;
            case org.lumen.home.model.NetIcon.WIFI_1: res = org.lumen.icons.R.drawable.lumen_ic_wifi_1; break;
            case org.lumen.home.model.NetIcon.WIFI_2: res = org.lumen.icons.R.drawable.lumen_ic_wifi_2; break;
            case org.lumen.home.model.NetIcon.WIFI_3: res = org.lumen.icons.R.drawable.lumen_ic_wifi_3; break;
            case org.lumen.home.model.NetIcon.WIFI_4: res = org.lumen.icons.R.drawable.lumen_ic_wifi_4; break;
            default: res = org.lumen.icons.R.drawable.lumen_ic_wifi_off;
        }
        mNet.setImageResource(res);
        mNet.setVisibility(VISIBLE);
        mVpn.setVisibility(vpn ? VISIBLE : GONE);
    }

    private boolean mTimeShown = true, mWxOn, mVpnOn;
    private int mNetIcon = -1;

    /** The corner for {@code dumpsys activity} (test/ui): the network mark (NetIcon, -1 never set), VPN key, clock. */
    public String state() {
        return "net=" + mNetIcon + " vpn=" + mVpnOn + " clock=" + mTimeShown;
    }

    /** The weather and the clock of the corner (hidden while the big clock shows them); the network mark always stays. */
    public void setTimeShown(boolean shown) {
        mTimeShown = shown;
        mClock.setVisibility(shown ? VISIBLE : GONE);
        showWeather();
    }

    private void showWeather() {
        int v = mTimeShown && mWxOn ? VISIBLE : GONE;
        mWxIcon.setVisibility(v);
        mWx.setVisibility(v);
    }

    public void setClock(String s) {
        mClock.setText(s);
    }

    /** Weather icon and temperature; none (0, null) hides both. */
    public void setWeather(int icon, String temp) {
        boolean on = icon != 0 && temp != null;
        mWxOn = on;
        showWeather();
        if (!on) return;
        mWxIcon.setImageResource(icon);
        mWx.setText(temp);
    }
}
