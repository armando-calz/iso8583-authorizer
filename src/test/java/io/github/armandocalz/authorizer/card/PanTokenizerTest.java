package io.github.armandocalz.authorizer.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PanTokenizerTest {

    private static final byte[] KEY = new byte[32];

    @Test
    void isDeterministicAndHidesThePan() {
        PanTokenizer tokenizer = new PanTokenizer(KEY);

        String token = tokenizer.token("4111111111111111");

        assertThat(token).hasSize(64).matches("[0-9a-f]+").doesNotContain("4111111111111111");
        assertThat(tokenizer.token("4111111111111111")).isEqualTo(token);
        assertThat(tokenizer.token("5555555555554444")).isNotEqualTo(token);
    }

    @Test
    void dependsOnTheKey() {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;

        assertThat(new PanTokenizer(otherKey).token("4111111111111111"))
                .isNotEqualTo(new PanTokenizer(KEY).token("4111111111111111"));
    }

    @Test
    void requiresA256BitKey() {
        assertThatThrownBy(() -> new PanTokenizer(new byte[16])).isInstanceOf(IllegalArgumentException.class);
    }
}
