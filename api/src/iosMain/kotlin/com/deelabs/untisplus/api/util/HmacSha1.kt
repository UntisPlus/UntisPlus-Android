package com.deelabs.untisplus.api.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.kCCHmacAlgSHA1

private const val SHA1_DIGEST_LENGTH = 20

@OptIn(ExperimentalForeignApi::class)
internal actual fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
	val out = ByteArray(SHA1_DIGEST_LENGTH)
	key.usePinned { keyPinned ->
		data.usePinned { dataPinned ->
			out.usePinned { outPinned ->
				CCHmac(
					kCCHmacAlgSHA1,
					keyPinned.addressOf(0),
					key.size.convert(),
					dataPinned.addressOf(0),
					data.size.convert(),
					outPinned.addressOf(0)
				)
			}
		}
	}
	return out
}
