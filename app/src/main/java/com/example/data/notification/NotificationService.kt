package com.example.data.notification

import com.example.data.model.PlatformType
import com.example.data.model.SocialPost
import java.util.UUID

data class SchedulingNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: String = "Just now",
    val type: NotificationType
)

enum class NotificationType {
    SCHEDULED,
    APPROVAL_REQUIRED,
    PUBLISHED,
    FAILED
}

interface NotificationService {
    suspend fun sendNotification(notification: SchedulingNotification)
    suspend fun notifyPostScheduled(post: SocialPost)
    suspend fun notifyApprovalRequired(post: SocialPost)
    suspend fun notifyPostPublished(post: SocialPost, platformResults: Map<PlatformType, String>)
    suspend fun notifyPostFailed(post: SocialPost, error: String)
    fun getSentNotifications(): List<SchedulingNotification>
}

class MockNotificationService : NotificationService {
    private val notifications = mutableListOf<SchedulingNotification>()

    override suspend fun sendNotification(notification: SchedulingNotification) {
        notifications.add(notification)
    }

    override suspend fun notifyPostScheduled(post: SocialPost) {
        sendNotification(
            SchedulingNotification(
                title = "Post Scheduled",
                message = "'${post.title}' scheduled for ${post.scheduledTime} (${post.timezone}).",
                type = NotificationType.SCHEDULED
            )
        )
    }

    override suspend fun notifyApprovalRequired(post: SocialPost) {
        sendNotification(
            SchedulingNotification(
                title = "Approval Required",
                message = "'${post.title}' requires user approval before publishing.",
                type = NotificationType.APPROVAL_REQUIRED
            )
        )
    }

    override suspend fun notifyPostPublished(post: SocialPost, platformResults: Map<PlatformType, String>) {
        val platformsStr = platformResults.keys.joinToString { it.displayName }
        sendNotification(
            SchedulingNotification(
                title = "Post Published (Mock)",
                message = "'${post.title}' successfully published to $platformsStr. Mock execution completed.",
                type = NotificationType.PUBLISHED
            )
        )
    }

    override suspend fun notifyPostFailed(post: SocialPost, error: String) {
        sendNotification(
            SchedulingNotification(
                title = "Publishing Failed",
                message = "Failed to publish '${post.title}': $error",
                type = NotificationType.FAILED
            )
        )
    }

    override fun getSentNotifications(): List<SchedulingNotification> {
        return notifications.toList()
    }
}
