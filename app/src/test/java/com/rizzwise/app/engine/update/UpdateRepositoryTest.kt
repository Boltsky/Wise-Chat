package com.rizzwise.app.engine.update

import com.rizzwise.app.BuildConfig
import com.rizzwise.app.data.repository.UpdateRepository
import com.rizzwise.app.domain.model.UpdateChannel
import org.junit.Assert.*
import org.junit.Test

class UpdateRepositoryTest {

    private val repository = UpdateRepository(preferencesManager = null)

    @Test
    fun `test parseVersionManifestJson correctly parses all fields`() {
        val json = """
            {
              "versionCode": 3,
              "versionName": "1.3.0",
              "commitSha": "abc1234",
              "releaseTitle": "v1.3.0 - AI Speed Boost",
              "releaseNotes": "• 50% faster reply generation\n• New personality presets",
              "downloadUrl": "https://github.com/Boltsky/Wise-Chat/raw/main/apk/wise-chat-release.apk",
              "githubReleaseUrl": "https://github.com/Boltsky/Wise-Chat",
              "forceUpdate": true,
              "minSupportedVersionCode": 1,
              "publishedAt": "2026-10-31T12:00:00Z"
            }
        """.trimIndent()

        val info = repository.parseVersionManifestJson(json)

        assertEquals(3, info.remoteVersionCode)
        assertEquals("1.3.0", info.remoteVersionName)
        assertEquals("abc1234", info.commitSha)
        assertEquals("v1.3.0 - AI Speed Boost", info.releaseTitle)
        assertTrue(info.releaseNotes.contains("50% faster"))
        assertEquals("https://github.com/Boltsky/Wise-Chat/raw/main/apk/wise-chat-release.apk", info.downloadUrl)
        assertEquals("https://github.com/Boltsky/Wise-Chat", info.githubUrl)
        assertTrue(info.forceUpdate)
        assertEquals("2026-10-31T12:00:00Z", info.publishedAt)
        assertFalse(info.isUpdateAvailable) // parse alone does not set comparison
    }

    @Test
    fun `test parseGitHubCommitJson extracts commit message and short sha`() {
        val json = """
            {
              "sha": "4b95dd4890abcdef1234567890abcdef12345678",
              "commit": {
                "message": "feat: add version control and update checker\n\nDetailed description here",
                "author": {
                  "date": "2026-10-31T10:30:00Z"
                }
              }
            }
        """.trimIndent()

        val info = repository.parseGitHubCommitJson(json)

        assertEquals("4b95dd4", info.commitSha)
        assertTrue(info.releaseTitle.contains("4b95dd4"))
        assertTrue(info.releaseNotes.contains("feat: add version control"))
        assertEquals("2026-10-31T10:30:00Z", info.publishedAt)
        assertTrue(info.githubUrl.contains("commit/4b95dd4890abcdef"))
    }

    @Test
    fun `test version code comparison accurately identifies newer version`() {
        val currentCode = BuildConfig.VERSION_CODE

        val olderManifest = repository.parseVersionManifestJson("""{"versionCode": ${currentCode - 1}, "versionName": "0.9.0"}""")
        val sameManifest = repository.parseVersionManifestJson("""{"versionCode": $currentCode, "versionName": "${BuildConfig.VERSION_NAME}"}""")
        val newerManifest = repository.parseVersionManifestJson("""{"versionCode": ${currentCode + 1}, "versionName": "2.0.0"}""")

        assertFalse(olderManifest.remoteVersionCode > currentCode)
        assertFalse(sameManifest.remoteVersionCode > currentCode)
        assertTrue(newerManifest.remoteVersionCode > currentCode)
    }

    @Test
    fun `test default URLs provided when manifest json fields are missing`() {
        val minimalJson = """
            {
              "versionCode": 2,
              "versionName": "1.2.0"
            }
        """.trimIndent()

        val info = repository.parseVersionManifestJson(minimalJson)

        assertEquals(2, info.remoteVersionCode)
        assertEquals("1.2.0", info.remoteVersionName)
        assertEquals(UpdateRepository.DEFAULT_DOWNLOAD_URL, info.downloadUrl)
        assertEquals(UpdateRepository.GITHUB_REPO_URL, info.githubUrl)
        assertFalse(info.forceUpdate)
    }

    @Test
    fun `test update channels have clear titles and descriptions`() {
        assertEquals("Stable Releases", UpdateChannel.STABLE.title)
        assertEquals("Fast Track (All Commits)", UpdateChannel.ALL_COMMITS.title)
        assertTrue(UpdateChannel.ALL_COMMITS.description.contains("main"))
    }

    @Test
    fun `test parseVersionManifestJson supports here now platform manifest format`() {
        val hereNowJson = """
            {
              "appId": "rizzwise",
              "appName": "RizzWise",
              "version": "1.3.0",
              "versionCode": 3,
              "releaseDate": "2026-11-05",
              "mandatory": false,
              "minSupportedVersion": "1.0.0",
              "platforms": {
                "android": {
                  "fileName": "rizzwise-release.apk",
                  "downloadUrl": "/releases/rizzwise/rizzwise-release.apk",
                  "fileSize": "15.7 MB",
                  "bytes": 15691646,
                  "sha256": "c981b18946efc5f3cbc212dd5b5ce4fc40f2e266e7800cc59fa8afc172e1427b"
                }
              },
              "changelog": [
                "Dual update endpoints (here.now CDN + GitHub)",
                "Refined pick-up lines and conversation starters",
                "Ultra-fast edge update synchronization"
              ],
              "releaseNotesUrl": "/#rizzwise"
            }
        """.trimIndent()

        val info = repository.parseVersionManifestJson(hereNowJson)

        assertEquals(3, info.remoteVersionCode)
        assertEquals("1.3.0", info.remoteVersionName)
        assertEquals("RizzWise v1.3.0", info.releaseTitle)
        assertTrue(info.releaseNotes.contains("• Dual update endpoints"))
        assertTrue(info.releaseNotes.contains("• Refined pick-up lines"))
        assertEquals("https://boltsky-release-hub.vercel.app/releases/rizzwise/rizzwise-release.apk", info.downloadUrl)
        assertEquals("https://boltsky-release-hub.vercel.app/#rizzwise", info.githubUrl)
        assertFalse(info.forceUpdate)
        assertEquals("2026-11-05", info.publishedAt)
    }

    @Test
    fun `test absolute download URLs are preserved as is`() {
        val json = """
            {
              "versionCode": 3,
              "versionName": "1.3.0",
              "downloadUrl": "https://custom.cdn.com/apk/release.apk"
            }
        """.trimIndent()

        val info = repository.parseVersionManifestJson(json)
        assertEquals("https://custom.cdn.com/apk/release.apk", info.downloadUrl)
    }
}
