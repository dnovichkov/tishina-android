package ru.dmdp.tishina.feature.history.detail

import android.content.Intent
import androidx.annotation.StringRes

/**
 * One-shot effects from [DetailViewModel] to [DetailScreen]. Modelled as a Channel-backed Flow
 * so emissions are buffered if the screen is briefly off-frame (configuration change), but never
 * replayed if the user has already seen them.
 */
sealed interface DetailUiEffect {

    /** Show a transient Snackbar (note-too-long / save-failed). */
    data class ShowSnackbar(@StringRes val messageRes: Int) : DetailUiEffect

    /** Pop the back stack — triggered by successful delete or "not found" load. */
    data object NavigateBack : DetailUiEffect

    /**
     * Launch the Android system share chooser with the pre-built `ACTION_SEND` intent (FR-10
     * P1). The intent carries either a PNG snapshot of the chart with EXTRA_TEXT summary
     * (rich path), or a text-only fallback when snapshotting failed.
     */
    data class LaunchShareIntent(val intent: Intent) : DetailUiEffect
}
