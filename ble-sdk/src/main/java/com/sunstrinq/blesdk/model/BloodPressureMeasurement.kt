package com.sunstrinq.blesdk.model

data class BloodPressureMeasurement(
    val systolic: Float,
    val diastolic: Float,
    val map: Float,
    val unitIsKpa: Boolean
)
