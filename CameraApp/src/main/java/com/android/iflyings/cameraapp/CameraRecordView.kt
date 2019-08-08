package com.android.iflyings.cameraapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import java.util.*
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.util.Size
import android.view.TextureView
import android.util.SparseIntArray
import android.graphics.RectF
import android.graphics.Matrix
import android.os.Handler
import android.os.HandlerThread

class CameraRecordView : TextureView, TextureView.SurfaceTextureListener {
    companion object {
        private const val TAG = "zw"
        private val ORIENTATIONS = SparseIntArray()
    }

    private var mDisplayRotation = 0
    private var mSensorOrientation = 0
    private var mPreviewSize: Size? = null
    private var mCameraDevice: CameraDevice? = null
    private var mCameraCaptureSession: CameraCaptureSession? = null
    private lateinit var mCameraRecordManager: CameraRecordManager

    private lateinit var mBackgroundThread: HandlerThread
    private lateinit var mBackgroundHandler: Handler

    override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, w: Int, h: Int) {
        Log.i(TAG, "onSurfaceTextureSizeChanged")
    }
    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {
        //Log.i(TAG, "onSurfaceTextureUpdated")
    }
    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
        Log.i(TAG, "onSurfaceTextureDestroyed")
        closeCamera()

        mBackgroundHandler.removeCallbacksAndMessages(null)
        mBackgroundThread.quitSafely()
        mBackgroundThread.join()
        return false
    }
    override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, w: Int, h: Int) {
        Log.i(TAG, "onSurfaceTextureAvailable")
        mBackgroundThread = HandlerThread("MediaThread")
        mBackgroundThread.start()
        mBackgroundHandler = Handler(mBackgroundThread.looper)

        createCamera()
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        ORIENTATIONS.append(Surface.ROTATION_0, 90)
        ORIENTATIONS.append(Surface.ROTATION_90, 0)
        ORIENTATIONS.append(Surface.ROTATION_180, 270)
        ORIENTATIONS.append(Surface.ROTATION_270, 180)

        surfaceTextureListener = this
    }

    private fun getPreferredPreviewSize(width: Int, height: Int): Size? {
        val cameraManager =
                context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            // 获取指定摄像头的特性
            val characteristics = cameraManager.getCameraCharacteristics("0")
            // 摄像头方向
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
            // 获取摄像头支持的配置属性
            val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val mapFormats = streamConfigurationMap.outputFormats
                    .filter { it == ImageFormat.YUV_420_888 }
                    .takeIf { it.isNotEmpty() }?.get(0) ?: return null
            // 获取所有支持的预览尺寸
            val mapSizes = streamConfigurationMap.getOutputSizes(mapFormats)
            return mapSizes.filter { it.width >= width && it.height >= height }
                    .takeIf { it.isNotEmpty() }?.sortedBy { size -> size.width*size.height }?.get(0)
                    ?: mapSizes.asList().sortedByDescending { size -> size.width*size.height }[0]
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            return null
        }
    }

    @SuppressLint("MissingPermission")
    private fun createCamera() {
        if (null == mPreviewSize || null == surfaceTexture) {
            return
        }
        configureTransform(this.width, this.height, mDisplayRotation)
        val cameraManager =
                context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraManager.openCamera("0",
                object: CameraDevice.StateCallback() {
                    override fun onOpened(cameraDevice: CameraDevice) {
                        try {
                            mCameraDevice = cameraDevice
                            createMediaRecord()
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }
                    override fun onDisconnected(cameraDevice: CameraDevice) {
                        cameraDevice.close()
                        mCameraDevice = null
                    }
                    override fun onError(cameraDevice: CameraDevice, error: Int) {
                        //Log.e("zw", "Error Code = $error")
                        cameraDevice.close()
                        mCameraDevice = null
                    }
                }, mBackgroundHandler)
    }

    fun openCamera(width: Int, height: Int, displayRotation: Int, ip: String, port: Int) {
        mPreviewSize = getPreferredPreviewSize(width, height)
        Log.i("zw", "PreviewSize = (${mPreviewSize!!.width},${mPreviewSize!!.height})")
        mDisplayRotation = displayRotation
        mCameraRecordManager = CameraRecordManager(ip, port)
        createCamera()
    }
    private fun closeCamera() {
        destroyMediaRecord()
        mCameraDevice?.close()
        mCameraDevice = null
    }

    private fun updatePreview(builder: CaptureRequest.Builder, session: CameraCaptureSession) {
        // 设置相机的控制模式为自动，方法具体含义点进去（auto-exposure, auto-white-balance, auto-focus）
        //builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        try { //设置重复捕获图片信息
            session.setRepeatingRequest(builder.build(), null, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    private fun createMediaPreview() {
        if (null == mCameraDevice || null == mPreviewSize || null == surfaceTexture) {
            return
        }
        mCameraRecordManager.setMediaSize(mPreviewSize!!.width, mPreviewSize!!.height)
        mCameraRecordManager.setOutputSurface(Surface(surfaceTexture))
        mCameraRecordManager.prepare()
        try {
            val builder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val recorderSurface = mCameraRecordManager.getInputSurface() //放在 prepare() 之后
            builder.addTarget(recorderSurface)
            mCameraDevice!!.createCaptureSession(arrayListOf(recorderSurface),
                    object: CameraCaptureSession.StateCallback() {
                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            mCameraCaptureSession = cameraCaptureSession
                            updatePreview(builder, cameraCaptureSession)
                            mCameraRecordManager.start()
                        }
                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            mCameraCaptureSession = null
                            cameraCaptureSession.close()
                            mCameraRecordManager.stop()
                        }
                    }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    private fun destroyMediaPreview() {
        mCameraRecordManager.stop()
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession!!.close()
            mCameraCaptureSession = null
        }
    }
    private fun configureTransform(viewWidth: Int, viewHeight: Int, displayRotation: Int) {
        val matrix = Matrix()
        val texRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufRect = RectF(0f, 0f, mPreviewSize!!.width.toFloat(), mPreviewSize!!.height.toFloat())
        val centerX = texRect.centerX()
        val centerY = texRect.centerY()
        Log.i(TAG,"view = ($viewWidth,$viewHeight),Buffer = (${mPreviewSize!!.width},${mPreviewSize!!.height})")

        val angle = ORIENTATIONS.get(displayRotation)
        matrix.setRotate(angle.toFloat(), centerX, centerY)
        if (angle == 90 || angle == 270) {
            texRect.right = viewHeight.toFloat()
            texRect.bottom = viewWidth.toFloat()
        }
        val scaleX: Float
        val scaleY: Float
        if (viewWidth.toFloat() / viewHeight.toFloat() >= mPreviewSize!!.width.toFloat() / mPreviewSize!!.height.toFloat()) {
            scaleY = viewHeight.toFloat() / texRect.height()
            scaleX = scaleY * texRect.height() * bufRect.width() / (texRect.width() * bufRect.height())
        } else {
            scaleX = viewWidth.toFloat() / texRect.width()
            scaleY = scaleX * texRect.width() * bufRect.height() / (texRect.height() * bufRect.width())
        }
        matrix.postScale(scaleX, scaleY, centerX, centerY)
        setTransform(matrix)
    }
    /////////////////////////////////////////////////////////////////////////////////////
    private fun createMediaRecord() {
        if (null == mCameraDevice || null == mPreviewSize || null == surfaceTexture) {
            return
        }
        mCameraRecordManager.setMediaSize(mPreviewSize!!.width, mPreviewSize!!.height)
        mCameraRecordManager.setOutputSurface(Surface(surfaceTexture))
        mCameraRecordManager.prepare()
        try {
            val builder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val recorderSurface = mCameraRecordManager.getInputSurface() //放在 prepare() 之后
            builder.addTarget(recorderSurface)
            mCameraDevice!!.createCaptureSession(arrayListOf(recorderSurface),
                    object: CameraCaptureSession.StateCallback() {
                        override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                            mCameraCaptureSession = cameraCaptureSession
                            updatePreview(builder, cameraCaptureSession)
                            mCameraRecordManager.start()
                        }
                        override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                            cameraCaptureSession.close()
                            mCameraCaptureSession = null
                            mCameraRecordManager.stop()
                        }
                    }, mBackgroundHandler)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }
    private fun destroyMediaRecord() {
        mCameraRecordManager.stop()
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession!!.close()
            mCameraCaptureSession = null
        }
    }
}
