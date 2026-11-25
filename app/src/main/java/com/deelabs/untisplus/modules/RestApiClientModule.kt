package com.deelabs.untisplus.modules

import com.deelabs.untisplus.data.database.entities.jsonRpcApiUrl
import com.deelabs.untisplus.data.database.entities.restApiUrl

import com.deelabs.untisplus.api.client.UserDataApi
import com.deelabs.untisplus.api.client.installCookieStorage
import com.deelabs.untisplus.api.rest.MessagesApi
import com.deelabs.untisplus.api.rest.UserApi
import com.deelabs.untisplus.data.repository.UserRepository
import com.deelabs.untisplus.exceptions.UntisRestApiException
import com.deelabs.untisplus.model.rest.ErrorResponse
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.module
import kotlin.reflect.KProperty

val restApiClientModule = module {
	single<MessagesApiFactory> {
		DefaultMessagesApiFactory(get(), get(), get(), get())
	}
	single<UserApiFactory> {
		DefaultUserApiFactory(get(), get(), get(), get())
	}
}

interface MessagesApiFactory {
	suspend fun create(): MessagesApi
}

class DefaultMessagesApiFactory constructor(
	private val userRepository: UserRepository,
	private val userDataApi: UserDataApi,
	private val httpClientEngineFactory: HttpClientEngineFactory<*>,
	private val cookiesStorage: CookiesStorage
) : MessagesApiFactory {
	val messagesApi by keyedLazy({ userRepository.currentUser!!.id }) {
		MessagesApi(
			baseUrl = userRepository.currentUser!!.restApiUrl.toString(),
			httpClientEngine = httpClientEngineFactory.create()
		) {
			it.expectSuccess = true
			it.installCookieStorage(cookiesStorage)
			it.install(ContentNegotiation) {
				json()
			}
			it.install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 3)
				retryOnException(maxRetries = 3)
				exponentialDelay()
			}
			it.HttpResponseValidator {
				handleResponseExceptionWithRequest { exception, request ->
					val clientException = exception as? ClientRequestException ?: return@handleResponseExceptionWithRequest
					val exceptionResponse = clientException.response
					val errorResponse = exceptionResponse.body<ErrorResponse>()
					throw UntisRestApiException(errorResponse)
				}
			}
		}
	}

	override suspend fun create(): MessagesApi = messagesApi.apply {
		val user = userRepository.currentUser!!
		// TODO: Only fetch token if missing or expired
		val token = userDataApi.getAuthToken(user.jsonRpcApiUrl.toString(), user.user, user.key)
		setBearerToken(token)
	}
}

interface UserApiFactory {
	suspend fun create(): UserApi
}

/** REST client for the official app's home/integration links. */
class DefaultUserApiFactory constructor(
	private val userRepository: UserRepository,
	private val userDataApi: UserDataApi,
	private val httpClientEngineFactory: HttpClientEngineFactory<*>,
	private val cookiesStorage: CookiesStorage
) : UserApiFactory {
	val userApi by keyedLazy({ userRepository.currentUser!!.id }) {
		UserApi(
			baseUrl = userRepository.currentUser!!.restApiUrl.toString(),
			httpClientEngine = httpClientEngineFactory.create()
		) {
			it.expectSuccess = true
			it.installCookieStorage(cookiesStorage)
			it.install(ContentNegotiation) {
				json()
			}
			it.install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 3)
				retryOnException(maxRetries = 3)
				exponentialDelay()
			}
			it.HttpResponseValidator {
				handleResponseExceptionWithRequest { exception, request ->
					val clientException = exception as? ClientRequestException ?: return@handleResponseExceptionWithRequest
					throw UntisRestApiException(clientException.response.body<ErrorResponse>())
				}
			}
		}
	}

	override suspend fun create(): UserApi = userApi.apply {
		val user = userRepository.currentUser!!
		val token = userDataApi.getAuthToken(user.jsonRpcApiUrl.toString(), user.user, user.key)
		setBearerToken(token)
	}
}

class KeyedLazy<K, V>(
	private val keyProvider: () -> K,
	private val initializer: (K) -> V
) {
	private var cachedKey: K? = null
	private var cachedValue: V? = null

	operator fun getValue(thisRef: Any?, property: KProperty<*>): V = synchronized(this) {
		val key = keyProvider()
		if (cachedKey != key) {
			cachedValue = initializer(key)
			cachedKey = key
		}
		return cachedValue!!
	}
}

fun <K, V> keyedLazy(keyProvider: () -> K, initializer: (K) -> V): KeyedLazy<K, V> =
	KeyedLazy(keyProvider, initializer)
