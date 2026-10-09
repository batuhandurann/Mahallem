package com.batuhanduran.burada.data.remote

/**
 * Bounds private-photo decoding to a useful chat-thumbnail resolution.
 * A 5 MB compressed image may otherwise allocate >60 MB when fully decoded.
 *
 * Android BitmapFactory.inSampleSize uses power-of-two reductions reliably.
 * We choose the smallest such value keeping BOTH output dimensions <= limit.
 */
internal object PhotoDecodePolicy {
    const val MAX_DISPLAY_EDGE = 2048

    fun sampleSize(width: Int, height: Int, maxEdge: Int = MAX_DISPLAY_EDGE): Int {
        require(width > 0 && height > 0) { "Photo dimensions must be positive" }
        require(maxEdge > 0) { "Maximum dimension must be positive" }
        val longest = maxOf(width, height).toLong()
        var sample = 1
        while ((longest + sample - 1) / sample > maxEdge) {
            check(sample <= Int.MAX_VALUE / 2) { "Image dimension exceeds supported range" }
            sample *= 2
        }
        return sample
    }
}
