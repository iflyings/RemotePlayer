package com.android.iflyings.recvapp

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.SurfaceHolder
import kotlinx.android.synthetic.main.activity_main.*

class MediaRecvActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var mMediaRecvManager: MediaRecvManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sc_show.holder.setKeepScreenOn(true)
        sc_show.holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        mMediaRecvManager = MediaRecvManager()
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mMediaRecvManager.setOutputSurface(holder.surface)
        mMediaRecvManager.prepare()
        mMediaRecvManager.start()
    }
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mMediaRecvManager.stop()
    }

}
