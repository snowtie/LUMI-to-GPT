# LUMI to GPT v0.8.3

## 주요 기능

- 로그인된 ChatGPT 웹 계정을 LUMI Chat의 `GPT Web` 프로바이더로 연결
- Little LUMI의 기존 대화창·기억·페르소나·말풍선 경로 유지
- Little LUMI의 `루미 AI 설정`에 `GPT Web`과 `GPT-SoVITS` 추가
- 로컬 GPT-SoVITS 자동 실행, 자동 절전·초절전, 집중 모드 즉시 무음
- Codex 앱·CLI 작업 완료 알림용 Rust MCP 포함
- 휴대용 설치기에서 `GPT 애드온만`과 `GPT 애드온 + 로컬 TTS` 선택 가능

## 이번 배포에서 확인한 수정

- 최신 LUMI Chat 기준으로 Little LUMI 연동 패치를 다시 생성
- 긴 답변이 중간에 잘리지 않고 완성된 본문으로 전달되도록 보정
- 말풍선 표시 시간을 최소 6초, 최대 60초로 적용
- 음성 미리듣기와 실제 대화 음성이 중복 재생되지 않도록 단일 재생 경로 사용
- `%LOCALAPPDATA%\LumiToGPT\settings.json`에 GPT-SoVITS 설정을 보관하고 업데이트 후 복구
- 설치 시 현재 원본 JAR을 새 백업으로 보존하고, 호환되지 않는 LUMI Chat 버전에는 적용을 중단

## 설치 전 확인

- Windows 11 x64용 배포본입니다.
- Little LUMI와 LUMI Chat이 필요합니다.
- 기존 사용자는 Little LUMI를 종료한 뒤 `INSTALL.cmd`를 다시 한 번 실행하면 됩니다.
- ChatGPT 웹 사용량과 이용 조건은 로그인한 계정 기준으로 적용됩니다.
- 현재 실행 파일은 코드 서명되지 않아 Windows SmartScreen 경고가 표시될 수 있습니다.
- TTS 자동 설치에는 약 5.7GB의 공식 GPT-SoVITS 다운로드와 사용 권한이 있는 `GPT_weights_v2.7z`가 필요합니다.
