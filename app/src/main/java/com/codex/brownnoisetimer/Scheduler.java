package com.codex.brownnoisetimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

final class Scheduler {
    static final String ACTION_START = "com.codex.brownnoisetimer.START";
    static final String ACTION_STOP = "com.codex.brownnoisetimer.STOP";
    static final String ACTION_STOP_NOW = "com.codex.brownnoisetimer.STOP_NOW";
    static final String EXTRA_STOP_AT = "stop_at";
    private static final int START_REQUEST = 101;
    private static final int STOP_REQUEST = 102;
    private static final int SHOW_REQUEST = 103;

    private Scheduler() {}

    static boolean canScheduleExact(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return manager != null && manager.canScheduleExactAlarms();
    }

    static void cancelAll(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        manager.cancel(startIntent(context, 0));
        // Remove alarms created by older versions of the app as well.
        manager.cancel(alarmIntent(context, ACTION_START, START_REQUEST, 0));
        manager.cancel(alarmIntent(context, ACTION_STOP, STOP_REQUEST, 0));
        manager.cancel(stopIntent(context));
    }

    static boolean scheduleAll(Context context) {
        cancelAll(context);
        if (!SettingsStore.enabled(context)) return true;
        if (!canScheduleExact(context)) return false;
        long startAt = nextStartMillis(context, System.currentTimeMillis());
        long stopAt = stopForStart(context, startAt);
        scheduleStart(context, startAt, stopAt);
        scheduleStop(context, stopAt);
        return true;
    }

    static void scheduleNextStart(Context context) {
        if (!SettingsStore.enabled(context) || !canScheduleExact(context)) return;
        long startAt = nextStartMillis(context, System.currentTimeMillis() + 60_000L);
        scheduleStart(context, startAt, stopForStart(context, startAt));
    }

    static void scheduleNextStop(Context context) {
        if (!SettingsStore.enabled(context) || !canScheduleExact(context)) return;
        long startAt = nextStartMillis(context, System.currentTimeMillis());
        scheduleStop(context, stopForStart(context, startAt));
    }

    static void scheduleStopAt(Context context, long stopAt) {
        if (SettingsStore.enabled(context) && canScheduleExact(context)) {
            scheduleStop(context, stopAt);
        }
    }

    static long nextStartMillis(Context context, long afterMillis) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate();
        int minutes = SettingsStore.startMinutes(context);
        for (int offset = 0; offset < 8; offset++) {
            LocalDate date = today.plusDays(offset);
            DayOfWeek day = date.getDayOfWeek();
            if (SettingsStore.weekdaysOnly(context)
                    && (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)) continue;
            long candidate = LocalDateTime.of(date, java.time.LocalTime.of(minutes / 60, minutes % 60))
                    .atZone(zone).toInstant().toEpochMilli();
            if (candidate > afterMillis) return candidate;
        }
        throw new IllegalStateException("No scheduled day found");
    }

    static long stopForStart(Context context, long startMillis) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate();
        int start = SettingsStore.startMinutes(context);
        int stop = SettingsStore.stopMinutes(context);
        if (stop <= start) date = date.plusDays(1);
        return LocalDateTime.of(date, java.time.LocalTime.of(stop / 60, stop % 60))
                .atZone(zone).toInstant().toEpochMilli();
    }

    private static void scheduleStart(Context context, long at, long stopAt) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        PendingIntent operation = startIntent(context, stopAt);
        Intent show = new Intent(context, MainActivity.class);
        PendingIntent showIntent = PendingIntent.getActivity(context, SHOW_REQUEST, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent), operation);
    }

    private static void scheduleStop(Context context, long at) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        Intent show = new Intent(context, MainActivity.class);
        PendingIntent showIntent = PendingIntent.getActivity(context, SHOW_REQUEST, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent),
                alarmIntent(context, ACTION_STOP, STOP_REQUEST, 0));
    }

    private static PendingIntent alarmIntent(Context context, String action, int requestCode, long stopAt) {
        Intent intent = new Intent(context, ScheduleReceiver.class).setAction(action);
        if (stopAt > 0) intent.putExtra(EXTRA_STOP_AT, stopAt);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent startIntent(Context context, long stopAt) {
        Intent intent = new Intent(context, PlaybackService.class)
                .setAction(PlaybackService.ACTION_PLAY);
        if (stopAt > 0) intent.putExtra(EXTRA_STOP_AT, stopAt);
        return PendingIntent.getForegroundService(context, START_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent stopIntent(Context context) {
        Intent intent = new Intent(context, PlaybackService.class)
                .setAction(ACTION_STOP);
        return PendingIntent.getService(context, STOP_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
