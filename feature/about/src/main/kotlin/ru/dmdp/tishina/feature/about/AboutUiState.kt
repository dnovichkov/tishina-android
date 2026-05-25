package ru.dmdp.tishina.feature.about

import ru.dmdp.tishina.core.domain.model.AppVersion
import ru.dmdp.tishina.core.domain.model.OssLicense

/**
 * Immutable snapshot rendered by `AboutScreen` (FR-21).
 *
 * `loading = true` is the cold-start frame before [AboutViewModel] has resolved
 * version and licenses; the UI shows a small progress indicator until then. The
 * subsequent state holds both pieces in one snapshot so a single recomposition
 * paints the entire screen — version + license-list arrive together, not as two
 * independent flows.
 */
data class AboutUiState(
    val version: AppVersion = AppVersion(versionName = "", versionCode = 0),
    val ossLicenses: List<OssLicense> = emptyList(),
    val loading: Boolean = true,
)
