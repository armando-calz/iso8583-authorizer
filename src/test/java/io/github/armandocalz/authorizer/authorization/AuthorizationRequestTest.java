package io.github.armandocalz.authorizer.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoFormatException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class AuthorizationRequestTest {

    @Test
    void parsesTheFieldsTheRulesNeed() {
        AuthorizationRequest request = AuthorizationRequest.from(
                AuthorizationRequests.purchase("4111111111111111", YearMonth.of(2028, 12), 25_000).build());

        assertThat(request.pan()).isEqualTo("4111111111111111");
        assertThat(request.amountMinor()).isEqualTo(25_000);
        assertThat(request.expiry()).isEqualTo(YearMonth.of(2028, 12));
        assertThat(request.currency()).isEqualTo("484");
        assertThat(request.isPurchase()).isTrue();
    }

    @Test
    void rejectsMissingFieldsAndInvalidExpiryDates() {
        assertThatThrownBy(() -> AuthorizationRequest.from(AuthorizationRequests
                .purchase("4111111111111111", YearMonth.of(2028, 12), 1).remove(Iso8583Fields.TERMINAL_ID).build()))
                .isInstanceOf(IsoFormatException.class).hasMessageContaining("Field 41");
        assertThatThrownBy(() -> AuthorizationRequest.from(AuthorizationRequests
                .purchase("4111111111111111", YearMonth.of(2028, 12), 1).set(Iso8583Fields.EXPIRATION_DATE, "2813").build()))
                .isInstanceOf(IsoFormatException.class).hasMessageContaining("Field 14");
    }
}
