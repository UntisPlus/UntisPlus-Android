package com.deelabs.untisplus.api.client

import com.deelabs.untisplus.api.model.proxy.AnalyticsOptions
import com.deelabs.untisplus.api.model.proxy.AnalyticsReport
import com.deelabs.untisplus.api.model.proxy.AnalyticsReportRequest
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineFactory
import kotlinx.serialization.json.Json

/**
 * Client for the proxy's teacher-cancellation analytics surface.
 *
 * Both endpoints use the session `JSESSIONID` cookie captured at proxy login.
 * [baseUrl] is the proxy origin (`scheme://authority`, no `/WebUntis`).
 */
open class ProxyAnalyticsApi : ApiClient {
	constructor(
		engineFactory: HttpClientEngineFactory<*>? = null,
		config: ((HttpClientConfig<*>) -> Unit)? = null,
		jsonBlock: Json = ApiClient.ANALYTICS_JSON
	) : super(engineFactory, config, jsonBlock)

	/** Builds on an already-configured client (used by tests). */
	constructor(httpClient: HttpClient) : super(httpClient)

	/** Approved targets and configured school years for the signed-in account. */
	open suspend fun options(baseUrl: String, school: String? = null): AnalyticsOptions =
		get(proxyUrl(baseUrl, "/api/analytics/options", school)).body()

	/** Runs a read-only cancellation report. */
	open suspend fun teacherCancellations(
		baseUrl: String,
		request: AnalyticsReportRequest,
		school: String? = null
	): AnalyticsReport =
		post(proxyUrl(baseUrl, "/api/analytics/teacher-cancellations", school), request).body()
}
