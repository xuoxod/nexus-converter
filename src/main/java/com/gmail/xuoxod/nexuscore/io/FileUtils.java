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
 * Utility class for common file input/output operations.
 * Uses modern Java NIO (java.nio.file) where possible.
 * Methods typically throw IOException, which the caller must handle.
 */
public final class FileUtils {

    // Default charset for string operations
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    // Private constructor to prevent instantiation
    private FileUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // --- File Reading ---

    /**
     * Reads the entire content of a file into a single String using UTF-8 encoding.
     * Note: This loads the entire file into memory. Not suitable for very large
     * files.
     *
     * @param filePath The path to the file.
     * @return The content of the file as a String.
     * @throws IOException If an I/O error occurs reading from the file or the file
     *                     does not exist.
     */
    public static String readFileToString(String filePath) throws IOException {
        return readFileToString(filePath, DEFAULT_CHARSET);
    }

    /**
     * Reads the entire content of a file into a single String using the specified
     * charset.
     * Note: This loads the entire file into memory. Not suitable for very large
     * files.
     *
     * @param filePath The path to the file.
     * @param charset  The charset to use for decoding the file bytes.
     * @return The content of the file as a String.
     * @throws IOException If an I/O error occurs reading from the file or the file
     *                     does not exist.
     */
    public static String readFileToString(String filePath, Charset charset) throws IOException {
        byte[] encoded = readFileToBytes(filePath);
        return new String(encoded, charset);
    }

    /**
     * Reads all lines from a file into a List of Strings using UTF-8 encoding.
     *
     * @param filePath The path to the file.
     * @return A List containing all lines from the file.
     * @throws IOException If an I/O error occurs reading from the file or the file
     *                     does not exist.
     */
    public static List<String> readLines(String filePath) throws IOException {
        return readLines(filePath, DEFAULT_CHARSET);
    }

    /**
     * Reads all lines from a file into a List of Strings using the specified
     * charset.
     *
     * @param filePath The path to the file.
     * @param charset  The charset to use for decoding the file bytes.
     * @return A List containing all lines from the file.
     * @throws IOException If an I/O error occurs reading from the file or the file
     *                     does not exist.
     */
    public static List<String> readLines(String filePath, Charset charset) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readAllLines(path, charset);
    }

    /**
     * Reads the entire content of a file into a byte array.
     * Note: This loads the entire file into memory. Not suitable for very large
     * files.
     *
     * @param filePath The path to the file.
     * @return The content of the file as a byte array.
     * @throws IOException If an I/O error occurs reading from the file or the file
     *                     does not exist.
     */
    public static byte[] readFileToBytes(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readAllBytes(path);
    }

    // --- File Writing ---

    /**
     * Writes a String to a file using UTF-8 encoding.
     * If the file exists, it will be overwritten. If not, it will be created.
     * Parent directories will be created if they do not exist.
     *
     * @param filePath The path to the file.
     * @param content  The String content to write.
     * @throws IOException If an I/O error occurs writing to the file.
     */
    public static void writeFileFromString(String filePath, String content) throws IOException {
        writeFileFromString(filePath, content, DEFAULT_CHARSET, false);
    }

    /**
     * Writes a String to a file using the specified charset.
     * If the file exists, it will be overwritten. If not, it will be created.
     * Parent directories will be created if they do not exist.
     *
     * @param filePath The path to the file.
     * @param content  The String content to write.
     * @param charset  The charset to use for encoding the string.
     * @throws IOException If an I/O error occurs writing to the file.
     */
    public static void writeFileFromString(String filePath, String content, Charset charset) throws IOException {
        writeFileFromString(filePath, content, charset, false);
    }

    /**
     * Writes a String to a file using the specified charset, with an option to
     * append.
     * If the file does not exist, it will be created.
     * Parent directories will be created if they do not exist.
     *
     * @param filePath The path to the file.
     * @param content  The String content to write.
     * @param charset  The charset to use for encoding the string.
     * @param append   If true, the content will be appended to the file; otherwise,
     *                 the file will be overwritten.
     * @throws IOException If an I/O error occurs writing to the file.
     */
    public static void writeFileFromString(String filePath, String content, Charset charset, boolean append)
            throws IOException {
        byte[] data = content.getBytes(charset);
        writeFileFromBytes(filePath, data, append);
    }

    /**
     * Writes a byte array to a file.
     * If the file exists, it will be overwritten. If not, it will be created.
     * Parent directories will be created if they do not exist.
     *
     * @param filePath The path to the file.
     * @param data     The byte array content to write.
     * @throws IOException If an I/O error occurs writing to the file.
     */
    public static void writeFileFromBytes(String filePath, byte[] data) throws IOException {
        writeFileFromBytes(filePath, data, false);
    }

    /**
     * Writes a byte array to a file, with an option to append.
     * If the file does not exist, it will be created.
     * Parent directories will be created if they do not exist.
     *
     * @param filePath The path to the file.
     * @param data     The byte array content to write.
     * @param append   If true, the content will be appended to the file; otherwise,
     *                 the file will be overwritten.
     * @throws IOException If an I/O error occurs writing to the file.
     */
    public static void writeFileFromBytes(String filePath, byte[] data, boolean append) throws IOException {
        Path path = Paths.get(filePath);
        // Ensure parent directories exist
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

    // --- Other File/Path Helpers ---

    /**
     * Checks if a file or directory exists at the given path.
     *
     * @param filePath The path to check.
     * @return true if the path exists, false otherwise. Returns false if filePath
     *         is null or empty.
     */
    public static boolean exists(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        Path path = Paths.get(filePath);
        return Files.exists(path);
    }

    /**
     * Checks if the given path represents an existing regular file.
     *
     * @param filePath The path to check.
     * @return true if the path exists and is a regular file, false otherwise.
     */
    public static boolean isFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        Path path = Paths.get(filePath);
        return Files.isRegularFile(path); // Checks existence and that it's not a directory/link etc.
    }

    /**
     * Checks if the given path represents an existing directory.
     *
     * @param filePath The path to check.
     * @return true if the path exists and is a directory, false otherwise.
     */
    public static boolean isDirectory(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        Path path = Paths.get(filePath);
        return Files.isDirectory(path); // Checks existence and that it's a directory
    }

    /**
     * Creates a directory, including any necessary but nonexistent parent
     * directories.
     * Does nothing if the directory already exists.
     *
     * @param dirPath The path to the directory to create.
     * @throws IOException              If an I/O error occurs or the directory
     *                                  cannot be created (e.g., permissions, path
     *                                  is a file).
     * @throws IllegalArgumentException if dirPath is null or empty.
     */
    public static void createDirectories(String dirPath) throws IOException {
        if (dirPath == null || dirPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Directory path cannot be null or empty.");
        }
        Path path = Paths.get(dirPath);
        Files.createDirectories(path);
    }

    /**
     * Deletes a file or an empty directory.
     * Does not delete directories that are not empty.
     *
     * @param filePath The path to the file or empty directory to delete.
     * @return true if the file/directory was deleted by this operation, false if it
     *         did not exist.
     * @throws IOException              If an I/O error occurs (e.g., directory not
     *                                  empty, permissions).
     * @throws IllegalArgumentException if filePath is null or empty.
     */
    public static boolean delete(String filePath) throws IOException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("File path cannot be null or empty.");
        }
        Path path = Paths.get(filePath);
        return Files.deleteIfExists(path);
    }

    /**
     * Gets the filename component from a full path string.
     * e.g., "/path/to/file.txt" -> "file.txt"
     * e.g., "file.txt" -> "file.txt"
     *
     * @param filePath The full path string.
     * @return The filename, or an empty string if the path is null, empty, or ends
     *         with a separator.
     */
    public static String getFileName(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "";
        }
        Path path = Paths.get(filePath);
        Path fileName = path.getFileName();
        return (fileName != null) ? fileName.toString() : "";
    }

    /**
     * Gets the file extension from a filename or full path.
     * Returns the extension without the leading dot (e.g., "txt", "jpg").
     *
     * @param filePath The filename or full path string.
     * @return The file extension (lowercase), or an empty string if no extension is
     *         found or path is invalid.
     */
    public static String getFileExtension(String filePath) {
        String fileName = getFileName(filePath);
        if (!fileName.isEmpty()) {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < fileName.length() - 1) { // Ensure dot is not first or last char
                return fileName.substring(dotIndex + 1).toLowerCase();
            }
        }
        return "";
    }

    // TODO: Consider adding methods for:
    // - Deleting directories recursively (use Files.walkFileTree)
    // - Copying files/directories (use Files.copy)
    // - Moving/Renaming files/directories (use Files.move)
    // - Listing directory contents (Files.list or Files.walk) with filtering
    // options
    // - Getting file size (Files.size), last modified time
    // (Files.getLastModifiedTime), etc.
    // - Reading/Writing using streams for large files (Files.newInputStream,
    // Files.newOutputStream)
}
