package ru.dmdp.tishina

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
 * Starts EAGERLY so the upstream DataStore subscription kicks off as soon as the
 * VM is created (during `MainActivity.onCreate`, before `setContent`), giving the
 * IO read the earliest possible head-start. EAGERLY does NOT guarantee the value
 * is persisted-loaded by the time Compose first reads `appearance.value` — the
 * DataStore IO hop is asynchronous, so the very first frame of a cold start may
 * still render with [AppearanceSettings] defaults (System theme, dynamic colors on)
 * before snapping to the persisted preference one frame later. A true flicker-free
 * cold start would require deferring `setContent` via SplashScreen.keepOnScreenCondition
 * until the first non-default emission lands; that is deferred to a Phase 5 polish task.
 * WhileSubscribed would have the same behaviour with extra teardown noise on rotation,
 * so EAGERLY is still the right default here even without the flicker claim.
 */
@HiltViewModel
class AppViewModel @Inject constructor(observeAppSettings: ObserveAppSettingsUseCase) : ViewModel() {

    val appearance: StateFlow<AppearanceSettings> = observeAppSettings()
        .map { snapshot -> snapshot.appearance }
        // Defense-in-depth: if DataStore чтение упало (corruption-handler не отработал,
        // транзиентная I/O ошибка), без `.catch` исключение улетает в `viewModelScope`
        // → дефолтный uncaught handler → краш активити при старте. Тут blast radius
        // больше, чем у feature-VM: на эту StateFlow подписаны и `TishinaTheme`, и все
        // экраны через `MainActivity`. Сваливаемся обратно на дефолтный
        // `AppearanceSettings()` — пользователь увидит системную тему вместо своей,
        // но приложение продолжит работать.
        .catch { emit(AppearanceSettings()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppearanceSettings(),
        )
}
