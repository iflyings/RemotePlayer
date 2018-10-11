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

    private val mRecvBufferQueue = LinkedBlockingQueue<ByteArray>()

    @Volatile private var isThreadRunning = false

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
            val datagramSocket = DatagramSocket(LOCAL_PORT)
            datagramSocket.soTimeout = 3000

            val dp = DatagramPacket(ByteArray(1800), 1800)
            while (isThreadRunning) {
                try {
                    datagramSocket.receive(dp)
                } catch (e: SocketTimeoutException) {
                    e.printStackTrace()
                    continue
                }
                if (dp.data.isNotEmpty()) {
                    val recvBuffer = ByteArray(dp.length)
                    System.arraycopy(dp.data, 0, recvBuffer, 0, dp.length)
                    mRecvBufferQueue.offer(recvBuffer, 2, TimeUnit.SECONDS)
                }
            }
            datagramSocket.close()
        }
    }
    inner class MediaDecodeRunnable : Runnable {
        override fun run() {
            val frameBytes = ByteArray(100000)
            var frameWriteLength = 0
            var nowPacketLength = 0
            var prevPacketIndex = 0

            while (isThreadRunning) {
                val recvBuffer = mRecvBufferQueue.poll(10, TimeUnit.MICROSECONDS)
                if (null != recvBuffer) {
                    val packetLength = TypeUtils.byteArrayToInt(recvBuffer, 0)
                    val packetIndex = TypeUtils.byteArrayToInt(recvBuffer, 4)
                    if (packetLength > 100000) {
                        throw IllegalStateException("recv package is too large")
                    }
                    Log.i("zw", "packetLength = $packetLength,nowPacketLength = $nowPacketLength,packetIndex = $packetIndex")
                    if (packetIndex == 0) {
                        if (nowPacketLength != 0) {
                            Log.i("zw","packet has been lost")
                        }
                        nowPacketLength = packetLength
                        prevPacketIndex = 0
                        frameWriteLength = 0
                    } else {
                        if (packetLength == nowPacketLength && prevPacketIndex + 1 == packetIndex) {
                            prevPacketIndex = packetIndex
                        } else {
                            Log.i("zw","packet order is failed")
                            continue
                        }
                    }
                    System.arraycopy(recvBuffer, 8, frameBytes, frameWriteLength, recvBuffer.size - 8)
                    frameWriteLength += recvBuffer.size - 8
                    if (nowPacketLength == frameWriteLength) {
                        synchronized(isThreadRunning) {
                            if (isThreadRunning) {
                                mMediaDecoder.decodeVideo(frameBytes, 0, frameWriteLength)
                            }
                        }
                        nowPacketLength = 0
                    }
                }
            }
        }
    }
}