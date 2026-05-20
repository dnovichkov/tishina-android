package ru.dmdp.tishina.feature.history.detail

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
}
