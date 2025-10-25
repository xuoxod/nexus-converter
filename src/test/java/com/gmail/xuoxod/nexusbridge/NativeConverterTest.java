package com.gmail.xuoxod.nexusbridge;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

public class NativeConverterTest {

    @Test
    void backendVersion_isNotEmpty() {
        String v = NativeConverter.backendVersion();
        assertNotNull(v);
        assertFalse(v.isBlank());
    }

    @Test
    void probe_validSample_returnsTrue() {
        Path sample = Path.of("native/assets/samples/dev-tone.mp4");
        // Auto-skip this test when the developer doesn't have large samples locally
        assumeTrue(Files.exists(sample), "Skipping: sample not present -> " + sample.toAbsolutePath());
        boolean ok = NativeConverter.probe(sample.toAbsolutePath().toString());
        assertTrue(ok, "Expected probe to succeed for valid sample");
    }

    @Test
    void probe_missingFile_returnsFalse() {
        boolean ok = NativeConverter.probe("/path/that/does/not/exist.mp4");
        assertFalse(ok);
    }
}
