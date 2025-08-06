package org.peyilo.libreadview

import android.view.ViewGroup

interface PageDelegate {
    fun getItemViewType(): Int
    fun onCreateView(parent: ViewGroup): AbstractPageContainer.ViewHolder
    fun onBindView(holder: AbstractPageContainer.ViewHolder, position: Int, data: Any)
}
