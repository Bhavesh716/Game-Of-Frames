package com.instantcollabmaker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.instantcollabmaker.ui.theme.FtColor
import com.instantcollabmaker.ui.theme.FtType
import com.instantcollabmaker.ui.theme.Spacing

/**
 * The overflow menu a person card can carry: rename, and (optionally) view/download their
 * own appearance collage without leaving the current screen.
 *
 * Assignment-minimal mode: [onViewCollage]/[onDownloadCollage] default to `null` and, when
 * null, their menu items simply aren't shown — this is how the per-person collage entry
 * point is disabled for the simplified final flow without deleting the feature. Pass real
 * callbacks to re-enable them for a screen that still wants that workflow.
 */
@Composable
fun PersonOverflowMenu(
    onRename: () -> Unit,
    onViewCollage: (() -> Unit)? = null,
    onDownloadCollage: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconAction(
            icon = Icons.Default.MoreVert,
            contentDescription = "More options",
            onClick = { expanded = true },
            modifier = Modifier,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = FtColor.SurfaceElevated,
        ) {
            DropdownMenuItem(
                text = { Text("Rename", color = FtColor.TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = FtColor.TextSecondary) },
                onClick = { expanded = false; onRename() },
            )
            if (onViewCollage != null) {
                DropdownMenuItem(
                    text = { Text("View Character's Collage", color = FtColor.TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = FtColor.TextSecondary) },
                    onClick = { expanded = false; onViewCollage() },
                )
            }
            if (onDownloadCollage != null) {
                DropdownMenuItem(
                    text = { Text("Download Character's Collage", color = FtColor.TextPrimary) },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = FtColor.TextSecondary) },
                    onClick = { expanded = false; onDownloadCollage() },
                )
            }
        }
    }
}

/** A clean rename dialog — plain text field, Cancel/Save. */
@Composable
fun RenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FtColor.Surface,
        title = { Text("Rename", style = FtType.titleMedium, color = FtColor.TextPrimary) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = FtType.body.copy(color = FtColor.TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = FtColor.TextPrimary,
                    unfocusedTextColor = FtColor.TextPrimary,
                    focusedContainerColor = FtColor.SurfaceElevated,
                    unfocusedContainerColor = FtColor.SurfaceElevated,
                    focusedIndicatorColor = FtColor.Accent,
                    unfocusedIndicatorColor = FtColor.Stroke,
                    cursorColor = FtColor.Accent,
                ),
            )
        },
        confirmButton = {
            TertiaryButton(
                text = "Save",
                tint = FtColor.Accent,
                onClick = {
                    if (text.isNotBlank()) onConfirm(text.trim())
                },
            )
        },
        dismissButton = {
            TertiaryButton(text = "Cancel", onClick = onDismiss)
        },
    )
}
