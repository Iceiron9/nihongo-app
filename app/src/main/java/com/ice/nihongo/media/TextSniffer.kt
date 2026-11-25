package com.ice.nihongo.media

import android.content.Context
import android.net.Uri

/** 判斷檔案是不是文字（字幕或歌詞），不看副檔名。 */
object TextSniffer {
    private const val MAX = 5 * 1024 * 1024

    fun read(ctx: Context, uri: Uri): String? {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX + 1) } ?: return null
        return decode(bytes)
    }

    /** 太大、或看起來是二進位檔就回傳 null */
    fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX) return null
        if (bytes.take(4096).count { it == 0.toByte() } > 0) return null // 二進位檔通常有 0 位元組
        val text = runCatching {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrNull() ?: return null
        val sample = text.take(4096)
        val weird = sample.count { it.isISOControl() && it !in "\n\r\t" }
        return if (weird * 20 > sample.length) null else text
    }

    private val MEDIA_EXT = Regex("""\.(mp4|m4a|mp3|mkv|webm|mov|aac|wav|flac|ogg|opus)$""", RegexOption.IGNORE_CASE)

    /** 檔名 → 歌名：「歌名.mp4.txt」這種被多加副檔名的，兩層都去掉 */
    fun title(fileName: String): String {
        var t = fileName.trim()
        if (t.endsWith(".txt", true) && MEDIA_EXT.containsMatchIn(t.dropLast(4))) t = t.dropLast(4)
        t = t.substringBeforeLast('.', t)
        return t.ifBlank { fileName }
    }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        while (out.size() < n) {
            val r = read(buf, 0, minOf(buf.size, n - out.size()))
            if (r < 0) break
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }
}
