package com.gmail.xuoxod.nexusbridge;

import static org.junit.jupiter.api.Assertions.*;

import com.gmail.xuoxod.nexuscore.io.FileUtils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class NativeConverterTest {

    @TempDir
    Path tempDir;

    /**
     * Generates a valid 44.1kHz stereo 16-bit PCM WAV file in memory (0.1 seconds of 440Hz sine wave)
     * so tests are 100% self-contained without requiring external binary assets.
     */
    private static byte[] generateSineWavBytes() throws IOException {
        int sampleRate = 44100;
        int channels = 2;
        int numSamples = sampleRate / 10; // 0.1s
        int dataSize = numSamples * channels * 2;
        ByteBuffer buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes());
        buf.putInt(36 + dataSize);
        buf.put("WAVE".getBytes());
        buf.put("fmt ".getBytes());
        buf.putInt(16); // PCM chunk size
        buf.putShort((short) 1); // PCM format
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(sampleRate * channels * 2);
        buf.putShort((short) (channels * 2));
        buf.putShort((short) 16);
        buf.put("data".getBytes());
        buf.putInt(dataSize);
        for (int i = 0; i < numSamples; i++) {
            short sample = (short) (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 16000);
            buf.putShort(sample);
            buf.putShort(sample);
        }
        return buf.array();
    }

    @Test
    void backendVersion_isNotEmpty() {
        String v = NativeConverter.backendVersion();
        assertNotNull(v);
        assertFalse(v.isBlank());
    }

    @Test
    void probe_and_convert_validAudioFixture_succeeds() throws Exception {
        Path wavFile = tempDir.resolve("synthetic-tone.wav");
        Path mp3File = tempDir.resolve("output.mp3");
        Files.write(wavFile, generateSineWavBytes());

        assertTrue(NativeConverter.probe(wavFile.toString()), "Expected probe to succeed for valid PCM WAV");
        int rc = NativeConverter.performConversion(wavFile.toString(), mp3File.toString());
        assertEquals(0, rc, "Expected conversion to MP3 to succeed");
        assertTrue(Files.exists(mp3File) && Files.size(mp3File) > 100, "Expected non-empty MP3 output file");
    }

    @Test
    void probe_missingFile_returnsFalse() {
        boolean ok = NativeConverter.probe("/path/that/does/not/exist.mp4");
        assertFalse(ok);
    }

    @Test
    void adversarial_jniBoundaryAndFileUtilsDefense() throws Exception {
        assertFalse(NativeConverter.probe(null));
        assertFalse(NativeConverter.probe(""));
        assertFalse(NativeConverter.probe("   "));
        assertFalse(NativeConverter.probe("bad\0path.mp4"));
        assertEquals(2, NativeConverter.performConversion(null, "out.mp3"));
        assertEquals(2, NativeConverter.performConversion("in.mp4", "out\0.mp3"));

        // Corrupted / non-media input file must be rejected cleanly without panic
        Path corruptFile = tempDir.resolve("corrupt.mp4");
        Files.writeString(corruptFile, "NOT_A_MEDIA_HEADER_1234567890");
        assertFalse(NativeConverter.probe(corruptFile.toString()));

        // Path traversal & null byte defense in FileUtils
        assertThrows(SecurityException.class, () -> FileUtils.resolveSafeChild(tempDir, "../../etc/passwd"));
        assertThrows(SecurityException.class, () -> FileUtils.resolveSafeChild(tempDir, "%2e%2e/secret"));
        assertThrows(IllegalArgumentException.class, () -> FileUtils.readFileToString("bad\0file.txt"));
        assertThrows(IOException.class, () -> FileUtils.readFileToBytesBounded(corruptFile.toString(), 5));

        // ANSI log scrubbing in Main
        String scrubbed = Main.scrubAnsi("\u001B[31mInjected\u001B[0m\r\nForgedLine\0");
        assertFalse(scrubbed.contains("\u001B"));
        assertFalse(scrubbed.contains("\n"));
    }
}

