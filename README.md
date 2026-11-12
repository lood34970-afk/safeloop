# SafeLoop — Pleos 차량용 앱 공모전

현대자동차그룹 차량용 앱 개발 공모전용 Android Automotive 프로젝트입니다. SafeLoop는 출발 전·주행 중·도착 후의 안전 습관을 차량 데이터로 설명하고, 다음 이동에서 고칠 행동 하나를 제안하는 전 주기 운전 코치입니다. Kotlin, Compose, `minSdk 28`, Gradle 8.10.2, Pleos Vehicle SDK 2.3.2와 NaviHelper 3.0.5를 사용합니다.

## 시연 영상

Pleos Connect AVD v2.1에서 release APK를 실행하고 Virtual Console로 차량 신호(도어, 기어, 차속, 방향지시등, 제동)를 바꿔 가며 녹화했습니다. 탑승 브리핑 → 주행 상태 → 도착 확인 → 상세 리포트 순서입니다. (2분 2초, 자막 포함)

▶ [시연 영상 보기 (mp4, 2.3MB)](https://github.com/lood34970-afk/safeloop/releases/download/v1.0/SafeLoop_demo.mp4) · [Release v1.0: APK와 영상](https://github.com/lood34970-afk/safeloop/releases/tag/v1.0)

<!-- ▼ 여기에 YouTube 링크: - YouTube: https://youtu.be/... -->

## 현재 산출물

- 여러 공모전 아이디어의 공통 구조와 현황: [ideas/README.md](ideas/README.md)
- 공모전 평가 기준과 아이디어 발상 기준: [docs/BRAINSTORMING_GUIDE.md](docs/BRAINSTORMING_GUIDE.md)
- 선정 아이디어와 제안서 작성 계획: [docs/PROPOSAL_PLAN.md](docs/PROPOSAL_PLAN.md)
- 공식 양식 기반 제안서 원고: [docs/PROPOSAL_CONTENT_V1.md](docs/PROPOSAL_CONTENT_V1.md)
- 사용자 인터뷰 질문지와 판정 기준: [docs/INTERVIEW_GUIDE.md](docs/INTERVIEW_GUIDE.md)
- AVD 기술 검증 결과: [docs/TECH_SPIKE_RESULTS.md](docs/TECH_SPIKE_RESULTS.md)
- 실행 가능한 MVP와 실제·시연 데이터 구분: [docs/MVP_STATUS.md](docs/MVP_STATUS.md)
- 동작 프로토타입 APK: `app/build/outputs/apk/debug/app-debug.apk`

AVD에서 운전석 안전벨트·도어·창문, 브레이크, 방향지시등, 크루즈 capability와 현재 상태, AAOS 차속·기어·주차브레이크, NaviHelper 서비스 연결을 확인했습니다. 현재 앱은 이 신호를 실시간 구독하고 값이 바뀔 때만 타임스탬프·출처와 함께 최근 50건을 기록합니다. 상세 리포트는 실제 사건과 점수를 표시합니다. 지원 신호가 없거나 제한속도 표본이 없으면 임의 점수를 만들지 않고 미평가 사유를 보여 줍니다. 전체 점수는 출발 25%·주행 50%·도착 25%이며 세 영역이 모두 평가 가능할 때만 표시합니다. 주차브레이크가 해제된 상태에서는 Safe Close를 완료하지 않습니다. ADAS는 문서와 AVD 서비스까지 확인했지만 공개 Maven 아티팩트가 없어 SDK 배포 경로 확인이 남아 있습니다.

## 빠른 시작

```powershell
./scripts/doctor.ps1
./gradlew.bat assembleDebug
./scripts/test-score-engine.ps1
```

Android Studio에서 이 폴더를 열고 Pleos Connect Emulator를 실행한 뒤 `app` 구성을 Run 하면 됩니다. Pleos 에뮬레이터 등록과 계정 작업은 [docs/SETUP.md](docs/SETUP.md)를 참고하세요.

에뮬레이터 최초 실행 시 화면의 USB 디버깅을 한 번 허용한 뒤 아래 명령으로 빌드·설치·실행할 수 있습니다.

```powershell
./scripts/run-pleos.ps1
```

## 다음 작업

1. 정상 경로 생성 환경에서 NaviHelper 제한속도·TBT 실경로 이벤트와 Tire·Safety·Light capability 추가 검증
2. TBT와 방향지시등 선행 시간 점수 규칙 구현
3. 주행 세션 경계, 로컬 리포트 저장과 앱 재실행 후 복원 구현
4. ADAS SDK 배포 좌표·권한을 운영사에 확인하고 LaneChange/Planning 이벤트 연결
5. 사용자 인터뷰를 마무리하고 참가자 정보·서명 입력 후 제출용 release APK와 시연 자료 준비

