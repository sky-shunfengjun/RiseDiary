package com.risediary.app.media

import kotlinx.serialization.Serializable
import com.risediary.app.BuildConfig
import com.risediary.app.data.entity.Flight
import com.risediary.app.data.sync.RecordIdentity
import java.net.URI

/** Durable identity; library sources never use a device path as their identity. */
@Serializable
sealed interface VideoSourceRef {
    @Serializable data class DeviceVideo(val video: LocalVideoRef) : VideoSourceRef
    @Serializable data class LibraryVideo(val materialId: String) : VideoSourceRef
}

fun libraryVideoRef(id: String, name: String, mime: String? = null): LocalVideoRef {
    require(RecordIdentity.isValidId(id))
    return LocalVideoRef("content://${BuildConfig.APPLICATION_ID}.library/video/$id", name, mime)
}

fun LocalVideoRef.libraryMaterialId(): String? = runCatching {
    val uri = URI(uriString)
    if (uri.scheme != "content" || uri.rawAuthority != "${BuildConfig.APPLICATION_ID}.library") null
    else uri.rawPath.removePrefix("/video/").takeIf { uri.rawPath == "/video/$it" && RecordIdentity.isValidId(it) }
}.getOrNull()

internal fun requireVideoSource(video: LocalVideoRef?, source: VideoSourceRef?) {
    require((video == null) == (source == null)) { "视频来源不完整" }
    video?.let { require(validateLocalVideoFields(it.uriString, it.displayName, it.mimeType) == null) }
    when (source) {
        is VideoSourceRef.DeviceVideo -> require(source.video == video) { "手机视频来源不匹配" }
        is VideoSourceRef.LibraryVideo -> require(RecordIdentity.isValidId(source.materialId) && video?.libraryMaterialId() == source.materialId) { "素材来源不匹配" }
        null -> Unit
    }
}

fun Flight.videoSourceRef(): VideoSourceRef? = materialId?.let(VideoSourceRef::LibraryVideo)
    ?: localVideoRef()?.let(VideoSourceRef::DeviceVideo)

/** Resolve an owned library URI at the playback boundary; persisted records keep only the UUID. */
fun Flight.playbackVideoRef(): LocalVideoRef? = when (val source = videoSourceRef()) {
    is VideoSourceRef.LibraryVideo -> libraryVideoRef(source.materialId, materialName ?: "素材视频")
    is VideoSourceRef.DeviceVideo -> source.video
    null -> null
}

internal fun Flight.withVideoAssociation(video: LocalVideoRef?, source: VideoSourceRef?, removedName: String?): Flight {
    requireVideoSource(video, source)
    require(removedName == null || source == null)
    val device = (source as? VideoSourceRef.DeviceVideo)?.video
    val material = source as? VideoSourceRef.LibraryVideo
    return copy(videoUri = device?.uriString, videoDisplayName = device?.displayName, videoMimeType = device?.mimeType,
        materialId = material?.materialId, materialName = if (material != null) video?.displayName else removedName,
        materialRemoved = removedName != null)
}

internal fun validateMaterialAssociation(id: String?, name: String?, removed: Boolean, deviceUri: String?) {
    require(id == null || com.risediary.app.data.sync.RecordIdentity.isValidId(id)) { "素材编号无效" }
    require(name == null || (name.isNotBlank() && name.length <= 1024)) { "素材名称无效" }
    require(if (removed) id == null && name != null else (id == null) == (name == null)) { "素材关联信息不完整" }
    require(deviceUri == null || (id == null && name == null && !removed)) { "不能同时关联手机视频和素材库视频" }
}
