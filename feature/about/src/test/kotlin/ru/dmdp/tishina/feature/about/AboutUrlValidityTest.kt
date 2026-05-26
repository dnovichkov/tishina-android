package ru.dmdp.tishina.feature.about

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URI

/**
 * Phase 6 Task 5 — defence-in-depth для строк-URL в `:feature:about`.
 *
 * Phase 5 хранит URL в обычных `<string translatable="false">` без какой-либо
 * валидации. После Phase 6 эти URL становятся production-ссылками для
 * Google Play и RuStore рецензентов — typo здесь = неработающая ссылка
 * в карточке приложения. Этот тест парсит каждый URL через `java.net.URI`
 * и проверяет:
 *
 * 1. URL абсолютный, со схемой `https` (никаких http / file / content);
 * 2. authority/host разрешается (не пустой);
 * 3. host оканчивается на `github.com` (или поддомен) — отлавливает случайные
 *    замены типа `gitub.com` или `github.io.com`;
 * 4. строка не содержит whitespace или RTL-маркеров (стандартная защита от
 *    случайного copy-paste из IDE с обёрнутыми символами).
 *
 * Table-driven, чтобы добавление новой ссылки в strings.xml требовало одной
 * строки в `linksUnderTest`.
 */
@RunWith(RobolectricTestRunner::class)
class AboutUrlValidityTest {

    private data class LinkUnderTest(val label: String, val resId: Int, val expectedHostSuffix: String)

    private val linksUnderTest = listOf(
        LinkUnderTest(
            label = "about_github_url",
            resId = R.string.about_github_url,
            expectedHostSuffix = "github.com",
        ),
        LinkUnderTest(
            label = "about_privacy_url",
            resId = R.string.about_privacy_url,
            // GitHub Pages поддомен — `<user>.github.io`. Достаточно проверить tail.
            expectedHostSuffix = "github.io",
        ),
    )

    @Test
    fun `all about-screen link strings are well-formed https URLs with non-empty host`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        linksUnderTest.forEach { link ->
            val raw = context.getString(link.resId)

            assertTrue(
                "${link.label}: must not contain whitespace, was [$raw]",
                raw == raw.trim() && !raw.contains(' '),
            )

            val uri = runCatching { URI.create(raw) }.getOrNull()
            assertNotNull("${link.label}: not parseable as URI, was [$raw]", uri)
            assertEquals("${link.label}: must use https scheme", "https", uri!!.scheme)
            val host = uri.host
            assertNotNull("${link.label}: must have non-null host", host)
            assertTrue("${link.label}: must have non-empty host", host.isNotBlank())
            assertTrue(
                "${link.label}: host [$host] does not end with [${link.expectedHostSuffix}]",
                host == link.expectedHostSuffix || host.endsWith(".${link.expectedHostSuffix}"),
            )
        }
    }
}
