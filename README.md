# LUMI to GPT

<img src="ui/lumi-chat-addon.png" alt="LUMI Chat Addon" width="280">

`LUMI Chat`을 ChatGPT 계정에 연결하는 Windows용 확장 모드입니다. 대화창·말풍선·기억·페르소나는 LUMI Chat이 그대로 담당하고, 이 확장은 공식 `Codex App Server` OAuth 연결, 로컬 GPT-SoVITS 목소리와 Codex 작업 완료 알림 MCP를 추가합니다.

비공식 커뮤니티 확장이며 STUDIO LUMI, LUMI Chat 제작자 또는 OpenAI의 공식 제품이 아닙니다.

***TTS 음성 가중치를 제공해주신 분께 감사의 말씀을 올립니다.***

## 필요한 것

- Windows 11 x64
- Little LUMI
- Steam 창작마당의 `LUMI Chat` — 필수 의존성
- Codex를 사용할 수 있는 ChatGPT 계정

OpenAI API 키, Python, 브라우저 확장 프로그램은 필요하지 않습니다. 대화는 일반 ChatGPT 웹 메시지가 아니라 로그인한 계정의 **Codex 사용량**을 쓰며, Free 계정도 제공되는 범위 안에서 사용할 수 있습니다.

## 설치와 사용

1. 창작마당에서 `LUMI Chat`을 구독하고 Little LUMI를 한 번 실행한 뒤 종료합니다.
2. GitHub 배포 ZIP을 풀고 `INSTALL.cmd`를 실행합니다.
3. `GPT 애드온만`, `GPT 애드온 + LUMI TTS`, 기존 설치에 `LUMI TTS만 추가` 중 하나를 고릅니다.
4. Little LUMI를 다시 실행하고 바탕화면의 `LUMI to GPT`를 엽니다.
5. `ChatGPT 계정 연결`을 누른 뒤 브라우저에 표시된 코드를 입력해 로그인합니다.
6. Little LUMI의 `루미 AI 설정 → 두뇌`에서 `ChatGPT (OAuth)`를 확인합니다.
7. 꼬미를 더블클릭하거나 우클릭 → `말 걸기`로 대화합니다.

기본 모델은 사용량이 가벼운 `GPT-5.6 Luna`, 추론 강도는 `낮음`입니다. 자율 혼잣말과 화면 구경은 새 설치에서 꺼진 상태로 시작하며, 필요할 때 `루미 AI 설정 → 자동 동작`에서 켤 수 있습니다.

현재 실행 파일은 코드 서명되지 않아 처음 실행할 때 Windows SmartScreen 경고가 표시될 수 있습니다. 배포자가 제공한 `SHA256SUMS.txt`와 파일 해시가 같은지 확인한 뒤 실행하세요.

## 휴대용 설치 모드

- `GPT Add-on only` — 애드온, Little LUMI 연동 패치, 공식 Codex App Server를 설치합니다.
- `GPT Add-on + LUMI GPT-SoVITS TTS` — 위 구성에 GPT-SoVITS 실행 환경과 LUMI 음성 가중치를 추가하고 `루미 AI 설정`까지 채웁니다.
- `Add LUMI GPT-SoVITS TTS to an existing install` — 1번으로 이미 설치한 애드온은 건드리지 않고 GPT-SoVITS와 LUMI 음성만 추가합니다.

설치 과정은 `%LOCALAPPDATA%\LumiToGPT\logs`에 단계별 로그를 남깁니다. 실패하면 콘솔에 실패 단계, 오류 종류, 발생 위치와 정확한 로그 파일 경로가 표시됩니다.

설치기는 OpenAI의 공식 Codex App Server Windows x64 압축본 약 75MB를 내려받아 해시를 확인합니다. TTS 모드는 추가로 약 5.7GB인 [공식 GPT-SoVITS v2 Windows 통합판](https://huggingface.co/lj1995/GPT-SoVITS-windows-package/blob/main/GPT-SoVITS-v2-240821.7z)과 배포 허가를 받은 약 420MB의 `GPT_weights_v2.7z`를 내려받습니다. 음성 가중치는 v0.9.0 GitHub Release의 별도 자산으로 유지되며 자세한 범위는 [VOICE_MODEL_NOTICE.txt](VOICE_MODEL_NOTICE.txt)를 따릅니다.

ChatGPT OAuth 정보와 애드온 설정은 `%LOCALAPPDATA%\LumiToGPT`에 분리해 저장합니다. ChatGPT 페이지의 DOM이나 비공개 웹 API는 사용하지 않습니다.

## 역할 분담

LUMI Chat이 그대로 담당하는 기능:

- 더블클릭·우클릭 대화 입력창과 말풍선
- 대화 기억과 캐릭터별 페르소나
- 자율 혼잣말과 화면 구경
- Fish Audio/ElevenLabs TTS, 캐릭터별 목소리와 입 모양
- 집중 모드 중 음성 자동 차단

LUMI to GPT가 추가하는 기능:

- LUMI Chat의 OpenAI 호환 요청을 공식 Codex App Server로 전달
- ChatGPT 장치 코드 OAuth 로그인과 자동 토큰 갱신
- 실행할 때 GitHub 최신 Release를 확인하고 새 버전을 UI에서 제안
- `GPT-5.6 Luna` 저사용량 기본값
- 답변 전체를 LUMI Chat의 원래 응답 경로에 반환
- 말풍선 표시 시간 6~60초 적용
- 로컬 GPT-SoVITS 선행 준비, 자동 절전·초절전과 집중 모드 즉시 종료
- Python 없는 Codex 앱·CLI 작업 완료 알림 MCP

AI·목소리·페르소나 설정은 모두 Little LUMI의 기존 `루미 AI 설정`에서 관리합니다. LUMI to GPT 창은 계정 연결과 업데이트 확인만 다루며 별도 대화 입력창은 없습니다. 업데이트는 자동 설치하지 않고 사용자가 `새 버전 받기`를 누를 때 공식 GitHub Release 페이지를 엽니다.

## 개발 및 검증

```powershell
cargo test --manifest-path .\src-tauri\Cargo.toml
.\build.ps1
python .\tests\smoke_release.py
```

소스 코드는 BSD 3-Clause로 공개합니다. Little LUMI, Shimeji-ee와 Codex 구성요소의 원저작권·라이선스는 [NOTICE.txt](NOTICE.txt)에 따릅니다.

배포 ZIP과 창작마당 업로드 폴더는 각각 `release\LUMI-to-GPT-v1.0.0-windows-x64.zip`, `release\workshop-content`에 생성됩니다. Steam 항목의 필요 항목에는 `LUMI Chat` Workshop ID `3794360578`을 지정해야 합니다.
