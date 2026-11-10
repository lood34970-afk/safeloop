package com.example.pleoschallenge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AppBackground = Color(0xFF091119)
private val NavBackground = Color(0xFF0D1822)
private val Panel = Color(0xFF13212D)
private val PanelRaised = Color(0xFF192A38)
private val Mint = Color(0xFF58E1B2)
private val Blue = Color(0xFF78A9FF)
private val Amber = Color(0xFFFFC466)
private val TextPrimary = Color(0xFFF2F7FA)
private val TextSecondary = Color(0xFFA9BBC8)
private val Border = Color(0xFF29404F)

private enum class SafeLoopPage(val label: String, val marker: String) {
    BRIEFING("탑승 브리핑", "01"),
    DRIVING("주행 상태", "02"),
    CLOSE("도착 확인", "03"),
    REPORT("상세 리포트", "04"),
}

private enum class Tone {
    GOOD,
    WARNING,
    INFO,
    MUTED,
}

@Composable
fun SafeLoopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            secondary = Blue,
            background = AppBackground,
            surface = Panel,
            onPrimary = Color(0xFF052219),
            onBackground = TextPrimary,
            onSurface = TextPrimary,
        ),
        content = content,
    )
}

@Composable
fun SafeLoopApp() {
    val context = LocalContext.current
    var page by remember { mutableStateOf(SafeLoopPage.BRIEFING) }
    var results by remember { mutableStateOf(emptyList<ProbeResult>()) }
    var signalEvents by remember { mutableStateOf(emptyList<SignalEvent>()) }
    var windowClosed by remember { mutableStateOf(false) }
    var habitPinned by remember { mutableStateOf(false) }
    val probe = remember(context) {
        PleosCapabilityProbe(
            context = context.applicationContext,
            onUpdate = { results = it },
            onEventsUpdate = { signalEvents = it },
        )
    }

    DisposableEffect(probe) {
        probe.start()
        onDispose { probe.close() }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                selected = page,
                onSelected = { page = it },
                connectedCount = results.count { it.status == ProbeStatus.SUPPORTED },
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 30.dp, vertical = 24.dp),
            ) {
                when (page) {
                    SafeLoopPage.BRIEFING -> BriefingPage(
                        results = results,
                        onRefresh = probe::runVehicleChecks,
                        onStart = { page = SafeLoopPage.DRIVING },
                    )

                    SafeLoopPage.DRIVING -> DrivingPage(
                        results = results,
                        signalEvents = signalEvents,
                        onArrive = { page = SafeLoopPage.CLOSE },
                    )

                    SafeLoopPage.CLOSE -> ClosePage(
                        results = results,
                        windowClosed = windowClosed,
                        onWindowClosed = { windowClosed = true },
                        onRefresh = probe::runVehicleChecks,
                        onOpenReport = { page = SafeLoopPage.REPORT },
                    )

                    SafeLoopPage.REPORT -> ReportPage(
                        results = results,
                        signalEvents = signalEvents,
                        habitPinned = habitPinned,
                        onPinHabit = { habitPinned = true },
                        onNextBriefing = { page = SafeLoopPage.BRIEFING },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationRail(
    selected: SafeLoopPage,
    onSelected: (SafeLoopPage) -> Unit,
    connectedCount: Int,
) {
    Column(
        modifier = Modifier
            .width(228.dp)
            .fillMaxHeight()
            .background(NavBackground)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Mint),
                contentAlignment = Alignment.Center,
            ) {
                Text("S", color = Color(0xFF07251B), fontWeight = FontWeight.Black, fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SafeLoop", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("DRIVE BETTER", color = TextSecondary, fontSize = 10.sp, letterSpacing = 1.4.sp)
            }
        }

        Spacer(Modifier.height(40.dp))
        SafeLoopPage.entries.forEach { item ->
            NavigationItem(
                page = item,
                selected = selected == item,
                onClick = { onSelected(item) },
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Panel)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (connectedCount > 0) Mint else Amber))
                Spacer(Modifier.width(8.dp))
                Text("PLEOS 연결", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            Text("차량 신호 ${connectedCount}개 확인", color = TextSecondary, fontSize = 12.sp)
            Text("AVD 시연 모드", color = Blue, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NavigationItem(page: SafeLoopPage, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PanelRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            page.marker,
            color = if (selected) Mint else TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            page.label,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun BriefingPage(
    results: List<ProbeResult>,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
) {
    val seat = results.firstOrNull { it.id == "seatbelt" }
    val door = results.firstOrNull { it.id == "door" }
    val navigation = results.firstOrNull { it.id == "navigation" }
    val seatBuckled = seat?.detail?.contains("미착용") == false && seat.detail.contains("착용")
    val seatTone = when {
        seat == null || seat.status == ProbeStatus.CHECKING -> Tone.MUTED
        seat.status != ProbeStatus.SUPPORTED -> Tone.INFO
        seatBuckled -> Tone.GOOD
        else -> Tone.WARNING
    }
    val seatSummary = when {
        seat == null || seat.status == ProbeStatus.CHECKING -> "차량 신호 확인 중"
        seat.status != ProbeStatus.SUPPORTED -> "센서 미지원 · 점수에서 제외"
        seatBuckled -> "운전석 안전벨트 착용 완료"
        else -> "시동 전에 안전벨트를 착용하세요"
    }

    PageColumn {
        PageHeader(
            eyebrow = "SAFE START · 약 10초",
            title = "좋은 아침이에요. 출발 전에 확인할게요.",
            subtitle = "필요한 내용만 짧게 브리핑하고, 자세한 기록은 리포트로 분리합니다.",
            action = {
                OutlinedButton(onClick = onRefresh) { Text("차량 신호 다시 확인") }
            },
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CardPanel(modifier = Modifier.weight(1.08f).fillMaxHeight()) {
                SectionTitle("출발 상태", "차량에서 직접 확인한 항목")
                Spacer(Modifier.height(14.dp))
                StatusRow("안전벨트", seatSummary, seatTone)
                HorizontalDivider(color = Border)
                StatusRow(
                    "운전석 도어",
                    door?.detail ?: "Vehicle Door 신호 확인 중",
                    signalTone(door, positiveKeyword = "닫힘"),
                )
                HorizontalDivider(color = Border)
                StatusRow(
                    "목적지·경로",
                    navigation?.detail ?: "NaviHelper 연결 확인 중",
                    if (navigation?.status == ProbeStatus.SUPPORTED) Tone.GOOD else Tone.MUTED,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "확인할 수 없는 항목은 감점하지 않고 리포트에 미평가 사유를 남깁니다.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }

            CardPanel(
                modifier = Modifier.weight(0.92f).fillMaxHeight(),
                background = Color(0xFF173126),
            ) {
                Pill("오늘 이어갈 행동", Mint, Color(0xFF08271D))
                Spacer(Modifier.height(22.dp))
                Text("안전벨트부터,\n시동은 그다음", fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(
                    "인터뷰에서 실제로 확인된 출발 전 누락을 첫 브리핑 행동으로 설정했습니다.",
                    color = Color(0xFFB8D8C9),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF06251A)),
                ) {
                    Text("브리핑 확인하고 출발", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun DrivingPage(
    results: List<ProbeResult>,
    signalEvents: List<SignalEvent>,
    onArrive: () -> Unit,
) {
    val speed = results.firstOrNull { it.id == "speed" }
    val speedLimit = results.firstOrNull { it.id == "speed_limit" }
    val tbt = results.firstOrNull { it.id == "tbt" }
    val turnSignal = results.firstOrNull { it.id == "turn_signal" }
    val cruise = results.firstOrNull { it.id == "cruise" }
    val speedValue = speed?.detail
        ?.substringAfter("현재 ", missingDelimiterValue = "—")
        ?.substringBefore(" km/h")
        ?: "—"

    PageColumn {
        PageHeader(
            eyebrow = "SAFE DRIVE · 자동 기록",
            title = "주행 중에는 방해하지 않고 기록만 합니다.",
            subtitle = "긴 피드백과 조작은 숨기고, 운전에 필요한 상태만 크게 보여 줍니다.",
        )
        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CardPanel(modifier = Modifier.weight(1.25f).fillMaxHeight(), background = Color(0xFF10283A)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Pill(
                        if (speed?.status == ProbeStatus.SUPPORTED) "AAOS 실시간 속도" else "속도 신호 확인 중",
                        Blue,
                        Color(0xFF0A2032),
                    )
                    Text("08:35", color = TextSecondary, fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(speedValue, fontSize = 92.sp, lineHeight = 92.sp, fontWeight = FontWeight.Light)
                    Text(" km/h", color = TextSecondary, fontSize = 22.sp, modifier = Modifier.padding(bottom = 15.dp))
                }
                Text(
                    if (speedLimit?.status == ProbeStatus.SUPPORTED) {
                        speedLimit.detail.substringAfter("현재 ")
                    } else {
                        "현재 도로 제한속도 — · NaviHelper 이벤트 대기"
                    },
                    color = TextSecondary,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.weight(1f))
                Text("주행 중 조작할 항목이 없습니다", color = Mint, fontWeight = FontWeight.SemiBold)
            }

            Column(modifier = Modifier.weight(0.95f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactMetric(
                    title = "방향지시등",
                    value = turnSignal?.detail?.substringAfter("현재 ") ?: "신호 확인 중",
                    detail = "Vehicle SDK 실신호",
                    tone = if (turnSignal?.status == ProbeStatus.SUPPORTED) Tone.GOOD else Tone.MUTED,
                )
                CompactMetric(
                    title = "다음 경로 안내",
                    value = tbt?.detail?.substringAfter("실시간 · ") ?: "이벤트 대기",
                    detail = "NaviHelper TBT 실경로 이벤트",
                    tone = if (tbt?.status == ProbeStatus.SUPPORTED) Tone.INFO else Tone.MUTED,
                )
                CompactMetric(
                    title = "크루즈·보조 구간",
                    value = cruise?.detail?.substringAfter("현재 ") ?: "신호 확인 중",
                    detail = "활성 구간은 운전자 평가에서 제외",
                    tone = if (cruise?.status == ProbeStatus.SUPPORTED) Tone.INFO else Tone.MUTED,
                )
                CompactMetric(
                    title = "실시간 이벤트",
                    value = "${signalEvents.size}건 기록",
                    detail = "값이 달라질 때만 타임스탬프와 함께 저장",
                    tone = Tone.INFO,
                )
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onArrive, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("도착 흐름 시연")
                }
            }
        }
    }
}

@Composable
private fun ClosePage(
    results: List<ProbeResult>,
    windowClosed: Boolean,
    onWindowClosed: () -> Unit,
    onRefresh: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val gear = results.firstOrNull { it.id == "gear" }
    val parkingBrake = results.firstOrNull { it.id == "parking_brake" }
    val window = results.firstOrNull { it.id == "window" }
    val door = results.firstOrNull { it.id == "door" }
    val signalSaysWindowClosed = window?.status == ProbeStatus.SUPPORTED && window.detail.contains("닫힘")
    val resolvedWindowClosed = windowClosed || signalSaysWindowClosed
    val gearParked = gear?.status == ProbeStatus.SUPPORTED && gear.detail.contains("GEAR_PARK")
    val parkingBrakeEngaged = parkingBrake?.status == ProbeStatus.SUPPORTED && parkingBrake.detail.contains("체결")
    val doorClosed = door?.status == ProbeStatus.SUPPORTED && door.detail.contains("닫힘")
    val readyToLeave = gearParked && parkingBrakeEngaged && resolvedWindowClosed && doorClosed
    val attentionTitle = when {
        !gearParked -> "기어를 P단으로\n변경하세요"
        !parkingBrakeEngaged -> "주차 브레이크를\n체결하세요"
        !resolvedWindowClosed -> "운전석 창문을\n확인하세요"
        !doorClosed -> "운전석 도어를\n확인하세요"
        else -> "안심하고\n이동하세요"
    }

    PageColumn {
        PageHeader(
            eyebrow = "SAFE CLOSE · 정차 후",
            title = if (readyToLeave) "도착 확인을 마쳤어요." else "내리기 전에 한 가지만 확인하세요.",
            subtitle = "인터뷰에서 확인된 창문 누락을 도착 후 짧은 확인 흐름으로 연결했습니다.",
        )
        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CardPanel(modifier = Modifier.weight(1.15f).fillMaxHeight()) {
                SectionTitle("차량 종료 상태", "Vehicle SDK · AAOS 실신호")
                Spacer(Modifier.height(14.dp))
                StatusRow(
                    "기어",
                    gear?.detail ?: "Vehicle Gear 신호 확인 중",
                    signalTone(gear, positiveKeyword = "GEAR_PARK"),
                )
                HorizontalDivider(color = Border)
                StatusRow(
                    "주차 브레이크",
                    parkingBrake?.detail ?: "AAOS 신호 확인 중",
                    signalTone(parkingBrake, positiveKeyword = "체결"),
                )
                HorizontalDivider(color = Border)
                StatusRow(
                    "운전석 창문",
                    when {
                        windowClosed -> "운전자 확인 완료"
                        window != null -> window.detail
                        else -> "Vehicle Window 신호 확인 중"
                    },
                    if (resolvedWindowClosed) Tone.GOOD else signalTone(window, positiveKeyword = "닫힘"),
                )
                HorizontalDivider(color = Border)
                StatusRow(
                    "운전석 도어",
                    door?.detail ?: "Vehicle Door 신호 확인 중",
                    signalTone(door, positiveKeyword = "닫힘"),
                )
            }

            CardPanel(
                modifier = Modifier.weight(0.85f).fillMaxHeight(),
                background = if (readyToLeave) Color(0xFF173126) else Color(0xFF3A2918),
            ) {
                Pill(if (readyToLeave) "확인 완료" else "확인 필요", if (readyToLeave) Mint else Amber, Color(0xFF231706))
                Spacer(Modifier.height(20.dp))
                Text(
                    attentionTitle,
                    fontSize = 34.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (readyToLeave) "이번 도착 확인은 리포트에 기록됩니다." else "차량을 제어하지 않고 운전자가 확인할 수 있도록 알려 줍니다.",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = when {
                        readyToLeave -> onOpenReport
                        !resolvedWindowClosed -> onWindowClosed
                        else -> onRefresh
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (readyToLeave) Mint else Amber,
                        contentColor = Color(0xFF1F1607),
                    ),
                ) {
                    Text(
                        when {
                            readyToLeave -> "이번 주행 리포트 보기"
                            !resolvedWindowClosed -> "창문을 확인했어요"
                            else -> "차량 상태 다시 확인"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportPage(
    results: List<ProbeResult>,
    signalEvents: List<SignalEvent>,
    habitPinned: Boolean,
    onPinHabit: () -> Unit,
    onNextBriefing: () -> Unit,
) {
    val scoreSummary = remember(results, signalEvents) {
        TripScoreEngine.evaluate(results, signalEvents)
    }
    val pendingCategories = listOf(scoreSummary.start, scoreSummary.driving, scoreSummary.close)
        .count { it.score == null }
    PageColumn {
        PageHeader(
            eyebrow = "TRIP REPORT · 운전 종료 후",
            title = "이번 주행의 근거를 자세히 확인하세요.",
            subtitle = "브리핑과 분리된 화면입니다. 정차 상태에서만 상세 내용을 제공합니다.",
            action = { Pill("실신호 이벤트 ${signalEvents.size}건", Blue, Color(0xFF0A2032)) },
        )
        Spacer(Modifier.height(18.dp))

        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CardPanel(modifier = Modifier.weight(0.68f).fillMaxHeight(), background = Color(0xFF10283A)) {
                Text("이번 주행 점수", color = TextSecondary, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(scoreSummary.overallScore?.toString() ?: "—", fontSize = 70.sp, lineHeight = 76.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (scoreSummary.overallScore == null) " / 주행 평가 대기" else " / 100",
                        color = TextSecondary,
                        fontSize = if (scoreSummary.overallScore == null) 13.sp else 18.sp,
                        modifier = Modifier.padding(bottom = 11.dp),
                    )
                }
                Spacer(Modifier.height(18.dp))
                ScoreLine("출발 준비", scoreSummary.start.score, Amber)
                ScoreLine("주행 습관", scoreSummary.driving.score, Mint)
                ScoreLine("도착 확인", scoreSummary.close.score, Blue)
                Spacer(Modifier.weight(1f))
                Text(
                    if (pendingCategories > 0) "미평가 영역 ${pendingCategories}개" else "평가 제외 ${scoreSummary.excludedCount}건",
                    color = Amber,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(scoreSummary.driving.reason, color = TextSecondary, fontSize = 12.sp)
                Text("전체 점수: 출발 25% · 주행 50% · 도착 25%", color = TextSecondary, fontSize = 11.sp)
            }

            CardPanel(modifier = Modifier.weight(1.08f).fillMaxHeight()) {
                SectionTitle("실신호 사건", "값이 변경된 시각과 차량 데이터 근거")
                Spacer(Modifier.height(12.dp))
                if (signalEvents.isEmpty()) {
                    TimelineRow("--:--", "차량 신호 대기", "Vehicle SDK·AAOS 이벤트를 기다리고 있습니다.", Tone.MUTED)
                } else {
                    signalEvents.takeLast(3).forEach { event ->
                        TimelineRow(
                            time = formatEventTime(event.timestampMillis),
                            title = event.title,
                            detail = "${event.detail} · ${event.source}",
                            tone = eventTone(event),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("동일한 값은 중복 저장하지 않으며 최근 50건을 메모리에 유지합니다.", color = TextSecondary, fontSize = 12.sp)
            }

            CardPanel(modifier = Modifier.weight(0.86f).fillMaxHeight(), background = Color(0xFF173126)) {
                Pill("다음 탑승 브리핑", Mint, Color(0xFF08271D))
                Spacer(Modifier.height(18.dp))
                Text("안전벨트부터,\n시동은 그다음", fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("다음 탑승 시 10초 브리핑에서 다시 알려 드릴게요.", color = Color(0xFFB8D8C9), lineHeight = 21.sp)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = if (habitPinned) onNextBriefing else onPinHabit,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Color(0xFF06251A)),
                ) {
                    Text(if (habitPinned) "다음 탑승 화면 보기" else "다음 브리핑에 추가", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), content = content)
}

@Composable
private fun PageHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(eyebrow, color = Mint, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text(title, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (action != null) {
            Spacer(Modifier.width(20.dp))
            action()
        }
    }
}

@Composable
private fun CardPanel(
    modifier: Modifier = Modifier,
    background: Color = Panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = background),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), content = content)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Text(subtitle, color = TextSecondary, fontSize = 13.sp)
}

@Composable
private fun StatusRow(title: String, detail: String, tone: Tone) {
    val color = toneColor(tone)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(13.dp))
        Text(title, modifier = Modifier.weight(0.28f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(detail, modifier = Modifier.weight(0.72f), color = if (tone == Tone.MUTED) TextSecondary else TextPrimary, fontSize = 13.sp, maxLines = 2)
    }
}

@Composable
private fun CompactMetric(title: String, value: String, detail: String, tone: Tone) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(toneColor(tone)))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextSecondary, fontSize = 12.sp)
                Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(detail, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TimelineRow(time: String, title: String, detail: String, tone: Tone) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(time, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.width(48.dp))
        Box(Modifier.padding(top = 5.dp).size(9.dp).clip(CircleShape).background(toneColor(tone)))
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = TextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScoreLine(label: String, score: Int?, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), color = TextSecondary, fontSize = 13.sp)
        Box(
            modifier = Modifier
                .width(86.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(Border),
        ) {
            if (score != null) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(score / 100f).background(color))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(score?.toString() ?: "—", modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun Pill(text: String, background: Color, contentColor: Color) {
    Text(
        text,
        modifier = Modifier.clip(CircleShape).background(background).padding(horizontal = 12.dp, vertical = 6.dp),
        color = contentColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

private fun toneColor(tone: Tone): Color = when (tone) {
    Tone.GOOD -> Mint
    Tone.WARNING -> Amber
    Tone.INFO -> Blue
    Tone.MUTED -> TextSecondary
}

private fun signalTone(result: ProbeResult?, positiveKeyword: String): Tone = when {
    result == null || result.status == ProbeStatus.CHECKING -> Tone.MUTED
    result.status != ProbeStatus.SUPPORTED -> Tone.INFO
    result.detail.contains(positiveKeyword) -> Tone.GOOD
    else -> Tone.WARNING
}

private fun eventTone(event: SignalEvent): Tone = when {
    event.detail.contains("착용") && !event.detail.contains("미착용") -> Tone.GOOD
    event.detail.contains("닫힘") || event.detail.contains("체결") || event.detail.contains("GEAR_PARK") -> Tone.GOOD
    event.detail.contains("미착용") || event.detail.contains("열림") -> Tone.WARNING
    event.signalId == "parking_brake" && event.detail.contains("해제") -> Tone.WARNING
    else -> Tone.INFO
}

private fun formatEventTime(timestampMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(timestampMillis))

@Preview(widthDp = 1280, heightDp = 720)
@Composable
private fun SafeLoopPreview() {
    SafeLoopTheme {
        SafeLoopApp()
    }
}
