package io.github.armandocalz.authorizer.terminal;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AMOUNT;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AUTHORIZATION_ID_RESPONSE;

import io.github.armandocalz.authorizer.demo.DemoCards;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.terminal.Terminal.Exchange;
import java.io.IOException;
import java.io.PrintStream;
import java.time.YearMonth;

/**
 * A guided walk through what the host does, against a running host with the {@code demo}
 * profile. Every step states the response it expects and is marked as passed or failed. Every
 * approval is reversed by the end, so the demo can be run any number of times.
 */
public final class DemoScenario {

    private final Terminal terminal;
    private final PrintStream out;
    private int passed;
    private int failed;

    public DemoScenario(Terminal terminal, PrintStream out) {
        this.terminal = terminal;
        this.out = out;
    }

    /** @return whether every step got the expected response */
    public boolean run() throws IOException {
        String pan = DemoCards.ACTIVE_PAN;
        YearMonth expiry = DemoCards.ACTIVE_EXPIRY;

        section("Network management");
        check("0800 echo test", "00", terminal.send(terminal.echo().build()));

        section("Magnetic stripe purchase, retransmission and reversal");
        Exchange purchase = check("Purchase 250.00 MXN", "00", terminal.send(terminal.purchase(pan, expiry, 25_000).build()));
        Exchange copy = check("Same purchase retransmitted (no answer arrived)", "00", terminal.send(purchase.request()));
        expect("  ...gets the original authorization code, holds nothing again",
                authCode(copy).equals(authCode(purchase)));
        check("0400 reversal of the purchase", "00", terminal.send(terminal.reversalOf(purchase.request()).build()));
        check("0400 reversal repeated", "00", terminal.send(terminal.reversalOf(purchase.request()).build()));

        section("EMV chip purchase");
        Exchange chip = check("Chip purchase 1,234.50 MXN", "00",
                terminal.send(terminal.chipPurchase(pan, expiry, 123_450).build()));
        expect("  ...response carries a valid ARPC (issuer authenticated)", terminal.issuerAuthenticated(chip));
        check("Same chip data with field 4 changed to 9,999.00 (not what the chip signed)", "05",
                terminal.send(terminal.asNewTransaction(chip.request()).set(AMOUNT, "%012d".formatted(999_900)).build()));
        check("Captured cryptogram replayed as a new transaction", "05",
                terminal.send(terminal.asNewTransaction(chip.request()).build()));
        check("0400 reversal of the chip purchase", "00", terminal.send(terminal.reversalOf(chip.request()).build()));

        section("Declines");
        check("Purchase above the credit limit (20,000.00)", "51", terminal.send(terminal.purchase(pan, expiry, 2_000_000).build()));
        check("Blocked card", "62",
                terminal.send(terminal.purchase(DemoCards.BLOCKED_PAN, DemoCards.BLOCKED_EXPIRY, 1_000).build()));
        check("Expired card", "54",
                terminal.send(terminal.purchase(DemoCards.EXPIRED_PAN, DemoCards.EXPIRED_EXPIRY, 1_000).build()));
        check("Unknown card", "14", terminal.send(terminal.purchase("4242424242424242", expiry, 1_000).build()));

        section("Reversal arriving before its purchase");
        IsoMessage late = terminal.purchase(pan, expiry, 5_000).build();
        check("0400 for a purchase the host has not seen yet", "00", terminal.send(terminal.reversalOf(late).build()));
        check("The late purchase arrives: declined, nothing held", "05", terminal.send(late));

        out.printf("%n%d passed, %d failed%n", passed, failed);
        return failed == 0;
    }

    private void section(String title) {
        out.printf("%n%s%n", title);
    }

    private Exchange check(String description, String expectedCode, Exchange exchange) {
        boolean ok = exchange.responseCode().equals(expectedCode);
        report(ok, "%-2s %-4s %s  (%d ms)".formatted(exchange.responseCode(), exchange.response().mti(), description,
                exchange.elapsed().toMillis()) + (ok ? "" : "  [expected " + expectedCode + "]"));
        return exchange;
    }

    private void expect(String description, boolean condition) {
        report(condition, "        " + description.strip());
    }

    private void report(boolean ok, String line) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        out.println((ok ? "  ✓ " : "  ✗ ") + line);
    }

    private static String authCode(Exchange exchange) {
        return exchange.response().get(AUTHORIZATION_ID_RESPONSE).orElse("");
    }
}
