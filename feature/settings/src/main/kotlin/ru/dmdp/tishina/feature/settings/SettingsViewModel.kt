package ru.dmdp.tishina.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
            is SettingsUiEvent.ResetCalibration -> launchPersistence { resetCalibration() }
            is SettingsUiEvent.ChangeTimeWeighting -> launchPersistence { updateTimeWeighting(event.weighting) }
            is SettingsUiEvent.ChangeThemeMode -> launchPersistence { updateThemeMode(event.mode) }
            is SettingsUiEvent.ChangeDynamicColors -> launchPersistence { updateDynamicColors(event.enabled) }
            is SettingsUiEvent.ChangeAppLocale -> handleChangeAppLocale(event.locale)
        }
    }

    private fun handleChangeCalibration(db: Float) {
        viewModelScope.launch {
            val result = updateCalibration(db)
            result.onFailure { error ->
                // `UpdateCalibrationUseCase` returns IllegalArgumentException ONLY for
                // validation rejections; any other Throwable comes from the repository
                // (DataStore I/O failure, cancellation, etc.) and must not be reported as
                // an "out of range" message.
                val messageRes = if (error is IllegalArgumentException) {
                    CoreUiR.string.settings_calibration_out_of_range
                } else {
                    CoreUiR.string.settings_save_failed
                }
                effectChannel.trySend(SettingsUiEffect.ShowSnackbar(messageRes))
            }
        }
    }

    private fun handleChangeAppLocale(locale: ru.dmdp.tishina.core.domain.model.AppLocale) {
        viewModelScope.launch {
            // Apply the locale ONLY if persistence succeeded — otherwise we'd recreate the
            // activity into a state the next cold start can't replay (DataStore would still
            // hold the prior value). try/catch instead of runCatching so a scope cancellation
            // propagates as cancellation rather than being misrouted to a "save failed"
            // snackbar at teardown.
            try {
                updateAppLocale(locale)
                effectChannel.trySend(SettingsUiEffect.ApplyAppLocale(locale))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                effectChannel.trySend(SettingsUiEffect.ShowSnackbar(CoreUiR.string.settings_save_failed))
            }
        }
    }

    /**
     * Wraps `viewModelScope.launch` so a DataStore I/O failure surfaces as a
     * snackbar instead of escaping to the coroutine's default uncaught handler
     * (which on Android crashes the process). Use-cases below run pure
     * `repository.update*` writes — none of them can throw a recoverable
     * validation error, so a single generic message is enough.
     *
     * try/catch instead of runCatching so structured cancellation is preserved —
     * runCatching would swallow `CancellationException` and turn a normal
     * scope/lifecycle teardown into a bogus "save failed" snackbar.
     */
    private fun launchPersistence(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                effectChannel.trySend(SettingsUiEffect.ShowSnackbar(CoreUiR.string.settings_save_failed))
            }
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
