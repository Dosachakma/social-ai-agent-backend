package com.example

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test

class PostModelValidationTest {

    @Test
    fun testSocialPostDefaultFields() {
        val post = SocialPost(
            title = "Test Announcement",
            content = "Exciting news dropping today!",
            targetPlatforms = listOf(PlatformType.FACEBOOK, PlatformType.INSTAGRAM),
            scheduledTime = "Today 5:00 PM",
            status = PostStatus.DRAFT
        )

        assertNotNull(post.id)
        assertEquals("ws_default", post.workspaceId)
        assertEquals(2, post.targetPlatforms.size)
        assertEquals("Exciting news dropping today!", post.content)
        assertEquals(ActionApprovalState.PROPOSED, post.approvalState)
        assertTrue(post.isDemoData)
    }
}
