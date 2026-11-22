package com.budgetapp.ui.pay

import com.budgetapp.ui.theme.PhosphorLight
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.budgetapp.ui.theme.LocalStatusColors
import kotlinx.coroutines.delay
import java.io.File
import com.budgetapp.ui.components.Pill

/** Records one audio note at a time into the app's private storage. */
private class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null

    fun start(): Boolean {
        val dir = File(context.filesDir, "voice-notes").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}.m4a")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setOutputFile(target.absolutePath)
            created.prepare()
            created.start()
            recorder = created
            file = target
            true
        } catch (e: Exception) {
            created.release()
            target.delete()
            false
        }
    }

    /** Returns the saved file path, or null if nothing usable was recorded. */
    fun stop(): String? {
        val active = recorder ?: return null
        val target = file
        recorder = null
        file = null
        return try {
            active.stop()
            target?.absolutePath
        } catch (e: RuntimeException) {
            // stop() throws when the recording is too short to contain audio.
            target?.delete()
            null
        } finally {
            active.release()
        }
    }
}

/**
 * Record, play back or remove the voice note for a payment.
 * [path] is the saved recording, or null when there is none.
 * [keepOnRemove] is a recording Remove must leave on disk: a saved payment's
 * note, which is deleted only once the edit is saved.
 */
@Composable
fun VoiceNoteControl(path: String?, onChange: (String?) -> Unit, keepOnRemove: String? = null) {
    val context = LocalContext.current
    val status = LocalStatusColors.current
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun startRecording() {
        if (recorder.start()) {
            seconds = 0
            recording = true
        } else {
            Toast.makeText(context, "Couldn't start recording", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopPlayback() {
        player?.release()
        player = null
    }

    val askMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            Toast.makeText(context, "Microphone permission is needed for voice notes", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1_000)
            seconds++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder.stop()
            player?.release()
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            recording -> Pill(
                icon = PhosphorLight.Stop,
                label = "Stop  ${formatDuration(seconds)}",
                color = status.danger,
                onClick = {
                    recording = false
                    onChange(recorder.stop())
                },
            )

            path == null -> Pill(
                icon = PhosphorLight.Microphone,
                label = "Voice note",
                onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) startRecording() else askMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                },
            )

            else -> {
                val playing = player != null
                Pill(
                    icon = if (playing) PhosphorLight.Stop else PhosphorLight.Play,
                    label = if (playing) "Stop" else "Play voice note",
                    onClick = {
                        if (playing) {
                            stopPlayback()
                        } else {
                            player = runCatching {
                                MediaPlayer().apply {
                                    setDataSource(path)
                                    setOnCompletionListener { stopPlayback() }
                                    prepare()
                                    start()
                                }
                            }.getOrNull()
                        }
                    },
                )
                Pill(
                    icon = PhosphorLight.X,
                    label = "Remove",
                    onClick = {
                        stopPlayback()
                        if (path != keepOnRemove) File(path).delete()
                        onChange(null)
                    },
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
