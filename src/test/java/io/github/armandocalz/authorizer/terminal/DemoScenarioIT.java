package io.github.armandocalz.authorizer.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.armandocalz.authorizer.PostgresTestcontainer;
import io.github.armandocalz.authorizer.account.CreditAccountRepository;
import io.github.armandocalz.authorizer.card.CardRepository;
import io.github.armandocalz.authorizer.card.PanTokenizer;
import io.github.armandocalz.authorizer.demo.DemoCards;
import io.github.armandocalz.authorizer.emv.EmvCryptography;
import io.github.armandocalz.authorizer.host.IsoServerConfiguration.IsoServerLifecycle;
import io.github.armandocalz.authorizer.network.IsoClient;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** The guided demo, run against the host with the demo profile: every step must pass, twice in a row. */
@SpringBootTest(properties = "authorizer.iso.port=0")
@ActiveProfiles("demo")
@Import(PostgresTestcontainer.class)
class DemoScenarioIT {

    @Autowired IsoServerLifecycle server;
    @Autowired EmvCryptography emv;
    @Autowired CardRepository cards;
    @Autowired CreditAccountRepository accounts;
    @Autowired PanTokenizer tokenizer;

    /** One card, used by both runs: its counter keeps increasing, like a real chip's. */
    private final ChipCounter chipCounter = ChipCounter.inMemory();

    private boolean runDemo(ByteArrayOutputStream output) throws Exception {
        try (Terminal terminal = new Terminal(
                IsoClient.connect("localhost", server.port(), Duration.ofSeconds(5)), emv, chipCounter, DemoCards.CURRENCY)) {
            return new DemoScenario(terminal, new PrintStream(output, true, StandardCharsets.UTF_8)).run();
        }
    }

    @Test
    void everyStepGetsTheExpectedResponseAndTheDemoCanBeRepeated() throws Exception {
        for (int run = 1; run <= 2; run++) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            boolean passed = runDemo(output);

            String report = output.toString(StandardCharsets.UTF_8);
            assertThat(passed).as("run %d:%n%s", run, report).isTrue();
            assertThat(report).contains("0 failed").doesNotContain("✗");
        }

        var account = cards.findByToken(tokenizer.token(DemoCards.ACTIVE_PAN))
                .flatMap(card -> accounts.findById(card.accountId())).orElseThrow();
        assertThat(account.availableMinor()).as("every approval was reversed").isEqualTo(DemoCards.CREDIT_LIMIT_MINOR);
    }
}
