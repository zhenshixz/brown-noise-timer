package com.codex.brownnoisetimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.ZoneId;

final class Scheduler {
    static final String ACTION_START = "com.codex.brownnoisetimer.START";
    static final String ACTION_STOP = "com.codex.brownnoisetimer.STOP";
    static final String ACTION_STOP_NOW = "com.codex.brownnoisetimer.STOP_NOW";
    static final String EXTRA_STOP_AT = "stop_at";
    static final String EXTRA_SCHEDULED = "scheduled";
    static final String EXTRA_EVENT_AT = "event_at";
    static final String EXTRA_REVISION = "schedule_revision";
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
        manager.cancel(startIntent(context, 0, 0));
        manager.cancel(stopIntent(context, 0));
        // Remove broadcast starts and service stops from older versions too.
        manager.cancel(PendingIntent.getBroadcast(context, START_REQUEST,
                new Intent(context, ScheduleReceiver.class).setAction(ACTION_START),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        manager.cancel(PendingIntent.getService(context, STOP_REQUEST,
                new Intent(context, PlaybackService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    static boolean scheduleAll(Context context) {
        cancelAll(context);
        if (!SettingsStore.enabled(context)) return true;
        if (!canScheduleExact(context)) return false;
        long now = System.currentTimeMillis();
        scheduleNextStart(context, now);
        scheduleNextStop(context, now);
        return true;
    }

    static void scheduleNextStart(Context context, long afterMillis) {
        if (!SettingsStore.enabled(context) || !canScheduleExact(context)) return;
        long startAt = nextStartMillis(context, afterMillis);
        if (startAt == Long.MAX_VALUE) return;
        long nextStop = nextStopMillis(context, startAt - 1);
        scheduleStart(context, startAt, nextStop == Long.MAX_VALUE ? 0 : nextStop);
    }

    static void scheduleNextStop(Context context, long afterMillis) {
        if (!SettingsStore.enabled(context) || !canScheduleExact(context)) return;
        long stopAt = nextStopMillis(context, afterMillis);
        if (stopAt != Long.MAX_VALUE) scheduleStop(context, stopAt);
    }

    static long nextStartMillis(Context context, long afterMillis) {
        long next = Long.MAX_VALUE;
        ZoneId zone = ZoneId.systemDefault();
        for (ScheduleRule rule : SettingsStore.rules(context)) {
            next = Math.min(next, rule.nextStartAfter(afterMillis, zone));
        }
        return next;
    }

    static long nextStopMillis(Context context, long afterMillis) {
        long next = Long.MAX_VALUE;
        ZoneId zone = ZoneId.systemDefault();
        for (ScheduleRule rule : SettingsStore.rules(context)) {
            next = Math.min(next, rule.nextStopAfter(afterMillis, zone));
        }
        return next;
    }

    static boolean isCurrentRevision(Context context, Intent intent) {
        return intent.getIntExtra(EXTRA_REVISION, -1) == SettingsStore.revision(context);
    }

    private static void scheduleStart(Context context, long at, long stopAt) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent(context)),
                startIntent(context, at, stopAt));
    }

    private static void scheduleStop(Context context, long at) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;
        manager.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showIntent(context)),
                stopIntent(context, at));
    }

    private static PendingIntent showIntent(Context context) {
        return PendingIntent.getActivity(context, SHOW_REQUEST,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent startIntent(Context context, long at, long stopAt) {
        Intent intent = new Intent(context, PlaybackService.class)
                .setAction(PlaybackService.ACTION_PLAY)
                .putExtra(EXTRA_SCHEDULED, true)
                .putExtra(EXTRA_EVENT_AT, at)
                .putExtra(EXTRA_STOP_AT, stopAt)
                .putExtra(EXTRA_REVISION, SettingsStore.revision(context));
        return PendingIntent.getForegroundService(context, START_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent stopIntent(Context context, long at) {
        Intent intent = new Intent(context, ScheduleReceiver.class)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_EVENT_AT, at)
                .putExtra(EXTRA_REVISION, SettingsStore.revision(context));
        return PendingIntent.getBroadcast(context, STOP_REQUEST, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
