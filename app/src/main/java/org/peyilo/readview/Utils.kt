package org.peyilo.readview

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * 从assets文件夹中,复制指定文件到指定路径
 */
fun copyAssetToInternalStorage(context: Context, assetFileName: String, outputFile: File): File? {
    val assetManager = context.assets
    try {
        // 打开 assets 文件流
        val inputStream: InputStream = assetManager.open(assetFileName)
        val outputStream: OutputStream = FileOutputStream(outputFile)

        // 将文件内容从 assets 复制到内部存储
        val buffer = ByteArray(1024)
        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            outputStream.write(buffer, 0, length)
        }

        // 关闭流
        inputStream.close()
        outputStream.close()

        return outputFile
    } catch (e: IOException) {
        e.printStackTrace()
        return null
    }
}