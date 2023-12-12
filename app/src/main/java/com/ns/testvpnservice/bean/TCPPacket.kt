package com.ns.testvpnservice.bean

import java.nio.ByteBuffer

class TCPPacket(val tcpPacketData: ByteArray) {
    val headerBean: TCPHeader = TCPHeader(tcpPacketData.copyOfRange(0, 20))

    class TCPHeader(val headerData: ByteArray) {
        var srcPort: Int
        var dstPort: Int
        init {
            // (tcpHeader[0].toInt() and 0xFF shl 8) or (tcpHeader[1].toInt() and 0xFF)
            srcPort = (headerData[0].toInt() and 0xFF shl 8) or (headerData[1].toInt() and 0xFF)
            dstPort = (headerData[2].toInt() and 0xFF shl 8) or (headerData[3].toInt() and 0xFF)
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

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("TCP:{\n")
            sb.append("Source port:${this.srcPort}\n")
            sb.append("Destination port:${this.dstPort}\n")
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