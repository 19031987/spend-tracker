package com.spendtracker.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxWidth
import com.spendtracker.app.ui.SettingsViewModel
import com.spendtracker.app.ui.components.AppCard
import com.spendtracker.app.ui.components.SecondaryButton
import com.spendtracker.app.ui.components.TonalButton
import com.spendtracker.app.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private const val MODEL_SUFFIX = ".litertlm"

/** Gemma model files already on the phone (Pocket Agent keeps its models in Download/PocketAgent/models). */
internal fun findModelsOnPhone(): List<File> {
    val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return listOf(File(downloads, "PocketAgent/models"), downloads)
        .flatMap { dir -> dir.listFiles()?.toList().orEmpty() }
        .filter { it.isFile && it.name.endsWith(MODEL_SUFFIX, ignoreCase = true) && it.canRead() && it.length() > 0 }
        .sortedBy { it.length() }
}

internal fun describeModel(path: String?): String {
    val file = path?.let { File(it) }?.takeIf { it.canRead() } ?: return "No model chosen"
    return String.format(Locale.UK, "%s · %.1f GB", file.name, file.length() / 1_000_000_000.0)
}

private fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

/** Copies the picked file into the app's own storage (the model runtime needs a real file path). */
private suspend fun copyModel(context: Context, uri: Uri, name: String, onProgress: (Long) -> Unit): File =
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "models").apply { mkdirs() }
        val target = File(dir, name)
        val temp = File(dir, "$name.part")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open the file" }
                temp.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 20)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(copied)
                    }
                }
            }
            dir.listFiles()?.filter { it != temp }?.forEach { it.delete() }
            check(temp.renameTo(target)) { "Could not save the model" }
            target
        } finally {
            temp.delete()
        }
    }

/**
 * Settings card: read alerts the rules cannot fully read with a Gemma model that runs on this
 * phone. Off until the user switches it on and chooses a model file.
 */
@Composable
fun AiReaderCard(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by rememberSaveable { mutableStateOf(viewModel.isAiReaderEnabled()) }
    var modelPath by rememberSaveable { mutableStateOf(viewModel.getAiModelPath()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var copiedBytes by remember { mutableStateOf<Long?>(null) }

    fun useModel(path: String) {
        modelPath = path
        viewModel.setAiModelPath(path)
        message = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri)
            if (name == null || !name.endsWith(MODEL_SUFFIX, ignoreCase = true)) {
                message = "Choose a Gemma model file ending in $MODEL_SUFFIX"
            } else {
                scope.launch {
                    copiedBytes = 0L
                    message = "Copying the model. Keep this screen open."
                    try {
                        val file = copyModel(context.applicationContext, uri, name) { copiedBytes = it }
                        useModel(file.absolutePath)
                    } catch (e: Exception) {
                        message = "Could not copy the model: ${e.message}"
                    } finally {
                        copiedBytes = null
                    }
                }
            }
        }
    }

    AppCard(verticalSpacing = Spacing.md) {
        SettingsListRow(
            title = "Read unclear alerts with Gemma",
            supporting = "When the merchant can't be read from a bank alert, ask a Gemma model that runs on this phone. " +
                "Nothing is sent anywhere.",
            leading = {
                Icon(Icons.Outlined.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailing = {
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        viewModel.setAiReaderEnabled(it)
                    }
                )
            }
        )
        Text(
            text = "Model: " + describeModel(modelPath),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val bytes = copiedBytes
        if (bytes != null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = String.format(Locale.UK, "%.1f GB copied", bytes / 1_000_000_000.0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        message?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TonalButton(
            text = "Use the model already on this phone",
            enabled = bytes == null,
            onClick = {
                if (!hasAllFilesAccess()) {
                    message = "Allow \"All files access\" for this app, then tap this button again. " +
                        "It is only used to read the model file in Download."
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:" + context.packageName)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    }
                } else {
                    val found = findModelsOnPhone().firstOrNull()
                    if (found == null) {
                        message = "No $MODEL_SUFFIX model found in Download or Download/PocketAgent/models"
                    } else {
                        useModel(found.absolutePath)
                    }
                }
            }
        )
        SecondaryButton(
            text = "Choose a model file (makes a copy)",
            enabled = bytes == null,
            onClick = { picker.launch(arrayOf("*/*")) }
        )
    }
}
