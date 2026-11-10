# SafeLoop 핵심 API 기술 검증 결과

> 최초 검증일: 2026-10-28 (KST)  
> 추가 검증일: 2026-10-30 (KST)
> 환경: Pleos Connect v2.1 AVD, Vehicle SDK 2.3.2, NaviHelper 3.0.5
> 앱: `com.example.pleoschallenge`

## 결론

안전벨트, 운전석 도어·창문, 브레이크 페달, 방향지시등, 크루즈 상태와 AAOS 차속·기어·주차브레이크를 실제 SDK 코드로 빌드하고 Pleos AVD에서 capability·현재 상태 응답을 받았다. NaviHelper 서비스 연결도 확인했다. 차선 변경·ADAS는 공식 API 문서와 AVD 서비스는 확인했지만, 앱이 컴파일할 수 있는 ADAS SDK 아티팩트를 공개 Maven 저장소에서 찾지 못해 추가 확인이 필요하다.

| 검증 항목 | 사용 API/권한 | AVD 실측 결과 | 결정 |
|---|---|---|---|
| 운전석 안전벨트 | Vehicle `Seat.checkSeatBeltBuckledCapabilities`, `getCurrentSeatBeltBuckledState`, `CAR_SEATS` | `READ_WRITE`, 현재 `미착용` | Safe Start 핵심 기능에 포함 |
| 운전석 도어 | Vehicle `Door.checkDoorPositionCapabilities`, `getCurrentDoorPosition`, `CAR_DOORS` | `READ_WRITE`, 현재 `닫힘`, 위치 `0%` | Safe Start와 Safe Close에 포함 |
| 운전석 창문 | Vehicle `Window.checkWindowPositionCapabilities`, `getCurrentWindowPosition`, `CAR_WINDOWS` | `READ_WRITE`, 현재 `닫힘`, 위치 `0%` | Safe Close에 포함 |
| 브레이크 페달 | Vehicle `Brake.checkBrakePedalStatusCapabilities`, `getCurrentBrakePedalStatus`, `CAR_DRIVE` | `READ`, 현재 `해제` | 주행·도착 사건의 보조 신호로 포함 |
| 방향지시등 | Vehicle `TurnSignal.checkTurnSignalStateCapabilities`, `getCurrentTurnSignalState`, `CAR_LIGHTS` | `READ`, 현재 `OFF` | 실제 이벤트 구독 및 차선/분기 사건 결합 진행 |
| 크루즈 상태 | Vehicle `DrivingMode.checkCruiseControlStateCapabilities`, `getCruiseControlState`, `CAR_DRIVE` | `READ`, 현재 `ENABLED` | 평가 제외 구간 판별에 포함 |
| 차량 속도 | AAOS `CarPropertyManager`, `PERF_VEHICLE_SPEED`, `CAR_SPEED` | 현재 `0 km/h` | Safe Drive 실시간 표시에 포함 |
| 기어 | AAOS `CarPropertyManager`, `GEAR_SELECTION`, `CAR_POWERTRAIN` | 현재 `GEAR_PARK` | Safe Close 완료 조건에 포함 |
| 주차브레이크 | AAOS `CarPropertyManager`, `PARKING_BRAKE_ON`, `CAR_POWERTRAIN` | 현재 `해제` | 해제 상태에서 완료를 차단하는 조건으로 포함 |
| 제한속도·TBT | NaviHelper 3.0.5 초기화 및 이벤트 리스너, `NAVI_ROUTE` | 서비스 3.0.4에 연결 성공, 이벤트 대기 | 경로를 시작한 상태에서 제한속도·TBT 실데이터 추가 검증 |
| 차선 변경·ADAS | 문서상 `LaneChangeStatus`, Planning/Warning 이벤트 | AVD에 `ai.pleos.playground.service.adas` 존재. 공개 Maven의 `Adas`/`ADAS`/`adas` 좌표는 모두 404 | SDK 신청/배포 좌표를 주최 측에 문의. 확보 전에는 `추가 확인`으로 표시 |

## 재현 증거

- 진단 코드: `app/src/main/java/com/example/pleoschallenge/PleosCapabilityProbe.kt`
- 진단 화면: `app/src/main/java/com/example/pleoschallenge/MainActivity.kt`
- 디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`
- 화면 캡처: [safeloop-capability-spike.png](assets/safeloop-capability-spike.png)

실측 로그:

```text
seatbelt | SUPPORTED | READ_WRITE · 현재 미착용
door | SUPPORTED | READ_WRITE · 현재 닫힘 · 위치 0%
window | SUPPORTED | READ_WRITE · 현재 닫힘 · 위치 0%
brake | SUPPORTED | READ · 현재 해제
turn_signal | SUPPORTED | READ · 현재 OFF
cruise | SUPPORTED | READ · 현재 ENABLED
speed | SUPPORTED | 현재 0 km/h
gear | SUPPORTED | 현재 GEAR_PARK
parking_brake | SUPPORTED | 현재 해제
navigation | SUPPORTED | NaviHelper 3.0.5 서비스 연결 성공 · 이벤트 대기
adas | PARTIAL | 공식 API 문서는 있으나 공개 Maven 아티팩트 미확인
```

## 발견한 구현 주의사항

1. Vehicle SDK는 `initialize()` 직후 요청을 큐에 보관했다가 서비스 연결 후 capability 콜백을 전달한다. 화면은 `확인 중` 상태를 유지해야 한다.
2. NaviHelper의 `getTBTInfo()`는 `pleos.car.permission.NAVI_ROUTE`가 없으면 `SecurityException`이 발생한다. 비동기 연결 콜백 내부 호출도 별도 예외 처리해야 한다.
3. Vehicle SDK 2.3.2 Kotlin 코드에서는 `vehicle.getSeat()`, `getTurnSignal()`, `getDrivingMode()`처럼 명시적 getter를 사용해야 공개 API에 접근된다.
4. `CruiseControl` 최신 상태값은 `ENABLED`, `ACTIVATED`, `USER_OVERRIDE`, `SUSPENDED`, `FORCED_DEACTIVATION_WARNING`, `OTHER`다. 평가 제외 규칙은 상태별 의미를 나눠야 한다.
5. capability가 지원돼도 현재 값이 `null` 또는 일시적 오류일 수 있으므로 해당 사건은 감점하지 않는다.
6. AAOS 차속은 런타임 `android.car.permission.CAR_SPEED` 승인이 필요하다. 앱 시작 시 권한을 요청하고 거부된 경우 차속 항목만 미평가해야 한다.
7. Pleos AVD 앱은 사용자 10에서 실행되므로 ADB 자동 검증 시에도 해당 사용자에 권한을 부여해야 한다.
8. 여러 capability 콜백이 동시에 도착할 수 있으므로 Vehicle 리스너 등록은 동기화해 한 번만 수행해야 한다.
9. AAOS 차속은 정지 중에도 같은 값이 반복 수신될 수 있으므로 상태 갱신과 사건 저장 전에 동일 값을 제거해야 한다.
10. Pleos AVD 내비 앱은 `차량 정보 확인 실패` 후 재시도 시 NaviHelper `INITIALIZING`과 빈 TBT를 전달했지만 `서비스 연결 중`에서 멈췄다. 실경로 제한속도 검증에는 정상 경로 생성이 가능한 AVD 설정 또는 실차 환경이 필요하다.

## 다음 기술 작업

- [x] 방향지시등·안전벨트·도어·창문·브레이크·크루즈를 Vehicle 리스너로, 차속·기어·주차브레이크를 AAOS 콜백으로 구독한다.
- [x] 동일 값 중복을 제거하고 Vehicle SDK·AAOS·NaviHelper 사건을 타임스탬프와 출처가 있는 공통 모델로 저장한다.
- [ ] 경로 안내를 시작하고 `onCurrentRoadSpeedLimit`, `onTBTInfo`, `onNavigationStatus` 이벤트를 수집한다.
- [x] 검증된 AAOS 차속·기어·주차브레이크를 타임스탬프 기반 이벤트 모델에 결합한다.
- [x] 실신호 기반 출발·주행·도착 점수와 미평가 규칙을 구현하고 단위 테스트 3건을 통과한다.
- [ ] ADAS SDK의 공식 배포 좌표·사용 신청·권한을 공모전 운영사 또는 Pleos 문서 문의 채널로 확인한다.
- [ ] ADAS를 확보하면 `LaneChangeState.CHANGING/CHANGED`와 방향지시등 시간차를 검증한다.
- [ ] 지원·권한·데이터 신뢰도에 따른 `평가/미평가/오류` 규칙을 단위 테스트로 고정한다.
