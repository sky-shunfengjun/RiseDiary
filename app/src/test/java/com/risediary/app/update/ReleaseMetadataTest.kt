package com.risediary.app.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseMetadataTest {
    @Test fun releaseRetainsNotesAndSelectsThePublishedApk() {
        val release = parseReleaseResponse("""{
          "tag_name":"v1.1.3","name":"Release","draft":false,"prerelease":false,
          "html_url":"https://github.com/sky-shunfengjun/RiseDiary/releases/tag/v1.1.3",
          "body":"## Changes\n- Fixed a crash",
          "assets":[
            {"id":1,"name":"source.zip","state":"uploaded","browser_download_url":"https://github.com/sky-shunfengjun/RiseDiary/releases/download/v1.1.3/source.zip"},
            {"id":2,"name":"RiseDiary-v1.1.3.apk","state":"uploaded","size":1024,"digest":"sha256:${"a".repeat(64)}","browser_download_url":"https://github.com/sky-shunfengjun/RiseDiary/releases/download/v1.1.3/RiseDiary-v1.1.3.apk"}
          ]
        }""")
        assertEquals("## Changes\n- Fixed a crash", release.body)
        assertEquals(2L, selectReleaseApk(release)?.id)
        assertEquals("a".repeat(64), selectReleaseApk(release)?.sha256)
    }

    @Test fun ambiguousApksRequireTheReleasePage() {
        assertNull(selectReleaseApk(release(listOf(asset(1, "arm64.apk"), asset(2, "x86.apk")))))
    }

    @Test fun officialNamedAssetWinsOverOtherApks() {
        assertEquals(2L, selectReleaseApk(release(listOf(asset(1, "arm64.apk"), asset(2, "RiseDiary-v1.1.3.apk"))))?.id)
    }

    @Test fun draftPrereleaseAndMalformedVersionsAreNotReportedAsLatest() {
        for (json in listOf(
            """{"tag_name":"v1.1.3","draft":true}""",
            """{"tag_name":"v1.1.3","prerelease":true}""",
            """{"tag_name":"latest"}""",
            """{"tag_name":"v1.1.3","draft":false,"prerelease":"invalid"}"""
        )) assertTrue(runCatching { parseReleaseResponse(json) }.isFailure)
    }

    @Test fun apkUrlsCannotEscapeTheProjectReleaseDirectory() {
        for (url in listOf(
            "http://github.com/sky-shunfengjun/RiseDiary/releases/download/v1.1.3/app.apk",
            "https://github.com.evil.test/sky-shunfengjun/RiseDiary/releases/download/v1.1.3/app.apk",
            "https://github.com/other/repo/releases/download/v1.1.3/app.apk",
            "https://github.com/sky-shunfengjun/RiseDiary/releases/download/../app.apk",
            "https://github.com/sky-shunfengjun/RiseDiary/releases/download/%2e%2e/app.apk"
        )) assertFalse(isProjectApkUrl(url))
    }

    @Test fun proxyChangesOnlyTheApkDownloadUrl() {
        val url = "https://github.com/sky-shunfengjun/RiseDiary/releases/download/v1.1.3/app.apk"
        assertEquals(url, UpdateChannel.OFFICIAL.downloadUrl(url))
        assertEquals("https://gh.sevencdn.com/https://github.com/sky-shunfengjun/RiseDiary/releases/download/v1.1.3/app.apk", UpdateChannel.PROXY_7ED.downloadUrl(url))
    }

    private fun release(assets: List<GitHubAsset>) = GitHubRelease("v1.1.3", "v1.1.3", RELEASES_URL, assets = assets)
    private fun asset(id: Long, name: String) = GitHubAsset(id, name,
        "https://github.com/sky-shunfengjun/RiseDiary/releases/download/v1.1.3/$name", 1024)
}
