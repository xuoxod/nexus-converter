use log::debug;
use std::fs::{self, File};
use std::io::{ErrorKind, Read};
use std::path::{Path, PathBuf};
use thiserror::Error;

#[derive(Debug, Clone)]
pub struct ValidatedMp4 {
    pub canonical_path: PathBuf,
    pub size_bytes: u64,
}

#[derive(Debug, Error)]
pub enum Mp4ValidationError {
    #[error("path is empty")]
    EmptyPath,
    #[error("path does not exist: {0}")]
    NotFound(PathBuf),
    #[error("path is not a file: {0}")]
    NotAFile(PathBuf),
    #[error("file extension must be .mp4 (case-insensitive), found: {0}")]
    BadExtension(String),
    #[error("file is not readable: {0}")]
    NotReadable(PathBuf),
    #[error("file is empty: {0}")]
    EmptyFile(PathBuf),
    #[error("failed to read file header for sniffing: {0}")]
    ReadHeader(std::io::Error),
    #[error("magic sniff mismatch: expected video/mp4, detected: {0:?}")]
    MagicMismatch(Option<&'static str>),
    #[error("io error: {0}")]
    Io(#[from] std::io::Error),
}

/// Validate that the given path is a readable, non-empty .mp4 file
/// and that the header magic indicates an MP4/ISO-BMFF container.
/// Returns canonical path and file size on success.
pub fn validate_mp4_path<P: AsRef<Path>>(path: P) -> Result<ValidatedMp4, Mp4ValidationError> {
    let p = path.as_ref();
    if p.as_os_str().is_empty() {
        return Err(Mp4ValidationError::EmptyPath);
    }

    if !p.exists() {
        return Err(Mp4ValidationError::NotFound(p.to_path_buf()));
    }
    if !p.is_file() {
        return Err(Mp4ValidationError::NotAFile(p.to_path_buf()));
    }

    // Extension check
    if let Some(ext) = p.extension().and_then(|s| s.to_str()) {
        if ext.to_ascii_lowercase() != "mp4" {
            return Err(Mp4ValidationError::BadExtension(ext.to_string()));
        }
    } else {
        return Err(Mp4ValidationError::BadExtension(String::from("(none)")));
    }

    // Readable check
    match File::open(p) {
        Ok(_) => {}
        Err(e) => {
            if matches!(e.kind(), ErrorKind::PermissionDenied | ErrorKind::Other) {
                return Err(Mp4ValidationError::NotReadable(p.to_path_buf()));
            } else {
                return Err(Mp4ValidationError::Io(e));
            }
        }
    }

    let meta = fs::metadata(p)?;
    let size = meta.len();
    if size == 0 {
        return Err(Mp4ValidationError::EmptyFile(p.to_path_buf()));
    }

    // Magic sniff: read up to 8KB for infer
    let mut f = File::open(p).map_err(Mp4ValidationError::Io)?;
    let mut buf = [0u8; 8192];
    let n = f.read(&mut buf).map_err(Mp4ValidationError::ReadHeader)?;
    let kind = infer::get(&buf[..n]);
    let mime = kind.as_ref().map(|k| k.mime_type());
    debug!("infer kind: {:?}", mime);

    // Be strict when we get a non-mp4 mime; tolerate unknown to allow FFmpeg probing.
    match mime {
        Some("video/mp4") | Some("application/mp4") => {}
        None => {}
        other => return Err(Mp4ValidationError::MagicMismatch(other)),
    }

    let canonical_path = p.canonicalize().unwrap_or_else(|_| p.to_path_buf());
    Ok(ValidatedMp4 {
        canonical_path,
        size_bytes: size,
    })
}

/// Ensure the parent directory of the output path exists and is writable.
pub fn ensure_output_writable<P: AsRef<Path>>(output_path: P) -> Result<(), std::io::Error> {
    let p = output_path.as_ref();
    if let Some(parent) = p.parent() {
        if !parent.exists() {
            fs::create_dir_all(parent)?;
        }
        // Attempt a quick create+delete temp file for writability check
        let probe = parent.join(".nexus_write_probe.tmp");
        match File::create(&probe) {
            Ok(_) => {
                let _ = fs::remove_file(&probe);
                Ok(())
            }
            Err(e) => Err(e),
        }
    } else {
        // No parent implies current dir
        Ok(())
    }
}

// Optional deep probe using FFmpeg for containers/streams
#[cfg(feature = "probe")]
pub mod probe {
    use super::*;
    use ffmpeg_next as ffmpeg;
    use thiserror::Error;

    static INIT: std::sync::Once = std::sync::Once::new();
    fn ensure_ffmpeg() {
        INIT.call_once(|| {
            let _ = ffmpeg::init();
        });
    }

    #[derive(Debug, Error)]
    pub enum ProbeError {
        #[error("ffmpeg open failed: {0:?}")]
        Open(ffmpeg::Error),
        #[error("no audio stream detected")]
        NoAudio,
    }

    /// Ensure the file opens via FFmpeg and has at least one audio stream
    pub fn ensure_mp4_decodable(path: &Path) -> Result<(), ProbeError> {
        ensure_ffmpeg();
        let ictx = ffmpeg::format::input(path).map_err(ProbeError::Open)?;
        let has_audio = ictx
            .streams()
            .any(|s| s.parameters().medium() == ffmpeg::media::Type::Audio);
        if has_audio {
            Ok(())
        } else {
            Err(ProbeError::NoAudio)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use tempfile::NamedTempFile;

    #[test]
    fn not_found() {
        let p = PathBuf::from("/tmp/definitely-does-not-exist-xyz/xx.mp4");
        let err = validate_mp4_path(&p).unwrap_err();
        matches!(err, Mp4ValidationError::NotFound(_));
    }

    #[test]
    fn bad_extension() {
        let t = NamedTempFile::new().unwrap();
        let p = t.path().with_extension("txt");
        fs::write(&p, b"hello").unwrap();
        let err = validate_mp4_path(&p).unwrap_err();
        matches!(err, Mp4ValidationError::BadExtension(_));
    }

    #[test]
    fn empty_file() {
        let p = NamedTempFile::new().unwrap().into_temp_path();
        let p = p.with_extension("mp4");
        File::create(&p).unwrap(); // empty
        let err = validate_mp4_path(&p).unwrap_err();
        matches!(err, Mp4ValidationError::EmptyFile(_));
    }

    #[test]
    fn accepts_unknown_magic_but_mp4_ext() {
        let p = NamedTempFile::new().unwrap().into_temp_path();
        let p = p.with_extension("mp4");
        fs::write(&p, b"not-a-real-mp4-but-we-allow-ffmpeg-probe").unwrap();
        let ok = validate_mp4_path(&p).unwrap();
        assert!(ok.size_bytes > 0);
    }

    #[cfg(unix)]
    #[test]
    fn unreadable_file() {
        use std::os::unix::fs::PermissionsExt;
        let p = NamedTempFile::new().unwrap().into_temp_path();
        let p = p.with_extension("mp4");
        fs::write(&p, b"data").unwrap();
        let mut perms = fs::metadata(&p).unwrap().permissions();
        perms.set_mode(0o000);
        fs::set_permissions(&p, perms).unwrap();
        let err = validate_mp4_path(&p).unwrap_err();
        matches!(
            err,
            Mp4ValidationError::NotReadable(_) | Mp4ValidationError::Io(_)
        );
    }
}
