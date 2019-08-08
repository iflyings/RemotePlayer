package com.android.iflyings.cameraapp

import android.Manifest
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.Toast

class CameraRecordActivity : AppCompatActivity() {
    companion object {
        private var PERMISSIONS_LIST = arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_PERMISSION_CODE = 683

        private const val PARAM_WIDTH = "width"
        private const val PARAM_HEIGHT = "height"
    }

    private var isRecording = false
    private var mWidth = 0
    private var mHeight = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mWidth = intent.getIntExtra(PARAM_WIDTH, 1920)
        mHeight = intent.getIntExtra(PARAM_HEIGHT, 1080)

        requestedOrientation = if (mWidth > mHeight) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        ib_control.setOnClickListener {
            isRecording = if (isRecording) {
                ib_control.setImageResource(R.drawable.ic_play_arrow_black_48dp)
                false
            } else {
                ib_control.setImageResource(R.drawable.ic_stop_black_48dp)
                true
            }
        }

        requestPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (i in 0 until grantResults.size) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    //判断是否勾选禁止后不再询问
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        Toast.makeText(this, R.string.no_permission_to_open_camera, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, R.string.permission_of_camera_has_been_stopped, Toast.LENGTH_SHORT).show()
                    }
                    return
                }
            }
            initCamera()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        for (i in 0 until PERMISSIONS_LIST.size) {
            if (ContextCompat.checkSelfPermission(this, PERMISSIONS_LIST[i]) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(PERMISSIONS_LIST[i])
            }
        }
        if (permissions.isEmpty()) {//未授予的权限为空，表示都授予了
            initCamera()
        } else {//请求权限方法
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSION_CODE)
        }
    }

    private fun initCamera() {
        runOnUiThread {
            cv_show.openCamera(mWidth, mHeight, windowManager.defaultDisplay.rotation, "192.168.2.102", 18000)
            ib_control.isClickable = true
        }
    }
}
