package com.yzp.videolearning

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Course(
    val categoryId: String,
    val categoryName: String,
    val courseId: String,
    val title: String,
    val chapterOrTopic: String,
    val description: String,
    val teacher: String,
    val titleOrRank: String,
    val durationSeconds: Long?,
    val durationDisplay: String,
    val videoId: String,
    val playerUrl: String,
)

data class CourseCategory(
    val id: String,
    val name: String,
    val courses: List<Course>,
)

data class LearningProgress(
    val lastPositionMs: Long = 0L,
    val completed: Boolean = false,
    val lastOpenedAt: Long = 0L,
)

fun parseDurationToSeconds(value: String?): Long? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isEmpty()) return null
    if (normalized.matches(Regex("\\d+"))) return normalized.toLongOrNull()?.times(60L)
    val parts = normalized.split(":")
    if (parts.size == 2 && parts.all { it.matches(Regex("\\d+")) }) {
        val m = parts[0].toLongOrNull() ?: return null
        val s = parts[1].toLongOrNull() ?: return null
        if (s > 59) return null
        return m * 60L + s
    }
    if (parts.size == 3 && parts.all { it.matches(Regex("\\d+")) }) {
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].toLongOrNull() ?: return null
        if (m > 59 || s > 59) return null
        return h * 3600L + m * 60L + s
    }
    return null
}

fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds < 0) return "时长未知"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60
    return when {
        hours > 0 -> String.format(Locale.CHINA, "%d:%02d:%02d", hours, minutes, remainingSeconds)
        remainingSeconds > 0 -> String.format(Locale.CHINA, "%d分%02d秒", minutes, remainingSeconds)
        else -> "${minutes}分钟"
    }
}

class CourseCatalogRepository(private val context: Context) {
    fun load(): List<CourseCategory> {
        val json = context.assets.open("courses.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseCourseCatalog(json)
    }
}

fun parseCourseCatalog(json: String): List<CourseCategory> {
        val array = JSONArray(json)
        val courses = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: JSONObject()
                val courseId = item.text("courseId").ifBlank { "unavailable-$index" }
                val seconds = item.optLong("durationSeconds", -1L).takeIf { it >= 0 }
                add(
                    Course(
                        categoryId = item.text("categoryId").ifBlank { "uncategorized" },
                        categoryName = item.text("categoryName").ifBlank { "其他课程" },
                        courseId = courseId,
                        title = item.text("title").ifBlank { "未命名课程" },
                        chapterOrTopic = item.text("chapterOrTopic"),
                        description = item.text("description"),
                        teacher = item.text("teacher"),
                        titleOrRank = item.text("titleOrRank"),
                        durationSeconds = seconds,
                        durationDisplay = item.text("durationDisplay").ifBlank { formatDuration(seconds) },
                        videoId = item.text("videoId").ifBlank { "missing-$courseId" },
                        playerUrl = item.text("playerUrl"),
                    )
                )
            }
        }
        return courses.groupBy { it.categoryId }.map { (id, items) ->
            CourseCategory(id = id, name = items.first().categoryName, courses = items)
        }
}

private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key, "").trim()
