package org.lumen.projector.ui;
public final class Notify { public static void show(android.content.Context c, String title, String desc) {
    lumen.fake.World.event("notify " + title + (desc == null ? "" : " / " + desc)); } }
