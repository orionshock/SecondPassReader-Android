package com.secondpasslibrary.client

/** A transport failure while using an authenticated Library endpoint. No credential is exposed. */
data class AuthenticatedAccessFailure(val apiBaseUrl: String)
