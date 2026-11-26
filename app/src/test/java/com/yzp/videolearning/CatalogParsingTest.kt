package com.yzp.videolearning

import org.junit.Assert.*
import org.junit.Test

class CatalogParsingTest {
    @Test fun badRecordDoesNotHideValidCoursesAndOptionalNullsStayEmpty() {
        val groups = parseCourseCatalog("""[
          {"categoryId":"a","categoryName":"A","courseId":"one","title":"第一课","videoId":"10","teacher":null,"durationSeconds":90},
          {"categoryId":"a","categoryName":"A","courseId":"bad","title":"链接待更新","playerUrl":"invalid"},
          {"categoryId":"b","categoryName":"B","courseId":"two","title":"第二课","videoId":"20"},
          {"categoryId":"a","categoryName":"A","courseId":"three","title":"第三课"}
        ]""")
        assertEquals(listOf("a", "b"), groups.map { it.id })
        assertEquals(listOf("one", "bad", "three"), groups.first().courses.map { it.courseId })
        assertEquals("", groups.first().courses.first().teacher)
        assertEquals("1分30秒", groups.first().courses.first().durationDisplay)
        assertFalse(PlaybackPolicy.allows(groups.first().courses[1].playerUrl))
        assertEquals("第二课", groups[1].courses.first().title)
    }

    @Test fun malformedEntryGetsBrowsablePlaceholder() {
        val groups = parseCourseCatalog("""[null,{"courseId":"ok","title":"可用课程"}]""")
        assertEquals(2, groups.single().courses.size)
        assertEquals("未命名课程", groups.single().courses.first().title)
        assertEquals("可用课程", groups.single().courses.last().title)
    }

    @Test fun minuteSecondDurationAndUnknownValues() {
        assertEquals(31L * 60 + 5, parseDurationToSeconds("31:05"))
        assertNull(parseDurationToSeconds("31:65"))
        assertNull(parseDurationToSeconds("unknown"))
    }
}
