package com.jake.duolauncher

internal interface FeedClient {
    fun connect()
    fun resume()
    fun page(progress: Float, scrolling: Boolean)
    fun pause()
    fun closeForHome(): Boolean
    fun disconnect()
}
