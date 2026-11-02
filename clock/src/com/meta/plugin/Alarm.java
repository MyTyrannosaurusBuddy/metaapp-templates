package com.meta.plugin;

/** One alarm entry. */
class Alarm {
    long id;
    int hour;
    int minute;
    boolean enabled = true;
    String label = "";
    long snoozeUntil = 0L;
    String lastFiredKey = "";

    Alarm(long id, int hour, int minute, String label) {
        this.id = id;
        this.hour = hour;
        this.minute = minute;
        this.label = label == null ? "" : label;
    }

    String timeText() {
        return (hour < 10 ? "0" : "") + hour + ":" + (minute < 10 ? "0" : "") + minute;
    }

    String serialize() {
        return id + "|" + hour + "|" + minute + "|" + (enabled ? 1 : 0) + "|"
                + label.replace("|", " ").replace("\n", " ") + "|" + lastFiredKey;
    }

    static Alarm parse(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 5) {
            return null;
        }
        try {
            Alarm a = new Alarm(Long.parseLong(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]), p[4]);
            a.enabled = "1".equals(p[3]);
            if (p.length > 5) {
                a.lastFiredKey = p[5];
            }
            return a;
        } catch (Exception e) {
            return null;
        }
    }
}
