# Clock

Four-in-one time app: analog clock, alarm list, stopwatch and countdown
timer, all in one dark-themed interface with a shared page shell.

Layout:
- `DynamicPlugin.java` - PluginContract entry point, tab bar, single Handler drives all pages
- `Page.java` / `Ui.java` - page abstraction and shared UI helpers
- `ClockPage.java` / `AnalogClockView.java` - clock page + custom analog clock view
- `AlarmPage.java` / `Alarm.java` / `Beeper.java` - alarm list (persisted, snooze) + audio cue
- `StopwatchPage.java` / `TimerPage.java` - stopwatch and countdown pages

Reference points: multi-page app shell, custom analog view drawing,
state persistence without androidx, Handler-driven ticking.
