package ru.dmdp.tishina.testutils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Inert composable stand-ins for [HistoryScreen]/[DetailScreen] inside `TishinaApp` tests.
 *
 * The real screens construct their ViewModels through [hiltViewModel], which under
 * Robolectric resolves through the host [ComponentActivity] — and that activity does NOT
 * implement Hilt's `GeneratedComponentManager` (the manifest's `@HiltAndroidApp` only
 * applies to the Application class, not the test Activity). Calling them from a navigation
 * test crashes with `IllegalStateException at EntryPoints.java:62`.
 *
 * Production callers of `TishinaApp(...)` keep using the default screen composables; tests
 * that only need to assert routing/destination state inject these stubs instead.
 */
const val HistoryContentStubTestTag: String = "history_content_stub"
const val DetailContentStubTestTag: String = "detail_content_stub"

@Composable
fun HistoryScreenTestStub(
    @Suppress("UNUSED_PARAMETER") onNavigateToDetail: (Long) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onNavigateToMeasure: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().testTag(HistoryContentStubTestTag))
}

@Composable
fun DetailScreenTestStub(
    @Suppress("UNUSED_PARAMETER") measurementId: Long = 0L,
    @Suppress("UNUSED_PARAMETER") onNavigateBack: () -> Unit = {},
) {
    Box(modifier = Modifier.fillMaxSize().testTag(DetailContentStubTestTag))
}
