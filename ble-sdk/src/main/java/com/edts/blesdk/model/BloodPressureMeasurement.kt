package com.edts.blesdk.model

data class BloodPressureMeasurement(
    val systolic: Float,
    val diastolic: Float,
    val map: Float,
    val unitIsKpa: Boolean
)
