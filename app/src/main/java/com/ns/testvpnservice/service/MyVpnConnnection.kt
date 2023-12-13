package com.ns.testvpnservice.service

import android.app.PendingIntent
import android.os.ParcelFileDescriptor
import android.util.Log
import com.ns.testvpnservice.bean.IPPacket
import com.ns.testvpnservice.bean.TCPPacket
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.TimeUnit

class MyVpnConnection(private val mService: MyVPNService, private  val connectionId: Int, private val mServerName: String, private  val mServerPort: Int,
    private val allow: Boolean, private val packages: Set<String>) : Runnable{
    companion object {
        private const val TAG = "MyVpnConnection"
        private const val MAX_HANDSHAKE_ATTEMPTS = 50
        private const val MAX_PACKET_SIZE = Short.MAX_VALUE.toInt()
        private var IDLE_INTERVAL_MS = TimeUnit.MILLISECONDS.toMillis(100)
        private var KEEPALIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(15)
        private var RECEIVE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(20)
    }
    private lateinit var mConfigureIntent: PendingIntent
    private var mListener: OnEstablishListener? = null


    fun interface OnEstablishListener {
        fun onEstablish(tunInterface: ParcelFileDescriptor?)
    }


    override fun run() {
        Log.i(TAG, "Starting")
        val addr = InetSocketAddress(mServerName, mServerPort)
        var attempt = 0
        while (attempt < 10) {
            if (run2(addr)) {
                attempt = 0
            }

            Thread.sleep(3000)
            attempt++
        }
        Log.i(TAG, "Giving up")
    }

    private fun run2(server: SocketAddress): Boolean {
        var iface: ParcelFileDescriptor? = null
        var connected = false
        try {
//            val tunnel = DatagramChannel.open()
              // Protect the tunnel before connecting to avoid loopback.
//            if (!mService.protect(tunnel.socket())) {
//                throw IllegalStateException("Cannot protect the tunnel")
//            }

//            tunnel.connect(server)
//            tunnel.configureBlocking(false)

//            iface = handshake(tunnel)
            iface = configure("mock")
            iface ?: return false

            connected = true

            val input = FileInputStream(iface.fileDescriptor)
            val output = FileOutputStream(iface.fileDescriptor)
            val packet = ByteBuffer.allocate(MAX_PACKET_SIZE)

            // Timeouts:
            //   - when data has not been sent in a while, send empty keepalive messages.
            //   - when data has not been received in a while, assume the connection is broken.
            var lastSendTime = System.currentTimeMillis()
            var lastReceiveTime = System.currentTimeMillis()

            while (true) {
                var idle = true

                val len = input.read(packet.array())
                if (len > 0) {
                    packet.limit(len)
                    val byteArray = ByteArray(len)
                    packet.get(byteArray)
                    val data = parseIpv4Packet(byteArray)
//                    packet.position(0)
//                    tunnel.write(ByteBuffer.wrap(byteArray))
                    output.write(data)
                    packet.clear()
                }
//                len = tunnel.read(packet)
//                if (len > 0) {
//                    // ignore control message, which start with zero
//                    output.write(packet.array())
//                    packet.clear()
//                }
            }
        } catch (e: SocketException) {
            Log.e(TAG, "Cannot use socket ${e.message}")
        } finally {
            iface?.close()
        }

        return connected
    }

    private fun run(server: SocketAddress): Boolean {
        var iface: ParcelFileDescriptor? = null
        var connected = false
        try {
            val tunnel = DatagramChannel.open()
            //  Protect the tunnel before connecting to avoid loopback.
            if (!mService.protect(tunnel.socket())) {
                throw IllegalStateException("Cannot protect the tunnel")
            }

            tunnel.connect(server)
            tunnel.configureBlocking(false)

            iface = handshake(tunnel)
            iface ?: return false

            connected = true

            val input = FileInputStream(iface.fileDescriptor)
            val output = FileOutputStream(iface.fileDescriptor)
            val packet = ByteBuffer.allocate(MAX_PACKET_SIZE)

            // Timeouts:
            //   - when data has not been sent in a while, send empty keepalive messages.
            //   - when data has not been received in a while, assume the connection is broken.
            var lastSendTime = System.currentTimeMillis()
            var lastReceiveTime = System.currentTimeMillis()

            while (true) {
                var idle = true

                var len = input.read(packet.array())
                if (len > 0) {
                    tunnel.write(packet)
                    packet.clear()

                    idle = false
                    lastReceiveTime = System.currentTimeMillis()
                }

                len = tunnel.read(packet)
                if (len > 0) {
                    // ignore control message, which start with zero
                    if (packet.get(0) != 0.toByte()) {
                        output.write(packet.array())
                    }
                    packet.clear()

                    idle = false
                    lastSendTime = System.currentTimeMillis()
                }
                // If we are idle or waiting for the network, sleep for a
                // fraction of time to avoid busy looping.
                if (idle) {
                    Thread.sleep(IDLE_INTERVAL_MS)
                    val timeNow = System.currentTimeMillis()

                    if (lastSendTime + KEEPALIVE_INTERVAL_MS <= timeNow) {
                        // We are receiving for a long time but not sending.
                        // Send empty control messages.
                        packet.put(0.toByte())
                        packet.limit(1)

                        for (i in 1..3) {
                            packet.position(0)
//                            tunnel.write(packet)
                        }
                        packet.clear()
                        lastSendTime = timeNow
                    } else if (lastReceiveTime + RECEIVE_TIMEOUT_MS <= timeNow) {
                        throw IllegalStateException("Timed out")
                    }
                }
            }
        } catch (e: SocketException) {
            Log.e(TAG, "Cannot use socket ${e.message}")
        } finally {
            iface?.close()
        }

        return connected
    }

    fun setConfigureIntent(intent: PendingIntent) {
        mConfigureIntent = intent
    }

    fun setOnEstablishListener(listener: OnEstablishListener?) {
        mListener = listener
    }

    private fun handshake(tunnel: DatagramChannel): ParcelFileDescriptor? {
        // To build a secured tunnel, we should perform mutual authentication
        // and exchange session keys for encryption. To keep things simple in
        // this demo, we just send the shared secret in plaintext and wait
        // for the server to send the parameters.

        // Allocate the buffer for handshaking. We have a hardcoded maximum
        // handshake size of 1024 bytes, which should be enough for demo
        // purposes.
        val packet = ByteBuffer.allocate(1024)
        packet.put(0.toByte()).flip()
        // Send the secret several times in case of packet loss.
        for (i in 0..2) {
            packet.position(0)
            tunnel.write(packet)
        }
        packet.clear()

        // Wait for the parameters within a limited time.
        for (i in 1 .. MAX_HANDSHAKE_ATTEMPTS) {
            Thread.sleep(IDLE_INTERVAL_MS)

            val length = tunnel.read(packet)
            if (length > 0 && packet.get(0) == 0.toByte()) {
                return configure(String(packet.array(), 1, length - 1, US_ASCII).trim())
            }
            return configure("mock")
        }
        throw IOException("Time out")
    }

    private fun configure(parameters: String): ParcelFileDescriptor? {
        val builder = mService.Builder()
//        parameters.split(" ").forEach {p ->
//            val fields = p.split(",")
//                when (fields[0].get(0)) {
//                    'm' -> builder.setMtu(fields[1].toInt())
//                    'a' -> builder.addAddress(fields[1], fields[2].toInt())
//                    'r' -> builder.addRoute(fields[1], fields[2].toInt())
//                    'd' -> builder.addDnsServer(fields[1])
//                    's' -> builder.addSearchDomain(fields[1])
//                }
//        }
//        builder.setMtu()
        builder.addAddress("10.0.0.1", 32)
        builder.addRoute("0.0.0.0", 0)
        val vpnInterface: ParcelFileDescriptor?
        packages.forEach {
            if (allow) {
                builder.addAllowedApplication(it)
            } else {
                builder.addDisallowedApplication(it)
            }
        }
        builder.setSession(mServerName).setConfigureIntent(mConfigureIntent)
        synchronized(mService) {
            vpnInterface = builder.establish()
            mListener?.onEstablish(vpnInterface)
        }
        Log.i(TAG, "New interface:${vpnInterface.toString()} (${parameters})")

        return vpnInterface
    }

    private fun parseIpv4Packet(packetData: ByteArray): ByteArray {
        if (packetData.size < 20) {
            Log.e(TAG, "Invalid IPv4 packet. Minimum length is 20 bytes.")
            return ByteArray(0)
        }
        val ipPacket = IPPacket(packetData)
        val ipHeaderBean = ipPacket.headerBean
        Log.i(TAG,"IP Header src:$ipHeaderBean")
        ipHeaderBean.settingSrcIp(ipHeaderBean.dstIP)
        ipHeaderBean.settingDstIp(InetAddress.getByName("10.0.0.1"))
        ipHeaderBean.refreshChecksum()
        Log.i(TAG,"IP Header refresh checksum:$ipHeaderBean")
        if (ipHeaderBean.isTCP()) {
            val tcpPacket = TCPPacket(packetData.copyOfRange(20, packetData.size))
            val tcpHeader = tcpPacket.headerBean
            Log.i(TAG,"TCP Header src:$tcpHeader")
//            tcpHeader.settingSrcPort(tcpHeader.dstPort)
            tcpHeader.settingDstPort(3939)
            tcpHeader.refreshChecksum(ipHeaderBean)
            ipPacket.settingPayload(tcpPacket.toData())
//            val tcpHeader2 = TCPPacket(tcpPacket.toData()).headerBean
            Log.i(TAG,"TCP Header refresh checksum:$tcpHeader")
        }
//        System.arraycopy(destinationIp.address, 0, packetData, 12, 4)
//        System.arraycopy(InetAddress.getByName("172.217.163.46").address, 0, packetData, 16, 4)
        // TCP local server
//        val tcpListenerAddr = InetAddress.getByName("localhost:3939")
//        System.arraycopy("localhost", 0, packetData, 16, 4)

//        val sourceIp2 = InetAddress.getByAddress(packetData.copyOfRange(12, 16))
//        val destinationIp2 = InetAddress.getByAddress(packetData.copyOfRange(16, 20))

        // Parse TCP Header
//        val tcpHeader = packetData.copyOfRange(20, 24)
//        val srcPort = (tcpHeader[0].toInt() and 0xFF shl 8) or (tcpHeader[1].toInt() and 0xFF)
//        val dstPort = (tcpHeader[2].toInt() and 0xFF shl 8) or (tcpHeader[3].toInt() and 0xFF)

//        System.arraycopy(dstPort, 0, packetData, 20, 2)
//        System.arraycopy(3939, 0, packetData, 20, 2)



//        ipHeaderBean.settingSrcIp(InetAddress.getByName("192.168.0.66"))
//        ipHeaderBean.settingDstIp(InetAddress.getByName("192.168.0.99"))
//        Log.i(TAG,"new:$ipHeaderBean")
//        val ipPacket2 = IPPacket(ipPacket.toData())
//        Log.i(TAG,"new2:${ipPacket2.headerBean}")
//        ipHeaderBean.setSrcIp(ipHeaderBean.dstIP)

        return ipPacket.toData()
    }
}