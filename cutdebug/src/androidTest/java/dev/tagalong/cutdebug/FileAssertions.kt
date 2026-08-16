package dev.tagalong.cutdebug

import java.io.File
import java.security.MessageDigest

/** Backs the "original file is never modified" assertion (task 2.5). */
object FileAssertions {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
