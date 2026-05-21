package ru.dmdp.tishina.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import ru.dmdp.tishina.core.domain.model.AppLocale
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.model.ThemeMode
import ru.dmdp.tishina.core.domain.model.TimeWeighting
import ru.dmdp.tishina.core.ui.preferences.ChoicePreference
import ru.dmdp.tishina.core.ui.preferences.PreferenceCategory
import ru.dmdp.tishina.core.ui.preferences.SliderPreference
import ru.dmdp.tishina.core.ui.preferences.SwitchPreference
import ru.dmdp.tishina.core.ui.R as CoreUiR

const val SettingsScreenTestTag: String = "tishina_settings_screen"
const val SettingsScreenLoadingTestTag: String = "tishina_settings_screen_loading"
const val SettingsScreenContentTestTag: String = "tishina_settings_screen_content"
const val SettingsAboutLinkTestTag: String = "tishina_settings_about_link"

/**
 * Stateful entry point — wires the [SettingsViewModel] and routes effects to a
 * snackbar host. `onApplyLocale` lifts the locale-apply signal up to
 * `MainActivity` (Task 7); `onAboutClick` will route to the About screen once
 * Phase 5 builds it.
 *
 * `isDynamicColorSupported` is hoisted as a parameter (default = runtime API
 * check) so screenshot tests can lock the API-≤30 layout without running on a
 * Robolectric SDK 30 image.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit = {},
    onAboutClick: () -> Unit = {},
    onApplyLocale: (AppLocale) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    isDynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = androidx.compose.ui.platform.LocalContext.current.resources

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is SettingsUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(resources.getString(effect.messageRes))
                }
                is SettingsUiEffect.ApplyAppLocale -> onApplyLocale(effect.locale)
            }
        }
    }

    SettingsScreenContent(
        state = state,
        isDynamicColorSupported = isDynamicColorSupported,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        onAboutClick = onAboutClick,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Pure / stateless body of the Settings screen. Tests and screenshot fixtures
 * call this directly with a synthetic [SettingsUiState] — no Hilt or DataStore
 * required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    isDynamicColorSupported: Boolean,
    onEvent: (SettingsUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(SettingsScreenTestTag),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CoreUiR.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(CoreUiR.string.settings_back_cd),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(SettingsScreenLoadingTestTag),
                )
            }
        } else {
            SettingsScreenBody(
                state = state,
                isDynamicColorSupported = isDynamicColorSupported,
                onEvent = onEvent,
                onAboutClick = onAboutClick,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun SettingsScreenBody(
    state: SettingsUiState,
    isDynamicColorSupported: Boolean,
    onEvent: (SettingsUiEvent) -> Unit,
    onAboutClick: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(SettingsScreenContentTestTag),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item("category_measurement") {
            MeasurementSection(state = state, onEvent = onEvent)
        }
        item("category_appearance") {
            AppearanceSection(
                state = state,
                isDynamicColorSupported = isDynamicColorSupported,
                onEvent = onEvent,
            )
        }
        item("category_language") {
            LanguageSection(state = state, onEvent = onEvent)
        }
        item("footer") {
            AboutFooter(onAboutClick = onAboutClick)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MeasurementSection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    val calibrationFormatRes = CoreUiR.string.settings_calibration_format
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    PreferenceCategory(title = stringResource(CoreUiR.string.settings_category_measurement)) {
        SliderPreference(
            title = stringResource(CoreUiR.string.settings_calibration_title),
            value = state.calibrationOffsetDb,
            onValueChange = { onEvent(SettingsUiEvent.ChangeCalibration(it)) },
            valueRange = AppearanceSettings.CALIBRATION_MIN_DB..AppearanceSettings.CALIBRATION_MAX_DB,
            // (max - min) / step - 1 = (20 - (-20)) / 0.1 - 1 = 399 inner steps.
            steps = 399,
            valueFormatter = { v -> resources.getString(calibrationFormatRes, v) },
            onResetClick = { onEvent(SettingsUiEvent.ResetCalibration) },
            resetButtonLabel = stringResource(CoreUiR.string.settings_calibration_reset),
            description = stringResource(CoreUiR.string.settings_calibration_description),
            valueContentDescription = { v ->
                resources.getString(
                    CoreUiR.string.settings_calibration_value_cd,
                    resources.getString(calibrationFormatRes, v),
                )
            },
        )
        Spacer(modifier = Modifier.height(12.dp))
        val weightingOptions = listOf(TimeWeighting.FAST, TimeWeighting.SLOW)
        val weightingLabels = listOf(
            stringResource(CoreUiR.string.settings_time_weighting_fast),
            stringResource(CoreUiR.string.settings_time_weighting_slow),
        )
        ChoicePreference(
            title = stringResource(CoreUiR.string.settings_time_weighting_title),
            options = weightingOptions,
            optionLabels = weightingLabels,
            selectedOption = state.timeWeighting,
            onOptionSelected = { onEvent(SettingsUiEvent.ChangeTimeWeighting(it)) },
        )
        Text(
            text = stringResource(CoreUiR.string.settings_time_weighting_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceSection(
    state: SettingsUiState,
    isDynamicColorSupported: Boolean,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    PreferenceCategory(title = stringResource(CoreUiR.string.settings_category_appearance)) {
        val themeOptions = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
        val themeLabels = listOf(
            stringResource(CoreUiR.string.settings_theme_system),
            stringResource(CoreUiR.string.settings_theme_light),
            stringResource(CoreUiR.string.settings_theme_dark),
        )
        ChoicePreference(
            title = stringResource(CoreUiR.string.settings_theme_title),
            options = themeOptions,
            optionLabels = themeLabels,
            selectedOption = state.themeMode,
            onOptionSelected = { onEvent(SettingsUiEvent.ChangeThemeMode(it)) },
        )
        Spacer(modifier = Modifier.height(12.dp))
        SwitchPreference(
            title = stringResource(CoreUiR.string.settings_dynamic_colors_title),
            subtitle = stringResource(CoreUiR.string.settings_dynamic_colors_description),
            checked = state.dynamicColors && isDynamicColorSupported,
            enabled = isDynamicColorSupported,
            onCheckedChange = { onEvent(SettingsUiEvent.ChangeDynamicColors(it)) },
        )
    }
}

@Composable
private fun LanguageSection(
    state: SettingsUiState,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    PreferenceCategory(title = stringResource(CoreUiR.string.settings_category_language)) {
        val options = listOf(AppLocale.System, AppLocale.Russian, AppLocale.English)
        val labels = listOf(
            stringResource(CoreUiR.string.settings_language_system),
            stringResource(CoreUiR.string.settings_language_russian),
            stringResource(CoreUiR.string.settings_language_english),
        )
        ChoicePreference(
            title = stringResource(CoreUiR.string.settings_language_title),
            options = options,
            optionLabels = labels,
            selectedOption = state.locale,
            onOptionSelected = { onEvent(SettingsUiEvent.ChangeAppLocale(it)) },
        )
    }
}

@Composable
private fun AboutFooter(onAboutClick: () -> Unit) {
    TextButton(
        onClick = onAboutClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(SettingsAboutLinkTestTag),
    ) {
        Text(text = stringResource(CoreUiR.string.settings_about_link))
    }
}
