package com.deelabs.untisplus.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.timetable.PeriodElement
import com.deelabs.untisplus.data.database.entities.User
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.repository.MasterDataRepository
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.helpers.AppTheme
import com.deelabs.untisplus.ui.common.ProfileSelectorAction
import com.deelabs.untisplus.ui.dialogs.ElementPickerDialogFullscreen
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ShortcutConfigureActivity : ComponentActivity(), KoinComponent {
	private val userDao: UserDao by inject()

	private val userRepository: UserRepository by inject()

	private val masterDataRepository: MasterDataRepository by inject()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		setContent {
			AppTheme {
				/*AppScaffold(
					modifier = Modifier.fillMaxSize(),
					topBar = {
						TopAppBar(
							title = {
								Text("Add shortcut")
							},
							navigationIcon = {
								IconButton(onClick = { finish() }) {
									Icon(
										imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
										contentDescription = stringResource(id = R.string.all_back)
									)
								}
							}
						)
					},
					bottomBar = {
						BottomAppBar {
							Row(
								horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
								modifier = Modifier
									.fillMaxWidth()
									.padding(16.dp)
							) {
								TextButton(onClick = {}) {
									Text(stringResource(R.string.all_cancel))
								}
								Button(onClick = {}) {
									Text(stringResource(R.string.all_save))
								}
							}
						}
					}
				) {*/
				val users = remember { mutableStateListOf<User>() }
				var selectedUserId by rememberSaveable {
					mutableLongStateOf(userRepository.currentUser?.id ?: -1L)
				}

				LaunchedEffect(userDao) {
					userDao.getAllFlow().collect {
						users.clear()
						users.addAll(it)
						if (selectedUserId == -1L) {
							selectedUserId = it.firstOrNull()?.id ?: -1L
						}
					}
				}

				ElementPickerDialogFullscreen(
					masterDataRepository = masterDataRepository,
					title = { Text(stringResource(id = R.string.widget_timetable_link)) },
					onDismiss = { finish() },
					onSelect = { element ->
						setupShortcut(selectedUserId, element)
						finish()
					},
					additionalActions = {
						ProfileSelectorAction(
							users = users,
							currentSelectionId = selectedUserId,
							hideIfSingleProfile = true,
							onSelectionChange = {
								selectedUserId = it.id
							}
						)
					}
				)
			}
		}
	}

	private fun setupShortcut(
		userId: Long,
		element: PeriodElement?
	) {
		val shortcutIntent = Intent(this, MainActivity::class.java)
			.setAction("")
			.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			.putExtra(MainActivity.EXTRA_LONG_USER_ID, userId)
			.putExtra(MainActivity.EXTRA_STRING_ELEMENT_TYPE, element?.type?.name)
			.putExtra(MainActivity.EXTRA_LONG_ELEMENT_ID, element?.id ?: -1L)

		val shortcutInfo = ShortcutInfoCompat.Builder(this, "${userId}-${element?.type}-${element?.id}")
			.setIntent(shortcutIntent)
			.setShortLabel(
				if (element == null) resources.getString(R.string.all_personal)
				else masterDataRepository.getShortName(element, resources.getString(R.string.widget_timetable))
			)
			.setIcon(IconCompat.createWithResource(this, R.mipmap.ic_shortcut))
			.build()

		// Ask the launcher to pin the shortcut while the user is still in this flow.
		ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null)

		val resultIntent = Intent()
		element?.let {
			resultIntent.putExtra(
				MainActivity.EXTRA_STRING_PERIOD_ELEMENT,
				Json.encodeToString(PeriodElement.serializer(), it)
			)
		}
		setResult(RESULT_OK, resultIntent)
	}
}
