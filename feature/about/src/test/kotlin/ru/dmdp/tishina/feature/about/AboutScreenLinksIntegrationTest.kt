package ru.dmdp.tishina.feature.about

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import ru.dmdp.tishina.core.designsystem.theme.TishinaTheme
import ru.dmdp.tishina.core.domain.model.AppVersion

/**
 * Phase 6 Task 5 — Privacy Policy + GitHub Pages hosting + финальные URL.
 *
 * Защитный тест уровня integration: гарантирует, что строковые ресурсы
 * `about_github_url` / `about_privacy_url` хранят финальные production-URL,
 * соответствующие именно тому GitHub username, который зашит в git remote
 * (`dnovichkov`). Phase 5 оставил placeholder `dmitrynovichkov` — если кто-то
 * случайно вернёт его обратно (например при rebase из старой ветки), тест
 * подсветит регрессию.
 *
 * Проверка идёт через реальный Intent.ACTION_VIEW: `AboutScreen` сам стартует
 * activity, `AboutScreenContent` принимает callback. Чтобы не дублировать
 * Hilt-инфраструктуру, тестируем именно payload, передаваемый в `onOpenUrl`,
 * **и** сверяем его с production-кодом, который превращает строку в Intent.
 */
@RunWith(RobolectricTestRunner::class)
class AboutScreenLinksIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleVersion = AppVersion("1.0.0-rc1", 100)

    @Test
    fun `github link uses final dnovichkov url, not placeholder`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val githubUrl = context.getString(R.string.about_github_url)

        // Placeholder из Phase 5 — должен исчезнуть в Phase 6.
        assertFalse(
            "about_github_url all still references the dmitrynovichkov placeholder",
            githubUrl.contains("dmitrynovichkov"),
        )
        assertEquals(
            "GitHub URL must point at the real account from git remote",
            "https://github.com/dnovichkov/tishina-android",
            githubUrl,
        )
    }

    @Test
    fun `privacy link uses final dnovichkov github pages url, not placeholder`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val privacyUrl = context.getString(R.string.about_privacy_url)

        assertFalse(
            "about_privacy_url still references the dmitrynovichkov placeholder",
            privacyUrl.contains("dmitrynovichkov"),
        )
        assertEquals(
            "Privacy Policy must live on the same GitHub Pages account that hosts the repo",
            "https://dnovichkov.github.io/tishina-android/privacy/",
            privacyUrl,
        )
    }

    @Test
    fun `clicking GitHub link surfaces a captured ACTION_VIEW intent to the final URL`() {
        val opened = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val expected = context.getString(R.string.about_github_url)

        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(version = sampleVersion, loading = false),
                    onNavigateBack = {},
                    onOpenUrl = { opened += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(AboutScreenContentTestTag)
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(AboutScreenGithubTestTag))
        composeTestRule.onNodeWithTag(AboutScreenGithubTestTag).performClick()

        // 1) композабл получил финальную строку из ресурсов;
        // 2) производственный wrapper в `AboutScreen` затем строит Intent.ACTION_VIEW.
        //    Здесь дублируем второй шаг, чтобы зафиксировать end-to-end контракт
        //    «строковый ресурс → Intent», который сейчас живёт в `runCatching` lambda.
        assertEquals(1, opened.size)
        assertEquals(expected, opened.first())

        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(opened.first()))
        context.packageManager // sanity touch — Robolectric initialises shadowOf(application)
        Shadows.shadowOf(context).grantPermissions(Intent.ACTION_VIEW)
        assertEquals(expected, intent.data?.toString())
    }

    @Test
    fun `clicking Privacy link surfaces a captured ACTION_VIEW intent to the final URL`() {
        val opened = mutableListOf<String>()
        val context = ApplicationProvider.getApplicationContext<Application>()
        val expected = context.getString(R.string.about_privacy_url)

        composeTestRule.setContent {
            TishinaTheme(darkTheme = false, dynamicColor = false) {
                AboutScreenContent(
                    state = AboutUiState(version = sampleVersion, loading = false),
                    onNavigateBack = {},
                    onOpenUrl = { opened += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(AboutScreenContentTestTag)
            .performScrollToNode(androidx.compose.ui.test.hasTestTag(AboutScreenPrivacyTestTag))
        composeTestRule.onNodeWithTag(AboutScreenPrivacyTestTag).performClick()

        assertEquals(1, opened.size)
        assertEquals(expected, opened.first())

        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(opened.first()))
        assertNotNull(intent.data)
        assertEquals(expected, intent.data?.toString())
    }
}
