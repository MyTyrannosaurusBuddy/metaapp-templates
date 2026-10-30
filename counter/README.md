# Counter

A plus/minus counter. The count persists across app restarts (lineage-scoped prefs) and across feedback revisions.

## Adapting

- Change the starting value or the button layout freely.
- The count is stored in SharedPreferences `"counter"` under key `"count"`.
- Keep the root `MATCH_PARENT` and let the buttons size themselves.
