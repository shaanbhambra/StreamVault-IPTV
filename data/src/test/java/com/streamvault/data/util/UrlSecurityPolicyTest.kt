package com.streamvault.data.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlSecurityPolicyTest {

    @Test
    fun `validateXtreamServerUrl allows http and https endpoints`() {
        assertThat(UrlSecurityPolicy.validateXtreamServerUrl("http://provider.example.com")).isNull()
        assertThat(UrlSecurityPolicy.validateXtreamServerUrl("https://provider.example.com")).isNull()
        assertThat(UrlSecurityPolicy.validateXtreamServerUrl("ftp://provider.example.com"))
            .isEqualTo("Xtream server URLs must use HTTP or HTTPS.")
    }

    @Test
    fun `validateXtreamEpgUrl allows http and https endpoints`() {
        assertThat(UrlSecurityPolicy.validateXtreamEpgUrl("http://provider.example.com/xmltv.php")).isNull()
        assertThat(UrlSecurityPolicy.validateXtreamEpgUrl("https://provider.example.com/xmltv.php")).isNull()
        assertThat(UrlSecurityPolicy.validateXtreamEpgUrl("file:///storage/emulated/0/guide.xml"))
            .isEqualTo("Xtream EPG URLs must use HTTP or HTTPS.")
    }

    @Test
    fun `validatePlaylistSourceUrl allows local http and https`() {
        assertThat(UrlSecurityPolicy.validatePlaylistSourceUrl("file:///storage/emulated/0/playlist.m3u")).isNull()
        assertThat(UrlSecurityPolicy.validatePlaylistSourceUrl("content://downloads/public_downloads/1")).isNull()
        assertThat(UrlSecurityPolicy.validatePlaylistSourceUrl("https://example.com/playlist.m3u")).isNull()
        assertThat(UrlSecurityPolicy.validatePlaylistSourceUrl("http://example.com/playlist.m3u")).isNull()
        assertThat(UrlSecurityPolicy.validatePlaylistSourceUrl("ftp://example.com/playlist.m3u"))
            .isEqualTo("Playlist sources must use HTTP, HTTPS, or point to a local file.")
    }

    @Test
    fun `stream entry urls reject newline injection`() {
        assertThat(UrlSecurityPolicy.isAllowedStreamEntryUrl("https://example.com/live.ts%0AInjected: true")).isFalse()
        assertThat(UrlSecurityPolicy.isAllowedStreamEntryUrl("https://example.com/live.ts")).isTrue()
    }

    @Test
    fun `stream entry urls reject double-encoded newline injection`() {
        // %250A -> %0A -> \n requires two-pass decode to catch
        assertThat(UrlSecurityPolicy.isAllowedStreamEntryUrl("https://example.com/live.ts%250AInjected: true")).isFalse()
        // %250D -> %0D -> \r
        assertThat(UrlSecurityPolicy.isAllowedStreamEntryUrl("https://example.com/live.ts%250DInjected: true")).isFalse()
        // Mixed-case double-encoded
        assertThat(UrlSecurityPolicy.isAllowedStreamEntryUrl("https://example.com/live.ts%250aInjected: true")).isFalse()
    }

    @Test
    fun `stream entry urls reject tab injection`() {
        // %09 (tab) can split structured log entries
        assertThat(UrlSecurityPolicy.isAllowedStreamEntryUrl("https://example.com/live.ts%09Injected: true")).isFalse()
    }

    @Test
    fun `sanitizeImportedAssetUrl drops unsupported schemes`() {
        assertThat(UrlSecurityPolicy.sanitizeImportedAssetUrl("ftp://example.com/logo.png")).isNull()
        assertThat(UrlSecurityPolicy.sanitizeImportedAssetUrl("https://example.com/logo.png"))
            .isEqualTo("https://example.com/logo.png")
    }
}
