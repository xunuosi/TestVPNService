package com.ns.testvpnservice

import com.ns.testvpnservice.bean.IPPacket
import com.ns.testvpnservice.bean.TCPPacket
import java.net.InetAddress
import java.nio.ByteBuffer
import kotlin.experimental.inv

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

        fun mockTestPacket(dstPort: Short): ByteArray {
            val data = "Hello".toByteArray()

            // create a byte buffer to store the IP header and the TCP header
            val buffer = ByteBuffer.allocate(40 + data.size)

            // set the IP header fields, such as version, identification, flags, etc.
            buffer.put(0x45.toByte()) // set the IP version to 4 and the IP header length to 5
            buffer.put(0x00.toByte()) // set the type of service to 0
            buffer.putShort((40 + data.size).toShort()) // set the total length to 40 + data size
            buffer.putShort(1234) // set the identification field to 1234
            buffer.putShort(0x4000.toShort()) // set the flags field to 0x4000 (don't fragment)
            buffer.put(64.toByte()) // set the time to live field to 64
            buffer.put(6.toByte()) // set the protocol field to 6 (TCP)
            buffer.putShort(0) // set the header checksum to 0 (will be calculated later)
            val src = InetAddress.getByName("110.242.68.3").address
            buffer.put(src) // set the source address to 192.168.1.1
            val dst = InetAddress.getByName("10.8.0.2").address
            buffer.put(dst) // set the destination address to 192.168.1.5
            // set the TCP header fields, such as source port, destination port, sequence number, etc.
            buffer.putShort(80) // set the source port to 5000
            buffer.putShort(dstPort) // set the destination port to 1419
            buffer.putInt(1000) // set the sequence number to 1000
            buffer.putInt(0) // set the acknowledgment number to 0
            buffer.putShort(0x5000.toShort()) // set the data offset to 5 and the reserved field to 0
            buffer.putShort(0x0002.toShort()) // set the flags field to 0x0002 (SYN) // set the window size to 65535
            buffer.putShort(0) // set the checksum to 0 (will be calculated later)
            buffer.putShort(0) // set the urgent pointer to 0
            // copy the data to the buffer
            buffer.put(data)

            val ipHeaderChecksum = calculateChecksum(buffer.array(), 0, 20)
            buffer.putShort(10, ipHeaderChecksum)

            val tcpHeaderChecksum = calculateChecksum(buffer.array(), 20, 20 + data.size)
            buffer.putShort(36, tcpHeaderChecksum)

            return buffer.array()
        }

        fun calculateChecksum(array: ByteArray, i: Int, i1: Int): Short {
            return checksum(0, array, i, i1)
        }

        fun checksum(sum: Long, buf: ByteArray, offset: Int, len: Int): Short {
            var sum = sum
            sum += getsum(buf, offset, len)
            while (sum shr 16 > 0) {
                sum = (sum and 0xFFFFL) + (sum shr 16)
            }
            return sum.toShort().inv()
        }

        fun getsum(buf: ByteArray, offset: Int, len: Int): Long {
            //	Log.d(TAG,"getsum offset  "+offset+"  len"+len+"  length"+buf.length);
            var offset = offset
            var len = len
            var sum: Long = 0
            while (len > 1) {
                sum += (readShort(buf, offset)
                    .toInt() and 0xFFFF).toLong()
                offset += 2
                len -= 2
            }
            if (len > 0) {
                sum += (buf[offset].toInt() and 0xFF shl 8).toLong()
            }
            return sum
        }

        fun readShort(data: ByteArray, offset: Int): Short {
            val r = data[offset].toInt() and 0xFF shl 8 or (data[offset + 1].toInt() and 0xFF)
            return r.toShort()
        }
    }
}