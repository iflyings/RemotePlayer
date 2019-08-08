package com.android.iflyings.recvapp

import android.view.Surface
import com.android.iflyings.mediaencoder.MediaDecoder
import com.android.iflyings.mediaencoder.MediaReceiver


class MediaRecvManager {

    private val mMediaDecoder = MediaDecoder()
    private val mMediaReceiver = MediaReceiver(18000)

    fun setOutputSurface(surface: Surface) {
        mMediaDecoder.setOutputSurface(surface)
    }

    fun prepare() {
        mMediaReceiver.setOnDecodeCallback(object :MediaReceiver.OnDecodeCallback{
            override fun decode(frame: ByteArray, offset: Int, length: Int, presentationTimeUs: Long) {
                mMediaDecoder.decodeVideo(frame, offset, length, presentationTimeUs)
            }
        })
        mMediaDecoder.prepare()
    }
    fun start() {
        mMediaReceiver.start()
        mMediaDecoder.start()
    }
    fun stop() {
        mMediaReceiver.stop()
        mMediaDecoder.stop()
    }
}