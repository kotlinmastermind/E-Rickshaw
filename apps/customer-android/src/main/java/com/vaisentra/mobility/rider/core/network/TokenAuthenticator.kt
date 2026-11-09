package com.vaisentra.mobility.rider.core.network

import com.vaisentra.mobility.core.network.CoreTokenAuthenticator
import com.vaisentra.mobility.rider.core.security.TokenManager

/**
 * Enterprise Token Authenticator for Rider App.
 * Delegates automatic JWT token rotation to CoreTokenAuthenticator.
 */
class TokenAuthenticator(
    tokenManager: TokenManager,
    baseUrlProvider: () -> String
) : CoreTokenAuthenticator(tokenManager, baseUrlProvider, tag = "TokenAuthenticator")
