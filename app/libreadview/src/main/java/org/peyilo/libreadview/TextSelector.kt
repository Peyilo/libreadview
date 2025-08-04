package org.peyilo.libreadview

interface TextSelector {

    /**
     * 获取当前选中的文本（从原始内容中截取）
     */
    fun getSelectedText(): String?

    /**
     * 当前选区的起始位置
     */
    fun getSelectionStart(): Int

    /**
     * 当前选区的结束位置
     */
    fun getSelectionEnd(): Int

}