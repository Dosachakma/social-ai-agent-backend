package com.example.ui.screens.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.GlassCard
import com.example.ui.components.PlatformBadge
import com.example.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissUserMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openScheduleDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = "Schedule Post") },
                text = { Text("Schedule Post") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar with Title and Mock Mode Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Content Calendar",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Demo Workspace",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "• ${uiState.selectedTimezone}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // View Mode Tabs (Day, Week, Month)
                TabRow(
                    selectedTabIndex = uiState.viewMode.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    CalendarViewMode.entries.forEach { mode ->
                        Tab(
                            selected = uiState.viewMode == mode,
                            onClick = { viewModel.setViewMode(mode) },
                            text = { Text(mode.label, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Platform Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = uiState.filterPlatform == null,
                            onClick = { viewModel.filterByPlatform(null) },
                            label = { Text("All Platforms") }
                        )
                    }
                    items(PlatformType.entries.toTypedArray()) { platform ->
                        FilterChip(
                            selected = uiState.filterPlatform == platform,
                            onClick = { viewModel.filterByPlatform(if (uiState.filterPlatform == platform) null else platform) },
                            label = { Text(platform.displayName) },
                            leadingIcon = { PlatformBadge(platform = platform, showLabel = false) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Content View depending on ViewMode
                when (uiState.viewMode) {
                    CalendarViewMode.DAY -> DayViewSection(uiState = uiState, viewModel = viewModel)
                    CalendarViewMode.WEEK -> WeekViewSection(uiState = uiState, viewModel = viewModel)
                    CalendarViewMode.MONTH -> MonthViewSection(uiState = uiState, viewModel = viewModel)
                }
            }

            // Loading overlay
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    // Schedule Post Dialog
    if (uiState.showScheduleDialog) {
        SchedulePostDialog(uiState = uiState, viewModel = viewModel)
    }

    // Post Details Modal Dialog
    uiState.selectedPostDetail?.let { post ->
        PostDetailsDialog(post = post, viewModel = viewModel)
    }
}

@Composable
fun DayViewSection(
    uiState: CalendarUiState,
    viewModel: CalendarViewModel
) {
    val filteredPosts = remember(uiState.scheduledPosts, uiState.filterPlatform, uiState.selectedDay) {
        uiState.scheduledPosts.filter { post ->
            (uiState.filterPlatform == null || post.targetPlatforms.contains(uiState.filterPlatform))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Timeline Queue - Aug ${uiState.selectedDay}, 2026",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (filteredPosts.isEmpty()) {
            EmptyQueuePlaceholder()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredPosts) { post ->
                    CalendarPostCard(post = post, onClick = { viewModel.selectPostForDetail(post) })
                }
            }
        }
    }
}

@Composable
fun WeekViewSection(
    uiState: CalendarUiState,
    viewModel: CalendarViewModel
) {
    val days = listOf("Mon 12", "Tue 13", "Wed 14", "Thu 15", "Fri 16", "Sat 17", "Sun 18")

    val filteredPosts = remember(uiState.scheduledPosts, uiState.filterPlatform, uiState.selectedDay) {
        uiState.scheduledPosts.filter { post ->
            val matchesPlatform = uiState.filterPlatform == null || post.targetPlatforms.contains(uiState.filterPlatform)
            val matchesDay = post.scheduledTime.contains(uiState.selectedDay, ignoreCase = true) ||
                    (post.scheduledAt != null && post.scheduledAt.contains(uiState.selectedDay))
            matchesPlatform && (matchesDay || uiState.selectedDay.isBlank())
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Week Days Selector Row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(days) { dayLabel ->
                val dayNum = dayLabel.drop(4)
                val isSelected = uiState.selectedDay == dayNum || dayLabel.startsWith(uiState.selectedDay.take(3))
                val postCount = uiState.scheduledPosts.count { 
                    it.scheduledTime.contains(dayNum) || (it.scheduledAt != null && it.scheduledAt.contains(dayNum))
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { viewModel.selectDay(dayNum) }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dayLabel.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayNum,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                        if (postCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Scheduled Queue for Day ${uiState.selectedDay}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredPosts.isEmpty()) {
            EmptyQueuePlaceholder()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredPosts) { post ->
                    CalendarPostCard(post = post, onClick = { viewModel.selectPostForDetail(post) })
                }
            }
        }
    }
}

@Composable
fun MonthViewSection(
    uiState: CalendarUiState,
    viewModel: CalendarViewModel
) {
    val daysInMonth = (1..31).toList()
    val selectedDayInt = uiState.selectedDay.toIntOrNull() ?: 12

    val filteredPosts = remember(uiState.scheduledPosts, uiState.filterPlatform, uiState.selectedDay) {
        uiState.scheduledPosts.filter { post ->
            val matchesPlatform = uiState.filterPlatform == null || post.targetPlatforms.contains(uiState.filterPlatform)
            val matchesDay = post.scheduledTime.contains(uiState.selectedDay) ||
                    (post.scheduledAt != null && post.scheduledAt.contains("-${uiState.selectedDay.padStart(2, '0')}"))
            matchesPlatform && matchesDay
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${uiState.selectedMonth} Overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Month Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(daysInMonth) { dayNum ->
                val dayStr = dayNum.toString()
                val isSelected = dayNum == selectedDayInt
                val postCount = uiState.scheduledPosts.count {
                    it.scheduledTime.contains(dayStr) || (it.scheduledAt != null && it.scheduledAt.contains("-$dayStr"))
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { viewModel.selectDay(dayStr) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayStr,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            if (postCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = postCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Posts for August $selectedDayInt, 2026",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredPosts.isEmpty()) {
            EmptyQueuePlaceholder()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredPosts) { post ->
                    CalendarPostCard(post = post, onClick = { viewModel.selectPostForDetail(post) })
                }
            }
        }
    }
}

@Composable
fun CalendarPostCard(
    post: SocialPost,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    post.targetPlatforms.forEach { platform ->
                        PlatformBadge(platform = platform, showLabel = false)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (post.requireApproval) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (post.approvalState == ActionApprovalState.APPROVED)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = post.approvalState.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (post.approvalState == ActionApprovalState.APPROVED)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    StatusChip(status = post.status)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = post.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = post.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!post.mediaUrl.isNullBalnk()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Media Attached",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Media attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${post.scheduledTime} (${post.timezone.take(15)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Repeat: ${post.repeatOption.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyQueuePlaceholder() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.EventNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No posts scheduled for this slot.",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap '+ Schedule Post' to queue content across platforms.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SchedulePostDialog(
    uiState: CalendarUiState,
    viewModel: CalendarViewModel
) {
    AlertDialog(
        onDismissRequest = { viewModel.closeScheduleDialog() },
        title = {
            Text(
                text = "Schedule Social Post",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = uiState.formTitle,
                    onValueChange = { viewModel.updateFormTitle(it) },
                    label = { Text("Post Title / Topic") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.formContent,
                    onValueChange = { viewModel.updateFormContent(it) },
                    label = { Text("Post Content / Caption") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                Text(
                    text = "Select Platforms:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlatformType.entries.forEach { platform ->
                        val isSelected = uiState.formPlatforms.contains(platform)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.toggleFormPlatform(platform) },
                            label = { Text(platform.name.take(3)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = uiState.formMediaUrl,
                    onValueChange = { viewModel.updateFormMediaUrl(it) },
                    label = { Text("Media Image URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.formScheduledDate,
                        onValueChange = { viewModel.updateFormScheduledDate(it) },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = uiState.formScheduledTime,
                        onValueChange = { viewModel.updateFormScheduledTime(it) },
                        label = { Text("Time (HH:MM)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = uiState.formTimezone,
                    onValueChange = { viewModel.updateFormTimezone(it) },
                    label = { Text("Timezone") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Require User Approval",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Must be approved before worker executes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.formRequireApproval,
                        onCheckedChange = { viewModel.updateFormRequireApproval(it) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { viewModel.submitSchedulePost() }) {
                Text("Schedule Post")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { viewModel.closeScheduleDialog() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PostDetailsDialog(
    post: SocialPost,
    viewModel: CalendarViewModel
) {
    AlertDialog(
        onDismissRequest = { viewModel.selectPostForDetail(null) },
        title = {
            Text(
                text = post.title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusChip(status = post.status)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "Approval: ${post.approvalState.label}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Divider()

                Text(
                    text = "Scheduled Time:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${post.scheduledTime} (${post.timezone})",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "Platforms & Execution Status:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                post.targetPlatforms.forEach { platform ->
                    val result = post.platformPublishResults.find { it.platform == platform }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlatformBadge(platform = platform, showLabel = true)
                            val statusText = result?.status?.label ?: "Pending"
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (result?.status == ActionApprovalState.SUCCESS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (post.approvalState == ActionApprovalState.AWAITING_APPROVAL || post.approvalState == ActionApprovalState.PROPOSED) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ This post requires user approval before execution.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (post.approvalState == ActionApprovalState.AWAITING_APPROVAL || post.approvalState == ActionApprovalState.PROPOSED) {
                    Button(onClick = { viewModel.approvePost(post.id) }) {
                        Text("Approve")
                    }
                } else {
                    Button(onClick = { viewModel.triggerMockExecution(post.id) }) {
                        Text("Execute Now (Mock)")
                    }
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = { viewModel.cancelPost(post.id) }) {
                Text("Cancel Post")
            }
        }
    )
}

private fun String?.isNullBalnk(): Boolean = this == null || this.isBlank()
