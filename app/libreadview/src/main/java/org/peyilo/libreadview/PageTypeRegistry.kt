package org.peyilo.libreadview

object PageTypeRegistry {

    private val delegates = mutableMapOf<Int, PageDelegate>()

    fun register(delegate: PageDelegate) {
        val type = delegate.getItemViewType()
        require(!delegates.containsKey(type)) { "ViewType $type already registered" }
        delegates[type] = delegate
    }

    fun getDelegate(viewType: Int): PageDelegate =
        delegates[viewType] ?: error("No delegate for viewType $viewType")

    fun getAllViewTypes(): Set<Int> = delegates.keys

}
