package com.deelabs.untisplus.api.model.request

import com.deelabs.untisplus.api.model.untis.Auth
import kotlinx.serialization.Serializable

@Serializable
data class OfficeHourRegistrationsParams(
	val periodId: Long,
	val teacherId: Long,
	val auth: Auth
) : BaseParams()
