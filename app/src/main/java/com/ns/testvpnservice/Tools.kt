package com.ns.testvpnservice

import com.ns.testvpnservice.bean.IPPacket.IPHeader

class Tools {
    companion object {
        fun checkIPSum(data: ByteArray, len: Int): Short {
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
    }
}