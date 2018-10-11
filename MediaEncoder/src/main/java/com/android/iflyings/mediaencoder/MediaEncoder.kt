package com.android.iflyings.mediaencoder

import android.media.*
import android.os.*
import android.util.Log
import android.view.Surface
import java.util.*

class MediaEncoder {
    companion object {
        private const val TAG = "zw"

        private const val STATE_INITIALIZED = 0
        private const val STATE_PREPARED = 1
        private const val STATE_ENCODING = 2
        private const val STATE_UNINITIALIZED = 3
    }

    private var mState = STATE_INITIALIZED

    private lateinit var mVideoThread: HandlerThread
    private lateinit var mVideoHandler: Handler
    private lateinit var mAudioThread: Thread
    @Volatile private var isEncoding = false

    var videoWidth = 1440
    var videoHeight = 1080
    var videoFrameRate = 30//fps
    var IFrameInterval = 3
    var videoBitRate = 2 * 1024 * 1024 //2M码率

    private var mTimeStamp = 0L
    private var mEncodeAvailableListener: EncodeAvailableListener? = null
    private var mInputSurface: Surface? = null


    var isAudio: Boolean = true
    var audioBitRate = 1000 * 30
    private var mSampleRate = 0
    private var audioChannelCount = 2
    private var mPresentationTimeUs = 0L

    private val mAudioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private lateinit var mAudioRecord: AudioRecord
    private lateinit var mAudioInputData: ByteArray

    private val mVideoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private val mVideoCodecCallback = object: MediaCodec.Callback() {
        override fun onInputBufferAvailable(mediaCodec: MediaCodec, inputBufferId: Int) {
            //val inputBuffer = mediaCodec.getInputBuffer(inputBufferId)
            //mediaCodec.queueInputBuffer(inputBufferId, 0, 0, 0, 0)
        }
        override fun onOutputBufferAvailable(mediaCodec: MediaCodec, outputBufferId: Int,
                                             bufferInfo: MediaCodec.BufferInfo) {
            val outputBuffer = mediaCodec.getOutputBuffer(outputBufferId)
            if (outputBuffer != null && bufferInfo.size > 0) {
                val buffer = ByteArray(bufferInfo.size)
                outputBuffer.get(buffer)
                mVideoHandler.post {
                    mEncodeAvailableListener!!.onVideoAvailable(buffer, 0, bufferInfo.size)
                }
            }
            mediaCodec.releaseOutputBuffer(outputBufferId, true)
            if (System.currentTimeMillis() - mTimeStamp >= IFrameInterval * 1000) { // 5秒后，设置请求关键帧的参数 GO
                mTimeStamp = System.currentTimeMillis()
                //做Bundle初始化  主要目的是请求编码器“即时”产生同步帧
                val params = Bundle()
                params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                mediaCodec.setParameters(params)
            }
        }
        override fun onOutputFormatChanged(mediaCodec: MediaCodec, format: MediaFormat) {
            Log.d(TAG, "onOutputFormatChanged")
        }
        override fun onError(mediaCodec: MediaCodec, e: MediaCodec.CodecException) {
            mediaCodec.reset()
            Log.e(TAG, "Encoder Error")
        }
    }

    fun setMediaEncoderCallback(callback: EncodeAvailableListener) {
        mEncodeAvailableListener = callback
    }

    private fun checkMediaCodecInfoByType(mimeType: String): Boolean {
        val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in mediaCodecList.codecInfos) {
            if (!codecInfo.isEncoder) {
                continue
            }
            for (type in codecInfo.supportedTypes) {
                if (type.toLowerCase().endsWith(mimeType) && checkColorFormat(codecInfo)) {
                    return true
                }
            }
        }
        throw IllegalStateException("unsupported MimeType type")
    }
    private fun checkColorFormat(mediaCodecInfo: MediaCodecInfo): Boolean {
        val codecCapabilities = mediaCodecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
        for (format in codecCapabilities.colorFormats) {
            if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                return true
            }
        }
        throw IllegalStateException("unsupported ColorFormat type")
    }

    private fun initAudioDevice() {
        val sampleRates = intArrayOf(44100, 22050, 16000, 11025)
        for (sampleRate in sampleRates) {
            //编码制式
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            // stereo 立体声，
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 3
            mAudioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
            if (mAudioRecord.state != AudioRecord.STATE_INITIALIZED) {
                continue
            }
            mAudioInputData  = ByteArray(bufferSize)
            mSampleRate = sampleRate
            break
        }
    }
    private fun initAudioEncoder() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, mSampleRate, audioChannelCount)
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mAudioInputData.size)
        format.setInteger(MediaFormat.KEY_BIT_RATE, audioBitRate)
        mAudioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }
    private fun initVideoEncoder() {
        checkMediaCodecInfoByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate) //编码器需要, 解码器可选
        format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFrameInterval) //帧间隔(这个参数在很多手机上无效, 第二帧关键帧过了之后全是P帧)
        //format.setInteger(MediaFormat.KEY_ROTATION, 90)
        mVideoCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        if (mEncodeAvailableListener != null) {
            //必须在configure之后，start之前
            mInputSurface = mVideoCodec.createInputSurface()
            //必须在configure之后，start之前
            //final Surface inputSurface = MediaCodec.createPersistentInputSurface();
            //mediaCodec.setInputSurface(inputSurface);
            mVideoCodec.setCallback(mVideoCodecCallback)
        }
    }

    fun prepare() {
        if (mState != STATE_INITIALIZED) {
            throw IllegalStateException("media encoder state is not init")
        }
        if (isAudio) {
            initAudioDevice()
            initAudioEncoder()
        }
        initVideoEncoder()
        mState = STATE_PREPARED
    }
    fun getInputSurface(): Surface {
        if (mState == STATE_PREPARED || mState == STATE_ENCODING) {
            return mInputSurface!!
        }
        throw IllegalStateException("media encoder state is not prepare")
    }

    fun start() {
        if (mState != STATE_PREPARED) {
            throw IllegalStateException("media encoder state is not prepare")
        }

        if (isAudio) {
            isEncoding = true
            mAudioThread = Thread(AudioRunnable())
            mAudioThread.start()

            mAudioCodec.start()
            mAudioRecord.startRecording()
        }

        mVideoThread = HandlerThread("video thread")
        mVideoThread.start()
        mVideoHandler = Handler(mVideoThread.looper)

        mVideoCodec.start()

        mState = STATE_ENCODING
    }
    fun stop() {
        if (mState != STATE_ENCODING) {
            throw IllegalStateException("media encoder state is not encoding")
        }

        if (isAudio) {
            synchronized(isEncoding) {
                isEncoding = false
                mAudioRecord.stop()
                mAudioRecord.release()
                mAudioCodec.stop()
                mAudioCodec.release()
            }
        }

        mVideoCodec.stop()
        mVideoCodec.release()

        mVideoThread.quitSafely()
        mVideoThread.join()

        mState = STATE_UNINITIALIZED
    }

    private fun encode(mediaCodec: MediaCodec, inputData: ByteArray, inputOffset: Int, inputLength: Int) {
        //-1表示一直等待；0表示不等待；其他大于0的参数表示等待毫秒数
        val inputBufferIndex = mediaCodec.dequeueInputBuffer(-1)
        if (inputBufferIndex >= 0) {
            val inputBuffer =  mediaCodec.getInputBuffer(inputBufferIndex)!!
            inputBuffer.clear()
            inputBuffer.put(inputData, inputOffset, inputLength)
            val pts = Date().time * 1000 - mPresentationTimeUs
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, inputLength,  pts, 0)
        }

        val bufferInfo = MediaCodec.BufferInfo()
        var outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
        while (outputBufferIndex >= 0) { //循环解码，直到数据全部解码完成
            val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)!!
            if (mediaCodec == mAudioCodec) {
                val outPacketSize = bufferInfo.size + 7// 7为ADTS头部的大小
                val outputData = ByteArray(outPacketSize)
                outputBuffer.get(outputData, 7, bufferInfo.size)
                ADTSUtils.addADTStoPacket(mSampleRate, outputData, outPacketSize)//添加ADTS 代码后面会贴上
                mEncodeAvailableListener!!.onAudioAvailable(outputData, 0, outputData.size)
            } else if (mediaCodec == mVideoCodec) {
                val outPacketSize = bufferInfo.size
                val outputData = ByteArray(outPacketSize)
                outputBuffer.get(outputData, 0, bufferInfo.size)
                mEncodeAvailableListener!!.onVideoAvailable(outputData, 0, bufferInfo.size)
            }
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
            outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
        }
    }

    interface EncodeAvailableListener {
        fun onVideoAvailable(buffer: ByteArray, offset: Int, length: Int)
        fun onAudioAvailable(buffer: ByteArray, offset: Int, length: Int)
    }

    inner class AudioRunnable : Runnable {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            mPresentationTimeUs = Date().time * 1000
            while (isEncoding) {
                val readSize = mAudioRecord.read(mAudioInputData, 0, mAudioInputData.size)
                if (readSize > 0) {
                    synchronized(isEncoding) {
                        if (isEncoding) {
                            encode(mAudioCodec, mAudioInputData, 0, readSize)
                        }
                    }
                }
            }
        }
    }
}
