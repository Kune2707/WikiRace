package com.wikirace.websocket;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.*;
import org.springframework.messaging.support.*;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.stereotype.Component;

// The simple broker does not implement receipts. Confirm only after its subscription handler completes.
@Component
public class SubscriptionReceipts implements ExecutorChannelInterceptor {
    private final ObjectProvider<MessageChannel> outbound;
    public SubscriptionReceipts(@Qualifier("clientOutboundChannel") ObjectProvider<MessageChannel> outbound) { this.outbound = outbound; }
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception exception) {
        var input = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (exception != null || !(handler instanceof SimpleBrokerMessageHandler) || input == null || input.getCommand() != StompCommand.SUBSCRIBE) return;
        String receipt = input.getFirstNativeHeader("receipt");
        if (receipt == null || receipt.length() > 128) return;
        var output = StompHeaderAccessor.create(StompCommand.RECEIPT);
        output.setSessionId(input.getSessionId()); output.setNativeHeader("receipt-id", receipt);
        outbound.getObject().send(MessageBuilder.createMessage(new byte[0], output.getMessageHeaders()));
    }
}
