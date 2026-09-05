# Little LUMI integration patch

이 패치는 현재 Little LUMI Model의 `Shimeji-ee.jar`에 아래 연결점만 추가합니다.

- 두뇌 프로바이더 `ChatGPT (OAuth)`와 `GPT-5.6 Luna` 기본값
- 목소리 프로바이더 `GPT-SoVITS`와 로컬 모델 설정 필드
- 원본 `ChatService -> SpeechDirector -> TtsPlayer` 단일 출력 경로
- 일반 말풍선 표시 시간 6~60초

설치기는 원본 JAR 전체를 배포하지 않고 이 폴더의 작은 클래스 패치만 적용합니다. 적용 전 `Shimeji-ee.jar.lumi-to-gpt.bak`을 만들며, 확인된 원본 클래스 해시가 다르면 패치를 거부합니다.
