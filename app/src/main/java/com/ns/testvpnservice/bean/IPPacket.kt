package com.ns.testvpnservice.bean

import com.ns.testvpnservice.Tools
import java.net.InetAddress
import java.nio.ByteBuffer

class IPPacket(val ipPacketData: ByteArray) {
    val headerBean: IPHeader = IPHeader(ipPacketData.copyOfRange(0, 20))

    class IPHeader(val ipHeaderData: ByteArray) {
        var srcIP: InetAddress
        var dstIP: InetAddress
        val version: Int
        val headerLen: Int
        val payloadSize: Int
        val protocol: Int
        var checksum: Short
        init {
            version = ipHeaderData[0].toInt() shr 4
            headerLen = (ipHeaderData[0].toInt() and 0x0F) * 4
            payloadSize = ((ipHeaderData[2].toInt() shl 8 or ipHeaderData[3].toInt()).toByte().toUShort() - headerLen.toUInt()).toInt()
            protocol = ipHeaderData[9].toInt()
            checksum = ((ipHeaderData[10].toInt() and 0xFF shl 8) or (ipHeaderData[11].toInt() and 0xFF)).toShort()
            srcIP = InetAddress.getByAddress(ipHeaderData.copyOfRange(12, 16))
            dstIP = InetAddress.getByAddress(ipHeaderData.copyOfRange(16, 20))
        }

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("IP:{\n")
            sb.append("Version:${this.version}\n")
            sb.append("HeaderSize:${this.headerLen}\n")
            sb.append("PayloadSize:${this.payloadSize}\n")
            sb.append("Protocol:${this.protocol.toUInt()}\n")
            sb.append("Src IP:${this.srcIP}\n")
            sb.append("Dst IP:${this.dstIP}\n")
            sb.append("Checksum:${this.checksum.toUShort()}\n")
            sb.append("}\n")
            return sb.toString()
        }
        fun settingSrcIp(address: InetAddress) {
            this.srcIP = address
            System.arraycopy(address.address, 0, ipHeaderData, 12, 4)
        }

        fun settingDstIp(address: InetAddress) {
            this.dstIP = address
            System.arraycopy(address.address, 0, ipHeaderData, 16, 4)
        }

        fun isTCP(): Boolean {
            return this.protocol == 6
        }

        fun isUDP(): Boolean {
            return this.protocol == 17
        }

        fun refreshChecksum() {
            this.ipHeaderData[10] = 0x00
            this.ipHeaderData[11] = 0x00
//            val data = byteArrayOf(0x45, 0x00, 0x00, 0x1c, 0x74, 0x68, 0x00, 0x00,
//                0x80.toByte(), 0x11, 0x59, 0x8F.toByte(), 0xc0.toByte(),
//                0xa8.toByte(), 0x64, 0x01, 0xab.toByte(), 0x46, 0x9c.toByte(), 0xe9.toByte())
//            data[10] = 0x00
//            data[11] = 0x00
            val newSum = Tools.checkIPSum(this.ipHeaderData, this.ipHeaderData.size)
            this.checksum = newSum
            val buffer = ByteBuffer.wrap(this.ipHeaderData)
            buffer.putShort(10, newSum)
        }

        fun toData(): ByteArray {
            return this.ipHeaderData
        }
    }

    fun toData(): ByteArray {
        val buffer = ByteBuffer.wrap(this.ipPacketData)
        buffer.position(0)
        buffer.put(this.headerBean.toData())
        return this.ipPacketData
    }

    fun settingPayload(data: ByteArray) {
        val buffer = ByteBuffer.wrap(this.ipPacketData)
        buffer.position(this.headerBean.headerLen)
        buffer.put(data)
    }
}