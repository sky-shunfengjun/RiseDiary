/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.about

internal data class ThirdPartyProject(val id: String, val name: String, val url: String)

/** Direct app libraries and copied/adapted source projects. Full notices and licenses remain separate. */
internal val thirdPartyProjects = listOf(
    ThirdPartyProject("miuix", "miuix", "https://github.com/compose-miuix-ui/miuix"),
    ThirdPartyProject("liquid-glass", "AndroidLiquidGlass / Backdrop", "https://github.com/Kyant0/AndroidLiquidGlass"),
    ThirdPartyProject("capsule", "Capsule", "https://github.com/Kyant0/Capsule"),
    ThirdPartyProject("hyperceiler", "HyperCeiler", "https://github.com/ReChronoRain/HyperCeiler"),
    ThirdPartyProject("kernelsu", "KernelSU", "https://github.com/tiann/KernelSU"),
    ThirdPartyProject("kernelsu-ui", "KernelSU-Style-UI-Kit", "https://github.com/chenaizhang/KernelSU-Style-UI-Kit"),
    ThirdPartyProject("hyperisland", "HyperIsland", "https://github.com/1812z/HyperIsland"),
    ThirdPartyProject("lucide", "Lucide", "https://github.com/lucide-icons/lucide"),
    ThirdPartyProject("compose", "Jetpack Compose / Material 3", "https://developer.android.com/compose"),
    ThirdPartyProject("media3", "AndroidX Media3", "https://developer.android.com/media/media3"),
    ThirdPartyProject("room", "AndroidX Room", "https://developer.android.com/jetpack/androidx/releases/room"),
    ThirdPartyProject("datastore", "AndroidX DataStore", "https://developer.android.com/jetpack/androidx/releases/datastore"),
    ThirdPartyProject("activity", "AndroidX Activity", "https://developer.android.com/jetpack/androidx/releases/activity"),
    ThirdPartyProject("lifecycle", "AndroidX Lifecycle", "https://developer.android.com/jetpack/androidx/releases/lifecycle"),
    ThirdPartyProject("navigation", "AndroidX Navigation", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    ThirdPartyProject("navigationevent", "AndroidX Navigation Event", "https://developer.android.com/jetpack/androidx/releases/navigationevent"),
    ThirdPartyProject("work", "AndroidX WorkManager", "https://developer.android.com/jetpack/androidx/releases/work"),
    ThirdPartyProject("biometric", "AndroidX Biometric", "https://developer.android.com/jetpack/androidx/releases/biometric"),
    ThirdPartyProject("hilt", "Dagger / Hilt", "https://dagger.dev/hilt/"),
    ThirdPartyProject("androidx-hilt", "AndroidX Hilt", "https://developer.android.com/jetpack/androidx/releases/hilt"),
    ThirdPartyProject("kotlin", "Kotlin", "https://github.com/JetBrains/kotlin"),
    ThirdPartyProject("coroutines", "kotlinx.coroutines", "https://github.com/Kotlin/kotlinx.coroutines"),
    ThirdPartyProject("serialization", "kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization"),
    ThirdPartyProject("vico", "Vico", "https://github.com/patrykandpatrick/vico"),
    ThirdPartyProject("markdown-renderer", "Multiplatform Markdown Renderer", "https://github.com/mikepenz/multiplatform-markdown-renderer"),
)
