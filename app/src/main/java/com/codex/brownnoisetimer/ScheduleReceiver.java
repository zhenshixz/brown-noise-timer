package com.codex.brownnoisetimer;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Scheduler.ACTION_START.equals(action)) {
            long stopAt = intent.getLongExtra(Scheduler.EXTRA_STOP_AT, 0);
            if (SettingsStore.enabled(context)
                    && !SettingsStore.fileUri(context).isEmpty()
                    && stopAt > System.currentTimeMillis()) {
                try {
                    Intent play = new Intent(context, PlaybackService.class)
                            .setAction(PlaybackService.ACTION_PLAY)
                            .putExtra(Scheduler.EXTRA_STOP_AT, stopAt);
                    context.startForegroundService(play);
                } catch (RuntimeException error) {
                    SettingsStore.get(context).edit()
                            .putString(SettingsStore.LAST_ERROR, "定时启动失败：" + error.getClass().getSimpleName())
                            .apply();
                }
            }
            Scheduler.scheduleNextStart(context);
        } else if (Scheduler.ACTION_STOP.equals(action)) {
            context.stopService(new Intent(context, PlaybackService.class));
            Scheduler.scheduleNextStop(context);
        } else if (Scheduler.ACTION_STOP_NOW.equals(action)) {
            context.stopService(new Intent(context, PlaybackService.class));
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            Scheduler.scheduleAll(context);
        }
    }
}

