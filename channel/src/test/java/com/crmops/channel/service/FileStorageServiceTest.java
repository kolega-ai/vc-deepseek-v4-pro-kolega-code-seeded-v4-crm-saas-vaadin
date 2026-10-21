package com.crmops.channel.service;

import com.crmops.channel.config.ChannelProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The canonicalized-base-directory file resolution (one of the canonical
 * precision-baseline locations): stored names can never escape the storage root.
 */
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(new ChannelProperties(null, null, null, null,
                tempDir.toString(), 10_000_000, 30, 3000, "http://localhost:8080", false, null, null, null));
    }

    @Test
    void storeAndOpenRoundTrip() throws Exception {
        byte[] content = "hello document".getBytes(StandardCharsets.UTF_8);
        storage.store("abc-123.pdf", content);
        try (var in = storage.open("abc-123.pdf")) {
            assertArrayEquals(content, in.readAllBytes());
        }
    }

    @Test
    void traversalNamesAreRejected() {
        assertThrows(ValidationException.class, () -> storage.resolveWithinRoot("../etc/passwd"));
        assertThrows(ValidationException.class, () -> storage.resolveWithinRoot(".."));
        assertThrows(ValidationException.class, () -> storage.resolveWithinRoot("a/../../b"));
        assertThrows(ValidationException.class, () -> storage.resolveWithinRoot("a\\..\\b"));
        assertThrows(ValidationException.class, () -> storage.resolveWithinRoot("sub/dir/file.txt"));
    }

    @Test
    void resolvedPathStaysInsideRoot() {
        Path resolved = storage.resolveWithinRoot("fine-123.txt");
        assertTrue(resolved.startsWith(storage.canonicalRoot()));
    }
}
