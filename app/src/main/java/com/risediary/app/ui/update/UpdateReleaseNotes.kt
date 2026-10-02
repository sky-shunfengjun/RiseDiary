package com.risediary.app.ui.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.mikepenz.markdown.model.MarkdownState
import com.mikepenz.markdown.model.ReferenceLinkHandlerImpl
import com.mikepenz.markdown.model.rememberMarkdownState
import com.risediary.app.update.GitHubRelease
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/** Page changes and download refreshes must never send an existing document back to Loading. */
@Composable
internal fun rememberReleaseNotesState(release: GitHubRelease?): MarkdownState? {
    if (release == null || release.body.isBlank()) return null
    return key(release.tagName, release.body) {
        val flavour = remember { GFMFlavourDescriptor() }
        val parser = remember(flavour) { MarkdownParser(flavour) }
        val links = remember { ReferenceLinkHandlerImpl() }
        rememberMarkdownState(
            content = release.body,
            flavour = flavour,
            parser = parser,
            referenceLinkHandler = links,
        )
    }
}
