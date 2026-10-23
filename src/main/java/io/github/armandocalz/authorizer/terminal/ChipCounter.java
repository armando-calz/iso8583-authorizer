package io.github.armandocalz.authorizer.terminal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * The simulated chip's application transaction counter (ATC). A real chip keeps it in its own
 * memory and never repeats it, and the host rejects a repeated one as a replay (ADR 0006). This
 * one persists it in a file when given a path, so runs of the terminal keep increasing it.
 */
public final class ChipCounter {

    private final Path file;
    private int last;

    private ChipCounter(Path file, int last) {
        this.file = file;
        this.last = last;
    }

    /** Kept in memory only, starting from a clock-derived value. */
    public static ChipCounter inMemory() {
        return new ChipCounter(null, initialValue());
    }

    /** Persisted in {@code file}, created on first use. */
    public static ChipCounter persistedIn(Path file) {
        try {
            int last = Files.exists(file) ? Integer.parseInt(Files.readString(file).strip()) : initialValue();
            return new ChipCounter(file, last);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the chip counter from " + file, e);
        }
    }

    public synchronized int next() {
        if (last >= 0xFFFF) {
            throw new IllegalStateException("ATC exhausted: a real card would have to be replaced");
        }
        last++;
        if (file != null) {
            try {
                Files.createDirectories(file.toAbsolutePath().getParent());
                Files.writeString(file, Integer.toString(last));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot save the chip counter to " + file, e);
            }
        }
        return last;
    }

    /** Leaves room for many runs before 65535, and is likely above what an earlier run used. */
    private static int initialValue() {
        return (int) (Instant.now().getEpochSecond() / 60 % 30_000);
    }
}
