package ru.dmdp.tishina.testutils

import androidx.compose.foundation.clickable
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
 * via `TishinaApp(aboutContent = { onBack -> AboutScreenTestStub(onBack) })`.
 *
 * The whole stub surface is clickable and routes through [onNavigateBack] so navigation
 * tests can drive the back affordance the same way the real screen's TopBar back arrow
 * does — see `TishinaNavHostTest.back from About …`.
 */
const val AboutContentStubTestTag: String = "about_content_stub"
const val AboutStubBackTestTag: String = "about_content_stub_back"

@Composable
fun AboutScreenTestStub(
    onNavigateBack: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AboutContentStubTestTag),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(AboutStubBackTestTag)
                .clickable(onClick = onNavigateBack),
        )
    }
}
