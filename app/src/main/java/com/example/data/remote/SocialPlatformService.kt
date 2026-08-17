package com.example.data.remote

import com.example.data.model.*

/**
 * Platform-agnostic interface for social media platform interactions.
 * Abstracts direct API integration for Facebook, Instagram, Twitter, LinkedIn, TikTok.
 */
interface SocialPlatformService {
    val platform: PlatformType
    val environment: ExecutionEnvironment

    suspend fun connect(account: SocialAccount): AppResult<SocialAccount>
    suspend fun disconnect(accountId: String): AppResult<Boolean>
    suspend fun getAccount(accountId: String): AppResult<SocialAccount>
    
    suspend fun createPost(post: SocialPost): AppResult<SocialPost>
    suspend fun publishPost(postId: String): AppResult<SocialPost>
    suspend fun schedulePost(post: SocialPost, scheduledTime: String): AppResult<SocialPost>
    
    suspend fun getComments(postId: String): AppResult<List<SocialComment>>
    suspend fun replyToComment(commentId: String, replyText: String): AppResult<Boolean>
    suspend fun deleteComment(commentId: String): AppResult<Boolean>
    
    suspend fun getMessages(accountId: String): AppResult<List<SocialMessage>>
    suspend fun replyToMessage(messageId: String, replyText: String): AppResult<Boolean>
    
    suspend fun getAnalytics(accountId: String): AppResult<AnalyticsData>
}
