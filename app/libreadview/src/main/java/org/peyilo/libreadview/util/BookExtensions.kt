package org.peyilo.libreadview.util

import org.peyilo.libreadview.data.Book
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

fun Book.save(parent: String): File {
    return save(File(parent))
}

fun Book.save(parent: File): File {
    return save(parent, "$title.txt")
}

fun Book.save(parent: File, child: String): File {
    val target = File(parent, child)
    FileOutputStream(target).use { fos ->
        save(fos)
    }
    return target
}

fun Book.save(outputStream: OutputStream) {
    return OutputStreamWriter(outputStream, StandardCharsets.UTF_8).use { out ->
        out.write(this.toString())
    }
}