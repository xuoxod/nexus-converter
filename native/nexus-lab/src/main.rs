use clap::Parser;
use log::{error, info};

use nexus_kit::{ensure_output_writable, validate_mp4_path};

/// Nexus Lab CLI: validate and convert MP4 -> MP3 using the native engine
#[derive(Parser, Debug)]
#[command(
    name = "nexus-lab",
    version,
    about = "Validate and convert media using Nexus backend",
    author = ""
)]
struct Args {
    /// Path to an input .mp4 file
    input: String,
    /// Output MP3 path; defaults to input with .mp3 in same directory
    #[arg(short, long)]
    out: Option<String>,
    /// Perform deep FFmpeg probe before conversion (requires --features nexus-kit/probe at build time)
    #[arg(long)]
    probe: bool,
}

fn main() {
    env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"))
        .target(env_logger::Target::Stderr)
        .init();

    let args = Args::parse();

    // 1) Validate input mp4
    let validated = match validate_mp4_path(&args.input) {
        Ok(v) => v,
        Err(e) => {
            error!("Validation failed: {e}");
            std::process::exit(2);
        }
    };
    info!(
        "Validated MP4: {} ({} bytes)",
        validated.canonical_path.display(),
        validated.size_bytes
    );

    // 2) Determine output path
    let output = match args.out {
        Some(s) => s,
        None => {
            let mut p = validated.canonical_path.clone();
            p.set_extension("mp3");
            p.to_string_lossy().to_string()
        }
    };
    // Optional deep probe using FFmpeg, if compiled with feature
    if args.probe {
        #[cfg(feature = "probe")]
        {
            use nexus_kit::probe::ensure_mp4_decodable;
            if let Err(e) = ensure_mp4_decodable(validated.canonical_path.as_path()) {
                error!("FFmpeg probe failed: {e}");
                std::process::exit(5);
            }
        }
        #[cfg(not(feature = "probe"))]
        {
            info!("--probe requested but probe feature not enabled at build time; skipping");
        }
    }

    if let Err(e) = ensure_output_writable(&output) {
        error!("Output path not writable: {e}");
        std::process::exit(3);
    }

    // 3) Attempt conversion via the native library reusable API
    match media_converter_lib::convert_to_mp3(&validated.canonical_path.to_string_lossy(), &output)
    {
        Ok(()) => {
            info!("Conversion completed: {}", output);
        }
        Err(e) => {
            error!("Conversion failed: {e:?}");
            std::process::exit(4);
        }
    }
}
