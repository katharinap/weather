package com.katharina.weather.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertsResponseDto(
    @SerialName("alerts") val alerts: List<AlertDto> = emptyList(),
    @SerialName("location") val location: AlertLocationDto? = null
)

@Serializable
data class AlertLocationDto(
    @SerialName("warn_cell_id") val warnCellId: Long? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("name_short") val nameShort: String? = null,
    @SerialName("district") val district: String? = null,
    @SerialName("state") val state: String? = null,
    @SerialName("state_short") val stateShort: String? = null
)

@Serializable
data class AlertDto(
    @SerialName("id") val id: Long? = null,
    @SerialName("alert_id") val alertId: String? = null,
    @SerialName("effective") val effective: String? = null,
    @SerialName("onset") val onset: String? = null,
    @SerialName("expires") val expires: String? = null,
    @SerialName("category") val category: String? = null,
    @SerialName("response_type") val responseType: String? = null,
    @SerialName("urgency") val urgency: String? = null,
    @SerialName("severity") val severity: String? = null,
    @SerialName("certainty") val certainty: String? = null,
    @SerialName("event_code") val eventCode: Int? = null,
    @SerialName("event_en") val eventEn: String? = null,
    @SerialName("event_de") val eventDe: String? = null,
    @SerialName("headline_en") val headlineEn: String? = null,
    @SerialName("headline_de") val headlineDe: String? = null,
    @SerialName("description_en") val descriptionEn: String? = null,
    @SerialName("description_de") val descriptionDe: String? = null,
    @SerialName("instruction_en") val instructionEn: String? = null,
    @SerialName("instruction_de") val instructionDe: String? = null,
    @SerialName("status") val status: String? = null
)
