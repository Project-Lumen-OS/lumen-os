// SPDX-License-Identifier: LicenseRef-PolyForm-Noncommercial-1.0.0
package org.lumen.cec;

/**
 * A framework HDMI-CEC setting of the defaults table (devices/_image/cec_defaults.tsv): its system_server HdmiCecConfig
 * name and the row of the HDMI-CEC page that switches it (null = no row, the projector app never writes it).
 */
public enum CecSetting {
    HDMI_CEC_ENABLED("hdmi_cec_enabled", CecRow.MASTER),
    TV_SEND_STANDBY_ON_SLEEP("tv_send_standby_on_sleep", CecRow.SEND_STANDBY),
    TV_WAKE_ON_ONE_TOUCH_PLAY("tv_wake_on_one_touch_play", CecRow.WAKE),
    POWER_CONTROL_MODE("power_control_mode", null),
    POWER_STATE_CHANGE_ON_ACTIVE_SOURCE_LOST("power_state_change_on_active_source_lost", null),
    SET_MENU_LANGUAGE("set_menu_language", null),
    QUERY_SAD_LPCM("query_sad_lpcm", null),
    QUERY_SAD_DD("query_sad_dd", null),
    QUERY_SAD_MPEG1("query_sad_mpeg1", null),
    QUERY_SAD_MP3("query_sad_mp3", null),
    QUERY_SAD_MPEG2("query_sad_mpeg2", null),
    QUERY_SAD_AAC("query_sad_aac", null),
    QUERY_SAD_DTS("query_sad_dts", null),
    QUERY_SAD_ATRAC("query_sad_atrac", null),
    QUERY_SAD_ONEBITAUDIO("query_sad_onebitaudio", null),
    QUERY_SAD_DDP("query_sad_ddp", null),
    QUERY_SAD_DTSHD("query_sad_dtshd", null),
    QUERY_SAD_TRUEHD("query_sad_truehd", null),
    QUERY_SAD_DST("query_sad_dst", null),
    QUERY_SAD_WMAPRO("query_sad_wmapro", null),
    QUERY_SAD_MAX("query_sad_max", null);

    public final String key;
    public final CecRow row;

    CecSetting(String key, CecRow row) {
        this.key = key;
        this.row = row;
    }

    /** The setting of an HdmiCecConfig name, null when the table does not know it. */
    public static CecSetting of(String key) {
        for (CecSetting s : values()) if (s.key.equals(key)) return s;
        return null;
    }

    /** An on/off switch the projector app writes itself (lumen-cec CecSettings): the one-time reset may name it. */
    public boolean writable() {
        return this == TV_SEND_STANDBY_ON_SLEEP || this == TV_WAKE_ON_ONE_TOUCH_PLAY;
    }
}
