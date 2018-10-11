package com.android.iflyings.cameraapp

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.android.iflyings.mediaencoder.MediaDecoder
import com.android.iflyings.mediaencoder.MediaEncoder
import com.android.iflyings.mediaencoder.TypeUtils
import java.nio.ByteBuffer

class CameraRecordManager {
    companion object {
        private const val TAG = "zw"
    }

    private val mMediaEncoder = MediaEncoder()
    private val mMediaDecoder = MediaDecoder()
    private val mBackgroundThread = HandlerThread("MediaThread")
    //private var mImageReader: ImageReader? = null
    //private var mAudioFile: BufferedOutputStream? = null

    val backgroundHandler: Handler
    init {
        mBackgroundThread.start()
        backgroundHandler = Handler(mBackgroundThread.looper)
    }

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
                mMediaDecoder.decodeVideo(buffer, offset, length)
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
        if (mBackgroundThread.isAlive) {
            mBackgroundThread.quitSafely()
            mBackgroundThread.join()
        }
    }

    private fun sendData(outputBuffer: ByteBuffer, size: Int) {
        Log.i(TAG, "sendData = $size")

        var packetIndex = 0
        var packetRemain = size
        while (packetRemain > 0) {
            val length = if (packetRemain > 1024) 1024 else packetRemain
            val buffer = ByteArray(length + 8)
            TypeUtils.intToByteArray(size, buffer, 0)
            TypeUtils.intToByteArray(packetIndex, buffer, 4)
            outputBuffer.get(buffer, 8, length)
            packetIndex += 1
            packetRemain -= length
        }
    }
}