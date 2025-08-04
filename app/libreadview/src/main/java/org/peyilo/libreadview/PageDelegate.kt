package org.peyilo.libreadview

import android.view.ViewGroup

interface PageDelegate {
    fun getItemViewType(): Int
    fun onCreateView(parent: ViewGroup): PageContainer.ViewHolder
    fun onBindView(holder: PageContainer.ViewHolder, position: Int, data: Any)
}
