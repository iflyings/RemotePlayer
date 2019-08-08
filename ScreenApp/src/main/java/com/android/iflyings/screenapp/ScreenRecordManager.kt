package com.android.iflyings.screenapp

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.os.*
import com.android.iflyings.mediaencoder.MediaEncoder
import com.android.iflyings.mediaencoder.MediaSender
import com.android.iflyings.mediaencoder.TypeUtils
import java.lang.IllegalStateException
import java.lang.Thread.sleep
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class ScreenRecordManager(mediaProjection: MediaProjection) {

    private val mMediaEncoder = MediaEncoder()
    private val mMediaProjection: MediaProjection = mediaProjection

    private var mDisplayWidth: Int = 1280
    private var mDisplayHeight: Int = 800

    private var mVirtualDisplay: VirtualDisplay? = null

    private var mRecordStart = 0L

    private lateinit var mMediaSender: MediaSender

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
    fun setIpAddress(ip: String) {
        mMediaSender = MediaSender(ip, 18000)
    }
    fun prepare() {
        mMediaEncoder.setMediaEncoderCallback(object: MediaEncoder.EncodeAvailableListener {
            override fun onVideoAvailable(buffer: ByteArray, offset: Int, length: Int) {
                //mMediaDecoder.decodeVideo(buffer, offset, length)
                if (mRecordStart == 0L) {
                    mRecordStart = System.nanoTime() / 1000
                }
                mMediaSender.sendData(buffer, offset, length, System.nanoTime() / 1000 - mRecordStart)
            }

            override fun onAudioAvailable(buffer: ByteArray, offset: Int, length: Int) {
                //mAudioFile?.write(buffer,offset,length)
                //mMediaDecoder.decodeAudio(buffer, offset, length)
            }
        })
        mMediaEncoder.prepare()
    }
    fun start() {
        mMediaEncoder.start()

        mVirtualDisplay = mMediaProjection.createVirtualDisplay("record_screen",
                mDisplayWidth, mDisplayHeight, 1, DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                mMediaEncoder.getInputSurface(), null, null)//将屏幕数据与surface进行关联
    }
    fun stop() {
        mVirtualDisplay?.release()
        mVirtualDisplay = null

        mMediaEncoder.stop()
    }
}