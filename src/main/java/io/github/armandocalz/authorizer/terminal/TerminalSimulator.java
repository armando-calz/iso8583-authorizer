package io.github.armandocalz.authorizer.terminal;

import io.github.armandocalz.authorizer.demo.DemoCards;
import io.github.armandocalz.authorizer.emv.EmvCryptography;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.network.IsoClient;
import io.github.armandocalz.authorizer.terminal.Terminal.Exchange;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Command-line terminal for the authorization host.
 *
 * <pre>
 * demo                                run the guided scenario against a host with the demo profile
 * echo                                send a 0800 echo test
 * purchase AMOUNT [--chip] [--card PAN --expiry YYMM]
 * </pre>
 *
 * Host and port come from AUTHORIZER_HOST / AUTHORIZER_PORT (default localhost:8583). The chip uses
 * the test issuer key from AUTHORIZER_EMV_IMK_AC, the same default as the host, and keeps its
 * transaction counter in AUTHORIZER_TERMINAL_STATE (default ~/.iso8583-terminal/atc).
 */
public final class TerminalSimulator {

    private static final String DEFAULT_TEST_IMK = "0123456789ABCDEFFEDCBA9876543210";

    private TerminalSimulator() {
    }

    public static void main(String[] args) throws Exception {
        List<String> arguments = List.of(args);
        String command = arguments.isEmpty() ? "demo" : arguments.getFirst();
        String host = env("AUTHORIZER_HOST", "localhost");
        int port = Integer.parseInt(env("AUTHORIZER_PORT", "8583"));
        EmvCryptography cardKeys = new EmvCryptography(HexFormat.of().parseHex(env("AUTHORIZER_EMV_IMK_AC", DEFAULT_TEST_IMK)));

        System.out.printf("Terminal %s -> %s:%d%n", Terminal.TERMINAL_ID_VALUE, host, port);
        ChipCounter atc = ChipCounter.persistedIn(Path.of(env("AUTHORIZER_TERMINAL_STATE",
                Path.of(System.getProperty("user.home"), ".iso8583-terminal", "atc").toString())));
        try (Terminal terminal = new Terminal(
                IsoClient.connect(host, port, Duration.ofSeconds(10)), cardKeys, atc, DemoCards.CURRENCY)) {
            switch (command) {
                case "demo" -> System.exit(new DemoScenario(terminal, System.out).run() ? 0 : 1);
                case "echo" -> print(terminal.send(terminal.echo().build()));
                case "purchase" -> purchase(terminal, arguments);
                default -> {
                    System.err.println("Unknown command: " + command + " (use demo, echo or purchase)");
                    System.exit(2);
                }
            }
        }
    }

    private static void purchase(Terminal terminal, List<String> arguments) throws Exception {
        if (arguments.size() < 2) {
            throw new IllegalArgumentException("Usage: purchase AMOUNT [--chip] [--card PAN --expiry YYMM]");
        }
        long amountMinor = new BigDecimal(arguments.get(1)).movePointRight(2).longValueExact();
        String pan = option(arguments, "--card").orElse(DemoCards.ACTIVE_PAN);
        var expiry = option(arguments, "--expiry")
                .map(yymm -> java.time.YearMonth.of(2000 + Integer.parseInt(yymm.substring(0, 2)), Integer.parseInt(yymm.substring(2))))
                .orElse(DemoCards.ACTIVE_EXPIRY);
        IsoMessage request = arguments.contains("--chip")
                ? terminal.chipPurchase(pan, expiry, amountMinor).build()
                : terminal.purchase(pan, expiry, amountMinor).build();
        Exchange exchange = terminal.send(request);
        print(exchange);
        if (arguments.contains("--chip")) {
            System.out.println("ARPC valid: " + terminal.issuerAuthenticated(exchange));
        }
    }

    private static void print(Exchange exchange) {
        System.out.println("-> " + exchange.request());
        System.out.println("<- " + exchange.response() + "  (" + exchange.elapsed().toMillis() + " ms)");
    }

    private static Optional<String> option(List<String> arguments, String name) {
        int i = arguments.indexOf(name);
        return i >= 0 && i + 1 < arguments.size() ? Optional.of(arguments.get(i + 1)) : Optional.empty();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
