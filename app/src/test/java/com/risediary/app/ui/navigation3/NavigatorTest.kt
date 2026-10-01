package com.risediary.app.ui.navigation3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.runtime.saveable.SaverScope
import org.junit.Test

class NavigatorTest {
    @Test
    fun opening_an_existing_destination_returns_to_its_original_entry() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordDetail(7))
        navigator.push(Route.RecordEdit(7))
        navigator.push(Route.RecordDetail(7))
        assertEquals(listOf(Route.Main, Route.RecordDetail(7)), navigator.backStack.toList())
    }

    @Test
    fun repeated_back_cannot_remove_the_main_page() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordForm(false, 0L, 0L))
        repeat(3) { navigator.pop() }
        assertEquals(listOf(Route.Main), navigator.backStack.toList())
    }

    @Test
    fun popping_to_a_missing_destination_preserves_the_root() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordDetail(7))
        navigator.popUntil { it == Route.About }
        assertEquals(listOf(Route.Main), navigator.backStack.toList())
    }

    @Test
    fun different_record_parameters_keep_distinct_entries() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordDetail(7))
        navigator.push(Route.RecordDetail(8))
        assertEquals(3, navigator.backStackSize())
        assertEquals(Route.RecordDetail(8), navigator.current())
    }

    @Test
    fun saving_a_form_returns_to_its_parent_without_removing_an_earlier_draft() {
        val navigator = Navigator(Route.Main)
        val draft = Route.RecordForm(false, 0L, 0L)
        navigator.push(draft)
        navigator.push(Route.Timer)
        navigator.push(Route.RecordForm(true, 600_000L, 1234L))
        navigator.pop()
        assertEquals(listOf(Route.Main, draft, Route.Timer), navigator.backStack.toList())
    }

    @Test
    fun saved_stack_uses_serialized_routes_and_preserves_timer_parameters() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordForm(true, 600_000L, 1234L))
        val scope = object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        val saved = with(Navigator.Saver) { scope.save(navigator) }
        assertTrue("Saved navigation must be a portable serialized value", saved is String)
        val restored = requireNotNull(Navigator.Saver.restore(requireNotNull(saved)))
        assertEquals(navigator.backStack.toList(), restored.backStack.toList())
    }

    @Test
    fun an_empty_restored_stack_falls_back_to_the_main_page() {
        val restored = requireNotNull(Navigator.Saver.restore("[]"))
        assertEquals(listOf(Route.Main), restored.backStack.toList())
    }

    @Test
    fun replacing_with_an_existing_route_does_not_create_duplicate_entries() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordDetail(7))
        navigator.push(Route.RecordEdit(7))
        navigator.replace(Route.RecordDetail(7))
        assertEquals(listOf(Route.Main, Route.RecordDetail(7)), navigator.backStack.toList())
    }

    @Test
    fun replacing_the_root_preserves_the_main_page() {
        val navigator = Navigator(Route.Main)
        navigator.replace(Route.About)
        navigator.pop()
        assertEquals(Route.Main, navigator.current())
    }
}
