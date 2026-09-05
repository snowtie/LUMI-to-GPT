fn main() {
    let manifest = tauri_build::AppManifest::new().commands(&[
        "prewarm_gpt_sovits",
        "open_codex_login_url",
        "open_latest_release",
        "install_latest_update",
    ]);
    let attributes = tauri_build::Attributes::new().app_manifest(manifest);
    tauri_build::try_build(attributes).expect("failed to run Tauri build script");
}
