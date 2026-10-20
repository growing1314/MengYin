package com.mengyin.prank;

import android.content.Context;
import android.content.SharedPreferences;

/** 简单的设置存储。 */
public final class Prefs {
    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("mengyin", Context.MODE_PRIVATE);
    }

    public static VoicePreset preset(Context c) {
        return VoicePreset.byId(sp(c).getString("preset", "normal"));
    }

    public static void setPreset(Context c, VoicePreset p) {
        sp(c).edit().putString("preset", p.id).apply();
    }

    /** 点语音后等几秒再播放，留时间去按住微信的说话按钮。 */
    public static int delaySeconds(Context c) {
        return sp(c).getInt("delay", 2);
    }

    public static void setDelaySeconds(Context c, int s) {
        sp(c).edit().putInt("delay", s).apply();
    }

    /** 播放时自动把媒体音量调到最大，播完恢复。 */
    public static boolean boost(Context c) {
        return sp(c).getBoolean("boost", true);
    }

    public static void setBoost(Context c, boolean b) {
        sp(c).edit().putBoolean("boost", b).apply();
    }

    /** 语音包排序方式：new / used / name。 */
    public static String sort(Context c) {
        return sp(c).getString("sort", ClipStore.SORT_NEW);
    }

    public static void setSort(Context c, String s) {
        sp(c).edit().putString("sort", s).apply();
    }

    /** 悬浮窗里的筛选：全部 / 收藏 / 某个分类。 */
    public static String floatFilter(Context c) {
        return sp(c).getString("floatFilter", ClipStore.FILTER_ALL);
    }

    public static void setFloatFilter(Context c, String f) {
        sp(c).edit().putString("floatFilter", f).apply();
    }

    /** 上一次导入时选的分类，下次默认选它。 */
    public static String lastCategory(Context c) {
        return sp(c).getString("lastCategory", "");
    }

    public static void setLastCategory(Context c, String s) {
        sp(c).edit().putString("lastCategory", s).apply();
    }

    public static boolean seeded(Context c) {
        return sp(c).getBoolean("seeded", false);
    }

    public static void setSeeded(Context c) {
        sp(c).edit().putBoolean("seeded", true).apply();
    }
}
