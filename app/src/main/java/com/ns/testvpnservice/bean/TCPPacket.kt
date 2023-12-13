package com.ns.testvpnservice.bean

import com.ns.testvpnservice.Tools
import java.nio.ByteBuffer

class TCPPacket(val tcpPacketData: ByteArray) {
    val headerBean: TCPHeader = TCPHeader(tcpPacketData.copyOfRange(0, 20))
    val payload: ByteArray
    init {
        val buffer = ByteBuffer.wrap(tcpPacketData)
        payload = ByteArray(tcpPacketData.size - 20)
        buffer.position(20)
        buffer.get(payload)
    }

    inner class TCPHeader(val headerData: ByteArray) {
        var srcPort: Int
        var dstPort: Int
        var checksum: Short
        init {
            // (tcpHeader[0].toInt() and 0xFF shl 8) or (tcpHeader[1].toInt() and 0xFF)
            srcPort = (headerData[0].toInt() and 0xFF shl 8) or (headerData[1].toInt() and 0xFF)
            dstPort = (headerData[2].toInt() and 0xFF shl 8) or (headerData[3].toInt() and 0xFF)
            checksum = ((headerData[16].toInt() and 0xFF shl 8) or (headerData[17].toInt() and 0xFF)).toShort()
        }

        fun settingSrcPort(port: Int) {
            this.srcPort = port
            val buffer = ByteBuffer.wrap(this.headerData)
            buffer.putShort(0, port.toShort())
        }

        fun settingDstPort(port: Int) {
            this.dstPort = port
            val buffer = ByteBuffer.wrap(this.headerData)
            buffer.putShort(2, port.toShort())
        }

        fun toData(): ByteArray {
            return this.headerData
        }

        fun refreshChecksum(ipHeader: IPPacket.IPHeader) {
            this.headerData[16] = 0x00
            this.headerData[17] = 0x00
            val newSum = Tools.checkTCPSum(ipHeader, this@TCPPacket)
            this.checksum = newSum
            val buffer = ByteBuffer.wrap(this.headerData)
            buffer.putShort(16, newSum)
        }

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("TCP:{\n")
            sb.append("Source port:${this.srcPort}\n")
            sb.append("Destination port:${this.dstPort}\n")
            sb.append("Checksum:${this.checksum}\n")
            sb.append("PayloadSize:${this@TCPPacket.payload.size}\n")
            sb.append("}\n")
            return sb.toString()
        }
    }

    fun toData(): ByteArray {
        val buffer = ByteBuffer.wrap(this.tcpPacketData)
        buffer.position(0)
        buffer.put(this.headerBean.toData())
        return this.tcpPacketData
    }
}