package com.xjtu.toolbox.schedule

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.extra.SuperBottomSheet
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperSpinner
import top.yukonga.miuix.kmp.basic.SpinnerEntry
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 自定义课程编辑弹窗 (MIUIX 风格)
 * @param existing 编辑已有课程时传入，为 null 表示新增
 * @param termCode 当前学期代码
 * @param onSave 保存回调
 * @param onDelete 删除回调（仅编辑模式）
 * @param onDismiss 关闭回调
 */
@Composable
fun CustomCourseDialog(
    show: MutableState<Boolean> = mutableStateOf(true),
    existing: CustomCourseEntity? = null,
    termCode: String,
    onSave: (CustomCourseEntity) -> Unit,
    onDelete: ((CustomCourseEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isEdit = existing != null

    var courseName by remember { mutableStateOf(existing?.courseName ?: "") }
    var teacher by remember { mutableStateOf(existing?.teacher ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var dayOfWeek by remember { mutableIntStateOf(existing?.dayOfWeek ?: 1) }
    var startSection by remember { mutableIntStateOf(existing?.startSection ?: 1) }
    var endSection by remember { mutableIntStateOf(existing?.endSection ?: 2) }
    var selectedWeeks by remember {
        mutableStateOf(
            if (existing != null) {
                existing.weekBits.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }.toSet()
            } else {
                (1..16).toSet()
            }
        )
    }

    val showDeleteConfirm = remember { mutableStateOf(false) }

    // ── 删除确认 (SuperDialog) ──
    if (existing != null && showDeleteConfirm.value) {
        BackHandler { showDeleteConfirm.value = false }
        SuperDialog(
            show = showDeleteConfirm,
            title = "删除课程",
            summary = "确定要删除「${existing.courseName}」吗？此操作不可恢复。",
            onDismissRequest = { showDeleteConfirm.value = false }
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = "取消",
                    onClick = { showDeleteConfirm.value = false },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onDelete?.invoke(existing); onDismiss() },
                    modifier = Modifier.weight(1f)
                ) { Text("删除") }
            }
        }
    }

    // ── 主编辑面板 ──
    BackHandler(enabled = show.value) { show.value = false; onDismiss() }
    SuperBottomSheet(
        show = show,
        title = if (isEdit) "编辑课程" else "添加课程",
        onDismissRequest = { show.value = false; onDismiss() }
    ) {
        Column(
            modifier = Modifier.overScrollVertical().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 课程名称 ──
            TextField(
                value = courseName,
                onValueChange = { courseName = it },
                label = "课程名称 *",
                borderColor = if (courseName.isNotEmpty() && courseName.isBlank()) MiuixTheme.colorScheme.error else Color.Unspecified,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ── 教师 / 教室 ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = "教师（可选）",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = location,
                    onValueChange = { location = it },
                    label = "教室（可选）",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MiuixTheme.colorScheme.outline.copy(alpha = 0.2f))

            // ── 星期 ──
            Text("星期", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            TabRowWithContour(
                tabs = listOf("一", "二", "三", "四", "五", "六", "日"),
                selectedTabIndex = dayOfWeek - 1,
                onTabSelected = { dayOfWeek = it + 1 },
                modifier = Modifier.fillMaxWidth()
            )

            // ── 节次 (SuperSpinner) ──
            val sectionEntries = (1..12).map { SpinnerEntry(title = "第${it}节") }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuperSpinner(
                    title = "开始",
                    items = sectionEntries,
                    selectedIndex = startSection - 1,
                    dialogButtonString = "确定",
                    onSelectedIndexChange = { index ->
                        startSection = index + 1
                        if (endSection < index + 1) endSection = index + 1
                    },
                    modifier = Modifier.weight(1f)
                )
                Text("→", style = MiuixTheme.textStyles.body1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                SuperSpinner(
                    title = "结束",
                    items = sectionEntries,
                    selectedIndex = endSection - 1,
                    dialogButtonString = "确定",
                    onSelectedIndexChange = { index ->
                        endSection = index + 1
                        if (startSection > index + 1) startSection = index + 1
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(color = MiuixTheme.colorScheme.outline.copy(alpha = 0.2f))

            // ── 上课周次 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("上课周次", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.weight(1f))
                TextButton(text = "全选", onClick = { selectedWeeks = (1..20).toSet() })
                TextButton(text = "单周", onClick = { selectedWeeks = (1..20).filter { it % 2 == 1 }.toSet() })
                TextButton(text = "双周", onClick = { selectedWeeks = (1..20).filter { it % 2 == 0 }.toSet() })
                TextButton(text = "清空", onClick = { selectedWeeks = emptySet() })
            }
            WeekCheckboxGrid(selectedWeeks = selectedWeeks, onToggle = { week ->
                selectedWeeks = if (week in selectedWeeks) selectedWeeks - week else selectedWeeks + week
            })

            // ── 备注 ──
            TextField(
                value = note,
                onValueChange = { note = it },
                label = "备注（可选）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ── 底部操作区 ──
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                val weekBitsStr = (1..20).joinToString("") { if (it in selectedWeeks) "1" else "0" }
                val entity = (existing ?: CustomCourseEntity(
                    courseName = "", teacher = "", location = "", weekBits = "",
                    dayOfWeek = 1, startSection = 1, endSection = 1, termCode = termCode
                )).copy(
                    courseName = courseName.trim(),
                    teacher = teacher.trim(),
                    location = location.trim(),
                    weekBits = weekBitsStr,
                    dayOfWeek = dayOfWeek,
                    startSection = startSection,
                    endSection = endSection,
                    termCode = termCode,
                    note = note.trim()
                )
                onSave(entity)
                show.value = false
                onDismiss()
            },
            enabled = courseName.isNotBlank() && selectedWeeks.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isEdit) "保存修改" else "添加课程")
        }

        if (isEdit) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                text = "删除此课程",
                onClick = { showDeleteConfirm.value = true },
                colors = ButtonDefaults.textButtonColors(textColor = MiuixTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(16.dp))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun WeekCheckboxGrid(selectedWeeks: Set<Int>, onToggle: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in listOf(1..10, 11..20)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (week in row) {
                    val isSelected = week in selectedWeeks
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 36.dp).clickable { onToggle(week) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "$week",
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}
