package com.wikirace.config;

import java.util.Arrays;
import java.util.Map;
import com.wikirace.websocket.StompGuard;
import com.wikirace.websocket.SubscriptionReceipts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.server.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final StompGuard guard;
    private final SubscriptionReceipts receipts;
    private final String[] origins;
    private final com.wikirace.websocket.SocketSessions sockets;
    public WebSocketConfiguration(StompGuard guard, SubscriptionReceipts receipts, com.wikirace.websocket.SocketSessions sockets, @Value("${app.websocket.allowed-origins}") String origins) {
        this.sockets = sockets;
        this.guard = guard;
        this.receipts = receipts;
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toArray(String[]::new);
        if (this.origins.length == 0 || Arrays.asList(this.origins).contains("*")) throw new IllegalArgumentException("Exact WebSocket origins are required.");
    }
    @Bean(name = {"stompHeartbeatScheduler", "taskScheduler"}) public ThreadPoolTaskScheduler stompHeartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("stomp-heartbeat-"); return scheduler;
    }
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(origins).addInterceptors(new HandshakeInterceptor() {
            public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Map<String,Object> attributes) {
                return request.getURI().getRawQuery() == null;
            }
            public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler, Exception exception) {}
        });
    }
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic/rooms/").setHeartbeatValue(new long[]{10000,10000}).setTaskScheduler(stompHeartbeatScheduler());
        registry.setPreservePublishOrder(true);
    }
    public void configureClientInboundChannel(ChannelRegistration registration) { registration.interceptors(guard, receipts); }
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(sockets::decorate);
        registration.setMessageSizeLimit(8192).setSendBufferSizeLimit(524288).setSendTimeLimit(10000);
    }
}
