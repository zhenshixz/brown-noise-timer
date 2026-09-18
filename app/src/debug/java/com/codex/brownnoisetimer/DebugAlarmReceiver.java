package com.codex.brownnoisetimer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** ADB-only short alarm for testing delivery while the phone stays locked. */
public final class DebugAlarmReceiver extends BroadcastReceiver {
    private static final String ACTION_END_TEST = "com.codex.brownnoisetimer.END_TEST";

    @Override
    public void onReceive(Context context, Intent request) {
        if (ACTION_END_TEST.equals(request.getAction())) {
            context.getSystemService(AlarmManager.class).cancel(endIntent(context));
            context.stopService(new Intent(context, PlaybackService.class));
            Scheduler.scheduleAll(context);
            Log.i("BrownNoiseTimer", "Debug alarm ended; normal schedule restored");
            return;
        }
        long startAt = System.currentTimeMillis() + 20_000L;
        long stopAt = startAt + 120_000L;
        Intent play = new Intent(context, PlaybackService.class)
                .setAction(PlaybackService.ACTION_PLAY)
                .putExtra(Scheduler.EXTRA_SCHEDULED, true)
                .putExtra(Scheduler.EXTRA_EVENT_AT, startAt)
                .putExtra(Scheduler.EXTRA_STOP_AT, stopAt)
                .putExtra(Scheduler.EXTRA_REVISION, SettingsStore.revision(context));
        PendingIntent operation = PendingIntent.getForegroundService(context, 901, play,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent show = new Intent(context, MainActivity.class);
        PendingIntent showIntent = PendingIntent.getActivity(context, 902, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        context.getSystemService(AlarmManager.class).setAlarmClock(
                new AlarmManager.AlarmClockInfo(startAt, showIntent), operation);
        PendingIntent stopOperation = endIntent(context);
        context.getSystemService(AlarmManager.class).setAlarmClock(
                new AlarmManager.AlarmClockInfo(stopAt, showIntent), stopOperation);
        Log.i("BrownNoiseTimer", "Debug alarm scheduled at " + startAt);
    }

    private static PendingIntent endIntent(Context context) {
        Intent stop = new Intent(context, DebugAlarmReceiver.class).setAction(ACTION_END_TEST);
        return PendingIntent.getBroadcast(context, 903, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
