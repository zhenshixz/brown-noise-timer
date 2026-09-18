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
            Scheduler.scheduleAll(context);
        } else if (Scheduler.ACTION_STOP.equals(action)) {
            if (Scheduler.isCurrentRevision(context, intent)) {
                context.stopService(new Intent(context, PlaybackService.class));
                Scheduler.scheduleNextStop(context, Math.max(System.currentTimeMillis(),
                        intent.getLongExtra(Scheduler.EXTRA_EVENT_AT, 0)));
            } else {
                Scheduler.scheduleAll(context);
            }
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
