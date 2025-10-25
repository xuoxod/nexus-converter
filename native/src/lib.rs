use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use std::convert::TryInto;
use std::sync::Once;

// Logging
use env_logger;
use log::{error, info, LevelFilter}; // Import log macros

// FFmpeg
use ffmpeg_next as ffmpeg;
use ffmpeg_next::Rescale; // Needed for the .rescale() method

const BACKEND_VERSION: &str = "0.1.0-alpha";

// Static variables for one-time initialization
static FFMPEG_INIT: Once = Once::new();
static LOGGER_INIT: Once = Once::new();
// Desired log level set by Java before logger init (None -> use env/default)
static mut DESIRED_LOG_LEVEL: Option<LevelFilter> = None;

// Helper function to initialize FFmpeg safely
fn init_ffmpeg() {
    FFMPEG_INIT.call_once(|| {
        info!("Initializing FFmpeg...");
        match ffmpeg::init() {
            Ok(_) => info!("  FFmpeg initialized successfully."),
            Err(e) => error!("  ERROR initializing FFmpeg: {:?}", e),
        }
        // Optional: Set FFmpeg log level (can be noisy)
        // ffmpeg::log::set_level(ffmpeg::log::Level::Info);
    });
}

// Helper function to initialize the logger safely
fn init_logger() {
    LOGGER_INIT.call_once(|| {
        let mut builder =
            env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"));
        builder.target(env_logger::Target::Stderr);
        // Apply desired log level if provided by Java prior to init
        unsafe {
            if let Some(level) = DESIRED_LOG_LEVEL {
                builder.filter_level(level);
                // Also set max level for the log facade
                log::set_max_level(level);
            }
        }
        let _ = builder.try_init();
        // If logger already initialized by host (e.g., CLI), this will be a no-op.
        info!("Logger initialized (or already set by host).");
    });
}

// Combined init function
fn ensure_initialized() {
    init_logger();
    init_ffmpeg();
}

/// Quick probe to ensure the input is decodable and contains at least one audio stream.
fn ensure_decodable_with_audio(input_path: &str) -> Result<(), ffmpeg::Error> {
    ensure_initialized();
    info!("probe: input={}", input_path);
    let ictx = ffmpeg::format::input(&input_path)?;
    for stream in ictx.streams() {
        let codec_params = stream.parameters();
        if codec_params.medium() == ffmpeg::media::Type::Audio {
            // Try constructing a decoder to verify it opens
            let ctx = ffmpeg::codec::context::Context::from_parameters(codec_params.clone())?;
            let _decoder = ctx.decoder().audio()?;
            return Ok(());
        }
    }
    Err(ffmpeg::Error::Bug) // no audio stream found
}

// --- Configure log level before initialization ---
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_com_gmail_xuoxod_nexusbridge_NativeConverter_setLogLevel<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    level_jstring: JString<'local>,
) {
    let level_str: String = match env.get_string(&level_jstring) {
        Ok(s) => match s.to_str() {
            Ok(v) => v.to_string(),
            Err(_) => return,
        },
        Err(_) => return,
    };
    let level = match level_str.to_ascii_lowercase().as_str() {
        "off" => LevelFilter::Off,
        "error" => LevelFilter::Error,
        "warn" | "warning" => LevelFilter::Warn,
        "info" => LevelFilter::Info,
        "debug" => LevelFilter::Debug,
        "trace" => LevelFilter::Trace,
        _ => LevelFilter::Info,
    };
    // If logger not yet initialized, record the desired level; otherwise, tighten max level
    let already_inited = LOGGER_INIT.is_completed();
    if !already_inited {
        unsafe {
            DESIRED_LOG_LEVEL = Some(level);
        }
    }
    // If call_once didn't run (logger already initialized), set max level now
    if already_inited {
        log::set_max_level(level);
    }
}

/// Public reusable conversion API for Rust callers (CLI, tests, etc.)
/// Converts the given MP4 input path to an MP3 at the given output path.
/// Returns Ok(()) on success, or an ffmpeg::Error on failure.
pub fn convert_to_mp3(input_path: &str, output_path: &str) -> Result<(), ffmpeg::Error> {
    ensure_initialized();
    info!(
        "convert_to_mp3: input={}, output={}",
        input_path, output_path
    );

    // Open input file
    let mut ictx = ffmpeg::format::input(&input_path)?;
    info!(
        "  Format: {}, Duration: {:.2}s",
        ictx.format().name(),
        ictx.duration() as f64 / ffmpeg::ffi::AV_TIME_BASE as f64
    );

    // Find first usable audio stream and decoder
    let mut stream_index_opt: Option<usize> = None;
    let mut decoder_opt: Option<ffmpeg::decoder::Audio> = None;
    for stream in ictx.streams() {
        let current_index = stream.index();
        let codec_params = stream.parameters();
        if codec_params.medium() == ffmpeg::media::Type::Audio {
            match ffmpeg::codec::context::Context::from_parameters(codec_params.clone()) {
                Ok(codec_context) => match codec_context.decoder().audio() {
                    Ok(audio_decoder) => {
                        stream_index_opt = Some(current_index);
                        decoder_opt = Some(audio_decoder);
                        break;
                    }
                    Err(_) => {}
                },
                Err(_) => {}
            }
        }
    }
    let stream_index = stream_index_opt.ok_or(ffmpeg::Error::Bug)?; // not found
    let mut decoder = decoder_opt.ok_or(ffmpeg::Error::Bug)?;

    // Setup output and encoder
    let (mut octx, mut opened_encoder, out_time_base, encoder_time_base) =
        (|| -> Result<_, ffmpeg::Error> {
            let mut octx = ffmpeg::format::output(&output_path)?;
            let encoder_desc = ffmpeg::encoder::find(ffmpeg::codec::Id::MP3)
                .ok_or(ffmpeg::Error::EncoderNotFound)?;
            let mut out_stream = octx.add_stream(encoder_desc)?;
            let codec_ctx = ffmpeg::codec::context::Context::new();
            let mut encoder = codec_ctx.encoder().audio()?;
            let target_sample_rate = 44100;
            let target_channel_layout = ffmpeg::channel_layout::ChannelLayout::STEREO;
            let target_bit_rate = 192_000;
            let target_format = ffmpeg::format::Sample::I16(ffmpeg::format::sample::Type::Planar);
            encoder.set_rate(target_sample_rate as i32);
            encoder.set_channel_layout(target_channel_layout);
            encoder.set_format(target_format);
            encoder.set_bit_rate(target_bit_rate);
            let encoder_time_base_tuple = (1, target_sample_rate as i32);
            encoder.set_time_base(encoder_time_base_tuple);
            out_stream.set_time_base(encoder_time_base_tuple);
            let opened_encoder = encoder.open_as(encoder_desc)?;
            out_stream.set_parameters(&opened_encoder);
            let out_time_base = out_stream.time_base();
            octx.write_header()?;
            let encoder_time_base: ffmpeg::Rational = encoder_time_base_tuple.into();
            Ok((octx, opened_encoder, out_time_base, encoder_time_base))
        })()?;

    // Optional resampler when formats don't match
    let mut resampler: Option<ffmpeg::software::resampling::Context> = None;
    if decoder.format() != opened_encoder.format()
        || decoder.rate() != opened_encoder.rate()
        || decoder.channel_layout() != opened_encoder.channel_layout()
    {
        match ffmpeg::software::resampling::context::Context::get(
            decoder.format(),
            decoder.channel_layout(),
            decoder.rate(),
            opened_encoder.format(),
            opened_encoder.channel_layout(),
            opened_encoder.rate(),
        ) {
            Ok(swr_ctx) => {
                resampler = Some(swr_ctx);
            }
            Err(e) => return Err(e),
        }
    }

    // Processing loop state
    let mut decoded_frame = ffmpeg::frame::Audio::empty();
    let mut resampled_frame = ffmpeg::frame::Audio::empty();
    let mut encoded_packet = ffmpeg::Packet::empty();

    let encoder_frame_size = opened_encoder.frame_size() as usize;
    if encoder_frame_size == 0 {
        return Err(ffmpeg::Error::Bug);
    }
    let num_channels = opened_encoder.channels() as usize;
    let mut audio_buffer: Vec<Vec<i16>> = vec![Vec::new(); num_channels];
    let mut total_samples_sent_to_encoder: i64 = 0;
    let encoder_rate = opened_encoder.rate() as i64;

    // Main loop
    'main_loop: loop {
        let mut reached_input_eof = false;
        match decoder.receive_frame(&mut decoded_frame) {
            Ok(_) => {
                let frame_to_buffer = if let Some(ref mut swr) = resampler {
                    swr.run(&decoded_frame, &mut resampled_frame)?;
                    &resampled_frame
                } else {
                    &decoded_frame
                };
                for i in 0..num_channels {
                    let plane = frame_to_buffer.data(i);
                    let plane_ptr = plane.as_ptr();
                    let num_samples_in_frame = frame_to_buffer.samples();
                    let samples_i16: &[i16] = unsafe {
                        std::slice::from_raw_parts(plane_ptr as *const i16, num_samples_in_frame)
                    };
                    audio_buffer[i].extend_from_slice(samples_i16);
                }
            }
            Err(ffmpeg::Error::Other {
                errno: ffmpeg::ffi::EAGAIN,
            }) => match ictx.packets().next() {
                Some((stream, packet)) => {
                    if stream.index() == stream_index {
                        decoder.send_packet(&packet)?;
                    }
                }
                None => {
                    decoder.send_eof()?;
                    reached_input_eof = true;
                }
            },
            Err(ffmpeg::Error::Eof) => {
                break 'main_loop;
            }
            Err(e) => return Err(e),
        }

        // Drain buffer into encoder
        while audio_buffer
            .iter()
            .all(|buf| buf.len() >= encoder_frame_size)
        {
            let mut encoding_frame = ffmpeg::frame::Audio::new(
                opened_encoder.format(),
                encoder_frame_size,
                opened_encoder.channel_layout(),
            );
            let ret = unsafe { ffmpeg::ffi::av_frame_get_buffer(encoding_frame.as_mut_ptr(), 0) };
            if ret < 0 {
                return Err(ffmpeg::Error::from(ret));
            }
            encoding_frame.set_rate(opened_encoder.rate());
            for i in 0..num_channels {
                let source_samples = &audio_buffer[i][0..encoder_frame_size];
                let dest_plane = encoding_frame.data_mut(i);
                let dest_samples_i16: &mut [i16] = unsafe {
                    std::slice::from_raw_parts_mut(
                        dest_plane.as_mut_ptr() as *mut i16,
                        encoder_frame_size,
                    )
                };
                dest_samples_i16.copy_from_slice(source_samples);
            }
            let frame_pts =
                total_samples_sent_to_encoder.rescale((1, encoder_rate as i32), out_time_base);
            encoding_frame.set_pts(Some(frame_pts));
            opened_encoder.send_frame(&encoding_frame)?;
            total_samples_sent_to_encoder += encoder_frame_size as i64;
            for i in 0..num_channels {
                audio_buffer[i].drain(0..encoder_frame_size);
            }
            loop {
                match opened_encoder.receive_packet(&mut encoded_packet) {
                    Ok(_) => {
                        encoded_packet.set_stream(0);
                        let original_pts = encoded_packet.pts().unwrap_or(0);
                        encoded_packet
                            .set_pts(Some(original_pts.rescale(encoder_time_base, out_time_base)));
                        if let Some(dts) = encoded_packet.dts() {
                            encoded_packet
                                .set_dts(Some(dts.rescale(encoder_time_base, out_time_base)));
                        }
                        encoded_packet.write_interleaved(&mut octx)?;
                    }
                    Err(ffmpeg::Error::Other {
                        errno: ffmpeg::ffi::EAGAIN,
                    }) => break,
                    Err(e) => return Err(e),
                }
            }
        }

        if reached_input_eof
            && audio_buffer
                .iter()
                .all(|buf| buf.len() < encoder_frame_size)
        {
            break 'main_loop;
        }
    }

    // Flush remaining samples (pad with silence)
    if audio_buffer.iter().any(|buf| !buf.is_empty()) {
        let remaining_samples = audio_buffer[0].len();
        if !audio_buffer
            .iter()
            .all(|buf| buf.len() == remaining_samples)
        {
            return Err(ffmpeg::Error::Bug);
        }
        let mut final_frame = ffmpeg::frame::Audio::new(
            opened_encoder.format(),
            opened_encoder.frame_size() as usize,
            opened_encoder.channel_layout(),
        );
        let ret = unsafe { ffmpeg::ffi::av_frame_get_buffer(final_frame.as_mut_ptr(), 0) };
        if ret < 0 {
            return Err(ffmpeg::Error::from(ret));
        }
        final_frame.set_rate(opened_encoder.rate());
        let encoder_frame_size = opened_encoder.frame_size() as usize;
        for i in 0..num_channels {
            let dest_plane = final_frame.data_mut(i);
            let dest_samples_i16: &mut [i16] = unsafe {
                std::slice::from_raw_parts_mut(
                    dest_plane.as_mut_ptr() as *mut i16,
                    encoder_frame_size,
                )
            };
            // copy remaining and pad
            dest_samples_i16[..remaining_samples].copy_from_slice(&audio_buffer[i][..]);
            for sample in dest_samples_i16[remaining_samples..].iter_mut() {
                *sample = 0;
            }
        }
        let frame_pts =
            total_samples_sent_to_encoder.rescale((1, encoder_rate as i32), out_time_base);
        final_frame.set_pts(Some(frame_pts));
        opened_encoder.send_frame(&final_frame)?;
    }

    // Flush encoder
    opened_encoder.send_eof()?;
    loop {
        match opened_encoder.receive_packet(&mut encoded_packet) {
            Ok(_) => {
                encoded_packet.set_stream(0);
                let original_pts = encoded_packet.pts().unwrap_or(0);
                encoded_packet
                    .set_pts(Some(original_pts.rescale(encoder_time_base, out_time_base)));
                if let Some(dts) = encoded_packet.dts() {
                    encoded_packet.set_dts(Some(dts.rescale(encoder_time_base, out_time_base)));
                }
                encoded_packet.write_interleaved(&mut octx)?;
            }
            Err(ffmpeg::Error::Other {
                errno: ffmpeg::ffi::EAGAIN,
            }) => break,
            Err(ffmpeg::Error::Eof) => break,
            Err(e) => return Err(e),
        }
    }

    // Finish file
    octx.write_trailer()?;
    Ok(())
}

// --- Function to get backend version ---
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_com_gmail_xuoxod_nexusbridge_NativeConverter_getBackendVersion<
    'local,
>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> jstring {
    ensure_initialized();
    info!("Java called getBackendVersion");
    let output: JString<'local> = env
        .new_string(BACKEND_VERSION)
        .expect("❌ Couldn't create Java string for version!");
    output.into_raw()
}

// --- Function to handle the conversion ---
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_com_gmail_xuoxod_nexusbridge_NativeConverter_convertToMp3<'local>(
    mut env: JNIEnv<'local>, // Needs mut for get_string
    _object: JClass<'local>,
    input_path_jstring: JString<'local>,
    output_path_jstring: JString<'local>,
) -> bool {
    ensure_initialized();
    info!("Java called convertToMp3");

    // --- Get Input/Output Paths ---
    let input_path_java_str = match env.get_string(&input_path_jstring) {
        Ok(s) => s,
        Err(e) => {
            error!("Error getting input path string from JNIEnv: {:?}", e);
            return false;
        }
    };
    let input_path_rust: String = match input_path_java_str.try_into() {
        Ok(s) => s,
        Err(e) => {
            error!(
                "Error converting input path JavaStr to Rust String: {:?}",
                e
            );
            return false;
        }
    };
    let output_path_java_str = match env.get_string(&output_path_jstring) {
        Ok(s) => s,
        Err(e) => {
            error!("Error getting output path string from JNIEnv: {:?}", e);
            return false;
        }
    };
    let output_path_rust: String = match output_path_java_str.try_into() {
        Ok(s) => s,
        Err(e) => {
            error!(
                "Error converting output path JavaStr to Rust String: {:?}",
                e
            );
            return false;
        }
    };
    info!("  Input Path: {}", input_path_rust);
    info!("  Output Path: {}", output_path_rust);

    match convert_to_mp3(&input_path_rust, &output_path_rust) {
        Ok(_) => true,
        Err(e) => {
            error!("Conversion failed: {:?}", e);
            false
        }
    }
}

// --- Function to probe decodability & audio presence ---
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_com_gmail_xuoxod_nexusbridge_NativeConverter_probeInput<'local>(
    mut env: JNIEnv<'local>,
    _object: JClass<'local>,
    input_path_jstring: JString<'local>,
) -> bool {
    ensure_initialized();
    info!("Java called probeInput");

    let input_path_java_str = match env.get_string(&input_path_jstring) {
        Ok(s) => s,
        Err(e) => {
            error!("Error getting input path string from JNIEnv: {:?}", e);
            return false;
        }
    };
    let input_path_rust: String = match input_path_java_str.try_into() {
        Ok(s) => s,
        Err(e) => {
            error!(
                "Error converting input path JavaStr to Rust String: {:?}",
                e
            );
            return false;
        }
    };

    match ensure_decodable_with_audio(&input_path_rust) {
        Ok(_) => true,
        Err(e) => {
            error!("Probe failed: {:?}", e);
            false
        }
    }
}
// --- Standard Rust Tests ---
#[cfg(test)]
mod tests {
    #[test]
    fn it_works() {
        // Initialize logger for Rust tests too, if needed
        // let _ = env_logger::builder().is_test(true).try_init();
        let result = 2 + 2;
        assert_eq!(result, 4);
    }
}
