package com.batuhanduran.burada.ui.components

import androidx.compose.runtime.Composable
import com.batuhanduran.burada.moderation.ReportReason

/** Reasons and optional explanation are separate fields, never discarded by the caller. */
@Composable
fun ReportListingDialog(
    itemTitle: String,
    onDismiss: () -> Unit,
    onConfirmReport: (ReportReason, String) -> Unit
) {
    ReportContentDialog(itemTitle, onDismiss, onConfirmReport)
}
