package com.gomoku;

import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class GameController {

    @Autowired
    private JdbcTemplate jdbc;

    private GomokuGame getGame(HttpSession session) {
        GomokuGame game = (GomokuGame) session.getAttribute("game");
        if (game == null) {
            game = new GomokuGame();
            game.newGame(GomokuGame.Difficulty.NORMAL, false, GomokuGame.Mode.VS_AI);
            session.setAttribute("game", game);
        }
        return game;
    }

    @GetMapping("/state")
    public Map<String, Object> getState(HttpSession session) {
        return buildState(getGame(session));
    }

    @PostMapping("/new")
    public Map<String, Object> newGame(@RequestBody Map<String, String> body, HttpSession session) {
        String diff  = body.getOrDefault("difficulty", "NORMAL").toUpperCase();
        String color = body.getOrDefault("color", "black").toLowerCase();
        String modeS = body.getOrDefault("mode", "VS_AI").toUpperCase();
        boolean playerWhite = "white".equals(color);

        GomokuGame game = new GomokuGame();
        game.newGame(
            GomokuGame.Difficulty.valueOf(diff),
            playerWhite,
            GomokuGame.Mode.valueOf(modeS)
        );
        session.setAttribute("game", game);

        // 後攻（白）選択時はAIが先に黒を打つ
        if (playerWhite && game.getMode() == GomokuGame.Mode.VS_AI) {
            game.aiMove();
        }

        return buildState(game);
    }

    @PostMapping("/move")
    public Map<String, Object> move(@RequestBody Map<String, Integer> body, HttpSession session) {
        GomokuGame game = getGame(session);
        int row = body.get("row");
        int col = body.get("col");

        boolean moved = game.playerMove(row, col);
        Map<String, Object> res = buildState(game);
        res.put("moved", moved);

        if (moved && !game.isGameOver() && game.getMode() == GomokuGame.Mode.VS_AI) {
            int[] aiPos = game.aiMove();
            res = buildState(game);
            res.put("moved", true);
            if (aiPos != null) {
                res.put("lastRow", aiPos[0]);
                res.put("lastCol", aiPos[1]);
            }
        }
        return res;
    }

    @PostMapping("/name")
    public Map<String, Object> setName(@RequestBody Map<String, String> body, HttpSession session) {
        session.setAttribute("playerName", body.getOrDefault("name", "Player"));
        return Map.of("ok", true);
    }

    @PostMapping("/ranking/submit")
    public Map<String, Object> submitRanking(@RequestBody Map<String, Object> body, HttpSession session) {
        try {
            ensureTable();
            String name = (String) body.getOrDefault("name", session.getAttribute("playerName"));
            if (name == null || name.isBlank()) name = "Player";
            int score = ((Number) body.get("score")).intValue();
            String diff = ((String) body.get("difficulty")).toUpperCase();
            jdbc.update("INSERT INTO ranking(name, score, difficulty) VALUES(?,?,?)", name, score, diff);
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @GetMapping("/ranking")
    public Map<String, Object> getRanking() {
        try {
            ensureTable();
            Map<String, Object> result = new LinkedHashMap<>();
            for (String diff : new String[]{"EASY","NORMAL","HARD","EXPERT"}) {
                List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT name, score, created_at FROM ranking WHERE difficulty=? ORDER BY score ASC LIMIT 10", diff);
                result.put(diff, rows);
            }
            return result;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    @DeleteMapping("/ranking")
    public Map<String, Object> clearRanking() {
        try {
            jdbc.execute("DELETE FROM ranking");
            return Map.of("ok", true);
        } catch (Exception e) {
            return Map.of("ok", false, "error", e.getMessage());
        }
    }

    @GetMapping("/debug")
    public Map<String, Object> debug() {
        try {
            String ver = jdbc.queryForObject("SELECT version()", String.class);
            return Map.of("db", ver);
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    private Map<String, Object> buildState(GomokuGame game) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("board",         game.getBoard());
        m.put("currentPlayer", game.getCurrentPlayer());
        m.put("gameOver",      game.isGameOver());
        m.put("winner",        game.getWinner());
        m.put("winLine",       winLineToArray(game.getWinLine()));
        m.put("blackScore",    game.blackScore());
        m.put("whiteScore",    game.whiteScore());
        m.put("playerColor",   game.getPlayerColor());
        m.put("lastRow",       game.getLastRow());
        m.put("lastCol",       game.getLastCol());
        return m;
    }

    private int[][] winLineToArray(List<int[]> wl) {
        if (wl == null) return null;
        return wl.toArray(new int[0][]);
    }

    private void ensureTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS ranking (
                id         SERIAL PRIMARY KEY,
                name       VARCHAR(50) NOT NULL,
                score      INTEGER NOT NULL,
                difficulty VARCHAR(10) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )""");
    }
}
