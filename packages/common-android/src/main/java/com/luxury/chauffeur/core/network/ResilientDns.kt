package com.luxury.chauffeur.core.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Enterprise Resilient DNS Resolver.
 * Automatically resolves e-rickshaw-z32z.onrender.com even if the physical device's
 * local mobile network or WiFi ISP DNS resolver fails, times out, or blocks Render domains in India.
 */
object ResilientDns : Dns {
    private val RENDER_FALLBACK_IPS = listOf(
        byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 16.toByte()),
        byteArrayOf(216.toByte(), 24.toByte(), 57.toByte(), 18.toByte())
    )

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.contains("onrender.com", ignoreCase = true)) {
            // First attempt: try system DNS with a quick 2.5-second timeout
            try {
                val future = CompletableFuture.supplyAsync {
                    Dns.SYSTEM.lookup(hostname)
                }
                val addresses = future.get(2500, TimeUnit.MILLISECONDS)
                if (!addresses.isNullOrEmpty()) return addresses
            } catch (_: Throwable) {
                // System DNS failed, hung, or timed out
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

