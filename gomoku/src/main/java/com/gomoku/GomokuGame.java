package com.gomoku;

import java.util.*;

public class GomokuGame {

    public static final int BOARD_SIZE = 15;
    public static final int WIN_COUNT = 5;

    public enum Difficulty { EASY, NORMAL, HARD, EXPERT }
    public enum Mode { VS_AI, VS_HUMAN }

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int currentPlayer = 1; // 1=black, 2=white
    private int playerColor = 1;
    private int aiColor = 2;
    private int lastRow = -1;
    private int lastCol = -1;
    private List<int[]> winLine = null;
    private Difficulty difficulty = Difficulty.NORMAL;
    private Mode mode = Mode.VS_AI;
    private boolean playerIsWhite = false;
    private boolean gameOver = false;
    private String winner = null;

    // ── 初期化 ────────────────────────────────────────────────────────────────

    public void newGame(Difficulty diff, boolean playerWhite, Mode m) {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        difficulty = diff;
        mode = m;
        playerIsWhite = playerWhite;
        playerColor = playerWhite ? 2 : 1;
        aiColor     = playerWhite ? 1 : 2;
        currentPlayer = 1;
        lastRow = lastCol = -1;
        winLine = null;
        gameOver = false;
        winner = null;
    }

    // ── 手を置く（AI対戦用） ───────────────────────────────────────────────────

    public boolean playerMove(int row, int col) {
        if (gameOver || board[row][col] != 0 || currentPlayer != playerColor) return false;
        place(row, col, playerColor);
        if (checkWin(row, col, playerColor)) { endGame(playerColor); return true; }
        if (isDraw()) { endGame(0); return true; }
        currentPlayer = aiColor;
        return true;
    }

    // 手番チェックなしでAIに強制的に打たせる（後攻開始用）
    public void forceAiMove() {
        int[] pos = calcAiMove();
        place(pos[0], pos[1], aiColor);
        if (checkWin(pos[0], pos[1], aiColor)) { endGame(aiColor); }
        else if (isDraw()) { endGame(0); }
        else { currentPlayer = playerColor; }
    }

    public int[] aiMove() {
        if (gameOver || currentPlayer != aiColor) return null;
        int[] pos = calcAiMove();
        place(pos[0], pos[1], aiColor);
        if (checkWin(pos[0], pos[1], aiColor)) { endGame(aiColor); }
        else if (isDraw()) { endGame(0); }
        else { currentPlayer = playerColor; }
        return pos;
    }

    // ── 手を置く（2人対戦用） ──────────────────────────────────────────────────

    public boolean playerMoveAs(int row, int col, int player) {
        if (gameOver || board[row][col] != 0 || currentPlayer != player) return false;
        place(row, col, player);
        if (checkWin(row, col, player)) { endGame(player); return true; }
        if (isDraw()) { endGame(0); return true; }
        currentPlayer = (player == 1) ? 2 : 1;
        return true;
    }

    // ── 内部処理 ───────────────────────────────────────────────────────────────

    private void place(int row, int col, int color) {
        board[row][col] = color;
        lastRow = row;
        lastCol = col;
    }

    private void endGame(int winner) {
        gameOver = true;
        if (winner == 0) { this.winner = "draw"; }
        else { this.winner = (winner == 1) ? "black" : "white"; }
    }

    private boolean isDraw() {
        for (int[] row : board)
            for (int c : row) if (c == 0) return false;
        return true;
    }

    // ── 勝利判定 ───────────────────────────────────────────────────────────────

    public boolean checkWin(int row, int col, int player) {
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
        for (int[] d : dirs) {
            List<int[]> line = new ArrayList<>();
            line.add(new int[]{row, col});
            line.addAll(collect(row, col, d[0], d[1], player));
            line.addAll(collect(row, col, -d[0], -d[1], player));
            if (line.size() >= WIN_COUNT) {
                winLine = line;
                return true;
            }
        }
        return false;
    }

    private List<int[]> collect(int row, int col, int dr, int dc, int player) {
        List<int[]> list = new ArrayList<>();
        int r = row + dr, c = col + dc;
        while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE && board[r][c] == player) {
            list.add(new int[]{r, c});
            r += dr; c += dc;
        }
        return list;
    }

    public int countDir(int row, int col, int dr, int dc, int player) {
        int count = 0;
        int r = row + dr, c = col + dc;
        while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE && board[r][c] == player) {
            count++; r += dr; c += dc;
        }
        return count;
    }

    // ── AI ────────────────────────────────────────────────────────────────────

    private int[] calcAiMove() {
        int[][] b = copyBoard();
        return switch (difficulty) {
            case EASY   -> easyMove(b);
            case NORMAL -> minimax(b, 2, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
            case HARD   -> minimax(b, 3, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
            case EXPERT -> iterativeDeepening(b, 1000);
        };
    }

    private int[] easyMove(int[][] b) {
        List<int[]> cands = getCandidates(b);
        return cands.get(new Random().nextInt(cands.size()));
    }

    private int[] minimax(int[][] b, int depth, int alpha, int beta, boolean isMax) {
        List<int[]> cands = getCandidates(b);
        // score-sort for better pruning
        cands.sort((a, c2) -> Integer.compare(
            -quickScore(b, c2[0], c2[1], isMax ? aiColor : playerColor),
            -quickScore(b, a[0], a[1], isMax ? aiColor : playerColor)));

        int bestScore = isMax ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int[] bestMove = cands.get(0);

        for (int[] mv : cands) {
            int color = isMax ? aiColor : playerColor;
            b[mv[0]][mv[1]] = color;
            boolean win = checkWinBoard(b, mv[0], mv[1], color);
            int score;
            if (win) {
                score = isMax ? 1_000_000 + depth : -1_000_000 - depth;
            } else if (depth == 1 || getCandidates(b).isEmpty()) {
                score = evaluate(b);
            } else {
                int[] res = minimax(b, depth - 1, alpha, beta, !isMax);
                score = res[2];
            }
            b[mv[0]][mv[1]] = 0;

            if (isMax) {
                if (score > bestScore) { bestScore = score; bestMove = mv; }
                alpha = Math.max(alpha, score);
            } else {
                if (score < bestScore) { bestScore = score; bestMove = mv; }
                beta = Math.min(beta, score);
            }
            if (beta <= alpha) break;
        }
        return new int[]{bestMove[0], bestMove[1], bestScore};
    }

    private int[] iterativeDeepening(int[][] b, long ms) {
        long end = System.currentTimeMillis() + ms;
        int[] best = getCandidates(b).get(0);
        for (int d = 2; d <= 4; d++) {
            if (System.currentTimeMillis() >= end) break;
            best = minimax(b, d, Integer.MIN_VALUE, Integer.MAX_VALUE, true);
        }
        return best;
    }

    // ── 評価関数 ───────────────────────────────────────────────────────────────

    public int evaluate(int[][] b) {
        return score(b, aiColor) - (int)(score(b, playerColor) * 1.1);
    }

    private int score(int[][] b, int color) {
        int total = 0;
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (b[r][c] != color) continue;
                for (int[] d : dirs) {
                    int cnt = 1
                        + countDirBoard(b, r, c, d[0], d[1], color)
                        + countDirBoard(b, r, c, -d[0], -d[1], color);
                    boolean openA = isOpen(b, r, c, d[0], d[1], color, cnt);
                    boolean openB = isOpen(b, r, c, -d[0], -d[1], color, cnt);
                    total += lineScore(cnt, openA, openB);
                }
            }
        }
        return total;
    }

    private int lineScore(int cnt, boolean a, boolean b) {
        if (cnt >= 5) return 1_000_000;
        boolean both = a && b;
        return switch (cnt) {
            case 4 -> both ? 100_000 : 10_000;
            case 3 -> both ?   5_000 :    500;
            case 2 -> both ?     200 :     50;
            default -> both ?    10  :      5;
        };
    }

    private boolean isOpen(int[][] b, int r, int c, int dr, int dc, int color, int cnt) {
        int steps = 1;
        int rr = r, cc = c;
        while (steps < cnt) { rr += dr; cc += dc; steps++; }
        rr += dr; cc += dc;
        return rr >= 0 && rr < BOARD_SIZE && cc >= 0 && cc < BOARD_SIZE && b[rr][cc] == 0;
    }

    private int quickScore(int[][] b, int r, int c, int color) {
        b[r][c] = color;
        int s = score(b, color);
        b[r][c] = 0;
        return s;
    }

    // ── 候補手 ────────────────────────────────────────────────────────────────

    public List<int[]> getCandidates(int[][] b) {
        boolean hasStone = false;
        boolean[][] cand = new boolean[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (b[r][c] == 0) continue;
                hasStone = true;
                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE && b[nr][nc] == 0)
                            cand[nr][nc] = true;
                    }
                }
            }
        }
        if (!hasStone) return List.of(new int[]{7, 7});
        List<int[]> list = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++)
            for (int c = 0; c < BOARD_SIZE; c++)
                if (cand[r][c]) list.add(new int[]{r, c});
        return list;
    }

    // ── ユーティリティ ─────────────────────────────────────────────────────────

    private int[][] copyBoard() {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) copy[i] = board[i].clone();
        return copy;
    }

    private boolean checkWinBoard(int[][] b, int row, int col, int player) {
        int[][] dirs = {{0,1},{1,0},{1,1},{1,-1}};
        for (int[] d : dirs) {
            int cnt = 1
                + countDirBoard(b, row, col, d[0], d[1], player)
                + countDirBoard(b, row, col, -d[0], -d[1], player);
            if (cnt >= WIN_COUNT) return true;
        }
        return false;
    }

    private int countDirBoard(int[][] b, int row, int col, int dr, int dc, int player) {
        int count = 0;
        int r = row + dr, c = col + dc;
        while (r >= 0 && r < BOARD_SIZE && c >= 0 && c < BOARD_SIZE && b[r][c] == player) {
            count++; r += dr; c += dc;
        }
        return count;
    }

    // ── Getter ────────────────────────────────────────────────────────────────

    public int[][] getBoard()        { return board; }
    public int getCurrentPlayer()    { return currentPlayer; }
    public boolean isGameOver()      { return gameOver; }
    public String getWinner()        { return winner; }
    public List<int[]> getWinLine()  { return winLine; }
    public int getLastRow()          { return lastRow; }
    public int getLastCol()          { return lastCol; }
    public int getPlayerColor()      { return playerColor; }
    public int getAiColor()          { return aiColor; }
    public Mode getMode()            { return mode; }
    public Difficulty getDifficulty(){ return difficulty; }

    public int blackScore() {
        int s = 0;
        for (int[] row : board) for (int c : row) if (c == 1) s++;
        return s;
    }
    public int whiteScore() {
        int s = 0;
        for (int[] row : board) for (int c : row) if (c == 2) s++;
        return s;
    }
    public int moveCount() { return blackScore() + whiteScore(); }
}
