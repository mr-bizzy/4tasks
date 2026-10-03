package org.tasks.auth

import org.tasks.data.entity.CaldavAccount
import org.tasks.googleapis.GoogleAccountState

private const val GOOGLE_JSON_UNAUTHORIZED_PREFIX = "401 Unauthorized"

fun CaldavAccount.isUnauthorized(): Boolean =
    isLoggedOut() ||
        error?.startsWith(GOOGLE_JSON_UNAUTHORIZED_PREFIX, ignoreCase = true) == true ||
        (isGoogleTasks && GoogleAccountState.fromError(error).needsSignIn) ||
        error?.startsWith(TokenError.REFRESH_FAILED, ignoreCase = true) == true
