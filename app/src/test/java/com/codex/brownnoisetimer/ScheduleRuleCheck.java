package com.codex.brownnoisetimer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class ScheduleRuleCheck {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static void main(String[] args) {
        ScheduleRule weekendWrap = new ScheduleRule(1, true, false, 420, 600, 5, 1);
        require(weekendWrap.appliesOn(LocalDate.of(2026, 9, 18)), "Friday included");
        require(weekendWrap.appliesOn(LocalDate.of(2026, 9, 21)), "Monday included");
        require(!weekendWrap.appliesOn(LocalDate.of(2026, 9, 22)), "Tuesday excluded");
        require(weekendWrap.nextStopAfter(at(2026, 9, 18, 0), ZONE) == Long.MAX_VALUE,
                "start-only has no paired stop");

        ScheduleRule overnight = new ScheduleRule(2, true, true, 22 * 60, 2 * 60, 5, 5);
        require(overnight.nextStopAfter(at(2026, 9, 18, 23), ZONE)
                == at(2026, 9, 19, 2), "Friday period stops Saturday");
        require(overnight.nextStartAfter(at(2026, 9, 18, 23), ZONE)
                == at(2026, 9, 25, 22), "next weekly Friday start");

        ScheduleRule stopOnly = new ScheduleRule(3, false, true, 0, 9 * 60, 1, 5);
        require(stopOnly.nextStartAfter(at(2026, 9, 18, 0), ZONE) == Long.MAX_VALUE,
                "stop-only has no start");
        require(stopOnly.nextStopAfter(at(2026, 9, 18, 10), ZONE)
                == at(2026, 9, 21, 9), "stop-only follows its own calendar day");
        overnight.enabled = false;
        require(overnight.nextStartAfter(at(2026, 9, 18, 0), ZONE) == Long.MAX_VALUE,
                "disabled rule has no start");
        require(overnight.nextStopAfter(at(2026, 9, 18, 0), ZONE) == Long.MAX_VALUE,
                "disabled rule has no stop");
        System.out.println("ScheduleRuleCheck passed");
    }

    private static long at(int year, int month, int day, int hour) {
        return LocalDateTime.of(year, month, day, hour, 0).atZone(ZONE)
                .toInstant().toEpochMilli();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
