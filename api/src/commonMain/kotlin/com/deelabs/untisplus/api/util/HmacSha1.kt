package com.deelabs.untisplus.api.util

/**
 * Computes the HMAC-SHA1 of [data] using [key]. Backed by the platform crypto
 * provider (JCA on JVM/Android, CommonCrypto on Apple targets).
 */
internal expect fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray
