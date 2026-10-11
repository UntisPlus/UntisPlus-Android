package com.deelabs.untisplus.modules

import com.deelabs.untisplus.api.client.AbsenceApi
import com.deelabs.untisplus.api.client.ClassRegApi
import com.deelabs.untisplus.api.client.MessagesApi
import com.deelabs.untisplus.api.client.OfficeHoursApi
import com.deelabs.untisplus.api.client.ProxyAbsenceApi
import com.deelabs.untisplus.api.client.ProxyCalendarApi
import com.deelabs.untisplus.api.client.ProxyCookieStorage
import com.deelabs.untisplus.api.client.ProxyHomeworkApi
import com.deelabs.untisplus.api.client.ProxyStatusApi
import com.deelabs.untisplus.api.client.ProxySubscriptionsApi
import com.deelabs.untisplus.api.client.ProxyTimetableApi
import com.deelabs.untisplus.api.client.SchoolSearchApi
import com.deelabs.untisplus.api.client.TimetableApi
import com.deelabs.untisplus.api.client.UserDataApi
import com.deelabs.untisplus.api.client.installCookieStorage
import com.deelabs.untisplus.api.client.installSse
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val jsonRpcApiClientModule = module {
	single<HttpClientEngineFactory<*>> { CIO }

	/**
	 * Process-wide cookie store. The proxy sets `JSESSIONID` during the intern
	 * login (`getUserData2017`); every client that talks to the proxy shares this
	 * instance so the session rides along on later `/api/…` calls.
	 */
	single { ProxyCookieStorage() }

	single<CookiesStorage> { get<ProxyCookieStorage>() }

	single<HttpClient>(named("json")) {
		HttpClient(get<HttpClientEngineFactory<*>>().create()) {
			install(ContentNegotiation) {
				json()
			}
			install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 3)
				retryOnException(maxRetries = 3)
				exponentialDelay()
			}
			expectSuccess = true
		}
	}

	/**
	 * Plain client for fetching external `.ics` files. Responses are read as raw
	 * text, so no content negotiation is installed, and `expectSuccess` stays off
	 * so conditional `304 Not Modified` responses can be handled by the caller.
	 */
	single<HttpClient>(named("ics")) {
		HttpClient(get<HttpClientEngineFactory<*>>().create()) {
			install(HttpRequestRetry) {
				retryOnServerErrors(maxRetries = 2)
				retryOnException(maxRetries = 2)
				exponentialDelay()
			}
			expectSuccess = false
		}
	}

	single {
		val cookies = get<CookiesStorage>()
		SchoolSearchApi(get(), { it.installCookieStorage(cookies) })
	}

	single {
		val cookies = get<CookiesStorage>()
		UserDataApi(get(), { it.installCookieStorage(cookies) })
	}

	single {
		val cookies = get<CookiesStorage>()
		TimetableApi(get(), { it.installCookieStorage(cookies) })
	}

	single {
		val cookies = get<CookiesStorage>()
		MessagesApi(get(), { it.installCookieStorage(cookies) })
	}

	single {
		val cookies = get<CookiesStorage>()
		ClassRegApi(get(), { it.installCookieStorage(cookies) })
	}

	single {
		val cookies = get<CookiesStorage>()
		AbsenceApi(get(), { it.installCookieStorage(cookies) })
	}

	single {
		val cookies = get<CookiesStorage>()
		OfficeHoursApi(get(), { it.installCookieStorage(cookies) })
	}

	single {
		val cookies = get<CookiesStorage>()
		ProxyStatusApi(
			get(),
			{
				it.installCookieStorage(cookies)
				it.expectSuccess = true
			}
		)
	}

	single {
		val cookies = get<CookiesStorage>()
		ProxyHomeworkApi(
			get(),
			{
				it.installCookieStorage(cookies)
				it.expectSuccess = true
			}
		)
	}

	single {
		val cookies = get<CookiesStorage>()
		ProxyAbsenceApi(
			get(),
			{
				it.installCookieStorage(cookies)
				it.expectSuccess = true
			}
		)
	}

	single {
		val cookies = get<CookiesStorage>()
		ProxyCalendarApi(
			get(),
			{
				it.installCookieStorage(cookies)
				it.expectSuccess = true
			}
		)
	}

	single {
		val cookies = get<CookiesStorage>()
		ProxySubscriptionsApi(
			get(),
			{
				it.installCookieStorage(cookies)
				it.expectSuccess = true
			}
		)
	}

	single {
		val cookies = get<CookiesStorage>()
		ProxyTimetableApi(
			get(),
			{
				it.installCookieStorage(cookies)
				it.installSse()
				it.expectSuccess = true
			}
		)
	}
}
