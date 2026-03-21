package com.gomoku;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    // roomId → [sessionA, sessionB]
    private final Map<String, List<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    // sessionId → roomId
    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();
    // roomId → GomokuGame
    private final Map<String, GomokuGame> games = new ConcurrentHashMap<>();
    // sessionId → playerColor (1=black 2=white)
    private final Map<String, Integer> playerColors = new ConcurrentHashMap<>();
    // sessionId → name
    private final Map<String, String> names = new ConcurrentHashMap<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Map<?, ?> msg = mapper.readValue(message.getPayload(), Map.class);
        String type = (String) msg.get("type");

        switch (type) {
            case "join"  -> handleJoin(session, msg);
            case "move"  -> handleMove(session, msg);
        }
    }

    private void handleJoin(WebSocketSession session, Map<?, ?> msg) throws Exception {
        String roomId = (String) msg.get("roomId");
        Object nameRaw = msg.get("name");
        String name = (nameRaw instanceof String s) ? s : "Player";
        names.put(session.getId(), name);

        rooms.computeIfAbsent(roomId, k -> Collections.synchronizedList(new ArrayList<>()));
        List<WebSocketSession> room = rooms.get(roomId);

        if (room.size() >= 2) {
            send(session, Map.of("type", "error", "message", "満室です"));
            return;
        }

        room.add(session);
        sessionRoom.put(session.getId(), roomId);

        if (room.size() == 1) {
            playerColors.put(session.getId(), 1); // 先攻=黒
            send(session, Map.of("type", "waiting", "message", "対戦相手を待っています…"));
        } else {
            // 2人揃ったのでゲーム開始
            playerColors.put(session.getId(), 2); // 後攻=白
            GomokuGame game = new GomokuGame();
            game.newGame(GomokuGame.Difficulty.NORMAL, false, GomokuGame.Mode.VS_HUMAN);
            games.put(roomId, game);

            WebSocketSession playerA = room.get(0);
            WebSocketSession playerB = room.get(1);

            send(playerA, Map.of("type", "start", "color", 1, "opponentName", names.get(playerB.getId())));
            send(playerB, Map.of("type", "start", "color", 2, "opponentName", names.get(playerA.getId())));
        }
    }

    private void handleMove(WebSocketSession session, Map<?, ?> msg) throws Exception {
        String roomId = sessionRoom.get(session.getId());
        if (roomId == null) return;
        GomokuGame game = games.get(roomId);
        if (game == null || game.isGameOver()) return;

        int row    = ((Number) msg.get("row")).intValue();
        int col    = ((Number) msg.get("col")).intValue();
        int player = playerColors.getOrDefault(session.getId(), 1);

        boolean moved = game.playerMoveAs(row, col, player);
        if (!moved) return;

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("type",          "update");
        update.put("board",         game.getBoard());
        update.put("currentPlayer", game.getCurrentPlayer());
        update.put("gameOver",      game.isGameOver());
        update.put("winner",        game.getWinner());
        update.put("winLine",       game.getWinLine() != null ? game.getWinLine().toArray(new int[0][]) : null);
        update.put("lastRow",       game.getLastRow());
        update.put("lastCol",       game.getLastCol());

        for (WebSocketSession s : rooms.get(roomId)) send(s, update);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = sessionRoom.remove(session.getId());
        if (roomId == null) return;
        List<WebSocketSession> room = rooms.get(roomId);
        if (room != null) {
            room.remove(session);
            for (WebSocketSession s : room) {
                if (s.isOpen()) send(s, Map.of("type", "opponent_left"));
            }
            if (room.isEmpty()) {
                rooms.remove(roomId);
                games.remove(roomId);
            }
        }
        playerColors.remove(session.getId());
        names.remove(session.getId());
    }

    private void send(WebSocketSession session, Object data) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(data)));
        }
    }
}
