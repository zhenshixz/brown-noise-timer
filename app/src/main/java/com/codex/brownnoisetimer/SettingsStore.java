package com.codex.brownnoisetimer;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

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
    private static final String RULES_JSON = "rules_json";
    private static final String REVISION = "schedule_revision";

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
        for (ScheduleRule rule : rules(context)) if (rule.enabled) return true;
        return false;
    }

    static int revision(Context context) {
        return get(context).getInt(REVISION, 0);
    }

    static List<ScheduleRule> rules(Context context) {
        SharedPreferences prefs = get(context);
        String saved = prefs.getString(RULES_JSON, null);
        if (saved != null) {
            try {
                JSONArray array = new JSONArray(saved);
                List<ScheduleRule> result = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    int firstDay = item.optInt("firstDay", 1);
                    int lastDay = item.optInt("lastDay", 7);
                    int start = item.optInt("startMinutes", 420);
                    int stop = item.optInt("stopMinutes", 600);
                    boolean hasStart = item.optBoolean("startEnabled", true);
                    boolean hasStop = item.optBoolean("stopEnabled", true);
                    if (firstDay < 1 || firstDay > 7 || lastDay < 1 || lastDay > 7
                            || start < 0 || start >= 1440 || stop < 0 || stop >= 1440
                            || (!hasStart && !hasStop)) continue;
                    ScheduleRule rule = new ScheduleRule(item.optLong("id", i + 1), hasStart,
                            hasStop, start, stop, firstDay, lastDay);
                    rule.enabled = item.optBoolean("enabled", prefs.getBoolean(ENABLED, false));
                    result.add(rule);
                }
                return result;
            } catch (JSONException ignored) {
                // Fall back to the old schedule if stored rules cannot be read.
            }
        }
        List<ScheduleRule> legacy = new ArrayList<>();
        ScheduleRule rule = new ScheduleRule(1, true, true, startMinutes(context),
                stopMinutes(context), 1, weekdaysOnly(context) ? 5 : 7);
        rule.enabled = prefs.getBoolean(ENABLED, false);
        legacy.add(rule);
        return legacy;
    }

    static void saveRules(Context context, List<ScheduleRule> rules) {
        JSONArray array = new JSONArray();
        boolean anyEnabled = false;
        for (ScheduleRule rule : rules) {
            anyEnabled |= rule.enabled;
            JSONObject item = new JSONObject();
            try {
                item.put("id", rule.id);
                item.put("enabled", rule.enabled);
                item.put("startEnabled", rule.startEnabled);
                item.put("stopEnabled", rule.stopEnabled);
                item.put("startMinutes", rule.startMinutes);
                item.put("stopMinutes", rule.stopMinutes);
                item.put("firstDay", rule.firstDay);
                item.put("lastDay", rule.lastDay);
            } catch (JSONException error) {
                throw new IllegalStateException("Unable to save schedule", error);
            }
            array.put(item);
        }
        SharedPreferences prefs = get(context);
        prefs.edit()
                .putString(RULES_JSON, array.toString())
                .putBoolean(ENABLED, anyEnabled)
                .putInt(REVISION, prefs.getInt(REVISION, 0) + 1)
                .apply();
    }
}
