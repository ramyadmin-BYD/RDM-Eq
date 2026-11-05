package com.rdm.eq

import com.rdm.eq.update.ReleaseParser
import com.rdm.eq.update.UpdateException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReleaseParserTest {
    private val sha = "a".repeat(64)
    private val cert = "b".repeat(64)
    private val meta = "rdm-eq-update.json"

    private fun release(tag: String, draft: Boolean = false, pre: Boolean = false, withMeta: Boolean = true, apkSize: Long = 1000, digest: String? = null) =
        JSONObject().put("tag_name", tag).put("name", "RDM Eq $tag").put("body", "## Changes\n- one")
            .put("draft", draft).put("prerelease", pre).put("published_at", "2026-11-05T10:00:00Z")
            .put("assets", JSONArray().apply {
                put(JSONObject().put("name", "RDM-Eq-${tag.removePrefix("v")}.apk").put("browser_download_url", "https://github.com/x/y/releases/download/$tag/a.apk")
                    .put("size", apkSize).apply { digest?.let { put("digest", "sha256:$it") } })
                if (withMeta) put(JSONObject().put("name", meta).put("browser_download_url", "https://github.com/x/y/m.json").put("size", 300))
            })

    private fun metaJson(code: Int = 3, app: String = "com.rdm.eq", s: String = sha, c: String = cert, apk: String = "RDM-Eq-1.2.0.apk", size: Long = 1000, minSdk: Int = 28) =
        JSONObject().put("applicationId", app).put("versionCode", code).put("versionName", "1.2.0").put("apk", apk)
            .put("sha256", s).put("size", size).put("signingCertSha256", c).put("minSdk", minSdk).toString()

    @Test fun ignoresDraftsAndPrereleasesUnlessBeta() {
        val json = JSONArray().put(release("v1.3.0", draft = true)).put(release("v1.2.1", pre = true)).put(release("v1.2.0")).toString()
        val all = ReleaseParser.parseReleases(json)
        assertEquals(listOf("v1.2.0"), ReleaseParser.candidates(all, includeBeta = false, metadataAsset = meta).map { it.tag })
        assertEquals(listOf("v1.2.1", "v1.2.0"), ReleaseParser.candidates(all, includeBeta = true, metadataAsset = meta).map { it.tag })
    }

    @Test fun ignoresReleasesWithoutMetadata() {
        val all = ReleaseParser.parseReleases(JSONArray().put(release("v9.0.0", withMeta = false)).toString())
        assertTrue(ReleaseParser.candidates(all, false, meta).isEmpty())
    }

    @Test(expected = UpdateException::class)
    fun malformedReleaseListThrows() { ReleaseParser.parseReleases("<html>rate limited</html>") }

    @Test fun parsesGitHubDigest() {
        val r = ReleaseParser.parseReleases(JSONArray().put(release("v1.2.0", digest = sha)).toString()).single()
        assertEquals(sha, r.assets.first().githubSha256)
    }

    @Test fun newerReleaseIsOffered() {
        val r = ReleaseParser.parseReleases(JSONArray().put(release("v1.2.0")).toString()).single()
        val u = ReleaseParser.validate(r, ReleaseParser.parseMetadata(metaJson()), "com.rdm.eq", 2, setOf(cert), 33)!!
        assertEquals("RDM-Eq-1.2.0.apk", u.apk.name)
        assertEquals(3, u.metadata.versionCode)
    }

    @Test fun sameOrOlderVersionIsNotAnUpdate() {
        val r = ReleaseParser.parseReleases(JSONArray().put(release("v1.2.0")).toString()).single()
        assertNull(ReleaseParser.validate(r, ReleaseParser.parseMetadata(metaJson(code = 3)), "com.rdm.eq", 3, setOf(cert), 33))
        assertNull(ReleaseParser.validate(r, ReleaseParser.parseMetadata(metaJson(code = 2)), "com.rdm.eq", 3, setOf(cert), 33))
    }

    private fun expectFail(block: () -> Unit) { try { block(); fail("expected UpdateException") } catch (e: UpdateException) { } }

    @Test fun rejectsWrongAppWrongKeyMissingAssetSizeAndDigestMismatch() {
        val r = ReleaseParser.parseReleases(JSONArray().put(release("v1.2.0")).toString()).single()
        expectFail { ReleaseParser.validate(r, ReleaseParser.parseMetadata(metaJson(app = "com.evil")), "com.rdm.eq", 2, setOf(cert), 33) }
        expectFail { ReleaseParser.validate(r, ReleaseParser.parseMetadata(metaJson(c = "c".repeat(64))), "com.rdm.eq", 2, setOf(cert), 33) }
        expectFail { ReleaseParser.validate(r, ReleaseParser.parseMetadata(metaJson(apk = "Other.apk")), "com.rdm.eq", 2, setOf(cert), 33) }
        expectFail { ReleaseParser.validate(r, ReleaseParser.parseMetadata(metaJson(size = 999)), "com.rdm.eq", 2, setOf(cert), 33) }
        expectFail { ReleaseParser.validate(r, ReleaseParser.parseMetadata(metaJson(minSdk = 40)), "com.rdm.eq", 2, setOf(cert), 33) }
        val withDigest = ReleaseParser.parseReleases(JSONArray().put(release("v1.2.0", digest = "d".repeat(64))).toString()).single()
        expectFail { ReleaseParser.validate(withDigest, ReleaseParser.parseMetadata(metaJson()), "com.rdm.eq", 2, setOf(cert), 33) }
    }

    @Test fun rejectsMalformedMetadata() {
        expectFail { ReleaseParser.parseMetadata("not json") }
        expectFail { ReleaseParser.parseMetadata(metaJson(s = "xyz")) }
        expectFail { ReleaseParser.parseMetadata(metaJson(apk = "../../evil.apk")) }
        expectFail { ReleaseParser.parseMetadata(JSONObject().put("applicationId", "com.rdm.eq").toString()) }
    }

    @Test fun certWithColonsIsNormalised() {
        val colon = cert.chunked(2).joinToString(":").uppercase()
        assertEquals(cert, ReleaseParser.parseMetadata(metaJson(c = colon)).signingCertSha256)
    }

    @Test fun formatsDates() {
        assertEquals("5 Nov 2026", ReleaseParser.formatDate("2026-11-05T10:00:00Z"))
        assertEquals("garbage", ReleaseParser.formatDate("garbage"))
    }
}
