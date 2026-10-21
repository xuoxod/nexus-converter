package com.gmail.xuoxod.nexuscore.io;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Sovereign utility class for common file input/output operations.
 * Enforces null-byte rejection, sandboxed path resolution, and bounded reads.
 */
public final class FileUtils {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    public static final long DEFAULT_MAX_READ_BYTES = 64L * 1024L * 1024L; // 64 MiB

    private FileUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    private static Path validateSafePath(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty.");
        }
        if (filePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("File path contains illegal null byte.");
        }
        return Paths.get(filePath);
    }

    /**
     * Resolves a relative path against a sandbox root directory and rejects traversal outside the root.
     */
    public static Path resolveSafeChild(Path baseDir, String childPath) {
        if (baseDir == null || childPath == null || childPath.trim().isEmpty() || childPath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid base directory or child path.");
        }
        String lower = childPath.toLowerCase();
        if (lower.contains("%2e%2e")) {
            throw new SecurityException("Path traversal attempt rejected: " + childPath);
        }
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(childPath).normalize();
        if (!resolved.startsWith(normalizedBase)) {
            throw new SecurityException("Path traversal attempt escaped sandbox root: " + childPath);
        }
        return resolved;
    }

    public static String readFileToString(String filePath) throws IOException {
        return readFileToString(filePath, DEFAULT_CHARSET);
    }

    public static String readFileToString(String filePath, Charset charset) throws IOException {
        byte[] encoded = readFileToBytes(filePath);
        return new String(encoded, charset);
    }

    public static List<String> readLines(String filePath) throws IOException {
        return readLines(filePath, DEFAULT_CHARSET);
    }

    public static List<String> readLines(String filePath, Charset charset) throws IOException {
        Path path = validateSafePath(filePath);
        return Files.readAllLines(path, charset);
    }

    public static byte[] readFileToBytes(String filePath) throws IOException {
        return readFileToBytesBounded(filePath, DEFAULT_MAX_READ_BYTES);
    }

    public static byte[] readFileToBytesBounded(String filePath, long maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        Path path = validateSafePath(filePath);
        long size = Files.size(path);
        if (size > maxBytes) {
            throw new IOException("File size (" + size + " bytes) exceeds maximum allowed bound (" + maxBytes + " bytes)");
        }
        return Files.readAllBytes(path);
    }

    public static void writeFileFromString(String filePath, String content) throws IOException {
        writeFileFromString(filePath, content, DEFAULT_CHARSET, false);
    }

    public static void writeFileFromString(String filePath, String content, Charset charset) throws IOException {
        writeFileFromString(filePath, content, charset, false);
    }

    public static void writeFileFromString(String filePath, String content, Charset charset, boolean append)
            throws IOException {
        byte[] data = content.getBytes(charset);
        writeFileFromBytes(filePath, data, append);
    }

    public static void writeFileFromBytes(String filePath, byte[] data) throws IOException {
        writeFileFromBytes(filePath, data, false);
    }

    public static void writeFileFromBytes(String filePath, byte[] data, boolean append) throws IOException {
        Path path = validateSafePath(filePath);
        Path parentDir = path.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        StandardOpenOption[] options = append
                ? new StandardOpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND }
                : new StandardOpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING };

        Files.write(path, data, options);
    }

    public static boolean exists(String filePath) {
        if (filePath == null || filePath.trim().isEmpty() || filePath.indexOf('\0') >= 0) {
            return false;
        }
        return Files.exists(Paths.get(filePath));
    }

    public static boolean isFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty() || filePath.indexOf('\0') >= 0) {
            return false;
        }
        return Files.isRegularFile(Paths.get(filePath));
    }

    public static boolean isDirectory(String filePath) {
        if (filePath == null || filePath.trim().isEmpty() || filePath.indexOf('\0') >= 0) {
            return false;
        }
        return Files.isDirectory(Paths.get(filePath));
    }

    public static void createDirectories(String dirPath) throws IOException {
        Path path = validateSafePath(dirPath);
        Files.createDirectories(path);
    }

    public static boolean delete(String filePath) throws IOException {
        Path path = validateSafePath(filePath);
        return Files.deleteIfExists(path);
    }

    public static String getFileName(String filePath) {
        if (filePath == null || filePath.trim().isEmpty() || filePath.indexOf('\0') >= 0) {
            return "";
        }
        Path path = Paths.get(filePath);
        Path fileName = path.getFileName();
        return (fileName != null) ? fileName.toString() : "";
    }

    public static String getFileExtension(String filePath) {
        String fileName = getFileName(filePath);
        if (!fileName.isEmpty()) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
                return fileName.substring(dotIndex + 1).toLowerCase();
            }
        }
        return "";
    }
}

