package io.github.armandocalz.authorizer.demo;

import java.time.YearMonth;

/**
 * Cards loaded by the {@code demo} profile and used by the terminal simulator. All numbers are
 * well-known public test PANs (they pass the Luhn check but belong to no real account).
 */
public final class DemoCards {

    public static final String CURRENCY = "484"; // MXN
    public static final long CREDIT_LIMIT_MINOR = 1_000_000; // 10,000.00

    public static final String ACTIVE_PAN = "4111111111111111";
    public static final YearMonth ACTIVE_EXPIRY = YearMonth.of(2030, 12);

    public static final String BLOCKED_PAN = "5555555555554444";
    public static final YearMonth BLOCKED_EXPIRY = YearMonth.of(2030, 12);

    public static final String EXPIRED_PAN = "4000056655665556";
    public static final YearMonth EXPIRED_EXPIRY = YearMonth.of(2024, 12);

    private DemoCards() {
    }
}
