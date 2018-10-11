package com.android.iflyings.mediaencoder

import android.media.*
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

class MediaDecoder {
    companion object {
        private const val TAG = "zw"
        private const val FRAME_RATE = 30
        private const val TIMEOUT_US = -1L

        private const val STATE_INITIALIZED = 0
        private const val STATE_PREPARED = 1
        private const val STATE_DECODING = 2
        private const val STATE_UNINITIALIZED = 3
    }

    private var mState = STATE_INITIALIZED

    private val mVideoCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    private lateinit var mOutputSurface: Surface
    private val mAudioCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private lateinit var mAudioTrack: AudioTrack

    private var mFrameCount = 0

    fun setOutputSurface(surface: Surface) {
        if (mState != STATE_INITIALIZED) {
            throw IllegalStateException("media decoder state is not init")
        }
        mOutputSurface = surface
    }

    private fun initAudioDevice() {
        val minBufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        mAudioTrack = AudioTrack(AudioManager.STREAM_MUSIC, 44100, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM)
    }
    private fun initAudioDecoder() {
        //MediaFormat用于描述音视频数据的相关参数
        val mediaFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 44100, 2)
        //比特率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * 30)
        //用来标记AAC是否有adts头，1->有
        mediaFormat.setInteger(MediaFormat.KEY_IS_ADTS, 1)
        //用来标记aac的类型
        mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        //ByteBuffer key（暂时不了解该参数的含义，但必须设置）
        val data = byteArrayOf(0x11, 0x90.toByte())
        val csd_0 = ByteBuffer.wrap(data)
        mediaFormat.setByteBuffer("csd-0", csd_0)
        //解码器配置
        mAudioCodec.configure(mediaFormat, null, null, 0)
    }
    private fun initVideoDecoder() {
        val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080)
        mVideoCodec.configure(mediaFormat, mOutputSurface, null, 0)
    }

    fun prepare() {
        if (mState != STATE_INITIALIZED) {
            throw IllegalStateException("media decoder state is not init")
        }
        initAudioDevice()
        initAudioDecoder()
        initVideoDecoder()

        mState = STATE_PREPARED
    }
    fun start() {
        if (mState != STATE_PREPARED) {
            throw IllegalStateException("media decoder state is not prepare")
        }

        mAudioTrack.play()
        mAudioCodec.start()
        mVideoCodec.start()

        mState = STATE_DECODING
    }
    fun stop() {
        if (mState != STATE_DECODING) {
            throw IllegalStateException("media decoder state is not decoding")
        }

        mAudioTrack.stop()
        mAudioTrack.release()
        synchronized(mAudioCodec) {
            mAudioCodec.stop()
            mAudioCodec.release()
        }
        synchronized(mVideoCodec) {
            mVideoCodec.stop()
            mVideoCodec.release()
        }

        mState = STATE_UNINITIALIZED
    }

    fun decodeVideo(inputData: ByteArray, inputOffset: Int, inputLength: Int) {
        if (mState != STATE_DECODING) {
            throw IllegalStateException("media decoder state is not decoding")
        }

        synchronized(mVideoCodec) {
            val inputBufferId = mVideoCodec.dequeueInputBuffer(TIMEOUT_US)
            if (inputBufferId >= 0) {
                val inputBuffer = mVideoCodec.getInputBuffer(inputBufferId)!!
                inputBuffer.clear()
                inputBuffer.put(inputData, inputOffset, inputLength)
                val presentationTimeUs = 1000000L * mFrameCount / FRAME_RATE
                mVideoCodec.queueInputBuffer(inputBufferId, 0, inputLength, presentationTimeUs, 0)
                mFrameCount++
                // presentationTimeUs 此缓冲区的显示时间戳（以微秒为单位），通常是这个缓冲区应该呈现的媒体时间
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = mVideoCodec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >= 0) {
                mVideoCodec.releaseOutputBuffer(outputBufferIndex, true)//更新surface
                outputBufferIndex = mVideoCodec.dequeueOutputBuffer(bufferInfo, 0)
            }
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //此处可以或得到视频的实际分辨率，用以修正宽高比
                val mediaFormat = mVideoCodec.outputFormat
                val mediaWidth = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
                val mediaHeight = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
                //val mediaRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                Log.i(TAG, "mediaWidth = $mediaWidth, mediaHeight = $mediaHeight")
            }
        }
    }
    fun decodeAudio(inputData: ByteArray, inputOffset: Int, inputLength: Int) {
        if (mState != STATE_DECODING) {
            throw IllegalStateException("media decoder state is not decoding")
        }

        synchronized(mAudioCodec) {
            val inputBufferId = mAudioCodec.dequeueInputBuffer(-1)
            if (inputBufferId >= 0) {
                val inputBuffer = mAudioCodec.getInputBuffer(inputBufferId)!!
                inputBuffer.clear()
                inputBuffer.put(inputData, inputOffset, inputLength)
                val presentationTimeUs = 1000000L * mFrameCount / FRAME_RATE
                mAudioCodec.queueInputBuffer(inputBufferId, 0, inputLength, presentationTimeUs, 0)
                mFrameCount++
                // presentationTimeUs 此缓冲区的显示时间戳（以微秒为单位），通常是这个缓冲区应该呈现的媒体时间
            }
            val bufferInfo = MediaCodec.BufferInfo()
            var outputBufferIndex = mAudioCodec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputBufferIndex >= 0) {
                mAudioCodec.releaseOutputBuffer(outputBufferIndex, true)//更新surface
                outputBufferIndex = mAudioCodec.dequeueOutputBuffer(bufferInfo, 0)
            }
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                //此处可以或得到视频的实际分辨率，用以修正宽高比
                val mediaFormat = mAudioCodec.outputFormat
                val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val changeCount = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                //val mediaRate = mediaFormat.getInteger(MediaFormat.KEY_FRAME_RATE)
                Log.i(TAG, "sampleRate = $sampleRate, changeCount = $changeCount")
            }
        }
    }
}