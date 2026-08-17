package com.example.data.security

import com.example.data.model.*

sealed class AccountValidationResult {
    object Allowed : AccountValidationResult()
    data class Blocked(val code: String, val message: String) : AccountValidationResult()

    val isAllowed: Boolean get() = this is Allowed
    val isBlocked: Boolean get() = this is Blocked
}

class AccountValidationEngine {

    fun validateActionExecution(
        account: SocialAccount?,
        requiredCapability: SocialCapability?,
        hasUserPermission: Boolean = true,
        isApproved: Boolean = true
    ): AccountValidationResult {
        // 1. User Permission Check
        if (!hasUserPermission) {
            return AccountValidationResult.Blocked(
                code = "PERMISSION_DENIED",
                message = "User permission missing or denied for this action."
            )
        }

        // 2. Account Existence Check
        if (account == null) {
            return AccountValidationResult.Blocked(
                code = "ACCOUNT_NOT_FOUND",
                message = "No account found for target social platform."
            )
        }

        // 3. Account Connection Check
        if (!account.isConnected || account.connectionStatus == ConnectionStatus.DISCONNECTED) {
            return AccountValidationResult.Blocked(
                code = "DISCONNECTED",
                message = "Account '${account.displayName}' is disconnected (Status: ${account.connectionStatus.displayName})."
            )
        }

        // 4. Token Status Check
        if (account.tokenStatus != TokenStatus.VALID || account.connectionStatus == ConnectionStatus.REAUTH_REQUIRED) {
            return AccountValidationResult.Blocked(
                code = if (account.tokenStatus == TokenStatus.EXPIRED) "EXPIRED_TOKEN" else "REAUTH_REQUIRED",
                message = "Token for account '${account.displayName}' is ${account.tokenStatus.displayName}. Reauthentication required."
            )
        }

        // 5. Capability Check
        if (requiredCapability != null && !account.availableCapabilities.contains(requiredCapability)) {
            return AccountValidationResult.Blocked(
                code = "MISSING_CAPABILITY",
                message = "Account '${account.displayName}' (${account.accountType.displayName}) lacks required capability '${requiredCapability.displayName}'."
            )
        }

        // 6. Approval Check
        if (!isApproved) {
            return AccountValidationResult.Blocked(
                code = "APPROVAL_REQUIRED",
                message = "Action requires explicit user approval."
            )
        }

        return AccountValidationResult.Allowed
    }
}
