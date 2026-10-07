package com.wikirace.websocket;

import com.wikirace.game.runtime.RoomRegistry;
import com.wikirace.game.service.GameService;
import com.wikirace.wikipedia.FakeArticleProvider;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.*;
import static org.assertj.core.api.Assertions.*;

class StompGuardTest {
    final GameService games = new GameService(new RoomRegistry(),new FakeArticleProvider(),Clock.systemUTC(),2048);
    final StompGuard guard = new StompGuard(games);
    final com.wikirace.dto.GameDtos.SessionResponse player = games.create("Host");
    final String code = player.view().room().roomCode();
    void send(StompHeaderAccessor headers) { headers.setLeaveMutable(true); guard.preSend(MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders()),null); }
    StompHeaderAccessor connect(String token) {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT); headers.setNativeHeader("roomCode",code); headers.setNativeHeader("X-Player-Token",token); return headers;
    }
    @Test void validatesTokenBindsServerPrincipalAndRemovesSecretHeader() {
        var headers = connect(player.playerSessionToken()); send(headers);
        assertThat(headers.getUser()).isEqualTo(new RoomPrincipal(code,player.playerId()));
        assertThat(headers.getNativeHeader("X-Player-Token")).isNull();
    }
    @Test void wrongTokenIsRejectedAndNotRetainedInErrorMessage() {
        var headers = connect("SECRET"); assertThatThrownBy(() -> send(headers)).hasMessage("Unauthorized STOMP operation.");
        assertThat(headers.getNativeHeader("X-Player-Token")).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"/topic/rooms/OTHER2","/topic/rooms/*","/topic/rooms/**","/topic/rooms/","/queue/private","/topic/rooms/ABC234/extra"})
    void unauthorizedDestinationsAreRejected(String destination) {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE); headers.setUser(new RoomPrincipal(code,player.playerId())); headers.setDestination(destination);
        assertThatThrownBy(() -> send(headers)).isInstanceOf(org.springframework.messaging.MessageDeliveryException.class);
    }
    @Test void ownRoomSubscriptionIsAllowedButClientSendIsNeverAllowed() {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE); headers.setUser(new RoomPrincipal(code,player.playerId())); headers.setDestination("/topic/rooms/"+code); send(headers);
        headers = StompHeaderAccessor.create(StompCommand.SEND); headers.setUser(new RoomPrincipal(code,player.playerId())); headers.setDestination("/topic/rooms/"+code);
        var forbidden = headers; assertThatThrownBy(() -> send(forbidden)).isInstanceOf(org.springframework.messaging.MessageDeliveryException.class);
    }
    @Test void missingIdentityCannotSubscribe() {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE); headers.setDestination("/topic/rooms/"+code);
        assertThatThrownBy(() -> send(headers)).isInstanceOf(org.springframework.messaging.MessageDeliveryException.class);
    }
    @ParameterizedTest @ValueSource(strings={"roomCode", "X-Player-Token"})
    void duplicateConnectCredentialsAreRejectedAndSecretHeadersRemoved(String name) {
        var headers = connect(player.playerSessionToken());
        headers.addNativeHeader(name, name.equals("roomCode") ? code : "extra-secret");
        assertThatThrownBy(() -> send(headers)).hasMessage("Unauthorized STOMP operation.");
        assertThat(headers.getNativeHeader("X-Player-Token")).isNull();
        assertThat(headers.getUser()).isNull();
    }
    @Test void clientSuppliedPrincipalCannotOverrideAuthenticatedIdentity() {
        var headers = connect(player.playerSessionToken());
        headers.setUser(new RoomPrincipal(code, java.util.UUID.randomUUID()));
        assertThatThrownBy(() -> send(headers)).hasMessage("Unauthorized STOMP operation.");
        assertThat(headers.getNativeHeader("X-Player-Token")).isNull();
    }
}
