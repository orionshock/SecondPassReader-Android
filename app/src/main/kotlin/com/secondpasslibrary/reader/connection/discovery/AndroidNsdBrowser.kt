package com.secondpasslibrary.reader.connection.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ext.SdkExtensions
import java.util.concurrent.atomic.AtomicBoolean

internal class AndroidNsdBrowser(context: Context) : NsdBrowser {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)

    @Suppress("DEPRECATION")
    override fun browse(serviceType: String, listener: NsdBrowserListener): AutoCloseable {
        val stopped = AtomicBoolean(false)
        val activeServices = mutableMapOf<String, NsdServiceInfo>()
        val multicastLock = runCatching(::acquireMulticastLockIfRequired).getOrNull()
        lateinit var discoveryListener: NsdManager.DiscoveryListener

        fun stop() {
            if (!stopped.compareAndSet(false, true)) return
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            if (multicastLock?.isHeld == true) multicastLock.release()
            synchronized(activeServices) { activeServices.clear() }
        }

        discoveryListener =
            object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) = Unit

                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    if (stopped.get()) return
                    val key = serviceInfo.serviceKey()
                    synchronized(activeServices) { activeServices[key] = serviceInfo }
                    listener.onServiceFound(key)
                    runCatching {
                        nsdManager.resolveService(
                            serviceInfo,
                            object : NsdManager.ResolveListener {
                                override fun onResolveFailed(
                                    serviceInfo: NsdServiceInfo,
                                    error: Int
                                ) = Unit

                                override fun onServiceResolved(resolved: NsdServiceInfo) {
                                    val isActive = synchronized(activeServices) {
                                        !stopped.get() && activeServices[key] === serviceInfo
                                    }
                                    if (isActive) {
                                        listener.onServiceResolved(key, resolved.attributes)
                                    }
                                }
                            }
                        )
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    val key = serviceInfo.serviceKey()
                    synchronized(activeServices) { activeServices.remove(key) }
                    if (!stopped.get()) listener.onServiceLost(key)
                }

                override fun onDiscoveryStopped(serviceType: String) = Unit

                override fun onStartDiscoveryFailed(serviceType: String, error: Int) = stop()

                override fun onStopDiscoveryFailed(serviceType: String, error: Int) = stop()
            }

        try {
            nsdManager.discoverServices(
                serviceType,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (_: RuntimeException) {
            stop()
        }
        return AutoCloseable(::stop)
    }

    private fun acquireMulticastLockIfRequired(): WifiManager.MulticastLock? {
        if (!requiresMulticastLock()) return null
        return wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private companion object {
        const val MULTICAST_LOCK_TAG = "second-pass-lan-discovery"
        const val AUTOMATIC_MULTICAST_EXTENSION = 7

        fun requiresMulticastLock(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                (
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.TIRAMISU &&
                        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) <
                        AUTOMATIC_MULTICAST_EXTENSION
                    )
    }
}

private fun NsdServiceInfo.serviceKey(): String = "$serviceName\u0000$serviceType"
