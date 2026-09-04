# LUMI to GPT

`LUMI Chat`을 로그인된 ChatGPT 웹 계정에 연결하는 Windows용 확장 모드입니다. 대화창·말풍선·기억·페르소나는 LUMI Chat이 담당하고, 이 확장은 GPT 웹 연결, 로컬 GPT-SoVITS 목소리와 Python 없는 Codex MCP를 추가합니다.

비공식 커뮤니티 확장이며 STUDIO LUMI, LUMI Chat 제작자 또는 OpenAI의 공식 제품이 아닙니다.

## 필요한 것

- Little LUMI
- Steam 창작마당의 `LUMI Chat` — 필수 의존성
- 로그인 가능한 ChatGPT 웹 계정
- Codex 앱 또는 CLI — 작업 완료 알림을 쓸 때만 필요

별도 OpenAI API 키, Python, 브라우저 확장 프로그램은 필요하지 않습니다. ChatGPT 웹 계정의 메시지 한도와 이용 조건은 그대로 적용됩니다.

## 설치와 사용

1. 창작마당에서 `LUMI Chat`를 구독하고 다운로드 받은 GitHub 배포 ZIP을 풉니다.
2. Little LUMI를 한 번 실행해 LUMI Chat을 활성화한 뒤 종료합니다.
3. `INSTALL.cmd`를 실행하고 `GPT 애드온만` 또는 `GPT 애드온 + 로컬 TTS`를 고릅니다.
4. Little LUMI를 다시 실행합니다.
5. 바탕화면의 `LUMI to GPT`를 실행하고 ChatGPT에 로그인합니다.
6. ChatGPT에서 LUMI 전용 프로젝트를 만들고, 오른쪽 위 `LUMI 프로젝트`에서 `현재 프로젝트 연결`을 누릅니다.
7. Little LUMI의 `루미 AI 설정 → 두뇌`에서 `GPT Web`을 확인합니다.
8. 꼬미를 더블클릭하거나 우클릭 → `말 걸기`로 대화합니다.

현재 실행 파일은 코드 서명되지 않았기 때문에 처음 실행할 때 Windows SmartScreen 경고가 표시될 수 있습니다. 배포자가 제공한 `SHA256SUMS.txt`와 파일 해시가 같은지 확인한 뒤 실행하세요.

설치기는 `conf\ai.properties`의 LLM 연결값만 로컬 브리지로 바꾸고 기존 파일을 `ai.properties.lumi-to-gpt.bak`으로 한 번 백업합니다. 기존 OpenAI 키가 있다면 덮어쓰지 않으며 TTS와 자동 대화 설정 등 나머지 값도 보존합니다.

## 휴대용 설치 모드

- `GPT Add-on only` — 작은 GPT 웹 브리지와 Little LUMI 연동 패치만 설치합니다.
- `GPT Add-on + local GPT-SoVITS TTS` — 위 구성에 GPT-SoVITS 실행 환경과 사용자가 준비한 음성 가중치를 추가하고 `루미 AI 설정`까지 자동으로 채웁니다.

TTS 모드는 약 5.7GB인 [공식 GPT-SoVITS v2 Windows 통합판](https://huggingface.co/lj1995/GPT-SoVITS-windows-package/blob/main/GPT-SoVITS-v2-240821.7z)을 내려받아 `%LOCALAPPDATA%\LumiToGPT`에 풉니다. `GPT_weights_v2.7z`는 저작권과 배포 권한 문제 때문에 저장소나 기본 ZIP에 포함하지 않습니다. 본인이 사용할 권리가 있는 가중치 파일을 `INSTALL.cmd` 옆이나 다운로드 폴더에 둬야 합니다.

참조 음성은 설치된 LUMI Voice Pack에서 찾습니다. 다른 참조 음성을 사용하려면 `install.ps1`의 `-ReferenceAudio`, `-ReferenceText` 옵션으로 직접 지정할 수 있습니다.

## 역할 분담

LUMI Chat이 그대로 담당하는 기능:

- 더블클릭·우클릭 대화 입력창과 말풍선
- 대화 기억과 캐릭터별 페르소나
- 자율 혼잣말과 화면 구경
- Fish Audio/ElevenLabs TTS, 캐릭터별 목소리와 입 모양
- 집중 모드 중 음성 자동 차단

LUMI to GPT가 추가하는 기능:

- LUMI Chat의 OpenAI 호환 요청을 로그인된 ChatGPT 웹으로 전달
- LUMI 전용 ChatGPT 프로젝트 지정
- 답변이 완성된 뒤 전체 내용을 LUMI Chat의 원래 응답 경로에 반환
- LUMI Chat의 원래 말풍선 경로에서 표시 시간을 6~60초로 적용
- 로컬 GPT-SoVITS 선행 준비, 자동 절전·초절전과 집중 모드 즉시 종료
- LUMI Chat의 원래 TTS 재생 경로에 GPT-SoVITS 프로바이더 추가
- Codex 앱·CLI용 단일 Rust MCP 실행 파일

GPT Web, Fish Audio, ElevenLabs, GPT-SoVITS와 페르소나는 모두 Little LUMI의 `루미 AI 설정`에서 관리합니다. ChatGPT 창에는 전용 프로젝트 연결 버튼만 있으며, 별도 대화 입력창이나 AI 설정 사본은 없습니다.

## 개발 및 검증

```powershell
cargo test --manifest-path .\src-tauri\Cargo.toml
.\build.ps1
python .\tests\smoke_release.py
```

소스 코드는 BSD 3-Clause로 공개합니다. Little LUMI와 Shimeji-ee에서 수정한 부분의 원저작권·라이선스는 [NOTICE.txt](NOTICE.txt)에 따릅니다.
독립 배포 ZIP과 창작마당 업로드 폴더는 각각 `release\LUMI-to-GPT-v0.8.3-windows-x64.zip`, `release\workshop-content`에 생성됩니다. `release\SHA256SUMS.txt`로 다운로드 파일을 확인할 수 있습니다. Steam 항목의 필요 항목에는 `LUMI Chat`의 Workshop ID `3794360578`을 지정해야 합니다.
