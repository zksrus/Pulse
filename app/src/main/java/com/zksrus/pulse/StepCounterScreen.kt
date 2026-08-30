package com.zksrus.pulse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepCounterScreen(
    uiState: StepUiState,
    onRefresh: () -> Unit,
    onGoalChanged: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "🏃 Шагомер",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    if (uiState.isDemoMode) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Demo", fontSize = 12.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color(0xFFF0883E).copy(alpha = 0.2f),
                                labelColor = Color(0xFFF0883E)
                            )
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Обновить",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF3FB950),
                    strokeWidth = 3.dp
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Step Ring
                uiState.stepData?.let { data ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically()
                    ) {
                        StepRing(
                            steps = data.steps,
                            goal = data.goal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Stats Cards Row
                uiState.stepData?.let { data ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            icon = Icons.Default.Favorite,
                            label = "Калории",
                            value = "${data.calories} kcal",
                            color = Color(0xFFF0883E),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = null,
                            label = "Расстояние",
                            value = String.format("%.1f км", data.distance),
                            color = Color(0xFF58A6FF),
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            icon = null,
                            label = "Время",
                            value = "${(data.steps / 100).coerceAtMost(120)} мин",
                            color = Color(0xFFBC8CFF),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Weekly Chart
                if (uiState.weeklySteps.isNotEmpty()) {
                    WeeklyChart(
                        weeklySteps = uiState.weeklySteps,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Goal selector
                uiState.stepData?.let { data ->
                    GoalSelector(
                        currentGoal = data.goal,
                        onGoalChanged = onGoalChanged
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Sign in prompt for non-demo mode
                if (!uiState.isDemoMode && !uiState.isSignedIn) {
                    SignInPrompt()
                }

                // Error message
                uiState.error?.let { error ->
                    ErrorBanner(error = error)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun StatCard(
    icon: ImageVector?,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun WeeklyChart(
    weeklySteps: List<DailySteps>,
    modifier: Modifier = Modifier
) {
    val maxSteps = weeklySteps.maxOfOrNull { it.steps } ?: 1L

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Активность за неделю",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklySteps.forEach { day ->
                    val barHeight = if (maxSteps > 0) {
                        (day.steps.toFloat() / maxSteps.toFloat())
                    } else 0f

                    val isToday = day == weeklySteps.last()

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(24.dp)
                                .fillMaxHeight(barHeight.coerceAtLeast(0.05f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (isToday) Color(0xFF3FB950) else Color(0xFF238636).copy(alpha = 0.6f)
                                )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = day.date,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) Color(0xFF3FB950) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Среднее: ${formatStepNumber(weeklySteps.map { it.steps }.average().toLong())} шагов/день",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSelector(
    currentGoal: Long,
    onGoalChanged: (Long) -> Unit
) {
    val goals = listOf(5000L, 8000L, 10000L, 15000L, 20000L)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Дневная цель",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            goals.forEach { goal ->
                val isSelected = goal == currentGoal
                FilterChip(
                    selected = isSelected,
                    onClick = { onGoalChanged(goal) },
                    label = {
                        Text(
                            text = "${goal / 1000}K",
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        selectedContainerColor = Color(0xFF3FB950).copy(alpha = 0.3f),
                        selectedLabelColor = Color(0xFF3FB950)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun SignInPrompt() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF58A6FF).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "📱 Подключите Huawei Health",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF58A6FF)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Установите Huawei Health на телефон и включите синхронизация данных о шагах",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ErrorBanner(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF0883E).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = "⚠️ $error",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFF0883E)
        )
    }
}
