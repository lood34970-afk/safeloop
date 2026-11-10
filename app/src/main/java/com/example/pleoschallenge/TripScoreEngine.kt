package com.example.pleoschallenge

import kotlin.math.roundToInt

data class CategoryScore(
    val score: Int?,
    val evaluatedCount: Int,
    val passedCount: Int,
    val excludedCount: Int,
    val reason: String,
)

data class TripScoreSummary(
    val overallScore: Int?,
    val start: CategoryScore,
    val driving: CategoryScore,
    val close: CategoryScore,
) {
    val excludedCount: Int
        get() = start.excludedCount + driving.excludedCount + close.excludedCount
}

object TripScoreEngine {
    private const val SPEED_TOLERANCE_KMH = 3f

    fun evaluate(
        results: List<ProbeResult>,
        events: List<SignalEvent>,
    ): TripScoreSummary {
        val latest = results.associateBy { it.id }
        val start = evaluateChecks(
            checks = listOf(
                latest["seatbelt"] to { detail -> detail.isSeatbeltBuckled() },
                latest["door"] to { detail -> detail.contains("닫힘") },
            ),
            completeReason = "안전벨트·도어 실신호 기준",
        )
        val close = evaluateChecks(
            checks = listOf(
                latest["gear"] to { detail -> detail.contains("GEAR_PARK") },
                latest["parking_brake"] to { detail -> detail.contains("체결") },
                latest["window"] to { detail -> detail.contains("닫힘") },
                latest["door"] to { detail -> detail.contains("닫힘") },
            ),
            completeReason = "P단·주차브레이크·창문·도어 실신호 기준",
        )
        val driving = evaluateDriving(events)
        val overall = if (start.score != null && driving.score != null && close.score != null) {
            (start.score * 0.25f + driving.score * 0.5f + close.score * 0.25f).roundToInt()
        } else {
            null
        }
        return TripScoreSummary(overall, start, driving, close)
    }

    private fun evaluateChecks(
        checks: List<Pair<ProbeResult?, (String) -> Boolean>>,
        completeReason: String,
    ): CategoryScore {
        val evaluable = checks.filter { (result, _) -> result?.status == ProbeStatus.SUPPORTED }
        val passed = evaluable.count { (result, rule) -> rule(result!!.detail) }
        val excluded = checks.size - evaluable.size
        val score = if (evaluable.isEmpty()) null else (passed * 100f / evaluable.size).roundToInt()
        val reason = when {
            evaluable.isEmpty() -> "지원되는 차량 신호가 없어 미평가"
            excluded > 0 -> "$completeReason · 미지원 ${excluded}건 제외"
            else -> completeReason
        }
        return CategoryScore(score, evaluable.size, passed, excluded, reason)
    }

    private fun evaluateDriving(events: List<SignalEvent>): CategoryScore {
        var speedLimit: Float? = null
        var cruiseActive = false
        var evaluated = 0
        var compliant = 0
        var excluded = 0

        events.sortedBy { it.sequence }.forEach { event ->
            when (event.signalId) {
                "speed_limit" -> speedLimit = event.value.kmhOrNull()?.takeIf { it > 0f }
                "cruise" -> cruiseActive = event.value.isCruiseActive()
                "speed" -> {
                    val speed = event.value.kmhOrNull() ?: return@forEach
                    val limit = speedLimit ?: return@forEach
                    if (cruiseActive) {
                        excluded += 1
                    } else {
                        evaluated += 1
                        if (speed <= limit + SPEED_TOLERANCE_KMH) compliant += 1
                    }
                }
            }
        }

        val score = if (evaluated == 0) null else (compliant * 100f / evaluated).roundToInt()
        val reason = when {
            speedLimit == null -> "NaviHelper 제한속도 이벤트가 없어 미평가"
            evaluated == 0 && excluded > 0 -> "크루즈 활성 표본 ${excluded}건 제외 · 운전자 표본 대기"
            evaluated == 0 -> "제한속도 수신 완료 · 차속 변화 표본 대기"
            excluded > 0 -> "제한속도 +3 km/h 이내 준수 · 크루즈 ${excluded}건 제외"
            else -> "제한속도 +3 km/h 이내 준수율"
        }
        return CategoryScore(score, evaluated, compliant, excluded, reason)
    }

    private fun String.isSeatbeltBuckled(): Boolean =
        contains("착용") && !contains("미착용")

    private fun String.isCruiseActive(): Boolean =
        contains("ACTIVATED") || contains("USER_OVERRIDE")

    private fun String.kmhOrNull(): Float? =
        KMH_PATTERN.find(this)?.groupValues?.getOrNull(1)?.toFloatOrNull()

    private val KMH_PATTERN = Regex("(-?\\d+(?:\\.\\d+)?)\\s*km/h")
}
