package com.ns.testvpnservice.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Handler
import android.os.Message
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.core.util.Pair
import com.ns.testvpnservice.MainActivity
import com.ns.testvpnservice.R
import com.ns.testvpnservice.monitor.LocalService
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread


class MyVPNService : VpnService(), Handler.Callback, LocalService.LocalServiceListener {
    companion object {
        private const val TAG = "MyVPNService"
        const val ACTION_CONNECT = "com.ns.testvpnservice.START"
        const val ACTION_DISCONNECT = "com.ns.testvpnservice.STOP"
        val mNextConnectionId = AtomicInteger(1)
        val PACKAGES = setOf("com.termux", "com.ns.testvpnservice")
//        val PACKAGES = setOf("com.termux")
        val MTU_SIZE = 1500
    }

    private class Connection<Thread, ParcelFileDescriptor>(
        first: Thread,
        second: ParcelFileDescriptor?
    ) : Pair<Thread, ParcelFileDescriptor?>(first, second) {}

    private val mConnectingThread: AtomicReference<Thread> = AtomicReference<Thread>()
    private val mConnection: AtomicReference<Connection<Thread, ParcelFileDescriptor>> =
        AtomicReference<Connection<Thread, ParcelFileDescriptor>>()

    private val mHandler: Handler = Handler(this)
    private lateinit var mConfigureIntent: PendingIntent

    override fun onCreate() {
        super.onCreate()

        mConfigureIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun handleMessage(msg: Message): Boolean {
        Toast.makeText(this, msg.what, Toast.LENGTH_SHORT).show();
        if (msg.what != R.string.disconnected) {
            updateForegroundNotification(msg.what);
        }
        return true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && ACTION_DISCONNECT == intent.action) {
            disconnect()
            return START_NOT_STICKY
        } else {
            connect()
            return START_STICKY
        }
    }

    private fun connect() {
        updateForegroundNotification(R.string.connecting)
        mHandler.sendEmptyMessage(R.string.connecting)

        val proxy = LocalService(39399, this)
        proxy.start()

        startConnection(
            MyVpnConnection(
                this,
                mNextConnectionId.getAndIncrement(),
                "localhost",
                3939,
                true,
                PACKAGES,
                proxy
            )
        )
    }

    private fun startConnection(connection: MyVpnConnection) {
        // Replace any existing connecting thread with the  new one.
        val thread = Thread(connection, "ToyVpnThread")
        setConnectingThread(thread)
        // Handler to mark as connected once onEstablish is called.
        connection.setConfigureIntent(mConfigureIntent)
        connection.setOnEstablishListener { tunInterface ->
            mHandler.sendEmptyMessage(R.string.connected)
            mConnectingThread.compareAndSet(thread, null)
            setConnection(Connection(thread, tunInterface))

//            thread(start = true) {
//                Thread.sleep(500)
//                val socket = SocketChannel.open()
//                socket.connect(InetSocketAddress(39399))
//                val buffer = ByteBuffer.allocate(5)
//                buffer.put("hello".toByteArray())
//                buffer.flip()
//                socket.write(buffer)
//            }
        }
        thread.start()
    }

    @SuppressLint("ForegroundServiceType")
    private fun updateForegroundNotification(msgRes: Int) {
        val CHANNEL_ID = "MyVPNService"
        val service: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        startForeground(
            1, Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(androidx.core.R.drawable.notify_panel_notification_icon_bg)
                .setContentText(getString(msgRes))
                .setContentIntent(mConfigureIntent)
                .build()
        )
    }

    override fun onDestroy() {
        disconnect()
    }

    private fun disconnect() {
        mHandler.sendEmptyMessage(R.string.disconnected)
        setConnectingThread(null)
        setConnection(null)
        stopForeground(true)
    }

    private fun setConnectingThread(thread: Thread?) {
        val oldThread: Thread? = mConnectingThread.getAndSet(thread)
        oldThread?.interrupt()
    }

    private fun setConnection(connection: Connection<Thread, ParcelFileDescriptor>?) {
        val oldConnection: Connection<Thread, ParcelFileDescriptor>? =
            mConnection.getAndSet(connection)
        if (oldConnection != null) {
            try {
                oldConnection.first.interrupt()
                oldConnection.second?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Closing VPN interface", e)
            }
        }
    }

    override fun onRemoteTunnelCreated(socket: Socket) {
        this.protect(socket)
    }
}