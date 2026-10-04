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
    fun opening_video_again_creates_a_fresh_entry_even_when_an_older_player_is_retained() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordVideo(7))
        navigator.push(Route.RecordDetail(7))
        navigator.push(Route.RecordVideo(7))
        assertEquals(4, navigator.backStackSize())
        assertEquals(Route.RecordDetail(7), navigator.backStack[2])
    }

    @Test
    fun restoring_video_stack_preserves_the_current_opening_and_preview_reference() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordVideo(7))
        navigator.push(Route.VideoPreview(com.risediary.app.media.LocalVideoRef(
            "content://com.android.providers.media.documents/document/video%3A12", "测试.mp4", "video/mp4")))
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = requireNotNull(with(Navigator.Saver) { scope.save(navigator) })
        val restored = requireNotNull(Navigator.Saver.restore(saved))
        assertEquals(navigator.backStack.toList(), restored.backStack.toList())
    }

    @Test
    fun timer_and_draft_identities_survive_navigation_restore() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.VideoTimer("session"))
        navigator.push(Route.RecordForm(false, 0L, 0L, "draft"))
        val scope = object : SaverScope { override fun canBeSaved(value: Any) = true }
        val saved = requireNotNull(with(Navigator.Saver) { scope.save(navigator) })
        val restored = requireNotNull(Navigator.Saver.restore(saved))
        assertEquals(Route.VideoTimer("session"), restored.backStack[1])
        assertEquals(Route.RecordForm(false, 0L, 0L, "draft"), restored.current())
    }

    @Test
    fun a_new_draft_does_not_reuse_a_completed_form_entry() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordForm(false, 0L, 0L, "first"))
        navigator.push(Route.RecordForm(false, 0L, 0L, "second"))
        assertEquals(3, navigator.backStackSize())
        assertEquals(Route.RecordForm(false, 0L, 0L, "second"), navigator.current())
    }

    @Test fun missingFormAlsoRemovesItsUnsavedVideoPreview() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.ModeSelect)
        navigator.push(Route.RecordForm(false, 0L, 0L, "expired"))
        navigator.push(Route.VideoPreview(com.risediary.app.media.LocalVideoRef("content://video/1", "video", "video/mp4")))
        navigator.discardExpiredForms { false }
        assertEquals(Route.ModeSelect, navigator.current())
        assertEquals(2, navigator.backStackSize())
    }

    @Test fun activityRecreationKeepsAStillLiveFormSession() {
        val navigator = Navigator(Route.Main)
        navigator.push(Route.RecordForm(false, 0L, 0L, "live"))
        navigator.discardExpiredForms { it == "live" }
        assertEquals(Route.RecordForm(false, 0L, 0L, "live"), navigator.current())
    }

    @Test
    fun replacing_the_root_preserves_the_main_page() {
        val navigator = Navigator(Route.Main)
        navigator.replace(Route.About)
        navigator.pop()
        assertEquals(Route.Main, navigator.current())
    }
}
