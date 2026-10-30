# MetaApk App Templates

Ready-made app source templates for [MetaApk](https://github.com/MyTyrannosaurusBuddy/MetaApk) - the tool that generates Android apps from natural language, compiles them on-device and runs them inside the host.

## Purpose

These templates give the generation agent a starting point it can **search and fetch** (via its `web_search` / `web_fetch` tools). Instead of writing a well-known app type from scratch, it can reuse a verified implementation and adapt it.

## Layout convention

One directory per template:

```
<template-name>/
  src/com/meta/plugin/
    DynamicPlugin.java      # entry point implementing PluginContract (required)
    ...                     # helper classes, one file each
  README.md                 # what the template is, notes on adapting it
```

The entry class is always `DynamicPlugin` implementing `PluginContract` (`onCreate(Context, ViewGroup, ApiBridge)` / `onDestroy()` / `getTitle()`), package `com.meta.plugin`. Helpers may use sub-packages.

## Templates

| Template | Status | Notes |
|---|---|---|
| [counter](counter/) | ✅ verified on device | plus/minus counter with persisted count |
| minesweeper | 🚧 pending | regenerate + verify, then add |
| clock | 🚧 pending | |
| stopwatch | 🚧 pending | |
| calculator | 🚧 pending | |

## Rules

- Pure Java UI (no XML), Java 8 syntax, standard android.*/java.* APIs plus com.google.gson only.
- Cell/grid sizes derive from the canvas, never hard-coded pixels.
- Templates must be built and run successfully in MetaApk before being committed.
