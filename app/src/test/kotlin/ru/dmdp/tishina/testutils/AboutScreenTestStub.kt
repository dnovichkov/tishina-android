package ru.dmdp.tishina.testutils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Inert stand-in for the real `AboutScreen` inside `TishinaApp` navigation tests.
 *
 * `AboutScreen` resolves `AboutViewModel` through `hiltViewModel()` which under Robolectric
 * (without `@HiltAndroidTest` infra) explodes at `EntryPoints.java:62`. Tests that navigate
 * to the About destination only to assert TopBar / back-stack behavior inject this stub
 * via `TishinaApp(aboutContent = { AboutScreenTestStub() })`.
 */
const val AboutContentStubTestTag: String = "about_content_stub"

@Composable
fun AboutScreenTestStub(
    @Suppress("UNUSED_PARAMETER") onNavigateBack: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().testTag(AboutContentStubTestTag))
}
