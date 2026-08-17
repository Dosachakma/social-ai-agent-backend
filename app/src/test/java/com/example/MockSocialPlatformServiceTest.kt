package com.example

import com.example.data.remote.FacebookPlatformService
import com.example.data.remote.SocialPlatformRegistry
import com.example.data.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MockSocialPlatformServiceTest {

    @Test
    fun testPlatformRegistryRetrieval() {
        val fbService = SocialPlatformRegistry.getService(PlatformType.FACEBOOK)
        assertEquals(PlatformType.FACEBOOK, fbService.platform)
        assertEquals(ExecutionEnvironment.MOCK, fbService.environment)
    }

    @Test
    fun testPublishPostNeverClaimsRealPublishInMockMode() = runBlocking {
        val service = FacebookPlatformService()
        val result = service.publishPost("post_123")

        assertTrue(result.isSuccess)
        val post = result.getOrNull()
        assertNotNull(post)
        assertEquals(PostStatus.PUBLISHED, post!!.status)
        assertTrue(post.content.contains("Mock execution completed"))
        assertTrue(post.isDemoData)
    }
}
