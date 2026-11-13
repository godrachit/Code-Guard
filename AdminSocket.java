package com.codeguard.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Pushes live events (violations, start/submit) to connected admin browsers. */
@Component
public class AdminSocket extends TextWebSocketHandler {
  private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
  private final ObjectMapper om = new ObjectMapper();
  @Override public void afterConnectionEstablished(WebSocketSession s) { sessions.add(s); }
  @Override public void afterConnectionClosed(WebSocketSession s, CloseStatus st) { sessions.remove(s); }
  public void broadcast(Map<String, String> msg) {
    try {
      TextMessage m = new TextMessage(om.writeValueAsString(msg));
      for (WebSocketSession s : sessions) {
        synchronized (s) { if (s.isOpen()) { try { s.sendMessage(m); } catch (IOException ignored) {} } }
      }
    } catch (Exception ignored) {}
  }
}
