package com.android.iflyings.recvapp

import android.util.Log
import android.view.Surface
import com.android.iflyings.mediaencoder.MediaDecoder
import java.lang.IllegalStateException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class MediaRecvManager {

    companion object {
        private const val LOCAL_PORT = 18000
    }

    @Volatile private var isThreadRunning = false
    private val mRecvBufferQueue = LinkedBlockingQueue<DatagramPacket>()
    private val mMediaDecoder = MediaDecoder()

    fun setOutputSurface(surface: Surface) {
        mMediaDecoder.setOutputSurface(surface)
    }

    fun prepare() {
        mMediaDecoder.prepare()
    }
    fun start() {
        isThreadRunning = true
        Thread(MediaDecodeRunnable()).start()
        Thread(MediaRecvRunnable()).start()
        mMediaDecoder.start()
    }
    fun stop() {
        isThreadRunning = false
        mMediaDecoder.stop()
    }

    inner class MediaRecvRunnable : Runnable {
        override fun run() {
            val datagramSocket = DatagramSocket(LOCAL_PORT).apply {
                receiveBufferSize = 100 * 1024
                soTimeout = 3000
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
                    //val recvBuffer = ByteArray(dp.length)
                    //System.arraycopy(dp.data, 0, recvBuffer, 0, dp.length)
                    //mRecvBufferQueue.offer(recvBuffer, 2, TimeUnit.SECONDS)
                    mRecvBufferQueue.offer(this, 200, TimeUnit.MICROSECONDS)
                }
            }
            datagramSocket.close()
        }
    }
    inner class MediaDecodeRunnable : Runnable {
        override fun run() {
            val frameBytes = ByteArray(100000)
            var frameWriteLength = 0
            var nowPacketNumber = 0
            var prevPacketIndex = 0

            while (isThreadRunning) {
                val datagramPacket = mRecvBufferQueue.poll(10, TimeUnit.MICROSECONDS) ?: continue
                val recvBuffer = datagramPacket.data
                val packetNumber = TypeUtils.byteArrayToInt(recvBuffer, 0)
                val packetIndex = TypeUtils.byteArrayToInt(recvBuffer, 4)
                val packetLength = TypeUtils.byteArrayToInt(recvBuffer, 8)
                val presentationTimeUs = TypeUtils.byteArrayToLong(recvBuffer, 12)
                val packetHeadLength = 20
                if (packetLength > 100000) {
                    throw IllegalStateException("recv package is too large")
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
                            mMediaDecoder.decodeVideo(frameBytes, 0, frameWriteLength, presentationTimeUs)
                        }
                    }
                    nowPacketNumber = 0
                }
                //Log.i("zw","length = $length")
                //mMediaDecoder.decodeVideo(data, 0, length)
            }
        }
    }
}