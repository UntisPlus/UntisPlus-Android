package com.deelabs.untisplus.ui.pages.settings.fragments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.sapuseven.compose.protostore.ui.preferences.Preference
import com.sapuseven.compose.protostore.ui.preferences.PreferenceGroup
import com.deelabs.untisplus.BuildConfig
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.proxy.ProxyCapabilityState
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_PRIVACY_POLICY
import com.deelabs.untisplus.data.model.github.GitHubApi.URL_GITHUB_REPOSITORY
import com.deelabs.untisplus.ui.navigation.AppRoutes
import com.deelabs.untisplus.ui.pages.settings.SettingsScreenViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCategoryAbout(navController: NavController, viewModel: SettingsScreenViewModel) {
	val uriHandler = LocalUriHandler.current
	var showUpdateCheck by remember { mutableStateOf(false) }

	Preference(
		title = { Text(stringResource(R.string.app_name)) },
		summary = {
			Text(
				stringResource(
					R.string.preference_info_app_version_desc,
					BuildConfig.VERSION_NAME,
					BuildConfig.VERSION_CODE
				)
			)
		},
		onClick = {
			uriHandler.openUri("$URL_GITHUB_REPOSITORY/releases")
		},
		leadingContent = {
			Icon(
				painter = painterResource(R.drawable.settings_about_app_icon),
				contentDescription = null
			)
		}
	)

	Preference(
		title = { Text(stringResource(R.string.preference_check_updates)) },
		summary = {
			Text(
				stringResource(
					R.string.preference_check_updates_desc,
					BuildConfig.RELEASE_VERSION_NAME,
					BuildConfig.DEV_VERSION_NAME
				)
			)
		},
		onClick = { showUpdateCheck = true },
		leadingContent = {
			Icon(
				painter = painterResource(R.drawable.settings_info),
				contentDescription = null
			)
		}
	)

	if (showUpdateCheck) {
		UpdateCheckDialog(viewModel = viewModel, onDismiss = { showUpdateCheck = false })
	}

	if (viewModel.proxyAvailable) {
		var showProxyDiagnostics by remember { mutableStateOf(false) }
		var showProxyCapabilities by remember { mutableStateOf(false) }

		Preference(
			title = { Text(stringResource(R.string.preference_proxy_status)) },
			summary = { Text(stringResource(R.string.preference_proxy_status_desc)) },
			onClick = { showProxyDiagnostics = true },
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_info),
					contentDescription = null
				)
			}
		)

		Preference(
			title = { Text(stringResource(R.string.preference_proxy_capabilities)) },
			summary = { Text(stringResource(R.string.preference_proxy_capabilities_desc)) },
			onClick = { showProxyCapabilities = true },
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.all_debug),
					contentDescription = null
				)
			}
		)

		if (showProxyDiagnostics) {
			ProxyDiagnosticsDialog(
				viewModel = viewModel,
				onDismiss = { showProxyDiagnostics = false }
			)
		}

		if (showProxyCapabilities) {
			ProxyCapabilitiesDialog(
				viewModel = viewModel,
				onDismiss = { showProxyCapabilities = false }
			)
		}
	}

	PreferenceGroup(stringResource(id = R.string.preference_info_general)) {
		val externalSourceDialog = remember { mutableStateOf(false) }

		Preference(
			title = { Text(stringResource(R.string.preference_info_github)) },
			summary = {
				//Text(URL_GITHUB_REPOSITORY)
			},
			onClick = {
				uriHandler.openUri(URL_GITHUB_REPOSITORY)
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_info_github),
					contentDescription = null
				)
			}
		)

		Preference(
			title = { Text(stringResource(R.string.preference_info_license)) },
			summary = { Text(stringResource(R.string.preference_info_license_desc)) },
			onClick = {
				uriHandler.openUri("$URL_GITHUB_REPOSITORY/blob/master/LICENSE")
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_info_github),
					contentDescription = null
				)
			}
		)

		Preference(
			title = { Text(stringResource(R.string.preference_info_contributors)) },
			summary = { Text(stringResource(R.string.preference_info_contributors_desc)) },
			onClick = {
				externalSourceDialog.value = true
			},
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_about_contributor),
					contentDescription = null
				)
			}
		)

		if (externalSourceDialog.value) {
			AlertDialog(
				onDismissRequest = {
					externalSourceDialog.value = false
				},
				confirmButton = {
					FlowRow(
						horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
						verticalArrangement = Arrangement.spacedBy(8.dp),
						modifier = Modifier
							.fillMaxWidth()
					) {
						TextButton(
							onClick = {
								uriHandler.openUri(URL_GITHUB_PRIVACY_POLICY)
							}
						) {
							Text(text = stringResource(id = R.string.preference_info_privacy_policy))
						}

						Row(
							horizontalArrangement = Arrangement.spacedBy(8.dp)
						) {
							TextButton(
								onClick = {
									externalSourceDialog.value = false
								}
							) {
								Text(text = stringResource(id = R.string.all_cancel))
							}

							TextButton(
								onClick = {
									externalSourceDialog.value = false
									navController.navigate(AppRoutes.Settings.About.Contributors)
								}
							) {
								Text(text = stringResource(id = R.string.all_ok))
							}
						}
					}
				},
				title = {
					Text(text = stringResource(id = R.string.preference_info_privacy))
				},
				text = {
					Text(stringResource(id = R.string.preference_info_privacy_desc))
				}
			)
		}

		Preference(
			title = { Text(stringResource(R.string.preference_info_libraries)) },
			summary = { Text(stringResource(R.string.preference_info_libraries_desc)) },
			onClick = { navController.navigate(AppRoutes.Settings.About.Libraries) },
			leadingContent = {
				Icon(
					painter = painterResource(R.drawable.settings_about_library),
					contentDescription = null
				)
			}
		)
	}
}

@Composable
private fun UpdateCheckDialog(viewModel: SettingsScreenViewModel, onDismiss: () -> Unit) {
	val uriHandler = LocalUriHandler.current
	var result by remember {
		mutableStateOf<SettingsScreenViewModel.UpdateCheckResult?>(null)
	}

	LaunchedEffect(Unit) {
		result = viewModel.checkForUpdates()
	}

	val openUrl = when (val value = result) {
		is SettingsScreenViewModel.UpdateCheckResult.ReleaseAvailable ->
			value.downloadUrl ?: value.htmlUrl
		is SettingsScreenViewModel.UpdateCheckResult.DevUpdateAvailable -> value.branchUrl
		else -> null
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		title = { Text(stringResource(R.string.preference_check_updates)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
				when (val value = result) {
					null -> Row(
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						CircularProgressIndicator()
						Text(stringResource(R.string.update_check_checking))
					}

					is SettingsScreenViewModel.UpdateCheckResult.ReleaseAvailable -> {
						Text(stringResource(R.string.update_check_release_available, value.tag))
						value.notes?.takeIf { it.isNotBlank() }?.let { notes ->
							HorizontalDivider()
							Text(stringResource(R.string.update_check_release_notes))
							Text(notes.take(1200))
						}
					}

					is SettingsScreenViewModel.UpdateCheckResult.DevUpdateAvailable -> {
						Text(stringResource(R.string.update_check_dev_available, value.remoteSha))
						Text(stringResource(R.string.update_check_dev_hint))
					}

					SettingsScreenViewModel.UpdateCheckResult.UpToDate -> {
						Text(
							stringResource(
								if (BuildConfig.DEBUG)
									R.string.update_check_up_to_date_dev
								else
									R.string.update_check_up_to_date_release,
								if (BuildConfig.DEBUG) BuildConfig.GIT_COMMIT_SHA else BuildConfig.VERSION_NAME
							)
						)
					}

					is SettingsScreenViewModel.UpdateCheckResult.Failed -> {
						Text(stringResource(R.string.update_check_failed))
						value.reason?.let { Text(it) }
					}
				}
			}
		},
		confirmButton = {
			if (openUrl != null) {
				TextButton(
					onClick = {
						uriHandler.openUri(openUrl)
						onDismiss()
					}
				) {
					Text(stringResource(R.string.update_check_open))
				}
			} else {
				TextButton(onClick = onDismiss) {
					Text(stringResource(R.string.all_ok))
				}
			}
		},
		dismissButton = {
			TextButton(onClick = onDismiss) {
				Text(stringResource(R.string.all_close))
			}
		}
	)
}

@Composable
private fun ProxyCapabilitiesDialog(viewModel: SettingsScreenViewModel, onDismiss: () -> Unit) {
	val me by viewModel.proxyMe.collectAsState()
	val scope = rememberCoroutineScope()
	var error by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(Unit) {
		viewModel.refreshProxyMe()
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(text = stringResource(id = R.string.all_ok))
			}
		},
		title = { Text(text = stringResource(id = R.string.proxy_capabilities_title)) },
		text = {
			Column {
				when (val value = me) {
					null -> Row(
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						verticalAlignment = Alignment.CenterVertically
					) {
						CircularProgressIndicator()
						Text(text = stringResource(id = R.string.proxy_capabilities_loading))
					}

					else -> Column(
						modifier = Modifier.verticalScroll(rememberScrollState()),
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						value.v?.let { DiagnosticRow("v", it.toString()) }
						value.role?.let { DiagnosticRow("role", it) }
						DiagnosticRow("level", value.level)
						value.school?.let { DiagnosticRow("school", it) }
						if (value.groups.isNotEmpty()) {
							DiagnosticRow("groups", value.groups.joinToString(", "))
						}
						DiagnosticRow("effective", value.effective.size.toString())

						error?.let {
							Text(text = stringResource(id = R.string.proxy_capabilities_error, it))
						}

						HorizontalDivider()

						value.capabilities.toSortedMap().entries
							.groupBy { it.key.substringBefore('.') }
							.forEach { (group, entries) ->
								Text(
									text = group,
									style = MaterialTheme.typography.titleSmall
								)
								entries.forEach { (key, state) ->
									val canToggle = state.toggleable &&
										(state.userOptedIn || (state.serverEnabled && state.adminAllowed))
									val unknownError = stringResource(id = R.string.proxy_capabilities_error_unknown)
									Row(
										modifier = Modifier.fillMaxWidth(),
										verticalAlignment = Alignment.CenterVertically
									) {
										Column(modifier = Modifier.weight(1f)) {
											Text(text = key)
											Text(
												text = capabilityStatusText(state),
												style = MaterialTheme.typography.bodySmall
											)
										}
										Switch(
											checked = state.effective,
											enabled = canToggle,
											onCheckedChange = { checked ->
												scope.launch {
													viewModel.setProxyCapability(key, checked)
														.onFailure { failure ->
															error = failure.message ?: unknownError
														}
												}
											}
										)
									}
								}
								HorizontalDivider()
							}
					}
				}
			}
		}
	)
}

@Composable
private fun capabilityStatusText(state: ProxyCapabilityState): String {
	val parts = mutableListOf(
		stringResource(
			if (state.effective) R.string.proxy_capability_on
			else R.string.proxy_capability_off
		)
	)
	state.source?.let { parts += stringResource(R.string.proxy_capability_source, it) }
	if (!state.serverEnabled) parts += stringResource(R.string.proxy_capability_server_off)
	if (!state.adminAllowed) parts += stringResource(R.string.proxy_capability_admin_off)
	if (!state.userOptedIn) parts += stringResource(R.string.proxy_capability_not_opted_in)
	return parts.joinToString(" · ")
}

@Composable
private fun ProxyDiagnosticsDialog(viewModel: SettingsScreenViewModel, onDismiss: () -> Unit) {
	var diagnostics by remember { mutableStateOf<SettingsScreenViewModel.ProxyDiagnostics?>(null) }
	var failed by remember { mutableStateOf(false) }
	var loading by remember { mutableStateOf(true) }

	LaunchedEffect(Unit) {
		viewModel.proxyDiagnostics()
			.onSuccess { diagnostics = it }
			.onFailure { failed = true }
		loading = false
	}

	AlertDialog(
		onDismissRequest = onDismiss,
		confirmButton = {
			TextButton(onClick = onDismiss) {
				Text(text = stringResource(id = R.string.all_ok))
			}
		},
		title = { Text(text = stringResource(id = R.string.preference_proxy_status)) },
		text = {
			when {
				loading -> Row(
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					CircularProgressIndicator()
					Text(text = stringResource(id = R.string.preference_proxy_status_loading))
				}

				failed -> Text(text = stringResource(id = R.string.preference_proxy_status_error))
				else -> diagnostics?.let { d ->
					Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
						DiagnosticRow("status", d.status.status)
						d.status.version?.let { DiagnosticRow("version", it) }
						d.status.mode?.let { DiagnosticRow("mode", it) }
						d.status.uptimeSec?.let { DiagnosticRow("uptime_sec", it.toString()) }

						HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

						DiagnosticRow("username", d.me.username)
						DiagnosticRow("level", d.me.level)
						DiagnosticRow("recon", d.me.permissions.recon.toString())
						DiagnosticRow("boosted", d.me.permissions.boosted.toString())
						DiagnosticRow("editor", d.me.permissions.editor.toString())
					}
				}
			}
		}
	)
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
	Row(modifier = Modifier.fillMaxWidth()) {
		Text(text = label, modifier = Modifier.padding(end = 8.dp))
		Text(text = value)
	}
}
