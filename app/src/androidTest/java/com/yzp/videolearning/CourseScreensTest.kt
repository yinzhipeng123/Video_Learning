package com.yzp.videolearning

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun categoriesAndCoursesKeepCatalogOrderAndOpenTheSelectedCategory() {
        val orderedCourses = listOf(
            sampleCourse("z-course", title = "课程丙"),
            sampleCourse("a-course", title = "课程甲"),
            sampleCourse("m-course", title = "课程乙"),
        )
        val firstCategory = CourseCategory("z-category", "先出现的分类", orderedCourses)
        val secondCourse = sampleCourse("second-only", categoryId = "a-category", categoryName = "后出现的分类")
        val secondCategory = CourseCategory("a-category", "后出现的分类", listOf(secondCourse))
        showCatalog(listOf(firstCategory, secondCategory))

        val firstTop = compose.onNodeWithTag("category-z-category").fetchSemanticsNode().boundsInRoot.top
        val secondTop = compose.onNodeWithTag("category-a-category").fetchSemanticsNode().boundsInRoot.top
        assertTrue("分类应保持清单顺序", firstTop < secondTop)
        compose.onNodeWithTag("category-z-category").performClick()

        val courseTops = orderedCourses.map {
            compose.onNodeWithTag("course-${it.courseId}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue("课程应保持清单顺序", courseTops.zipWithNext().all { (a, b) -> a < b })
        compose.onNodeWithTag("course-second-only").assertDoesNotExist()

        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("categories").performScrollToNode(hasTestTag("category-a-category"))
        compose.onNodeWithTag("category-a-category").performClick()
        compose.onNodeWithTag("course-second-only").assertIsDisplayed()
        compose.onNodeWithTag("course-z-course").assertDoesNotExist()
    }

    @Test
    fun detailsShowCompleteInformationAndCompletionCanBeChanged() {
        val description = "课程介绍包含文化传承的背景、实践方式与案例分析。".repeat(14)
        val course = sampleCourse("full").copy(
            title = "完整课程标题",
            chapterOrTopic = "课程第一章",
            description = description,
            teacher = "王老师，长期从事文化传承教学与研究",
            titleOrRank = "副教授",
            durationDisplay = "48分35秒",
        )
        showCatalog(listOf(categoryOf(course)))
        openDetails(course)

        compose.onNodeWithText(course.title).assertIsDisplayed()
        compose.onNodeWithText(course.chapterOrTopic).assertIsDisplayed()
        listOf(course.durationDisplay, course.teacher, course.titleOrRank, course.description).forEach { value ->
            compose.onNodeWithTag("course-detail").performScrollToNode(hasText(value))
            compose.onNodeWithText(value).assertTextEquals(value)
        }
        compose.onNodeWithTag("start-learning").assertIsDisplayed()
        compose.onNodeWithText("标记为已学完").performClick()
        compose.onNodeWithText("取消已学完").assertIsDisplayed().performClick()
        compose.onNodeWithText("标记为已学完").assertIsDisplayed()
    }

    @Test
    fun missingOptionalDetailsDoNotLeaveEmptySections() {
        val course = sampleCourse("optional").copy(
            chapterOrTopic = "", description = "", teacher = "", titleOrRank = "",
            durationSeconds = null, durationDisplay = "时长未知",
        )
        showCatalog(listOf(categoryOf(course)))
        openDetails(course)

        compose.onNodeWithText(course.title).assertIsDisplayed()
        compose.onNodeWithText("时长未知").assertIsDisplayed()
        listOf("主讲教师", "教师职称", "课程简介", "null").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("start-learning").assertIsDisplayed()
    }

    @Test
    fun returningFromDetailsPreservesScrolledCoursePosition() {
        val courses = (1..35).map { sampleCourse("row-$it", title = "课程 $it") }
        val selected = courses[25]
        showCatalog(listOf(CourseCategory("culture", "文化课程", courses)))
        compose.onNodeWithTag("category-culture").performClick()
        compose.onNodeWithTag("course-list").performScrollToNode(hasTestTag("course-${selected.courseId}"))
        val before = compose.onNodeWithTag("course-${selected.courseId}")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("course-${selected.courseId}").performClick()
        compose.onNodeWithText(selected.title).assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()

        val after = compose.onNodeWithTag("course-${selected.courseId}")
            .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
        assertEquals("返回后应保持同一课程在列表中的位置", before, after, 1f)
        compose.onNodeWithTag("course-${courses.first().courseId}").assertDoesNotExist()
    }

    @Test
    fun invalidPlayerLinkCanRetryAndReturnToSelectAnotherCourse() {
        val bad = sampleCourse("invalid", title = "异常课程").copy(playerUrl = "not-a-valid-course-link")
        val valid = sampleCourse("valid", title = "另一节正常课程")
        val openedVideoIds = mutableListOf<String>()
        showCatalog(listOf(CourseCategory("culture", "文化课程", listOf(bad, valid))), openedVideoIds)
        openDetails(bad)
        compose.onNodeWithTag("start-learning").performClick()

        val errorText = "课程播放链接无效，请联系课程提供方更新。"
        compose.onNodeWithText(errorText).assertIsDisplayed()
        compose.onNodeWithText("重新加载").assertIsDisplayed().performClick()
        compose.onNodeWithText(errorText).assertIsDisplayed()
        compose.onNodeWithText("返回课程详情").performClick()
        compose.onNodeWithTag("course-detail").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("course-${valid.courseId}").performClick()
        compose.onNodeWithText(valid.title).assertIsDisplayed()
        compose.onNodeWithTag("start-learning").assertIsDisplayed()
        compose.onNodeWithText(errorText).assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(bad.videoId), openedVideoIds) }
    }

    private fun openDetails(course: Course) {
        compose.onNodeWithTag("category-${course.categoryId}").performClick()
        compose.onNodeWithTag("course-${course.courseId}").performClick()
    }

    private fun showCatalog(categories: List<CourseCategory>, openedVideoIds: MutableList<String> = mutableListOf()) {
        compose.setContent {
            var state by remember { mutableStateOf(CatalogUiState(categories = categories)) }
            CourseTheme {
                CourseApp(
                    state = state,
                    onOpened = { videoId ->
                        openedVideoIds.add(videoId)
                        val old = state.progress[videoId] ?: LearningProgress()
                        state = state.copy(progress = state.progress + (videoId to old.copy(lastOpenedAt = 1L)))
                    },
                    onCompleted = { videoId, completed ->
                        val old = state.progress[videoId] ?: LearningProgress()
                        state = state.copy(progress = state.progress + (videoId to old.copy(completed = completed)))
                    },
                    onPosition = { videoId, position ->
                        val old = state.progress[videoId] ?: LearningProgress()
                        state = state.copy(progress = state.progress + (videoId to old.copy(lastPositionMs = position)))
                    },
                )
            }
        }
    }

    private fun categoryOf(course: Course) = CourseCategory(course.categoryId, course.categoryName, listOf(course))

    private fun sampleCourse(
        id: String,
        title: String = "示例课程",
        categoryId: String = "culture",
        categoryName: String = "文化课程",
    ) = Course(
        categoryId = categoryId, categoryName = categoryName, courseId = id,
        title = title, chapterOrTopic = "", description = "示例课程介绍",
        teacher = "王老师", titleOrRank = "教授", durationSeconds = 600L,
        durationDisplay = "10分钟", videoId = "video-$id",
        // Synthetic URL: the test only selects this course; it never requests the remote page.
        playerUrl = "https://e54144713.at.baijiayun.com/web/video/player?vid=1&token=test-placeholder",
    )
}
