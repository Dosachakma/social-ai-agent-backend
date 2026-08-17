package com.example.ui.screens.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.config.MetaConfigurationStatus
import com.example.data.config.MetaConfigurationValidator
import com.example.data.config.SecurityConfig
import com.example.data.model.*
import com.example.data.remote.RealLinkedInOAuthService
import com.example.data.remote.RealMetaOAuthService
import com.example.data.remote.RealTwitterOAuthService
import com.example.data.repository.SocialMediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountsUiState(
    val accounts: List<SocialAccount> = emptyList(),
    val isLoading: Boolean = false,
    val selectedAccountForDetails: SocialAccount? = null,
    val accountToDisconnect: SocialAccount? = null,
    val connectProviderDialog: OAuthProvider? = null,
    val selectedExecutionEnvironment: ExecutionEnvironment = ExecutionEnvironment.MOCK,
    val metaConfigStatus: MetaConfigurationStatus = MetaConfigurationValidator.validate(environment = ExecutionEnvironment.MOCK),
    val isConnecting: Boolean = false,
    val statusMessage: String? = null,
    val oauthLaunchUrl: String? = null
)

class AccountsViewModel(
    private val repository: SocialMediaRepository,
    private val realMetaOAuthService: RealMetaOAuthService = RealMetaOAuthService(),
    private val realTwitterOAuthService: RealTwitterOAuthService = RealTwitterOAuthService(),
    private val realLinkedInOAuthService: RealLinkedInOAuthService = RealLinkedInOAuthService()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountsUiState(
            isLoading = true,
            metaConfigStatus = realMetaOAuthService.getConfigurationStatus(ExecutionEnvironment.MOCK)
        )
    )
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getConnectedAccounts().collect { list ->
                _uiState.update { it.copy(accounts = list, isLoading = false) }
            }
        }

        // Listen for OAuth Deep Link Callbacks from MainActivity
        viewModelScope.launch {
            com.example.data.remote.OAuthCallbackManager.callbacks.collect { payload ->
                handleOAuthCallback(payload)
            }
        }
    }

    fun setExecutionEnvironment(environment: ExecutionEnvironment) {
        val newConfigStatus = realMetaOAuthService.getConfigurationStatus(environment)
        _uiState.update {
            it.copy(
                selectedExecutionEnvironment = environment,
                metaConfigStatus = newConfigStatus
            )
        }
    }

    fun showAccountDetails(account: SocialAccount?) {
        _uiState.update { it.copy(selectedAccountForDetails = account) }
    }

    fun showDisconnectConfirmation(account: SocialAccount?) {
        _uiState.update { it.copy(accountToDisconnect = account) }
    }

    fun showConnectDialog(provider: OAuthProvider?) {
        _uiState.update { it.copy(connectProviderDialog = provider) }
    }

    fun clearOauthLaunchUrl() {
        _uiState.update { it.copy(oauthLaunchUrl = null) }
    }

    fun connectAccount(
        provider: OAuthProvider,
        code: String = "code_mock_auth",
        environment: ExecutionEnvironment = uiState.value.selectedExecutionEnvironment
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, connectProviderDialog = null) }

            if (environment != ExecutionEnvironment.MOCK) {
                when (provider) {
                    OAuthProvider.FACEBOOK, OAuthProvider.INSTAGRAM -> {
                        val configStatus = realMetaOAuthService.getConfigurationStatus(environment)
                        if (!configStatus.isReady) {
                            val errorDetails = configStatus.errors.joinToString(" | ")
                            _uiState.update {
                                it.copy(
                                    isConnecting = false,
                                    metaConfigStatus = configStatus,
                                    statusMessage = "LIVE CONFIGURATION REQUIRED: $errorDetails"
                                )
                            }
                            return@launch
                        }

                        val sessionRes = realMetaOAuthService.createOAuthSession(provider)
                        if (sessionRes is AppResult.Success) {
                            val session = sessionRes.data
                            val authUrl = realMetaOAuthService.config.generateAuthorizationUrl(session.state)
                            _uiState.update {
                                it.copy(
                                    isConnecting = true,
                                    oauthLaunchUrl = authUrl,
                                    statusMessage = "Redirecting to ${provider.displayName} OAuth..."
                                )
                            }
                        } else if (sessionRes is AppResult.Error) {
                            _uiState.update {
                                it.copy(
                                    isConnecting = false,
                                    statusMessage = "OAuth initialization failed: ${sessionRes.error.message}"
                                )
                            }
                        }
                    }

                    OAuthProvider.TWITTER -> {
                        val sessionRes = realTwitterOAuthService.createOAuthSession(provider)
                        if (sessionRes is AppResult.Success) {
                            val session = sessionRes.data
                            val authUrl = realTwitterOAuthService.config.generateAuthorizationUrl(session.state)
                            _uiState.update {
                                it.copy(
                                    isConnecting = true,
                                    oauthLaunchUrl = authUrl,
                                    statusMessage = "Redirecting to X / Twitter OAuth 2.0 PKCE..."
                                )
                            }
                        } else if (sessionRes is AppResult.Error) {
                            _uiState.update {
                                it.copy(
                                    isConnecting = false,
                                    statusMessage = "X / Twitter initialization failed: ${sessionRes.error.message}"
                                )
                            }
                        }
                    }

                    OAuthProvider.LINKEDIN -> {
                        val sessionRes = realLinkedInOAuthService.createOAuthSession(provider)
                        if (sessionRes is AppResult.Success) {
                            val session = sessionRes.data
                            val authUrl = realLinkedInOAuthService.config.generateAuthorizationUrl(session.state)
                            _uiState.update {
                                it.copy(
                                    isConnecting = true,
                                    oauthLaunchUrl = authUrl,
                                    statusMessage = "Redirecting to LinkedIn OAuth 2.0..."
                                )
                            }
                        } else if (sessionRes is AppResult.Error) {
                            _uiState.update {
                                it.copy(
                                    isConnecting = false,
                                    statusMessage = "LinkedIn initialization failed: ${sessionRes.error.message}"
                                )
                            }
                        }
                    }

                    else -> {
                        // Demo / other platform fallback
                        val result = repository.connectAccount(provider, code)
                        when (result) {
                            is AppResult.Success -> {
                                _uiState.update {
                                    it.copy(
                                        isConnecting = false,
                                        statusMessage = "Connected ${provider.displayName} account successfully."
                                    )
                                }
                            }
                            is AppResult.Error -> {
                                _uiState.update {
                                    it.copy(
                                        isConnecting = false,
                                        statusMessage = "Connection failed: ${result.error.message}"
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Demo Workspace Connection
                val result = repository.connectAccount(provider, code)
                when (result) {
                    is AppResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                statusMessage = "Connected ${provider.displayName} account successfully (Demo Workspace)."
                            )
                        }
                    }
                    is AppResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isConnecting = false,
                                statusMessage = "Connection failed: ${result.error.message}"
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Requirement 6: Connect callback processing to existing account state.
     * Dispatches callback to correct platform OAuth service based on state prefix or session.
     */
    fun handleOAuthCallback(payload: com.example.data.remote.OAuthCallbackPayload) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, oauthLaunchUrl = null) }

            val state = payload.state ?: ""
            val result = when {
                state.startsWith("tw_") -> realTwitterOAuthService.handleDeepLinkCallback(payload)
                state.startsWith("li_") -> realLinkedInOAuthService.handleDeepLinkCallback(payload)
                else -> realMetaOAuthService.handleDeepLinkCallback(payload)
            }

            when (result) {
                is AppResult.Success -> {
                    val connectedAccount = result.data
                    repository.saveConnectedAccount(connectedAccount)
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            statusMessage = "Connected ${connectedAccount.platform.displayName} account '${connectedAccount.accountName}' successfully!"
                        )
                    }
                }
                is AppResult.Error -> {
                    val err = result.error
                    val userFriendlyMsg = when (err.code) {
                        "USER_CANCELLED" -> "OAuth flow was cancelled by the user."
                        "ACCESS_DENIED" -> "Permissions request was denied by the user."
                        "TICKET_EXPIRED" -> "Authorization ticket expired. Please initiate connection again."
                        "TICKET_NOT_FOUND", "MISSING_TICKET" -> "Invalid or missing authorization ticket."
                        "INVALID_SESSION", "CSRF_ERROR" -> "Security verification failed (state mismatch / CSRF rejected)."
                        "BACKEND_NOT_CONFIGURED" -> "OAuth backend token exchange endpoint is not configured."
                        else -> "LIVE Connection Failed (${err.code}): ${err.message}"
                    }
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            statusMessage = userFriendlyMsg
                        )
                    }
                }
            }
        }
    }

    fun confirmDisconnect() {
        val account = uiState.value.accountToDisconnect ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(accountToDisconnect = null) }
            val result = repository.disconnectAccount(account.id)
            if (result is AppResult.Success) {
                _uiState.update {
                    it.copy(statusMessage = "Disconnected ${account.displayName} (${account.handle}). OAuth tokens revoked.")
                }
            } else if (result is AppResult.Error) {
                _uiState.update {
                    it.copy(statusMessage = "Failed to disconnect: ${result.error.message}")
                }
            }
        }
    }

    fun updateTokenStatus(accountId: String, tokenStatus: TokenStatus) {
        viewModelScope.launch {
            repository.updateTokenStatus(accountId, tokenStatus)
            _uiState.update {
                it.copy(
                    selectedAccountForDetails = null,
                    statusMessage = "Token status set to ${tokenStatus.displayName}."
                )
            }
        }
    }

    fun toggleConnection(accountId: String) {
        viewModelScope.launch {
            repository.toggleAccountConnection(accountId)
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
