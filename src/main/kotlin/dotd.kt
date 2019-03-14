package com.jacklt.ktor

import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.*
import io.ktor.network.tls.tls
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.io.ByteWriteChannel
import kotlinx.coroutines.io.readPacket
import kotlinx.io.core.ByteReadPacket
import kotlinx.io.core.buildPacket
import kotlinx.io.core.readBytes
import java.net.InetSocketAddress
import java.net.SocketAddress
import kotlin.coroutines.coroutineContext

const val LOCAL_PORT = 53
const val DNS_HOST = "1.1.1.1"
const val DNS_PORT = 853

val selectorManager = ActorSelectorManager(Dispatchers.IO)

data class DnsPacket(val id: Short, val data: ByteArray) {
    val packetSize = 2 + data.size
}

private fun ByteReadPacket.toDnsPacket() = DnsPacket(readShort(), readBytes())

private fun DnsPacket.toByteReadPacket(hasSizePrefix: Boolean) = buildPacket {
    if (hasSizePrefix) writeShort(packetSize.toShort())
    writeShort(id)
    writeFully(data, 0, data.size)
}

fun main() = runBlocking {
    println("dotd.kt: Start DnsOverTLS")
    val dnsService = DnsService().apply { launch { start() } }
    val udp = aSocket(selectorManager).udp().bind(InetSocketAddress(LOCAL_PORT))
    val addr = mutableMapOf<Short, SocketAddress>()
    launch {
        udp.incoming.consumeEach { datagram ->
            val dnsPacket = datagram.packet.toDnsPacket()
            addr[dnsPacket.id] = datagram.address
            println("--> $dnsPacket = ${String(dnsPacket.data)}")
            dnsService.incoming.send(dnsPacket)
        }
    }
    dnsService.outgoing.consumeEach { dnsPacket ->
        println("<-- $dnsPacket = ${String(dnsPacket.data)}")
        udp.outgoing.send(Datagram(dnsPacket.toByteReadPacket(hasSizePrefix = false), addr.remove(dnsPacket.id)!!))
    }
    println("dotd.kt: Finish DnsOverTLS")
}

class DnsService {
    private val requestPending = mutableMapOf<Short, List<Byte>>()
    private val cache = mutableMapOf<List<Byte>, ByteArray>()
    private val _incoming = Channel<DnsPacket>()
    private val _outgoing = Channel<DnsPacket>()
    val incoming: SendChannel<DnsPacket> = _incoming
    val outgoing: ReceiveChannel<DnsPacket> = _outgoing

    suspend fun socket() = aSocket(selectorManager).tcp().connect(DNS_HOST, DNS_PORT).tls(coroutineContext)

    suspend fun start() = coroutineScope {
        var socket: Socket? = null
        var readJob: Job? = null
        var w: ByteWriteChannel? = null
        _incoming.consumeEach {
            requestPending[it.id] = it.data.toList()
            val cached = cache[it.data.toList()]
            if (cached != null) {
                _outgoing.send(DnsPacket(it.id, cached))
            } else {
                if (socket?.isClosed != false) {
                    println("DnsService: new socket")
                    readJob?.cancel()
                    socket = socket()
                    w = socket!!.openWriteChannel()
                    readJob = launch {
                        val r = socket!!.openReadChannel()
                        try {
                            while (true) {
                                val respLen = r.readShort()
                                println("DnsService: read $respLen bytes")
                                val response = r.readPacket(respLen.toInt()).toDnsPacket()
                                cache[requestPending.remove(response.id)!!] = response.data
                                _outgoing.send(response)
                            }
                        } catch (e: ClosedReceiveChannelException) {
                            println("DnsService: socket closed")
                            socket!!.close()
                        }
                    }
                }
                println("DnsService: write ${it.packetSize} bytes")
                w!!.writePacket(it.toByteReadPacket(hasSizePrefix = true))
                w!!.flush()
            }
        }
    }
}
