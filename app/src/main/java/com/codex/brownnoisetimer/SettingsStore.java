package com.codex.brownnoisetimer;

import android.content.Context;
import android.content.SharedPreferences;

final class SettingsStore {
    private static final String PREFS = "brown_noise_settings";
    static final String FILE_URI = "file_uri";
    static final String FILE_NAME = "file_name";
    static final String START_MINUTES = "start_minutes";
    static final String STOP_MINUTES = "stop_minutes";
    static final String WEEKDAYS_ONLY = "weekdays_only";
    static final String ENABLED = "enabled";
    static final String PLAYING = "playing";
    static final String LAST_ERROR = "last_error";

    private SettingsStore() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String fileUri(Context context) {
        return get(context).getString(FILE_URI, "");
    }

    static int startMinutes(Context context) {
        return get(context).getInt(START_MINUTES, 7 * 60);
    }

    static int stopMinutes(Context context) {
        return get(context).getInt(STOP_MINUTES, 10 * 60);
    }

    static boolean weekdaysOnly(Context context) {
        return get(context).getBoolean(WEEKDAYS_ONLY, false);
    }

    static boolean enabled(Context context) {
        return get(context).getBoolean(ENABLED, false);
    }
}

