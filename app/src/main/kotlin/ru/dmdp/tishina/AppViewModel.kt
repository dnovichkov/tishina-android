package ru.dmdp.tishina

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.dmdp.tishina.core.domain.model.AppearanceSettings
import ru.dmdp.tishina.core.domain.usecase.ObserveAppSettingsUseCase
import javax.inject.Inject

/**
 * Activity-scoped ViewModel that surfaces the persisted [AppearanceSettings]
 * to `MainActivity` so the root [ru.dmdp.tishina.core.designsystem.theme.TishinaTheme]
 * can react to FR-17 changes (theme mode + dynamic colors) without each screen
 * re-subscribing to [ObserveAppSettingsUseCase].
 *
 * Starts EAGERLY so the splash/first-frame already has the correct theme
 * applied — no flash of default-light then dark when the user previously
 * picked dark mode.
 */
@HiltViewModel
class AppViewModel @Inject constructor(observeAppSettings: ObserveAppSettingsUseCase) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> = observeAppSettings()
        .map { snapshot -> snapshot.appearance }
        .stateIn(
            scope = viewModelScope,
            // EAGERLY (not WhileSubscribed) because MainActivity reads the
            // value in `setContent` synchronously — a WhileSubscribed flow
            // could emit `initialValue` on the first frame and then flip to
            // the persisted preference one frame later, producing a visible
            // theme flicker on every cold start.
            started = SharingStarted.Eagerly,
            initialValue = AppearanceSettings(),
        )
}
