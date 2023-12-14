package com.ns.testvpnservice.bean

import com.ns.testvpnservice.Tools
import java.nio.ByteBuffer
import kotlin.experimental.and

class TCPPacket(val tcpPacketData: ByteArray) {
    val headerBean: TCPHeader = TCPHeader(tcpPacketData.copyOfRange(0, 20))
    val payloadSize: Int
    var payload: ByteArray? = null

    enum class Flag() {
        URG, ACK, PSH, RST, SYN, FIN
    }

    init {
        val buffer = ByteBuffer.wrap(tcpPacketData)
        payloadSize = tcpPacketData.size - headerBean.size
        if (payloadSize > 0) {
            payload = ByteArray(payloadSize)
            buffer.position(headerBean.size)
            buffer.get(payload)
        }
    }

    inner class TCPHeader(val headerData: ByteArray) {
        var srcPort: Int
        var dstPort: Int
        var checksum: Short
        val size: Int
        val flag: Byte

        init {
            // (tcpHeader[0].toInt() and 0xFF shl 8) or (tcpHeader[1].toInt() and 0xFF)
            srcPort = (headerData[0].toInt() and 0xFF shl 8) or (headerData[1].toInt() and 0xFF)
            dstPort = (headerData[2].toInt() and 0xFF shl 8) or (headerData[3].toInt() and 0xFF)
            size = ((headerData[12].toInt() and 0xF0 shr 4) and 0x0F) * 4
            flag = headerData[13]
            checksum = ((headerData[16].toInt() and 0xFF shl 8) or (headerData[17].toInt() and 0xFF)).toShort()
        }

        private fun isFlag(flag: Flag): Boolean {
            return when (flag) {
                Flag.URG -> this.flag.and(0x20.toByte()) == 0x20.toByte()
                Flag.ACK -> this.flag.and(0x10.toByte()) == 0x10.toByte()
                Flag.PSH -> this.flag.and(0x08.toByte()) == 0x08.toByte()
                Flag.RST -> this.flag.and(0x04.toByte()) == 0x20.toByte()
                Flag.SYN -> this.flag.and(0x02.toByte()) == 0x02.toByte()
                Flag.FIN -> this.flag.and(0x01.toByte()) == 0x01.toByte()
            }
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
            sb.append("Size:${this.size}\n")
            sb.append("URG:" + isFlag(Flag.URG) + "\n")
            sb.append("ACK:" + isFlag(Flag.ACK) + "\n")
            sb.append("PSH:" + isFlag(Flag.PSH) + "\n")
            sb.append("RST:" + isFlag(Flag.RST) + "\n")
            sb.append("SYN:" + isFlag(Flag.SYN) + "\n")
            sb.append("FIN:" + isFlag(Flag.FIN) + "\n")
            sb.append("Checksum:${this.checksum.toUShort()}\n")
            sb.append("PayloadSize:${this@TCPPacket.payloadSize}\n")
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