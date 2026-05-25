package ru.dmdp.tishina.feature.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.dmdp.tishina.core.domain.model.AppVersion
import ru.dmdp.tishina.core.domain.model.OssLicense

const val AboutScreenTestTag: String = "tishina_about_screen"
const val AboutScreenLoadingTestTag: String = "tishina_about_screen_loading"
const val AboutScreenContentTestTag: String = "tishina_about_screen_content"
const val AboutScreenVersionTestTag: String = "tishina_about_screen_version"
const val AboutScreenDisclaimerTestTag: String = "tishina_about_screen_disclaimer"
const val AboutScreenGithubTestTag: String = "tishina_about_screen_github_link"
const val AboutScreenPrivacyTestTag: String = "tishina_about_screen_privacy_link"
const val AboutScreenLicensesTestTag: String = "tishina_about_screen_licenses"

private fun licenseTestTag(name: String): String =
    "tishina_about_screen_license_" + name.lowercase().filter { it.isLetterOrDigit() || it == '_' }

/**
 * Stateful entry point for AboutScreen (FR-21) — wires [AboutViewModel] and renders the
 * stateless [AboutScreenContent].
 *
 * Link clicks fire `Intent.ACTION_VIEW` directly from the composable (URLs are static
 * placeholders in Phase 5, replaced with real hosts in Phase Release). The Intent is
 * launched against `LocalContext.current` — in this Compose tree that resolves to the
 * hosting ComponentActivity, so the browser starts as a sibling Activity in the same
 * task and Back returns to Tishina. We do NOT add `FLAG_ACTIVITY_NEW_TASK`: that flag
 * is only required when the caller isn't an Activity, and applying it here would force
 * the browser into a separate task — pressing Back from the browser would surface Home
 * (or an unrelated browser task) instead of the AboutScreen.
 *
 * The legacy two-parameter signature `AboutScreen(onNavigateBack, modifier)` is kept
 * intact — `viewModel` carries a default `hiltViewModel()`, so the existing call site
 * in `TishinaNavHost` and historical tests continue to compile unchanged.
 */
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    AboutScreenContent(
        state = state,
        onNavigateBack = onNavigateBack,
        onOpenUrl = { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        },
        modifier = modifier,
    )
}

/**
 * Pure / stateless body of AboutScreen. Tests and screenshot fixtures call this directly
 * with a synthetic [AboutUiState] — no Hilt, no PackageManager, no AssetManager required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreenContent(
    state: AboutUiState,
    onNavigateBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(AboutScreenTestTag),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.about_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.about_back_cd),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(AboutScreenLoadingTestTag),
                )
            }
        } else {
            AboutScreenBody(
                state = state,
                onOpenUrl = onOpenUrl,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun AboutScreenBody(
    state: AboutUiState,
    onOpenUrl: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val githubUrl = stringResource(R.string.about_github_url)
    val privacyUrl = stringResource(R.string.about_privacy_url)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(AboutScreenContentTestTag),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("header") { AboutHeader(version = state.version) }
        item("description") { DescriptionCard() }
        item("disclaimer") { DisclaimerCard() }
        item("links") {
            LinksSection(
                githubUrl = githubUrl,
                privacyUrl = privacyUrl,
                onOpenUrl = onOpenUrl,
            )
        }
        item("licenses_header") { LicensesHeader() }
        if (state.ossLicenses.isEmpty()) {
            item("licenses_empty") { LicensesEmptyState() }
        } else {
            // Stable key on `name` so a curation-time reorder of oss_licenses.json doesn't
            // recycle row slots (which would briefly render wrong content during recomposition)
            // and so the testTag derived from the same `name` matches a stable LazyColumn slot.
            items(state.ossLicenses, key = { it.name }) { license ->
                LicenseRow(license = license, onClick = { onOpenUrl(license.url) })
            }
        }
        item("footer_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun AboutHeader(version: AppVersion) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .size(72.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = stringResource(R.string.about_app_icon_cd),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.about_header_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Build the localised "Version X (build N)" / fallback "Version build N" label in
        // the UI layer — the domain `AppVersion` deliberately exposes no `displayName` so
        // the "build"/"сборка" word can come from strings.xml (NFR-17 / NFR-18). Branching
        // on isBlank() preserves the prior FALLBACK behaviour where PackageManager returned
        // null versionName: we drop the empty prefix instead of rendering "Version  (build 0)".
        val versionFormatted = if (version.versionName.isBlank()) {
            stringResource(R.string.about_version_format_build_only, version.versionCode)
        } else {
            stringResource(
                R.string.about_version_format,
                version.versionName,
                version.versionCode,
            )
        }
        Text(
            modifier = Modifier.testTag(AboutScreenVersionTestTag),
            text = stringResource(R.string.about_version_label) + " " + versionFormatted,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DescriptionCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DisclaimerCard() {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AboutScreenDisclaimerTestTag),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.about_disclaimer_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.about_disclaimer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinksSection(
    githubUrl: String,
    privacyUrl: String,
    onOpenUrl: (String) -> Unit,
) {
    SectionTitle(title = stringResource(R.string.about_links_section_title))
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            LinkRow(
                icon = Icons.Filled.Code,
                title = stringResource(R.string.about_github_title),
                subtitle = stringResource(R.string.about_github_subtitle),
                testTag = AboutScreenGithubTestTag,
                onClick = { onOpenUrl(githubUrl) },
            )
            HorizontalDivider()
            LinkRow(
                icon = Icons.Filled.Shield,
                title = stringResource(R.string.about_privacy_title),
                subtitle = stringResource(R.string.about_privacy_subtitle),
                testTag = AboutScreenPrivacyTestTag,
                onClick = { onOpenUrl(privacyUrl) },
            )
        }
    }
}

@Composable
private fun LinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Role.Button so TalkBack announces "<title>, button" rather than reading the row
            // as a generic container — without it the OpenInNew icon (decorative,
            // contentDescription=null) gives a11y no hint that the row is tappable.
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicensesHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AboutScreenLicensesTestTag),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionTitle(title = stringResource(R.string.about_licenses_section_title))
        Text(
            text = stringResource(R.string.about_licenses_section_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicensesEmptyState() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(R.string.about_licenses_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LicenseRow(license: OssLicense, onClick: () -> Unit) {
    val openCd = stringResource(R.string.about_license_open_cd)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(licenseTestTag(license.name)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Role.Button so TalkBack reads each license card as an actionable button.
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = license.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = license.version + " · " + license.license,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = openCd,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}
