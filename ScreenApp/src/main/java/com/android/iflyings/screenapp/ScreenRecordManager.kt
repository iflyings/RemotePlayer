package com.android.iflyings.screenapp

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.*
import com.android.iflyings.mediaencoder.MediaEncoder
import com.android.iflyings.mediaencoder.TypeUtils
import java.lang.IllegalStateException
import java.lang.Thread.sleep
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ScreenRecordManager(mediaProjection: MediaProjection) {
    companion object {
        private const val TAG = "zw"
        private const val REMOTE_PORT = 18000
    }

    private val mMediaEncoder = MediaEncoder()
    private val mMediaProjection: MediaProjection = mediaProjection
    private val mDatagramSocket = DatagramSocket()

    private var mDisplayWidth: Int = 1280
    private var mDisplayHeight: Int = 800
    private var mRemoteIpAddress: String? = null
    private var mMediaSendThread: HandlerThread? = null
    private var mMediaSendHandler: Handler? = null

    private var packageNumber = 0

    private var mVirtualDisplay: VirtualDisplay? = null

    private var mRecordStart = 0L

    fun setDisplaySize(width: Int, height: Int) {
        if (mVirtualDisplay != null) {
            throw IllegalStateException("VirtualDisplay has been created")
        }
        mDisplayWidth = width
        mDisplayHeight = height
        mMediaEncoder.videoWidth = width
        mMediaEncoder.videoHeight = height
    }
    fun setAudio(audio: Boolean) {
        mMediaEncoder.isAudio = audio
    }
    fun setIpAddress(ip: String?) {
        if (mVirtualDisplay != null) {
            throw IllegalStateException("VirtualDisplay has been created")
        }
        mRemoteIpAddress = ip
    }
    fun prepare() {
        mMediaEncoder.setMediaEncoderCallback(object: MediaEncoder.EncodeAvailableListener {
            override fun onVideoAvailable(buffer: ByteArray, offset: Int, length: Int) {
                //mMediaDecoder.decodeVideo(buffer, offset, length)
                if (mRecordStart == 0L) {
                    mRecordStart = System.nanoTime() / 1000
                }
                sendData(buffer, offset, length, System.nanoTime() / 1000 - mRecordStart)
            }

            override fun onAudioAvailable(buffer: ByteArray, offset: Int, length: Int) {
                //mAudioFile?.write(buffer,offset,length)
                //mMediaDecoder.decodeAudio(buffer, offset, length)
            }
        })
        mMediaEncoder.prepare()
    }
    fun start() {
        mMediaSendThread = HandlerThread("screen-manager")
        mMediaSendThread!!.start()
        mMediaSendHandler = Handler(mMediaSendThread!!.looper)

        mMediaEncoder.start()

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("record_screen",
                mDisplayWidth, mDisplayHeight, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mMediaEncoder.getInputSurface(), null, null)//将屏幕数据与surface进行关联
    }
    fun stop() {
        mVirtualDisplay?.release()
        mVirtualDisplay = null

        mMediaEncoder.stop()
        mDatagramSocket.close()

        mMediaSendThread = mMediaSendThread?.takeIf { it.isAlive }?.run {
            quitSafely()
            join()
            null
        }
    }

    private fun sendData(buffer: ByteArray, offset: Int, length: Int, presentationTimeUs: Long) {
        if (mRemoteIpAddress == null || mMediaSendHandler == null || length > 50000) {
            return
        }
        mMediaSendHandler!!.post {

            var packetRemain = length
            var packetIndex = 0
            while (packetRemain > 0) {
                //Log.i(TAG, "length = $length,packetRemain = $packetRemain")
                val packetHead = 20
                val packetWrite = if (packetRemain > 1450) 1450 else packetRemain
                val packetBuffer = ByteArray(packetWrite + packetHead)

                TypeUtils.intToByteArray(packageNumber, packetBuffer, 0)
                TypeUtils.intToByteArray(packetIndex, packetBuffer, 4)
                TypeUtils.intToByteArray(length, packetBuffer, 8)
                TypeUtils.longToByteArray(presentationTimeUs, packetBuffer, 12)
                System.arraycopy(buffer, offset + length - packetRemain, packetBuffer, packetHead, packetWrite)

                val datagramPacket = DatagramPacket(packetBuffer, 0, packetBuffer.size, InetAddress.getByName(mRemoteIpAddress), REMOTE_PORT)
                mDatagramSocket.send(datagramPacket)
                packetIndex += 1
                packetRemain -= packetWrite
                sleep(0, 1000)
            }
            //val datagramPacket = DatagramPacket(buffer, offset, length, InetAddress.getByName(mRemoteIpAddress), REMOTE_PORT)
            //mDatagramSocket.send(datagramPacket)

            packageNumber += 1
        }
    }
}