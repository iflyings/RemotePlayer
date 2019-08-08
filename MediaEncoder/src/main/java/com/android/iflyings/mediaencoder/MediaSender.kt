package com.android.iflyings.mediaencoder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MediaSender(ip: String, port: Int) {

    private val mDatagramSocket = DatagramSocket()
    private var mRemoteIpAddress = ip
    private var mRemotePort = port

    private var mPackageNumber = 0

    fun sendData(buffer: ByteArray, offset: Int, length: Int, presentationTimeUs: Long) {
        if (length > 50000) {
            return
        }
        GlobalScope.launch(Dispatchers.IO) {
            var packetRemain = length
            var packetIndex = 0
            val packetHead = 20
            while (packetRemain > 0) {
                val packetBuffer = ByteArray(1400 + packetHead)
                //Log.i(TAG, "length = $length,packetRemain = $packetRemain")
                val packetWrite = if (packetRemain > 1400) 1400 else packetRemain

                TypeUtils.intToByteArray(mPackageNumber, packetBuffer, 0)
                TypeUtils.intToByteArray(packetIndex, packetBuffer, 4)
                TypeUtils.intToByteArray(length, packetBuffer, 8)
                TypeUtils.longToByteArray(presentationTimeUs, packetBuffer, 12)
                System.arraycopy(buffer, offset + length - packetRemain, packetBuffer, packetHead, packetWrite)

                val datagramPacket = DatagramPacket(packetBuffer, 0, packetWrite + packetHead,
                        InetAddress.getByName(mRemoteIpAddress), mRemotePort)
                mDatagramSocket.send(datagramPacket)
                packetIndex += 1
                packetRemain -= packetWrite
                //delay(1)
            }
            mPackageNumber += 1
        }
    }
}