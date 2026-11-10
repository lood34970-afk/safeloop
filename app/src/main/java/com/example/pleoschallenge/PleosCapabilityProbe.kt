package com.example.pleoschallenge

import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WindowPositionListener
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs

class PleosCapabilityProbe(
    context: Context,
    private val onUpdate: (List<ProbeResult>) -> Unit,
    private val onEventsUpdate: (List<SignalEvent>) -> Unit,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val signalExecutor = Executors.newSingleThreadExecutor()
    private val vehicle = Vehicle(appContext)
    private val naviHelper = NaviHelper(appContext)
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)
    private val driverDoor = DoorArea(DoorValue.ROW_1_LEFT)
    private val driverWindow = WindowArea(WindowValue.ROW_1_LEFT)
    private var aaosCar: Car? = null
    private var carPropertyManager: CarPropertyManager? = null
    private var initialized = false
    private var naviListenerRegistered = false
    private var vehicleListenersRegistered = false
    private var aaosListenersRegistered = false
    private val signalEvents = mutableListOf<SignalEvent>()
    private val lastRecordedDetails = mutableMapOf<String, String>()
    private val lastQueuedStates = ConcurrentHashMap<String, String>()
    private var eventSequence = 0L

    private val results = linkedMapOf(
        "seatbelt" to ProbeResult("seatbelt", "운전석 안전벨트", ProbeStatus.CHECKING, "Vehicle Seat capability 확인 대기"),
        "door" to ProbeResult("door", "운전석 도어", ProbeStatus.CHECKING, "Vehicle Door capability 확인 대기"),
        "window" to ProbeResult("window", "운전석 창문", ProbeStatus.CHECKING, "Vehicle Window capability 확인 대기"),
        "gear" to ProbeResult("gear", "기어", ProbeStatus.CHECKING, "AAOS CarPropertyManager 연결 대기"),
        "brake" to ProbeResult("brake", "브레이크 페달", ProbeStatus.CHECKING, "Vehicle Brake capability 확인 대기"),
        "speed" to ProbeResult("speed", "차량 속도", ProbeStatus.CHECKING, "AAOS CarPropertyManager 연결 대기"),
        "parking_brake" to ProbeResult("parking_brake", "주차 브레이크", ProbeStatus.CHECKING, "AAOS CarPropertyManager 연결 대기"),
        "turn_signal" to ProbeResult("turn_signal", "방향지시등", ProbeStatus.CHECKING, "Vehicle TurnSignal capability 확인 대기"),
        "cruise" to ProbeResult("cruise", "크루즈·보조 구간", ProbeStatus.CHECKING, "Vehicle DrivingMode capability 확인 대기"),
        "navigation" to ProbeResult("navigation", "내비게이션 상태", ProbeStatus.CHECKING, "NaviHelper 연결 대기"),
        "speed_limit" to ProbeResult("speed_limit", "현재 도로 제한속도", ProbeStatus.CHECKING, "실경로 이벤트 대기"),
        "tbt" to ProbeResult("tbt", "다음 경로 안내", ProbeStatus.CHECKING, "실경로 이벤트 대기"),
        "adas" to ProbeResult("adas", "차선 변경·ADAS", ProbeStatus.CHECKING, "ADAS SDK 런타임 배포 여부 확인 대기"),
    )

    private val naviListener = object : NaviHelperEventListener {
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            if (speedLimit > 0) {
                update("speed_limit", ProbeStatus.SUPPORTED, "실시간 · 현재 $speedLimit km/h")
            } else {
                update("speed_limit", ProbeStatus.PARTIAL, "현재 도로의 제한속도 정보 없음 · 미평가")
            }
        }

        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            val next = tbtInfoList.firstOrNull()
            val detail = if (next == null) {
                "실시간 · 현재 안내 없음"
            } else {
                val description = next.description.ifBlank { next.type.name }
                "실시간 · ${String.format(Locale.US, "%.0f", next.distance)}m 후 $description"
            }
            update("tbt", ProbeStatus.SUPPORTED, detail)
        }

        override fun onNavigationStatus(status: NavigationStatus) {
            update(
                "navigation",
                ProbeStatus.SUPPORTED,
                "NaviHelper 연결됨 · 내비 상태 ${status.name}",
            )
        }
    }

    private val seatbeltListener = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) {
            update("seatbelt", ProbeStatus.SUPPORTED, "실시간 · 현재 ${if (buckled == true) "착용" else "미착용"}")
        }

        override fun onFailed(exception: Exception) {
            update("seatbelt", ProbeStatus.ERROR, "실시간 구독 실패: ${exception.shortMessage()}")
        }
    }

    private val doorPositionListener = object : DoorPositionListener {
        override fun onPositionUpdated(position: Int?) {
            val resolved = position ?: 0
            update("door", ProbeStatus.SUPPORTED, "실시간 · 현재 ${if (resolved <= 0) "닫힘" else "열림"} · 위치 $resolved%")
        }

        override fun onFailed(exception: Exception) {
            update("door", ProbeStatus.ERROR, "실시간 구독 실패: ${exception.shortMessage()}")
        }
    }

    private val windowPositionListener = object : WindowPositionListener {
        override fun onWindowPositionUpdated(position: Int?) {
            val resolved = position ?: 0
            update("window", ProbeStatus.SUPPORTED, "실시간 · 현재 ${if (resolved <= 0) "닫힘" else "열림"} · 위치 $resolved%")
        }

        override fun onFailed(exception: Exception) {
            update("window", ProbeStatus.ERROR, "실시간 구독 실패: ${exception.shortMessage()}")
        }
    }

    private val brakePedalListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            update("brake", ProbeStatus.SUPPORTED, "실시간 · 현재 ${if (pressed == true) "밟음" else "해제"}")
        }

        override fun onFailed(exception: Exception) {
            update("brake", ProbeStatus.ERROR, "실시간 구독 실패: ${exception.shortMessage()}")
        }
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: ai.pleos.playground.vehicle.constant.area.TurnSignalValue?) {
            update("turn_signal", ProbeStatus.SUPPORTED, "실시간 · 현재 ${value?.name ?: "UNKNOWN"}")
        }

        override fun onFailed(exception: Exception) {
            update("turn_signal", ProbeStatus.ERROR, "실시간 구독 실패: ${exception.shortMessage()}")
        }
    }

    private val cruiseControlListener = object : CruiseControlStateListener {
        override fun onCruiseControlStateUpdated(value: ai.pleos.playground.vehicle.constant.control.CruiseControl?) {
            update("cruise", ProbeStatus.SUPPORTED, "실시간 · 현재 ${value?.name ?: "UNKNOWN"}")
        }

        override fun onFailed(exception: Exception) {
            update("cruise", ProbeStatus.ERROR, "실시간 구독 실패: ${exception.shortMessage()}")
        }
    }

    private val aaosPropertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> updateSpeed(value.value as? Float)
                VehiclePropertyIds.GEAR_SELECTION -> updateGear(value.value as? Int)
                VehiclePropertyIds.PARKING_BRAKE_ON -> updateParkingBrake(value.value as? Boolean)
            }
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.w("SafeLoopProbe", "AAOS property error: property=$propertyId area=$areaId")
        }
    }

    fun start() {
        if (!initialized) {
            initialized = true
            probeAdasRuntime()
            signalExecutor.execute {
                try {
                    vehicle.initialize()
                } catch (error: Throwable) {
                    updateVehicleErrors("Vehicle 초기화 실패: ${error.shortMessage()}")
                }
                connectAaosCar()
                readAaosSignals()
                connectNaviHelper()
                runVehicleChecksInternal()
            }
        } else {
            runVehicleChecks()
        }
    }

    fun runVehicleChecks() {
        signalExecutor.execute { runVehicleChecksInternal() }
    }

    private fun runVehicleChecksInternal() {
        probeSeatbelt()
        probeDoor()
        probeWindow()
        probeBrakePedal()
        readAaosSignals()
        probeTurnSignal()
        probeCruiseControl()
        if (naviListenerRegistered) {
            runCatching { naviHelper.getTBTInfo() }
                .onFailure { update("navigation", ProbeStatus.ERROR, "TBT 요청 실패: ${it.shortMessage()}") }
        }
    }

    private fun probeDoor() {
        update("door", ProbeStatus.CHECKING, "CAR_DOORS 권한과 운전석 capability 확인 중")
        runCatching {
            vehicle.getDoor().checkDoorPositionCapabilities(
                { config, bounds ->
                    if (driverDoor !in bounds.keys) {
                        update("door", ProbeStatus.UNAVAILABLE, "운전석 도어 위치 센서 미지원 · 미평가")
                    } else {
                        registerVehicleListeners()
                        vehicle.getDoor().getCurrentDoorPosition(
                            driverDoor,
                            { position ->
                                val label = if ((position ?: 0) <= 0) "닫힘" else "열림"
                                update("door", ProbeStatus.SUPPORTED, "${config.accessType} · 현재 $label · 위치 ${position ?: 0}%")
                            },
                            { error -> update("door", ProbeStatus.ERROR, "상태 조회 실패: ${error.shortMessage()}") },
                        )
                    }
                },
                { error -> update("door", ProbeStatus.ERROR, "capability 확인 실패: ${error.shortMessage()}") },
            )
        }.onFailure { update("door", ProbeStatus.ERROR, "호출 실패: ${it.shortMessage()}") }
    }

    private fun probeWindow() {
        update("window", ProbeStatus.CHECKING, "CAR_WINDOWS 권한과 운전석 capability 확인 중")
        runCatching {
            vehicle.getWindow().checkWindowPositionCapabilities(
                { config, bounds ->
                    if (driverWindow !in bounds.keys) {
                        update("window", ProbeStatus.UNAVAILABLE, "운전석 창문 위치 센서 미지원 · 미평가")
                    } else {
                        registerVehicleListeners()
                        vehicle.getWindow().getCurrentWindowPosition(
                            driverWindow,
                            { position ->
                                val label = if ((position ?: 0) <= 0) "닫힘" else "열림"
                                update("window", ProbeStatus.SUPPORTED, "${config.accessType} · 현재 $label · 위치 ${position ?: 0}%")
                            },
                            { error -> update("window", ProbeStatus.ERROR, "상태 조회 실패: ${error.shortMessage()}") },
                        )
                    }
                },
                { error -> update("window", ProbeStatus.ERROR, "capability 확인 실패: ${error.shortMessage()}") },
            )
        }.onFailure { update("window", ProbeStatus.ERROR, "호출 실패: ${it.shortMessage()}") }
    }

    private fun probeBrakePedal() {
        update("brake", ProbeStatus.CHECKING, "CAR_DRIVE 권한과 브레이크 capability 확인 중")
        runCatching {
            vehicle.getBrake().checkBrakePedalStatusCapabilities(
                { config ->
                    registerVehicleListeners()
                    vehicle.getBrake().getCurrentBrakePedalStatus(
                        { pressed -> update("brake", ProbeStatus.SUPPORTED, "${config.accessType} · 현재 ${if (pressed == true) "밟음" else "해제"}") },
                        { error -> update("brake", ProbeStatus.ERROR, "상태 조회 실패: ${error.shortMessage()}") },
                    )
                },
                { error -> update("brake", ProbeStatus.ERROR, "capability 확인 실패: ${error.shortMessage()}") },
            )
        }.onFailure { update("brake", ProbeStatus.ERROR, "호출 실패: ${it.shortMessage()}") }
    }

    private fun connectAaosCar() {
        runCatching {
            aaosCar = Car.createCar(appContext)
            carPropertyManager = aaosCar?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                ?: error("CarPropertyManager를 찾을 수 없음")
            registerAaosListeners()
        }.onFailure {
            update("speed", ProbeStatus.ERROR, "AAOS 연결 실패: ${it.shortMessage()}")
            update("parking_brake", ProbeStatus.ERROR, "AAOS 연결 실패: ${it.shortMessage()}")
        }
    }

    private fun readAaosSignals() {
        val manager = carPropertyManager ?: return
        runCatching {
            val metersPerSecond = manager.getProperty(
                Float::class.javaObjectType,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                0,
            ).value ?: 0f
            updateSpeed(metersPerSecond)
        }.onFailure { update("speed", ProbeStatus.ERROR, "속도 조회 실패: ${it.shortMessage()}") }

        runCatching {
            val engaged = manager.getProperty(
                Boolean::class.javaObjectType,
                VehiclePropertyIds.PARKING_BRAKE_ON,
                0,
            ).value
            updateParkingBrake(engaged)
        }.onFailure { update("parking_brake", ProbeStatus.ERROR, "주차 브레이크 조회 실패: ${it.shortMessage()}") }

        runCatching {
            val gearValue = manager.getProperty(
                Int::class.javaObjectType,
                VehiclePropertyIds.GEAR_SELECTION,
                0,
            ).value
            updateGear(gearValue)
        }.onFailure { update("gear", ProbeStatus.ERROR, "기어 조회 실패: ${it.shortMessage()}") }
    }

    private fun probeSeatbelt() {
        update("seatbelt", ProbeStatus.CHECKING, "CAR_SEATS 권한과 운전석 capability 확인 중")
        runCatching {
            vehicle.getSeat().checkSeatBeltBuckledCapabilities(
                { config, areas ->
                    if (driverSeat !in areas) {
                        update("seatbelt", ProbeStatus.UNAVAILABLE, "운전석(ROW_1_LEFT) 안전벨트 센서 미지원 · 미평가")
                    } else {
                        registerVehicleListeners()
                        vehicle.getSeat().getCurrentSeatBeltBuckledState(
                            driverSeat,
                            { buckled ->
                                update(
                                    "seatbelt",
                                    ProbeStatus.SUPPORTED,
                                    "${config.accessType} · 현재 ${if (buckled == true) "착용" else "미착용"}",
                                )
                            },
                            { error -> update("seatbelt", ProbeStatus.ERROR, "상태 조회 실패: ${error.shortMessage()}") },
                        )
                    }
                },
                { error -> update("seatbelt", ProbeStatus.ERROR, "capability 확인 실패: ${error.shortMessage()}") },
            )
        }.onFailure { update("seatbelt", ProbeStatus.ERROR, "호출 실패: ${it.shortMessage()}") }
    }

    private fun probeTurnSignal() {
        update("turn_signal", ProbeStatus.CHECKING, "CAR_LIGHTS 권한과 capability 확인 중")
        runCatching {
            vehicle.getTurnSignal().checkTurnSignalStateCapabilities(
                { config ->
                    registerVehicleListeners()
                    vehicle.getTurnSignal().getCurrentTurnSignalState(
                        { value -> update("turn_signal", ProbeStatus.SUPPORTED, "${config.accessType} · 현재 ${value?.name ?: "UNKNOWN"}") },
                        { error -> update("turn_signal", ProbeStatus.ERROR, "상태 조회 실패: ${error.shortMessage()}") },
                    )
                },
                { error -> update("turn_signal", ProbeStatus.ERROR, "capability 확인 실패: ${error.shortMessage()}") },
            )
        }.onFailure { update("turn_signal", ProbeStatus.ERROR, "호출 실패: ${it.shortMessage()}") }
    }

    private fun probeCruiseControl() {
        update("cruise", ProbeStatus.CHECKING, "CAR_DRIVE 권한과 capability 확인 중")
        runCatching {
            vehicle.getDrivingMode().checkCruiseControlStateCapabilities(
                { config ->
                    registerVehicleListeners()
                    vehicle.getDrivingMode().getCruiseControlState(
                        { value -> update("cruise", ProbeStatus.SUPPORTED, "${config.accessType} · 현재 ${value?.name ?: "UNKNOWN"}") },
                        { error -> update("cruise", ProbeStatus.ERROR, "상태 조회 실패: ${error.shortMessage()}") },
                    )
                },
                { error -> update("cruise", ProbeStatus.ERROR, "capability 확인 실패: ${error.shortMessage()}") },
            )
        }.onFailure { update("cruise", ProbeStatus.ERROR, "호출 실패: ${it.shortMessage()}") }
    }

    private fun connectNaviHelper() {
        runCatching {
            naviHelper.initialize(
                {
                    signalExecutor.execute {
                        if (!naviListenerRegistered) {
                            naviHelper.addListener(naviListener)
                            naviListenerRegistered = true
                        }
                        update("navigation", ProbeStatus.SUPPORTED, "NaviHelper 3.0.5 서비스 연결 성공 · 이벤트 대기")
                        runCatching { naviHelper.getTBTInfo() }
                            .onFailure { update("navigation", ProbeStatus.ERROR, "TBT 요청 실패: ${it.shortMessage()}") }
                    }
                },
                { error -> update("navigation", ProbeStatus.ERROR, "NaviHelper 연결 실패: ${error.shortMessage()}") },
            )
        }.onFailure { update("navigation", ProbeStatus.ERROR, "NaviHelper 초기화 실패: ${it.shortMessage()}") }
    }

    private fun probeAdasRuntime() {
        val adasClass = runCatching { Class.forName("ai.pleos.playground.adas.Adas") }.getOrNull()
        if (adasClass == null) {
            update(
                "adas",
                ProbeStatus.PARTIAL,
                "공식 API 문서에는 LaneChange/Planning이 있으나 현재 공개 Maven 저장소에서 ADAS 아티팩트를 확인하지 못함",
            )
        } else {
            update("adas", ProbeStatus.SUPPORTED, "ADAS 런타임 ${adasClass.name} 확인 · 이벤트 구독 구현 가능")
        }
    }

    @Synchronized
    private fun registerVehicleListeners() {
        if (vehicleListenersRegistered) return
        runCatching {
            vehicle.getSeat().registerSeatBeltBuckledState(driverSeat, seatbeltListener)
            vehicle.getDoor().registerDoorPosition(driverDoor, doorPositionListener)
            vehicle.getWindow().registerWindowPosition(driverWindow, windowPositionListener)
            vehicle.getBrake().registerBrakePedalStatus(brakePedalListener)
            vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener)
            vehicle.getDrivingMode().registerCruiseControlState(cruiseControlListener)
            vehicleListenersRegistered = true
            Log.i("SafeLoopProbe", "Vehicle 실시간 리스너 등록 완료")
        }.onFailure {
            Log.w("SafeLoopProbe", "Vehicle 리스너 등록 실패", it)
        }
    }

    private fun unregisterVehicleListeners() {
        if (!vehicleListenersRegistered) return
        runCatching { vehicle.getSeat().unregisterSeatBeltBuckledState(driverSeat, seatbeltListener) }
        runCatching { vehicle.getDoor().unregisterDoorPosition(driverDoor, doorPositionListener) }
        runCatching { vehicle.getWindow().unregisterWindowPosition(driverWindow, windowPositionListener) }
        runCatching { vehicle.getBrake().unregisterBrakePedalStatus(brakePedalListener) }
        runCatching { vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener) }
        runCatching { vehicle.getDrivingMode().unregisterCruiseControlState(cruiseControlListener) }
        vehicleListenersRegistered = false
    }

    private fun registerAaosListeners() {
        if (aaosListenersRegistered) return
        val manager = carPropertyManager ?: return
        val propertyIds = listOf(
            VehiclePropertyIds.PERF_VEHICLE_SPEED,
            VehiclePropertyIds.GEAR_SELECTION,
            VehiclePropertyIds.PARKING_BRAKE_ON,
        )
        var registeredAny = false
        propertyIds.forEach { propertyId ->
            runCatching {
                registeredAny = manager.registerCallback(
                    aaosPropertyCallback,
                    propertyId,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE,
                ) || registeredAny
            }.onFailure {
                Log.w("SafeLoopProbe", "AAOS 리스너 등록 실패: property=$propertyId", it)
            }
        }
        aaosListenersRegistered = registeredAny
        if (registeredAny) Log.i("SafeLoopProbe", "AAOS 실시간 리스너 등록 완료")
    }

    private fun updateSpeed(metersPerSecond: Float?) {
        val kmPerHour = abs(metersPerSecond ?: 0f) * 3.6f
        update("speed", ProbeStatus.SUPPORTED, "실시간 · 현재 ${String.format(Locale.US, "%.0f", kmPerHour)} km/h")
    }

    private fun updateGear(gearValue: Int?) {
        update("gear", ProbeStatus.SUPPORTED, "실시간 · 현재 ${VehicleGear.toString(gearValue ?: VehicleGear.GEAR_UNKNOWN)}")
    }

    private fun updateParkingBrake(engaged: Boolean?) {
        update("parking_brake", ProbeStatus.SUPPORTED, "실시간 · 현재 ${if (engaged == true) "체결" else "해제"}")
    }

    private fun updateVehicleErrors(detail: String) {
        update("seatbelt", ProbeStatus.ERROR, detail)
        update("door", ProbeStatus.ERROR, detail)
        update("window", ProbeStatus.ERROR, detail)
        update("gear", ProbeStatus.ERROR, detail)
        update("brake", ProbeStatus.ERROR, detail)
        update("turn_signal", ProbeStatus.ERROR, detail)
        update("cruise", ProbeStatus.ERROR, detail)
    }

    private fun update(id: String, status: ProbeStatus, detail: String) {
        val stateKey = "$status|$detail"
        if (lastQueuedStates.put(id, stateKey) == stateKey) return
        Log.i("SafeLoopProbe", "$id | $status | $detail")
        mainHandler.post {
            val current = results.getValue(id)
            results[id] = current.copy(status = status, detail = detail)
            onUpdate(results.values.toList())
            val eventValue = detail.substringAfter("현재 ", detail)
            if (status == ProbeStatus.SUPPORTED && lastRecordedDetails[id] != eventValue) {
                lastRecordedDetails[id] = eventValue
                signalEvents += SignalEvent(
                    sequence = ++eventSequence,
                    timestampMillis = System.currentTimeMillis(),
                    signalId = id,
                    title = current.title,
                    value = eventValue,
                    detail = detail,
                    source = if (id in setOf("speed", "gear", "parking_brake")) {
                        "AAOS"
                    } else if (id in setOf("navigation", "speed_limit", "tbt")) {
                        "NaviHelper"
                    } else {
                        "Vehicle SDK"
                    },
                )
                if (signalEvents.size > 50) signalEvents.removeAt(0)
                onEventsUpdate(signalEvents.toList())
            }
        }
    }

    private fun Throwable.shortMessage(): String = message?.take(140) ?: javaClass.simpleName

    override fun close() {
        unregisterVehicleListeners()
        if (naviListenerRegistered) {
            runCatching { naviHelper.removeListener(naviListener) }
            naviListenerRegistered = false
        }
        runCatching { naviHelper.release() }
        runCatching { vehicle.release() }
        if (aaosListenersRegistered) runCatching { carPropertyManager?.unregisterCallback(aaosPropertyCallback) }
        aaosListenersRegistered = false
        runCatching { aaosCar?.disconnect() }
        signalExecutor.shutdownNow()
        aaosCar = null
        carPropertyManager = null
        initialized = false
    }
}
