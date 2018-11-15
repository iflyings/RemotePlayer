package com.android.iflyings.screenapp

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.os.Build
import android.util.Log
import android.widget.RemoteViews

class ScreenRecordService: Service() {

    private lateinit var mNotificationManager: NotificationManager
    private var mScreenRecordManager: ScreenRecordManager? = null

    override fun onCreate() {
        super.onCreate()
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        Log.i("zw","ScreenRecordService::onCreate")
    }

    override fun onDestroy() {
        mNotificationManager.cancelAll()
        mScreenRecordManager = mScreenRecordManager?.run {
            stop()
            null
        }
        super.onDestroy()
        Log.i("zw","ScreenRecordService::onDestroy")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val resultCode = intent.getIntExtra("code", -1)
        val resultData = intent.getParcelableExtra("data") as Intent
        val screenWidth = intent.getIntExtra("width", 720)
        val screenHeight = intent.getIntExtra("height", 1280)
        val isAudio = intent.getBooleanExtra("audio", true)
        val ip = intent.getStringExtra("ip")

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
        mScreenRecordManager = ScreenRecordManager(mediaProjection).apply {
            setDisplaySize(screenWidth, screenHeight)
            setAudio(isAudio)
            setIpAddress(ip)
            prepare()
            start()
        }

        startForeground(1, createNotification(ip))

        return super.onStartCommand(intent, START_FLAG_RETRY, startId)
    }

    private fun createNotification(ip: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel("screen-record", "1", NotificationManager.IMPORTANCE_DEFAULT)
            mNotificationManager.createNotificationChannel(notificationChannel)
            NotificationCompat.Builder(this, "screen-record")
        } else {
            NotificationCompat.Builder(this)
        }.apply {
            priority = NotificationCompat.PRIORITY_HIGH
            setOngoing(true)
            setSmallIcon(R.mipmap.ic_launcher_round)
            setWhen(System.currentTimeMillis())
        }
        val remoteViews = RemoteViews(packageName, R.layout.notification_screen_record).apply {
            setImageViewResource(R.id.iv_show, R.mipmap.ic_launcher_round)
            setTextViewText(R.id.tv_title, getString(R.string.app_name))
            setTextViewText(R.id.tv_content, ip)
        }
        val remoteIntent = Intent(this@ScreenRecordService, ScreenRecordActivity::class.java).apply {
            putExtra("exit", true)
        }
        val remotePendingIntent = PendingIntent.getActivity(this, 0, remoteIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        remoteViews.setOnClickPendingIntent(R.id.bt_exit, remotePendingIntent)
        builder.setContent(remoteViews)
        val notification = builder.build()
        mNotificationManager.notify(NotificationId, notification)
        return notification
    }

    companion object {
        private const val NotificationId = 1

        fun startService(context: Context, resultCode: Int, resultData: Intent,
                         screenWidth: Int, screenHeight: Int, isAudio: Boolean, ip: String) {
            val intent = Intent(context, ScreenRecordService::class.java).apply {
                putExtra("code", resultCode)
                putExtra("data", resultData)
                putExtra("audio", isAudio)
                putExtra("width", screenWidth)
                putExtra("height", screenHeight)
                putExtra("ip", ip)
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ScreenRecordService::class.java)
            context.stopService(intent)
        }
    }
}
