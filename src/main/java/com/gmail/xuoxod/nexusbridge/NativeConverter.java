package com.gmail.xuoxod.nexusbridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.jna.Library;
import com.sun.jna.Native;

public class NativeConverter {

    private static final Logger LOGGER = Logger.getLogger(NativeConverter.class.getName()); // Keep logger
    // Instance might not be needed if only using System.load and native methods
    // private static MediaConverterLib INSTANCE = null;

    // Define the JNA interface mapping to the Rust library
    // This is only needed if you use Native.load() and instance methods
    public interface MediaConverterLib extends Library {
        boolean convertToMp3(String inputPath, String outputPath);

        String getBackendVersion();
    }

    static {
        loadNativeLibrary();
    }

    private static void loadNativeLibrary() {
        try {
            // Respect quiet mode early (suppress INFO logs from the loader itself)
            if (Boolean.getBoolean("nexus.quiet")) {
                LOGGER.setLevel(Level.WARNING);
            }
            // --- Library Extraction Logic ---
            String libName = "media_converter_lib"; // Base name
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();
            String libFileName;
            String resourcePath;

            // Determine library filename and resource path based on OS/Arch
            // For now, we only handle Linux x86_64 based on your setup
            if (os.contains("linux") && (arch.equals("amd64") || arch.equals("x86_64"))) {
                libFileName = "lib" + libName + ".so";
                resourcePath = "/native/linux-x86_64/" + libFileName;
            } else {
                // Add support for other OS/Arch (macOS, Windows) here if needed
                throw new UnsupportedOperationException("Unsupported OS/Arch: " + os + "/" + arch);
            }

            if (!Boolean.getBoolean("nexus.quiet")) {
                LOGGER.log(Level.INFO, "Attempting to load native library from resource path: {0}", resourcePath);
            }

            InputStream libStream = NativeConverter.class.getResourceAsStream(resourcePath);
            if (libStream == null) {
                throw new UnsatisfiedLinkError("Cannot find native library resource: " + resourcePath);
            }

            // Create a temporary file to extract the library
            Path tempFile = Files.createTempFile(libName + "-", ".tmp");
            tempFile.toFile().deleteOnExit(); // Ensure cleanup

            // Copy the library from JAR to the temporary file
            Files.copy(libStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            libStream.close();

            // Make executable on Unix-like systems (important!)
            if (!os.contains("windows")) {
                try {
                    Runtime.getRuntime().exec(new String[] { "chmod", "+x", tempFile.toAbsolutePath().toString() })
                            .waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Failed to set executable permission on temp library", e);
                }
            }

            // Load the library from the extracted temporary file using its absolute path
            System.load(tempFile.toAbsolutePath().toString());
            if (!Boolean.getBoolean("nexus.quiet")) {
                LOGGER.log(Level.INFO, "✅ Native library '{0}' loaded successfully from temporary file.", libFileName);
            }

            // If you were using JNA's Native.load() before and need the INSTANCE:
            // You might need to load it *after* System.load, potentially using the temp
            // path,
            // but often System.load is sufficient if your Rust functions are exposed
            // correctly for JNI.
            // For now, we'll rely on System.load making the native functions available
            // globally.
            // INSTANCE = Native.load(libName, MediaConverterLib.class); // Or potentially
            // load via tempFile path if needed

        } catch (UnsatisfiedLinkError | IOException | UnsupportedOperationException e) {
            LOGGER.log(Level.SEVERE, "❌ Failed to load native library: " + e.getMessage(), e);
            // Decide how to handle this - exit, throw runtime exception, etc.
            System.exit(1); // Exit if library loading fails
        }
    }

    // Static method to call the Rust function
    // Ensure your Rust function is exposed correctly via JNI/FFI
    // The name needs to match the JNI export name (e.g.,
    // Java_com_gmail_xuoxod_nexusbridge_NativeConverter_convertToMp3)
    // This method now acts as a wrapper around the actual native call.
    public static int performConversion(String inputPath, String outputPath) {
        try {
            // Directly call the native method exposed via JNI after System.load
            boolean ok = convertToMp3(inputPath, outputPath);
            return ok ? 0 : 2; // 0 = success, 2 = native conversion reported failure
        } catch (UnsatisfiedLinkError e) {
            // Catch error in case System.load succeeded but the specific function isn't
            // found
            LOGGER.log(Level.SEVERE, "Native method 'convertToMp3' not found or library not loaded correctly.", e);
            return -1; // Indicate failure
        }
    }

    // Declare the native method signature matching the JNI export from Rust
    // The name format is Java_Package_Name_ClassName_MethodName
    private static native boolean convertToMp3(String inputPath, String outputPath);

    // Expose backend version string provided by Rust side
    private static native String getBackendVersion();

    // Adjust native log level before any other native call (levels:
    // off,error,warn,info,debug,trace)
    private static native void setLogLevel(String level);

    // Probe decodability and presence of audio stream
    private static native boolean probeInput(String inputPath);

    public static String backendVersion() {
        try {
            return getBackendVersion();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Unable to query backend version from native library.", t);
            return "unknown";
        }
    }

    public static boolean probe(String inputPath) {
        try {
            return probeInput(inputPath);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Probe failed due to link/load error.", t);
            return false;
        }
    }

    public static void applyQuiet(boolean quiet) {
        if (quiet) {
            try {
                setLogLevel("warn");
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "Failed to set native log level to warn.", t);
            }
        }
    }

    public static void applyDebug(boolean debug) {
        if (debug) {
            try {
                setLogLevel("info");
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "Failed to set native log level to info.", t);
            }
        }
    }
}
