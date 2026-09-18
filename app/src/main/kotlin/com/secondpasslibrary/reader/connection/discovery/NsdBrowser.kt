package com.secondpasslibrary.reader.connection.discovery

internal interface NsdBrowser {
    fun browse(serviceType: String, listener: NsdBrowserListener): AutoCloseable
}

internal interface NsdBrowserListener {
    fun onServiceFound(serviceKey: String)

    fun onServiceResolved(serviceKey: String, attributes: Map<String, ByteArray>)

    fun onServiceLost(serviceKey: String)
}
