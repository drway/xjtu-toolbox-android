package com.xjtu.toolbox.ui

import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.HorizontalDivider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjtu.toolbox.schedule.CourseItem
import com.xjtu.toolbox.util.XjtuTime

// ── 共享常量 ──────────────────────────────

val COURSE_COLORS = listOf(
    Color(0xFF1565C0), Color(0xFF2E7D32), Color(0xFFC62828), Color(0xFF6A1B9A),
    Color(0xFFEF6C00), Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF4527A0),
    Color(0xFF00695C), Color(0xFF283593), Color(0xFF558B2F), Color(0xFF8E24AA),
    Color(0xFFD84315),
)

val DAY_HEADERS = listOf("一", "二", "三", "四", "五", "六", "日")
const val MAX_SECTIONS = 11
private val SECTION_HEIGHT: Dp = 58.dp
private val LEFT_COL_WIDTH: Dp = 38.dp

fun courseColor(courseName: String, allNames: List<String>): Color {
    val index = allNames.distinct().sorted().indexOf(courseName)
    return if (index >= 0) COURSE_COLORS[index % COURSE_COLORS.size] else COURSE_COLORS[0]
}

// ── 通用课格接口 ─────────────────────────

interface ScheduleSlot {
    val slotName: String
    val slotLocation: String
    val slotDayOfWeek: Int
    val slotStartSection: Int
    val slotEndSection: Int
}

// ── 周选择器（左右箭头式）────────────────

@Composable
fun WeekSelector(currentWeek: Int, totalWeeks: Int, onWeekChange: (Int) -> Unit) {
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .pointerInput(currentWeek, totalWeeks) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffsetX < -80f && currentWeek < totalWeeks -> onWeekChange(currentWeek + 1)
                            dragOffsetX > 80f && currentWeek > 1 -> onWeekChange(currentWeek - 1)
                        }
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> dragOffsetX += dragAmount }
                )
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (currentWeek > 1) onWeekChange(currentWeek - 1) },
            enabled = currentWeek > 1
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一周",
                tint = if (currentWeek > 1) MiuixTheme.colorScheme.primary
                       else MiuixTheme.colorScheme.outline
            )
        }
        Text(
            "第 $currentWeek 周",
            style = MiuixTheme.textStyles.subtitle,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        IconButton(
            onClick = { if (currentWeek < totalWeeks) onWeekChange(currentWeek + 1) },
            enabled = currentWeek < totalWeeks
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一周",
                tint = if (currentWeek < totalWeeks) MiuixTheme.colorScheme.primary
                       else MiuixTheme.colorScheme.outline
            )
        }
    }
}

// ── 课表网格（绝对定位，完美对齐）────────

@Composable
fun ScheduleGrid(
    slots: List<ScheduleSlot>,
    allCourseNames: List<String>,
    showWeeks: Boolean = false,
    isCurrentWeek: Boolean = false,  // 是否显示当前时间线
    onSlotClick: (ScheduleSlot) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val isSummer = remember { XjtuTime.isSummerTime() }

    // 当前时间线位置计算（仅在当前周激活）
    val timeLineInfo = if (isCurrentWeek) {
        val now = java.time.LocalTime.now()
        val todayDow = java.time.LocalDate.now().dayOfWeek.value  // 1=Mon...7=Sun
        // 计算当前时间在网格中的 Y 比例
        // 找到当前时间处于哪两节课之间
        var yFraction: Float? = null
        for (section in 1..MAX_SECTIONS) {
            val ct = XjtuTime.getClassTime(section, isSummer) ?: continue
            if (now < ct.start) {
                // 在这节课之前
                if (section == 1) {
                    yFraction = 0f  // 在第1节之前
                } else {
                    val prevEnd = XjtuTime.getClassTime(section - 1, isSummer)?.end ?: ct.start
                    val gapSeconds = java.time.Duration.between(prevEnd, ct.start).seconds.toFloat()
                    val elapsed = java.time.Duration.between(prevEnd, now).seconds.toFloat()
                    yFraction = (section - 1).toFloat() + if (gapSeconds > 0) (elapsed / gapSeconds) * 0f else 0f
                    // 简化：课间时间线固定在上节课末尾
                    yFraction = (section - 1).toFloat()
                }
                break
            } else if (now >= ct.start && now <= ct.end) {
                // 在这节课内
                val durationSeconds = java.time.Duration.between(ct.start, ct.end).seconds.toFloat()
                val elapsed = java.time.Duration.between(ct.start, now).seconds.toFloat()
                yFraction = (section - 1).toFloat() + elapsed / durationSeconds
                break
            }
        }
        // 如果超过最后一节课
        if (yFraction == null) {
            val lastEnd = XjtuTime.getClassTime(MAX_SECTIONS, isSummer)?.end
            yFraction = if (lastEnd != null && now > lastEnd) MAX_SECTIONS.toFloat() else null
        }
        if (yFraction != null) Pair(todayDow, yFraction) else null
    } else null
    Column(
        Modifier
            .fillMaxSize()
            .overScrollVertical()
            .verticalScroll(scrollState)
    ) {
        // ── 星期头 ──
        Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            Box(Modifier.width(LEFT_COL_WIDTH), contentAlignment = Alignment.Center) {
                Text("", fontSize = 9.sp)
            }
            DAY_HEADERS.forEachIndexed { idx, day ->
                val todayDow = java.time.LocalDate.now().dayOfWeek.value
                val isToday = isCurrentWeek && (idx + 1) == todayDow
                Box(
                    Modifier
                        .weight(1f)
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        day, fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) MiuixTheme.colorScheme.primary
                               else MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // ── 网格主体：BoxWithConstraints 精确定位 ──
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(SECTION_HEIGHT * MAX_SECTIONS)
        ) {
            val dayWidth = (maxWidth - LEFT_COL_WIDTH) / 7

            // 背景层：节次编号 + 细线网格
            val gridLineColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.12f)
            Column(Modifier.fillMaxSize()) {
                for (section in 1..MAX_SECTIONS) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(SECTION_HEIGHT),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.width(LEFT_COL_WIDTH).fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$section", fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f)
                                )
                                val timeStr = XjtuTime.getClassTime(section, isSummer)?.start
                                    ?.let { "%d:%02d".format(it.hour, it.minute) } ?: ""
                                Text(
                                    timeStr, fontSize = 7.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
                                )
                            }
                        }
                        // 网格单元格用细边框代替棋盘色块
                        for (day in 1..7) {
                            Box(
                                Modifier
                                    .width(dayWidth)
                                    .fillMaxHeight()
                                    .drawBehind {
                                        // 底部水平线
                                        drawLine(gridLineColor, Offset(0f, size.height), Offset(size.width, size.height), 0.5f.dp.toPx())
                                        // 右侧垂直线
                                        drawLine(gridLineColor, Offset(size.width, 0f), Offset(size.width, size.height), 0.5f.dp.toPx())
                                    }
                            )
                        }
                    }
                }
            }

            // 前景层：课程卡片（冲突课程翻页显示）
            val conflictGroups = remember(slots) { buildConflictGroups(slots) }

            conflictGroups.forEach { group ->
                val topOffset = SECTION_HEIGHT * (group.startSection - 1)
                val span = group.endSection - group.startSection + 1
                val cellHeight = SECTION_HEIGHT * span
                val dayLeft = LEFT_COL_WIDTH + dayWidth * (group.dayOfWeek - 1)

                Box(
                    Modifier
                        .offset(x = dayLeft, y = topOffset)
                        .width(dayWidth)
                        .height(cellHeight)
                        .padding(1.dp)
                ) {
                    if (group.slots.size == 1) {
                        val slot = group.slots[0]
                        val slotSpan = (slot.slotEndSection - slot.slotStartSection + 1)
                            .coerceIn(1, MAX_SECTIONS - slot.slotStartSection + 1)
                        CourseCell(
                            name = slot.slotName,
                            location = slot.slotLocation,
                            weekInfo = formatWeekInfo(slot, showWeeks),
                            spanSections = slotSpan,
                            color = courseColor(slot.slotName, allCourseNames),
                            onClick = { onSlotClick(slot) }
                        )
                    } else {
                        FlippableCourseCell(
                            slots = group.slots,
                            groupStartSection = group.startSection,
                            groupSpan = span,
                            allCourseNames = allCourseNames,
                            showWeeks = showWeeks,
                            onSlotClick = onSlotClick
                        )
                    }
                }
            }

            // ── 当前时间线（横跨整行 + "现在"标签）──
            if (timeLineInfo != null) {
                val (todayDow, yFrac) = timeLineInfo
                val density = androidx.compose.ui.platform.LocalDensity.current
                val sectionHeightPx = with(density) { SECTION_HEIGHT.toPx() }
                val leftColPx = with(density) { LEFT_COL_WIDTH.toPx() }
                val dayWidthPx = with(density) { dayWidth.toPx() }
                val lineColor = Color(0xFFE53935)  // Material Red 600
                val yPos = sectionHeightPx * yFrac
                val timelineY = with(density) { (yPos / density.density).dp }

                // "现在" 标签
                Text(
                    "现在",
                    modifier = Modifier
                        .offset(x = 0.dp, y = timelineY - 14.dp)
                        .width(LEFT_COL_WIDTH),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = lineColor
                )

                // 时间线（虚线 + 实线 + 圆点）
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // 虚线横跨整行（淡色）
                            drawLine(
                                color = lineColor.copy(alpha = 0.4f),
                                start = Offset(leftColPx, yPos),
                                end = Offset(size.width, yPos),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(6.dp.toPx(), 3.dp.toPx())
                                )
                            )
                            // 今日列：加粗实线
                            val dayLeft = leftColPx + dayWidthPx * (todayDow - 1)
                            drawLine(
                                color = lineColor,
                                start = Offset(dayLeft, yPos),
                                end = Offset(dayLeft + dayWidthPx, yPos),
                                strokeWidth = 2.dp.toPx()
                            )
                            // 左侧圆点
                            drawCircle(
                                color = lineColor,
                                radius = 4.dp.toPx(),
                                center = Offset(dayLeft, yPos)
                            )
                        }
                )
            }
        }
    }
}

// ── 冲突分组 ──

private data class ConflictGroup(
    val slots: List<ScheduleSlot>,
    val dayOfWeek: Int,
    val startSection: Int,
    val endSection: Int
)

private fun buildConflictGroups(slots: List<ScheduleSlot>): List<ConflictGroup> {
    val validSlots = slots.filter { it.slotDayOfWeek in 1..7 && it.slotStartSection in 1..MAX_SECTIONS }
    val byDay = validSlots.groupBy { it.slotDayOfWeek }
    val groups = mutableListOf<ConflictGroup>()

    byDay.forEach { (day, daySlots) ->
        val n = daySlots.size
        val parent = IntArray(n) { it }
        fun find(x: Int): Int {
            var r = x; while (parent[r] != r) r = parent[r]
            var c = x; while (c != r) { val next = parent[c]; parent[c] = r; c = next }
            return r
        }
        fun union(a: Int, b: Int) { parent[find(a)] = find(b) }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val a = daySlots[i]; val b = daySlots[j]
                if (a.slotStartSection <= b.slotEndSection && b.slotStartSection <= a.slotEndSection) {
                    union(i, j)
                }
            }
        }

        daySlots.indices.groupBy { find(it) }.values.forEach { indices ->
            val groupSlots = indices.map { daySlots[it] }
            groups.add(ConflictGroup(
                slots = groupSlots,
                dayOfWeek = day,
                startSection = groupSlots.minOf { it.slotStartSection },
                endSection = groupSlots.maxOf { it.slotEndSection.coerceAtMost(MAX_SECTIONS) }
            ))
        }
    }
    return groups
}

private fun formatWeekInfo(slot: ScheduleSlot, showWeeks: Boolean): String {
    if (!showWeeks) return ""
    return (slot as? CourseItem)?.getWeeks()?.let { weeks ->
        if (weeks.isEmpty()) "" else {
            val sorted = weeks.sorted()
            val ranges = mutableListOf<String>()
            var s = sorted[0]; var e = sorted[0]
            for (i in 1 until sorted.size) {
                if (sorted[i] == e + 1) e = sorted[i]
                else { ranges.add(if (s == e) "$s" else "$s-$e"); s = sorted[i]; e = sorted[i] }
            }
            ranges.add(if (s == e) "$s" else "$s-$e")
            ranges.joinToString(",") + "周"
        }
    } ?: ""
}

// ── 冲突课程翻页卡片 ──

@Composable
private fun FlippableCourseCell(
    slots: List<ScheduleSlot>,
    groupStartSection: Int,
    groupSpan: Int,
    allCourseNames: List<String>,
    showWeeks: Boolean,
    onSlotClick: (ScheduleSlot) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { slots.size })

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val slot = slots[page]
            val slotSpan = (slot.slotEndSection - slot.slotStartSection + 1)
                .coerceIn(1, MAX_SECTIONS - slot.slotStartSection + 1)
            val relativeOffset = slot.slotStartSection - groupStartSection

            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(SECTION_HEIGHT * slotSpan)
                        .offset(y = SECTION_HEIGHT * relativeOffset)
                ) {
                    CourseCell(
                        name = slot.slotName,
                        location = slot.slotLocation,
                        weekInfo = formatWeekInfo(slot, showWeeks),
                        spanSections = slotSpan,
                        color = courseColor(slot.slotName, allCourseNames),
                        onClick = { onSlotClick(slot) }
                    )
                }
            }
        }

        // 翻页指示器
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp)
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                .padding(horizontal = 3.dp, vertical = 1.5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(slots.size) { index ->
                Box(
                    Modifier
                        .size(4.dp)
                        .background(
                            if (pagerState.currentPage == index) Color.White
                            else Color.White.copy(alpha = 0.4f),
                            CircleShape
                        )
                )
            }
        }
    }
}

// ── 课程卡片 ──

@Composable
fun CourseCell(
    name: String,
    location: String,
    weekInfo: String = "",
    spanSections: Int,
    color: Color,
    onClick: () -> Unit = {}
) {
    val textColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier
            .fillMaxSize(),
        onClick = onClick,
        cornerRadius = 12.dp,
        pressFeedbackType = top.yukonga.miuix.kmp.utils.PressFeedbackType.Sink,
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(color = color.copy(alpha = 0.85f))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.Black.copy(alpha = 0.10f))
                    )
                )
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                name, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = textColor,
                textAlign = TextAlign.Center,
                maxLines = when {
                    spanSections >= 4 -> 5
                    spanSections >= 3 -> 4
                    else -> 2
                },
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp
            )
            if (spanSections >= 2 && location.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    "@$location", fontSize = 8.sp,
                    color = textColor.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (weekInfo.isNotEmpty() && spanSections >= 2) {
                Text(
                    weekInfo, fontSize = 7.sp,
                    color = textColor.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
