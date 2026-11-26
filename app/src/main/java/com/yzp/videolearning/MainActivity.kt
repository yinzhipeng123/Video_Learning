package com.yzp.videolearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CourseTheme {
                val model: LearningViewModel = viewModel()
                val state by model.uiState.collectAsState()
                CourseApp(state, model::markOpened, model::setCompleted, model::savePosition)
            }
        }
    }
}

@Composable
fun CourseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF17685C), onPrimary = Color.White,
            primaryContainer = Color(0xFFD8EEE5), onPrimaryContainer = Color(0xFF123C35),
            background = Color(0xFFF7F8F4), surface = Color(0xFFF7F8F4),
            surfaceVariant = Color(0xFFE9EEE7), onSurface = Color(0xFF1C302A),
        ), content = content,
    )
}

@Composable
fun CourseApp(
    state: CatalogUiState,
    onOpened: (String) -> Unit,
    onCompleted: (String, Boolean) -> Unit,
    onPosition: (String, Long) -> Unit,
) {
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var courseId by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by rememberSaveable { mutableStateOf(false) }
    val category = state.categories.firstOrNull { it.id == categoryId }
    val course = category?.courses?.firstOrNull { it.courseId == courseId }
    // Owned above individual screens so Back preserves list position.
    val listState = rememberSaveable(categoryId, saver = LazyListState.Saver) { LazyListState() }
    when {
        course != null && playing -> PlayerScreen(
            course = course, onBack = { playing = false },
            initialPositionMs = state.progress[course.videoId]?.lastPositionMs ?: 0L,
            onPosition = { onPosition(course.videoId, it) },
        )
        course != null -> DetailScreen(
            course, state.progress[course.videoId], onBack = { courseId = null },
            onPlay = { onOpened(course.videoId); playing = true },
            onCompletedChange = { onCompleted(course.videoId, it) },
        )
        category != null -> CourseListScreen(category, state.progress, listState,
            onBack = { categoryId = null }, onCourseClick = { courseId = it.courseId })
        else -> CategoryScreen(state, onCategory = { categoryId = it.id })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(state: CatalogUiState, onCategory: (CourseCategory) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("文旅学堂", fontWeight = FontWeight.Bold) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("categories"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(24.dp)).padding(24.dp)) {
                    Text("每天一课 · 学有所获", color = Color(0xFFDAEAE1), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(16.dp))
                    Text("从一堂好课开始", color = Color.White, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("文化传承与文旅融合线上课程", color = Color.White)
                    if (state.categories.isNotEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Text("${state.categories.size} 个分类    ${state.categories.sumOf { it.courses.size }} 节课程",
                            color = Color.White, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            item { Text("选择学习方向", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (state.categories.isEmpty() && state.error == null) item { CircularProgressIndicator() }
            items(state.categories, key = { it.id }) { category ->
                Card(onClick = { onCategory(category) }, modifier = Modifier.fillMaxWidth().testTag("category-${category.id}"),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("${category.courses.size} 节课程 · 点击进入", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { Text("学习记录保存在本机，视频播放需要联网。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(category: CourseCategory, progress: Map<String, LearningProgress>, listState: LazyListState,
                     onBack: () -> Unit, onCourseClick: (Course) -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text(category.name) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("course-list"), state = listState,
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("共 ${category.courses.size} 节 · 按课程清单顺序", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(category.courses, key = { it.courseId }) { course ->
                Card(onClick = { onCourseClick(course) }, modifier = Modifier.fillMaxWidth().testTag("course-${course.courseId}"),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp)) {
                        Text(course.title.ifBlank { "未命名课程" }, style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold)
                        if (course.chapterOrTopic.isNotBlank() && course.chapterOrTopic != course.title) {
                            Spacer(Modifier.height(8.dp)); Text(course.chapterOrTopic, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(listOf(course.teacher.substringBefore('，').substringBefore(','), course.durationDisplay)
                            .filter { it.isNotBlank() }.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        progress[course.videoId]?.let { value ->
                            if (value.completed || value.lastOpenedAt > 0) {
                                Spacer(Modifier.height(10.dp))
                                Text(if (value.completed) "已学完" else "继续学习", color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(course: Course, progress: LearningProgress?, onBack: () -> Unit, onPlay: () -> Unit,
                 onCompletedChange: (Boolean) -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(topBar = { TopAppBar(title = { Text("课程详情") }, navigationIcon = { BackButton(onBack) }) },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Button(onClick = onPlay, modifier = Modifier.fillMaxWidth().testTag("start-learning")) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp))
                        Text(if ((progress?.lastOpenedAt ?: 0) > 0) "继续学习" else "开始学习")
                    }
                    TextButton(onClick = { onCompletedChange(progress?.completed != true) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CheckCircle, null); Spacer(Modifier.width(6.dp))
                        Text(if (progress?.completed == true) "取消已学完" else "标记为已学完")
                    }
                }
            }
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("course-detail"),
            contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Text(course.categoryName, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(12.dp))
                Text(course.title.ifBlank { "未命名课程" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (course.chapterOrTopic.isNotBlank() && course.chapterOrTopic != course.title) {
                    Spacer(Modifier.height(12.dp)); Text(course.chapterOrTopic, style = MaterialTheme.typography.titleMedium)
                }
            }
            item { DetailField("课程时长", course.durationDisplay) }
            if (course.teacher.isNotBlank()) item { DetailField("主讲教师", course.teacher) }
            if (course.titleOrRank.isNotBlank()) item { DetailField("教师职称", course.titleOrRank) }
            if (course.description.isNotBlank()) item { DetailField("课程简介", course.description) }
            if (progress?.completed == true) item { Text("本机记录：已学完", color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp)); Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
}
