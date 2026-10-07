package com.wikirace.wikipedia;

import java.net.http.HttpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(WikipediaProperties.class)
public class WikipediaConfiguration {
    @Bean
    public MediaWikiClient mediaWikiClient(ObjectMapper mapper, WikipediaProperties properties) {
        var http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        return new MediaWikiClient(http, mapper, properties);
    }
}
