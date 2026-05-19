package ru.dmdp.tishina.testutils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

const val MeasureContentStubTestTag: String = "measure_content_stub"

@Composable
fun MeasureScreenTestStub() {
    Box(modifier = Modifier.fillMaxSize().testTag(MeasureContentStubTestTag))
}
