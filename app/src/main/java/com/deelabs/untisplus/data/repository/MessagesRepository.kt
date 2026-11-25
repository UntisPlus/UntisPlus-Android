package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.data.cache.DiskCache
import com.deelabs.untisplus.model.rest.Message
import com.deelabs.untisplus.model.rest.MessagesResponse
import com.deelabs.untisplus.modules.MessagesApiFactory
import crocodile8.universal_cache.CachedSource
import crocodile8.universal_cache.time.TimeProvider
import kotlinx.serialization.serializer
import okio.Path

interface MessagesRepository {
	fun messageSource(): CachedSource<Long, Message>

	fun messagesSource(): CachedSource<Unit, MessagesResponse>

	fun messagesSentSource(): CachedSource<Unit, List<Message>>

	fun messagesDraftsSource(): CachedSource<Unit, List<Message>>
}

class UntisMessagesRepository constructor(
	private val messagesApiFactory: MessagesApiFactory,
	private val cacheDir: Path,
	private val timeProvider: TimeProvider,
) : MessagesRepository {
	override fun messageSource(): CachedSource<Long, Message> {
		return CachedSource(
			source = { params ->
				messagesApiFactory.create().getMessage(params).body()
			},
			cache = DiskCache(cacheDir.resolve("messenger").resolve("message"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun messagesSource(): CachedSource<Unit, MessagesResponse> {
		return CachedSource(
			source = { params ->
				messagesApiFactory.create().getMessages().body()
			},
			cache = DiskCache(cacheDir.resolve("messenger").resolve("messages"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun messagesSentSource(): CachedSource<Unit, List<Message>> {
		return CachedSource(
			source = { params ->
				messagesApiFactory.create().getMessagesSent().body().sentMessages ?: emptyList()
			},
			cache = DiskCache(cacheDir.resolve("messenger").resolve("messagesSent"), serializer()),
			timeProvider = timeProvider
		)
	}

	override fun messagesDraftsSource(): CachedSource<Unit, List<Message>> {
		return CachedSource(
			source = { params ->
				messagesApiFactory.create().getMessagesDrafts().body().draftMessages ?: emptyList()
			},
			cache = DiskCache(cacheDir.resolve("messenger").resolve("messagesDrafts"), serializer()),
			timeProvider = timeProvider
		)
	}
}
