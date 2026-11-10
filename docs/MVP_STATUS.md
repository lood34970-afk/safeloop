# SafeLoop MVP 구현 상태

> 검증일: 2026-10-30  
> 환경: Pleos Connect AVD, debug APK

## 구현된 화면 흐름

1. **탑승 브리핑**
   - 출발 전에 확인할 안전 상태와 이어갈 행동 하나를 약 10초 분량으로 표시
   - 운전석 안전벨트와 NaviHelper 연결 상태를 실제 SDK 결과로 표시
2. **주행 상태**
   - 주행 중 조작을 요구하지 않는 축소 화면
   - 방향지시등과 크루즈 상태를 실제 Vehicle SDK 결과로 표시
   - 값이 달라질 때만 타임스탬프 기반 실신호 이벤트로 기록
3. **도착 확인**
   - 창문·도어·P단·주차브레이크를 실제 차량 신호로 확인하는 Safe Close 흐름
   - 네 항목이 모두 안전 상태일 때만 리포트 이동을 허용
4. **상세 리포트**
   - 브리핑과 분리된 점수, 사건 근거, 미평가 구간, 다음 행동 화면
   - 최근 Vehicle SDK·AAOS·NaviHelper 이벤트의 발생 시각과 출처 표시
   - 다음 탑승 브리핑에 행동 하나를 연결

## 실제 데이터와 시연 데이터

| 항목 | 현재 상태 |
|---|---|
| 운전석 안전벨트 | Vehicle Seat API 실신호 |
| 운전석 도어 | Vehicle Door API 실신호 |
| 운전석 창문 | Vehicle Window API 실신호 |
| 브레이크 페달 | Vehicle Brake API 실신호 |
| 방향지시등 | Vehicle TurnSignal API 실신호 |
| 크루즈 상태 | Vehicle DrivingMode API 실신호 |
| 내비게이션 연결 | NaviHelper 3.0.5 실연결 |
| 차량 속도 | AAOS `PERF_VEHICLE_SPEED` 실신호 |
| 기어 | AAOS `GEAR_SELECTION` 실신호 |
| 주차브레이크 | AAOS `PARKING_BRAKE_ON` 실신호 |
| 현재 도로 제한속도 | NaviHelper 실경로 listener 연결 완료, AVD 경로 이벤트 대기 |
| 다음 경로 안내 | NaviHelper TBT listener 연결 완료, AVD 경로 이벤트 대기 |
| 사건 타임라인 | listener 기반 실신호 이벤트, 최근 50건 메모리 저장 |
| 출발·도착 점수 | 현재 지원되는 실신호의 통과 비율로 계산 |
| 주행 점수 | 제한속도와 차속 표본이 있을 때 준수율 계산, 크루즈 활성 표본 제외 |
| 전체 점수 | 출발 25%·주행 50%·도착 25%, 세 영역이 모두 평가 가능할 때만 표시 |
| ADAS 차선 변경 | SDK 배포 좌표·권한 확인 후 연결 예정 |

고정된 시연 점수는 제거했다. 실제로 확인할 수 없거나 신뢰도가 부족한 항목은 감점하지 않고 미평가로 처리하며, 한 영역이라도 미평가이면 전체 점수를 만들지 않는다.

AAOS 차량 속도 권한은 런타임 권한이므로 앱 시작 시 `android.car.permission.CAR_SPEED`를 요청한다. Pleos AVD 자동 검증에서는 사용자 10에 해당 권한을 부여한 뒤 실신호 수신을 확인했다.

## 검증 결과

- `:app:assembleDebug` 빌드 성공
- Pleos AVD 설치와 실행 성공
- 탑승 브리핑 → 주행 상태 → 도착 확인 → 상세 리포트 전환 성공
- Door·Window·Brake·속도·기어·주차브레이크 실신호 수신 성공
- 안전벨트·Door·Window·Brake·TurnSignal·Cruise Vehicle 리스너와 AAOS 속성 콜백 등록 성공
- 동일 값 중복 제거와 최근 50건 인메모리 이벤트 저장, 상세 리포트 표시 성공
- AVD 시작 시 실신호 이벤트 10건 기록 확인
- 고정 제한속도 시연 값을 제거하고 실제 NaviHelper 이벤트가 없으면 `이벤트 대기`로 표시
- `TripScoreEngineTest` 3건 통과: 경로 미수신 미평가, 크루즈 표본 제외, 전체 가중 점수
- AVD 실측 결과: 안전벨트 미착용·도어 닫힘으로 출발 50점, P단·주차브레이크 해제·창문/도어 닫힘으로 도착 75점
- AVD 내비 앱은 재시도 후 `INITIALIZING`·`TBT 안내 없음`을 전달했으나 `서비스 연결 중`에서 멈춰 실경로 생성은 확인하지 못함
- 주차브레이크가 해제된 상태에서는 도착 확인을 완료하지 않는 안전 조건 확인
- 앱 실행 중 치명적 예외 없음
- 2560×1440 AVD 전체 화면에서 잘림 없이 표시됨

## 화면 캡처

- [탑승 브리핑](assets/safeloop-mvp-briefing.png)
- [실신호가 반영된 탑승 브리핑](assets/safeloop-mvp-briefing-live.png)
- [주행 상태](assets/safeloop-mvp-driving.png)
- [실시간 사건·NaviHelper 대기가 반영된 주행 상태](assets/safeloop-mvp-driving-live.png)
- [도착 확인](assets/safeloop-mvp-close.png)
- [실신호가 반영된 도착 확인](assets/safeloop-mvp-close-live.png)
- [상세 리포트](assets/safeloop-mvp-report.png)
- [실신호 이벤트가 반영된 상세 리포트](assets/safeloop-mvp-live-events.png)
- [실신호 점수와 미평가 사유가 반영된 상세 리포트](assets/safeloop-mvp-real-score.png)

## 다음 구현 우선순위

1. 정상 경로 생성이 가능한 AVD/실차 환경에서 NaviHelper 제한속도와 TBT 사건을 수집한다.
2. TBT와 방향지시등의 선행 시간 규칙을 점수 엔진에 추가한다.
3. 주행 세션 경계를 정의하고 완료된 리포트를 로컬 저장소에 보관한다.
4. Virtual Console에서 값 변경 시나리오와 평가·미평가 규칙을 자동 검증한다.
5. ADAS SDK를 확보하면 차선 변경과 보조 기능 개입 이벤트를 같은 사건 모델에 연결한다.
