use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use std::convert::TryInto;
use std::sync::Once;

use env_logger;
use log::{error, info, LevelFilter};

use ffmpeg_next as ffmpeg;

pub mod converter;
pub use converter::{convert_to_mp3, ensure_decodable_with_audio};

const BACKEND_VERSION: &str = "0.1.0-alpha";

static FFMPEG_INIT: Once = Once::new();
static LOGGER_INIT: Once = Once::new();
static mut DESIRED_LOG_LEVEL: Option<LevelFilter> = None;

fn init_ffmpeg() {
    FFMPEG_INIT.call_once(|| {
        info!("Initializing FFmpeg...");
        match ffmpeg::init() {
            Ok(_) => info!("  FFmpeg initialized successfully."),
            Err(e) => error!("  ERROR initializing FFmpeg: {:?}", e),
        }
    });
}

fn init_logger() {
    LOGGER_INIT.call_once(|| {
        let mut builder =
            env_logger::Builder::from_env(env_logger::Env::default().default_filter_or("info"));
        builder.target(env_logger::Target::Stderr);
        unsafe {
            if let Some(level) = DESIRED_LOG_LEVEL {
                builder.filter_level(level);
                log::set_max_level(level);
            }
        }
        let _ = builder.try_init();
        info!("Logger initialized (or already set by host).");
    });
}

pub(crate) fn ensure_initialized() {
    init_logger();
    init_ffmpeg();
}

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
    let already_inited = LOGGER_INIT.is_completed();
    if !already_inited {
        unsafe {
            DESIRED_LOG_LEVEL = Some(level);
        }
    }
    if already_inited {
        log::set_max_level(level);
    }
}

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

#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_com_gmail_xuoxod_nexusbridge_NativeConverter_convertToMp3<'local>(
    mut env: JNIEnv<'local>,
    _object: JClass<'local>,
    input_path_jstring: JString<'local>,
    output_path_jstring: JString<'local>,
) -> bool {
    ensure_initialized();
    info!("Java called convertToMp3");

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

#[cfg(test)]
mod tests {
    #[test]
    fn it_works() {
        let result = 2 + 2;
        assert_eq!(result, 4);
    }
}
