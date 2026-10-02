/* Copyright (C) 2026 sky-shunfengjun. SPDX-License-Identifier: GPL-3.0-only */
package com.risediary.app.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Real Android BackHandler registration; requires a connected device/emulator. */
class PageBackScopeTest {
    @get:Rule val compose = createComposeRule()
    private class Owner(override val navigationEventDispatcher: NavigationEventDispatcher) :
        NavigationEventDispatcherOwner

    @Test fun returningPageRegistersAfterItsFormerHostDispatcherWasDisposed() {
        val app = NavigationEventDispatcher()
        val oldEntry = NavigationEventDispatcher(app).apply { dispose() }
        val input = DirectNavigationEventInput().also { app.addInput(it) }
        var backs = 0
        val visible = mutableStateOf(true)
        compose.setContent {
            if (!visible.value) return@setContent
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides Owner(app)) {
                StableNavigationBackHost {
                    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides Owner(oldEntry)) {
                        PageBackScope(true) { PageBackHandler { backs++ } }
                    }
                }
            }
        }
        compose.runOnIdle {
            input.backCompleted()
            assertEquals(1, backs)
        }
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle { app.dispose() }
    }

    @Test fun coveredPageCannotConsumeBackFromTheCurrentPage() {
        var fallback = 0
        var covered = 0
        val app = NavigationEventDispatcher(onBackCompletedFallback = { fallback++ })
        val entry = NavigationEventDispatcher(app)
        val visible = mutableStateOf(true)
        val input = DirectNavigationEventInput().also { app.addInput(it) }
        compose.setContent {
            if (!visible.value) return@setContent
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides Owner(app)) {
                StableNavigationBackHost {
                    CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides Owner(entry)) {
                        PageBackScope(false) { PageBackHandler { covered++ } }
                    }
                }
            }
        }
        compose.runOnIdle {
            input.backCompleted()
            assertEquals(0, covered)
            assertEquals(1, fallback)
        }
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle {
            entry.dispose()
            app.dispose()
        }
    }

    @Test fun repeatedEntryRecreationDoesNotLeaveHandlersOrDisposeTheAppDispatcher() {
        var handled = 0
        var fallback = 0
        val app = NavigationEventDispatcher(onBackCompletedFallback = { fallback++ })
        val input = DirectNavigationEventInput().also { app.addInput(it) }
        val entry = mutableStateOf(NavigationEventDispatcher(app).apply { dispose() })
        val visible = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides Owner(app)) {
                StableNavigationBackHost {
                    if (visible.value) {
                        key(entry.value) {
                            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides Owner(entry.value)) {
                                PageBackScope(true) { PageBackHandler { handled++ } }
                            }
                        }
                    }
                }
            }
        }
        repeat(25) {
            compose.runOnIdle { entry.value = NavigationEventDispatcher(app).apply { dispose() } }
            compose.runOnIdle { input.backCompleted() }
        }
        compose.runOnIdle { visible.value = false }
        compose.runOnIdle {
            input.backCompleted()
            assertEquals(25, handled)
            assertEquals(1, fallback)
            app.dispose()
        }
    }
}
