use ffmpeg_next as ffmpeg;
use ffmpeg_next::Rescale;
use log::info;

use crate::ensure_initialized;

/// Quick probe to ensure the input is decodable and contains at least one audio stream.
pub fn ensure_decodable_with_audio(input_path: &str) -> Result<(), ffmpeg::Error> {
    ensure_initialized();
    info!("probe: input={}", input_path);
    let ictx = ffmpeg::format::input(&input_path)?;
    for stream in ictx.streams() {
        let codec_params = stream.parameters();
        if codec_params.medium() == ffmpeg::media::Type::Audio {
            let ctx = ffmpeg::codec::context::Context::from_parameters(codec_params.clone())?;
            let _decoder = ctx.decoder().audio()?;
            return Ok(());
        }
    }
    Err(ffmpeg::Error::Bug)
}

/// Public reusable conversion API for Rust callers (CLI, tests, etc.)
/// Converts the given audio/MP4 input path to an MP3 at the given output path.
pub fn convert_to_mp3(input_path: &str, output_path: &str) -> Result<(), ffmpeg::Error> {
    ensure_initialized();
    info!("convert_to_mp3: input={}, output={}", input_path, output_path);

    let mut ictx = ffmpeg::format::input(&input_path)?;
    info!(
        "  Format: {}, Duration: {:.2}s",
        ictx.format().name(),
        ictx.duration() as f64 / ffmpeg::ffi::AV_TIME_BASE as f64
    );

    let mut stream_index_opt: Option<usize> = None;
    let mut decoder_opt: Option<ffmpeg::decoder::Audio> = None;
    for stream in ictx.streams() {
        let current_index = stream.index();
        let codec_params = stream.parameters();
        if codec_params.medium() == ffmpeg::media::Type::Audio {
            if let Ok(codec_context) = ffmpeg::codec::context::Context::from_parameters(codec_params.clone()) {
                if let Ok(audio_decoder) = codec_context.decoder().audio() {
                    stream_index_opt = Some(current_index);
                    decoder_opt = Some(audio_decoder);
                    break;
                }
            }
        }
    }
    let stream_index = stream_index_opt.ok_or(ffmpeg::Error::Bug)?;
    let mut decoder = decoder_opt.ok_or(ffmpeg::Error::Bug)?;

    let in_channel_layout = if decoder.channel_layout().is_empty() {
        ffmpeg::channel_layout::ChannelLayout::default(decoder.channels() as i32)
    } else {
        decoder.channel_layout()
    };
    decoder.set_channel_layout(in_channel_layout);

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

    let mut resampler: Option<ffmpeg::software::resampling::Context> = None;
    if decoder.format() != opened_encoder.format()
        || decoder.rate() != opened_encoder.rate()
        || in_channel_layout != opened_encoder.channel_layout()
    {
        let swr_ctx = ffmpeg::software::resampling::context::Context::get(
            decoder.format(),
            in_channel_layout,
            decoder.rate(),
            opened_encoder.format(),
            opened_encoder.channel_layout(),
            opened_encoder.rate(),
        )?;
        resampler = Some(swr_ctx);
    }

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

    'main_loop: loop {
        let mut reached_input_eof = false;
        match decoder.receive_frame(&mut decoded_frame) {
            Ok(_) => {
                if decoded_frame.channel_layout().is_empty() {
                    decoded_frame.set_channel_layout(in_channel_layout);
                }
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
            Err(ffmpeg::Error::Eof) => break 'main_loop,
            Err(e) => return Err(e),
        }

        while audio_buffer.iter().all(|buf| buf.len() >= encoder_frame_size) {
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
            drain_encoder_packets(&mut opened_encoder, &mut encoded_packet, &mut octx, encoder_time_base, out_time_base)?;
        }

        if reached_input_eof && audio_buffer.iter().all(|buf| buf.len() < encoder_frame_size) {
            break 'main_loop;
        }
    }

    if audio_buffer.iter().any(|buf| !buf.is_empty()) {
        let remaining_samples = audio_buffer[0].len();
        if !audio_buffer.iter().all(|buf| buf.len() == remaining_samples) {
            return Err(ffmpeg::Error::Bug);
        }
        let mut final_frame = ffmpeg::frame::Audio::new(
            opened_encoder.format(),
            encoder_frame_size,
            opened_encoder.channel_layout(),
        );
        let ret = unsafe { ffmpeg::ffi::av_frame_get_buffer(final_frame.as_mut_ptr(), 0) };
        if ret < 0 {
            return Err(ffmpeg::Error::from(ret));
        }
        final_frame.set_rate(opened_encoder.rate());
        for i in 0..num_channels {
            let dest_plane = final_frame.data_mut(i);
            let dest_samples_i16: &mut [i16] = unsafe {
                std::slice::from_raw_parts_mut(
                    dest_plane.as_mut_ptr() as *mut i16,
                    encoder_frame_size,
                )
            };
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

    opened_encoder.send_eof()?;
    drain_encoder_packets(&mut opened_encoder, &mut encoded_packet, &mut octx, encoder_time_base, out_time_base)?;
    octx.write_trailer()?;
    Ok(())
}

fn drain_encoder_packets(
    opened_encoder: &mut ffmpeg::encoder::Audio,
    encoded_packet: &mut ffmpeg::Packet,
    octx: &mut ffmpeg::format::context::Output,
    encoder_time_base: ffmpeg::Rational,
    out_time_base: ffmpeg::Rational,
) -> Result<(), ffmpeg::Error> {
    loop {
        match opened_encoder.receive_packet(encoded_packet) {
            Ok(_) => {
                encoded_packet.set_stream(0);
                let original_pts = encoded_packet.pts().unwrap_or(0);
                encoded_packet.set_pts(Some(original_pts.rescale(encoder_time_base, out_time_base)));
                if let Some(dts) = encoded_packet.dts() {
                    encoded_packet.set_dts(Some(dts.rescale(encoder_time_base, out_time_base)));
                }
                encoded_packet.write_interleaved(octx)?;
            }
            Err(ffmpeg::Error::Other {
                errno: ffmpeg::ffi::EAGAIN,
            })
            | Err(ffmpeg::Error::Eof) => break,
            Err(e) => return Err(e),
        }
    }
    Ok(())
}
