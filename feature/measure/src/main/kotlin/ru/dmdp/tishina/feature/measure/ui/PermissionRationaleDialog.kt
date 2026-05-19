package ru.dmdp.tishina.feature.measure.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.dmdp.tishina.feature.measure.R

const val PermissionRationaleDialogTestTag: String = "measure_permission_rationale_dialog"
const val PermissionRationaleConfirmTestTag: String = "measure_permission_rationale_confirm"
const val PermissionRationaleDismissTestTag: String = "measure_permission_rationale_dismiss"

/**
 * Material 3 [AlertDialog] explaining why the app needs RECORD_AUDIO.
 *
 * Per spec FR-2, the rationale text **explicitly** asserts that audio is not recorded and
 * never leaves the device — this addresses the single biggest objection real users raise to
 * microphone-permission prompts in noise-meter category competitive teardowns. The wording
 * is loaded from resources so it stays localizable.
 */
@Composable
fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier.testTag(PermissionRationaleDialogTestTag),
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.measure_permission_rationale_title)) },
        text = { Text(text = stringResource(id = R.string.measure_permission_rationale_body)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(PermissionRationaleConfirmTestTag),
            ) {
                Text(text = stringResource(id = R.string.measure_permission_rationale_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(PermissionRationaleDismissTestTag),
            ) {
                Text(text = stringResource(id = R.string.measure_permission_rationale_dismiss))
            }
        },
    )
}
