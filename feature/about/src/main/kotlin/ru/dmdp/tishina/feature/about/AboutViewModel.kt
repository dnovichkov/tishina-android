package ru.dmdp.tishina.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.dmdp.tishina.core.domain.repository.AppVersionProvider
import ru.dmdp.tishina.core.domain.repository.OssLicensesProvider
import javax.inject.Inject

/**
 * State holder for `AboutScreen` (FR-21).
 *
 * Both injected providers are `suspend` — their concrete implementations in `:core:data`
 * hop onto `Dispatchers.IO` for the `PackageManager` IPC and `AssetManager` file read so
 * Main is never blocked on a cold launch. The two reads run in parallel via `async` /
 * `await` inside a `coroutineScope` block so the cold-launch resolve pays for one IO
 * round-trip rather than two serialized ones. The state starts in `loading = true` and
 * flips to the loaded snapshot once both lookups complete — keeping it as a single
 * emission means AboutScreen renders the entire frame in one recomposition rather than
 * flashing version-only then licenses.
 *
 * No `Channel` for effects: the screen is fully driven by `state`, and link clicks
 * fire `Intent.ACTION_VIEW` directly from the composable (URLs are static strings).
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    private val versionProvider: AppVersionProvider,
    private val licensesProvider: OssLicensesProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(AboutUiState(loading = true))
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (version, licenses) = coroutineScope {
                val versionAsync = async { versionProvider.get() }
                val licensesAsync = async { licensesProvider.load() }
                versionAsync.await() to licensesAsync.await()
            }
            _state.value = AboutUiState(
                version = version,
                ossLicenses = licenses,
                loading = false,
            )
        }
    }
}
