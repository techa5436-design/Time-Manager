package com.example

import android.Manifest
import android.app.Application
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Task
import com.example.data.TaskDatabase
import com.example.data.TaskRepository
import com.example.ui.TaskViewModel
import com.example.ui.TaskViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import java.util.Calendar
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                // Initialize database & repository inside activity context
                val context = LocalContext.current.applicationContext
                val database = TaskDatabase.getDatabase(context)
                val repository = TaskRepository(database.taskDao())
                val app = context as Application

                val factory = TaskViewModelFactory(app, repository)
                val taskViewModel: TaskViewModel = viewModel(factory = factory)

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    containerColor = Color.Transparent
                ) { innerPadding ->
                    TaskGlassApp(
                        viewModel = taskViewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// Custom Glassmorphic background and borders
fun Modifier.glassmorphicCard(
    backgroundColor: Color = Color(0x18FFFFFF),
    borderColor: Color = Color(0x22FFFFFF),
    cornerRadius: Int = 20
) = this
    .clip(RoundedCornerShape(cornerRadius.dp))
    .background(backgroundColor)
    .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(cornerRadius.dp))

@Composable
fun TaskGlassApp(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentTasks by viewModel.currentDayTasks.collectAsStateWithLifecycle()
    val historicalTasks by viewModel.historicalTasks.collectAsStateWithLifecycle()
    val lifetimeScore by viewModel.lifetimeScore.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()

    var showAddTaskDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf("today") } // "today" or "insights"

    // Request notification permissions for Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!isGranted) {
            Toast.makeText(
                context,
                "Notifications disabled. Alarms won't show visual alerts.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Glassmorphic Space Dark Background
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Background deep mesh colors
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0C0D16), // cosmic black-blue
                            Color(0xFF141526)  // deep nebula dark slate
                        )
                    )
                )
                // Top-right glowing neon teal light orb
                drawCircle(
                    color = Color(0x1200E5FF),
                    radius = 420f,
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, 150f)
                )
                // Bottom-left glowing orchid purple light orb
                drawCircle(
                    color = Color(0x14D946EF),
                    radius = 550f,
                    center = androidx.compose.ui.geometry.Offset(50f, size.height * 0.85f)
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Score Dashboard & Gamified Badge
            ScoreDashboard(
                lifetimeScore = lifetimeScore,
                currentTasks = currentTasks,
                selectedDate = selectedDate
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Glass Segmented Custom Tab Control
            TabSelector(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "TabContent"
                ) { tab ->
                    when (tab) {
                        "today" -> {
                            ActiveTasksSection(
                                tasks = currentTasks,
                                onComplete = { viewModel.completeTask(it) },
                                onFail = { viewModel.failTask(it) },
                                onDelete = { viewModel.deleteTask(it) },
                                onReset = { viewModel.resetTask(it) },
                                onToggleAlarm = { viewModel.toggleAlarm(it) }
                            )
                        }
                        "insights" -> {
                            InsightsSection(
                                historicalTasks = historicalTasks
                            )
                        }
                    }
                }
            }
        }

        // Add Task Floating Action Button (Only on Today Tab)
        if (activeTab == "today") {
            FloatingActionButton(
                onClick = { showAddTaskDialog = true },
                containerColor = Color(0x40FFFFFF),
                contentColor = Color(0xFF00E5FF),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .border(BorderStroke(1.dp, Color(0x40FFFFFF)), CircleShape)
                    .testTag("add_task_fab"),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create Task",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Add Task Dialog
        if (showAddTaskDialog) {
            AddTaskDialog(
                onDismiss = { showAddTaskDialog = false },
                onTaskAdded = { title, time, isAlarm ->
                    viewModel.addTask(title, time, isAlarm)
                    showAddTaskDialog = false
                    Toast.makeText(context, "Task created successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// gamification ranks
fun getRankName(xp: Int): String {
    return when {
        xp < 50 -> "Task Initiate 🎯"
        xp < 150 -> "Focus Builder ⚡"
        xp < 300 -> "Momentum Master 🔥"
        else -> "Productivity Sage 👑"
    }
}

@Composable
fun ScoreDashboard(
    lifetimeScore: Int,
    currentTasks: List<Task>,
    selectedDate: String
) {
    val totalTasks = currentTasks.size
    val completedCount = currentTasks.count { it.status == "COMPLETED" }
    
    // Daily percentage calculation
    val dailyProgress = if (totalTasks == 0) 0f else (completedCount.toFloat() / totalTasks)
    val dailyPercent = (dailyProgress * 100).toInt()

    val animatedProgress by animateFloatAsState(
        targetValue = dailyProgress,
        animationSpec = tween(durationMillis = 800),
        label = "DailyProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphicCard(
                backgroundColor = Color(0x10FFFFFF),
                borderColor = Color(0x18FFFFFF),
                cornerRadius = 24
            )
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.3f)) {
                Text(
                    text = "LIFETIME REWARD",
                    fontSize = 11.sp,
                    color = Color(0x99FFFFFF),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "$lifetimeScore XP",
                    fontSize = 32.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Gamified Rank Level
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0x15FFFFFF), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Rank",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = getRankName(lifetimeScore),
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = TaskViewModel.getReadableDateString(selectedDate),
                    fontSize = 12.sp,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Medium
                )
            }

            // Daily completion progress ring
            Column(
                modifier = Modifier.weight(0.7f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(72.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 6.dp,
                        trackColor = Color(0x20FFFFFF)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$dailyPercent%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Score",
                            fontSize = 9.sp,
                            color = Color(0x88FFFFFF)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$completedCount/$totalTasks Done",
                    fontSize = 11.sp,
                    color = Color(0xBBFFFFFF),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TabSelector(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .glassmorphicCard(
                backgroundColor = Color(0x0AFFFFFF),
                borderColor = Color(0x10FFFFFF),
                cornerRadius = 14
            )
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Today Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (activeTab == "today") Color(0x25FFFFFF) else Color.Transparent
                    )
                    .clickable { onTabSelected("today") }
                    .testTag("tab_today"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Today,
                        contentDescription = "Today",
                        tint = if (activeTab == "today") Color(0xFF00E5FF) else Color(0x80FFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Today's Tasks",
                        color = if (activeTab == "today") Color.White else Color(0x80FFFFFF),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            // Insights Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (activeTab == "insights") Color(0x25FFFFFF) else Color.Transparent
                    )
                    .clickable { onTabSelected("insights") }
                    .testTag("tab_insights"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Insights",
                        tint = if (activeTab == "insights") Color(0xFF00E5FF) else Color(0x80FFFFFF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Past Insights",
                        color = if (activeTab == "insights") Color.White else Color(0x80FFFFFF),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveTasksSection(
    tasks: List<Task>,
    onComplete: (Task) -> Unit,
    onFail: (Task) -> Unit,
    onDelete: (Task) -> Unit,
    onReset: (Task) -> Unit,
    onToggleAlarm: (Task) -> Unit
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Checklist,
                    contentDescription = "No tasks",
                    tint = Color(0x40FFFFFF),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No tasks for today",
                    color = Color(0x80FFFFFF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Press the floating + button to write a task!",
                    color = Color(0x50FFFFFF),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(tasks, key = { it.id }) { task ->
                ActiveTaskItemCard(
                    task = task,
                    onComplete = { onComplete(task) },
                    onFail = { onFail(task) },
                    onDelete = { onDelete(task) },
                    onReset = { onReset(task) },
                    onToggleAlarm = { onToggleAlarm(task) }
                )
            }
        }
    }
}

@Composable
fun ActiveTaskItemCard(
    task: Task,
    onComplete: () -> Unit,
    onFail: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    onToggleAlarm: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphicCard(
                backgroundColor = when (task.status) {
                    "COMPLETED" -> Color(0x1534C759) // transparent emerald
                    "FAILED" -> Color(0x15FF3B30)    // transparent crimson
                    else -> Color(0x10FFFFFF)        // normal glass
                },
                borderColor = when (task.status) {
                    "COMPLETED" -> Color(0x3034C759)
                    "FAILED" -> Color(0x30FF3B30)
                    else -> Color(0x15FFFFFF)
                },
                cornerRadius = 18
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1.2f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Time text
                    Text(
                        text = task.time,
                        fontSize = 13.sp,
                        color = if (task.status == "PENDING") Color(0xFF00E5FF) else Color(0x80FFFFFF),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Alarm status indicator
                    Icon(
                        imageVector = if (task.isAlarmEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsOff,
                        contentDescription = "Alarm state",
                        tint = if (task.isAlarmEnabled && task.status == "PENDING") Color(0xFF00E5FF) else Color(0x50FFFFFF),
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .clickable { onToggleAlarm() }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                // Task Title
                Text(
                    text = task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (task.status == "PENDING") Color.White else Color(0x80FFFFFF),
                    textDecoration = if (task.status == "COMPLETED") TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Interactive decision buttons on the right: x and ✅
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (task.status == "PENDING") {
                    // Failing Cross (❌) Button
                    IconButton(
                        onClick = onFail,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x1EAA0000), CircleShape)
                            .border(BorderStroke(1.dp, Color(0x40FF3B30)), CircleShape)
                            .testTag("fail_task_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Mark Failed",
                            tint = Color(0xFFFF4D4D),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Success Check (✅) Button
                    IconButton(
                        onClick = onComplete,
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0x1E00AA00), CircleShape)
                            .border(BorderStroke(1.dp, Color(0x4034C759)), CircleShape)
                            .testTag("check_task_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Complete Task",
                            tint = Color(0xFF2ECC71),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // Task Done or Failed - show status badge & delete button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Status badge (clickable to reset to Pending)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (task.status == "COMPLETED") Color(0x3034C759) else Color(0x30FF3B30),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onReset() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (task.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = "Reset Status",
                                    tint = if (task.status == "COMPLETED") Color(0xFF2ECC71) else Color(0xFFFF4D4D),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (task.status == "COMPLETED") "Done" else "Failed",
                                    color = if (task.status == "COMPLETED") Color(0xFF2ECC71) else Color(0xFFFF4D4D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Trash Bin Button to remove completely
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Task",
                                tint = Color(0x60FFFFFF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InsightsSection(
    historicalTasks: List<Task>
) {
    if (historicalTasks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.TrendingUp,
                    contentDescription = "No Historical Insights",
                    tint = Color(0x30FFFFFF),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No prior history available",
                    color = Color(0x80FFFFFF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Check in tomorrow to view today's comprehensive productivity insights!",
                    color = Color(0x50FFFFFF),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp)
                )
            }
        }
    } else {
        // Group historical tasks by date, sorting newest dates first
        val tasksByDate = remember(historicalTasks) {
            historicalTasks.groupBy { it.date }.toList().sortedByDescending { it.first }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 30.dp)
        ) {
            items(tasksByDate) { (dateStr, tasks) ->
                HistoricalDayCard(
                    dateStr = dateStr,
                    tasks = tasks
                )
            }
        }
    }
}

@Composable
fun HistoricalDayCard(
    dateStr: String,
    tasks: List<Task>
) {
    var isExpanded by remember { mutableStateOf(false) }

    val total = tasks.size
    val completed = tasks.count { it.status == "COMPLETED" }

    val successRate = if (total == 0) 0 else ((completed.toFloat() / total) * 100).toInt()

    // Color code success rates
    val ratingColor = when {
        successRate >= 80 -> Color(0xFF2ECC71) // High - Emerald Green
        successRate >= 50 -> Color(0xFFF39C12) // Mid - Amber Orange
        else -> Color(0xFFFF4D4D)              // Low - Crimson Red
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphicCard(
                backgroundColor = Color(0x0CFFFFFF),
                borderColor = Color(0x12FFFFFF),
                cornerRadius = 18
            )
            .clickable { isExpanded = !isExpanded }
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = TaskViewModel.getReadableDateString(dateStr),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$completed/$total Tasks completed",
                        fontSize = 12.sp,
                        color = Color(0xCCFFFFFF),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Score Badge
                Box(
                    modifier = Modifier
                        .background(ratingColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, ratingColor.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$successRate% XP",
                        color = ratingColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom productivity meter bar
            LinearProgressIndicator(
                progress = { successRate / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = ratingColor,
                trackColor = Color(0x1AFFFFFF)
            )

            // Expanding detail view of tasks that day
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color(0x15FFFFFF), thickness = 1.dp)
                Spacer(modifier = Modifier.height(8.dp))

                tasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (task.status == "COMPLETED") Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = "status",
                                tint = if (task.status == "COMPLETED") Color(0xFF2ECC71) else Color(0xFFFF4D4D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = task.title,
                                color = Color(0xCCFFFFFF),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = task.time,
                            color = Color(0x60FFFFFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tap to review items",
                        fontSize = 10.sp,
                        color = Color(0x50FFFFFF)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand details",
                        tint = Color(0x40FFFFFF),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddTaskDialog(
    onDismiss: () -> Unit,
    onTaskAdded: (String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var alarmHour by remember { mutableIntStateOf(9) }
    var alarmMinute by remember { mutableIntStateOf(0) }
    var isAlarmEnabled by remember { mutableStateOf(true) }

    // Display formatted time state
    val timeString = String.format(Locale.getDefault(), "%02d:%02d", alarmHour, alarmMinute)

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassmorphicCard(
                    backgroundColor = Color(0xFF161824).copy(alpha = 0.95f),
                    borderColor = Color(0x22FFFFFF),
                    cornerRadius = 24
                )
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Create Task ✍️",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Task Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Task details...", color = Color(0x80FFFFFF)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_title_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0x30FFFFFF),
                        focusedLabelColor = Color(0xFF00E5FF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Time Pick Button Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassmorphicCard(
                            backgroundColor = Color(0x0AFFFFFF),
                            borderColor = Color(0x10FFFFFF),
                            cornerRadius = 14
                        )
                        .clickable {
                            // Launch Native TimePickerDialog
                            val tpd = TimePickerDialog(
                                context,
                                { _, h, m ->
                                    alarmHour = h
                                    alarmMinute = m
                                },
                                alarmHour,
                                alarmMinute,
                                true
                            )
                            tpd.show()
                        }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ALARM TIME",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0x80FFFFFF)
                        )
                        Text(
                            text = timeString,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0x1500E5FF), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Set Time ⏰",
                            color = Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Alarm switch toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAlarmEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = "Notification alert",
                            tint = if (isAlarmEnabled) Color(0xFF00E5FF) else Color(0x50FFFFFF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Sound Reminder",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Notify with sound at task time",
                                color = Color(0x50FFFFFF),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Switch(
                        checked = isAlarmEnabled,
                        onCheckedChange = { isAlarmEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E5FF),
                            uncheckedThumbColor = Color(0x80FFFFFF),
                            uncheckedTrackColor = Color(0x20FFFFFF)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("CANCEL", color = Color(0x80FFFFFF), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.trim().isEmpty()) {
                                Toast.makeText(context, "Please write task details!", Toast.LENGTH_SHORT).show()
                            } else {
                                onTaskAdded(title.trim(), timeString, isAlarmEnabled)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CREATE", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}
