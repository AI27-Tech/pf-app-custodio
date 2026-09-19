package com.ai27.app.custodio.domain.model

data class Gps(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val ageMs: Long?,
)