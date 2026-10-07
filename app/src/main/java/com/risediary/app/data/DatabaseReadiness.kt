package com.risediary.app.data

fun interface DatabaseReadiness { suspend fun ensureAvailable() }
