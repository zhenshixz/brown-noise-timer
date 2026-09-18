package com.codex.brownnoisetimer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/** One weekly rule. Days refer to the start day for a paired time period. */
final class ScheduleRule {
    final long id;
    boolean enabled = true;
    boolean startEnabled;
    boolean stopEnabled;
    int startMinutes;
    int stopMinutes;
    int firstDay;
    int lastDay;

    ScheduleRule(long id, boolean startEnabled, boolean stopEnabled,
                 int startMinutes, int stopMinutes, int firstDay, int lastDay) {
        this.id = id;
        this.startEnabled = startEnabled;
        this.stopEnabled = stopEnabled;
        this.startMinutes = startMinutes;
        this.stopMinutes = stopMinutes;
        this.firstDay = firstDay;
        this.lastDay = lastDay;
    }

    ScheduleRule copy() {
        ScheduleRule result = new ScheduleRule(id, startEnabled, stopEnabled,
                startMinutes, stopMinutes, firstDay, lastDay);
        result.enabled = enabled;
        return result;
    }

    boolean appliesOn(LocalDate date) {
        int day = date.getDayOfWeek().getValue();
        return firstDay <= lastDay
                ? day >= firstDay && day <= lastDay
                : day >= firstDay || day <= lastDay;
    }

    long nextStartAfter(long afterMillis, ZoneId zone) {
        if (!enabled || !startEnabled) return Long.MAX_VALUE;
        LocalDate today = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate();
        for (int offset = 0; offset < 8; offset++) {
            LocalDate date = today.plusDays(offset);
            if (!appliesOn(date)) continue;
            long candidate = at(date, startMinutes, zone);
            if (candidate > afterMillis) return candidate;
        }
        return Long.MAX_VALUE;
    }

    long nextStopAfter(long afterMillis, ZoneId zone) {
        if (!enabled || !stopEnabled) return Long.MAX_VALUE;
        LocalDate today = Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate();
        // A paired period may have started yesterday and stop after midnight today.
        int firstOffset = startEnabled && stopMinutes <= startMinutes ? -1 : 0;
        for (int offset = firstOffset; offset < 8; offset++) {
            LocalDate ruleDate = today.plusDays(offset);
            if (!appliesOn(ruleDate)) continue;
            LocalDate stopDate = startEnabled && stopMinutes <= startMinutes
                    ? ruleDate.plusDays(1) : ruleDate;
            long candidate = at(stopDate, stopMinutes, zone);
            if (candidate > afterMillis) return candidate;
        }
        return Long.MAX_VALUE;
    }

    private static long at(LocalDate date, int minutes, ZoneId zone) {
        return LocalDateTime.of(date, LocalTime.of(minutes / 60, minutes % 60))
                .atZone(zone).toInstant().toEpochMilli();
    }
}
