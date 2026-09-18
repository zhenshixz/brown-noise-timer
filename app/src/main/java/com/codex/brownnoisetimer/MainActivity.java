package com.codex.brownnoisetimer;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int PICK_FILE = 1;
    private static final int NOTIFICATION_PERMISSION = 2;
    private static final String[] DAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private final List<ScheduleRule> rules = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 2000);
        }
    };
    private TextView fileNameText;
    private TextView scheduleStatusText;
    private TextView nextStartText;
    private TextView playbackStatusText;
    private TextView permissionStatusText;
    private LinearLayout rulesContainer;
    private Button permissionButton;
    private boolean hadExactPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(0xFFF8F7F4);
        getWindow().setNavigationBarColor(0xFFF8F7F4);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        fileNameText = findViewById(R.id.fileNameText);
        scheduleStatusText = findViewById(R.id.scheduleStatusText);
        nextStartText = findViewById(R.id.nextStartText);
        playbackStatusText = findViewById(R.id.playbackStatusText);
        permissionStatusText = findViewById(R.id.permissionStatusText);
        rulesContainer = findViewById(R.id.rulesContainer);
        permissionButton = findViewById(R.id.permissionButton);
        hadExactPermission = Scheduler.canScheduleExact(this);
        rules.addAll(SettingsStore.rules(this));
        renderRules();
        refreshFileName();

        findViewById(R.id.chooseFileButton).setOnClickListener(v -> chooseFile());
        findViewById(R.id.addRuleButton).setOnClickListener(v -> editRule(-1));
        findViewById(R.id.testButton).setOnClickListener(v -> playNow());
        findViewById(R.id.stopButton).setOnClickListener(v -> stopNow());
        permissionButton.setOnClickListener(v -> requestExactPermission());
    }

    private void renderRules() {
        rulesContainer.removeAllViews();
        if (rules.isEmpty()) {
            TextView empty = label("还没有规则，点击“添加规则”开始。", 14, 0xFF756B63);
            empty.setPadding(dp(8), dp(16), 0, dp(16));
            rulesContainer.addView(empty);
            return;
        }
        for (int i = 0; i < rules.size(); i++) {
            final int index = i;
            ScheduleRule rule = rules.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(12));
            card.setBackgroundResource(R.drawable.bg_card);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.bottomMargin = dp(10);
            rulesContainer.addView(card, cardParams);

            LinearLayout heading = new LinearLayout(this);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(android.view.Gravity.CENTER_VERTICAL);
            card.addView(heading);
            TextView title = label(daysLabel(rule), 15, 0xFF392E27);
            title.setTypeface(null, 1);
            heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            Switch toggle = new Switch(this);
            toggle.setChecked(rule.enabled);
            toggle.setContentDescription("规则 " + (i + 1) + " 启用");
            heading.addView(toggle);
            String action = rule.startEnabled ? "开始 " + time(rule.startMinutes) : "";
            if (rule.stopEnabled) {
                if (!action.isEmpty()) action += "  →  ";
                action += "停止 " + time(rule.stopMinutes);
            }
            if (rule.startEnabled && rule.stopEnabled && rule.stopMinutes < rule.startMinutes) {
                action += "（次日）";
            }
            TextView detail = label(action, 17, rule.enabled ? 0xFF302B27 : 0xFF938C82);
            detail.setTypeface(null, 1);
            detail.setPadding(0, dp(9), 0, dp(8));
            card.addView(detail);

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            Button edit = textButton("编辑规则");
            Button delete = textButton("删除");
            actions.addView(edit, new LinearLayout.LayoutParams(-2, dp(40)));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(-2, dp(40));
            deleteParams.leftMargin = dp(12);
            actions.addView(delete, deleteParams);
            card.addView(actions);
            toggle.setOnClickListener(v -> {
                if (toggle.isChecked() && rule.startEnabled
                        && SettingsStore.fileUri(this).isEmpty()) {
                    toggle.setChecked(false);
                    toast("请先选择棕噪音 MP3");
                    return;
                }
                rule.enabled = toggle.isChecked();
                persistRules();
                renderRules();
            });
            edit.setOnClickListener(v -> editRule(index));
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("删除这条定时规则？")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (dialog, which) -> {
                        rules.remove(index);
                        persistRules();
                        renderRules();
                    }).show());
        }
    }

    private void editRule(int index) {
        long nextId = 1;
        for (ScheduleRule rule : rules) nextId = Math.max(nextId, rule.id + 1);
        ScheduleRule draft = index >= 0 ? rules.get(index).copy()
                : new ScheduleRule(nextId, true, true, 7 * 60, 10 * 60, 1, 7);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(10), dp(20), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);

        CheckBox startCheck = new CheckBox(this);
        startCheck.setText("定时开始播放");
        startCheck.setChecked(draft.startEnabled);
        body.addView(startCheck);
        Button startTime = editorButton(time(draft.startMinutes));
        body.addView(startTime, new LinearLayout.LayoutParams(-1, dp(48)));

        CheckBox stopCheck = new CheckBox(this);
        stopCheck.setText("定时停止播放");
        LinearLayout.LayoutParams stopCheckParams = new LinearLayout.LayoutParams(-1, -2);
        stopCheckParams.topMargin = dp(8);
        body.addView(stopCheck, stopCheckParams);
        stopCheck.setChecked(draft.stopEnabled);
        Button stopTime = editorButton(time(draft.stopMinutes));
        body.addView(stopTime, new LinearLayout.LayoutParams(-1, dp(48)));
        startTime.setEnabled(draft.startEnabled);
        stopTime.setEnabled(draft.stopEnabled);
        startCheck.setOnCheckedChangeListener((button, checked) -> {
            draft.startEnabled = checked;
            startTime.setEnabled(checked);
        });
        stopCheck.setOnCheckedChangeListener((button, checked) -> {
            draft.stopEnabled = checked;
            stopTime.setEnabled(checked);
        });
        startTime.setOnClickListener(v -> chooseTime(draft, true, startTime));
        stopTime.setOnClickListener(v -> chooseTime(draft, false, stopTime));

        TextView repeat = label("每周重复 · 包含首尾两天", 14, 0xFF51443A);
        repeat.setPadding(0, dp(18), 0, dp(8));
        body.addView(repeat);
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"每天", "工作日", "周末"};
        int[][] ranges = {{1, 7}, {1, 5}, {6, 7}};
        Button firstDay = editorButton("从 " + DAYS[draft.firstDay - 1]);
        Button lastDay = editorButton("到 " + DAYS[draft.lastDay - 1]);
        for (int i = 0; i < names.length; i++) {
            final int first = ranges[i][0];
            final int last = ranges[i][1];
            Button preset = smallButton(names[i]);
            LinearLayout.LayoutParams presetParams = new LinearLayout.LayoutParams(0, dp(42), 1);
            if (i > 0) presetParams.leftMargin = dp(6);
            presets.addView(preset, presetParams);
            preset.setOnClickListener(v -> {
                draft.firstDay = first;
                draft.lastDay = last;
                firstDay.setText("从 " + DAYS[first - 1]);
                lastDay.setText("到 " + DAYS[last - 1]);
            });
        }
        body.addView(presets);
        LinearLayout daysRow = new LinearLayout(this);
        daysRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams daysRowParams = new LinearLayout.LayoutParams(-1, -2);
        daysRowParams.topMargin = dp(8);
        body.addView(daysRow, daysRowParams);
        daysRow.addView(firstDay, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams lastParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        lastParams.leftMargin = dp(8);
        daysRow.addView(lastDay, lastParams);
        firstDay.setOnClickListener(v -> chooseDay(which -> {
            draft.firstDay = which + 1;
            firstDay.setText("从 " + DAYS[which]);
        }));
        lastDay.setOnClickListener(v -> chooseDay(which -> {
            draft.lastDay = which + 1;
            lastDay.setText("到 " + DAYS[which]);
        }));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "编辑规则" : "添加规则")
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存规则", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (!draft.startEnabled && !draft.stopEnabled) {
                        toast("请至少开启开始或停止中的一项");
                        return;
                    }
                    if (draft.startEnabled && draft.stopEnabled
                            && draft.startMinutes == draft.stopMinutes) {
                        toast("完整时段的开始和停止时间不能相同");
                        return;
                    }
                    if (draft.enabled && draft.startEnabled
                            && SettingsStore.fileUri(this).isEmpty()) {
                        toast("请先选择棕噪音 MP3");
                        return;
                    }
                    if (index >= 0) rules.set(index, draft);
                    else rules.add(draft);
                    persistRules();
                    renderRules();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private interface DayChoice { void selected(int index); }

    private void chooseDay(DayChoice choice) {
        new AlertDialog.Builder(this).setItems(DAYS, (dialog, which) -> choice.selected(which)).show();
    }

    private void chooseTime(ScheduleRule rule, boolean start, Button button) {
        int minutes = start ? rule.startMinutes : rule.stopMinutes;
        new TimePickerDialog(this, (picker, hour, minute) -> {
            int selected = hour * 60 + minute;
            if (start) rule.startMinutes = selected;
            else rule.stopMinutes = selected;
            button.setText(time(selected));
        }, minutes / 60, minutes % 60, true).show();
    }

    private static String time(int minutes) {
        return String.format(Locale.CHINA, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static String daysLabel(ScheduleRule rule) {
        if (rule.firstDay == 1 && rule.lastDay == 7) return "每天";
        if (rule.firstDay == 1 && rule.lastDay == 5) return "工作日";
        if (rule.firstDay == 6 && rule.lastDay == 7) return "周末";
        return DAYS[rule.firstDay - 1] + "至" + DAYS[rule.lastDay - 1];
    }

    private TextView label(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button smallButton(String value) {
        Button button = editorButton(value);
        button.setTextSize(13);
        return button;
    }

    private Button textButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13);
        button.setTextColor(0xFF865B40);
        button.setAllCaps(false);
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        button.setMinWidth(0);
        button.setMinHeight(0);
        return button;
    }

    private Button editorButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(0xFF704A36);
        button.setBackgroundResource(R.drawable.bg_secondary);
        button.setBackgroundTintList(ColorStateList.valueOf(0xFFF4EFE8));
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
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
        toast("已更换音频");
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

    private void refreshFileName() {
        String name = SettingsStore.get(this).getString(SettingsStore.FILE_NAME, "");
        fileNameText.setText(name.isEmpty() ? "尚未选择文件" : name);
    }

    private void persistRules() {
        SettingsStore.saveRules(this, rules);
        boolean enabled = SettingsStore.enabled(this);
        if (Scheduler.scheduleAll(this)) {
            toast("规则已保存并生效");
            if (enabled) requestNotificationPermission();
        } else {
            toast("规则已保存，请允许精确定时");
        }
        if (SettingsStore.get(this).getBoolean(SettingsStore.PLAYING, false)) {
            startService(new Intent(this, PlaybackService.class)
                    .setAction(PlaybackService.ACTION_REFRESH_SCHEDULE));
        }
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
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
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
        int count = 0;
        for (ScheduleRule rule : rules) if (rule.enabled) count++;
        scheduleStatusText.setText("已启用 " + count + " 条规则");
        if (!enabled) {
            nextStartText.setText("下次开始 · 暂无");
        } else if (!exact) {
            nextStartText.setText("下次开始 · 等待精确定时授权");
        } else {
            long now = System.currentTimeMillis();
            long start = Scheduler.nextStartMillis(this, now);
            if (start == Long.MAX_VALUE) {
                nextStartText.setText("下次开始 · 暂无（仅有停止规则）");
            } else {
                String at = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M月d日 E HH:mm", Locale.CHINA));
                nextStartText.setText("下次开始 · " + at);
            }
        }
        permissionStatusText.setText(enabled && !exact
                ? "精确定时权限未开启，规则暂时无法执行。" : "");
        permissionStatusText.setVisibility(enabled && !exact ? View.VISIBLE : View.GONE);
        permissionButton.setVisibility(enabled && !exact ? View.VISIBLE : View.GONE);
        SharedPreferences prefs = SettingsStore.get(this);
        String error = prefs.getString(SettingsStore.LAST_ERROR, "");
        if (!error.isEmpty()) playbackStatusText.setText("播放状态 · " + error);
        else if (prefs.getBoolean(SettingsStore.PLAYING, false))
            playbackStatusText.setText("播放状态 · 正在播放");
        else playbackStatusText.setText("播放状态 · 未播放");
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
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, 2000);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
