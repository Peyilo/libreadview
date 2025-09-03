package org.peyilo.readview

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.pow


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("org.peyilo.readview", appContext.packageName)
    }


    fun makeShadowBitmap(
        width: Int,
        height: Int,
        kOfx: (Float) -> Float = { x ->
            // 默认阴影渐变：pow(clamp(x, 0., 1.) * 1.5, .2);
            (x.coerceIn(0F, 1F) * 1.5).pow(0.2).toFloat()
        }
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (x in 0 until width) {
            // 归一化位置 t ∈ [0,1]
            val k = kOfx(x / height.toFloat()).coerceIn(0f, 1f)
            val alpha = ((1f - k) * 255f).toInt().coerceIn(0, 255)

            val color = (alpha shl 24) or 0x000000
            // 整列都一样的 alpha
            for (y in 0 until height) {
                pixels[y * width + x] = color
            }
        }

        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    fun saveBitmapToFile(context: Context, bitmap: Bitmap, fileName: String): File {
        // 存到 app 的外部缓存目录 /storage/emulated/0/Android/data/包名/cache
        val file = File(context.getExternalFilesDir(null), fileName)

        FileOutputStream(file).use { out ->
            // 压缩为 PNG（无损），quality 参数对 PNG 无意义
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return file
    }


    @Test
    fun test1() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bmp = makeShadowBitmap(1024, 2192)
        val file = saveBitmapToFile(context, bmp, "shadow.png")
        println("Bitmap saved to: ${file.absolutePath}")
    }


}