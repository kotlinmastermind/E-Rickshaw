package com.vaisentra.mobility.core.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Enterprise Resilient DNS Resolver.
 * Automatically resolves e-rickshaw-z32z.onrender.com even if the physical device's
 * local mobile network or WiFi ISP DNS resolver fails, times out, or blocks Render domains in India.
 *
 * Uses an isolated, bounded ThreadPoolExecutor to prevent ForkJoinPool.commonPool thread starvation
 * and guarantees cancellation of hung system DNS lookups upon timeout.
 */
object ResilientDns : Dns {
    private val RENDER_FALLBACK_IPS = listOf(
        byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte()),
        byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte())
    )

    // Dedicated, bounded executor for DNS resolution to isolate background lookups
    // from ForkJoinPool.commonPool and prevent thread exhaustion on slow ISP networks.
    private val dnsThreadPool = ThreadPoolExecutor(
        1,
        2,
        60L,
        TimeUnit.SECONDS,
        LinkedBlockingQueue<Runnable>(16),
        { runnable ->
            Thread(runnable, "Vasantra-Dns-Resolver").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY
            }
        },
        ThreadPoolExecutor.DiscardOldestPolicy()
    )

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.contains("onrender.com", ignoreCase = true)) {
            // First attempt: try system DNS on isolated bounded executor with safe 2.5s cancellation
            var future: Future<List<InetAddress>>? = null
            try {
                future = dnsThreadPool.submit(Callable {
                    Dns.SYSTEM.lookup(hostname)
                })
                val addresses = future.get(2500, TimeUnit.MILLISECONDS)
                if (!addresses.isNullOrEmpty()) return addresses
            } catch (_: Throwable) {
                // System DNS failed, hung, or timed out — explicitly cancel and interrupt task
                future?.cancel(true)
            }

            // Guaranteed Anycast edge fallback: direct Cloudflare Anycast Origin IPs
            return try {
                RENDER_FALLBACK_IPS.map { ipBytes ->
                    InetAddress.getByAddress(hostname, ipBytes)
                }
            } catch (_: Throwable) {
                listOf(
                    InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte())),
                    InetAddress.getByAddress(hostname, byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte()))
                )
            }
        }
        return try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) addresses else throw UnknownHostException("No IP address found for $hostname")
        } catch (e: UnknownHostException) {
            throw e
        } catch (t: Throwable) {
            throw UnknownHostException("Unable to resolve server address for \"$hostname\": ${t.message}")
        }
    }
}

