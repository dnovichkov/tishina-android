package ru.dmdp.tishina.locale

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.dmdp.tishina.core.domain.model.AppLocale

/**
 * FR-18 — verifies that [LocaleSwitcher] translates the [AppLocale] enum
 * into the exact `LocaleListCompat` shape `AppCompatDelegate` expects:
 *
 *  - [AppLocale.Russian] / [AppLocale.English] produce a single-tag list whose
 *    language identifier matches the persisted preference, so `Configuration`
 *    overrides the app context with that locale on the next Activity recreate.
 *  - [AppLocale.System] produces the empty list, which tells AppCompat to
 *    delegate back to the OS locale list (`LocaleManagerCompat.getSystemLocales`).
 *  - Re-applying the same locale stays idempotent — handy for cases where
 *    `MainActivity` re-collects the effect after a configuration change.
 *
 * Tests pin Robolectric to **API 32** so the round-trip through
 * `AppCompatDelegate.getApplicationLocales()` works: on API 33+ the delegate
 * forwards to the system `LocaleManager` (stubbed by Robolectric but never
 * mutated), so writes are silently dropped. On API ≤ 32 AppCompat owns the
 * storage itself (a SharedPreferences-backed file), which Robolectric honors.
 *
 * Each test snapshots the previously-applied locale list and restores it in
 * [tearDown] so a misordered test does not leak `ru`/`en` into the JVM
 * fixture used by sibling tests (Robolectric reuses the static
 * `AppCompatDelegate` state across runs in the same VM).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S_V2])
class LocaleSwitcherTest {

    private val savedLocales: LocaleListCompat = AppCompatDelegate.getApplicationLocales()

    @After
    fun tearDown() {
        AppCompatDelegate.setApplicationLocales(savedLocales)
    }

    @Test
    fun `apply Russian sets ru language tag`() {
        LocaleSwitcher.apply(AppLocale.Russian)

        assertEquals("ru", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun `apply English sets en language tag`() {
        LocaleSwitcher.apply(AppLocale.English)

        assertEquals("en", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    @Test
    fun `apply System clears the per-app locale list`() {
        // Seed with a non-system locale so the empty-list switch is observable.
        LocaleSwitcher.apply(AppLocale.Russian)
        assertEquals("ru", AppCompatDelegate.getApplicationLocales().toLanguageTags())

        LocaleSwitcher.apply(AppLocale.System)

        val locales = AppCompatDelegate.getApplicationLocales()
        assertTrue(
            "AppLocale.System must reset to the empty list so the OS locale list takes over",
            locales.isEmpty,
        )
        assertEquals("", locales.toLanguageTags())
    }

    @Test
    fun `apply is idempotent — re-applying the same locale leaves the list unchanged`() {
        LocaleSwitcher.apply(AppLocale.English)
        val first = AppCompatDelegate.getApplicationLocales().toLanguageTags()

        LocaleSwitcher.apply(AppLocale.English)
        val second = AppCompatDelegate.getApplicationLocales().toLanguageTags()

        assertEquals(first, second)
        assertEquals("en", second)
    }

    @Test
    fun `toLocaleListCompat round-trips every enum value`() {
        // Direct check of the pure mapping function — protects against future enum
        // additions slipping in without a corresponding `when` branch.
        AppLocale.values().forEach { locale ->
            val list = LocaleSwitcher.toLocaleListCompat(locale)
            val expectedTag = locale.tag
            assertEquals(
                "AppLocale.$locale must map to language tag '$expectedTag'",
                expectedTag,
                list.toLanguageTags(),
            )
        }
    }
}
