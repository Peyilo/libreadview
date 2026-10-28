package org.peyilo.libreadview.turning

/** Binding edge relative to the paging axis. Middle bindings are reserved for spread adapters. */
enum class IosCurlBinding {
    Minimum,
    Maximum,
}

/** Android host settings; the portable algorithms do not depend on these types. */
data class IosCurlOptions
@JvmOverloads
constructor(
    val vertical: Boolean = false,
    val binding: IosCurlBinding = IosCurlBinding.Minimum,
    /** Null follows the container layout direction. Vertical navigation is independent of RTL. */
    val rightToLeft: Boolean? = null,
    val shadowSoftness: Float = 1.35f,
    val shadowOpacity: Float = 0.55f,
    /**
     * Budget for two captured pages, raster output and readback, excluding driver-owned textures.
     */
    val bitmapBudgetBytes: Long = 32L * 1024 * 1024,
) {
    init {
        require(shadowSoftness.isFinite() && shadowSoftness in 1f..3f)
        require(shadowOpacity.isFinite() && shadowOpacity in 0f..1f)
        require(bitmapBudgetBytes >= 0)
    }
}
