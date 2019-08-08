package com.android.iflyings.mediaencoder

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MediaReceiver(port: Int) {

    private val mLocalPort = port
    private val mRecvBufferQueue = LinkedBlockingQueue<DatagramPacket>()
    @Volatile
    private var isThreadRunning = false
    private var mOnDecodeCallback: OnDecodeCallback? = null

    fun setOnDecodeCallback(callback: OnDecodeCallback?) {
        mOnDecodeCallback = callback
    }

    fun start() {
        isThreadRunning = true
        Thread(RecvRunnable()).start()
        Thread(DecodeRunnable()).start()
    }

    fun stop() {
        isThreadRunning = false
    }

    inner class DecodeRunnable : Runnable {
        override fun run() {
            val frameBytes = ByteArray(100000)
            var frameWriteLength = 0
            var nowPacketNumber = 0
            var prevPacketIndex = 0

            while (isThreadRunning) {
                val datagramPacket = mRecvBufferQueue.poll(100, TimeUnit.MICROSECONDS) ?: continue
                val recvBuffer = datagramPacket.data
                val packetNumber = TypeUtils.byteArrayToInt(recvBuffer, 0)
                val packetIndex = TypeUtils.byteArrayToInt(recvBuffer, 4)
                val packetLength = TypeUtils.byteArrayToInt(recvBuffer, 8)
                val presentationTimeUs = TypeUtils.byteArrayToLong(recvBuffer, 12)
                val packetHeadLength = 20
                if (packetLength > 100000) {
                    Log.i("zw","packet[$nowPacketNumber] is too large[$packetLength]")
                    continue
                }
                //Log.i("zw", "packetNumber = $packetNumber,nowPacketNumber = $nowPacketNumber,packetIndex = $packetIndex")
                if (packetIndex == 0) {
                    if (nowPacketNumber != 0) {
                        Log.i("zw","packet[$nowPacketNumber] has been lost")
                    }
                    nowPacketNumber = packetNumber
                    frameWriteLength = 0
                } else if (nowPacketNumber != packetNumber || prevPacketIndex + 1 != packetIndex) {
                    continue
                }
                prevPacketIndex = packetIndex
                System.arraycopy(recvBuffer, packetHeadLength, frameBytes, frameWriteLength, datagramPacket.length - packetHeadLength)
                frameWriteLength += datagramPacket.length - packetHeadLength
                //Log.i("zw", "packetLength = $packetLength,frameWriteLength = $frameWriteLength")
                if (packetLength == frameWriteLength) {
                    synchronized(isThreadRunning) {
                        if (isThreadRunning) {
                            mOnDecodeCallback?.decode(frameBytes, 0, frameWriteLength, presentationTimeUs)
                        }
                    }
                    nowPacketNumber = 0
                }
                //Log.i("zw","length = $length")
                //mMediaDecoder.decodeVideo(data, 0, length)
            }
        }
    }

    inner class RecvRunnable : Runnable {
        override fun run() {
            val datagramSocket = DatagramSocket(mLocalPort).apply {
                receiveBufferSize = 100 * 1024
                soTimeout = 2000
            }
            while (isThreadRunning) {
                val dp = DatagramPacket(ByteArray(1500), 1500)
                try {
                    datagramSocket.receive(dp)
                } catch (e: SocketTimeoutException) {
                    e.printStackTrace()
                    continue
                }
                dp.takeIf { it.length > 0 }?.apply {
                    mRecvBufferQueue.offer(this, 200, TimeUnit.MICROSECONDS)
                }
            }
            datagramSocket.close()
        }
    }

    interface OnDecodeCallback {

        fun decode(frame: ByteArray, offset: Int, length: Int, presentationTimeUs: Long)

    }

}