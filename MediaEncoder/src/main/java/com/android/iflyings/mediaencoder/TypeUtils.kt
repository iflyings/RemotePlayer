package com.android.iflyings.mediaencoder

class TypeUtils {

    companion object {

        fun byteArrayToInt(byte: ByteArray): Int {
            return (byte[3].toInt() and 0xFF) or
                    ((byte[2].toInt() and 0xFF) shl 8) or
                    ((byte[1].toInt() and 0xFF) shl 16) or
                    ((byte[0].toInt() and 0xFF) shl 24)
        }
        fun intToByteArray(data: Int, byte: ByteArray, offset: Int) {
            byte[offset + 0] = ((data ushr 24) and 0xFF).toByte()
            byte[offset + 1] = ((data ushr 16) and 0xFF).toByte()
            byte[offset + 2] = ((data ushr 8) and 0xFF).toByte()
            byte[offset + 3] = (data and 0xFF).toByte()
        }
    }
}