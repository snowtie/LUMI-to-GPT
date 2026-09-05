from __future__ import annotations

import hashlib
import json
import io
import os
import socket
import struct
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
import zipfile
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


PROJECT_DIR = Path(__file__).resolve().parent.parent
APP_EXE = PROJECT_DIR / "release" / "LUMI to GPT.exe"
RELEASE_DIR = PROJECT_DIR / "release"
VERSION = "1.0.1"
LONG_RESPONSE = "긴 응답 시작. " + ("마지막까지 잘리지 않는 문장입니다. " * 24) + "긴 응답 끝."
LUMI_CHAT_JAR = Path(
    os.environ.get(
        "LUMI_CHAT_JAR",
        r"D:\Steam\steamapps\common\Little LUMI\app\Shimeji-ee.jar",
    )
)
LUMI_JAVA = LUMI_CHAT_JAR.parent / "jre" / "bin" / "java.exe"
PATCH_JAR = RELEASE_DIR / "little-lumi-patch" / "lumi-to-gpt-little-lumi-patch.jar"
JAVAC_25 = Path(
    os.environ.get(
        "JAVAC_25",
        PROJECT_DIR.parents[1] / "work" / "java-tools" / "jdk25" / "bin" / "javac.exe",
    )
)


def free_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def get_json(url: str) -> dict:
    request = urllib.request.Request(url)
    with urllib.request.urlopen(request, timeout=1) as response:
        if response.status == 204:
            return {}
        return json.loads(response.read().decode("utf-8"))


def post_json(url: str, payload: dict, *, timeout: float = 10) -> dict:
    headers = {"Content-Type": "application/json; charset=utf-8"}
    request = urllib.request.Request(
        url,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return json.loads(response.read().decode("utf-8"))


def parse_speech_payload(payload: str) -> tuple[int, str]:
    timing, separator, text = payload.partition("\n")
    if not separator or not timing.startswith("@"):
        raise AssertionError(payload)
    return int(timing[1:]), text


def create_fake_lumi(root: Path) -> None:
    (root / "speech").mkdir(parents=True)
    (root / "conf").mkdir()
    (root / "Shimeji-ee.jar").write_bytes(b"test")
    (root / "conf" / "mod_ai_chat.txt").write_text("unlock\n", encoding="utf-8")
    (root / "conf" / "ai.properties").write_text(
        "tts.enabled=true\nchatter.enabled=false\nllm.provider=ollama\n",
        encoding="utf-8",
    )


def create_fake_codex_archive(path: Path) -> None:
    with zipfile.ZipFile(path, "w") as archive:
        archive.writestr("codex-app-server-x86_64-pc-windows-msvc.exe", b"test-runtime")


def run_lumi_chat_request(lumi_root: Path, *, timeout_seconds: float = 10) -> str:
    if not LUMI_CHAT_JAR.is_file() or not LUMI_JAVA.is_file():
        raise FileNotFoundError({"jar": str(LUMI_CHAT_JAR), "java": str(LUMI_JAVA)})
    source = """\
import java.util.List;

public final class LumiChatClientSmoke {
    public static void main(String[] args) throws Exception {
        Object settings = Class.forName("com.group_finity.mascot.lumi.ai.AiSettings")
            .getMethod("get")
            .invoke(null);
        Class<?> settingsClass = settings.getClass();
        System.err.println("dir=" + System.getProperty("user.dir"));
        System.err.println("provider=" + settingsClass.getMethod("llmProvider").invoke(settings));
        System.err.println("protocol=" + settingsClass.getMethod("llmProtocol").invoke(settings));
        System.err.println("base=" + settingsClass.getMethod("llmBase").invoke(settings));
        System.err.println("model=" + settingsClass.getMethod("llmModel").invoke(settings));
        System.err.flush();
        Class<?> messageClass = Class.forName("com.group_finity.mascot.lumi.ai.LlmClient$Msg");
        Object message = messageClass
            .getConstructor(String.class, String.class)
            .newInstance("user", "루미 챗 실제 클라이언트 테스트");
        Class<?> clientClass = Class.forName("com.group_finity.mascot.lumi.ai.LlmClient");
        String reply = (String) clientClass
            .getMethod("chat", String.class, List.class)
            .invoke(null, "루미 챗 시스템 지침", List.of(message));
        System.out.print(reply);
        System.out.flush();
        System.exit(0);
    }
}
"""
    with tempfile.TemporaryDirectory() as classes_dir:
        source_path = Path(classes_dir) / "LumiChatClientSmoke.java"
        source_path.write_text(source, encoding="utf-8")
        compile_result = subprocess.run(
            [str(JAVAC_25), "-encoding", "UTF-8", "-cp", str(LUMI_CHAT_JAR), str(source_path)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=30,
        )
        if compile_result.returncode != 0:
            raise AssertionError(compile_result.stderr or compile_result.stdout)
        classpath = os.pathsep.join((str(PATCH_JAR), str(LUMI_CHAT_JAR), classes_dir))
        try:
            result = subprocess.run(
                [
                    str(LUMI_JAVA),
                    "-Djdk.httpclient.HttpClient.log=errors,requests,headers",
                    "-cp",
                    classpath,
                    "LumiChatClientSmoke",
                ],
                cwd=lumi_root,
                text=True,
                encoding="utf-8",
                capture_output=True,
                timeout=timeout_seconds,
            )
        except subprocess.TimeoutExpired as error:
            raise AssertionError({"stdout": error.stdout, "stderr": error.stderr}) from error
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)
        return result.stdout


def run_lumi_tts_request(lumi_root: Path) -> str:
    source = """\
import com.group_finity.mascot.lumi.ai.TtsClient;

public final class LumiTtsClientSmoke {
    public static void main(String[] args) throws Exception {
        TtsClient.Audio audio = TtsClient.synthesize("GPT 답변 원문 그대로", "Lumi");
        System.out.print(audio.format().getSampleRate() + ":" + audio.pcm().length);
    }
}
"""
    with tempfile.TemporaryDirectory() as classes_dir:
        source_path = Path(classes_dir) / "LumiTtsClientSmoke.java"
        source_path.write_text(source, encoding="utf-8")
        compile_result = subprocess.run(
            [str(JAVAC_25), "-encoding", "UTF-8", "-cp", str(LUMI_CHAT_JAR), str(source_path)],
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=30,
        )
        if compile_result.returncode != 0:
            raise AssertionError(compile_result.stderr or compile_result.stdout)
        classpath = os.pathsep.join((str(PATCH_JAR), str(LUMI_CHAT_JAR), classes_dir))
        result = subprocess.run(
            [str(LUMI_JAVA), "-cp", classpath, "LumiTtsClientSmoke"],
            cwd=lumi_root,
            text=True,
            encoding="utf-8",
            capture_output=True,
            timeout=160,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)
        return result.stdout


def test_packaged_mcp() -> str:
    with tempfile.TemporaryDirectory() as temporary:
        lumi_root = Path(temporary)
        create_fake_lumi(lumi_root)
        env = os.environ.copy()
        env["LUMI_APP_DIR"] = temporary
        env["LUMI_BRIDGE_NOTIFY_URL"] = "http://127.0.0.1:9/notify"
        requests = [
            {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}},
            {"jsonrpc": "2.0", "method": "notifications/initialized"},
            {"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}},
            {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "tools/call",
                "params": {"name": "notify_lumi", "arguments": {"text": "패키지 MCP 성공"}},
            },
        ]
        result = subprocess.run(
            [str(APP_EXE), "--mcp"],
            input="".join(json.dumps(request, ensure_ascii=False) + "\n" for request in requests),
            text=True,
            encoding="utf-8",
            capture_output=True,
            env=env,
            timeout=15,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            check=True,
        )
        responses = [json.loads(line) for line in result.stdout.splitlines() if line.strip()]
        if [response["id"] for response in responses] != [1, 2, 3]:
            raise AssertionError(responses)
        tool = responses[1]["result"]["tools"][0]
        if tool["name"] != "notify_lumi" or "speak" in tool["inputSchema"]["properties"]:
            raise AssertionError(tool)
        duration_ms, notification = parse_speech_payload(
            (lumi_root / "speech" / "say.txt").read_text(encoding="utf-8")
        )
        if duration_ms != 6000 or notification != "패키지 MCP 성공":
            raise AssertionError({"duration_ms": duration_ms, "notification": notification})
        return notification


def test_release_package() -> dict[str, object]:
    project_ui = (PROJECT_DIR / "ui" / "index.html").read_text(encoding="utf-8")
    for expected in (
        "ChatGPT 계정 연결",
        "/auth/login",
        "GPT-5.6 Luna",
        "prewarm_gpt_sovits",
        "api.github.com/repos/snowtie/LUMI-to-GPT/releases/latest",
        "새 버전 받기",
        'src="lumi-chat-addon.png"',
    ):
        if expected not in project_ui:
            raise AssertionError({"missing_account_ui": expected})
    for removed in ("현재 프로젝트 연결", "chatgpt.com/backend-api", "bridge_next"):
        if removed in project_ui:
            raise AssertionError({"obsolete_web_bridge_ui": removed})
    if (PROJECT_DIR / "src-tauri" / "src" / "init.js").exists():
        raise AssertionError("ChatGPT DOM 주입 스크립트가 남아 있습니다.")

    tauri_commands = (
        "prewarm_gpt_sovits",
        "open_codex_login_url",
        "open_latest_release",
    )
    app_manifest = (PROJECT_DIR / "src-tauri" / "build.rs").read_text(encoding="utf-8")
    capability = json.loads(
        (PROJECT_DIR / "src-tauri" / "capabilities" / "chatgpt.json").read_text(encoding="utf-8")
    )
    permissions = set(capability["permissions"])
    for command in tauri_commands:
        permission = f"allow-{command.replace('_', '-')}"
        generated = PROJECT_DIR / "src-tauri" / "permissions" / "autogenerated" / f"{command}.toml"
        if f'"{command}"' not in app_manifest or permission not in permissions or not generated.is_file():
            raise AssertionError(
                {"command": command, "permission": permission, "generated": generated.is_file()}
            )
    if not capability.get("local") or "remote" in capability:
        raise AssertionError({"non_local_account_capability": capability})
    if permissions & {"allow-bridge-next", "allow-bridge-result"}:
        raise AssertionError({"obsolete_dom_bridge_permissions": sorted(permissions)})

    content = RELEASE_DIR / "workshop-content"
    tool_root = content / "LUMI-to-GPT"
    required = [
        RELEASE_DIR / "LUMI to GPT.exe",
        RELEASE_DIR / "INSTALL.cmd",
        RELEASE_DIR / "install.ps1",
        RELEASE_DIR / "README.md",
        RELEASE_DIR / "RELEASE_NOTES.md",
        RELEASE_DIR / "LICENSE",
        RELEASE_DIR / "NOTICE.txt",
        RELEASE_DIR / "VOICE_MODEL_NOTICE.txt",
        RELEASE_DIR / "LUMI-to-GPT.zip",
        RELEASE_DIR / f"LUMI-to-GPT-v{VERSION}-windows-x64.zip",
        RELEASE_DIR / "SHA256SUMS.txt",
        RELEASE_DIR / "workshop-description.txt",
        RELEASE_DIR / "workshop-dependency.txt",
        RELEASE_DIR / "workshop-preview.png",
        RELEASE_DIR / "little-lumi-patch" / "lumi-to-gpt-little-lumi-patch.jar",
        RELEASE_DIR / "little-lumi-patch" / "original-class-sha256.json",
        content / "README.txt",
        tool_root / "LUMI to GPT.exe",
        tool_root / "INSTALL.cmd",
        tool_root / "install.ps1",
        tool_root / "README.md",
        tool_root / "RELEASE_NOTES.md",
        tool_root / "LICENSE",
        tool_root / "NOTICE.txt",
        tool_root / "VOICE_MODEL_NOTICE.txt",
        tool_root / "little-lumi-patch" / "lumi-to-gpt-little-lumi-patch.jar",
        tool_root / "little-lumi-patch" / "original-class-sha256.json",
    ]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise AssertionError({"missing_release_files": missing})

    with zipfile.ZipFile(PATCH_JAR) as patch:
        patch_files = set(patch.namelist())
        expected_patch_classes = {
            "com/group_finity/mascot/lumi/SpeechDirector.class",
            "com/group_finity/mascot/lumi/ai/AiSettings.class",
            "com/group_finity/mascot/lumi/ai/AiSettingsDialog.class",
            "com/group_finity/mascot/lumi/ai/AiSettingsDialog$Page.class",
            "com/group_finity/mascot/lumi/ai/AiSettingsDialog$TabShell.class",
            "com/group_finity/mascot/lumi/ai/TtsClient.class",
            "com/group_finity/mascot/lumi/ai/TtsClient$Audio.class",
            "META-INF/lumi-to-gpt-patch.properties",
        }
        if not expected_patch_classes <= patch_files:
            raise AssertionError({"patch_entries": sorted(patch_files)})
        marker = patch.read("META-INF/lumi-to-gpt-patch.properties").decode("utf-8")
        if f"version={VERSION}" not in marker:
            raise AssertionError(marker)
        for entry in expected_patch_classes:
            if not entry.endswith(".class"):
                continue
            class_bytes = patch.read(entry)
            if class_bytes[:4] != b"\xca\xfe\xba\xbe" or int.from_bytes(class_bytes[6:8], "big") != 69:
                raise AssertionError({"invalid_java_25_class": entry})

    forbidden = [
        content / "conf" / "mod_ai_chat.txt",
        content / "conf" / "ai.properties",
        content / "voice",
        tool_root / "extension",
    ]
    present = [str(path) for path in forbidden if path.exists()]
    if present:
        raise AssertionError({"copied_lumi_chat_files": present})

    dependency = (RELEASE_DIR / "workshop-dependency.txt").read_text(encoding="utf-8-sig")
    if "3794360578" not in dependency:
        raise AssertionError(dependency)

    with zipfile.ZipFile(RELEASE_DIR / "LUMI-to-GPT.zip") as package:
        archive_files = set(package.namelist())
    expected_archive_files = {
        "LUMI to GPT.exe",
        "INSTALL.cmd",
        "install.ps1",
        "README.md",
        "RELEASE_NOTES.md",
        "LICENSE",
        "NOTICE.txt",
        "VOICE_MODEL_NOTICE.txt",
        "little-lumi-patch/lumi-to-gpt-little-lumi-patch.jar",
        "little-lumi-patch/original-class-sha256.json",
    }
    if archive_files != expected_archive_files:
        raise AssertionError({"archive_files": sorted(archive_files)})

    versioned_package = RELEASE_DIR / f"LUMI-to-GPT-v{VERSION}-windows-x64.zip"
    if versioned_package.read_bytes() != (RELEASE_DIR / "LUMI-to-GPT.zip").read_bytes():
        raise AssertionError("버전 ZIP과 호환용 ZIP의 내용이 다릅니다.")

    checksums = (RELEASE_DIR / "SHA256SUMS.txt").read_text(encoding="utf-8")
    for artifact in (
        RELEASE_DIR / "LUMI to GPT.exe",
        PATCH_JAR,
        versioned_package,
    ):
        digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
        if f"{digest}  {artifact.name}" not in checksums:
            raise AssertionError({"missing_checksum": artifact.name})
    if "4a0ff7071c3d0d4c56a48016d8bc66ca5c8c626d599c0e71300f0de3afa14e79  GPT_weights_v2.7z" not in checksums:
        raise AssertionError("음성 가중치 체크섬이 없습니다.")

    with (RELEASE_DIR / "workshop-preview.png").open("rb") as stream:
        if stream.read(8) != b"\x89PNG\r\n\x1a\n":
            raise AssertionError("창작마당 미리보기가 PNG가 아닙니다.")
        stream.read(8)
        width, height = struct.unpack(">II", stream.read(8))
    if (width, height) != (512, 512):
        raise AssertionError((width, height))
    logo_path = PROJECT_DIR / "ui" / "lumi-chat-addon.png"
    if not logo_path.is_file() or logo_path.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
        raise AssertionError("LUMI Chat Addon 로고 원본이 없습니다.")
    if (PROJECT_DIR / "src-tauri" / "icons" / "icon.ico").stat().st_size < 100_000:
        raise AssertionError("새 LUMI Chat Addon 앱 아이콘이 적용되지 않았습니다.")

    if (RELEASE_DIR / "install.ps1").read_bytes()[:3] != b"\xef\xbb\xbf":
        raise AssertionError("Windows PowerShell 5.1용 UTF-8 BOM이 install.ps1에 없습니다.")
    installer_menu = (RELEASE_DIR / "INSTALL.cmd").read_text(encoding="utf-8")
    if "[3] Add LUMI GPT-SoVITS TTS" not in installer_menu or "INSTALL_MODE=TtsOnly" not in installer_menu:
        raise AssertionError("TTS만 추가하는 설치 선택지가 없습니다.")

    with (
        tempfile.TemporaryDirectory() as install_dir,
        tempfile.TemporaryDirectory() as lumi_dir,
        tempfile.TemporaryDirectory() as package_dir,
    ):
        lumi_root = Path(lumi_dir)
        create_fake_lumi(lumi_root)
        codex_archive = Path(package_dir) / "codex-app-server.zip"
        create_fake_codex_archive(codex_archive)
        env = os.environ.copy()
        env["LUMI_APP_DIR"] = lumi_dir
        env["LOCALAPPDATA"] = install_dir
        powershell = (
            Path(os.environ.get("SystemRoot", r"C:\Windows"))
            / "System32"
            / "WindowsPowerShell"
            / "v1.0"
            / "powershell.exe"
        )
        installer_command = [
            str(powershell),
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(tool_root / "install.ps1"),
            "-TargetRoot",
            install_dir,
            "-CodexAppServerArchive",
            str(codex_archive),
            "-SkipMcp",
            "-SkipShortcut",
            "-SkipLumiPatch",
        ]
        result = subprocess.run(
            installer_command,
            text=True,
            encoding="utf-8",
            capture_output=True,
            env=env,
            timeout=30,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)
        for relative in ("lumi-to-gpt.exe", "codex-app-server.exe", "README.md"):
            if not (Path(install_dir) / relative).is_file():
                raise AssertionError(f"설치 누락: {relative}")
        install_logs = list((Path(install_dir) / "LumiToGPT" / "logs").glob("install-*.log"))
        if len(install_logs) != 1 or "설치 완료" not in install_logs[0].read_text(encoding="utf-8-sig"):
            raise AssertionError({"install_logs": [str(path) for path in install_logs]})
        configured = (lumi_root / "conf" / "ai.properties").read_text(encoding="utf-8")
        for expected in (
            "tts.enabled=true",
            "llm.provider=gpt_web",
            "llm.base.gpt_web=http://127.0.0.1:32123/v1",
            "llm.key.gpt_web=lumi-to-gpt",
            "llm.model.gpt_web=gpt-5.6-luna",
            "chatter.enabled=false",
            "screenwatch.enabled=false",
        ):
            if expected not in configured:
                raise AssertionError({"missing_lumi_chat_setting": expected})

        installed_bridge = Path(install_dir) / "lumi-to-gpt.exe"
        locked_bridge = subprocess.Popen(
            [str(installed_bridge), "--mcp"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        try:
            time.sleep(0.5)
            if locked_bridge.poll() is not None:
                raise AssertionError("재설치 잠금 테스트용 애드온이 바로 종료됐습니다.")
            reinstall = subprocess.run(
                installer_command,
                text=True,
                encoding="utf-8",
                capture_output=True,
                env=env,
                timeout=30,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
            if reinstall.returncode != 0:
                raise AssertionError(reinstall.stderr or reinstall.stdout)
            if "실행 중인 기존 LUMI to GPT를 종료합니다." not in reinstall.stdout:
                raise AssertionError({"missing_running_app_notice": reinstall.stdout})
            locked_bridge.wait(timeout=5)
        finally:
            if locked_bridge.poll() is None:
                locked_bridge.kill()
                locked_bridge.wait(timeout=5)

    with (
        tempfile.TemporaryDirectory() as install_dir,
        tempfile.TemporaryDirectory() as lumi_dir,
        tempfile.TemporaryDirectory() as package_dir,
    ):
        lumi_root = Path(lumi_dir)
        create_fake_lumi(lumi_root)
        package_root = Path(package_dir)
        runtime_archive = package_root / "runtime.7z"
        weights_archive = package_root / "weights.7z"
        codex_archive = package_root / "codex-app-server.zip"
        create_fake_codex_archive(codex_archive)
        reference = package_root / "reference.wav"
        reference.write_bytes(b"RIFF-test-wave")
        with zipfile.ZipFile(runtime_archive, "w") as archive:
            archive.writestr("GPT-SoVITS-v2/api_v2.py", "# test")
            archive.writestr("GPT-SoVITS-v2/runtime/python.exe", b"test")
        with zipfile.ZipFile(weights_archive, "w") as archive:
            archive.writestr("GPT_weights_v2/LUMI-e10.ckpt", b"gpt")
            archive.writestr("SoVITS_weights_v2/LUMI_e8_s880.pth", b"sovits")
        env = os.environ.copy()
        env["LUMI_APP_DIR"] = lumi_dir
        env["LOCALAPPDATA"] = install_dir
        result = subprocess.run(
            [
                str(powershell),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                str(tool_root / "install.ps1"),
                "-TargetRoot",
                str(Path(install_dir) / "app"),
                "-InstallMode",
                "WithTts",
                "-GptSovitsArchive",
                str(runtime_archive),
                "-VoiceWeightsArchive",
                str(weights_archive),
                "-CodexAppServerArchive",
                str(codex_archive),
                "-ReferenceAudio",
                str(reference),
                "-ReferenceText",
                "테스트 참조 대사",
                "-SkipMcp",
                "-SkipShortcut",
                "-SkipLumiPatch",
            ],
            text=True,
            encoding="utf-8",
            capture_output=True,
            env=env,
            timeout=60,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)
        configured = (lumi_root / "conf" / "ai.properties").read_text(encoding="utf-8")
        for expected in (
            "tts.enabled=true",
            "tts.provider=gpt_sovits",
            "tts.gpt_sovits.reference_text=테스트 참조 대사",
        ):
            if expected not in configured:
                raise AssertionError({"missing_portable_tts_setting": expected})
        persistent = json.loads(
            (Path(install_dir) / "LumiToGPT" / "settings.json").read_text(encoding="utf-8")
        )
        if not persistent["voice"]["enabled"]:
            raise AssertionError("휴대용 TTS 설치가 설정 보관본에 반영되지 않았습니다.")

    with (
        tempfile.TemporaryDirectory() as install_dir,
        tempfile.TemporaryDirectory() as lumi_dir,
        tempfile.TemporaryDirectory() as package_dir,
    ):
        lumi_root = Path(lumi_dir)
        create_fake_lumi(lumi_root)
        package_root = Path(package_dir)
        target_root = Path(install_dir) / "app"
        target_root.mkdir()
        (target_root / "lumi-to-gpt.exe").write_bytes(APP_EXE.read_bytes())
        runtime_archive = package_root / "runtime.7z"
        weights_archive = package_root / "weights.7z"
        reference = package_root / "reference.wav"
        reference.write_bytes(b"RIFF-test-wave")
        with zipfile.ZipFile(runtime_archive, "w") as archive:
            archive.writestr("GPT-SoVITS-v2/api_v2.py", "# test")
            archive.writestr("GPT-SoVITS-v2/runtime/python.exe", b"test")
        with zipfile.ZipFile(weights_archive, "w") as archive:
            archive.writestr("GPT_weights_v2/LUMI-e10.ckpt", b"gpt")
            archive.writestr("SoVITS_weights_v2/LUMI_e8_s880.pth", b"sovits")
        env = os.environ.copy()
        env["LUMI_APP_DIR"] = lumi_dir
        env["LOCALAPPDATA"] = install_dir
        result = subprocess.run(
            [
                str(powershell),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                str(tool_root / "install.ps1"),
                "-TargetRoot",
                str(target_root),
                "-InstallMode",
                "TtsOnly",
                "-GptSovitsArchive",
                str(runtime_archive),
                "-VoiceWeightsArchive",
                str(weights_archive),
                "-ReferenceAudio",
                str(reference),
                "-ReferenceText",
                "TTS 단독 설치 테스트",
                "-SkipMcp",
                "-SkipShortcut",
                "-SkipLumiPatch",
            ],
            text=True,
            encoding="utf-8",
            capture_output=True,
            env=env,
            timeout=60,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr or result.stdout)
        if (target_root / "codex-app-server.exe").exists():
            raise AssertionError("TTS 단독 설치가 Codex App Server를 새로 설치했습니다.")
        configured = (lumi_root / "conf" / "ai.properties").read_text(encoding="utf-8")
        if "tts.gpt_sovits.reference_text=TTS 단독 설치 테스트" not in configured:
            raise AssertionError(configured)
        tts_logs = list((Path(install_dir) / "LumiToGPT" / "logs").glob("install-*.log"))
        if len(tts_logs) != 1 or "모드: TtsOnly" not in tts_logs[0].read_text(encoding="utf-8-sig"):
            raise AssertionError({"tts_only_logs": [str(path) for path in tts_logs]})

    with tempfile.TemporaryDirectory() as install_dir, tempfile.TemporaryDirectory() as lumi_dir:
        create_fake_lumi(Path(lumi_dir))
        env = os.environ.copy()
        env["LUMI_APP_DIR"] = lumi_dir
        env["LOCALAPPDATA"] = install_dir
        result = subprocess.run(
            [
                str(powershell),
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-File",
                str(tool_root / "install.ps1"),
                "-TargetRoot",
                str(Path(install_dir) / "missing-app"),
                "-InstallMode",
                "TtsOnly",
                "-SkipMcp",
                "-SkipShortcut",
                "-SkipLumiPatch",
            ],
            text=True,
            encoding="utf-8",
            capture_output=True,
            env=env,
            timeout=30,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        if result.returncode == 0:
            raise AssertionError("설치 오류 상세 로그 테스트가 성공으로 끝났습니다.")
        failure_logs = list((Path(install_dir) / "LumiToGPT" / "logs").glob("install-*.log"))
        failure_text = failure_logs[0].read_text(encoding="utf-8-sig") if failure_logs else ""
        for expected in ("설치에 실패했습니다.", "실패 단계: 애드온 파일 확인", "오류 종류:", "상세 로그:"):
            if expected not in failure_text:
                raise AssertionError({"missing_failure_detail": expected, "log": failure_text})
    return {
        "files": len(required),
        "archive_files": len(archive_files),
        "preview": "512x512",
        "portable_tts": True,
        "tts_only": True,
        "detailed_install_log": True,
    }


def main() -> int:
    if not APP_EXE.is_file():
        raise FileNotFoundError(APP_EXE)
    with tempfile.TemporaryDirectory() as lumi_dir, tempfile.TemporaryDirectory() as local_dir:
        lumi_root = Path(lumi_dir)
        create_fake_lumi(lumi_root)
        voice_port = free_port()
        captured_voice_payloads: list[dict] = []

        wav_buffer = io.BytesIO()
        with wave.open(wav_buffer, "wb") as output:
            output.setnchannels(1)
            output.setsampwidth(2)
            output.setframerate(16000)
            output.writeframes(b"\0\0" * 1600)
        preview_wav = wav_buffer.getvalue()

        class FakeGptSovitsHandler(BaseHTTPRequestHandler):
            def do_GET(self) -> None:  # noqa: N802
                if not self.path.startswith(("/set_gpt_weights?", "/set_sovits_weights?")):
                    self.send_error(404)
                    return
                self.send_response(200)
                self.send_header("Content-Length", "0")
                self.end_headers()

            def do_POST(self) -> None:  # noqa: N802
                if self.path != "/tts":
                    self.send_error(404)
                    return
                length = int(self.headers.get("Content-Length", "0"))
                captured_voice_payloads.append(json.loads(self.rfile.read(length).decode("utf-8")))
                self.send_response(200)
                self.send_header("Content-Type", "audio/wav")
                self.send_header("Content-Length", str(len(preview_wav)))
                self.end_headers()
                self.wfile.write(preview_wav)

            def log_message(self, _format: str, *_args: object) -> None:
                return

        voice_server = ThreadingHTTPServer(("127.0.0.1", voice_port), FakeGptSovitsHandler)
        voice_thread = threading.Thread(target=voice_server.serve_forever, daemon=True)
        voice_thread.start()
        (lumi_root / "conf" / "ai.properties").write_text(
            "\n".join(
                (
                    "tts.enabled=true",
                    "tts.provider=gpt_sovits",
                    f"tts.gpt_sovits.base=http://127.0.0.1:{voice_port}",
                    r"tts.gpt_sovits.runtime=C\:\\runtime",
                    r"tts.gpt_sovits.gpt_weights=C\:\\models\\lumi.ckpt",
                    r"tts.gpt_sovits.sovits_weights=C\:\\models\\lumi.pth",
                    r"tts.gpt_sovits.reference_audio=D\:\\voice\\lumi.wav",
                    "tts.gpt_sovits.reference_text=참조 음성 문장",
                    "tts.gpt_sovits.text_language=ko",
                    "tts.gpt_sovits.prompt_language=ko",
                    "tts.gpt_sovits.power_mode=ultra_saver",
                    "tts.gpt_sovits.speed=1.0",
                    "chatter.enabled=false",
                    "llm.provider=ollama",
                    "",
                )
            ),
            encoding="utf-8",
        )
        (lumi_root / "speech" / "mascots_pos.txt").write_text(
            "1,10,20,Lumi\n2,30,40,Lumi\n",
            encoding="utf-8",
        )
        bridge_port = free_port()
        local_data = Path(local_dir) / "LumiToGPT"
        local_data.mkdir()
        (local_data / "settings.json").write_text(
            json.dumps({"port": bridge_port, "lumi_app_dir": lumi_dir}),
            encoding="utf-8",
        )
        prompt_log = Path(local_dir) / "codex-prompt.txt"
        fake_codex = Path(local_dir) / "fake_codex_app_server.py"
        fake_codex.write_text(
            """\
import json
import os
import sys

response_text = os.environ["LUMI_TEST_RESPONSE"]
prompt_log = os.environ["LUMI_TEST_PROMPT_LOG"]

def send(message):
    print(json.dumps(message, ensure_ascii=False), flush=True)

for line in sys.stdin:
    message = json.loads(line)
    method = message.get("method")
    request_id = message.get("id")
    if method == "initialized":
        continue
    if method == "initialize":
        send({"id": request_id, "result": {"userAgent": "fake-codex"}})
    elif method == "account/read":
        send({"id": request_id, "result": {"account": {"type": "chatgpt", "email": "test@example.com", "planType": "free"}, "requiresOpenaiAuth": True}})
    elif method == "account/login/start":
        send({"id": request_id, "result": {"type": "chatgptDeviceCode", "loginId": "test-login", "verificationUrl": "https://auth.openai.com/codex/device", "userCode": "TEST-CODE"}})
    elif method == "account/logout":
        send({"id": request_id, "result": {}})
    elif method == "thread/start":
        send({"id": request_id, "result": {"thread": {"id": "lumi-thread"}}})
    elif method == "turn/start":
        params = message["params"]
        text = next(item["text"] for item in params["input"] if item["type"] == "text")
        with open(prompt_log, "w", encoding="utf-8") as output:
            output.write(text)
        send({"id": request_id, "result": {"turn": {"id": "lumi-turn", "status": "inProgress", "items": []}}})
        item = {"id": "answer", "type": "agentMessage", "text": response_text}
        send({"method": "turn/completed", "params": {"threadId": "lumi-thread", "turn": {"id": "lumi-turn", "status": "completed", "items": [item], "error": None}}})
""",
            encoding="utf-8",
        )
        base_url = f"http://127.0.0.1:{bridge_port}"
        process_env = os.environ.copy()
        process_env["LOCALAPPDATA"] = local_dir
        process_env["LUMI_APP_DIR"] = lumi_dir
        process_env["LUMI_ALLOW_TEST_SHUTDOWN"] = "1"
        process_env["LUMI_CODEX_APP_SERVER"] = sys.executable
        process_env["LUMI_CODEX_APP_SERVER_ARGS"] = json.dumps([str(fake_codex)])
        process_env["LUMI_TEST_RESPONSE"] = LONG_RESPONSE
        process_env["LUMI_TEST_PROMPT_LOG"] = str(prompt_log)
        process = subprocess.Popen(
            [str(APP_EXE), "--headless"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            env=process_env,
            creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
        )
        try:
            health = None
            for _ in range(60):
                try:
                    health = get_json(f"{base_url}/health")
                    break
                except (OSError, urllib.error.URLError):
                    time.sleep(0.1)
            if not health or not health.get("ok") or not health.get("lumi_chat_found"):
                raise RuntimeError({"bridge_health": health})
            if health.get("version") != VERSION:
                raise AssertionError({"version": health.get("version")})
            auth = get_json(f"{base_url}/auth/status")
            if not auth.get("connected") or auth.get("model") != "gpt-5.6-luna":
                raise AssertionError({"codex_auth": auth})
            try:
                text = run_lumi_chat_request(lumi_root)
            except Exception as error:
                raise AssertionError(
                    {
                        "java_client": str(error),
                        "health": get_json(f"{base_url}/health"),
                    }
                ) from error
            if text != LONG_RESPONSE:
                raise AssertionError(text)
            captured_prompt = prompt_log.read_text(encoding="utf-8")
            if "[시스템]" not in captured_prompt or "[사용자]" not in captured_prompt:
                raise AssertionError({"captured_prompt": captured_prompt})
            tts_result = run_lumi_tts_request(lumi_root)
            if tts_result != "16000.0:3200":
                raise AssertionError({"tts_result": tts_result})
            if len(captured_voice_payloads) != 1:
                raise AssertionError({"tts_request_count": len(captured_voice_payloads)})
            voice_payload = captured_voice_payloads[0]
            if voice_payload.get("text") != "GPT 답변 원문 그대로":
                raise AssertionError({"tts_text": voice_payload.get("text")})
            if voice_payload.get("prompt_text") != "참조 음성 문장":
                raise AssertionError({"reference_text": voice_payload.get("prompt_text")})
            if (lumi_root / "speech" / "say.txt").exists():
                raise AssertionError("대화 응답이 원본 말풍선 경로 대신 say.txt로 중복 전달됐습니다.")
            voice_health = get_json(f"{base_url}/health")
            if voice_health.get("pending_voice") != 0:
                raise AssertionError({"pending_voice": voice_health.get("pending_voice")})

            saved_settings = json.loads((local_data / "settings.json").read_text(encoding="utf-8"))
            if saved_settings["voice"]["base_url"] != f"http://127.0.0.1:{voice_port}":
                raise AssertionError({"persisted_voice": saved_settings.get("voice")})
            if saved_settings["voice"]["power_mode"] != "ultra_saver":
                raise AssertionError({"voice_power_mode": saved_settings.get("voice")})
            if saved_settings["voice"]["gpt_weights_path"] != r"C:\models\lumi.ckpt":
                raise AssertionError({"voice_weights": saved_settings.get("voice")})

            (lumi_root / "conf" / "ai.properties").write_text(
                "tts.enabled=false\ntts.provider=fish\nchatter.enabled=false\n",
                encoding="utf-8",
            )
            recovery = subprocess.run(
                [str(APP_EXE), "--configure-lumi-chat"],
                text=True,
                encoding="utf-8",
                capture_output=True,
                env=process_env,
                timeout=10,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
            if recovery.returncode != 0:
                raise AssertionError(recovery.stderr or recovery.stdout)
            restored = (lumi_root / "conf" / "ai.properties").read_text(encoding="utf-8")
            for expected in (
                "lumi_to_gpt.voice.managed=true",
                "tts.enabled=true",
                "tts.provider=gpt_sovits",
                r"tts.gpt_sovits.gpt_weights=C\:\\models\\lumi.ckpt",
                "chatter.enabled=false",
            ):
                if expected not in restored:
                    raise AssertionError({"missing_restored_setting": expected, "restored": restored})

            mcp_notification = test_packaged_mcp()
            release = test_release_package()
            print(
                json.dumps(
                    {
                        "health": health["ok"],
                        "version": health["version"],
                        "lumi_chat": health["lumi_chat_found"],
                        "lumi_chat_client": True,
                        "codex_oauth_backend": auth["backend"],
                        "completion_chars": len(text),
                        "completion_ending": text[-7:],
                        "original_lumi_response_path": True,
                        "voice_queue": voice_health["pending_voice"],
                        "native_ai_settings_patch": True,
                        "single_tts_request": len(captured_voice_payloads),
                        "update_reset_recovery": True,
                        "mcp": mcp_notification,
                        "release": release,
                    },
                    ensure_ascii=False,
                )
            )
            return 0
        finally:
            voice_server.shutdown()
            voice_server.server_close()
            voice_thread.join(timeout=3)
            try:
                post_json(
                    f"{base_url}/test/shutdown",
                    {"token": "lumi-smoke-test"},
                    timeout=2,
                )
            except (OSError, urllib.error.URLError):
                pass
            try:
                process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                process.terminate()
                try:
                    process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=3)


if __name__ == "__main__":
    raise SystemExit(main())
