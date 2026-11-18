package com.android.tv.settings;

/**
 * Compile-time stand-in with the signatures of the class in the base TvSettings (LineageOS 21). The real class stays in
 * the base dex; this file never reaches the image. The ids AboutFragment uses, the values of the base APK (aapt2 dump resources): javac writes them into the code,
 * so test/about_link.py proves they equal the constants of the shipped class.
 */
public final class R {
    public static final class bool {
        public static final int config_additional_system_update_setting_enable = 0x7f050002;
        public static final int config_show_manual = 0x7f050010;
    }

    public static final class plurals {
        public static final int show_dev_countdown_cm = 0x7f0f0004;
    }

    public static final class string {
        public static final int device_info_default = 0x7f1102b0;
        public static final int factory_reset_content_description = 0x7f110347;
        public static final int selinux_status_disabled = 0x7f1105d6;
        public static final int selinux_status_permissive = 0x7f1105d8;
        public static final int show_dev_already_cm = 0x7f1105fd;
        public static final int show_dev_on_cm = 0x7f110600;
        public static final int system_update_content_description = 0x7f110712;
    }

    public static final class xml {
        public static final int device_info_settings = 0x7f15001f;
        public static final int device_info_settings_vendor = 0x7f150020;
        public static final int device_info_settings_x = 0x7f150021;
    }
}
