package com.codex.brownnoisetimer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

public final class PlaybackService extends Service {
    static final String ACTION_PLAY = "com.codex.brownnoisetimer.PLAY";
    static final String ACTION_REFRESH_SCHEDULE = "com.codex.brownnoisetimer.REFRESH_SCHEDULE";
    private static final String CHANNEL_ID = "brown_noise_playback";
    private static final int NOTIFICATION_ID = 7;

    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private long stopAt;
    private boolean pausedForFocus;
    private final Handler stopHandler = new Handler(Looper.getMainLooper());
    private final Runnable timedStop = () -> {
        Scheduler.scheduleNextStop(this, Math.max(stopAt, System.currentTimeMillis()));
        stopSelf();
    };

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                "棕噪音播放", NotificationManager.IMPORTANCE_LOW));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH_SCHEDULE.equals(intent.getAction())) {
            if (player == null) {
                stopSelf();
            } else {
                long nextStop = Scheduler.nextStopMillis(this, System.currentTimeMillis());
                stopAt = nextStop == Long.MAX_VALUE ? 0 : nextStop;
                stopHandler.removeCallbacks(timedStop);
                if (stopAt > 0) {
                    stopHandler.postDelayed(timedStop,
                            Math.max(1, stopAt - System.currentTimeMillis()));
                }
            }
            return START_NOT_STICKY;
        }
        if (intent != null && Scheduler.ACTION_STOP.equals(intent.getAction())) {
            Log.i("BrownNoiseTimer", "Scheduled playback stop");
            Scheduler.scheduleNextStop(this, System.currentTimeMillis());
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent == null || !ACTION_PLAY.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean scheduled = intent.getBooleanExtra(Scheduler.EXTRA_SCHEDULED, false);
        long eventAt = intent.getLongExtra(Scheduler.EXTRA_EVENT_AT, 0);
        long now = System.currentTimeMillis();
        if (scheduled) {
            Scheduler.scheduleNextStart(this, Math.max(now, eventAt) + 1);
            Scheduler.scheduleNextStop(this, now);
            if (!SettingsStore.enabled(this) || !Scheduler.isCurrentRevision(this, intent)
                    || eventAt <= 0 || now - eventAt > 5 * 60_000L) {
                Log.i("BrownNoiseTimer", "Skipping outdated scheduled start");
                if (player == null) stopSelf();
                return START_NOT_STICKY;
            }
        } else if (intent.getLongExtra(Scheduler.EXTRA_STOP_AT, 0) > 0) {
            // An alarm from the previous app version should be replaced, not played.
            Scheduler.scheduleAll(this);
            if (player == null) stopSelf();
            return START_NOT_STICKY;
        }
        stopAt = scheduled ? intent.getLongExtra(Scheduler.EXTRA_STOP_AT, 0) : 0;
        stopHandler.removeCallbacks(timedStop);
        Log.i("BrownNoiseTimer", "Playback service started, scheduled=" + scheduled);
        if (stopAt > 0 && now >= stopAt) {
            if (player == null) stopSelf();
            return START_NOT_STICKY;
        }
        try {
            Notification notification = notification("正在准备播放");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            if (player == null) playSelectedFile();
            if (stopAt > 0) {
                stopHandler.postDelayed(timedStop,
                        Math.max(1, stopAt - System.currentTimeMillis()));
            }
        } catch (Exception error) {
            fail("播放失败：" + error.getClass().getSimpleName());
        }
        return START_NOT_STICKY;
    }

    private void playSelectedFile() throws Exception {
        releasePlayer();
        String uriString = SettingsStore.fileUri(this);
        if (uriString.isEmpty()) {
            fail("尚未选择音频文件");
            return;
        }

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        audioManager = getSystemService(AudioManager.class);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this::onAudioFocusChanged)
                .build();
        if (audioManager == null
                || audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail("其他应用正在占用音频，稍后再试");
            return;
        }

        MediaPlayer next = new MediaPlayer();
        player = next;
        next.setAudioAttributes(attributes);
        next.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        next.setDataSource(this, Uri.parse(uriString));
        next.setOnPreparedListener(ready -> {
            if (ready != player) return;
            if (stopAt > 0 && System.currentTimeMillis() >= stopAt) {
                stopSelf();
                return;
            }
            ready.start();
            Log.i("BrownNoiseTimer", "Playback started");
            SettingsStore.get(this).edit()
                    .putBoolean(SettingsStore.PLAYING, true)
                    .remove(SettingsStore.LAST_ERROR)
                    .apply();
            getSystemService(NotificationManager.class)
                    .notify(NOTIFICATION_ID, notification("正在播放"));
        });
        next.setOnCompletionListener(done -> stopSelf());
        next.setOnErrorListener((failed, what, extra) -> {
            fail("文件播放失败，请重新选择音频");
            return true;
        });
        next.prepareAsync();
    }

    private void onAudioFocusChanged(int change) {
        if (player == null) return;
        if (change == AudioManager.AUDIOFOCUS_LOSS) {
            stopSelf();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            if (player.isPlaying()) {
                player.pause();
                pausedForFocus = true;
            }
        } else if (change == AudioManager.AUDIOFOCUS_GAIN && pausedForFocus) {
            if (stopAt == 0 || System.currentTimeMillis() < stopAt) {
                player.start();
                pausedForFocus = false;
            } else {
                stopSelf();
            }
        }
    }

    private Notification notification(String status) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openIntent = PendingIntent.getActivity(this, 201, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, ScheduleReceiver.class).setAction(Scheduler.ACTION_STOP_NOW);
        PendingIntent stopIntent = PendingIntent.getBroadcast(this, 202, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String name = SettingsStore.get(this).getString(SettingsStore.FILE_NAME, "棕噪音");
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_app)
                .setContentTitle(status + " · 棕噪音")
                .setContentText(name)
                .setContentIntent(openIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause, "停止播放", stopIntent).build())
                .build();
    }

    private void fail(String message) {
        Log.e("BrownNoiseTimer", message);
        SettingsStore.get(this).edit()
                .putBoolean(SettingsStore.PLAYING, false)
                .putString(SettingsStore.LAST_ERROR, message)
                .apply();
        stopSelf();
    }

    private void releasePlayer() {
        if (player != null) {
            player.reset();
            player.release();
            player = null;
        }
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
        pausedForFocus = false;
    }

    @Override
    public void onDestroy() {
        stopHandler.removeCallbacks(timedStop);
        releasePlayer();
        SettingsStore.get(this).edit().putBoolean(SettingsStore.PLAYING, false).apply();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
