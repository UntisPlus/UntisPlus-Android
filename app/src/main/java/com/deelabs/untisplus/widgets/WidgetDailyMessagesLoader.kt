package com.deelabs.untisplus.widgets

import android.content.Context
import androidx.core.text.HtmlCompat
import com.deelabs.untisplus.R
import com.deelabs.untisplus.api.model.untis.MessageOfDay
import com.deelabs.untisplus.data.database.entities.UserDao
import com.deelabs.untisplus.data.database.entities.getDisplayedName
import com.deelabs.untisplus.data.repository.InfoCenterRepository
import com.deelabs.untisplus.data.repository.UserRepository
import crocodile8.universal_cache.FromCache
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
 * Reads today's messages from the Info Center's existing disk cache. The cache
 * is filled by the app's regular proxy-backed Info Center refresh; the widget
 * itself never starts a network request or touches a login flow.
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
		val messages = runCatching {
			infoCenterRepository.messagesOfDaySource()
				.get(today, FromCache.ONLY, additionalKey = user.id)
				.last()
		}.getOrNull().orEmpty()

		val plainMessages = messages.map { message ->
			message.copy(body = HtmlCompat.fromHtml(message.body, HtmlCompat.FROM_HTML_MODE_COMPACT).toString())
		}

		return formatWidgetDailyMessages(
			messages = plainMessages,
			header = user.getDisplayedName(context),
			emptyMessage = context.getString(R.string.widget_messages_empty),
			unknownSubject = context.getString(R.string.widget_messages_untitled),
		)
	}
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
