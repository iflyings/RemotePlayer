package com.android.iflyings.cameraapp

import android.view.Surface
import com.android.iflyings.mediaencoder.MediaDecoder
import com.android.iflyings.mediaencoder.MediaEncoder
import com.android.iflyings.mediaencoder.MediaSender

class CameraRecordManager(ip: String, port: Int) {

    private val mMediaEncoder = MediaEncoder()
    private val mMediaDecoder = MediaDecoder()
    //private var mImageReader: ImageReader? = null
    //private var mAudioFile: BufferedOutputStream? = null
    private var mRecordStart = 0L

    private var mMediaSender = MediaSender(ip, port)

    fun getInputSurface(): Surface {
        return mMediaEncoder.getInputSurface()
    }
    fun setOutputSurface(surface: Surface) {
        mMediaDecoder.setOutputSurface(surface)
    }
    fun setMediaSize(width: Int, height: Int) {
        mMediaEncoder.videoWidth = width
        mMediaEncoder.videoHeight = height
        //mImageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
    }

    fun prepare() {
        //setupImageReader(mImageReader!!)
        mMediaEncoder.setMediaEncoderCallback(object: MediaEncoder.EncodeAvailableListener {
            override fun onVideoAvailable(buffer: ByteArray, offset: Int, length: Int) {
                if (mRecordStart == 0L) {
                    mRecordStart = System.nanoTime() / 1000
                }
                val presentationTimeUs = System.nanoTime() / 1000 - mRecordStart
                mMediaDecoder.decodeVideo(buffer, offset, length, presentationTimeUs)
                mMediaSender.sendData(buffer, offset, length, presentationTimeUs)
            }
            override fun onAudioAvailable(buffer: ByteArray, offset: Int, length: Int) {
                //mAudioFile?.write(buffer,offset,length)
                mMediaDecoder.decodeAudio(buffer, offset, length)
            }
        })

        mMediaEncoder.prepare()
        mMediaDecoder.prepare()
    }

    fun start() {
        //mAudioFile = BufferedOutputStream(FileOutputStream(File( "/data/user/0/com.android.iflyings.camera.sendapp/cache/record.aac")), 200 * 1024)
        mMediaDecoder.start()
        mMediaEncoder.start()
    }
    fun stop() {
        //mAudioFile?.close()
        mMediaEncoder.stop()
        mMediaDecoder.stop()
    }
}