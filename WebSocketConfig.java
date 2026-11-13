package com.codeguard.config;
import com.codeguard.service.AdminSocket;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
@Configuration @EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final AdminSocket socket;
  public WebSocketConfig(AdminSocket socket) { this.socket = socket; }
  @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry r) { r.addHandler(socket, "/ws/admin"); }
}
