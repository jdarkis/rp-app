package com.example.rpapp3.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.rpapp3.data.model.ModelRequestDetails
import com.example.rpapp3.data.model.ModelRequestStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ModelRequestDetailsDialog(
    details: ModelRequestDetails?,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API Request Details") },
        text = {
            when {
                isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                details == null -> {
                    Text(
                        "Request details are unavailable. This message may have been sent before request logging was added."
                    )
                }
                else -> {
                    RequestDetailsContent(details)
                }
            }
        },
        confirmButton = {
            if (details != null && !isLoading) {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("API request details", details.toCopyText())
                        )
                        Toast.makeText(context, "Request details copied", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text("Copy All", modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun RequestDetailsContent(details: ModelRequestDetails) {
    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .format(Date(details.createdAt))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DetailsSection("Request") {
            DetailLine("Status", if (details.status == ModelRequestStatus.SENT) "Sent" else "Not sent")
            details.failureReason?.let { DetailLine("Reason", it) }
            DetailLine("Provider", details.provider)
            DetailLine("Model", details.modelId)
            DetailLine("Streaming", details.streaming.toString())
            DetailLine("Captured", timestamp)
            details.endpoint?.let { DetailLine("Endpoint", it) }
        }

        DetailsSection("System Prompt") {
            MonospaceBlock(details.systemPrompt.ifBlank { "(empty)" })
        }

        DetailsSection("Messages") {
            details.messages.forEachIndexed { index, message ->
                Text(
                    text = "${index + 1}. ${message.role}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                MonospaceBlock(message.text)
            }
        }

        DetailsSection("Parameters") {
            details.parameters.forEach { parameter ->
                DetailLine(parameter.name, parameter.value)
            }
        }

        if (details.safetySettings.isNotEmpty()) {
            DetailsSection("Safety Settings") {
                details.safetySettings.forEach { setting ->
                    DetailLine(setting.name, setting.value)
                }
            }
        }

        DetailsSection(details.rawSnapshotLabel) {
            MonospaceBlock(details.rawSnapshot.ifBlank { "(not available)" })
        }
    }
}

@Composable
private fun DetailsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        content()
        HorizontalDivider()
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MonospaceBlock(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
