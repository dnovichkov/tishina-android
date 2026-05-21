package ru.dmdp.tishina.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.dmdp.tishina.core.domain.model.AppSettingsSnapshot
import ru.dmdp.tishina.core.domain.usecase.ObserveAppSettingsUseCase
import ru.dmdp.tishina.core.domain.usecase.ResetCalibrationUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateAppLocaleUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateCalibrationUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateDynamicColorsUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateThemeModeUseCase
import ru.dmdp.tishina.core.domain.usecase.UpdateTimeWeightingUseCase
import javax.inject.Inject
import ru.dmdp.tishina.core.ui.R as CoreUiR

/**
 * MVI-lite controller for `SettingsScreen`. Architecture mirrors
 * `MeasureViewModel`:
 *  - [state] is derived from [ObserveAppSettingsUseCase] via `stateIn` so the
 *    repository (DataStore) remains the single source of truth — rotation and
 *    process death restore from disk, not from `SavedStateHandle`.
 *  - [effects] is a `Channel.BUFFERED`-backed cold flow used for snackbars and
 *    locale-apply signals. A buffered channel survives rotation without
 *    replaying values, unlike `StateFlow`.
 *  - [onEvent] is the single entry point for UI intents; each branch fires the
 *    matching use-case in [viewModelScope] so the call site can stay
 *    fire-and-forget.
 *
 * Calibration validation lives in [UpdateCalibrationUseCase], which returns a
 * `Result<Float>`. On `Result.failure` the ViewModel translates the error into
 * [SettingsUiEffect.ShowSnackbar] — the state stays at the previously persisted
 * value, so the slider visually "snaps back" once the user lifts their finger
 * on the out-of-range position.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val observeAppSettings: ObserveAppSettingsUseCase,
    private val updateCalibration: UpdateCalibrationUseCase,
    private val resetCalibration: ResetCalibrationUseCase,
    private val updateTimeWeighting: UpdateTimeWeightingUseCase,
    private val updateThemeMode: UpdateThemeModeUseCase,
    private val updateDynamicColors: UpdateDynamicColorsUseCase,
    private val updateAppLocale: UpdateAppLocaleUseCase,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = observeAppSettings()
        .map { snapshot -> snapshot.toUiState() }
        .stateIn(
            scope = viewModelScope,
            // 5 s grace lets a config-change-driven recreate land without re-collecting from
            // DataStore — matches the `stateIn` cadence used by `MeasureViewModel`.
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SettingsUiState(loading = true),
        )

    private val effectChannel = Channel<SettingsUiEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsUiEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.ChangeCalibration -> handleChangeCalibration(event.db)
            is SettingsUiEvent.ResetCalibration -> viewModelScope.launch { resetCalibration() }
            is SettingsUiEvent.ChangeTimeWeighting -> viewModelScope.launch { updateTimeWeighting(event.weighting) }
            is SettingsUiEvent.ChangeThemeMode -> viewModelScope.launch { updateThemeMode(event.mode) }
            is SettingsUiEvent.ChangeDynamicColors -> viewModelScope.launch { updateDynamicColors(event.enabled) }
            is SettingsUiEvent.ChangeAppLocale -> handleChangeAppLocale(event.locale)
        }
    }

    private fun handleChangeCalibration(db: Float) {
        viewModelScope.launch {
            val result = updateCalibration(db)
            if (result.isFailure) {
                effectChannel.trySend(SettingsUiEffect.ShowSnackbar(CoreUiR.string.settings_calibration_out_of_range))
            }
        }
    }

    private fun handleChangeAppLocale(locale: ru.dmdp.tishina.core.domain.model.AppLocale) {
        viewModelScope.launch {
            updateAppLocale(locale)
            // ApplyAppLocale is emitted after persistence so `MainActivity.recreate()` triggered by
            // AppCompatDelegate sees the new value on its next DataStore read.
            effectChannel.trySend(SettingsUiEffect.ApplyAppLocale(locale))
        }
    }

    private fun AppSettingsSnapshot.toUiState(): SettingsUiState =
        SettingsUiState(
            calibrationOffsetDb = config.calibrationOffsetDb,
            timeWeighting = config.timeWeighting,
            themeMode = appearance.themeMode,
            dynamicColors = appearance.dynamicColors,
            locale = appearance.locale,
            loading = false,
        )

    private companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
