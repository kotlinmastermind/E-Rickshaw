package com.vaisentra.mobility.driver.core.network

import com.vaisentra.mobility.core.network.CoreTokenAuthenticator
import com.vaisentra.mobility.driver.core.security.DriverTokenManager

/**
 * Enterprise Token Authenticator for Driver App.
 * Delegates automatic JWT token rotation to CoreTokenAuthenticator.
 */
class DriverTokenAuthenticator(
    tokenManager: DriverTokenManager,
    baseUrlProvider: () -> String
) : CoreTokenAuthenticator(tokenManager, baseUrlProvider, tag = "DriverTokenAuthenticator")
