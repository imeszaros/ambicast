package com.github.imeszaros.ambicast

import com.github.imeszaros.ambilight.Ambilight
import com.github.imeszaros.ambilight.model.Layer
import com.github.imeszaros.ambilight.model.Layers
import com.github.imeszaros.ambilight.model.RGB
import com.github.imeszaros.ambilight.model.Topology
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.util.concurrent.ArrayBlockingQueue

class AmbiCastServer(
        private val ambilight: Ambilight,
        private val multicastGroup: String,
        private val multicastPort: Int,
        private val refreshRate: Long) {

    private val senderThreads = mutableListOf<SenderThread>()
    private val multicastThread = MulticastThread()

    init {
        NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { !it.isLoopback && it.isUp && it.supportsMulticast() }
                .onEach { LOG.info("Discovered multicast capable interface: {}", it.displayName) }
                .map { MulticastSocket().apply { this.networkInterface = it } }
                .map { SenderThread(it).apply { this.start() } }
                .toCollection(senderThreads)

        multicastThread.start()
    }

    fun shutdown () {
        multicastThread.shutdown()
        multicastThread.join()
    }

    private fun delay(millis: Long) = try {
        Thread.sleep(millis)
    } catch (e: InterruptedException) {
        // ignore
    }

    private inner class SenderThread(
            private val socket: MulticastSocket) : Thread() {

        private var keepAlive = true

        init {
            name = this::class.simpleName
        }

        private val queue = ArrayBlockingQueue<ByteArray>(1)

        fun post(data: ByteArray) {
            queue.clear()
            queue.add(data)
        }

        fun shutdown() {
            keepAlive = false

            try {
                socket.close()
            } catch (t: Throwable) {
                LOG.error("Unable to close multicast socket: {}", t.message, t)
            }

            interrupt()
        }

        override fun run() {
            val interfaceName = socket.networkInterface.displayName
            LOG.info("Sender thread started for interface {}", interfaceName)

            while (keepAlive) {
                try {
                    queue.take().apply {
                        try {
                            socket.send(DatagramPacket(this, this.size,
                                    InetAddress.getByName(multicastGroup), multicastPort))
                        } catch (t: Throwable) {
                            LOG.error("Network error: {}", t.message, t)
                            delay(DELAY_UNAVAILABLE)
                        }
                    }
                } catch (e: InterruptedException) {
                    // ignore
                }
            }

            LOG.info("Sender thread stopped for interface {}", interfaceName)
        }
    }

    private inner class MulticastThread : Thread() {

        private var keepAlive = true
        private var topology: Topology? = null

        init {
            name = this::class.simpleName
        }

        fun shutdown() {
            keepAlive = false
            interrupt()

            senderThreads.forEach {
                it.shutdown()
                it.join()
            }
        }

        fun writeLayers(layers: Layers, stream: ByteArrayOutputStream) {
            fun writeSide(side: Map<Int, RGB>) = side.values.forEach {
                stream.write(it.r)
                stream.write(it.g)
                stream.write(it.b)
            }

            fun writeLayer(layer: Layer) {
                layer.left?.let(::writeSide)
                layer.top?.let(::writeSide)
                layer.right?.let(::writeSide)
                layer.bottom?.let(::writeSide)
            }

            layers.layer1?.let(::writeLayer)
            layers.layer2?.let(::writeLayer)
            layers.layer3?.let(::writeLayer)
            layers.layer4?.let(::writeLayer)
        }

        fun send(content: ByteArray) {
            senderThreads.forEach { it.post(content) }
        }

        override fun run() {
            LOG.info("Multicast thread has been started.")

            while (keepAlive) {
                val nanoTime = System.nanoTime()

                if (topology == null) {
                    try {
                        topology = ambilight.getTopology()
                        LOG.info("Ambilight service is now available.")
                    } catch (t: Throwable) {
                        LOG.trace(t.message, t)
                        send(byteArrayOf(MSG_TYPE_UNAVAILABLE))
                        delay(DELAY_UNAVAILABLE)
                        continue
                    }
                }

                try {
                    ambilight.getProcessed()
                } catch (t: Throwable) {
                    topology = null
                    LOG.info("Ambilight service is unavailable.")
                    LOG.trace(t.message, t)
                    send(byteArrayOf(MSG_TYPE_UNAVAILABLE))
                    delay(DELAY_UNAVAILABLE)
                    null
                }?.also {
                    val msg = ByteArrayOutputStream()

                    msg.write(MSG_TYPE_AMBILIGHT_DATA.toInt())

                    msg.write(topology!!.let {
                        byteArrayOf(
                                it.layers.toByte(),
                                it.left.toByte(),
                                it.top.toByte(),
                                it.right.toByte(),
                                it.bottom.toByte())
                    })

                    writeLayers(it, msg)

                    send(msg.toByteArray())

                    while (keepAlive && refreshRate - ((System.nanoTime() - nanoTime) / 1_000_000) > 0) {
                        delay(1)
                    }
                }
            }

            LOG.info("Multicast thread exited.")
        }
    }

    companion object AmbiCastServer {

        val LOG = LoggerFactory.getLogger(com.github.imeszaros.ambicast.AmbiCastServer::class.java)!!

        const val DELAY_UNAVAILABLE = 3000L
        const val MSG_TYPE_AMBILIGHT_DATA = 31.toByte()
        const val MSG_TYPE_UNAVAILABLE = 32.toByte()
    }
}