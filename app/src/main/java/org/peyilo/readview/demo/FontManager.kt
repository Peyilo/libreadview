package org.peyilo.readview.demo

import android.graphics.Typeface
import org.peyilo.readview.App

object FontManager {
    // 缓存避免重复加载
    private val cache = mutableMapOf<String, Typeface?>()

    /**
     * 从 assets/fonts 目录加载字体
     *
     * @param name 字体文件名（不带路径，可以带扩展名也可以不带）
     *             例如 "linja-waso-Light" 或 "linja-waso-Light.ttf"
     */
    fun getTypeface(name: String): Typeface? {
        val key = name.removeSuffix(".ttf")
        return cache.getOrPut(key) {
            try {
                val fileName = if (name.endsWith(".ttf")) name else "$name.ttf"
                Typeface.createFromAsset(App.applicationContext.assets, "fonts/$fileName")
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}