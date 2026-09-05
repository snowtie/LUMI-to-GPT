# LUMI to GPT v1.0.7

## 주요 변경 사항

- TTS HTTP 실패 때 GPT-SoVITS의 실제 오류 응답과 파일 상태를 `tts-last-error.log`에 자동 기록
- 미리듣기 오류에 자동 생성된 상세 진단 로그 경로 표시
- 존재하지 않는 Steam 드라이브를 검색할 때 설치가 중단되는 문제 수정
- 배포 폴더에서 업데이트해도 실행 중인 이전 창을 종료하고 정식 설치 폴더를 갱신
- 자동 업데이트 때 바탕화면 바로가기를 다시 만들고 최신 앱 아이콘으로 셸 캐시 갱신
- 배포 폴더의 EXE를 직접 실행해도 기존 설치 폴더의 Codex App Server를 자동으로 검색
- 계정 및 업데이트 창의 `지금 업데이트` 버튼으로 최신 Release를 자동 검증·설치하고 재실행
- TTS 설치 중 출력이 잠시 멈춰도 완료 안내까지 CMD 창을 닫지 않도록 대기 문구 보강
- 화면 구경 이미지를 Codex가 실제로 읽을 수 있는 임시 로컬 이미지 입력으로 전달하고 응답 후 즉시 삭제
- 재설치나 TTS 추가 시 실행 중인 기존 애드온을 대상 경로 기준으로 안전하게 종료한 뒤 업데이트
- ChatGPT 웹 DOM 자동화를 제거하고 OpenAI 공식 `Codex App Server`와 장치 코드 OAuth로 전환
- API 키 없이 ChatGPT 계정으로 로그인하고 Codex 사용량으로 대화
- 기본 모델을 저사용량 `GPT-5.6 Luna`, 추론 강도를 `낮음`으로 지정
- 새 설치에서 자율 혼잣말과 화면 구경을 기본 비활성화
- 계정 연결만 담당하는 작은 로컬 창으로 UI 단순화
- 계정과 업데이트 상태를 한눈에 보는 UI 및 GitHub 새 버전 자동 확인 추가
- 새 LUMI Chat Addon 로고를 앱·트레이 아이콘, UI와 창작마당 미리보기에 적용
- Windows PowerShell 5.1에서 한글 설치 스크립트가 깨지던 UTF-8 BOM 문제 수정
- 설치 시 공식 Codex App Server v0.153.4 Windows x64 파일을 다운로드하고 SHA-256 검증
- 기존 애드온에 GPT-SoVITS와 LUMI 음성만 추가하는 설치 선택지 추가
- 설치 실패 단계, 오류 종류, 위치와 자식 프로세스 출력을 `%LOCALAPPDATA%\LumiToGPT\logs`에 기록

## 유지되는 기능

- Little LUMI의 기존 대화창·기억·페르소나·말풍선 경로
- 긴 답변 전체 반환과 말풍선 표시 시간 6~60초
- 로컬 GPT-SoVITS 자동 실행, 자동 절전·초절전, 집중 모드 즉시 무음
- 음성 미리듣기와 대화 음성의 단일 재생 경로
- 업데이트 후 GPT-SoVITS 설정 복구
- Codex 앱·CLI 작업 완료 알림용 Rust MCP

## 설치 전 확인

- Windows 11 x64용 배포본입니다.
- Little LUMI와 LUMI Chat이 필요합니다.
- 기존 사용자는 Little LUMI를 종료한 뒤 `INSTALL.cmd`를 다시 실행하세요.
- 대화는 로그인한 계정의 Codex 사용량과 한도를 따릅니다.
- 실행 파일은 코드 서명되지 않아 Windows SmartScreen 경고가 표시될 수 있습니다.
- TTS 자동 설치 시 약 5.7GB의 GPT-SoVITS 통합판과 약 420MB의 LUMI 음성 가중치를 추가로 내려받습니다.
