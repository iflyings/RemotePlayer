package com.android.iflyings.mediaencoder

class ADTSUtils {

    companion object {

        private val SAMPLE_RATE_TYPE: MutableMap<String, Int> = HashMap()

        fun addADTStoPacket(sampleRate: Int, packet: ByteArray, packetLen: Int) {
            if (SAMPLE_RATE_TYPE.isEmpty()) {
                SAMPLE_RATE_TYPE["96000"] = 0
                SAMPLE_RATE_TYPE["88200"] = 1
                SAMPLE_RATE_TYPE["64000"] = 2
                SAMPLE_RATE_TYPE["48000"] = 3
                SAMPLE_RATE_TYPE["44100"] = 4
                SAMPLE_RATE_TYPE["32000"] = 5
                SAMPLE_RATE_TYPE["24000"] = 6
                SAMPLE_RATE_TYPE["22050"] = 7
                SAMPLE_RATE_TYPE["16000"] = 8
                SAMPLE_RATE_TYPE["12000"] = 9
                SAMPLE_RATE_TYPE["11025"] = 10
                SAMPLE_RATE_TYPE["8000"] = 11
                SAMPLE_RATE_TYPE["7350"] = 12
            }
            val sampleRateType = SAMPLE_RATE_TYPE[sampleRate.toString()]!!
            val profile = 2 // AAC LC
            val chanCfg = 2 // CPE
            // fill in ADTS data
            packet[0] = 0xFF.toByte()
            packet[1] = 0xF9.toByte()
            packet[2] = ((profile - 1 shl 6) + (sampleRateType shl 2) + (chanCfg shr 2)).toByte()
            packet[3] = ((chanCfg and 3 shl 6) + (packetLen shr 11)).toByte()
            packet[4] = (packetLen and 0x7FF shr 3).toByte()
            packet[5] = ((packetLen and 7 shl 5) + 0x1F).toByte()
            packet[6] = 0xFC.toByte()
        }
    }
}