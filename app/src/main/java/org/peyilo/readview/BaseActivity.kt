package org.peyilo.readview

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

open class BaseActivity: AppCompatActivity() {

    private val insetsController: WindowInsetsControllerCompat by lazy {
        WindowCompat.getInsetsController(window, window.decorView)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 默认让内容延伸到状态栏和导航栏区域
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setBarAppearance(true)
    }

    fun setBarAppearance(lightIcons: Boolean = true) {
        setStatusBarAppearance(lightIcons)
        setNavigationBarAppearance(lightIcons)
    }

    /** 设置状态栏颜色 */
    fun setStatusBarAppearance(lightIcons: Boolean = true) {
        insetsController.isAppearanceLightStatusBars = lightIcons
    }

    /** 设置导航栏颜色 */
    fun setNavigationBarAppearance(lightIcons: Boolean = true) {
        insetsController.isAppearanceLightNavigationBars = lightIcons
    }

    /** 隐藏状态栏和导航栏（沉浸模式） */
    fun hideSystemBars() {
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    /** 显示状态栏和导航栏 */
    fun showSystemBars() {
        insetsController.show(WindowInsetsCompat.Type.systemBars())
    }

}