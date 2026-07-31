package com.megamusicmaker.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Saves recorded songs as standard WAV files. On Android 10+ they land in
 * Music/MegaMusicMaker so any music player or file manager can see them.
 */
object WavWriter {

    fun save(context: Context, fileName: String, data: FloatArray, sampleRate: Int = Synth.SR): Uri? {
        if (data.isEmpty()) return null
        val bytes = toWav(data, sampleRate)
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/MegaMusicMaker")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
                val f = File(dir, fileName)
                FileOutputStream(f).use { it.write(bytes) }
                Uri.fromFile(f)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun toWav(data: FloatArray, sr: Int): ByteArray {
        val pcm = ByteArray(data.size * 2)
        var j = 0
        for (x in data) {
            val s = (x.coerceIn(-1f, 1f) * 32767).toInt()
            pcm[j++] = (s and 0xFF).toByte()
            pcm[j++] = ((s shr 8) and 0xFF).toByte()
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)                 // PCM
        header.putShort(1)                 // mono
        header.putInt(sr)
        header.putInt(sr * 2)              // byte rate
        header.putShort(2)                 // block align
        header.putShort(16)                // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }
}
