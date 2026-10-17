package io.github.armandocalz.authorizer;

import io.github.armandocalz.authorizer.card.PanTokenizer;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuthorizerConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PanTokenizer panTokenizer(@Value("${authorizer.pan-token-key}") String hexKey) {
        return new PanTokenizer(HexFormat.of().parseHex(hexKey));
    }
}
