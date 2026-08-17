package com.example.data.repository

import com.example.data.model.SocialPost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class MockPostSchedulerService : PostSchedulerService {

    private val scheduledSlots = MutableStateFlow<Map<String, List<SocialPost>>>(emptyMap())

    override suspend fun schedulePost(post: SocialPost, timeInMillis: Long): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun cancelScheduledPost(postId: String): Result<Boolean> {
        return Result.success(true)
    }

    override suspend fun getScheduledCalendarSlots(): Flow<Map<String, List<SocialPost>>> {
        return scheduledSlots
    }
}
