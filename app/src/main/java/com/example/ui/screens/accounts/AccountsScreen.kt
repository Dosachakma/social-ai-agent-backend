package com.example.ui.screens.accounts

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.config.MetaConfigStatusLevel
import com.example.data.config.MetaConfigurationStatus
import com.example.data.model.*
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.GlassCard
import com.example.ui.components.PlatformBadge

@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(uiState.oauthLaunchUrl) {
        val url = uiState.oauthLaunchUrl
        if (!url.isNullOrBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
            viewModel.clearOauthLaunchUrl()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Social Accounts",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Manage OAuth connections & posting permissions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { viewModel.showConnectDialog(OAuthProvider.FACEBOOK) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connect")
                }
            }

            // Snackbar / Status message
            uiState.statusMessage?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearStatusMessage() }) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Requirement 10: Meta OAuth Configuration Diagnostics
            MetaConfigurationDiagnosticsCard(
                configStatus = uiState.metaConfigStatus,
                selectedEnvironment = uiState.selectedExecutionEnvironment,
                onEnvironmentChange = { env -> viewModel.setExecutionEnvironment(env) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(uiState.accounts) { account ->
                        AccountCard(
                            account = account,
                            onDetailsClick = { viewModel.showAccountDetails(account) },
                            onDisconnectClick = { viewModel.showDisconnectConfirmation(account) },
                            onConnectClick = {
                                val provider = when (account.platform) {
                                    PlatformType.FACEBOOK -> OAuthProvider.FACEBOOK
                                    PlatformType.INSTAGRAM -> OAuthProvider.INSTAGRAM
                                    PlatformType.TWITTER -> OAuthProvider.TWITTER
                                    PlatformType.LINKEDIN -> OAuthProvider.LINKEDIN
                                    PlatformType.TIKTOK -> OAuthProvider.TIKTOK
                                }
                                viewModel.showConnectDialog(provider)
                            }
                        )
                    }
                }
            }
        }

        // Account Details Dialog
        uiState.selectedAccountForDetails?.let { account ->
            AccountDetailsDialog(
                account = account,
                onDismiss = { viewModel.showAccountDetails(null) },
                onDisconnect = {
                    viewModel.showAccountDetails(null)
                    viewModel.showDisconnectConfirmation(account)
                },
                onSetTokenStatus = { tokenStatus ->
                    viewModel.updateTokenStatus(account.id, tokenStatus)
                }
            )
        }

        // Disconnect Confirmation Dialog
        uiState.accountToDisconnect?.let { account ->
            DisconnectConfirmationDialog(
                account = account,
                onConfirm = { viewModel.confirmDisconnect() },
                onDismiss = { viewModel.showDisconnectConfirmation(null) }
            )
        }

        // OAuth Connect Dialog
        uiState.connectProviderDialog?.let { provider ->
            ConnectOAuthDialog(
                provider = provider,
                currentEnvironment = uiState.selectedExecutionEnvironment,
                configStatus = uiState.metaConfigStatus,
                onEnvironmentChange = { env -> viewModel.setExecutionEnvironment(env) },
                onConfirm = { code, env -> viewModel.connectAccount(provider, code, env) },
                onDismiss = { viewModel.showConnectDialog(null) }
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: SocialAccount,
    onDetailsClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onConnectClick: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformBadge(platform = account.platform, showLabel = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionBadge(isConnected = account.isConnected)
                    if (account.tokenStatus != TokenStatus.VALID) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = account.tokenStatus.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = account.displayName,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = account.username,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Type: ${account.accountType.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Followers: ${account.followerCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync: ${account.lastSyncedTime}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (account.isConnected) {
                        OutlinedButton(
                            onClick = onDetailsClick,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Details")
                        }
                        Button(
                            onClick = onDisconnectClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Disconnect")
                        }
                    } else {
                        Button(
                            onClick = onConnectClick,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Connect Account")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountDetailsDialog(
    account: SocialAccount,
    onDismiss: () -> Unit,
    onDisconnect: () -> Unit,
    onSetTokenStatus: (TokenStatus) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformBadge(platform = account.platform, showLabel = true)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Account Metadata", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Name: ${account.displayName}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Handle: ${account.username}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Account Type: ${account.accountType.displayName}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Connection Status: ${account.connectionStatus.displayName}", style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Token Status: ${account.tokenStatus.displayName}", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Available Capabilities:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))

                SocialCapability.values().forEach { cap ->
                    val isAvailable = account.availableCapabilities.contains(cap)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isAvailable) "✓ " else "✗ ",
                            color = if (isAvailable) Color(0xFF2E7D32) else Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = cap.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(text = "Security Testing Tools:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onSetTokenStatus(TokenStatus.EXPIRED) }) {
                        Text("Simulate Expired Token", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(onClick = { onSetTokenStatus(TokenStatus.REVOKED) }) {
                        Text("Simulate Revoked", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text("Disconnect")
            }
        }
    )
}

@Composable
private fun DisconnectConfirmationDialog(
    account: SocialAccount,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disconnect Account?") },
        text = {
            Text(
                "Are you sure you want to disconnect ${account.displayName} (${account.username})?\n\n" +
                "• OAuth access tokens will be revoked and removed from secret storage.\n" +
                "• Scheduled posts targeting ${account.platform.displayName} will be paused.\n" +
                "• An audit log entry will be recorded."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            ) {
                Text("Disconnect Account")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MetaConfigurationDiagnosticsCard(
    configStatus: MetaConfigurationStatus,
    selectedEnvironment: ExecutionEnvironment,
    onEnvironmentChange: (ExecutionEnvironment) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Meta Configuration Diagnostics",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = when (configStatus.statusLevel) {
                        MetaConfigStatusLevel.READY -> Color(0xFF2E7D32)
                        MetaConfigStatusLevel.BACKEND_REQUIRED -> Color(0xFFE65100)
                        MetaConfigStatusLevel.INCOMPLETE -> MaterialTheme.colorScheme.error
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (configStatus.isReady && selectedEnvironment != ExecutionEnvironment.MOCK) "LIVE READY" else if (selectedEnvironment == ExecutionEnvironment.MOCK) "DEMO READY" else "LIVE CONFIGURATION REQUIRED",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedEnvironment == ExecutionEnvironment.MOCK,
                    onClick = { onEnvironmentChange(ExecutionEnvironment.MOCK) },
                    label = { Text("Demo") }
                )
                FilterChip(
                    selected = selectedEnvironment == ExecutionEnvironment.DEVELOPMENT,
                    onClick = { onEnvironmentChange(ExecutionEnvironment.DEVELOPMENT) },
                    label = { Text("Development") }
                )
                FilterChip(
                    selected = selectedEnvironment == ExecutionEnvironment.PRODUCTION || selectedEnvironment == ExecutionEnvironment.REAL,
                    onClick = { onEnvironmentChange(ExecutionEnvironment.PRODUCTION) },
                    label = { Text("Production") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DiagnosticRow(label = "Meta App ID", isOk = configStatus.appIdConfigured)
                DiagnosticRow(label = "Redirect URI (${configStatus.redirectUri})", isOk = configStatus.redirectUriConfigured)
                DiagnosticRow(label = "Server Token Exchange Backend", isOk = configStatus.backendConfigured)
                DiagnosticRow(label = "App Secret (Server-Side Protected)", isOk = configStatus.secretServerSide)
            }

            if (configStatus.errors.isNotEmpty() && selectedEnvironment != ExecutionEnvironment.MOCK) {
                Spacer(modifier = Modifier.height(8.dp))
                configStatus.errors.forEach { err ->
                    Text(
                        text = "• $err",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, isOk: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isOk) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isOk) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConnectOAuthDialog(
    provider: OAuthProvider,
    currentEnvironment: ExecutionEnvironment,
    configStatus: MetaConfigurationStatus,
    onEnvironmentChange: (ExecutionEnvironment) -> Unit,
    onConfirm: (code: String, environment: ExecutionEnvironment) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedAccountType by remember { mutableStateOf(if (provider == OAuthProvider.INSTAGRAM) "business" else "default") }
    var selectedEnv by remember { mutableStateOf(currentEnvironment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect ${provider.displayName}") },
        text = {
            Column {
                Text(
                    text = "Select Execution Environment:",
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedEnv == ExecutionEnvironment.MOCK,
                        onClick = {
                            selectedEnv = ExecutionEnvironment.MOCK
                            onEnvironmentChange(ExecutionEnvironment.MOCK)
                        },
                        label = { Text("Demo Workspace") }
                    )
                    FilterChip(
                        selected = selectedEnv == ExecutionEnvironment.DEVELOPMENT,
                        onClick = {
                            selectedEnv = ExecutionEnvironment.DEVELOPMENT
                            onEnvironmentChange(ExecutionEnvironment.DEVELOPMENT)
                        },
                        label = { Text("Development") }
                    )
                    FilterChip(
                        selected = selectedEnv == ExecutionEnvironment.PRODUCTION || selectedEnv == ExecutionEnvironment.REAL,
                        onClick = {
                            selectedEnv = ExecutionEnvironment.PRODUCTION
                            onEnvironmentChange(ExecutionEnvironment.PRODUCTION)
                        },
                        label = { Text("Production") }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (selectedEnv != ExecutionEnvironment.MOCK) {
                    if (!configStatus.isReady) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "LIVE CONFIGURATION REQUIRED",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                configStatus.errors.forEach { err ->
                                    Text(
                                        text = "• $err",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "LIVE Meta OAuth Mode (${selectedEnv.displayName})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "App ID and Backend Exchange service verified. Meta App Secret is strictly server-side.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Authorize connection to ${provider.displayName} in Demo Workspace.\nInteractive preview with pre-configured sample channels.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (provider == OAuthProvider.INSTAGRAM) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Select Instagram Account Type:", style = MaterialTheme.typography.titleSmall)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedAccountType == "business",
                            onClick = { selectedAccountType = "business" },
                            label = { Text("Business / Creator") }
                        )
                        FilterChip(
                            selected = selectedAccountType == "personal",
                            onClick = { selectedAccountType = "personal" },
                            label = { Text("Personal") }
                        )
                    }
                    if (selectedAccountType == "personal") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Note: Personal accounts lack automated post publishing capabilities per Meta API restrictions.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val code = if (selectedAccountType == "personal") "code_personal_auth" else "code_mock_auth"
                    onConfirm(code, selectedEnv)
                }
            ) {
                Text(
                    if (selectedEnv == ExecutionEnvironment.MOCK) "Authorise Connection"
                    else if (!configStatus.isReady) "LIVE CONFIGURATION REQUIRED"
                    else "Connect Live OAuth"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
