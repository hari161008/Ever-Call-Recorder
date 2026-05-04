// SPDX-License-Identifier: GPL-3.0-or-later
package com.coolappstore.evercallrecorder.by.svhp.ui.legal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coolappstore.evercallrecorder.by.svhp.R
import kotlinx.coroutines.launch

/**
 * First-run legal placeholder, also reachable from Settings → "Юридичне
 * попередження" for a fresh re-read.
 *
 * Two modes via [requireAck]:
 *  - `true` (consent flow): blocks back-press, swipe-down and scrim-tap. Only
 *    the explicit "Зрозуміло" button can dismiss the sheet.
 *  - `false` (settings re-read): behaves like a normal informational sheet.
 *
 * Locking is implemented by a triple-defense: `confirmValueChange` rejects the
 * Hidden state, `shouldDismissOnBackPress = false`, and the drag handle is
 * removed so the affordance no longer hints "swipe to dismiss".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalDisclaimerSheet(
    requireAck: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { sheetValue ->
            if (requireAck) sheetValue != SheetValue.Hidden else true
        },
    )
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = if (requireAck) {
            { /* swallow scrim/system-driven dismissals while consent is pending */ }
        } else onDismiss,
        sheetState = sheetState,
        dragHandle = if (requireAck) null else {
            { BottomSheetDefaults.DragHandle() }
        },
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = !requireAck,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = if (requireAck) 16.dp else 0.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.legal_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Text(
                stringResource(R.string.legal_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ExpandableTier(
                title = stringResource(R.string.legal_tier1_title),
                summary = stringResource(R.string.legal_tier1_summary),
                body = stringResource(R.string.legal_tier1_body),
                accent = MaterialTheme.colorScheme.primary,
            )
            ExpandableTier(
                title = stringResource(R.string.legal_tier2_title),
                summary = stringResource(R.string.legal_tier2_summary),
                body = stringResource(R.string.legal_tier2_body),
                accent = MaterialTheme.colorScheme.tertiary,
            )
            ExpandableTier(
                title = stringResource(R.string.legal_tier3_title),
                summary = stringResource(R.string.legal_tier3_summary),
                body = stringResource(R.string.legal_tier3_body),
                accent = MaterialTheme.colorScheme.error,
            )

            Spacer(Modifier.height(4.dp))

            Column {
                Text(
                    stringResource(R.string.legal_tech_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.legal_tech_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                stringResource(R.string.legal_share),
                style = MaterialTheme.typography.bodyMedium,
            )

            Text(
                stringResource(R.string.legal_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        if (requireAck) onAccept() else onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (requireAck) R.string.legal_action_accept
                        else R.string.legal_action_close,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ExpandableTier(
    title: String,
    summary: String,
    body: String,
    accent: androidx.compose.ui.graphics.Color,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation = if (expanded) 180f else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Icon(
                Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(150)),
        ) {
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, end = 32.dp),
            )
        }
    }
}
