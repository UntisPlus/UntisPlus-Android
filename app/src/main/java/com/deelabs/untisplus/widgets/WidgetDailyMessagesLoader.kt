package com.deelabs.untisplus.widgets

import android.content.Context
import androidx.core.text.HtmlCompat
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.MessageOfDay
import com.deelabs.untisplus.data.cache.CacheRead
import com.deelabs.untisplus.data.cache.readFreshOrCached
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.database.entities.getDisplayedName
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import com.deelabs.untisplus.data.repository.UserRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.datetime.toKotlinLocalDate
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate

internal data class WidgetDailyMessage(
	val subject: String,
	val body: String,
)

internal data class WidgetDailyMessages(
	val header: String = "",
	val messages: List<WidgetDailyMessage> = emptyList(),
	val hiddenCount: Int = 0,
	val message: String? = null,
)

/**
 * Reads today's messages from the Info Center's existing disk cache, using the
 * repository's normal refresh for stale entries. A cold, corrupt or expired
 * cache produces an explicit error state rather than blank content.
 */
internal class WidgetDailyMessagesLoader : KoinComponent {
	private val userDao: UserDao by inject()
	private val userRepository: UserRepository by inject()
	private val infoCenterRepository: InfoCenterRepository by inject()

	suspend fun load(context: Context, configuration: WidgetConfiguration): WidgetDailyMessages {
		val user = userDao.getById(configuration.userId)
			?: return WidgetDailyMessages(message = context.getString(R.string.widget_messages_no_profile))

		// The current user is loaded asynchronously after process death. Wait for
		// that state so a cached widget can still render without asking for login.
		val userState = userRepository.userState.first { it !is UserRepository.UserState.Loading }
		if (userState !is UserRepository.UserState.User) {
			return WidgetDailyMessages(message = context.getString(R.string.widget_messages_not_loaded))
		}

		val today = LocalDate.now().toKotlinLocalDate()
		return buildWidgetDailyMessages(
			readCache = {
				readFreshOrCached<List<MessageOfDay>> { fromCache, maxAge ->
					infoCenterRepository.messagesOfDaySource()
						.get(today, fromCache, maxAge = maxAge, additionalKey = user.id)
						.last()
				}
			},
			header = user.getDisplayedName(context),
			emptyMessage = context.getString(R.string.widget_messages_empty),
			unavailableMessage = context.getString(R.string.widget_messages_load_error),
			unknownSubject = context.getString(R.string.widget_messages_untitled),
			plainText = { body ->
				HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_COMPACT).toString()
			},
		)
	}
}

/** Pure widget state builder; [readCache] is fakeable in account-free tests. */
internal suspend fun buildWidgetDailyMessages(
	readCache: suspend () -> CacheRead<List<MessageOfDay>>,
	header: String,
	emptyMessage: String,
	unavailableMessage: String,
	unknownSubject: String,
	plainText: (String) -> String = { it },
): WidgetDailyMessages {
	val read = readCache()
	if (read !is CacheRead.Value) {
		return WidgetDailyMessages(header = header, message = unavailableMessage)
	}

	val plainMessages = read.value.map { it.copy(body = plainText(it.body)) }
	return formatWidgetDailyMessages(
		messages = plainMessages,
		header = header,
		emptyMessage = emptyMessage,
		unknownSubject = unknownSubject,
	)
}

internal fun formatWidgetDailyMessages(
	messages: List<MessageOfDay>,
	header: String = "",
	emptyMessage: String = "",
	unknownSubject: String = "Message",
	maxMessages: Int = 3,
	maxBodyLength: Int = 240,
): WidgetDailyMessages {
	if (messages.isEmpty()) {
		return WidgetDailyMessages(header = header, message = emptyMessage)
	}

	val limit = maxMessages.coerceAtLeast(0)
	val visible = messages.take(limit).map { message ->
		val body = message.body
			.trim()
			.replace(Regex("\\s+"), " ")
		WidgetDailyMessage(
			subject = message.subject.trim().ifEmpty { unknownSubject },
			body = if (body.length > maxBodyLength) {
				body.take(maxBodyLength - 1).trimEnd() + "…"
			} else {
				body
			},
		)
	}

	return WidgetDailyMessages(
		header = header,
		messages = visible,
		hiddenCount = (messages.size - visible.size).coerceAtLeast(0),
	)
}
