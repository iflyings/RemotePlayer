package com.android.iflyings.screenapp

import android.Manifest
import android.os.Bundle
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.view.WindowManager
import android.widget.Toast

class ScreenRecordActivity : Activity() {
    companion object {
        private var PERMISSIONS_LIST = arrayOf(Manifest.permission.RECORD_AUDIO)
        private const val REQUEST_PERMISSION_CODE = 683
        private const val REQUEST_CODE_SCREEN_CAPTURE = 934
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_screen)

        if (intent.getBooleanExtra("exit", false)) {
            ScreenRecordService.stopService(this)
            finish()
        }

        requestPermissions()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        for (i in 0 until PERMISSIONS_LIST.size) {
            if (ContextCompat.checkSelfPermission(this, PERMISSIONS_LIST[i]) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(PERMISSIONS_LIST[i])
            }
        }
        if (permissions.isEmpty()) {//未授予的权限为空，表示都授予了
            startScreenCapture()
        } else {//请求权限方法
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSION_CODE) {
            for (i in 0 until grantResults.size) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    //判断是否勾选禁止后不再询问
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        //Toast.makeText(this, R.string.no_permission_to_open_camera, Toast.LENGTH_SHORT).show()
                    } else {
                        //Toast.makeText(this, R.string.permission_of_camera_has_been_stopped, Toast.LENGTH_SHORT).show()
                    }
                    return
                }
            }
            startScreenCapture()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }

    private fun startScreenCapture() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        startActivityForResult(intent, REQUEST_CODE_SCREEN_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent) {
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "User cancelled", Toast.LENGTH_SHORT).show()
                return
            }

            ScreenRecordService.startService(this, resultCode, resultData,
                    800, 600, false, "192.168.1.23")
            finish()
        }
    }
}
