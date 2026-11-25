package com.deelabs.untisplus.data.repository

import com.deelabs.untisplus.modules.UserApiFactory

/** A school-configured link from the official app's home integrations. */
data class CustomLink(
	val name: String,
	val url: String,
)

interface CustomLinksRepository {
	suspend fun load(): Result<List<CustomLink>>
}

/** Reads `GET /view/v1/home` through the generated REST client. */
class RestCustomLinksRepository constructor(
	private val userApiFactory: UserApiFactory,
) : CustomLinksRepository {
	override suspend fun load(): Result<List<CustomLink>> = runCatching {
		userApiFactory.create().getHome().body()
			.integrationsSection
			.orEmpty()
			.mapNotNull { section ->
				val url = section.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
				val name = section.name?.takeIf { it.isNotBlank() } ?: url
				CustomLink(name, url)
			}
	}
}
