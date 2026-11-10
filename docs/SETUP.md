# Pleos Connect 개발환경 설정

## 기준 버전

- Android Studio: 최신 Stable
- JDK: 17
- Android SDK: API 35로 빌드, `minSdk 28`
- Gradle: 8.10.2
- Android Gradle Plugin: 8.8.2
- Pleos Vehicle SDK: 2.3.2
- Pleos Connect Emulator: 2.1.0, Android API 34 이미지

## Pleos 전용 에뮬레이터 등록

1. Android Studio에서 `Tools > SDK Manager > SDK Update Sites`로 이동합니다.
2. `+`를 누르고 다음 값을 입력합니다.
   - Name: `Pleos Connect System Image`
   - Windows/Linux URL: `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`
3. `SDK Platforms`에서 `Show Package Details`를 켭니다.
4. API 34 아래의 `Pleos Connect system Image`를 설치합니다.
5. Android Studio를 재시작합니다.
6. `Tools > Device Manager > Create device > Automotive`에서 `Pleos Connect v2.1.0`을 선택합니다.
7. RAM 4 GB, API 34, Boot option `Cold boot`로 생성합니다. Quick boot에서는 VHAL을 사용할 수 없습니다.

이 PC에는 `Pleos_Connect_v2_1` AVD가 위 설정으로 이미 생성되어 있습니다. 최초 부팅 화면에서 USB 디버깅을 한 번 허용해야 명령줄에서 APK를 설치할 수 있습니다.

## Pleos 계정에서 해야 할 일

Pleos Playground 로그인 후 `Workspace > My Project`에서 프로젝트를 생성합니다. 현재 문서 기준으로 Vehicle State와 NaviHelper SDK는 별도 신청 없이 사용할 수 있습니다. FusedLocation, TTS, STT, Vehicle Control은 아직 준비 중이므로 상태가 바뀐 뒤 신청해야 합니다.

## 빌드

```powershell
./scripts/doctor.ps1
./gradlew.bat assembleDebug
./scripts/run-pleos.ps1
```

디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다. 제출용 release APK는 최종 앱명과 팀명이 정해진 뒤 서명 구성을 추가하고 `apk/팀명_앱이름_release.apk` 형식으로 복사합니다.

이 PC에서는 Pleos Maven 서버의 인증서 폐기 목록(CRL) 조회가 차단되어 공식 SDK 3개를 SHA-256 검증 후 `vendor/maven`에 로컬 캐시했습니다. 이 폴더는 SDK 재배포를 피하기 위해 Git에서 제외됩니다. 다른 PC에서 공식 Maven 서버가 정상 연결되면 원격 저장소에서 그대로 받아 빌드됩니다.

## Virtual Console

Pleos 에뮬레이터를 부팅한 뒤 실행합니다.

```powershell
./scripts/open-virtual-console.ps1
```

## 공식 문서

- 공모전 개발 환경: <https://appdevchallenge.auto-hmg.io/guide/development>
- 빠른 시작: <https://document.pleos.ai/docs/pleos-only/getting-started>
- 에뮬레이터 설치: <https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator>
- SDK 개요: <https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/>

