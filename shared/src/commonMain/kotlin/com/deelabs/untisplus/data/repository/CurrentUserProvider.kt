package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.data.database.entities.User

/**
 * Provides the currently active [User] without depending on the (Android-only,
 * DataStore-backed) `UserRepository`. Shared repositories use this so the iOS
 * app can supply its own implementation.
 */
interface CurrentUserProvider {
	val currentUser: User?
}
