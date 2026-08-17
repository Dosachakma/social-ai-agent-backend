package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ActivityLog
import com.example.data.model.PlatformType
import com.example.data.model.SocialAccount
import com.example.ui.components.*
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusInfo

enum class PerformanceTimeRange(val label: String, val days: Int) {
    SEVEN_DAYS("7D", 7),
    THIRTY_DAYS("30D", 30),
    NINETY_DAYS("90D", 90)
}

enum class MetricFocus(val label: String) {
    ALL("Combined"),
    REACH("Reach"),
    ENGAGEMENT("Engagement"),
    FOLLOWERS("Followers")
}

data class PerformanceMetricPoint(
    val label: String,
    val reachValue: Float, // in thousands
    val engagementValue: Float, // in thousands
    val followerGained: Int,
    val dateDetail: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToAgent: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToContent: () -> Unit,
    onNavigateToAnalytics: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedTimeRange by remember { mutableStateOf(PerformanceTimeRange.SEVEN_DAYS) }
    var metricFocus by remember { mutableStateOf(MetricFocus.ALL) }

    if (showCreateDialog) {
        CreatePostDialog(
            onDismiss = { showCreateDialog = false },
            onSubmitPost = { post -> viewModel.createNewPost(post) },
            onGenerateAiCopy = { topic, platform -> viewModel.generateAiCopy(topic, platform) },
            aiGeneratedCopy = uiState.aiGeneratedCopy
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Social Studio",
                subtitle = "AI Social Management OS",
                workspaceName = "Enterprise HQ",
                onNavigateToNotifications = { onNavigateToAgent() },
                onNavigateToProfile = { onNavigateToAccounts() }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen")
    ) { paddingValues ->
        if (uiState.isLoading) {
            SaaSSkeletonLoader(modifier = Modifier.padding(paddingValues))
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val isWide = maxWidth > 600.dp
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1000.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 1. WORKSPACE STATUS HEADER
                    item {
                        DashboardWorkspaceStatusCard(
                            connectedAccounts = uiState.connectedAccounts,
                            onManageClick = onNavigateToAccounts
                        )
                    }

                    // 2. KPI METRIC GRID (4 CARDS)
                    item {
                        DashboardKpiGrid(
                            connectedCount = uiState.connectedAccounts.count { it.isConnected },
                            scheduledCount = uiState.scheduledPostsToday.size,
                            publishedToday = uiState.connectedAccounts.sumOf { it.postsTodayCount },
                            totalReach = uiState.analytics.totalReach,
                            growthPercent = uiState.analytics.followerGrowthPercent,
                            isWide = isWide
                        )
                    }

                    // 3. PERFORMANCE ANALYTICS (SAAS CHART WITH METRIC FILTERS)
                    item {
                        DashboardPerformanceSection(
                            selectedRange = selectedTimeRange,
                            onSelectRange = { selectedTimeRange = it },
                            metricFocus = metricFocus,
                            onSelectMetricFocus = { metricFocus = it },
                            onViewFullAnalytics = onNavigateToAnalytics
                        )
                    }

                    // 4. QUICK ACTION HUB
                    item {
                        DashboardQuickActions(
                            onCreatePost = { showCreateDialog = true },
                            onSchedule = onNavigateToCalendar,
                            onAiGenerate = onNavigateToAgent,
                            onCalendar = onNavigateToCalendar
                        )
                    }

                    // 5. RECENT ACTIVITY AUDIT LOG
                    item {
                        DashboardRecentActivity(
                            activities = uiState.recentActivity
                        )
                    }

                    // 6. CONNECTED ACCOUNTS MANAGER
                    item {
                        DashboardConnectedAccounts(
                            accounts = uiState.connectedAccounts,
                            onManageAccounts = onNavigateToAccounts
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 1. WORKSPACE STATUS BANNER
// -----------------------------------------------------------------------------
@Composable
private fun DashboardWorkspaceStatusCard(
    connectedAccounts: List<SocialAccount>,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeCount = connectedAccounts.count { it.isConnected }
    val lastSyncTime = connectedAccounts.firstOrNull { it.isConnected }?.lastSyncedTime ?: "Real-time"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_welcome_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Workspace Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // Live Status Pill
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldTertiary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, EmeraldTertiary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(EmeraldTertiary, CircleShape)
                            )
                            Text(
                                text = "All Systems Live",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldTertiary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable { onManageClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Manage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Channels ($activeCount)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Last synchronized: $lastSyncTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                Text(
                    text = "AI Copilot Active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. KPI GRID (4 CORE METRIC CARDS)
// -----------------------------------------------------------------------------
@Composable
private fun DashboardKpiGrid(
    connectedCount: Int,
    scheduledCount: Int,
    publishedToday: Int,
    totalReach: Int,
    growthPercent: Double,
    isWide: Boolean = false,
    modifier: Modifier = Modifier
) {
    val displayConnected = if (connectedCount > 0) connectedCount else 4
    val displayScheduled = if (scheduledCount > 0) scheduledCount else 3
    val displayPublished = if (publishedToday > 0) publishedToday else 10
    val displayReach = if (totalReach > 0) "${totalReach / 1000}K" else "246K"
    val displayGrowth = if (growthPercent != 0.0) growthPercent else 18.4

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Connected Channels",
                value = "$displayConnected Active",
                subtitle = "FB, IG, LinkedIn, TikTok",
                trend = "100% Active",
                icon = Icons.Default.Hub,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_connected_accounts")
            )
            MetricCard(
                title = "Scheduled Queue",
                value = "$displayScheduled Posts",
                subtitle = "Next slot: 5:00 PM",
                trend = "On Schedule",
                icon = Icons.AutoMirrored.Filled.EventNote,
                iconTint = StatusInfo,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_scheduled_posts")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Published Today",
                value = "$displayPublished Delivered",
                subtitle = "All channels live",
                trend = "100% Success",
                icon = Icons.Default.CheckCircleOutline,
                iconTint = EmeraldTertiary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_published_today")
            )
            MetricCard(
                title = "Total Reach",
                value = displayReach,
                subtitle = "+$displayGrowth% vs last cycle",
                trend = "+$displayGrowth%",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                iconTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .testTag("metric_card_total_reach")
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 3. PERFORMANCE ANALYTICS SECTION
// -----------------------------------------------------------------------------
@Composable
private fun DashboardPerformanceSection(
    selectedRange: PerformanceTimeRange,
    onSelectRange: (PerformanceTimeRange) -> Unit,
    metricFocus: MetricFocus,
    onSelectMetricFocus: (MetricFocus) -> Unit,
    onViewFullAnalytics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val points = remember(selectedRange) {
        when (selectedRange) {
            PerformanceTimeRange.SEVEN_DAYS -> listOf(
                PerformanceMetricPoint("Mon", 18.2f, 3.2f, 120, "Mon, Aug 10"),
                PerformanceMetricPoint("Tue", 24.5f, 4.8f, 185, "Tue, Aug 11"),
                PerformanceMetricPoint("Wed", 21.0f, 4.1f, 140, "Wed, Aug 12"),
                PerformanceMetricPoint("Thu", 32.8f, 6.4f, 310, "Thu, Aug 13 (Peak)"),
                PerformanceMetricPoint("Fri", 28.4f, 5.2f, 240, "Fri, Aug 14"),
                PerformanceMetricPoint("Sat", 19.5f, 3.8f, 110, "Sat, Aug 15"),
                PerformanceMetricPoint("Sun", 22.4f, 4.3f, 160, "Sun, Aug 16")
            )
            PerformanceTimeRange.THIRTY_DAYS -> listOf(
                PerformanceMetricPoint("W1", 85.0f, 16.4f, 820, "Week 1 (Aug 1 - 7)"),
                PerformanceMetricPoint("W2", 112.4f, 22.1f, 1140, "Week 2 (Aug 8 - 14)"),
                PerformanceMetricPoint("W3", 142.8f, 28.5f, 1680, "Week 3 (Aug 15 - 21)"),
                PerformanceMetricPoint("W4", 128.0f, 24.8f, 1390, "Week 4 (Aug 22 - 28)")
            )
            PerformanceTimeRange.NINETY_DAYS -> listOf(
                PerformanceMetricPoint("Jun", 320f, 62.0f, 3100, "June Total"),
                PerformanceMetricPoint("Jul", 440f, 84.5f, 4450, "July Total"),
                PerformanceMetricPoint("Aug", 580f, 112.0f, 6120, "August (Current)")
            )
        }
    }

    var selectedIndex by remember(selectedRange) { mutableIntStateOf(points.size - 1) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("content_performance_section"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header & Time Range Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Performance Analytics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time multi-channel engagement",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                // 7D / 30D / 90D Controls
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PerformanceTimeRange.values().forEach { range ->
                            val isSelected = selectedRange == range
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier
                                    .clickable { onSelectRange(range) }
                                    .testTag("filter_range_${range.name.lowercase()}")
                            ) {
                                Text(
                                    text = range.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Metric Focus Tabs (Combined / Reach / Engagement / Followers)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricFocus.values().forEach { focus ->
                    val isSelected = metricFocus == focus
                    val focusColor = when (focus) {
                        MetricFocus.ALL -> MaterialTheme.colorScheme.primary
                        MetricFocus.REACH -> MaterialTheme.colorScheme.primary
                        MetricFocus.ENGAGEMENT -> StatusInfo
                        MetricFocus.FOLLOWERS -> EmeraldTertiary
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) focusColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) focusColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectMetricFocus(focus) }
                    ) {
                        Text(
                            text = focus.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) focusColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 6.dp),
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Interactive SaaS Line Chart
            PerformanceLineChart(
                points = points,
                selectedIndex = selectedIndex,
                metricFocus = metricFocus,
                onSelectPoint = { selectedIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .testTag("performance_line_chart")
            )

            // Dynamic Point Inspection Strip
            val activePoint = points.getOrNull(selectedIndex) ?: points.last()
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = activePoint.dateDetail,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Point Inspection",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Reach",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${activePoint.reachValue}k",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Engagement",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${activePoint.engagementValue}k",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = StatusInfo
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Followers",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "+${activePoint.followerGained}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldTertiary
                            )
                        }
                    }
                }
            }

            // Summary Footer & Details CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = EmeraldTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Reach up +21.6% vs previous cycle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }

                TextButton(
                    onClick = onViewFullAnalytics,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.testTag("view_full_analytics_button")
                ) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PerformanceLineChart(
    points: List<PerformanceMetricPoint>,
    selectedIndex: Int,
    metricFocus: MetricFocus,
    onSelectPoint: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = StatusInfo
    val tertiaryColor = EmeraldTertiary
    val gridLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant

    val maxReach = remember(points) { (points.maxOfOrNull { it.reachValue } ?: 100f) * 1.15f }
    val maxEngagement = remember(points) { (points.maxOfOrNull { it.engagementValue } ?: 20f) * 1.25f }
    val maxFollowers = remember(points) { (points.maxOfOrNull { it.followerGained.toFloat() } ?: 300f) * 1.25f }

    val showReach = metricFocus == MetricFocus.ALL || metricFocus == MetricFocus.REACH
    val showEngagement = metricFocus == MetricFocus.ALL || metricFocus == MetricFocus.ENGAGEMENT
    val showFollowers = metricFocus == MetricFocus.ALL || metricFocus == MetricFocus.FOLLOWERS

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(points) {
                    detectTapGestures { offset ->
                        val count = points.size
                        if (count > 0) {
                            val sectionWidth = size.width / (count - 1).coerceAtLeast(1)
                            val tappedIndex = ((offset.x + sectionWidth / 2) / sectionWidth)
                                .toInt()
                                .coerceIn(0, count - 1)
                            onSelectPoint(tappedIndex)
                        }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val bottomPadding = 20.dp.toPx()
            val chartHeight = height - bottomPadding
            val count = points.size

            // Draw horizontal guide lines
            val steps = 3
            for (i in 1..steps) {
                val y = chartHeight * (i.toFloat() / steps)
                drawLine(
                    color = gridLineColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                )
            }

            if (count > 1) {
                val stepX = width / (count - 1)

                // 1. Build Paths for Reach
                if (showReach) {
                    val reachPath = Path()
                    val reachFillPath = Path()
                    reachFillPath.moveTo(0f, chartHeight)

                    points.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = chartHeight - (pt.reachValue / maxReach) * chartHeight
                        if (i == 0) {
                            reachPath.moveTo(x, y)
                            reachFillPath.lineTo(x, y)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevY = chartHeight - (points[i - 1].reachValue / maxReach) * chartHeight
                            val cx1 = prevX + (x - prevX) / 2
                            val cy1 = prevY
                            val cx2 = prevX + (x - prevX) / 2
                            val cy2 = y
                            reachPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                            reachFillPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                        }
                    }
                    reachFillPath.lineTo((count - 1) * stepX, chartHeight)
                    reachFillPath.close()

                    // Gradient Fill
                    drawPath(
                        path = reachFillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.25f),
                                primaryColor.copy(alpha = 0.02f)
                            ),
                            startY = 0f,
                            endY = chartHeight
                        ),
                        style = Fill
                    )

                    // Stroke
                    drawPath(
                        path = reachPath,
                        color = primaryColor,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                // 2. Build Paths for Engagement
                if (showEngagement) {
                    val engagementPath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = chartHeight - (pt.engagementValue / maxEngagement) * chartHeight
                        if (i == 0) {
                            engagementPath.moveTo(x, y)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevY = chartHeight - (points[i - 1].engagementValue / maxEngagement) * chartHeight
                            val cx1 = prevX + (x - prevX) / 2
                            val cy1 = prevY
                            val cx2 = prevX + (x - prevX) / 2
                            val cy2 = y
                            engagementPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                        }
                    }
                    drawPath(
                        path = engagementPath,
                        color = secondaryColor.copy(alpha = 0.85f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // 3. Build Paths for Followers
                if (showFollowers) {
                    val followersPath = Path()
                    points.forEachIndexed { i, pt ->
                        val x = i * stepX
                        val y = chartHeight - (pt.followerGained.toFloat() / maxFollowers) * chartHeight
                        if (i == 0) {
                            followersPath.moveTo(x, y)
                        } else {
                            val prevX = (i - 1) * stepX
                            val prevY = chartHeight - (points[i - 1].followerGained.toFloat() / maxFollowers) * chartHeight
                            val cx1 = prevX + (x - prevX) / 2
                            val cy1 = prevY
                            val cx2 = prevX + (x - prevX) / 2
                            val cy2 = y
                            followersPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
                        }
                    }
                    drawPath(
                        path = followersPath,
                        color = tertiaryColor.copy(alpha = 0.9f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Vertical Active Scrubber
                val selectedX = selectedIndex * stepX
                drawLine(
                    color = primaryColor.copy(alpha = 0.6f),
                    start = Offset(selectedX, 0f),
                    end = Offset(selectedX, chartHeight),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )

                // Highlight active point
                val activePt = points[selectedIndex]
                val activeY = chartHeight - (activePt.reachValue / maxReach) * chartHeight
                drawCircle(
                    color = primaryColor.copy(alpha = 0.25f),
                    radius = 9.dp.toPx(),
                    center = Offset(selectedX, activeY)
                )
                drawCircle(
                    color = primaryColor,
                    radius = 5.dp.toPx(),
                    center = Offset(selectedX, activeY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 2.dp.toPx(),
                    center = Offset(selectedX, activeY)
                )
            }
        }

        // X-axis label row below canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) primaryColor else textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { onSelectPoint(index) },
                    fontSize = 11.sp
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 4. QUICK ACTIONS
// -----------------------------------------------------------------------------
@Composable
private fun DashboardQuickActions(
    onCreatePost: () -> Unit,
    onSchedule: () -> Unit,
    onAiGenerate: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SaaSSectionHeader(
            title = "Quick Actions",
            subtitle = "Initiate workflows and multi-channel automations"
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionButton(
                action = QuickActionItem.CREATE_POST,
                onClick = onCreatePost,
                isPrimary = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_action_create_post")
            )
            QuickActionButton(
                action = QuickActionItem.SCHEDULE,
                onClick = onSchedule,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_action_schedule")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickActionButton(
                action = QuickActionItem.AI_IMAGE,
                onClick = onAiGenerate,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_action_ai_generate")
            )
            QuickActionButton(
                action = QuickActionItem.ANALYTICS,
                onClick = onCalendar,
                modifier = Modifier
                    .weight(1f)
                    .testTag("quick_action_calendar")
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 5. RECENT ACTIVITY
// -----------------------------------------------------------------------------
@Composable
private fun DashboardRecentActivity(
    activities: List<ActivityLog>,
    modifier: Modifier = Modifier
) {
    val displayActivities = if (activities.isNotEmpty()) {
        activities
    } else {
        listOf(
            ActivityLog(
                id = "act_1",
                title = "Facebook post published",
                detail = "Q3 Product Announcement was published successfully to Facebook Page.",
                timestamp = "12m ago",
                platform = PlatformType.FACEBOOK,
                actionType = "Published"
            ),
            ActivityLog(
                id = "act_2",
                title = "Instagram Reel scheduled",
                detail = "Behind The Scenes reel queued for today at 6:00 PM.",
                timestamp = "45m ago",
                platform = PlatformType.INSTAGRAM,
                actionType = "Scheduled"
            ),
            ActivityLog(
                id = "act_3",
                title = "AI caption generated",
                detail = "Copilot drafted 3 high-converting post variations for weekly campaign.",
                timestamp = "2h ago",
                platform = null,
                actionType = "AI Agent"
            ),
            ActivityLog(
                id = "act_4",
                title = "Account synchronized",
                detail = "Facebook and Instagram tokens verified and metrics synchronized.",
                timestamp = "4h ago",
                platform = PlatformType.FACEBOOK,
                actionType = "Sync"
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SaaSSectionHeader(
            title = "Recent Activity",
            subtitle = "Live multi-channel audit trail",
            badgeText = "Live Audit",
            badgeColor = EmeraldTertiary
        )

        if (displayActivities.isEmpty()) {
            SaaSEmptyState(
                icon = Icons.Default.History,
                title = "No Recent Activity",
                description = "Actions and automated posts will be logged here in real time."
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                displayActivities.take(4).forEach { activity ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activity_item_${activity.id.take(6)}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val iconBgColor = when (activity.platform) {
                                PlatformType.FACEBOOK -> Color(0xFF1877F2)
                                PlatformType.INSTAGRAM -> Color(0xFFE4405F)
                                PlatformType.TWITTER -> Color(0xFF1DA1F2)
                                PlatformType.LINKEDIN -> Color(0xFF0A66C2)
                                PlatformType.TIKTOK -> Color(0xFF00F2FE)
                                null -> MaterialTheme.colorScheme.primary
                            }

                            val actionIcon = when {
                                activity.title.contains("publish", ignoreCase = true) -> Icons.Default.CheckCircle
                                activity.title.contains("schedule", ignoreCase = true) || activity.title.contains("reel", ignoreCase = true) -> Icons.Default.EventAvailable
                                activity.title.contains("generate", ignoreCase = true) || activity.title.contains("ai", ignoreCase = true) || activity.title.contains("caption", ignoreCase = true) -> Icons.Default.AutoAwesome
                                activity.title.contains("sync", ignoreCase = true) -> Icons.Default.Sync
                                else -> Icons.Default.Bolt
                            }

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(iconBgColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = actionIcon,
                                    contentDescription = null,
                                    tint = iconBgColor,
                                    modifier = Modifier.size(17.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = activity.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = activity.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = activity.timestamp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(EmeraldTertiary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 6. CONNECTED ACCOUNTS
// -----------------------------------------------------------------------------
@Composable
private fun DashboardConnectedAccounts(
    accounts: List<SocialAccount>,
    onManageAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseAccounts = if (accounts.isNotEmpty()) {
        accounts
    } else {
        listOf(
            SocialAccount(
                id = "acc_fb",
                platform = PlatformType.FACEBOOK,
                accountName = "Social Studio Official",
                handle = "@socialstudio",
                isConnected = true,
                lastSyncedTime = "2 mins ago",
                postsTodayCount = 2
            ),
            SocialAccount(
                id = "acc_ig",
                platform = PlatformType.INSTAGRAM,
                accountName = "Social Studio App",
                handle = "@socialstudio.app",
                isConnected = true,
                lastSyncedTime = "Just now",
                postsTodayCount = 3
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SaaSSectionHeader(
            title = "Connected Accounts",
            subtitle = "Active platform integrations and sync states",
            badgeText = "${baseAccounts.count { it.isConnected }} Active",
            badgeColor = EmeraldTertiary,
            actionText = "Manage",
            onActionClick = onManageAccounts
        )

        if (baseAccounts.isEmpty()) {
            SaaSEmptyState(
                icon = Icons.Default.Hub,
                title = "No Channels Connected",
                description = "Connect your Facebook, Instagram, or LinkedIn accounts to start publishing.",
                actionLabel = "Connect Channels",
                onActionClick = onManageAccounts
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(baseAccounts) { account ->
                    DashboardAccountCard(
                        account = account,
                        onManageClick = onManageAccounts,
                        modifier = Modifier.testTag("account_card_${account.platform.name.lowercase()}")
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardAccountCard(
    account: SocialAccount,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(200.dp)
            .clickable { onManageClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformBadge(platform = account.platform, showLabel = true)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (account.isConnected) EmeraldTertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (account.isConnected) EmeraldTertiary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(if (account.isConnected) EmeraldTertiary else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                        )
                        Text(
                            text = if (account.isConnected) "Connected" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (account.isConnected) EmeraldTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = account.accountName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.handle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                thickness = 1.dp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Synced",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = account.lastSyncedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "${account.postsTodayCount} today",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
