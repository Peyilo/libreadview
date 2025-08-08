package org.peyilo.libreadview

import android.content.Context
import android.util.AttributeSet

/**
 * ReadView和PageContainer的唯一的一个区别是：一个对内的adapter，一个对外的adapter
 */
abstract class ReadView(
    context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int
): AbstractPageContainer(context, attrs, defStyleAttr, defStyleRes), BookNavigator {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): this(context, attrs, defStyleAttr, 0)
    constructor(context: Context, attrs: AttributeSet?): this(context, attrs, 0)
    constructor(context: Context): this(context, null)

//    private var isLongPress = false
//    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong() // 默认500ms
//    private val handler = Handler(Looper.getMainLooper())
//    private var downX = 0F
//    private var downY = 0F
//
//    /**
//     * selectText模式开启后，会拦截滑动手势和点击手势，全部交给onSelectTextTouchEvent处理
//     */
//    private var selectTextMode = false
//    private var disableSelectTextFlag = false
//
//    companion object {
//        private const val TAG = "ReadView"
//    }
//
//    private var longPressRunnableBeMoved = true
//    private val longPressRunnable = Runnable {
//        selectTextMode = onSelectTextTriggered(downX, downY)
//        isLongPress = true
//        LogHelper.d(TAG, "selectText: selectTextMode enable")
//    }

    /**
     * ReadView对内提供了adapter的getter和setter
     */
    protected var adapter: Adapter<out ViewHolder>
        get() = getInnerAdapter() ?: throw IllegalStateException("Adapter is not initialized. Did you forget to set it?")
        set(value) = setInnerAdapter(value)


//    /**
//     * 为长按选择文本手势提供支持。
//     * 如果本轮手势不是长按手势，那么所有事件应该交给AbstractPageContainer处理；
//     * 如果本轮时间为长按手势，且onSelectTextTriggered返回值为false，即本轮手势为长按手势，但是非selectText模式，
//     * 那么本轮手势将会被全部忽略；
//     * 如果本轮时间为长按手势，且onSelectTextTriggered返回值为true，即本轮手势为长按手势，并且进入了selectText模式，
//     * 那么直至disableSelectTextMode()被调用之前，所有的事件（不止本轮事件）都将会交给onSelectTextTouchEvent()处理，
//     * 事件不会传递到AbstractPageContainer,并且在disableSelectTextMode()被调用后的那轮所有事件也将会被忽略。即
//     * disableSelectTextMode()被调用之后的下一轮事件才会再继续正常处理
//     */
//    override fun onTouchEvent(event: MotionEvent): Boolean {
//        // 保证disableSelectTextMode()被调用后的那轮所有事件也将会被忽略
//        if (disableSelectTextFlag) {
//            when(event.action) {
//                // disableSelectTextMode()被调用之后的下一轮事件继续正常处理，
//                // 因此需要再本轮事件结束时，清除标记位，以免影响下一轮事件
//                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                    LogHelper.d(TAG, "selectText: disableSelectTextFlag = false")
//                    disableSelectTextFlag = false
//                }
//            }
//            LogHelper.d(TAG, "selectText: 保证disableSelectTextMode()被调用后的那轮所有事件也将会被忽略")
//            return true
//        }
//        if (selectTextMode) {
//            return onSelectTextTouchEvent(event)            // 一旦longPressRunnable运行
//        }
//        // 检测出长按手势
//        when (event.action) {
//            MotionEvent.ACTION_DOWN -> {
//                downX = event.x
//                downY = event.y
//                longPressRunnableBeMoved = false
//                isLongPress = false
//                super.onTouchEvent(event)
//                onSelectTextTouchEvent(event)
//                handler.postDelayed(longPressRunnable, longPressTimeout)
//            }
//            else -> {
//                if (!longPressRunnableBeMoved) {
//                    handler.removeCallbacks(longPressRunnable)
//                    longPressRunnableBeMoved = true
//                }
//            }
//        }
//        // 当前事件为长按事件，本轮事件不再交给AbstractPageContainer处理了
//        if (isLongPress) {
//            LogHelper.d(TAG, "selectText：当前事件为长按事件，本轮事件不再交给AbstractPageContainer处理了")
//            return true
//        }
//
//        // 交给AbstractPageContainer处理
//        LogHelper.d(TAG, "selectText: super.onTouchEvent(event)")
//        return super.onTouchEvent(event)
//    }
//
//    protected open fun onSelectTextTouchEvent(event: MotionEvent): Boolean {
//        LogHelper.d(TAG, "selectText: onSelectTextTouchEvent ${event.action}")
//        when (event.action) {
//            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                disableSelectTextMode(event)
//            }
//        }
//        return true
//    }
//
//    /**
//     * 当长按被触发，会立刻回调这个函数
//     * @param downX 长按的位置x
//     * @param downY 长按的位置y
//     * @return 返回值决定了本次长按事件，是否开启selectText模式
//     */
//    protected open fun onSelectTextTriggered(downX: Float, downY: Float): Boolean  = true
//
//    protected fun disableSelectTextMode(event: MotionEvent) {
//        LogHelper.d(TAG, "selectText: disableSelectTextMode")
//        selectTextMode = false
//        if (event.action != MotionEvent.ACTION_UP && event.action != MotionEvent.ACTION_CANCEL) {
//            LogHelper.d(TAG, "selectText: disableSelectTextFlag = true")
//            disableSelectTextFlag = true
//        }
//    }

}