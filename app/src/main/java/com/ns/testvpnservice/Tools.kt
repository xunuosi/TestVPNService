package com.ns.testvpnservice

import com.ns.testvpnservice.bean.IPPacket
import com.ns.testvpnservice.bean.TCPPacket
import java.nio.ByteBuffer

class Tools {
    companion object {
        fun checkIPSum(data: ByteArray, len: Int): Short {
           return calCheckSum(data, len)
        }

        private fun calCheckSum(data: ByteArray, len: Int): Short {
            var sum: Long = 0

            var i = 0
            while (i < len - 1) {
                sum += ((data[i].toUByte().toLong() shl 8) or data[i + 1].toUByte().toLong())
                i += 2
            }
            // If the length is odd, process the last byte
            if (len % 2 == 1) {
                sum += (data[len - 1].toUByte().toLong() shl 8)
            }
            // Fold the carry bits
            while (sum > 0xFFFF) {
                sum = (sum and 0xFFFF) + (sum shr 16)
            }

            return sum.inv().toShort()
        }

        fun checkTCPSum(ipHeader: IPPacket.IPHeader, tcpPacket: TCPPacket): Short {
            // the total size of the pseudo header(12 Bytes) = IP of the Source (32 bits) + IP of the Destination (32 bits) +TCP/UDP segment Length(16 bit) + Protocol(8 bits) + Fixed 8 bits
            val size = 12 + 20 + ipHeader.payloadSize
            val buffer = ByteBuffer.allocate(size)
            // Write pseudo header
            buffer.put(ipHeader.srcIP.address)
            buffer.put(ipHeader.dstIP.address)
            buffer.put(0x00)
            buffer.put((ipHeader.protocol and 0xFF).toByte())
            buffer.putShort(ipHeader.payloadSize.toShort())
            // Write TCP packet
            buffer.put(tcpPacket.toData())

            return calCheckSum(buffer.array(), size)
        }
    }
}