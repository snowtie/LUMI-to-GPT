fn main() {
    let manifest = tauri_build::AppManifest::new().commands(&[
        "bridge_next",
        "bridge_result",
        "prewarm_gpt_sovits",
    ]);
    let attributes = tauri_build::Attributes::new().app_manifest(manifest);
    tauri_build::try_build(attributes).expect("failed to run Tauri build script");
}
