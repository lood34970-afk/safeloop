package com.example.pleoschallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripScoreEngineTest {
    @Test
    fun `경로 데이터가 없으면 주행과 전체 점수를 만들지 않는다`() {
        val summary = TripScoreEngine.evaluate(
            results = currentResults(
                seatbelt = "READ_WRITE · 현재 미착용",
                parkingBrake = "실시간 · 현재 해제",
            ),
            events = emptyList(),
        )

        assertEquals(50, summary.start.score)
        assertEquals(75, summary.close.score)
        assertNull(summary.driving.score)
        assertNull(summary.overallScore)
        assertEquals("NaviHelper 제한속도 이벤트가 없어 미평가", summary.driving.reason)
    }

    @Test
    fun `제한속도 표본은 크루즈 활성 구간을 제외한다`() {
        val events = listOf(
            event(1, "speed_limit", "60 km/h"),
            event(2, "speed", "55 km/h"),
            event(3, "speed", "65 km/h"),
            event(4, "cruise", "ACTIVATED"),
            event(5, "speed", "70 km/h"),
        )

        val driving = TripScoreEngine.evaluate(currentResults(), events).driving

        assertEquals(50, driving.score)
        assertEquals(2, driving.evaluatedCount)
        assertEquals(1, driving.passedCount)
        assertEquals(1, driving.excludedCount)
    }

    @Test
    fun `모든 단계가 평가 가능하면 가중 전체 점수를 계산한다`() {
        val events = listOf(
            event(1, "speed_limit", "60 km/h"),
            event(2, "speed", "60 km/h"),
        )

        val summary = TripScoreEngine.evaluate(currentResults(), events)

        assertEquals(100, summary.start.score)
        assertEquals(100, summary.driving.score)
        assertEquals(100, summary.close.score)
        assertEquals(100, summary.overallScore)
    }

    private fun currentResults(
        seatbelt: String = "READ_WRITE · 현재 착용",
        parkingBrake: String = "실시간 · 현재 체결",
    ): List<ProbeResult> = listOf(
        result("seatbelt", seatbelt),
        result("door", "READ_WRITE · 현재 닫힘 · 위치 0%"),
        result("gear", "실시간 · 현재 GEAR_PARK"),
        result("parking_brake", parkingBrake),
        result("window", "READ_WRITE · 현재 닫힘 · 위치 0%"),
    )

    private fun result(id: String, detail: String) = ProbeResult(
        id = id,
        title = id,
        status = ProbeStatus.SUPPORTED,
        detail = detail,
    )

    private fun event(sequence: Long, id: String, value: String) = SignalEvent(
        sequence = sequence,
        timestampMillis = sequence,
        signalId = id,
        title = id,
        value = value,
        detail = "실시간 · 현재 $value",
        source = "test",
    )
}
