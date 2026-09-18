package com.codex.brownnoisetimer;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final int NOTIFICATION_PERMISSION = 2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000);
        }
    };
    private TextView fileNameText;
    private TextView scheduleStatusText;
    private TextView playbackStatusText;
    private TextView permissionStatusText;
    private Button startTimeButton;
    private Button stopTimeButton;
    private Button permissionButton;
    private Button disableButton;
    private CheckBox weekdaysCheckBox;
    private int startMinutes;
    private int stopMinutes;
    private boolean hadExactPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(0xFFF8F6F2);
        getWindow().setNavigationBarColor(0xFFF8F6F2);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        fileNameText = findViewById(R.id.fileNameText);
        scheduleStatusText = findViewById(R.id.scheduleStatusText);
        playbackStatusText = findViewById(R.id.playbackStatusText);
        permissionStatusText = findViewById(R.id.permissionStatusText);
        startTimeButton = findViewById(R.id.startTimeButton);
        stopTimeButton = findViewById(R.id.stopTimeButton);
        permissionButton = findViewById(R.id.permissionButton);
        disableButton = findViewById(R.id.disableButton);
        weekdaysCheckBox = findViewById(R.id.weekdaysCheckBox);
        hadExactPermission = Scheduler.canScheduleExact(this);

        startMinutes = SettingsStore.startMinutes(this);
        stopMinutes = SettingsStore.stopMinutes(this);
        weekdaysCheckBox.setChecked(SettingsStore.weekdaysOnly(this));
        updateTimeButtons();
        refreshFileName();

        findViewById(R.id.chooseFileButton).setOnClickListener(v -> chooseFile());
        startTimeButton.setOnClickListener(v -> chooseTime(true));
        stopTimeButton.setOnClickListener(v -> chooseTime(false));
        findViewById(R.id.saveButton).setOnClickListener(v -> saveSchedule());
        findViewById(R.id.testButton).setOnClickListener(v -> playNow());
        findViewById(R.id.stopButton).setOnClickListener(v -> stopNow());
        permissionButton.setOnClickListener(v -> requestExactPermission());
        disableButton.setOnClickListener(v -> disableSchedule());
    }

    private void chooseFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_FILE || resultCode != RESULT_OK || data == null
                || data.getData() == null) return;
        Uri uri = data.getData();
        String name = displayName(uri);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            toast("请选择 MP3 文件");
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException error) {
            toast("文件访问授权失败，请重新选择");
            return;
        }
        SettingsStore.get(this).edit()
                .putString(SettingsStore.FILE_URI, uri.toString())
                .putString(SettingsStore.FILE_NAME, name)
                .remove(SettingsStore.LAST_ERROR)
                .apply();
        refreshFileName();
        toast("已选择音频，请保存定时");
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) return cursor.getString(column);
            }
        } catch (Exception ignored) { }
        return uri.getLastPathSegment() == null ? "音频.mp3" : uri.getLastPathSegment();
    }

    private void chooseTime(boolean start) {
        int minutes = start ? startMinutes : stopMinutes;
        new TimePickerDialog(this, (picker, hour, minute) -> {
            if (start) startMinutes = hour * 60 + minute;
            else stopMinutes = hour * 60 + minute;
            updateTimeButtons();
        }, minutes / 60, minutes % 60, true).show();
    }

    private void updateTimeButtons() {
        startTimeButton.setText(String.format(Locale.CHINA, "%02d:%02d",
                startMinutes / 60, startMinutes % 60));
        stopTimeButton.setText(String.format(Locale.CHINA, "%02d:%02d",
                stopMinutes / 60, stopMinutes % 60));
    }

    private void refreshFileName() {
        String name = SettingsStore.get(this).getString(SettingsStore.FILE_NAME, "");
        fileNameText.setText(name.isEmpty() ? "尚未选择文件" : name);
    }

    private void saveSchedule() {
        if (SettingsStore.fileUri(this).isEmpty()) {
            toast("请先选择棕噪音 MP3");
            return;
        }
        if (startMinutes == stopMinutes) {
            toast("开始和停止时间不能相同");
            return;
        }
        stopService(new Intent(this, PlaybackService.class));
        SettingsStore.get(this).edit()
                .putInt(SettingsStore.START_MINUTES, startMinutes)
                .putInt(SettingsStore.STOP_MINUTES, stopMinutes)
                .putBoolean(SettingsStore.WEEKDAYS_ONLY, weekdaysCheckBox.isChecked())
                .putBoolean(SettingsStore.ENABLED, true)
                .remove(SettingsStore.LAST_ERROR)
                .apply();
        if (Scheduler.scheduleAll(this)) {
            toast("每日定时已开启");
            requestNotificationPermission();
        } else {
            toast("还需允许精确定时");
            requestExactPermission();
        }
        refreshStatus();
    }

    private void disableSchedule() {
        SettingsStore.get(this).edit().putBoolean(SettingsStore.ENABLED, false).apply();
        Scheduler.cancelAll(this);
        stopNow();
        toast("每日定时已暂停");
        refreshStatus();
    }

    private void playNow() {
        if (SettingsStore.fileUri(this).isEmpty()) {
            toast("请先选择棕噪音 MP3");
            return;
        }
        requestNotificationPermission();
        try {
            startForegroundService(new Intent(this, PlaybackService.class)
                    .setAction(PlaybackService.ACTION_PLAY));
            toast("正在准备播放，请听一下媒体音量");
        } catch (RuntimeException error) {
            toast("启动失败：" + error.getClass().getSimpleName());
        }
    }

    private void stopNow() {
        stopService(new Intent(this, PlaybackService.class));
        SettingsStore.get(this).edit().putBoolean(SettingsStore.PLAYING, false).apply();
        refreshStatus();
    }

    private void requestExactPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION);
        }
    }

    private void refreshStatus() {
        boolean enabled = SettingsStore.enabled(this);
        boolean exact = Scheduler.canScheduleExact(this);
        if (!enabled) {
            scheduleStatusText.setText("定时未开启");
        } else if (!exact) {
            scheduleStatusText.setText("等待授权：精确定时尚未开启");
        } else {
            long next = Scheduler.nextStartMillis(this, System.currentTimeMillis());
            String time = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("M月d日 E HH:mm", Locale.CHINA));
            scheduleStatusText.setText("定时已开启 · 下次 " + time);
        }
        permissionStatusText.setText(enabled && !exact
                ? "请点击下方按钮，允许应用设置精确闹钟。" : "");
        permissionButton.setVisibility(enabled && !exact ? View.VISIBLE : View.GONE);
        disableButton.setVisibility(enabled ? View.VISIBLE : View.GONE);

        SharedPreferences prefs = SettingsStore.get(this);
        String error = prefs.getString(SettingsStore.LAST_ERROR, "");
        if (!error.isEmpty()) playbackStatusText.setText(error);
        else if (prefs.getBoolean(SettingsStore.PLAYING, false))
            playbackStatusText.setText("当前正在播放");
        else playbackStatusText.setText("当前未播放");
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean exact = Scheduler.canScheduleExact(this);
        if (!hadExactPermission && exact && SettingsStore.enabled(this)) {
            Scheduler.scheduleAll(this);
            requestNotificationPermission();
        }
        hadExactPermission = exact;
        refreshStatus();
        handler.postDelayed(refresh, 2000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
