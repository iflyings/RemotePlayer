package com.android.iflyings.recvapp

class TypeUtils {

    companion object {

        fun byteArrayToInt(bytes: ByteArray, offset: Int): Int {
            return (bytes[offset + 3].toInt() and 0xFF) or
                    ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                    ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((bytes[offset + 0].toInt() and 0xFF) shl 24)
        }
        fun byteArrayToLong(bytes: ByteArray, offset: Int): Long {
            var value = 0L
            for (index in 0..7) {
                value = value or ((bytes[offset + index].toLong() and 0xFF) shl (8 * (7 - index)))
            }
            return value
        }
        fun intToByteArray(data: Int, byte: ByteArray, offset: Int) {
            byte[offset + 0] = ((data ushr 24) and 0xFF).toByte()
            byte[offset + 1] = ((data ushr 16) and 0xFF).toByte()
            byte[offset + 2] = ((data ushr 8) and 0xFF).toByte()
            byte[offset + 3] = (data and 0xFF).toByte()
        }
        fun longToByteArray(data: Long, byte: ByteArray, offset: Int) {
            byte[offset + 0] = ((data ushr 56) and 0xFF).toByte()
            byte[offset + 1] = ((data ushr 48) and 0xFF).toByte()
            byte[offset + 2] = ((data ushr 40) and 0xFF).toByte()
            byte[offset + 3] = ((data ushr 32) and 0xFF).toByte()
            byte[offset + 4] = ((data ushr 24) and 0xFF).toByte()
            byte[offset + 5] = ((data ushr 16) and 0xFF).toByte()
            byte[offset + 6] = ((data ushr 8) and 0xFF).toByte()
            byte[offset + 7] = (data and 0xFF).toByte()
        }
    }
}