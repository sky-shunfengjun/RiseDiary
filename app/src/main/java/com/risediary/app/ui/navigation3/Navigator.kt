/*
 * Copyright (C) 2026 sky-shunfengjun
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Navigator 改编自 KernelSU-Style-UI-Kit（GPL-3.0-only）。
 */
package com.risediary.app.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.navBackStackOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Simple navigation helper that owns a back stack.
 */
class Navigator(
    initialKey: Route
) {
    val backStack: NavBackStack = navBackStackOf(initialKey)

    private val resultBus = mutableMapOf<String, MutableSharedFlow<Any>>()

    fun push(key: Route) {
        val existingIndex = backStack.indexOf(key)
        if (existingIndex >= 0) {
            // miuix-nav keys identify saved state and ViewModels. Reuse that
            // entry instead of creating a second page with the same identity.
            while (backStack.lastIndex > existingIndex) backStack.removeAt(backStack.lastIndex)
        } else {
            backStack.add(key)
        }
    }

    fun replace(key: Route) {
        if (backStack.contains(key)) {
            push(key)
            return
        }
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        push(key)
    }

    fun replaceAll(keys: List<Route>) {
        if (keys.isEmpty()) return
        val root = backStack.first() as Route
        val uniqueKeys = (listOf(root) + keys).distinct()
        backStack.clear()
        backStack.addAll(uniqueKeys)
    }

    fun pop() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun popUntil(predicate: (Route) -> Boolean) {
        while (backStack.size > 1 && !predicate(backStack.last() as Route)) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun current(): Route {
        return backStack.last() as Route
    }

    fun backStackSize(): Int {
        return backStack.size
    }

    fun setResult(requestKey: String, value: Any) {
        ensureChannel(requestKey).tryEmit(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> observeResult(requestKey: String): SharedFlow<T> {
        return ensureChannel(requestKey) as SharedFlow<T>
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun clearResult(requestKey: String) {
        ensureChannel(requestKey).resetReplayCache()
    }

    private fun ensureChannel(key: String): MutableSharedFlow<Any> {
        return resultBus.getOrPut(key) { MutableSharedFlow(replay = 1, extraBufferCapacity = 0) }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val routesSerializer = ListSerializer(Route.serializer())
        val Saver: Saver<Navigator, String> = Saver(
            save = { navigator ->
                json.encodeToString(routesSerializer, navigator.backStack.map { it as Route })
            },
            restore = { encoded ->
                val routes = json.decodeFromString(routesSerializer, encoded)
                Navigator(Route.Main).apply { replaceAll(routes) }
            }
        )
    }
}

@Composable
fun rememberNavigator(startRoute: Route): Navigator {
    return rememberSaveable(startRoute, saver = Navigator.Saver) {
        Navigator(startRoute)
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
