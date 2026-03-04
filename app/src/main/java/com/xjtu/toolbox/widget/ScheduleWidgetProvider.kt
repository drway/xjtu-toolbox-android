package com.xjtu.toolbox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.google.gson.Gson
import com.xjtu.toolbox.MainActivity
import com.xjtu.toolbox.R
import com.xjtu.toolbox.schedule.CourseItem
import com.xjtu.toolbox.util.AppDatabase
import com.xjtu.toolbox.util.DataCache
import com.xjtu.toolbox.util.XjtuTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking

enum class WidgetSize { SMALL, LARGE }

private data class WidgetCourse(
    val name: String,
    val location: String,
    val startSection: Int,
    val endSection: Int
)

private data class WidgetScheduleData(
    val weekText: String,
    val statusText: String,
    val updateText: String,
    val courses: List<WidgetCourse>,
    val hasCache: Boolean
)

object ScheduleWidgetUpdater {
    const val ACTION_REFRESH = "com.xjtu.toolbox.widget.ACTION_REFRESH_SCHEDULE_WIDGET"

    private val gson = Gson()

    fun requestUpdate(context: Context) {
        context.sendBroadcast(
            Intent(context, ScheduleWidget2x2Provider::class.java).apply { action = ACTION_REFRESH }
        )
        context.sendBroadcast(
            Intent(context, ScheduleWidget4x2Provider::class.java).apply { action = ACTION_REFRESH }
        )
    }

    fun updateSpecific(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        size: WidgetSize
    ) {
        if (appWidgetIds.isEmpty()) return
        val data = loadTodaySchedule(context)
        appWidgetIds.forEach { widgetId ->
            val views = when (size) {
                WidgetSize.SMALL -> buildSmallViews(context, data)
                WidgetSize.LARGE -> buildLargeViews(context, data)
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildLaunchPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val launchIntent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildSmallViews(context: Context, data: WidgetScheduleData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_2x2)
        views.setTextViewText(R.id.widget_title, "今日课表")
        views.setTextViewText(R.id.widget_week, data.weekText)
        views.setTextViewText(R.id.widget_status, data.statusText)
        views.setTextViewText(R.id.widget_update, data.updateText)
        views.setOnClickPendingIntent(R.id.widget_root, buildLaunchPendingIntent(context, 1001))

        if (!data.hasCache) {
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, "暂无课表缓存\n请先打开课表页同步")
            views.setViewVisibility(R.id.row_course_1, android.view.View.GONE)
            return views
        }

        if (data.courses.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, "今天没有课程")
            views.setViewVisibility(R.id.row_course_1, android.view.View.GONE)
            return views
        }

        val first = data.courses.first()
        views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
        views.setViewVisibility(R.id.row_course_1, android.view.View.VISIBLE)
        views.setTextViewText(
            R.id.widget_time_1,
            "${XjtuTime.getClassStartStr(first.startSection)}-${XjtuTime.getClassEndStr(first.endSection)}"
        )
        views.setTextViewText(R.id.widget_name_1, first.name)
        views.setTextViewText(R.id.widget_location_1, first.location.ifBlank { "地点待定" })
        return views
    }

    private fun buildLargeViews(context: Context, data: WidgetScheduleData): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_4x2)
        views.setTextViewText(R.id.widget_title, "今日课表")
        views.setTextViewText(R.id.widget_week, data.weekText)
        views.setTextViewText(R.id.widget_status, data.statusText)
        views.setTextViewText(R.id.widget_update, data.updateText)
        views.setOnClickPendingIntent(R.id.widget_root, buildLaunchPendingIntent(context, 1002))

        if (!data.hasCache) {
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, "暂无课表缓存，请先打开课表页同步")
            hideLargeRows(views)
            return views
        }

        if (data.courses.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_empty, "今天没有课程")
            hideLargeRows(views)
            return views
        }

        views.setViewVisibility(R.id.widget_empty, android.view.View.GONE)
        bindLargeRow(views, 0, data.courses.getOrNull(0))
        bindLargeRow(views, 1, data.courses.getOrNull(1))
        bindLargeRow(views, 2, data.courses.getOrNull(2))
        return views
    }

    private fun hideLargeRows(views: RemoteViews) {
        views.setViewVisibility(R.id.row_course_1, android.view.View.GONE)
        views.setViewVisibility(R.id.row_course_2, android.view.View.GONE)
        views.setViewVisibility(R.id.row_course_3, android.view.View.GONE)
    }

    private fun bindLargeRow(views: RemoteViews, index: Int, course: WidgetCourse?) {
        val rowId = when (index) {
            0 -> R.id.row_course_1
            1 -> R.id.row_course_2
            else -> R.id.row_course_3
        }
        val timeId = when (index) {
            0 -> R.id.widget_time_1
            1 -> R.id.widget_time_2
            else -> R.id.widget_time_3
        }
        val nameId = when (index) {
            0 -> R.id.widget_name_1
            1 -> R.id.widget_name_2
            else -> R.id.widget_name_3
        }
        val locId = when (index) {
            0 -> R.id.widget_location_1
            1 -> R.id.widget_location_2
            else -> R.id.widget_location_3
        }

        if (course == null) {
            views.setViewVisibility(rowId, android.view.View.GONE)
            return
        }

        views.setViewVisibility(rowId, android.view.View.VISIBLE)
        views.setTextViewText(
            timeId,
            "${XjtuTime.getClassStartStr(course.startSection)}-${XjtuTime.getClassEndStr(course.endSection)}"
        )
        views.setTextViewText(nameId, course.name)
        views.setTextViewText(locId, course.location.ifBlank { "地点待定" })
    }

    private fun loadTodaySchedule(context: Context): WidgetScheduleData {
        val now = LocalTime.now()
        val nowDate = LocalDate.now()
        val updateText = "更新 ${now.format(DateTimeFormatter.ofPattern("HH:mm"))}"

        val cache = DataCache(context)
        val termListJson = cache.get("schedule_term_list", Long.MAX_VALUE)
        val termList = if (termListJson != null) {
            runCatching { gson.fromJson(termListJson, Array<String>::class.java)?.toList().orEmpty() }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val termCode = termList.firstOrNull()
            ?: return WidgetScheduleData(
                weekText = "未同步",
                statusText = "请先进入课表页",
                updateText = updateText,
                courses = emptyList(),
                hasCache = false
            )

        val scheduleJson = cache.get("schedule_$termCode", Long.MAX_VALUE)
        val apiCourses = if (scheduleJson != null) {
            runCatching { gson.fromJson(scheduleJson, Array<CourseItem>::class.java)?.toList().orEmpty() }
                .getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val customCourses = runCatching {
            runBlocking {
                AppDatabase.getInstance(context)
                    .customCourseDao()
                    .getByTerm(termCode)
                    .map { it.toCourseItem() }
            }
        }.getOrDefault(emptyList())

        val allCourses = apiCourses + customCourses

        val startDateRaw = cache.get("start_date_$termCode", Long.MAX_VALUE)
        val startDate = if (!startDateRaw.isNullOrBlank()) {
            runCatching {
                val dateStr = gson.fromJson(startDateRaw, String::class.java)
                LocalDate.parse(dateStr)
            }.getOrNull()
        } else {
            null
        }

        val currentWeek = startDate?.let {
            val days = java.time.temporal.ChronoUnit.DAYS.between(it, nowDate)
            ((days / 7) + 1).toInt()
        }

        val todayCourses = allCourses
            .asSequence()
            .filter { it.dayOfWeek == nowDate.dayOfWeek.value }
            .filter {
                if (currentWeek == null) true
                else currentWeek > 0 && it.isInWeek(currentWeek)
            }
            .sortedBy { it.startSection }
            .map {
                WidgetCourse(
                    name = it.courseName,
                    location = it.location,
                    startSection = it.startSection,
                    endSection = it.endSection
                )
            }
            .toList()

        val currentCourse = todayCourses.firstOrNull { course ->
            val start = XjtuTime.getClassTime(course.startSection)?.start
            val end = XjtuTime.getClassTime(course.endSection)?.end
            start != null && end != null && now >= start && now <= end
        }
        val nextCourse = todayCourses.firstOrNull { course ->
            val start = XjtuTime.getClassTime(course.startSection)?.start
            start != null && now < start
        }

        val status = when {
            todayCourses.isEmpty() -> "今天没有课程"
            currentCourse != null -> "正在上课：${currentCourse.name}"
            nextCourse != null -> "下一节：${XjtuTime.getClassStartStr(nextCourse.startSection)} ${nextCourse.name}"
            else -> "今日课程已结束"
        }

        val weekText = if (currentWeek != null) {
            if (currentWeek <= 0) "未开学" else "第${currentWeek}周"
        } else {
            "未计算周次"
        }

        return WidgetScheduleData(
            weekText = weekText,
            statusText = status,
            updateText = updateText,
            courses = todayCourses,
            hasCache = true
        )
    }
}

class ScheduleWidget2x2Provider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ScheduleWidgetUpdater.updateSpecific(context, appWidgetManager, appWidgetIds, WidgetSize.SMALL)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == ScheduleWidgetUpdater.ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidget2x2Provider::class.java))
            ScheduleWidgetUpdater.updateSpecific(context, manager, ids, WidgetSize.SMALL)
        }
    }
}

class ScheduleWidget4x2Provider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        ScheduleWidgetUpdater.updateSpecific(context, appWidgetManager, appWidgetIds, WidgetSize.LARGE)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        if (intent?.action == ScheduleWidgetUpdater.ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ScheduleWidget4x2Provider::class.java))
            ScheduleWidgetUpdater.updateSpecific(context, manager, ids, WidgetSize.LARGE)
        }
    }
}
