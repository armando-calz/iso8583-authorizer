package io.github.armandocalz.authorizer.terminal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChipCounterTest {

    @Test
    void neverRepeatsAcrossRunsWhenPersisted(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("state/atc");

        int first = ChipCounter.persistedIn(file).next();
        int second = ChipCounter.persistedIn(file).next();
        int third = ChipCounter.persistedIn(file).next();

        assertThat(second).isEqualTo(first + 1);
        assertThat(third).isEqualTo(first + 2);
        assertThat(Files.readString(file)).isEqualTo(Integer.toString(third));
    }

    @Test
    void increasesInMemory() {
        ChipCounter counter = ChipCounter.inMemory();

        assertThat(counter.next()).isLessThan(counter.next());
    }
}
