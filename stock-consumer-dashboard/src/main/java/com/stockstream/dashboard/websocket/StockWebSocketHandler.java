package com.stockstream.dashboard.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StockWebSocketHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper;

    public StockWebSocketHandler(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcast(String type, Object data) {
        try {
            String json = mapper.writeValueAsString(Map.of("type", type, "data", data));
            TextMessage message = new TextMessage(json);
            sessions.removeIf(session -> {
                if (!session.isOpen()) return true;
                synchronized (session) {
                    try {
                        session.sendMessage(message);
                        return false;
                    } catch (IOException e) {
                        return true;
                    }
                }
            });
        } catch (JsonProcessingException ignored) {
        }
    }
}
