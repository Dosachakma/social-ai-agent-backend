package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.remote.OAuthCallbackManager
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.SocialAiAgentTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthCallbackIntent(intent)
        setContent {
            SocialAiAgentTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BoxWithConstraints {
                        val isExpanded = maxWidth >= 600.dp
                        AppNavigation(isExpandedScreen = isExpanded)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallbackIntent(intent)
    }

    private fun handleOAuthCallbackIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        if (uri.scheme == "socialai" && uri.host == "auth" && uri.path == "/callback") {
            // Extract only status, ticket, state, error, error_code
            val status = uri.getQueryParameter("status") ?: "error"
            val state = uri.getQueryParameter("state")
            val error = uri.getQueryParameter("error")
            val errorCode = uri.getQueryParameter("error_code")
            val ticket = uri.getQueryParameter("ticket")

            // Safe debug log that never outputs raw ticket, code, or token
            Log.d(
                "OAuthCallback",
                "Received OAuth callback (status: $status, statePresent: ${!state.isNullOrBlank()}, hasTicket: ${!ticket.isNullOrBlank()}, errorCode: $errorCode)"
            )

            OAuthCallbackManager.dispatchCallback(
                status = status,
                ticket = ticket,
                state = state,
                error = error,
                errorCode = errorCode
            )
        }
    }
}

