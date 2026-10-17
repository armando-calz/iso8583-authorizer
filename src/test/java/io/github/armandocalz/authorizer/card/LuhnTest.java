package io.github.armandocalz.authorizer.card;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LuhnTest {

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111111", "5555555555554444", "378282246310005", "6011111111111117", "79927398713"})
    void acceptsValidNumbers(String pan) {
        assertThat(Luhn.isValid(pan)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"4111111111111112", "4111111111111121", "79927398710", "0", "4111 1111 1111 1111", "41111111A1111111"})
    void rejectsInvalidNumbers(String pan) {
        assertThat(Luhn.isValid(pan)).isFalse();
    }
}
