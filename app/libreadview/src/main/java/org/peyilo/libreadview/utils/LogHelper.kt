package org.peyilo.libreadview.utils

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

object LogHelper {

    /** 是否启用日志输出 */
    var ENABLE_LOG = true

    /** 默认 TAG，可自定义 */
    var DEFAULT_TAG = "LogHelper"

    fun d(tag: String = DEFAULT_TAG, msg: String?) {
        if (ENABLE_LOG) Log.d(tag, msg.orEmpty())
    }

    fun i(tag: String = DEFAULT_TAG, msg: String?) {
        if (ENABLE_LOG) Log.i(tag, msg.orEmpty())
    }

    fun w(tag: String = DEFAULT_TAG, msg: String?) {
        if (ENABLE_LOG) Log.w(tag, msg.orEmpty())
    }

    fun e(tag: String = DEFAULT_TAG, msg: String?, throwable: Throwable? = null) {
        if (!ENABLE_LOG) return
        if (throwable != null) {
            Log.e(tag, msg.orEmpty(), throwable)
        } else {
            Log.e(tag, msg.orEmpty())
        }
    }

    fun v(tag: String = DEFAULT_TAG, msg: String?) {
        if (ENABLE_LOG) Log.v(tag, msg.orEmpty())
    }

    /**
     * 美化打印 JSON 字符串
     */
    fun json(tag: String = DEFAULT_TAG, json: String?) {
        if (!ENABLE_LOG || json.isNullOrEmpty()) return

        try {
            val formatted = when {
                json.trim().startsWith("{") -> JSONObject(json).toString(4)
                json.trim().startsWith("[") -> JSONArray(json).toString(4)
                else -> json
            }
            Log.d(tag, "┌─── JSON ───────────────────────────────────")
            formatted.lines().forEach { Log.d(tag, "│ $it") }
            Log.d(tag, "└────────────────────────────────────────────")
        } catch (e: Exception) {
            Log.e(tag, "Invalid JSON:\n$json", e)
        }
    }

    /**
     * 快速打印异常堆栈
     */
    fun printStackTrace(tag: String = DEFAULT_TAG, throwable: Throwable) {
        if (ENABLE_LOG) Log.e(tag, Log.getStackTraceString(throwable))
    }

}