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
        footer = {
                "",
                "✨ Examples:",
                "  nexus-convert my_video.mp4",
                "  nexus-convert input.avi -o output_audio.mp3",
                "  nexus-convert video.flv -d /path/to/audio/files/",
                "  nexus-convert movie.mp4 -o existing.mp3 -f",
                "  Flags: --quiet (minimal output) | --debug (verbose logs)",
                "💖 Developed with NexusBridge. Enjoy!"
        })
public class Main implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to the input media file (e.g., video.mp4, audio.flv).")
    private File inputFile;

    @Option(names = { "-o", "--output" }, description = "Path to the output MP3 file.")
    private File outputFile;

    @Option(names = { "-d", "--directory" }, description = "Specify an output directory.")
    private File outputDirectory;

    @Option(names = { "-f", "--force" }, description = "Overwrite the output file if it already exists. Default: ${DEFAULT-VALUE}")
    private boolean forceOverwrite = false;

    @Option(names = "--skip-probe", description = "Skip preflight probe that validates decodability and presence of an audio stream.")
    private boolean skipProbe = false;

    @Option(names = { "-q", "--quiet" }, description = "Reduce native backend logs to warnings only.")
    private boolean quiet = false;

    @Option(names = { "--debug" }, description = "Enable verbose native logs and extra Java diagnostics.")
    private boolean debug = false;

    @Override
    public Integer call() throws Exception {
        if (quiet && debug) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string("@|red,bold ❌ Error: --quiet and --debug cannot be used together.|@"));
            return 2;
        }
        if (quiet) System.setProperty("nexus.quiet", "true");
        NativeConverter.applyQuiet(quiet);
        NativeConverter.applyDebug(debug);

        if (!quiet) {
            final String title = CommandLine.Help.Ansi.AUTO.string("@|bold,cyan NexusBridge Media Converter|@ ");
            final String backend = NativeConverter.backendVersion();
            System.out.println(CommandLine.Help.Ansi.AUTO.string(String.format("%s(backend %s)", title,
                    CommandLine.Help.Ansi.AUTO.string("@|yellow " + backend + "|@"))));
        }

        if (!inputFile.exists() || !inputFile.isFile()) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string("@|red ❌ Error: Input file not found or is not a regular file:|@ ")
                    + inputFile.getAbsolutePath());
            return 1;
        }
        if (!quiet) System.out.println("📄 Input File: " + inputFile.getAbsolutePath());

        File finalOutputFile = determineOutputPath();
        if (finalOutputFile.exists() && !forceOverwrite) {
            System.err.println(CommandLine.Help.Ansi.AUTO.string("@|red ❌ Error: Output file already exists:|@ ") + finalOutputFile.getAbsolutePath());
            System.err.println(CommandLine.Help.Ansi.AUTO.string("@|yellow    Use the -f or --force option to overwrite.|@"));
            return 1;
        } else if (finalOutputFile.exists() && forceOverwrite && !quiet) {
            System.out.println("⚠️ Warning: Output file exists and will be overwritten (force=true).");
        }
        if (!quiet) System.out.println("💾 Target Output File: " + finalOutputFile.getAbsolutePath());

        File parentDir = finalOutputFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!quiet) System.out.println("📁 Creating output directory: " + parentDir.getAbsolutePath());
            if (!parentDir.mkdirs()) {
                System.err.println(CommandLine.Help.Ansi.AUTO.string("@|red ❌ Error: Could not create output directory:|@ ") + parentDir.getAbsolutePath());
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

    private File determineOutputPath() {
        String inputFileName = inputFile.getName();
        String inputParentDir = inputFile.getParent();
        if (inputParentDir == null) {
            inputParentDir = ".";
        }

        int dotIndex = inputFileName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? inputFileName : inputFileName.substring(0, dotIndex);
        String defaultOutputName = baseName + ".mp3";

        File targetDir;
        String targetFileName;

        if (outputFile != null) {
            info("Output file explicitly specified via -o: " + outputFile);
            if (outputFile.isAbsolute() || outputFile.getParent() != null) {
                targetDir = outputFile.getParentFile();
                targetFileName = outputFile.getName();
                if (outputDirectory != null) {
                    warn("Both -o (with path) and -d specified. Using directory from -o: " + targetDir);
                }
            } else {
                targetDir = (outputDirectory != null) ? outputDirectory : new File(inputParentDir);
                targetFileName = outputFile.getName();
                info("Output filename specified via -o, using directory: " + targetDir.getAbsolutePath());
            }
        } else {
            targetFileName = defaultOutputName;
            if (outputDirectory != null) {
                targetDir = outputDirectory;
                info("Output directory explicitly specified via -d: " + targetDir.getAbsolutePath());
            } else {
                info("No output path specified. Checking default locations (Music > Downloads > Input Dir)...");
                String homeDir = System.getProperty("user.home");
                java.nio.file.Path musicPath = java.nio.file.Paths.get(homeDir, "Music");
                java.nio.file.Path downloadsPath = java.nio.file.Paths.get(homeDir, "Downloads");

                if (java.nio.file.Files.isDirectory(musicPath)) {
                    targetDir = musicPath.toFile();
                    info("Defaulting output to Music directory: " + targetDir.getAbsolutePath());
                } else if (java.nio.file.Files.isDirectory(downloadsPath)) {
                    targetDir = downloadsPath.toFile();
                    info("Music directory not found. Defaulting output to Downloads directory: "
                            + targetDir.getAbsolutePath());
                } else {
                    targetDir = new File(inputParentDir);
                    info("Music and Downloads directories not found. Defaulting output to input directory: "
                            + targetDir.getAbsolutePath());
                }
            }
        }

        if (!targetDir.isAbsolute()) {
            targetDir = targetDir.getAbsoluteFile();
            info("Resolved target directory to absolute path: " + targetDir.getAbsolutePath());
        }

        return new File(targetDir, targetFileName);
    }

    public static String scrubAnsi(String message) {
        if (message == null) {
            return "";
        }
        return message.replaceAll("\u001B\\[[0-9;?]*[ -/]*[@-~]", "")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\0", "");
    }

    private void info(String message) {
        if (!quiet) {
            System.out.println(CommandLine.Help.Ansi.AUTO.string("@|cyan [INFO]|@ ") + scrubAnsi(message));
        }
    }

    private void warn(String message) {
        if (!quiet) {
            System.out.println(CommandLine.Help.Ansi.AUTO.string("@|yellow [WARN]|@ ") + scrubAnsi(message));
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

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

