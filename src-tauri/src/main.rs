#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::VecDeque;
use std::env;
use std::error::Error;
use std::fs;
use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::{TcpStream, ToSocketAddrs};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, Command, Stdio};
use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};
use std::sync::mpsc::{self, Receiver};
use std::sync::{Arc, Mutex, OnceLock};
use std::thread;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tauri::menu::{Menu, MenuItem};
use tauri::tray::{MouseButton, TrayIconBuilder, TrayIconEvent};
use tauri::{Manager, WebviewUrl, WebviewWindowBuilder};
use tiny_http::{Header, Method, Request, Response, Server, StatusCode};
use uuid::Uuid;

#[cfg(windows)]
use std::os::windows::process::CommandExt;

const APP_NAME: &str = "LUMI to GPT";
const VERSION: &str = "1.0.0";
const HOST: &str = "127.0.0.1";
const DEFAULT_PORT: u16 = 32123;
const DEFAULT_LUMI_APP: &str = r"D:\Steam\steamapps\common\Little LUMI\app";
const DEFAULT_CODEX_MODEL: &str = "gpt-5.6-luna";
const DEFAULT_CODEX_EFFORT: &str = "low";
const LATEST_RELEASE_URL: &str = "https://github.com/snowtie/LUMI-to-GPT/releases/latest";
const REQUEST_TIMEOUT: Duration = Duration::from_secs(190);
const MAX_BODY_BYTES: u64 = 8 * 1024 * 1024;
const MAX_AUDIO_BYTES: u64 = 64 * 1024 * 1024;
const GPT_SOVITS_BALANCED_IDLE_TIMEOUT: Duration = Duration::from_secs(10 * 60);
const GPT_SOVITS_ULTRA_SAVER_IDLE_TIMEOUT: Duration = Duration::from_secs(60);
const GPT_SOVITS_START_TIMEOUT: Duration = Duration::from_secs(120);
const VOICE_MANAGED_KEY: &str = "lumi_to_gpt.voice.managed";
const GPT_SOVITS_COMPAT_RUNNER: &str = r#"import runpy
import sys
from pathlib import Path

api_path = Path(sys.argv[1]).resolve()
runtime_root = api_path.parent
sys.path.insert(0, str(runtime_root))
sys.path.insert(0, str(runtime_root / "GPT_SoVITS"))

from TTS_infer_pack.TextPreprocessor import TextPreprocessor

original_get_phones_and_bert = TextPreprocessor.get_phones_and_bert

def get_phones_and_bert(self, text, language, version="v1", final=False):
    if str(language).lower() in {"ko", "all_ko"} and version == "v1":
        version = "v2"
    return original_get_phones_and_bert(self, text, language, version, final)

TextPreprocessor.get_phones_and_bert = get_phones_and_bert
sys.argv = [str(api_path), *sys.argv[2:]]
runpy.run_path(str(api_path), run_name="__main__")
"#;

type AppResult<T> = Result<T, Box<dyn Error + Send + Sync>>;

#[derive(Clone, Debug, Deserialize, Serialize)]
#[serde(default)]
struct Settings {
    port: u16,
    lumi_app_dir: String,
    voice: GptSovitsSettings,
    lumi_tts_restore_enabled: Option<bool>,
}

#[derive(Clone, Debug, Deserialize, PartialEq, Serialize)]
#[serde(default)]
struct GptSovitsSettings {
    enabled: bool,
    power_mode: VoicePowerMode,
    base_url: String,
    runtime_dir: String,
    gpt_weights_path: String,
    sovits_weights_path: String,
    reference_audio_path: String,
    prompt_text: String,
    text_language: String,
    prompt_language: String,
    speed_factor: f32,
}

#[derive(Clone, Copy, Debug, Default, Deserialize, PartialEq, Serialize)]
#[serde(rename_all = "snake_case")]
enum VoicePowerMode {
    UltraSaver,
    #[default]
    #[serde(other)]
    Balanced,
}

impl Default for GptSovitsSettings {
    fn default() -> Self {
        Self {
            enabled: false,
            power_mode: VoicePowerMode::Balanced,
            base_url: "http://127.0.0.1:9880".to_owned(),
            runtime_dir: String::new(),
            gpt_weights_path: String::new(),
            sovits_weights_path: String::new(),
            reference_audio_path: String::new(),
            prompt_text: String::new(),
            text_language: "ko".to_owned(),
            prompt_language: "ko".to_owned(),
            speed_factor: 1.0,
        }
    }
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            port: DEFAULT_PORT,
            lumi_app_dir: DEFAULT_LUMI_APP.to_owned(),
            voice: GptSovitsSettings::default(),
            lumi_tts_restore_enabled: None,
        }
    }
}

fn local_data_dir() -> PathBuf {
    let root = env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .unwrap_or_else(|| env::temp_dir().join("LumiToGPT"));
    root.join("LumiToGPT")
}

fn settings_path() -> PathBuf {
    local_data_dir().join("settings.json")
}

fn settings_backup_path() -> PathBuf {
    local_data_dir().join("settings.json.bak")
}

fn read_settings(path: &Path) -> Option<Settings> {
    fs::read_to_string(path)
        .ok()
        .and_then(|text| serde_json::from_str(&text).ok())
}

fn write_settings_unlocked(settings: &Settings) -> AppResult<()> {
    let path = settings_path();
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let text = serde_json::to_string_pretty(settings)?;
    let temporary = path.with_extension("json.tmp");
    fs::write(&temporary, format!("{text}\n"))?;
    if read_settings(&path).is_some() {
        fs::copy(&path, settings_backup_path())?;
    }
    if path.exists() {
        fs::remove_file(&path)?;
    }
    fs::rename(temporary, path)?;
    Ok(())
}

fn settings_io_lock() -> &'static Mutex<()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
}

fn save_settings(settings: &Settings) -> AppResult<()> {
    let _guard = settings_io_lock()
        .lock()
        .map_err(|_| "설정 파일 잠금이 손상되었습니다.")?;
    write_settings_unlocked(settings)
}

fn load_settings() -> Settings {
    let _guard = match settings_io_lock().lock() {
        Ok(guard) => guard,
        Err(_) => return Settings::default(),
    };
    let path = settings_path();
    if let Some(settings) = read_settings(&path) {
        let backup = settings_backup_path();
        if !backup.exists() {
            let _ = fs::copy(&path, backup);
        }
        return settings;
    }
    let settings = read_settings(&settings_backup_path()).unwrap_or_default();
    let _ = write_settings_unlocked(&settings);
    settings
}

fn is_lumi_app_dir(path: &Path) -> bool {
    path.join("Shimeji-ee.jar").is_file() && path.join("speech").is_dir()
}

fn lumi_app_dir(settings: &Settings) -> PathBuf {
    if let Some(path) = env::var_os("LUMI_APP_DIR").map(PathBuf::from) {
        return path;
    }

    let configured = PathBuf::from(&settings.lumi_app_dir);
    if is_lumi_app_dir(&configured) {
        return configured;
    }

    for drive in b'C'..=b'Z' {
        for suffix in [
            r"Steam\steamapps\common\Little LUMI\app",
            r"Program Files (x86)\Steam\steamapps\common\Little LUMI\app",
            r"Program Files\Steam\steamapps\common\Little LUMI\app",
        ] {
            let candidate = PathBuf::from(format!("{}:\\{suffix}", drive as char));
            if is_lumi_app_dir(&candidate) {
                return candidate;
            }
        }
    }
    configured
}

fn is_lumi_chat_unlocked(app_dir: &Path) -> bool {
    app_dir.join("conf").join("mod_ai_chat.txt").is_file()
}

fn update_properties(path: &Path, updates: &[(&str, &str)]) -> AppResult<()> {
    let original = fs::read_to_string(path).unwrap_or_default();
    let mut seen = vec![false; updates.len()];
    let mut lines = Vec::new();
    for line in original.lines() {
        let key = line
            .split_once('=')
            .map(|(key, _)| key.trim())
            .unwrap_or_default();
        if let Some((index, (update_key, update_value))) = updates
            .iter()
            .enumerate()
            .find(|(_, (update_key, _))| key == *update_key)
        {
            lines.push(format!("{update_key}={update_value}"));
            seen[index] = true;
        } else {
            lines.push(line.to_owned());
        }
    }
    for (index, (key, value)) in updates.iter().enumerate() {
        if !seen[index] {
            lines.push(format!("{key}={value}"));
        }
    }
    let updated = format!("{}\n", lines.join("\n"));
    if updated == original.replace("\r\n", "\n") {
        return Ok(());
    }
    if path.is_file() {
        let backup = path.with_extension("properties.lumi-to-gpt.bak");
        if !backup.exists() {
            fs::copy(path, backup)?;
        }
    }
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(path, updated)?;
    Ok(())
}

fn configure_lumi_chat(settings: &Settings) -> AppResult<PathBuf> {
    let app_dir = lumi_app_dir(settings);
    if !is_lumi_app_dir(&app_dir) {
        return Err(format!(
            "Little LUMI 설치 폴더를 찾지 못했습니다: {}",
            app_dir.display()
        )
        .into());
    }
    if !is_lumi_chat_unlocked(&app_dir) {
        return Err(
            "필수 창작마당 항목 'LUMI Chat'을 먼저 구독하고 Little LUMI를 한 번 실행해 주세요."
                .into(),
        );
    }
    let ai_settings = app_dir.join("conf").join("ai.properties");
    let base_url = format!("http://{HOST}:{}/v1", settings.port);
    let has_gpt_web_key = fs::read_to_string(&ai_settings).is_ok_and(|properties| {
        properties.lines().any(|line| {
            line.split_once('=').is_some_and(|(key, value)| {
                key.trim() == "llm.key.gpt_web" && !value.trim().is_empty()
            })
        })
    });
    let mut updates = vec![
        ("llm.provider".to_owned(), "gpt_web".to_owned()),
        ("llm.base.gpt_web".to_owned(), base_url),
        (
            "llm.model.gpt_web".to_owned(),
            DEFAULT_CODEX_MODEL.to_owned(),
        ),
    ];
    if !has_gpt_web_key {
        updates.push(("llm.key.gpt_web".to_owned(), "lumi-to-gpt".to_owned()));
    }
    for key in ["chatter.enabled", "screenwatch.enabled"] {
        if property_value(&ai_settings, key).is_none() {
            updates.push((key.to_owned(), "false".to_owned()));
        }
    }
    for (key, value) in [
        ("tts.gpt_sovits.base", settings.voice.base_url.as_str()),
        (
            "tts.gpt_sovits.runtime",
            settings.voice.runtime_dir.as_str(),
        ),
        (
            "tts.gpt_sovits.gpt_weights",
            settings.voice.gpt_weights_path.as_str(),
        ),
        (
            "tts.gpt_sovits.sovits_weights",
            settings.voice.sovits_weights_path.as_str(),
        ),
        (
            "tts.gpt_sovits.reference_audio",
            settings.voice.reference_audio_path.as_str(),
        ),
        (
            "tts.gpt_sovits.reference_text",
            settings.voice.prompt_text.as_str(),
        ),
        (
            "tts.gpt_sovits.text_language",
            settings.voice.text_language.as_str(),
        ),
        (
            "tts.gpt_sovits.prompt_language",
            settings.voice.prompt_language.as_str(),
        ),
    ] {
        if property_value(&ai_settings, key).is_none() {
            updates.push((key.to_owned(), java_property_value(value)));
        }
    }
    if property_value(&ai_settings, "tts.gpt_sovits.power_mode").is_none() {
        let power_mode = match settings.voice.power_mode {
            VoicePowerMode::Balanced => "balanced",
            VoicePowerMode::UltraSaver => "ultra_saver",
        };
        updates.push((
            "tts.gpt_sovits.power_mode".to_owned(),
            power_mode.to_owned(),
        ));
    }
    if property_value(&ai_settings, "tts.gpt_sovits.speed").is_none() {
        updates.push((
            "tts.gpt_sovits.speed".to_owned(),
            settings.voice.speed_factor.to_string(),
        ));
    }
    let update_refs = updates
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    update_properties(&ai_settings, &update_refs)?;
    Ok(ai_settings)
}

fn java_property_value(value: &str) -> String {
    value
        .replace('\\', "\\\\")
        .replace(':', "\\:")
        .replace('=', "\\=")
}

fn migrate_legacy_voice_settings(settings: &mut Settings) -> AppResult<bool> {
    if settings.lumi_tts_restore_enabled.is_none() {
        return Ok(false);
    }
    let ai_settings = lumi_app_dir(settings).join("conf").join("ai.properties");
    let enabled = if settings.voice.enabled {
        "true"
    } else {
        "false"
    };
    update_properties(
        &ai_settings,
        &[("tts.enabled", enabled), ("tts.provider", "gpt_sovits")],
    )?;
    settings.lumi_tts_restore_enabled = None;
    Ok(true)
}

fn property_value(path: &Path, key: &str) -> Option<String> {
    fs::read_to_string(path).ok()?.lines().find_map(|line| {
        let (candidate, value) = line.split_once('=')?;
        (candidate.trim() == key).then(|| decode_java_property(value.trim()))
    })
}

fn decode_java_property(value: &str) -> String {
    let mut decoded = String::new();
    let mut chars = value.chars();
    while let Some(character) = chars.next() {
        if character != '\\' {
            decoded.push(character);
            continue;
        }
        match chars.next() {
            Some('t') => decoded.push('\t'),
            Some('n') => decoded.push('\n'),
            Some('r') => decoded.push('\r'),
            Some('f') => decoded.push('\u{000C}'),
            Some('u') => {
                let digits = chars.by_ref().take(4).collect::<String>();
                match u32::from_str_radix(&digits, 16)
                    .ok()
                    .and_then(char::from_u32)
                {
                    Some(character) => decoded.push(character),
                    None => {
                        decoded.push_str("\\u");
                        decoded.push_str(&digits);
                    }
                }
            }
            Some(character) => decoded.push(character),
            None => decoded.push('\\'),
        }
    }
    decoded
}

fn property_bool(path: &Path, key: &str) -> bool {
    property_value(path, key).is_some_and(|value| value.eq_ignore_ascii_case("true"))
}

fn gpt_sovits_settings_from_lumi(settings: &Settings) -> GptSovitsSettings {
    let ai_settings = lumi_app_dir(settings).join("conf").join("ai.properties");
    let mut voice = settings.voice.clone();
    voice.enabled = property_bool(&ai_settings, "tts.enabled")
        && property_value(&ai_settings, "tts.provider").as_deref() == Some("gpt_sovits");
    for (key, target) in [
        ("tts.gpt_sovits.base", &mut voice.base_url),
        ("tts.gpt_sovits.runtime", &mut voice.runtime_dir),
        ("tts.gpt_sovits.gpt_weights", &mut voice.gpt_weights_path),
        (
            "tts.gpt_sovits.sovits_weights",
            &mut voice.sovits_weights_path,
        ),
        (
            "tts.gpt_sovits.reference_audio",
            &mut voice.reference_audio_path,
        ),
        ("tts.gpt_sovits.reference_text", &mut voice.prompt_text),
        ("tts.gpt_sovits.text_language", &mut voice.text_language),
        ("tts.gpt_sovits.prompt_language", &mut voice.prompt_language),
    ] {
        if let Some(value) = property_value(&ai_settings, key) {
            *target = value;
        }
    }
    voice.power_mode = match property_value(&ai_settings, "tts.gpt_sovits.power_mode").as_deref() {
        Some("ultra_saver") => VoicePowerMode::UltraSaver,
        _ => VoicePowerMode::Balanced,
    };
    if let Some(speed) = property_value(&ai_settings, "tts.gpt_sovits.speed")
        .and_then(|value| value.parse::<f32>().ok())
    {
        voice.speed_factor = speed;
    }
    voice
}

fn voice_configuration_complete(voice: &GptSovitsSettings) -> bool {
    [
        voice.runtime_dir.as_str(),
        voice.gpt_weights_path.as_str(),
        voice.sovits_weights_path.as_str(),
        voice.reference_audio_path.as_str(),
    ]
    .iter()
    .all(|value| !value.trim().is_empty())
}

fn gpt_sovits_configuration(
    runtime_dir: String,
    gpt_weights_path: String,
    sovits_weights_path: String,
    reference_audio_path: String,
    prompt_text: String,
) -> AppResult<GptSovitsSettings> {
    let mut voice = GptSovitsSettings {
        enabled: true,
        power_mode: VoicePowerMode::Balanced,
        runtime_dir,
        gpt_weights_path,
        sovits_weights_path,
        reference_audio_path,
        prompt_text,
        ..GptSovitsSettings::default()
    };
    voice.base_url = "http://127.0.0.1:9880".to_owned();
    resolve_gpt_sovits_runtime(&voice)?;
    for path in [
        &voice.gpt_weights_path,
        &voice.sovits_weights_path,
        &voice.reference_audio_path,
    ] {
        if !Path::new(path).is_file() {
            return Err(format!("GPT-SoVITS 파일을 찾지 못했습니다: {path}").into());
        }
    }
    if voice.prompt_text.trim().is_empty() {
        return Err("GPT-SoVITS 참조 대사가 비어 있습니다.".into());
    }
    Ok(voice)
}

fn configure_gpt_sovits(
    settings: &mut Settings,
    runtime_dir: String,
    gpt_weights_path: String,
    sovits_weights_path: String,
    reference_audio_path: String,
    prompt_text: String,
) -> AppResult<()> {
    settings.voice = gpt_sovits_configuration(
        runtime_dir,
        gpt_weights_path,
        sovits_weights_path,
        reference_audio_path,
        prompt_text,
    )?;
    settings.lumi_tts_restore_enabled = None;
    restore_voice_settings_to_lumi(settings, &settings.voice)?;
    save_settings(settings)
}

fn restore_voice_settings_to_lumi(settings: &Settings, voice: &GptSovitsSettings) -> AppResult<()> {
    let ai_settings = lumi_app_dir(settings).join("conf").join("ai.properties");
    let power_mode = match voice.power_mode {
        VoicePowerMode::Balanced => "balanced",
        VoicePowerMode::UltraSaver => "ultra_saver",
    };
    let mut updates = vec![
        (VOICE_MANAGED_KEY.to_owned(), "true".to_owned()),
        (
            "tts.gpt_sovits.base".to_owned(),
            java_property_value(&voice.base_url),
        ),
        (
            "tts.gpt_sovits.runtime".to_owned(),
            java_property_value(&voice.runtime_dir),
        ),
        (
            "tts.gpt_sovits.gpt_weights".to_owned(),
            java_property_value(&voice.gpt_weights_path),
        ),
        (
            "tts.gpt_sovits.sovits_weights".to_owned(),
            java_property_value(&voice.sovits_weights_path),
        ),
        (
            "tts.gpt_sovits.reference_audio".to_owned(),
            java_property_value(&voice.reference_audio_path),
        ),
        (
            "tts.gpt_sovits.reference_text".to_owned(),
            java_property_value(&voice.prompt_text),
        ),
        (
            "tts.gpt_sovits.text_language".to_owned(),
            java_property_value(&voice.text_language),
        ),
        (
            "tts.gpt_sovits.prompt_language".to_owned(),
            java_property_value(&voice.prompt_language),
        ),
        (
            "tts.gpt_sovits.power_mode".to_owned(),
            power_mode.to_owned(),
        ),
        (
            "tts.gpt_sovits.speed".to_owned(),
            voice.speed_factor.to_string(),
        ),
    ];
    if voice.enabled {
        updates.push(("tts.enabled".to_owned(), "true".to_owned()));
        updates.push(("tts.provider".to_owned(), "gpt_sovits".to_owned()));
    }
    let update_refs = updates
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    update_properties(&ai_settings, &update_refs)
}

fn synchronize_voice_settings(settings: &mut Settings) -> AppResult<bool> {
    let app_dir = lumi_app_dir(settings);
    if !is_lumi_app_dir(&app_dir) {
        return Ok(false);
    }
    let ai_settings = app_dir.join("conf").join("ai.properties");
    let managed = property_bool(&ai_settings, VOICE_MANAGED_KEY);
    let native_voice = gpt_sovits_settings_from_lumi(settings);
    let local_complete = voice_configuration_complete(&settings.voice);
    let native_complete = voice_configuration_complete(&native_voice);

    if local_complete && (!managed || !native_complete) {
        restore_voice_settings_to_lumi(settings, &settings.voice)?;
        return Ok(false);
    }
    if native_complete {
        let changed = settings.voice != native_voice;
        settings.voice = native_voice;
        if !managed {
            update_properties(&ai_settings, &[(VOICE_MANAGED_KEY, "true")])?;
        }
        return Ok(changed);
    }
    Ok(false)
}

fn persist_voice_settings(settings: &Settings, voice: &GptSovitsSettings) -> AppResult<()> {
    {
        let _guard = settings_io_lock()
            .lock()
            .map_err(|_| "설정 파일 잠금이 손상되었습니다.")?;
        let mut persistent = read_settings(&settings_path())
            .or_else(|| read_settings(&settings_backup_path()))
            .unwrap_or_else(|| settings.clone());
        persistent.port = settings.port;
        persistent.lumi_app_dir.clone_from(&settings.lumi_app_dir);
        persistent.voice = voice.clone();
        write_settings_unlocked(&persistent)?;
    }
    let ai_settings = lumi_app_dir(settings).join("conf").join("ai.properties");
    update_properties(&ai_settings, &[(VOICE_MANAGED_KEY, "true")])
}

fn lumi_quiet_now(settings: &Settings) -> bool {
    property_bool(
        &lumi_app_dir(settings)
            .join("conf")
            .join("settings.properties"),
        "lumi.focus.enabled",
    )
}

fn gpt_sovits_base_url(base_url: &str) -> AppResult<String> {
    let mut base_url = base_url.trim().trim_end_matches('/');
    if base_url.is_empty() {
        return Err("GPT-SoVITS 서버 주소를 넣어 주세요.".into());
    }
    if base_url.to_ascii_lowercase().ends_with("/tts") {
        base_url = &base_url[..base_url.len() - 4];
    }
    let lower = base_url.to_ascii_lowercase();
    let local = [
        "http://127.0.0.1",
        "http://localhost",
        "http://[::1]",
        "https://127.0.0.1",
        "https://localhost",
        "https://[::1]",
    ]
    .iter()
    .any(|prefix| {
        lower
            .strip_prefix(prefix)
            .is_some_and(|rest| rest.is_empty() || rest.starts_with(':') || rest.starts_with('/'))
    });
    if !local || lower.contains('@') {
        return Err("GPT-SoVITS 서버는 이 PC의 localhost 주소만 사용할 수 있습니다.".into());
    }
    Ok(base_url.to_owned())
}

fn gpt_sovits_endpoint(base_url: &str) -> AppResult<String> {
    Ok(format!("{}/tts", gpt_sovits_base_url(base_url)?))
}

fn gpt_sovits_address(base_url: &str) -> AppResult<(String, u16)> {
    let base_url = gpt_sovits_base_url(base_url)?;
    let (scheme, remainder) = base_url
        .split_once("://")
        .ok_or("GPT-SoVITS 서버 주소 형식이 올바르지 않습니다.")?;
    let authority = remainder.split('/').next().unwrap_or_default();
    if authority.is_empty() {
        return Err("GPT-SoVITS 서버 주소 형식이 올바르지 않습니다.".into());
    }
    if remainder.len() != authority.len() {
        return Err("자동 실행 서버 주소에는 경로를 넣을 수 없습니다.".into());
    }
    let default_port = if scheme.eq_ignore_ascii_case("https") {
        443
    } else {
        80
    };
    if authority.starts_with('[') {
        let end = authority
            .find(']')
            .ok_or("GPT-SoVITS IPv6 주소 형식이 올바르지 않습니다.")?;
        let host = authority[..=end].to_owned();
        let port = authority[end + 1..]
            .strip_prefix(':')
            .map(str::parse)
            .transpose()?
            .unwrap_or(default_port);
        return Ok((host, port));
    }
    let (host, port) = if let Some((host, port)) = authority.rsplit_once(':') {
        (host.to_owned(), port.parse::<u16>()?)
    } else {
        (authority.to_owned(), default_port)
    };
    Ok((host, port))
}

fn gpt_sovits_server_reachable(base_url: &str) -> bool {
    let Ok((host, port)) = gpt_sovits_address(base_url) else {
        return false;
    };
    let Ok(addresses) = format!("{host}:{port}").to_socket_addrs() else {
        return false;
    };
    addresses
        .into_iter()
        .any(|address| TcpStream::connect_timeout(&address, Duration::from_millis(350)).is_ok())
}

#[derive(Debug)]
struct GptSovitsRuntime {
    root: PathBuf,
    python: PathBuf,
    api: PathBuf,
}

fn resolve_gpt_sovits_runtime(voice: &GptSovitsSettings) -> AppResult<GptSovitsRuntime> {
    let configured = env::var_os("LUMI_GPT_SOVITS_DIR")
        .map(PathBuf::from)
        .or_else(|| {
            (!voice.runtime_dir.trim().is_empty()).then(|| PathBuf::from(voice.runtime_dir.trim()))
        })
        .unwrap_or_else(|| local_data_dir().join("gpt-sovits"));
    let mut roots = vec![configured.clone()];
    if let Ok(entries) = fs::read_dir(&configured) {
        roots.extend(
            entries
                .filter_map(Result::ok)
                .map(|entry| entry.path())
                .filter(|path| path.is_dir()),
        );
    }
    for root in roots {
        let api = root.join("api_v2.py");
        let python = [
            root.join("runtime").join("python.exe"),
            root.join("python.exe"),
        ]
        .into_iter()
        .find(|path| path.is_file());
        if api.is_file() {
            if let Some(python) = python {
                return Ok(GptSovitsRuntime { root, python, api });
            }
        }
    }
    Err(format!(
        "GPT-SoVITS 실행 폴더를 찾지 못했습니다: {}",
        configured.display()
    )
    .into())
}

fn prepare_gpt_sovits_compat_runner() -> AppResult<PathBuf> {
    let path = local_data_dir().join("gpt-sovits-compat-runner.py");
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let current = fs::read_to_string(&path).ok();
    if current.as_deref() != Some(GPT_SOVITS_COMPAT_RUNNER) {
        fs::write(&path, GPT_SOVITS_COMPAT_RUNNER)?;
    }
    Ok(path)
}

struct ManagedGptSovits {
    child: Child,
    base_url: String,
    last_used: Instant,
}

static MANAGED_GPT_SOVITS: OnceLock<Mutex<Option<ManagedGptSovits>>> = OnceLock::new();
static ACTIVE_GPT_SOVITS_REQUESTS: AtomicUsize = AtomicUsize::new(0);
static GPT_SOVITS_PREWARMING: AtomicBool = AtomicBool::new(false);

fn managed_gpt_sovits() -> &'static Mutex<Option<ManagedGptSovits>> {
    MANAGED_GPT_SOVITS.get_or_init(|| Mutex::new(None))
}

fn clear_loaded_gpt_sovits_weights() {
    if let Some(cache) = LOADED_GPT_SOVITS_WEIGHTS.get() {
        if let Ok(mut loaded) = cache.lock() {
            *loaded = None;
        }
    }
}

fn stop_managed_gpt_sovits() -> bool {
    let managed = managed_gpt_sovits()
        .lock()
        .ok()
        .and_then(|mut process| process.take());
    let Some(mut managed) = managed else {
        return false;
    };
    let _ = managed.child.kill();
    let _ = managed.child.wait();
    clear_loaded_gpt_sovits_weights();
    true
}

fn spawn_gpt_sovits(voice: &GptSovitsSettings, base_url: &str) -> AppResult<()> {
    let runtime = resolve_gpt_sovits_runtime(voice)?;
    let compat_runner = prepare_gpt_sovits_compat_runner()?;
    let (_, port) = gpt_sovits_address(base_url)?;
    let log_path = local_data_dir().join("gpt-sovits.log");
    if let Some(parent) = log_path.parent() {
        fs::create_dir_all(parent)?;
    }
    let stdout = fs::File::create(&log_path)?;
    let stderr = stdout.try_clone()?;
    let mut command = Command::new(&runtime.python);
    command
        .arg("-u")
        .arg(&compat_runner)
        .arg(&runtime.api)
        .arg("-a")
        .arg("127.0.0.1")
        .arg("-p")
        .arg(port.to_string())
        .current_dir(&runtime.root)
        .env("PYTHONUTF8", "1")
        .env("PYTHONIOENCODING", "utf-8")
        .stdin(Stdio::null())
        .stdout(Stdio::from(stdout))
        .stderr(Stdio::from(stderr));
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        command.creation_flags(CREATE_NO_WINDOW);
    }
    let child = command.spawn().map_err(|error| {
        format!(
            "GPT-SoVITS 자동 실행 실패: {error} ({})",
            runtime.python.display()
        )
    })?;
    *managed_gpt_sovits()
        .lock()
        .map_err(|_| "GPT-SoVITS 프로세스 잠금 오류")? = Some(ManagedGptSovits {
        child,
        base_url: base_url.to_owned(),
        last_used: Instant::now(),
    });
    Ok(())
}

fn wait_for_gpt_sovits(base_url: &str) -> AppResult<()> {
    let deadline = Instant::now() + GPT_SOVITS_START_TIMEOUT;
    while Instant::now() < deadline {
        if gpt_sovits_server_reachable(base_url) {
            return Ok(());
        }
        let exited = managed_gpt_sovits().lock().ok().and_then(|mut managed| {
            managed
                .as_mut()
                .and_then(|process| process.child.try_wait().ok().flatten())
        });
        if let Some(status) = exited {
            stop_managed_gpt_sovits();
            return Err(format!(
                "GPT-SoVITS가 시작 중 종료되었습니다: {status}. 로그: {}",
                local_data_dir().join("gpt-sovits.log").display()
            )
            .into());
        }
        thread::sleep(Duration::from_millis(250));
    }
    stop_managed_gpt_sovits();
    Err(format!(
        "GPT-SoVITS 시작 시간이 초과되었습니다. 로그: {}",
        local_data_dir().join("gpt-sovits.log").display()
    )
    .into())
}

fn ensure_gpt_sovits_server(voice: &GptSovitsSettings) -> AppResult<()> {
    let base_url = gpt_sovits_base_url(&voice.base_url)?;
    if gpt_sovits_server_reachable(&base_url) {
        return Ok(());
    }
    let reuse_managed = {
        let mut managed = managed_gpt_sovits()
            .lock()
            .map_err(|_| "GPT-SoVITS 프로세스 잠금 오류")?;
        match managed.as_mut() {
            Some(process) => process.child.try_wait()?.is_none() && process.base_url == base_url,
            None => false,
        }
    };
    if !reuse_managed {
        stop_managed_gpt_sovits();
        spawn_gpt_sovits(voice, &base_url)?;
    }
    wait_for_gpt_sovits(&base_url)
}

struct GptSovitsRequest;

impl Drop for GptSovitsRequest {
    fn drop(&mut self) {
        ACTIVE_GPT_SOVITS_REQUESTS.fetch_sub(1, Ordering::SeqCst);
        if let Ok(mut managed) = managed_gpt_sovits().lock() {
            if let Some(process) = managed.as_mut() {
                process.last_used = Instant::now();
            }
        }
    }
}

fn begin_gpt_sovits_request(voice: &GptSovitsSettings) -> AppResult<GptSovitsRequest> {
    ACTIVE_GPT_SOVITS_REQUESTS.fetch_add(1, Ordering::SeqCst);
    let request = GptSovitsRequest;
    ensure_gpt_sovits_server(voice)?;
    Ok(request)
}

fn should_stop_gpt_sovits(
    focus: bool,
    enabled: bool,
    active: usize,
    idle: Duration,
    idle_timeout: Duration,
) -> bool {
    focus || (active == 0 && (!enabled || idle >= idle_timeout))
}

fn gpt_sovits_idle_timeout(voice: &GptSovitsSettings) -> Duration {
    env::var("LUMI_GPT_SOVITS_IDLE_SECONDS")
        .ok()
        .and_then(|value| value.parse::<u64>().ok())
        .filter(|seconds| (1..=3_600).contains(seconds))
        .map(Duration::from_secs)
        .unwrap_or_else(|| voice_power_idle_timeout(voice.power_mode))
}

fn voice_power_idle_timeout(power_mode: VoicePowerMode) -> Duration {
    match power_mode {
        VoicePowerMode::Balanced => GPT_SOVITS_BALANCED_IDLE_TIMEOUT,
        VoicePowerMode::UltraSaver => GPT_SOVITS_ULTRA_SAVER_IDLE_TIMEOUT,
    }
}

fn enforce_gpt_sovits_lifetime(settings: &Settings) -> bool {
    let voice = gpt_sovits_settings_from_lumi(settings);
    let focus = lumi_quiet_now(settings);
    let active = ACTIVE_GPT_SOVITS_REQUESTS.load(Ordering::SeqCst);
    let idle = managed_gpt_sovits()
        .lock()
        .ok()
        .and_then(|managed| managed.as_ref().map(|process| process.last_used.elapsed()));
    let idle_timeout = gpt_sovits_idle_timeout(&voice);
    if idle.is_some_and(|idle| {
        should_stop_gpt_sovits(focus, voice.enabled, active, idle, idle_timeout)
    }) {
        return stop_managed_gpt_sovits();
    }
    false
}

fn start_gpt_sovits_prewarm(settings: Settings) -> bool {
    let voice = gpt_sovits_settings_from_lumi(&settings);
    if !voice.enabled
        || lumi_quiet_now(&settings)
        || gpt_sovits_server_reachable(&voice.base_url)
        || GPT_SOVITS_PREWARMING
            .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
            .is_err()
    {
        return false;
    }
    thread::spawn(move || {
        if let Ok(_request) = begin_gpt_sovits_request(&voice) {
            let _ = ensure_gpt_sovits_weights(&voice);
        }
        GPT_SOVITS_PREWARMING.store(false, Ordering::SeqCst);
    });
    true
}

fn url_encode(value: &str) -> String {
    let mut encoded = String::with_capacity(value.len());
    for byte in value.as_bytes() {
        if byte.is_ascii_alphanumeric() || matches!(*byte, b'-' | b'_' | b'.' | b'~') {
            encoded.push(*byte as char);
        } else {
            encoded.push_str(&format!("%{byte:02X}"));
        }
    }
    encoded
}

static LOADED_GPT_SOVITS_WEIGHTS: OnceLock<Mutex<Option<(String, String, String)>>> =
    OnceLock::new();

fn ensure_gpt_sovits_weights(voice: &GptSovitsSettings) -> AppResult<()> {
    let gpt_path = voice.gpt_weights_path.trim();
    let sovits_path = voice.sovits_weights_path.trim();
    if gpt_path.is_empty() && sovits_path.is_empty() {
        return Ok(());
    }
    if gpt_path.is_empty() || sovits_path.is_empty() {
        return Err("GPT와 SoVITS 가중치 경로를 모두 넣어 주세요.".into());
    }
    let base_url = gpt_sovits_base_url(&voice.base_url)?;
    let target = (
        base_url.clone(),
        gpt_path.to_owned(),
        sovits_path.to_owned(),
    );
    let cache = LOADED_GPT_SOVITS_WEIGHTS.get_or_init(|| Mutex::new(None));
    let mut loaded = cache.lock().map_err(|_| "가중치 상태 잠금 오류")?;
    if loaded.as_ref() == Some(&target) {
        return Ok(());
    }
    for (route, parameter, path) in [
        ("set_gpt_weights", "weights_path", gpt_path),
        ("set_sovits_weights", "weights_path", sovits_path),
    ] {
        let url = format!("{base_url}/{route}?{parameter}={}", url_encode(path));
        ureq::get(&url)
            .timeout(Duration::from_secs(120))
            .call()
            .map_err(|error| format!("GPT-SoVITS 가중치 적용 실패: {error}"))?;
    }
    *loaded = Some(target);
    Ok(())
}

fn validate_voice_settings(voice: &GptSovitsSettings) -> AppResult<()> {
    gpt_sovits_endpoint(&voice.base_url)?;
    if voice.enabled && voice.reference_audio_path.trim().is_empty() {
        return Err("GPT-SoVITS를 켜려면 참조 음성 경로를 넣어 주세요.".into());
    }
    if voice.runtime_dir.chars().count() > 2048 {
        return Err("GPT-SoVITS 실행 폴더 경로가 너무 깁니다.".into());
    }
    if voice.reference_audio_path.chars().count() > 2048 {
        return Err("참조 음성 경로가 너무 깁니다.".into());
    }
    if voice.gpt_weights_path.chars().count() > 2048
        || voice.sovits_weights_path.chars().count() > 2048
    {
        return Err("가중치 경로가 너무 깁니다.".into());
    }
    if voice.gpt_weights_path.trim().is_empty() != voice.sovits_weights_path.trim().is_empty() {
        return Err("GPT와 SoVITS 가중치 경로를 모두 넣거나 모두 비워 주세요.".into());
    }
    if voice.prompt_text.chars().count() > 2000 {
        return Err("참조 대사는 2,000자 이하여야 합니다.".into());
    }
    for (name, language) in [
        ("본문 언어", voice.text_language.trim()),
        ("참조 언어", voice.prompt_language.trim()),
    ] {
        if language.is_empty()
            || language.len() > 24
            || !language
                .bytes()
                .all(|value| value.is_ascii_alphanumeric() || value == b'_' || value == b'-')
        {
            return Err(format!("{name} 코드가 올바르지 않습니다.").into());
        }
    }
    if !(0.5..=2.0).contains(&voice.speed_factor) {
        return Err("말하기 속도는 0.5에서 2.0 사이여야 합니다.".into());
    }
    Ok(())
}

fn gpt_sovits_settings_from_payload(payload: &Value) -> AppResult<GptSovitsSettings> {
    let text = |key: &str, default: &str| {
        payload
            .get(key)
            .and_then(Value::as_str)
            .unwrap_or(default)
            .trim()
            .to_owned()
    };
    let power_mode = match text("power_mode", "balanced").as_str() {
        "ultra_saver" => VoicePowerMode::UltraSaver,
        _ => VoicePowerMode::Balanced,
    };
    let speed_factor = payload
        .get("speed_factor")
        .and_then(|value| {
            value
                .as_f64()
                .or_else(|| value.as_str().and_then(|text| text.parse::<f64>().ok()))
        })
        .unwrap_or(1.0) as f32;
    let voice = GptSovitsSettings {
        enabled: true,
        power_mode,
        base_url: text("base_url", "http://127.0.0.1:9880"),
        runtime_dir: text("runtime_dir", ""),
        gpt_weights_path: text("gpt_weights_path", ""),
        sovits_weights_path: text("sovits_weights_path", ""),
        reference_audio_path: text("reference_audio_path", ""),
        prompt_text: text("prompt_text", ""),
        text_language: text("text_language", "ko"),
        prompt_language: text("prompt_language", "ko"),
        speed_factor,
    };
    validate_voice_settings(&voice)?;
    Ok(voice)
}

fn fetch_gpt_sovits_wav(voice: &GptSovitsSettings, text: &str) -> AppResult<Vec<u8>> {
    validate_voice_settings(voice)?;
    if voice.reference_audio_path.trim().is_empty() {
        return Err("GPT-SoVITS 참조 음성 경로를 넣어 주세요.".into());
    }
    let text = text.trim();
    if text.is_empty() {
        return Err("읽을 문장이 비어 있습니다.".into());
    }
    let _request = begin_gpt_sovits_request(voice)?;
    ensure_gpt_sovits_weights(voice)?;
    let endpoint = gpt_sovits_endpoint(&voice.base_url)?;
    let payload = serde_json::to_string(&json!({
        "text": text,
        "text_lang": voice.text_language.trim(),
        "ref_audio_path": voice.reference_audio_path.trim(),
        "prompt_text": voice.prompt_text.trim(),
        "prompt_lang": voice.prompt_language.trim(),
        "text_split_method": "cut5",
        "batch_size": 1,
        "speed_factor": voice.speed_factor,
        "media_type": "wav",
        "streaming_mode": false
    }))?;
    let response = ureq::post(&endpoint)
        .set("Content-Type", "application/json; charset=utf-8")
        .timeout(Duration::from_secs(120))
        .send_string(&payload)
        .map_err(|error| format!("GPT-SoVITS 연결 실패: {error}"))?;
    let mut wav = Vec::new();
    response
        .into_reader()
        .take(MAX_AUDIO_BYTES + 1)
        .read_to_end(&mut wav)?;
    if wav.len() as u64 > MAX_AUDIO_BYTES {
        return Err("GPT-SoVITS 음성 응답이 64MB를 넘었습니다.".into());
    }
    if wav.len() < 12 || &wav[0..4] != b"RIFF" || &wav[8..12] != b"WAVE" {
        return Err("GPT-SoVITS가 WAV 음성 대신 잘못된 응답을 보냈습니다.".into());
    }
    Ok(wav)
}

fn clean_message(text: &str) -> AppResult<String> {
    let cleaned = text
        .replace('\0', "")
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ");
    if cleaned.is_empty() {
        return Err("알림 문구가 비어 있습니다.".into());
    }
    Ok(cleaned)
}

fn lumi_message_payload(text: &str) -> AppResult<String> {
    let cleaned = clean_message(text)?;
    let reading_time_ms = (4_000 + cleaned.chars().count() as u64 * 110).clamp(6_000, 60_000);
    Ok(format!("@{reading_time_ms}\n{cleaned}"))
}

fn write_lumi_message(app_dir: &Path, text: &str) -> AppResult<PathBuf> {
    let payload = lumi_message_payload(text)?;
    let target = app_dir.join("speech").join("say.txt");
    let parent = target
        .parent()
        .ok_or("말풍선 폴더 경로가 올바르지 않습니다.")?;
    fs::create_dir_all(parent)?;
    let temporary = parent.join(format!(".say-{}.tmp", Uuid::new_v4().simple()));
    fs::write(&temporary, payload.as_bytes())?;
    if target.exists() {
        let _ = fs::remove_file(&target);
    }
    if let Err(error) = fs::rename(&temporary, &target) {
        let _ = fs::remove_file(&temporary);
        return Err(error.into());
    }
    Ok(target)
}

struct CodexAppServer {
    child: Child,
    input: ChildStdin,
    messages: Receiver<Value>,
    pending: VecDeque<Value>,
    next_id: u64,
}

impl CodexAppServer {
    fn spawn() -> AppResult<Self> {
        let (executable, arguments) = codex_app_server_command()?;
        let workspace = local_data_dir().join("codex-workspace");
        let codex_home = local_data_dir().join("codex-home");
        fs::create_dir_all(&workspace)?;
        fs::create_dir_all(&codex_home)?;

        let mut command = Command::new(&executable);
        command
            .args(arguments)
            .current_dir(workspace)
            .env("CODEX_HOME", codex_home)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null());
        #[cfg(windows)]
        command.creation_flags(0x08000000);

        let mut child = command.spawn().map_err(|error| {
            format!(
                "Codex App Server를 실행하지 못했습니다: {} ({error}). 설치기를 다시 실행해 주세요.",
                executable.display()
            )
        })?;
        let input = child
            .stdin
            .take()
            .ok_or("Codex App Server stdin을 열지 못했습니다.")?;
        let output = child
            .stdout
            .take()
            .ok_or("Codex App Server stdout을 열지 못했습니다.")?;
        let (sender, messages) = mpsc::channel();
        thread::spawn(move || {
            for line in BufReader::new(output).lines().map_while(Result::ok) {
                if let Ok(message) = serde_json::from_str::<Value>(&line) {
                    if sender.send(message).is_err() {
                        break;
                    }
                }
            }
        });

        let mut client = Self {
            child,
            input,
            messages,
            pending: VecDeque::new(),
            next_id: 0,
        };
        client.request(
            "initialize",
            json!({
                "clientInfo": {
                    "name": "lumi_to_gpt",
                    "title": APP_NAME,
                    "version": VERSION
                }
            }),
            Duration::from_secs(30),
        )?;
        client.send(json!({"method":"initialized","params":{}}))?;
        Ok(client)
    }

    fn send(&mut self, message: Value) -> AppResult<()> {
        serde_json::to_writer(&mut self.input, &message)?;
        self.input.write_all(b"\n")?;
        self.input.flush()?;
        Ok(())
    }

    fn receive_until(&self, deadline: Instant) -> AppResult<Value> {
        let remaining = deadline
            .checked_duration_since(Instant::now())
            .ok_or("Codex App Server 응답 시간이 초과되었습니다.")?;
        self.messages
            .recv_timeout(remaining)
            .map_err(|error| match error {
                mpsc::RecvTimeoutError::Timeout => {
                    "Codex App Server 응답 시간이 초과되었습니다.".into()
                }
                mpsc::RecvTimeoutError::Disconnected => {
                    "Codex App Server 연결이 종료되었습니다.".into()
                }
            })
    }

    fn request(&mut self, method: &str, params: Value, timeout: Duration) -> AppResult<Value> {
        let id = self.next_id;
        self.next_id += 1;
        self.send(json!({"method":method,"id":id,"params":params}))?;
        let deadline = Instant::now() + timeout;
        loop {
            let message = self.receive_until(deadline)?;
            if message.get("method").is_none() && message.get("id") == Some(&json!(id)) {
                if let Some(error) = message.get("error") {
                    let text = error
                        .get("message")
                        .and_then(Value::as_str)
                        .unwrap_or("Codex App Server 요청이 실패했습니다.");
                    return Err(text.to_owned().into());
                }
                return Ok(message.get("result").cloned().unwrap_or(Value::Null));
            }
            if message.get("method").is_some() && message.get("id").is_some() {
                self.send(json!({
                    "id": message["id"].clone(),
                    "error": {"code":-32601,"message":"LUMI to GPT에서 지원하지 않는 요청입니다."}
                }))?;
                continue;
            }
            if matches!(
                message.get("method").and_then(Value::as_str),
                Some("item/completed" | "turn/completed")
            ) {
                self.pending.push_back(message);
            }
        }
    }

    fn account(&mut self) -> AppResult<Value> {
        self.request(
            "account/read",
            json!({"refreshToken":false}),
            Duration::from_secs(30),
        )
    }

    fn account_status(&mut self) -> AppResult<Value> {
        let result = self.account()?;
        let account = result.get("account").cloned().unwrap_or(Value::Null);
        let connected = account.get("type").and_then(Value::as_str) == Some("chatgpt");
        Ok(json!({
            "connected": connected,
            "account": account,
            "model": DEFAULT_CODEX_MODEL,
            "effort": DEFAULT_CODEX_EFFORT,
            "backend": "codex_app_server"
        }))
    }

    fn start_login(&mut self) -> AppResult<Value> {
        self.request(
            "account/login/start",
            json!({"type":"chatgptDeviceCode"}),
            Duration::from_secs(30),
        )
    }

    fn logout(&mut self) -> AppResult<Value> {
        self.request("account/logout", json!({}), Duration::from_secs(30))?;
        Ok(json!({"ok":true}))
    }

    fn complete(&mut self, model: &str, prompt: String, images: Vec<String>) -> AppResult<String> {
        let account = self.account()?;
        if account["account"]["type"].as_str() != Some("chatgpt") {
            return Err(
                "ChatGPT 계정 연결이 필요합니다. LUMI to GPT 창에서 계정을 연결해 주세요.".into(),
            );
        }

        self.pending.clear();
        let model = match model.trim() {
            "" | "chatgpt-web" => DEFAULT_CODEX_MODEL,
            configured => configured,
        };
        let workspace = local_data_dir().join("codex-workspace");
        let thread = self.request(
            "thread/start",
            json!({
                "model": model,
                "cwd": workspace,
                "approvalPolicy": "never",
                "sandbox": "read-only",
                "personality": "none",
                "ephemeral": true,
                "serviceName": "lumi_to_gpt",
                "developerInstructions": "You are the conversation backend for the LUMI desktop mascot. Follow the character and conversation instructions inside the user's message. Return only the final Korean dialogue text for one speech bubble. Do not use tools, inspect files, run commands, browse, or explain your process."
            }),
            Duration::from_secs(30),
        )?;
        let thread_id = thread["thread"]["id"]
            .as_str()
            .ok_or("Codex 대화 ID를 받지 못했습니다.")?
            .to_owned();
        let mut input = vec![json!({"type":"text","text":prompt})];
        for image in images {
            input.push(json!({"type":"image","url":image,"detail":"low"}));
        }
        let turn = self.request(
            "turn/start",
            json!({
                "threadId": thread_id,
                "input": input,
                "model": model,
                "effort": DEFAULT_CODEX_EFFORT,
                "summary": "none",
                "approvalPolicy": "never",
                "sandboxPolicy": {"type":"readOnly","networkAccess":false}
            }),
            Duration::from_secs(30),
        )?;
        let turn_id = turn["turn"]["id"]
            .as_str()
            .ok_or("Codex 응답 작업 ID를 받지 못했습니다.")?
            .to_owned();
        let deadline = Instant::now() + REQUEST_TIMEOUT;
        let mut final_text = String::new();
        loop {
            let message = match self.pending.pop_front() {
                Some(message) => message,
                None => self.receive_until(deadline)?,
            };
            let method = message.get("method").and_then(Value::as_str);
            let params = &message["params"];
            if params.get("threadId").and_then(Value::as_str) != Some(thread_id.as_str()) {
                continue;
            }
            if method == Some("item/completed")
                && params.get("turnId").and_then(Value::as_str) == Some(turn_id.as_str())
                && params["item"]["type"].as_str() == Some("agentMessage")
            {
                if let Some(text) = params["item"]["text"].as_str() {
                    final_text = text.to_owned();
                }
                continue;
            }
            if method != Some("turn/completed") || params["turn"]["id"].as_str() != Some(&turn_id) {
                continue;
            }
            if params["turn"]["status"].as_str() != Some("completed") {
                let message = params["turn"]["error"]["message"]
                    .as_str()
                    .unwrap_or("Codex 응답 생성이 실패했습니다.");
                return Err(message.to_owned().into());
            }
            if final_text.trim().is_empty() {
                final_text = params["turn"]["items"]
                    .as_array()
                    .and_then(|items| {
                        items.iter().rev().find_map(|item| {
                            (item["type"].as_str() == Some("agentMessage"))
                                .then(|| item["text"].as_str())
                                .flatten()
                        })
                    })
                    .unwrap_or_default()
                    .to_owned();
            }
            let final_text = final_text.trim();
            if final_text.is_empty() {
                return Err("ChatGPT 응답이 비어 있습니다.".into());
            }
            return Ok(final_text.to_owned());
        }
    }
}

impl Drop for CodexAppServer {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
    }
}

fn codex_app_server_command() -> AppResult<(PathBuf, Vec<String>)> {
    if let Some(executable) = env::var_os("LUMI_CODEX_APP_SERVER").map(PathBuf::from) {
        let arguments = env::var("LUMI_CODEX_APP_SERVER_ARGS")
            .ok()
            .map(|value| serde_json::from_str::<Vec<String>>(&value))
            .transpose()
            .map_err(|error| format!("LUMI_CODEX_APP_SERVER_ARGS가 올바르지 않습니다: {error}"))?
            .unwrap_or_else(|| codex_default_arguments(&executable));
        return Ok((executable, arguments));
    }
    if let Ok(current) = env::current_exe() {
        if let Some(parent) = current.parent() {
            let bundled = parent.join("codex-app-server.exe");
            if bundled.is_file() {
                return Ok((bundled, Vec::new()));
            }
        }
    }
    Ok((PathBuf::from("codex"), vec!["app-server".to_owned()]))
}

fn codex_default_arguments(executable: &Path) -> Vec<String> {
    let name = executable
        .file_stem()
        .and_then(|value| value.to_str())
        .unwrap_or_default();
    if name.starts_with("codex-app-server") {
        Vec::new()
    } else {
        vec!["app-server".to_owned()]
    }
}

#[derive(Clone, Default)]
struct CodexState {
    client: Arc<Mutex<Option<CodexAppServer>>>,
}

impl CodexState {
    fn with_client<T>(
        &self,
        operation: impl FnOnce(&mut CodexAppServer) -> AppResult<T>,
    ) -> AppResult<T> {
        let mut client = self
            .client
            .lock()
            .map_err(|_| "Codex App Server 잠금이 손상되었습니다.")?;
        if client.is_none() {
            *client = Some(CodexAppServer::spawn()?);
        }
        let result = operation(client.as_mut().expect("Codex client initialized"));
        if result.is_err() {
            client.take();
        }
        result
    }

    fn account_status(&self) -> AppResult<Value> {
        self.with_client(CodexAppServer::account_status)
    }

    fn start_login(&self) -> AppResult<Value> {
        self.with_client(CodexAppServer::start_login)
    }

    fn logout(&self) -> AppResult<Value> {
        self.with_client(CodexAppServer::logout)
    }

    fn complete(&self, model: &str, prompt: String, images: Vec<String>) -> AppResult<String> {
        self.with_client(|client| client.complete(model, prompt, images))
    }

    fn running(&self) -> bool {
        self.client.lock().is_ok_and(|client| client.is_some())
    }
}

fn content_to_text_and_images(content: &Value) -> (String, Vec<String>) {
    if let Some(text) = content.as_str() {
        return (text.to_owned(), Vec::new());
    }
    let Some(items) = content.as_array() else {
        return (content.to_string(), Vec::new());
    };
    let mut texts = Vec::new();
    let mut images = Vec::new();
    for item in items {
        match item.get("type").and_then(Value::as_str) {
            Some("text") => {
                if let Some(text) = item.get("text").and_then(Value::as_str) {
                    texts.push(text.to_owned());
                }
            }
            Some("image_url") => {
                let image = item.get("image_url");
                let url = image.and_then(Value::as_str).or_else(|| {
                    image
                        .and_then(|value| value.get("url"))
                        .and_then(Value::as_str)
                });
                if let Some(url) = url.filter(|url| url.starts_with("data:image/")) {
                    images.push(url.to_owned());
                }
            }
            _ => {}
        }
    }
    (texts.join("\n"), images)
}

fn build_codex_prompt(messages: &Value) -> AppResult<(String, Vec<String>)> {
    let messages = messages.as_array().ok_or("messages 배열이 필요합니다.")?;
    let mut normalized = Vec::new();
    let mut images = Vec::new();
    for message in messages
        .iter()
        .rev()
        .take(16)
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
    {
        let Some(object) = message.as_object() else {
            continue;
        };
        let role = object.get("role").and_then(Value::as_str).unwrap_or("user");
        let (text, message_images) = content_to_text_and_images(
            object
                .get("content")
                .unwrap_or(&Value::String(String::new())),
        );
        if !text.trim().is_empty() {
            normalized.push((role.to_owned(), text.trim().to_owned()));
        }
        images.extend(message_images);
    }
    if normalized.is_empty() && images.is_empty() {
        return Err("전달할 대화 내용이 없습니다.".into());
    }

    let mut blocks = vec![
        "아래는 Little LUMI 데스크톱 마스코트가 보낸 대화 요청입니다.".to_owned(),
        "[시스템] 지시와 캐릭터 말투를 따르고, 마지막 사용자 요청에 답하세요.".to_owned(),
        "설명이나 머리말 없이 꼬미가 실제로 말할 답변 본문만 작성하세요. 말풍선 하나에 온전히 들어가도록 가능한 한 220자 이내의 완결된 문장으로 답하세요.".to_owned(),
    ];
    for (role, text) in normalized {
        let name = match role.as_str() {
            "system" => "시스템",
            "user" => "사용자",
            "assistant" => "꼬미",
            other => other,
        };
        blocks.push(format!("[{name}]\n{text}"));
    }
    if !images.is_empty() {
        blocks.push("첨부된 화면 이미지를 함께 보고 필요한 경우 자연스럽게 언급하세요.".to_owned());
    }
    let mut prompt = blocks.join("\n\n");
    if prompt.chars().count() > 28_000 {
        prompt = prompt
            .chars()
            .rev()
            .take(28_000)
            .collect::<String>()
            .chars()
            .rev()
            .collect();
    }
    Ok((prompt, images.into_iter().rev().take(1).collect()))
}

fn header(name: &str, value: &str) -> Header {
    Header::from_bytes(name.as_bytes(), value.as_bytes()).expect("valid HTTP header")
}

fn allowed_origin(request: &Request) -> Option<String> {
    let origin = request
        .headers()
        .iter()
        .find(|item| item.field.equiv("Origin"))?
        .value
        .as_str();
    matches!(
        origin,
        "http://tauri.localhost" | "https://tauri.localhost" | "tauri://localhost"
    )
    .then(|| origin.to_owned())
}

fn with_common_headers<R: Read>(response: Response<R>, origin: Option<&str>) -> Response<R> {
    let response = response
        .with_header(header("Access-Control-Allow-Methods", "GET, POST, OPTIONS"))
        .with_header(header("Access-Control-Allow-Headers", "Content-Type"))
        .with_header(header("Access-Control-Allow-Private-Network", "true"))
        .with_header(header("Cache-Control", "no-store"));
    match origin {
        Some(origin) => response
            .with_header(header("Access-Control-Allow-Origin", origin))
            .with_header(header("Vary", "Origin")),
        None => response,
    }
}

fn respond_json(request: Request, status: u16, payload: Value) {
    let origin = allowed_origin(&request);
    let data = serde_json::to_vec(&payload).unwrap_or_else(|_| b"{}".to_vec());
    let response = Response::from_data(data)
        .with_status_code(StatusCode(status))
        .with_header(header("Content-Type", "application/json; charset=utf-8"));
    let _ = request.respond(with_common_headers(response, origin.as_deref()));
}

fn respond_bytes(request: Request, status: u16, content_type: &str, data: Vec<u8>) {
    let origin = allowed_origin(&request);
    let response = Response::from_data(data)
        .with_status_code(StatusCode(status))
        .with_header(header("Content-Type", content_type));
    let _ = request.respond(with_common_headers(response, origin.as_deref()));
}

fn respond_empty(request: Request, status: u16) {
    let origin = allowed_origin(&request);
    let response = Response::empty(StatusCode(status));
    let _ = request.respond(with_common_headers(response, origin.as_deref()));
}

fn read_json(request: &mut Request) -> AppResult<Value> {
    let body_length = request.body_length().unwrap_or_default() as u64;
    if body_length == 0 || body_length > MAX_BODY_BYTES {
        return Err("요청 크기가 허용 범위를 벗어났습니다.".into());
    }
    let mut body = vec![0; body_length as usize];
    request.as_reader().read_exact(&mut body)?;
    Ok(serde_json::from_slice(&body)?)
}

#[derive(Clone)]
struct HttpContext {
    settings: Settings,
    state: CodexState,
    stop: Arc<AtomicBool>,
}

fn handle_request(mut request: Request, context: HttpContext) {
    let path = request
        .url()
        .split('?')
        .next()
        .unwrap_or(request.url())
        .to_owned();
    let has_origin = request
        .headers()
        .iter()
        .any(|item| item.field.equiv("Origin"));
    if has_origin && allowed_origin(&request).is_none() {
        respond_json(
            request,
            403,
            json!({"error":"허용되지 않은 요청 출처입니다."}),
        );
        return;
    }
    if request.method() == &Method::Options {
        respond_empty(request, 204);
        return;
    }

    if request.method() == &Method::Get && path == "/health" {
        respond_json(
            request,
            200,
            json!({
                "ok": true,
                "name": APP_NAME,
                "version": VERSION,
                "lumi_found": is_lumi_app_dir(&lumi_app_dir(&context.settings)),
                "lumi_chat_found": is_lumi_chat_unlocked(&lumi_app_dir(&context.settings)),
                "lumi_app_dir": lumi_app_dir(&context.settings),
                "codex_process_started": context.state.running(),
                "model": DEFAULT_CODEX_MODEL,
                "pending_voice": 0
            }),
        );
        return;
    }
    if request.method() == &Method::Get && path == "/auth/status" {
        match context.state.account_status() {
            Ok(status) => respond_json(request, 200, status),
            Err(error) => respond_json(request, 502, json!({"error":error.to_string()})),
        }
        return;
    }
    if request.method() == &Method::Get && path == "/v1/models" {
        respond_json(
            request,
            200,
            json!({"object":"list","data":[{
                "id":DEFAULT_CODEX_MODEL,"object":"model","created":0,"owned_by":"openai-codex"
            }]}),
        );
        return;
    }

    if request.method() != &Method::Post {
        respond_json(request, 404, json!({"error":"not_found"}));
        return;
    }
    let payload = match read_json(&mut request) {
        Ok(payload) if payload.is_object() => payload,
        Ok(_) => {
            respond_json(request, 400, json!({"error":"JSON 객체가 필요합니다."}));
            return;
        }
        Err(error) => {
            respond_json(request, 400, json!({"error":error.to_string()}));
            return;
        }
    };

    match path.as_str() {
        "/auth/login" => match context.state.start_login() {
            Ok(result) => respond_json(request, 200, result),
            Err(error) => respond_json(request, 502, json!({"error":error.to_string()})),
        },
        "/auth/logout" => match context.state.logout() {
            Ok(result) => respond_json(request, 200, result),
            Err(error) => respond_json(request, 502, json!({"error":error.to_string()})),
        },
        "/v1/chat/completions" => {
            let (prompt, images) = match build_codex_prompt(&payload["messages"]) {
                Ok(value) => value,
                Err(error) => {
                    respond_json(request, 400, json!({"error":{"message":error.to_string()}}));
                    return;
                }
            };
            let model = payload
                .get("model")
                .and_then(Value::as_str)
                .unwrap_or(DEFAULT_CODEX_MODEL)
                .to_owned();
            match context.state.complete(&model, prompt, images) {
                Ok(text) => {
                    let id = Uuid::new_v4().simple().to_string();
                    respond_json(
                        request,
                        200,
                        json!({
                            "id":format!("chatcmpl-{id}"),
                            "object":"chat.completion",
                            "created":SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, |value| value.as_secs()),
                            "model":model,
                            "choices":[{"index":0,"message":{"role":"assistant","content":text},"finish_reason":"stop"}],
                            "usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}
                        }),
                    );
                }
                Err(error) => {
                    respond_json(
                        request,
                        502,
                        json!({"error":{"message":error.to_string(),"type":"codex_app_server_error"}}),
                    );
                }
            }
        }
        "/voice/synthesize" => {
            let text = payload
                .get("text")
                .and_then(Value::as_str)
                .unwrap_or_default()
                .trim();
            if text.is_empty() {
                respond_json(
                    request,
                    400,
                    json!({"error":"합성할 문장이 비어 있습니다."}),
                );
                return;
            }
            if lumi_quiet_now(&context.settings) {
                respond_json(
                    request,
                    409,
                    json!({"error":"집중 모드라서 음성을 재생하지 않습니다."}),
                );
                return;
            }
            let mut voice = match gpt_sovits_settings_from_payload(&payload) {
                Ok(voice) => voice,
                Err(error) => {
                    respond_json(request, 400, json!({"error":error.to_string()}));
                    return;
                }
            };
            voice.enabled = gpt_sovits_settings_from_lumi(&context.settings).enabled;
            if let Err(error) = persist_voice_settings(&context.settings, &voice) {
                respond_json(
                    request,
                    500,
                    json!({"error":format!("목소리 설정 저장 실패: {error}")}),
                );
                return;
            }
            match fetch_gpt_sovits_wav(&voice, text) {
                Ok(wav) => respond_bytes(request, 200, "audio/wav", wav),
                Err(error) => respond_json(request, 502, json!({"error":error.to_string()})),
            }
        }
        "/notify" => {
            let text = payload
                .get("text")
                .and_then(Value::as_str)
                .unwrap_or_default();
            match write_lumi_message(&lumi_app_dir(&context.settings), text) {
                Ok(path) => {
                    respond_json(request, 200, json!({"ok":true,"path":path}));
                }
                Err(error) => respond_json(request, 400, json!({"error":error.to_string()})),
            }
        }
        "/test/shutdown" if env::var_os("LUMI_ALLOW_TEST_SHUTDOWN").is_some() => {
            if payload.get("token").and_then(Value::as_str) != Some("lumi-smoke-test") {
                respond_json(request, 403, json!({"error":"forbidden"}));
                return;
            }
            context.stop.store(true, Ordering::SeqCst);
            respond_json(request, 200, json!({"ok":true}));
        }
        _ => respond_json(request, 404, json!({"error":"not_found"})),
    }
}

struct RunningServer {
    stop: Arc<AtomicBool>,
    thread: thread::JoinHandle<()>,
}

fn start_http_server(settings: Settings) -> AppResult<RunningServer> {
    let server = Server::http(format!("{HOST}:{}", settings.port))?;
    let stop = Arc::new(AtomicBool::new(false));
    let context = HttpContext {
        state: CodexState::default(),
        settings,
        stop: stop.clone(),
    };
    let server_stop = stop.clone();
    let server_thread = thread::spawn(move || {
        while !server_stop.load(Ordering::SeqCst) {
            match server.recv_timeout(Duration::from_millis(250)) {
                Ok(Some(request)) => {
                    let request_context = context.clone();
                    thread::spawn(move || handle_request(request, request_context));
                }
                Ok(None) => {}
                Err(_) => break,
            }
        }
    });
    Ok(RunningServer {
        stop,
        thread: server_thread,
    })
}

fn notify_lumi(text: &str, settings: &Settings) -> AppResult<String> {
    let cleaned = clean_message(text)?;
    let notify_url = env::var("LUMI_BRIDGE_NOTIFY_URL")
        .unwrap_or_else(|_| format!("http://{HOST}:{}/notify", settings.port));
    let payload = serde_json::to_string(&json!({"text":cleaned}))?;
    if let Ok(response) = ureq::post(&notify_url)
        .set("Content-Type", "application/json; charset=utf-8")
        .timeout(Duration::from_secs(2))
        .send_string(&payload)
    {
        if response.status() == 200 {
            return Ok("꼬미에게 알림을 전달했습니다.".to_owned());
        }
    }
    let path = write_lumi_message(&lumi_app_dir(settings), &cleaned)?;
    Ok(format!("꼬미에게 알림을 전달했습니다: {}", path.display()))
}

fn mcp_tools() -> Value {
    json!([{
        "name":"notify_lumi",
        "description":"현재 Codex 작업의 완료, 실패 또는 사용자 확인 필요 상태를 Little LUMI의 말풍선으로 알립니다. 음성 여부와 집중 모드는 LUMI Chat 설정을 따릅니다.",
        "inputSchema":{
            "type":"object",
            "properties":{
                "text":{"type":"string","minLength":1,"maxLength":500,"description":"꼬미가 보여 줄 짧은 한국어 알림"}
            },
            "required":["text"],
            "additionalProperties":false
        },
        "annotations":{
            "title":"꼬미에게 작업 상태 알림",
            "readOnlyHint":false,
            "destructiveHint":false,
            "idempotentHint":false,
            "openWorldHint":false
        }
    }])
}

fn mcp_error(id: Value, code: i64, message: impl Into<String>) -> Value {
    json!({"jsonrpc":"2.0","id":id,"error":{"code":code,"message":message.into()}})
}

fn handle_mcp(request: &Value, settings: &Settings) -> Option<Value> {
    let method = request
        .get("method")
        .and_then(Value::as_str)
        .unwrap_or_default();
    let id = request.get("id").cloned().unwrap_or(Value::Null);
    match method {
        "initialize" => {
            let protocol = request
                .pointer("/params/protocolVersion")
                .and_then(Value::as_str)
                .unwrap_or("2025-06-18");
            Some(json!({
                "jsonrpc":"2.0","id":id,
                "result":{
                    "protocolVersion":protocol,
                    "capabilities":{"tools":{"listChanged":false}},
                    "serverInfo":{"name":"lumi","version":VERSION},
                    "instructions":"사용자에게 답변하기 직전, 작업이 완료되었거나 실패했거나 사용자 확인이 필요하면 notify_lumi를 한 번 호출해 짧은 한국어로 상태를 알리세요."
                }
            }))
        }
        "notifications/initialized" | "notifications/cancelled" => None,
        "ping" => Some(json!({"jsonrpc":"2.0","id":id,"result":{}})),
        "tools/list" => Some(json!({"jsonrpc":"2.0","id":id,"result":{"tools":mcp_tools()}})),
        "tools/call" => {
            if request.pointer("/params/name").and_then(Value::as_str) != Some("notify_lumi") {
                return Some(mcp_error(id, -32602, "알 수 없는 도구입니다."));
            }
            let text = request
                .pointer("/params/arguments/text")
                .and_then(Value::as_str)
                .unwrap_or_default();
            let (message, is_error) = match notify_lumi(text, settings) {
                Ok(message) => (message, false),
                Err(error) => (format!("꼬미 알림 실패: {error}"), true),
            };
            Some(json!({
                "jsonrpc":"2.0","id":id,
                "result":{"content":[{"type":"text","text":message}],"isError":is_error}
            }))
        }
        _ if !id.is_null() => Some(mcp_error(
            id,
            -32601,
            format!("지원하지 않는 메서드입니다: {method}"),
        )),
        _ => None,
    }
}

fn run_mcp(settings: &Settings) -> AppResult<()> {
    let input = io::stdin();
    let mut output = io::BufWriter::new(io::stdout());
    for line in input.lock().lines() {
        let line = line?;
        if line.trim().is_empty() {
            continue;
        }
        let response = match serde_json::from_str::<Value>(&line) {
            Ok(request) if request.is_object() => handle_mcp(&request, settings),
            Ok(_) => Some(mcp_error(Value::Null, -32700, "JSON 객체가 필요합니다.")),
            Err(error) => Some(mcp_error(Value::Null, -32700, error.to_string())),
        };
        if let Some(response) = response {
            serde_json::to_writer(&mut output, &response)?;
            output.write_all(b"\n")?;
            output.flush()?;
        }
    }
    Ok(())
}

struct TauriSettings(Arc<Mutex<Settings>>);

fn start_gpt_sovits_lifetime_monitor(
    settings: Arc<Mutex<Settings>>,
    stop: Arc<AtomicBool>,
) -> thread::JoinHandle<()> {
    thread::spawn(move || {
        while !stop.load(Ordering::SeqCst) {
            let settings = settings.lock().ok().map(|settings| settings.clone());
            if let Some(settings) = settings {
                enforce_gpt_sovits_lifetime(&settings);
            }
            thread::sleep(Duration::from_millis(250));
        }
        stop_managed_gpt_sovits();
    })
}

#[tauri::command]
fn prewarm_gpt_sovits(state: tauri::State<'_, TauriSettings>) -> Result<bool, String> {
    let settings = state
        .0
        .lock()
        .map_err(|_| "설정 잠금 오류".to_owned())?
        .clone();
    Ok(start_gpt_sovits_prewarm(settings))
}

#[tauri::command]
fn open_codex_login_url(url: String) -> Result<(), String> {
    if url != "https://auth.openai.com/codex/device" {
        return Err("허용되지 않은 로그인 주소입니다.".to_owned());
    }
    Command::new("rundll32.exe")
        .arg("url.dll,FileProtocolHandler")
        .arg(url)
        .spawn()
        .map(|_| ())
        .map_err(|error| format!("기본 브라우저를 열지 못했습니다: {error}"))
}

#[tauri::command]
fn open_latest_release() -> Result<(), String> {
    Command::new("rundll32.exe")
        .arg("url.dll,FileProtocolHandler")
        .arg(LATEST_RELEASE_URL)
        .spawn()
        .map(|_| ())
        .map_err(|error| format!("업데이트 페이지를 열지 못했습니다: {error}"))
}

fn show_window(app: &tauri::AppHandle, label: &str) -> Result<(), String> {
    let window = app
        .get_webview_window(label)
        .ok_or_else(|| format!("{label} 창을 찾지 못했습니다."))?;
    window.show().map_err(|error| error.to_string())?;
    window.set_focus().map_err(|error| error.to_string())
}

fn run_gui(server: RunningServer, settings: Settings) -> AppResult<()> {
    let shared_settings = Arc::new(Mutex::new(settings));
    let voice_monitor_stop = Arc::new(AtomicBool::new(false));
    let voice_monitor =
        start_gpt_sovits_lifetime_monitor(shared_settings.clone(), voice_monitor_stop.clone());
    let result = tauri::Builder::default()
        .manage(TauriSettings(shared_settings))
        .invoke_handler(tauri::generate_handler![
            prewarm_gpt_sovits,
            open_codex_login_url,
            open_latest_release
        ])
        .setup(move |app| {
            WebviewWindowBuilder::new(app, "account", WebviewUrl::App("index.html".into()))
                .title("LUMI to GPT")
                .inner_size(600.0, 740.0)
                .min_inner_size(500.0, 620.0)
                .build()?;

            let account =
                MenuItem::with_id(app, "account", "계정 및 업데이트", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "종료", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&account, &quit])?;
            let icon = tauri::image::Image::from_bytes(include_bytes!("../icons/icon.ico"))?;
            TrayIconBuilder::with_id("lumi-to-gpt")
                .icon(icon)
                .tooltip("LUMI to GPT")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id().as_ref() {
                    "account" => {
                        let _ = show_window(app, "account");
                    }
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if matches!(
                        event,
                        TrayIconEvent::DoubleClick {
                            button: MouseButton::Left,
                            ..
                        }
                    ) {
                        let _ = show_window(tray.app_handle(), "account");
                    }
                })
                .build(app)?;
            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                if window.label() == "account" {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .run(tauri::generate_context!());
    voice_monitor_stop.store(true, Ordering::SeqCst);
    let _ = voice_monitor.join();
    server.stop.store(true, Ordering::SeqCst);
    let _ = server.thread.join();
    result.map_err(|error| error.into())
}

fn run_gpt_sovits_lifecycle_test(settings: &Settings) -> AppResult<()> {
    let result = (|| {
        if gpt_sovits_server_reachable(&settings.voice.base_url) {
            return Err("GPT-SoVITS 포트를 사용하는 기존 서버를 먼저 종료해 주세요.".into());
        }
        let wav = fetch_gpt_sovits_wav(&settings.voice, "루미 음성 자동 실행 테스트예요.")?;
        let output = local_data_dir().join("gpt-sovits-self-test.wav");
        fs::write(output, wav)?;

        let deadline =
            Instant::now() + gpt_sovits_idle_timeout(&settings.voice) + Duration::from_secs(5);
        while gpt_sovits_server_reachable(&settings.voice.base_url) && Instant::now() < deadline {
            enforce_gpt_sovits_lifetime(settings);
            thread::sleep(Duration::from_millis(250));
        }
        if gpt_sovits_server_reachable(&settings.voice.base_url) {
            return Err("GPT-SoVITS 유휴 종료 시간이 초과되었습니다.".into());
        }
        Ok(())
    })();
    if result.is_err() {
        stop_managed_gpt_sovits();
    }
    result
}

fn real_main() -> AppResult<()> {
    let arguments = env::args().skip(1).collect::<Vec<_>>();
    let mut settings = load_settings();
    if arguments.iter().any(|value| value == "--mcp") {
        return run_mcp(&settings);
    }
    if arguments.iter().any(|value| value == "--test-gpt-sovits") {
        return run_gpt_sovits_lifecycle_test(&settings);
    }
    if arguments
        .iter()
        .any(|value| value == "--configure-gpt-sovits")
    {
        configure_lumi_chat(&settings)?;
        return configure_gpt_sovits(
            &mut settings,
            env::var("LUMI_TTS_RUNTIME")?,
            env::var("LUMI_TTS_GPT_WEIGHTS")?,
            env::var("LUMI_TTS_SOVITS_WEIGHTS")?,
            env::var("LUMI_TTS_REFERENCE_AUDIO")?,
            env::var("LUMI_TTS_REFERENCE_TEXT")?,
        );
    }
    if migrate_legacy_voice_settings(&mut settings)? {
        save_settings(&settings)?;
    }
    if synchronize_voice_settings(&mut settings)? {
        save_settings(&settings)?;
    }
    configure_lumi_chat(&settings)?;
    if arguments
        .iter()
        .any(|value| value == "--configure-lumi-chat")
    {
        return Ok(());
    }
    let server = start_http_server(settings.clone())?;
    if arguments.iter().any(|value| value == "--headless") {
        let _ = server.thread.join();
        return Ok(());
    }
    run_gui(server, settings)
}

fn main() {
    if let Err(error) = real_main() {
        eprintln!("{APP_NAME}: {error}");
        std::process::exit(1);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn prompt_keeps_roles_and_one_image() {
        let messages = json!([
            {"role":"system","content":"반말로 말해."},
            {"role":"user","content":[
                {"type":"text","text":"화면 봐 줘"},
                {"type":"image_url","image_url":{"url":"data:image/png;base64,AA=="}}
            ]}
        ]);
        let (prompt, images) = build_codex_prompt(&messages).unwrap();
        assert!(prompt.contains("[시스템]\n반말로 말해."));
        assert!(prompt.contains("[사용자]\n화면 봐 줘"));
        assert_eq!(images, vec!["data:image/png;base64,AA=="]);
    }

    #[test]
    fn invalid_lumi_path_is_rejected() {
        assert!(!is_lumi_app_dir(Path::new(r"Z:\missing-lumi\app")));
    }

    #[test]
    fn lumi_message_keeps_full_text_and_adds_reading_time() {
        let text = "응답이 중간에서 잘리지 않고 마지막 문장까지 보여요.";
        let payload = lumi_message_payload(text).unwrap();
        assert_eq!(payload, format!("@7190\n{text}"));

        assert!(lumi_message_payload("짧아요")
            .unwrap()
            .starts_with("@6000\n"));
        assert!(lumi_message_payload(&"긴".repeat(500))
            .unwrap()
            .starts_with("@59000\n"));
    }

    #[test]
    fn lumi_chat_configuration_preserves_other_settings() {
        let root = env::temp_dir().join(format!("lumi-chat-test-{}", Uuid::new_v4().simple()));
        let conf = root.join("conf");
        fs::create_dir_all(root.join("speech")).unwrap();
        fs::create_dir_all(&conf).unwrap();
        fs::write(root.join("Shimeji-ee.jar"), b"test").unwrap();
        fs::write(conf.join("mod_ai_chat.txt"), b"unlock").unwrap();
        let ai_settings = conf.join("ai.properties");
        let original = "tts.enabled=true\nllm.provider=ollama\nllm.key.openai=old-secret\n";
        fs::write(&ai_settings, original).unwrap();
        let settings = Settings {
            port: 34567,
            lumi_app_dir: root.to_string_lossy().into_owned(),
            ..Settings::default()
        };

        configure_lumi_chat(&settings).unwrap();

        let updated = fs::read_to_string(&ai_settings).unwrap();
        assert!(updated.contains("tts.enabled=true"));
        assert!(updated.contains("llm.provider=gpt_web"));
        assert!(updated.contains("llm.base.gpt_web=http://127.0.0.1:34567/v1"));
        assert!(updated.contains("llm.key.openai=old-secret"));
        assert!(updated.contains("llm.key.gpt_web=lumi-to-gpt"));
        assert!(updated.contains("llm.model.gpt_web=gpt-5.6-luna"));
        assert!(updated.contains("chatter.enabled=false"));
        assert!(updated.contains("screenwatch.enabled=false"));
        assert_eq!(
            fs::read_to_string(ai_settings.with_extension("properties.lumi-to-gpt.bak")).unwrap(),
            original
        );
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn voice_settings_are_restored_after_lumi_chat_update_reset() {
        let root = env::temp_dir().join(format!(
            "lumi-voice-update-test-{}",
            Uuid::new_v4().simple()
        ));
        fs::create_dir_all(root.join("speech")).unwrap();
        fs::create_dir_all(root.join("conf")).unwrap();
        fs::write(root.join("Shimeji-ee.jar"), b"test").unwrap();
        let ai_settings = root.join("conf").join("ai.properties");
        fs::write(
            &ai_settings,
            b"tts.enabled=false\ntts.provider=fish\nchatter.enabled=true\n",
        )
        .unwrap();
        let mut settings = Settings {
            lumi_app_dir: root.to_string_lossy().into_owned(),
            voice: GptSovitsSettings {
                enabled: true,
                power_mode: VoicePowerMode::UltraSaver,
                runtime_dir: "runtime-folder".to_owned(),
                gpt_weights_path: "lumi.ckpt".to_owned(),
                sovits_weights_path: "lumi.pth".to_owned(),
                reference_audio_path: "lumi.wav".to_owned(),
                prompt_text: "루미 참조 대사".to_owned(),
                speed_factor: 1.15,
                ..GptSovitsSettings::default()
            },
            ..Settings::default()
        };

        assert!(!synchronize_voice_settings(&mut settings).unwrap());
        assert!(property_bool(&ai_settings, VOICE_MANAGED_KEY));
        assert!(property_bool(&ai_settings, "tts.enabled"));
        assert_eq!(
            property_value(&ai_settings, "tts.provider").as_deref(),
            Some("gpt_sovits")
        );
        assert_eq!(
            property_value(&ai_settings, "tts.gpt_sovits.gpt_weights").as_deref(),
            Some("lumi.ckpt")
        );
        assert_eq!(
            property_value(&ai_settings, "tts.gpt_sovits.reference_text").as_deref(),
            Some("루미 참조 대사")
        );
        assert_eq!(
            property_value(&ai_settings, "tts.gpt_sovits.power_mode").as_deref(),
            Some("ultra_saver")
        );
        assert_eq!(
            property_value(&ai_settings, "chatter.enabled").as_deref(),
            Some("true")
        );
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn native_voice_edits_refresh_the_persistent_copy() {
        let root =
            env::temp_dir().join(format!("lumi-voice-edit-test-{}", Uuid::new_v4().simple()));
        fs::create_dir_all(root.join("speech")).unwrap();
        fs::create_dir_all(root.join("conf")).unwrap();
        fs::write(root.join("Shimeji-ee.jar"), b"test").unwrap();
        fs::write(
            root.join("conf").join("ai.properties"),
            concat!(
                "lumi_to_gpt.voice.managed=true\n",
                "tts.enabled=true\n",
                "tts.provider=gpt_sovits\n",
                "tts.gpt_sovits.base=http\\://127.0.0.1\\:9881\n",
                "tts.gpt_sovits.runtime=new-runtime\n",
                "tts.gpt_sovits.gpt_weights=new.ckpt\n",
                "tts.gpt_sovits.sovits_weights=new.pth\n",
                "tts.gpt_sovits.reference_audio=new.wav\n",
                "tts.gpt_sovits.reference_text=새 참조 대사\n",
                "tts.gpt_sovits.text_language=ko\n",
                "tts.gpt_sovits.prompt_language=ko\n",
                "tts.gpt_sovits.power_mode=balanced\n",
                "tts.gpt_sovits.speed=0.9\n"
            ),
        )
        .unwrap();
        let mut settings = Settings {
            lumi_app_dir: root.to_string_lossy().into_owned(),
            voice: GptSovitsSettings {
                runtime_dir: "old-runtime".to_owned(),
                gpt_weights_path: "old.ckpt".to_owned(),
                sovits_weights_path: "old.pth".to_owned(),
                reference_audio_path: "old.wav".to_owned(),
                ..GptSovitsSettings::default()
            },
            ..Settings::default()
        };

        assert!(synchronize_voice_settings(&mut settings).unwrap());
        assert!(settings.voice.enabled);
        assert_eq!(settings.voice.base_url, "http://127.0.0.1:9881");
        assert_eq!(settings.voice.runtime_dir, "new-runtime");
        assert_eq!(settings.voice.gpt_weights_path, "new.ckpt");
        assert_eq!(settings.voice.prompt_text, "새 참조 대사");
        assert!((settings.voice.speed_factor - 0.9).abs() < f32::EPSILON);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn native_gpt_sovits_fields_are_decoded_and_used() {
        let root = env::temp_dir().join(format!(
            "lumi-native-voice-test-{}",
            Uuid::new_v4().simple()
        ));
        fs::create_dir_all(root.join("speech")).unwrap();
        fs::create_dir_all(root.join("conf")).unwrap();
        fs::write(root.join("Shimeji-ee.jar"), b"test").unwrap();
        fs::write(
            root.join("conf").join("ai.properties"),
            concat!(
                "tts.enabled=true\n",
                "tts.provider=gpt_sovits\n",
                "tts.gpt_sovits.runtime=C\\:\\\\Lumi\\u0020Voice\n",
                "tts.gpt_sovits.reference_text=\\ub098\\ub294 \\ub8e8\\ubbf8\\uc608\\uc694\n",
                "tts.gpt_sovits.power_mode=ultra_saver\n",
                "tts.gpt_sovits.speed=1.15\n"
            ),
        )
        .unwrap();
        let settings = Settings {
            lumi_app_dir: root.to_string_lossy().into_owned(),
            ..Settings::default()
        };

        let voice = gpt_sovits_settings_from_lumi(&settings);

        assert!(voice.enabled);
        assert_eq!(voice.runtime_dir, r"C:\Lumi Voice");
        assert_eq!(voice.prompt_text, "나는 루미예요");
        assert_eq!(voice.power_mode, VoicePowerMode::UltraSaver);
        assert!((voice.speed_factor - 1.15).abs() < f32::EPSILON);
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn java_tts_payload_keeps_spoken_text_and_reference_text_separate() {
        let payload = json!({
            "text": "GPT가 실제로 답한 문장",
            "reference_audio_path": r"D:\voice\lumi.wav",
            "prompt_text": "참조 WAV에서 말한 문장",
            "text_language": "ko",
            "prompt_language": "ko",
            "speed_factor": "1.0"
        });

        let voice = gpt_sovits_settings_from_payload(&payload).unwrap();

        assert_eq!(payload["text"], "GPT가 실제로 답한 문장");
        assert_eq!(voice.prompt_text, "참조 WAV에서 말한 문장");
    }

    #[test]
    fn gpt_sovits_only_accepts_local_server() {
        assert_eq!(
            gpt_sovits_endpoint("http://127.0.0.1:9880").unwrap(),
            "http://127.0.0.1:9880/tts"
        );
        assert_eq!(
            gpt_sovits_endpoint("http://localhost:9880/tts/").unwrap(),
            "http://localhost:9880/tts"
        );
        assert!(gpt_sovits_endpoint("https://example.com/tts").is_err());
        assert!(gpt_sovits_endpoint("http://localhost@example.com/tts").is_err());
    }

    #[test]
    fn gpt_sovits_finds_integrated_runtime_in_one_child_folder() {
        let root = env::temp_dir().join(format!("gpt-sovits-runtime-{}", Uuid::new_v4().simple()));
        let package = root.join("GPT-SoVITS-v2");
        fs::create_dir_all(package.join("runtime")).unwrap();
        fs::write(package.join("runtime").join("python.exe"), b"test").unwrap();
        fs::write(package.join("api_v2.py"), b"test").unwrap();
        let voice = GptSovitsSettings {
            runtime_dir: root.to_string_lossy().into_owned(),
            ..GptSovitsSettings::default()
        };

        let runtime = resolve_gpt_sovits_runtime(&voice).unwrap();

        assert_eq!(runtime.root, package);
        assert!(runtime.python.ends_with(r"runtime\python.exe"));
        assert!(runtime.api.ends_with("api_v2.py"));
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn portable_installer_voice_configuration_validates_local_files() {
        let root = env::temp_dir().join(format!(
            "gpt-sovits-installer-test-{}",
            Uuid::new_v4().simple()
        ));
        let package = root.join("GPT-SoVITS-v2");
        fs::create_dir_all(package.join("runtime")).unwrap();
        fs::write(package.join("runtime").join("python.exe"), b"python").unwrap();
        fs::write(package.join("api_v2.py"), b"api").unwrap();
        let gpt = root.join("lumi.ckpt");
        let sovits = root.join("lumi.pth");
        let reference = root.join("reference.wav");
        fs::write(&gpt, b"gpt").unwrap();
        fs::write(&sovits, b"sovits").unwrap();
        fs::write(&reference, b"wav").unwrap();

        let voice = gpt_sovits_configuration(
            root.to_string_lossy().into_owned(),
            gpt.to_string_lossy().into_owned(),
            sovits.to_string_lossy().into_owned(),
            reference.to_string_lossy().into_owned(),
            "참조 대사".to_owned(),
        )
        .unwrap();
        assert!(voice.enabled);
        assert_eq!(voice.prompt_text, "참조 대사");
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn gpt_sovits_stop_policy_respects_focus_activity_and_idle_time() {
        assert!(should_stop_gpt_sovits(
            true,
            true,
            1,
            Duration::ZERO,
            GPT_SOVITS_BALANCED_IDLE_TIMEOUT
        ));
        assert!(!should_stop_gpt_sovits(
            false,
            true,
            0,
            GPT_SOVITS_BALANCED_IDLE_TIMEOUT - Duration::from_secs(1),
            GPT_SOVITS_BALANCED_IDLE_TIMEOUT
        ));
        assert!(should_stop_gpt_sovits(
            false,
            true,
            0,
            GPT_SOVITS_BALANCED_IDLE_TIMEOUT,
            GPT_SOVITS_BALANCED_IDLE_TIMEOUT
        ));
        assert!(!should_stop_gpt_sovits(
            false,
            false,
            1,
            GPT_SOVITS_BALANCED_IDLE_TIMEOUT,
            GPT_SOVITS_BALANCED_IDLE_TIMEOUT
        ));
        assert!(should_stop_gpt_sovits(
            false,
            false,
            0,
            Duration::ZERO,
            GPT_SOVITS_BALANCED_IDLE_TIMEOUT
        ));
    }

    #[test]
    fn voice_power_modes_use_simple_fixed_timeouts() {
        assert_eq!(
            voice_power_idle_timeout(VoicePowerMode::Balanced),
            Duration::from_secs(10 * 60)
        );
        assert_eq!(
            voice_power_idle_timeout(VoicePowerMode::UltraSaver),
            Duration::from_secs(60)
        );
        let old_settings: GptSovitsSettings =
            serde_json::from_value(json!({"enabled":true})).unwrap();
        assert_eq!(old_settings.power_mode, VoicePowerMode::Balanced);
    }

    #[test]
    fn gpt_sovits_sends_official_v2_request_and_reads_wav() {
        let server = Server::http("127.0.0.1:0").unwrap();
        let address = server.server_addr().to_ip().unwrap();
        let request_thread = thread::spawn(move || {
            let mut request = server.recv().unwrap();
            assert_eq!(request.method(), &Method::Post);
            assert_eq!(request.url(), "/tts");
            let mut body = String::new();
            request.as_reader().read_to_string(&mut body).unwrap();
            let payload: Value = serde_json::from_str(&body).unwrap();
            assert_eq!(payload["text"], "테스트 문장");
            assert_eq!(payload["text_lang"], "ko");
            assert_eq!(payload["ref_audio_path"], r"D:\voice\lumi.wav");
            assert_eq!(payload["prompt_text"], "루미 참조 대사");
            assert_eq!(payload["prompt_lang"], "ko");
            assert_eq!(payload["media_type"], "wav");
            assert_eq!(payload["streaming_mode"], false);
            let mut wav = b"RIFF".to_vec();
            wav.extend_from_slice(&[36, 0, 0, 0]);
            wav.extend_from_slice(b"WAVEfmt ");
            request.respond(Response::from_data(wav)).unwrap();
        });
        let voice = GptSovitsSettings {
            enabled: true,
            base_url: format!("http://{address}"),
            reference_audio_path: r"D:\voice\lumi.wav".to_owned(),
            prompt_text: "루미 참조 대사".to_owned(),
            ..GptSovitsSettings::default()
        };

        let wav = fetch_gpt_sovits_wav(&voice, "테스트 문장").unwrap();

        assert_eq!(&wav[0..4], b"RIFF");
        assert_eq!(&wav[8..12], b"WAVE");
        request_thread.join().unwrap();
    }

    #[test]
    fn gpt_sovits_loads_both_tuned_weights_before_synthesis() {
        let server = Server::http("127.0.0.1:0").unwrap();
        let address = server.server_addr().to_ip().unwrap();
        let request_thread = thread::spawn(move || {
            let gpt_request = server.recv().unwrap();
            assert_eq!(gpt_request.method(), &Method::Get);
            assert!(gpt_request
                .url()
                .starts_with("/set_gpt_weights?weights_path="));
            assert!(gpt_request.url().contains("LUMI%20e10.ckpt"));
            gpt_request.respond(Response::from_string("ok")).unwrap();

            let sovits_request = server.recv().unwrap();
            assert_eq!(sovits_request.method(), &Method::Get);
            assert!(sovits_request
                .url()
                .starts_with("/set_sovits_weights?weights_path="));
            assert!(sovits_request.url().contains("LUMI_e8_s880.pth"));
            sovits_request.respond(Response::from_string("ok")).unwrap();

            let tts_request = server.recv().unwrap();
            assert_eq!(tts_request.method(), &Method::Post);
            assert_eq!(tts_request.url(), "/tts");
            let mut wav = b"RIFF".to_vec();
            wav.extend_from_slice(&[36, 0, 0, 0]);
            wav.extend_from_slice(b"WAVEfmt ");
            tts_request.respond(Response::from_data(wav)).unwrap();
        });
        let voice = GptSovitsSettings {
            enabled: true,
            base_url: format!("http://{address}"),
            gpt_weights_path: r"C:\models\LUMI e10.ckpt".to_owned(),
            sovits_weights_path: r"C:\models\LUMI_e8_s880.pth".to_owned(),
            reference_audio_path: r"C:\voice\lumi.wav".to_owned(),
            ..GptSovitsSettings::default()
        };

        fetch_gpt_sovits_wav(&voice, "가중치 적용 테스트").unwrap();

        request_thread.join().unwrap();
    }

    #[test]
    fn legacy_sidecar_voice_migrates_to_native_tts_provider() {
        let root = env::temp_dir().join(format!("lumi-voice-test-{}", Uuid::new_v4().simple()));
        fs::create_dir_all(root.join("speech")).unwrap();
        fs::create_dir_all(root.join("conf")).unwrap();
        fs::write(root.join("Shimeji-ee.jar"), b"test").unwrap();
        let ai_settings = root.join("conf").join("ai.properties");
        fs::write(&ai_settings, b"tts.enabled=false\ntts.provider=fish\n").unwrap();
        let mut settings = Settings {
            lumi_app_dir: root.to_string_lossy().into_owned(),
            voice: GptSovitsSettings {
                enabled: true,
                ..GptSovitsSettings::default()
            },
            lumi_tts_restore_enabled: Some(false),
            ..Settings::default()
        };

        assert!(migrate_legacy_voice_settings(&mut settings).unwrap());
        assert!(property_bool(&ai_settings, "tts.enabled"));
        assert_eq!(settings.lumi_tts_restore_enabled, None);
        assert_eq!(
            property_value(&ai_settings, "tts.provider").unwrap(),
            "gpt_sovits"
        );
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn mcp_exposes_notify_tool() {
        let request = json!({"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}});
        let response = handle_mcp(&request, &Settings::default()).unwrap();
        assert_eq!(response["result"]["tools"][0]["name"], "notify_lumi");
    }
}
