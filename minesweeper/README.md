# Minesweeper

Full minesweeper game: 9x9 beginner board with mine reveal, auto-expand,
flagging (long-press), timer, and generated sound effects (explosion,
reveal ticks, win fanfare - synthesized PCM, no assets).

Layout:
- `DynamicPlugin.java` - PluginContract entry point + HUD (mine counter, timer, reset)
- `MinesweeperGame.java` - pure game logic (board state, reveal/expand, win check)
- `BoardView.java` - custom view drawing the board and handling touch
- `SoundFx.java` - synthesized sound effects

Reference points: custom View + onTouchEvent interaction, game logic
separated from rendering, AudioTrack PCM synthesis.
