package com.deelabs.untisplus.ui.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deelabs.untisplus.R
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.proxySchoolNameValue
import com.deelabs.untisplus.helpers.ProfileQrExport
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

/**
 * Warns before revealing the app login key encoded in a QR code. The payload is
 * generated only after confirmation and lives only in this composition; it is
 * never logged or stored in navigation/saved state.
 */
@Composable
fun ProfileQrExportDialog(
	user: User,
	onDismiss: () -> Unit
) {
	var confirmed by remember { mutableStateOf(false) }

	val qrBitmap = remember(user, confirmed) {
		if (!confirmed) return@remember null
		val key = user.key
		val payload = ProfileQrExport.buildUri(
			school = user.qrSchoolName(),
			user = user.user,
			key = key,
			anonymous = user.anonymous,
			apiUrl = user.customApiHost?.takeIf { it.isNotBlank() },
			skipAppSecret = !key.isNullOrBlank(),
		)
		runCatching {
			BarcodeEncoder()
				.encodeBitmap(payload, BarcodeFormat.QR_CODE, 512, 512)
				.asImageBitmap()
		}.getOrNull()
	}

	if (!confirmed) {
		AlertDialog(
			onDismissRequest = onDismiss,
			title = { Text(stringResource(R.string.profile_qr_export_warning_title)) },
			text = { Text(stringResource(R.string.profile_qr_export_warning)) },
			confirmButton = {
				TextButton(onClick = { confirmed = true }) {
					Text(stringResource(R.string.profile_qr_export_show))
				}
			},
			dismissButton = {
				TextButton(onClick = onDismiss) {
					Text(stringResource(R.string.all_cancel))
				}
			}
		)
		return
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.profile_qr_export_title)) },
		text = {
			if (qrBitmap != null) {
				Image(
					bitmap = qrBitmap,
					contentDescription = stringResource(R.string.profile_qr_export_image_description),
					modifier = Modifier
						.size(256.dp)
						.padding(8.dp)
						.background(Color.White, MaterialTheme.shapes.small)
				)
			} else {
				Text(stringResource(R.string.profile_qr_export_error))
			}
		},
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.all_close))
			}
		}
	)
}

/** Prefer the current school info, falling back to pre-schema-v12 profiles. */
@Suppress("DEPRECATION")
private fun User.qrSchoolName(): String? =
	proxySchoolNameValue ?: schoolId?.takeIf { it.isNotBlank() }
