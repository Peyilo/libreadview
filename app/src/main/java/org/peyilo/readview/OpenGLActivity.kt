package org.peyilo.readview

import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import org.peyilo.readview.ui.PageCurlGLView


class OpenGLActivity : AppCompatActivity() {
    private lateinit var glView: PageCurlGLView
    private lateinit var animator: ValueAnimator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()

        glView = PageCurlGLView(this)

        // 准备两张位图（示例从 drawable）
        val fromBmp = BitmapFactory.decodeResource(resources, R.drawable.curpage)
        val toBmp   = BitmapFactory.decodeResource(resources, R.drawable.nextpage)
        glView.setBitmaps(fromBmp, toBmp)

        setContentView(glView)

        // 播放过渡动画
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 12000
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { v ->
                glView.setProgress(v.animatedValue as Float)
            }
        }
        animator.start()

        // 可选：调整参数
        glView.setAngle(80)
        glView.setRadius(0.15f)
        glView.setRoll(false)
        glView.setUncurl(false)
        glView.setGreyback(false)
        glView.setOpacity(0.8f)
        glView.setShadow(0.2f)
    }

    override fun onPause() { super.onPause(); glView.onPause() }
    override fun onResume() { super.onResume(); glView.onResume() }
}