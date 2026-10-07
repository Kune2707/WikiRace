package com.wikirace.config;

import java.time.Clock;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class GameConfiguration {
    @Bean
    public com.wikirace.game.runtime.GameLimits gameLimits(
            @org.springframework.beans.factory.annotation.Value("${app.game.movement-per-second:10}") int movement,
            @org.springframework.beans.factory.annotation.Value("${app.game.lobby-grace:5m}") java.time.Duration grace,
            @org.springframework.beans.factory.annotation.Value("${app.game.finished-retention:30m}") java.time.Duration retention) {
        return new com.wikirace.game.runtime.GameLimits(movement, grace, retention);
    }
    @Bean
    public Clock gameClock() { return Clock.systemUTC(); }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictTextFields() {
        return builder -> builder.postConfigurer(mapper -> mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
    }
}
