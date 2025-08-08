package org.peyilo.readview

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit


/**
 * SharedPreferences 集中管理类
 * 所有需要持久化的参数都在这里统一声明和管理
 */
object AppPreferences {

    private fun getSharedPreferences(): SharedPreferences = App.applicationContext.getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)

    fun setChapIndex(chapIndex: Int) {
        getSharedPreferences().edit {
            putInt("chapIndex", chapIndex)
        }
    }

    fun setChapPageIndex(chapPageIndex: Int) {
        getSharedPreferences().edit {
            putInt("chapPageIndex", chapPageIndex)
        }
    }

    fun getChapIndex(): Int {
        return getSharedPreferences().getInt("chapIndex", 1)
    }

    fun getChapPageIndex(): Int {
        return getSharedPreferences().getInt("chapPageIndex", 1)
    }

    fun setFlipMode(flipMode: Int) {
        getSharedPreferences().edit {
            putInt("flipMode", flipMode)
        }
    }

    fun getFlipMode(): Int {
        return getSharedPreferences().getInt("flipMode", 0)
    }
}