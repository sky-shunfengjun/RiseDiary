package com.risediary.app.media

interface VideoFileAccess {
    suspend fun acquire(uriString: String, flags: Int): Result<LocalVideoRef>
    suspend fun check(video: LocalVideoRef): VideoAccessState
    suspend fun releaseUnused(referencedUris: Set<String>)
}
