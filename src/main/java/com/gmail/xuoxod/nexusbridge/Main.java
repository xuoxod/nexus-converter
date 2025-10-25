package com.gmail.xuoxod.nexusbridge;

import java.io.File;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

// --- Picocli Command Definition ---
@Command(name = "nexus-convert", // The command name users will type
        version = "NexusBridge Media Converter 1.0.0", // Version info for --version
        mixinStandardHelpOptions = true, // Adds --help and --version options automatically
        description = {
                "🎬 Converts various media files (e.g., FLV, AVI, MP4) to MP3 audio format.",
                "Utilizes a high-performance Rust backend for the heavy lifting. 🚀"
        },
        // *** CHANGE 'epilog' TO 'footer' HERE ***
        footer = { // Text displayed at the end of the help message
                "", // Blank line for spacing
                "✨ Examples:",
                "  # Convert a single video file to MP3 in the same directory",
                "  nexus-convert my_video.mp4",
                "",
                "  # Convert and specify an output file name",
                "  nexus-convert input.avi -o output_audio.mp3",
                "",
                "  # Convert and specify a different output directory",
                "  nexus-convert video.flv -d /path/to/audio/files/",
                "",
                "  # Force overwrite if output file exists",
                "  nexus-convert movie.mp4 -o existing.mp3 -f",
                "",
                "  Flags:",
                "    --quiet  minimal console output (spinner + final line); suppresses info",
                "    --debug  verbose native logs and extra Java diagnostics",
                "    Note: --quiet and --debug cannot be used together.",
                "",
                "💖 Developed with NexusBridge. Enjoy!"
        })
public class Main implements Callable<Integer> { // Implement Callable for Picocli

    // --- Picocli Options and Parameters ---

    @Parameters(index = "0", // First positional parameter
            description = "Path to the input media file (e.g., video.mp4, audio.flv).")
    private File inputFile; // Picocli converts String path to File object

    @Option(names = { "-o",
            "--output" }, description = "Path to the output MP3 file. If omitted, defaults to the input file's name with an .mp3 extension in the same directory.")
    private File outputFile; // Optional output file path

    @Option(names = { "-d",
            "--directory" }, description = "Specify an output directory. If used with -o, the directory part of -o takes precedence. If used alone, the output filename defaults as described for -o, but placed in this directory.")
    private File outputDirectory; // Optional output directory

    @Option(names = { "-f",
            "--force" }, description = "Overwrite the output file if it already exists. Default: ${DEFAULT-VALUE}")
    private boolean forceOverwrite = false; // Default is false

    @Option(names = "--skip-probe", description = "Skip preflight probe that validates decodability and presence of an audio stream.")
    private boolean skipProbe = false; // Default: do probe

    @Option(names = { "-q", "--quiet" }, description = "Reduce native backend logs to warnings only.")
    private boolean quiet = false;

    @Option(names = { "--debug" }, description = "Enable verbose native logs and extra Java diagnostics.")
    private boolean debug = false;

    // --- Application Logic ---

    @Override
    public Integer call() throws Exception { // This method is executed when the command runs
        // Validate mutually exclusive flags
        if (quiet && debug) {
            System.err.println(CommandLine.Help.Ansi.AUTO
                    .string("@|red,bold ❌ Error: --quiet and --debug cannot be used together.|@"));
            return 2;
        }
        // Apply quiet native logs early (must be before any native call like
        // backendVersion)
        // Propagate quiet mode to native loader early (before class init) via system
        // property
        if (quiet) {
            System.setProperty("nexus.quiet", "true");
        }
        NativeConverter.applyQuiet(quiet);
        NativeConverter.applyDebug(debug);

        if (!quiet) {
            final String title = CommandLine.Help.Ansi.AUTO.string("@|bold,cyan NexusBridge Media Converter|@ ");
            final String backend = NativeConverter.backendVersion();
            System.out.println(CommandLine.Help.Ansi.AUTO
                    .string(String.format("%s(backend %s)", title,
                            CommandLine.Help.Ansi.AUTO.string("@|yellow " + backend + "|@"))));
        }

        // 1. Validate Input File
        if (!inputFile.exists() || !inputFile.isFile()) {
            System.err.println(CommandLine.Help.Ansi.AUTO
                    .string("@|red ❌ Error: Input file not found or is not a regular file:|@ ")
                    + inputFile.getAbsolutePath());
            return 1; // Indicate error exit code
        }
        if (!quiet) {
            System.out.println("📄 Input File: " + inputFile.getAbsolutePath());
        }

        // 2. Determine Output Path Logic (Handling defaults and options)
        File finalOutputFile = determineOutputPath();

        // 3. Check for Output File Existence (if not forcing overwrite)
        if (finalOutputFile.exists() && !forceOverwrite) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string("@|red ❌ Error: Output file already exists:|@ ")
                    + finalOutputFile.getAbsolutePath());
            System.err.println(
                    CommandLine.Help.Ansi.AUTO.string("@|yellow    Use the -f or --force option to overwrite.|@"));
            return 1; // Indicate error exit code
        } else if (finalOutputFile.exists() && forceOverwrite) {
            if (!quiet) {
                System.out.println("⚠️ Warning: Output file exists and will be overwritten (force=true).");
            }
        }
        if (!quiet) {
            System.out.println("💾 Target Output File: " + finalOutputFile.getAbsolutePath());
        }

        // 4. Ensure Output Directory Exists
        File parentDir = finalOutputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!quiet) {
                System.out.println("📁 Creating output directory: " + parentDir.getAbsolutePath());
            }
            if (!parentDir.mkdirs()) {
                System.err.println(
                        CommandLine.Help.Ansi.AUTO.string("@|red ❌ Error: Could not create output directory:|@ ")
                                + parentDir.getAbsolutePath());
                return 1;
            }
        }

        // 5. Instantiate Native Converter and Call JNI Method
        try {
            if (!quiet) {
                System.out.println("⚙️ Initializing conversion backend...");
            }
            // No need to instantiate NativeConverter if using static methods
            // NativeConverter converter = new NativeConverter();

            // Preflight probe if enabled
            if (!skipProbe) {
                if (!quiet) {
                    System.out.print("🔎 Probing input... ");
                }
                boolean ok = NativeConverter.probe(inputFile.getAbsolutePath());
                if (!ok) {
                    if (!quiet) {
                        System.out.println(CommandLine.Help.Ansi.AUTO.string("@|red ❌ failed|@"));
                    }
                    System.err.println(CommandLine.Help.Ansi.AUTO
                            .string("@|red Input is not decodable or contains no audio stream.|@"));
                    return 4;
                }
                if (!quiet) {
                    System.out.println(CommandLine.Help.Ansi.AUTO.string("@|green ✓|@"));
                }
            }

            if (!quiet) {
                System.out.print("⏳ Performing conversion via Rust backend... ");
            }
            Spinner spinner = new Spinner("Converting");
            Thread spinnerThread = new Thread(spinner, "spinner");
            spinnerThread.setDaemon(true);
            spinnerThread.start();

            int conversionResult;
            try {
                conversionResult = NativeConverter.performConversion(
                        inputFile.getAbsolutePath(),
                        finalOutputFile.getAbsolutePath());
            } finally {
                spinner.stop();
                spinnerThread.join(200);
                if (!quiet) {
                    System.out.print("\r"); // return to line start for final status
                }
            }

            if (conversionResult == 0) {
                System.out.println(CommandLine.Help.Ansi.AUTO.string(
                        "@|green,bold ✅ Conversion successful!|@ Output: " + finalOutputFile.getAbsolutePath()));
                return 0;
            } else if (conversionResult == 2) {
                System.err.println(CommandLine.Help.Ansi.AUTO
                        .string("@|red ❌ Conversion failed in native backend. See logs for details.|@"));
                return 2;
            } else {
                System.err.println(CommandLine.Help.Ansi.AUTO
                        .string("@|red ❌ Conversion failed (link/load error).|@"));
                return 3;
            }
        } catch (UnsatisfiedLinkError ule) {
            System.err.println(CommandLine.Help.Ansi.AUTO
                    .string("@|red,bold ❌ FATAL: Could not link to the native Rust library.|@"));
            System.err
                    .println("   Ensure 'media_converter_lib' (or similar name for your OS) is built and accessible.");
            System.err.println("   Details: " + ule.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println(
                    CommandLine.Help.Ansi.AUTO.string("@|red ❌ An unexpected error occurred during conversion:|@"));
            e.printStackTrace(); // Print stack trace for debugging
            return 1;
        }
    }

    /**
     * Helper method to determine the final output file path based on user options.
     * Implements default logic: Music > Downloads > Input Directory if no output
     * specified.
     */
    private File determineOutputPath() {
        String inputFileName = inputFile.getName();
        String inputParentDir = inputFile.getParent();
        if (inputParentDir == null) {
            inputParentDir = "."; // Use current directory if no parent
        }

        // Get base name without extension
        int dotIndex = inputFileName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? inputFileName : inputFileName.substring(0, dotIndex);
        String defaultOutputName = baseName + ".mp3";

        File targetDir;
        String targetFileName;

        if (outputFile != null) {
            // --- User specified -o (output file path) ---
            info("Output file explicitly specified via -o: " + outputFile);
            // If -o is specified, it dictates the full path or filename
            if (outputFile.isAbsolute() || outputFile.getParent() != null) {
                // -o includes a directory path (absolute or relative)
                targetDir = outputFile.getParentFile();
                targetFileName = outputFile.getName();
                // If -d was also given, the directory from -o takes precedence. Log if needed.
                if (outputDirectory != null) {
                    warn("Both -o (with path) and -d specified. Using directory from -o: " + targetDir);
                }
            } else {
                // -o specifies only a filename, use specified output dir (-d) or input dir
                targetDir = (outputDirectory != null) ? outputDirectory : new File(inputParentDir);
                targetFileName = outputFile.getName();
                info("Output filename specified via -o, using directory: " + targetDir.getAbsolutePath());
            }
        } else {
            // --- No -o specified, determine target directory ---
            targetFileName = defaultOutputName; // Use default name (<baseName>.mp3)

            if (outputDirectory != null) {
                // User specified -d (output directory) only
                targetDir = outputDirectory;
                info("Output directory explicitly specified via -d: " + targetDir.getAbsolutePath());
            } else {
                // --- NO -o and NO -d: Implement the Music/Downloads/Input fallback ---
                info("No output path specified. Checking default locations (Music > Downloads > Input Dir)...");
                String homeDir = System.getProperty("user.home");
                // Use java.nio.file for robust path handling
                java.nio.file.Path musicPath = java.nio.file.Paths.get(homeDir, "Music"); // Case-sensitive on Linux
                java.nio.file.Path downloadsPath = java.nio.file.Paths.get(homeDir, "Downloads"); // Case-sensitive on
                                                                                                  // Linux

                if (java.nio.file.Files.isDirectory(musicPath)) {
                    targetDir = musicPath.toFile();
                    info("Defaulting output to Music directory: " + targetDir.getAbsolutePath());
                } else if (java.nio.file.Files.isDirectory(downloadsPath)) {
                    targetDir = downloadsPath.toFile();
                    info("Music directory not found. Defaulting output to Downloads directory: "
                            + targetDir.getAbsolutePath());
                } else {
                    // Fallback to input directory
                    targetDir = new File(inputParentDir);
                    info("Music and Downloads directories not found. Defaulting output to input directory: "
                            + targetDir.getAbsolutePath());
                }
                // --- End Default Logic ---
            }
        }

        // Ensure targetDir is absolute; resolve relative paths against current working
        // directory
        if (!targetDir.isAbsolute()) {
            targetDir = targetDir.getAbsoluteFile();
            info("Resolved target directory to absolute path: " + targetDir.getAbsolutePath());
        }

        // Construct the final absolute output file path
        File finalOutputFile = new File(targetDir, targetFileName);
        // Log the final decision
        // info("Final calculated output path: " + finalOutputFile.getAbsolutePath());
        // // Already logged in call()
        return finalOutputFile;
    }

    // --- Helper methods for logging ---
    private void info(String message) {
        if (!quiet) {
            System.out.println(CommandLine.Help.Ansi.AUTO.string("@|cyan [INFO]|@ ") + message);
        }
    }

    private void warn(String message) {
        if (!quiet) {
            System.out.println(CommandLine.Help.Ansi.AUTO.string("@|yellow [WARN]|@ ") + message);
        }
    }
    // Add error() if needed

    // --- Main Entry Point ---
    public static void main(String[] args) {
        // Use Picocli to parse arguments and execute the 'call' method
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode); // Exit with the code returned by call()
    }

    // --- Simple CLI spinner for inline updates ---
    private static final class Spinner implements Runnable {
        private final char[] frames = new char[] { '⠋', '⠙', '⠹', '⠸', '⠼', '⠴', '⠦', '⠧', '⠇', '⠏' };
        private volatile boolean running = true;
        private final String label;

        Spinner(String label) {
            this.label = label;
        }

        void stop() {
            running = false;
        }

        @Override
        public void run() {
            int i = 0;
            while (running) {
                char c = frames[i % frames.length];
                String text = String.format("\r%s %c", label, c);
                System.out.print(CommandLine.Help.Ansi.AUTO.string("@|cyan " + text + "|@"));
                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
                i++;
            }
        }
    }
}
