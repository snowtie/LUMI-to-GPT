# Steam 창작마당에서 다른 모드의 확장 기능을 배포할 때

확인일: 2026-09-04

> 이 문서는 현재 공개된 Steam 약관과 Little LUMI 설치본의 공식 안내를 바탕으로 한 배포 판단 자료이며, 법률 자문은 아닙니다.

## 결론

다른 모드를 필요 항목으로만 지정하고, 그 모드의 파일을 한 바이트도 포함하지 않는 독립 애드온은 Steam의 일반 구조상 가능합니다. Steamworks는 창작마당 항목 사이의 의존성을 공식 지원합니다. 다만 그 의존성은 웹에 표시되는 소프트 의존성이므로, 실제 연동 방식은 게임과 원본 모드가 지원해야 합니다.

반대로 다음 방식은 원작자의 명시적 허락이나 적용 가능한 라이선스 없이 올리면 안 됩니다.

- 원본 모드의 코드, 이미지, 음성, 설정 또는 바이너리를 복사해 포함
- 원본 모드를 수정한 전체 파일이나 패치된 실행 파일을 재업로드
- 원본 모드의 이름과 이미지를 써서 공식 후속작이나 공식 확장처럼 표시

출처를 적는 것만으로 재배포 권한이 생기지는 않습니다. Steam 업로더는 자신이 올리는 모든 자료에 충분한 권리가 있으며, 직접 만들었거나 모든 공동 기여자를 대신해 제출할 권리가 있다고 보증해야 합니다. [Steam Subscriber Agreement §6.D](https://store.steampowered.com/subscriber_agreement/#6)

## 배포 방식별 판단

| 방식 | 판단 | 이유 |
|---|---|---|
| 원본 파일을 그대로 포함 | 허락 없이는 불가 | 업로드한 모든 자료에 대한 권리가 필요함 |
| 원본 파일을 수정해 통째로 포함 | 허락이나 해당 라이선스 필요 | 수정했어도 원본 저작물이 포함됨 |
| 내 파일만 올리고 원본을 필요 항목으로 지정 | 일반적으로 가능 | Steamworks가 창작마당 항목 의존성을 지원함 |
| 원본 모드가 제공한 공개 API·파일 규격만 사용 | 비교적 안전 | 원본 자체를 배포하지 않고 호환 계층만 제공함 |
| 원본 모드 내부 구현을 덮어쓰거나 비공개 파일에 의존 | 사전 협의 권장 | 업데이트 파손, 약관·라이선스 및 지원 오인 위험이 큼 |
| 별도 EXE·설치 프로그램을 창작마당에 포함 | Little LUMI에서는 피해야 함 | 공식 창작마당의 공개 용도 및 앱의 모드 보호 범위와 맞지 않음 |

Steamworks의 `AddDependency`는 다른 창작마당 항목을 필요 항목으로 연결하지만, 컬렉션이 아닌 경우에는 웹과 API에 표시되는 소프트 의존성입니다. Steam이 원본을 합치거나 확장 호환성을 보장하는 기능은 아닙니다. [ISteamUGC::AddDependency](https://partner.steamgames.com/doc/api/ISteamUGC#AddDependency)

## Little LUMI에 적용하면

Little LUMI 창작마당은 현재 `LUMI Modules`이며, 공식 설명은 커뮤니티가 만든 캐릭터를 구독하여 데스크톱에 추가하는 공간이라고 안내합니다. 창작마당 기능도 구독 즉시 앱에서 사용하는 `Ready-To-Use Items`로 설정되어 있습니다. [Little LUMI 창작마당](https://steamcommunity.com/app/5075020/workshop/), [창작마당 소개](https://steamcommunity.com/workshop/about/?appid=5075020)

설치본의 공식 `mods\Mods Guide.txt`를 함께 보면 허용된 모딩 표면이 더 명확합니다.

- `mods\<이름>\` 아래의 파일을 `app\` 위에 덮어쓰는 데이터 모드 구조
- 캐릭터와 언어 파일 등이 대표 예시
- `Shimeji-ee.jar`, `jre\`, `lib\`, `steam_api64.dll` 등 프로그램 자체는 교체 거부
- Electron이 실행하는 정원 스크립트도 교체 거부
- 사용자 설정, AI 설정, 단축키 목록과 누적 데이터도 교체 거부
- 창작마당 항목은 사용자가 직접 허용한 프로그램 실행 목록을 수정할 수 없음

따라서 `LUMI to GPT` 같은 별도 Rust EXE는 Little LUMI의 일반 캐릭터 모듈이나 데이터 모드로 보기는 어렵습니다. Steam의 공통 약관에서 모든 EXE를 일괄 금지한다고 확인되지는 않았지만, Little LUMI가 공개한 창작마당 용도와 앱의 보호 규칙에는 맞지 않습니다. 창작마당에서 EXE를 내려받아 자동 실행하거나 설치 폴더를 변경하게 만드는 방식은 피하는 편이 안전합니다.

Little LUMI 설치본에는 기능 잠금 해제용 창작마당 항목을 구독하면 앱 자체가 `workshop-<번호>` 표식을 만드는 구조도 설명되어 있습니다. 이 경우 창작마당 파일을 실행하는 것이 아니라, 해당 기능의 코드는 본체에 미리 포함되어 있고 구독 여부만 확인합니다. 제삼자가 독립 EXE를 배포하는 것과는 다른 구조이며, 같은 방식을 쓰려면 STUDIO LUMI가 본체 쪽 연동을 추가해야 합니다.

## 저작권과 표시

Steam 커뮤니티 지침은 권리가 없는 콘텐츠의 업로드를 금지하고, 게시물이 해당 게임 공간에 맞아야 한다고 요구합니다. [Steam Community Rules and Guidelines](https://help.steampowered.com/en/faqs/view/6862-8119-C23E-EA7B)

Little LUMI 설치본의 `LICENSE.txt`에는 구성요소별 조건도 따로 적혀 있습니다.

- 루미 캐릭터 그림, 스프라이트와 페르소나 텍스트는 STUDIO LUMI의 별도 서면 허락 없이는 배포본 밖으로 추출·수정·재배포할 수 없음
- STUDIO LUMI가 추가한 비캐릭터 코드와 문서는 New BSD 조건으로 재배포·수정 가능
- 그 코드를 재배포할 때는 저작권 고지와 면책 조항을 유지해야 하고, STUDIO LUMI가 제품을 보증하는 것처럼 이름을 사용할 수 없음

현재 `LUMI to GPT`에 공식 페르소나 전문이나 공식 캐릭터 자산을 내장하여 함께 배포한다면 이 조건에 걸릴 수 있습니다. 가장 단순한 안전책은 해당 자료를 배포본에 넣지 않고, 사용자가 설치한 Little LUMI의 파일을 실행 시 읽게 하거나 사용자가 직접 입력하도록 하는 것입니다. 공식 자산을 패키지에 포함하려면 STUDIO LUMI의 서면 허락을 받는 편이 맞습니다.

## 현재 프로젝트에 권장하는 배포 형태

1. `LUMI to GPT`를 `LUMI Chat 확장`이 아니라 `Little LUMI용 독립 동반 앱`으로 표시합니다.
2. LUMI Chat의 파일, 코드, 설정과 자산은 포함하지 않습니다.
3. LUMI Chat 구독을 요구하지 않고 Little LUMI 본체만 대상으로 합니다.
4. 공식 페르소나·스프라이트는 패키지에 복사하지 않고 설치본에서 읽거나 사용자 입력으로 대체합니다.
5. Rust EXE는 창작마당 모듈에 숨겨 넣지 말고, 우선 GitHub Releases 같은 별도 배포 경로를 사용합니다.
6. 창작마당에 반드시 올리려면 STUDIO LUMI에 `외부 동반 앱/실행 파일 허용 여부`, `허용 파일 형식`, `공식 자산 사용 범위`를 먼저 서면 확인합니다.
7. 원본 모드에 의존하는 애드온으로 전환한다면 원작자의 허락을 받고, 필요 항목과 원작자 링크, 비공식 확장임을 설명 첫머리에 명시합니다.

Steam은 게임마다 허용 항목과 제출 규칙이 다르므로 해당 제품의 지침을 확인하라고 안내합니다. [Steam Workshop 제출 안내](https://steamcommunity.com/workshop/workshopsubmitinfo/) 또한 Valve와 해당 개발자는 창작마당 항목을 제한하거나 제거할 수 있습니다. [Steam Subscriber Agreement §6.B](https://store.steampowered.com/subscriber_agreement/#6)

## 확인이 필요한 현재 상태

2026-09-04 현재 공개 페이지에서 `LUMI Chat`과 `LUMI Voice Pack`에는 Steam 커뮤니티 지침 위반으로 제거되었다는 경고가 표시됩니다. [LUMI Chat](https://steamcommunity.com/sharedfiles/filedetails/?id=3794360578), [LUMI Voice Pack](https://steamcommunity.com/sharedfiles/filedetails/?id=3795005580)

Steam은 제거 사유의 세부 항목을 공개 페이지에 표시하지 않으므로, 이것이 EXE·파일 형식·권리 문제 때문이라고 단정할 수는 없습니다. 다만 같은 유형의 항목을 새로 올리기 전에 STUDIO LUMI와 Steam Support에 허용 형식을 확인해야 한다는 강한 신호입니다.

## 공식 근거

- [Steam Subscriber Agreement](https://store.steampowered.com/subscriber_agreement/)
- [Steam Community Rules and Guidelines](https://help.steampowered.com/en/faqs/view/6862-8119-C23E-EA7B)
- [Steam Workshop 제출 안내](https://steamcommunity.com/workshop/workshopsubmitinfo/)
- [Steamworks Workshop 구현 안내](https://partner.steamgames.com/doc/features/workshop/implementation)
- [Steamworks ISteamUGC API](https://partner.steamgames.com/doc/api/ISteamUGC)
- [Little LUMI 창작마당](https://steamcommunity.com/app/5075020/workshop/)
- [Little LUMI Steam 상점 페이지](https://store.steampowered.com/app/5075020/)
- 설치본 공식 문서: `D:\Steam\steamapps\common\Little LUMI\mods\Mods Guide.txt`
- 설치본 공식 문서: `D:\Steam\steamapps\common\Little LUMI\LICENSE.txt`
- 설치본 공식 문서: `D:\Steam\steamapps\common\Little LUMI\User Guide (ko).txt`
