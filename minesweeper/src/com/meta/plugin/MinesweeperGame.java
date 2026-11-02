package com.meta.plugin;

import java.util.Random;

/**
 * Pure game logic for Minesweeper. No Android dependencies.
 */
public class MinesweeperGame {

    public static final int STATUS_PLAYING = 0;
    public static final int STATUS_WON = 1;
    public static final int STATUS_LOST = 2;

    private final int rows;
    private final int cols;
    private final int mineTotal;

    private final boolean[] mine;
    private final boolean[] revealed;
    private final boolean[] flagged;
    private final int[] adjacent;
    private final int[] stack;
    private final Random random = new Random();

    private int revealedCount;
    private int flagCount;
    private int status = STATUS_PLAYING;
    private boolean minesPlaced;
    private int exploded = -1;

    public MinesweeperGame(int rows, int cols, int mineTotal) {
        this.rows = rows;
        this.cols = cols;
        int n = rows * cols;
        this.mineTotal = Math.max(1, Math.min(mineTotal, n - 1));
        mine = new boolean[n];
        revealed = new boolean[n];
        flagged = new boolean[n];
        adjacent = new int[n];
        stack = new int[n * 8 + 16];
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public int getMineTotal() {
        return mineTotal;
    }

    public int getStatus() {
        return status;
    }

    public boolean isMinesPlaced() {
        return minesPlaced;
    }

    public int getMinesRemaining() {
        return mineTotal - flagCount;
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    public boolean isRevealed(int r, int c) {
        return inBounds(r, c) && revealed[r * cols + c];
    }

    public boolean isFlagged(int r, int c) {
        return inBounds(r, c) && flagged[r * cols + c];
    }

    public boolean isMine(int r, int c) {
        return inBounds(r, c) && mine[r * cols + c];
    }

    public boolean isExploded(int r, int c) {
        return inBounds(r, c) && exploded == r * cols + c;
    }

    public int getAdjacent(int r, int c) {
        return inBounds(r, c) ? adjacent[r * cols + c] : 0;
    }

    private void placeMines(int firstIndex) {
        int n = rows * cols;
        boolean[] forbidden = new boolean[n];
        forbidden[firstIndex] = true;
        // Keep the first click and its neighbours (if possible) free of mines.
        if (n - 9 >= mineTotal) {
            int fr = firstIndex / cols;
            int fc = firstIndex % cols;
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = fr + dr;
                    int nc = fc + dc;
                    if (inBounds(nr, nc)) {
                        forbidden[nr * cols + nc] = true;
                    }
                }
            }
        }
        int placed = 0;
        while (placed < mineTotal) {
            int idx = random.nextInt(n);
            if (forbidden[idx] || mine[idx]) {
                continue;
            }
            mine[idx] = true;
            placed++;
        }
        for (int i = 0; i < n; i++) {
            adjacent[i] = countAdjacentMines(i);
        }
        minesPlaced = true;
    }

    private int countAdjacentMines(int index) {
        int r = index / cols;
        int c = index % cols;
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }
                int nr = r + dr;
                int nc = c + dc;
                if (inBounds(nr, nc) && mine[nr * cols + nc]) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Reveals a cell. Returns true when the state changed. */
    public boolean reveal(int r, int c) {
        if (status != STATUS_PLAYING || !inBounds(r, c)) {
            return false;
        }
        int idx = r * cols + c;
        if (flagged[idx] || revealed[idx]) {
            return false;
        }
        if (!minesPlaced) {
            placeMines(idx);
        }
        if (mine[idx]) {
            revealed[idx] = true;
            revealedCount++;
            exploded = idx;
            status = STATUS_LOST;
            return true;
        }

        int sp = 0;
        stack[sp++] = idx;
        while (sp > 0) {
            int cur = stack[--sp];
            if (revealed[cur] || flagged[cur] || mine[cur]) {
                continue;
            }
            revealed[cur] = true;
            revealedCount++;
            if (adjacent[cur] == 0) {
                int cr = cur / cols;
                int cc = cur % cols;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dr == 0 && dc == 0) {
                            continue;
                        }
                        int nr = cr + dr;
                        int nc = cc + dc;
                        if (!inBounds(nr, nc)) {
                            continue;
                        }
                        int ni = nr * cols + nc;
                        if (!revealed[ni] && !flagged[ni] && !mine[ni] && sp < stack.length) {
                            stack[sp++] = ni;
                        }
                    }
                }
            }
        }

        if (revealedCount >= rows * cols - mineTotal) {
            status = STATUS_WON;
            autoFlag();
        }
        return true;
    }

    private void autoFlag() {
        // Classic behaviour: on a win every remaining mine is flagged, so the
        // counter drops to zero and the board shows flags where the mines were.
        for (int i = 0; i < rows * cols; i++) {
            if (mine[i] && !flagged[i]) {
                flagged[i] = true;
                flagCount++;
            } else if (!mine[i] && flagged[i]) {
                flagged[i] = false;
                flagCount--;
            }
        }
    }

    /**
     * Chord: on an already revealed number whose neighbouring flag count matches
     * the number, open every remaining hidden neighbour. Returns true when the
     * board changed.
     */
    public boolean chord(int r, int c) {
        if (status != STATUS_PLAYING || !inBounds(r, c)) {
            return false;
        }
        int idx = r * cols + c;
        if (!revealed[idx] || mine[idx] || adjacent[idx] <= 0) {
            return false;
        }
        int flags = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (inBounds(nr, nc) && flagged[nr * cols + nc]) {
                    flags++;
                }
            }
        }
        if (flags != adjacent[idx]) {
            return false;
        }
        boolean changed = false;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (!inBounds(nr, nc)) {
                    continue;
                }
                int ni = nr * cols + nc;
                if (revealed[ni] || flagged[ni]) {
                    continue;
                }
                if (reveal(nr, nc)) {
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** Number of flags placed around a cell. */
    public int countFlagsAround(int r, int c) {
        int flags = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if (inBounds(nr, nc) && flagged[nr * cols + nc]) {
                    flags++;
                }
            }
        }
        return flags;
    }

    /** Toggles a flag. Returns true when the state changed. */
    public boolean toggleFlag(int r, int c) {
        if (status != STATUS_PLAYING || !inBounds(r, c)) {
            return false;
        }
        int idx = r * cols + c;
        if (revealed[idx]) {
            return false;
        }
        flagged[idx] = !flagged[idx];
        flagCount += flagged[idx] ? 1 : -1;
        if (flagCount < 0) {
            flagCount = 0;
        }
        return true;
    }
}
