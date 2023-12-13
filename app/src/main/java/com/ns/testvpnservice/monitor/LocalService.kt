package com.ns.testvpnservice.monitor

import android.util.Log
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.ServerSocketChannel

class LocalService(port: Int) : Thread() {
    private val mSelector: Selector
    private val mServerSocketChannel: ServerSocketChannel
    val myPort: Int
    private val mServerThread: Thread
    companion object {
        private const val TAG = "LocalService"
    }

    init {
        mSelector = Selector.open()

        mServerSocketChannel = ServerSocketChannel.open()
        mServerSocketChannel.configureBlocking(false)
        mServerSocketChannel.socket().bind(InetSocketAddress(port))
        mServerSocketChannel.register(mSelector, SelectionKey.OP_ACCEPT)
        myPort = mServerSocketChannel.socket().localPort
        mServerThread = Thread(this, "TcpProxyServerThread")
    }

    override fun start() {
        mServerThread.start()
    }

    override fun run() {
        Log.d(TAG, "has run...")
        try {
            while (true) {
                val select: Int = mSelector.select()
                if (select == 0) {
                    sleep(5)
                    Log.d(TAG, "sleep...")
                    continue
                }
                val selectionKeys: Set<SelectionKey> = mSelector.selectedKeys()
                    ?: continue
                val keyIterator: MutableIterator<SelectionKey> = mSelector.selectedKeys().iterator()
                while (keyIterator.hasNext()) {
                    val key = keyIterator.next()
                    if (key.isValid) {
                        try {
                            if (key.isAcceptable) {
                                Log.d(TAG, "isAcceptable")
                                onAccepted(key)
                            } else {
                                val attachment = key.attachment()
//                                if (attachment is KeyHandler) {
//                                    (attachment as KeyHandler).onKeyReady(key)
//                                }
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace(System.err)
                        }
                    }
                    keyIterator.remove()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace(System.err)
            Log.e(TAG, "updServer catch an exception: %s", e)
        } finally {
            this.stop()
            Log.i(TAG,"udpServer thread exited.")
        }
    }

    fun onAccepted(key: SelectionKey?) {
        val localChannel = mServerSocketChannel.accept()
        Log.d(TAG, "onAccepted:$localChannel")

//        var localTunnel: TcpTunnel? = null
//        try {
//            val localChannel = mServerSocketChannel.accept()
//            localTunnel = TunnelFactory.wrap(localChannel, mSelector)
//            val portKey = localChannel.socket().port.toShort()
//            val destAddress: InetSocketAddress = getDestAddress(localChannel)
//            if (destAddress != null) {
//                val remoteTunnel: TcpTunnel =
//                    TunnelFactory.createTunnelByConfig(destAddress, mSelector, portKey)
//                //关联兄弟
//                remoteTunnel.setIsHttpsRequest(localTunnel.isHttpsRequest())
//                remoteTunnel.setBrotherTunnel(localTunnel)
//                localTunnel.setBrotherTunnel(remoteTunnel)
//                //开始连接
//                remoteTunnel.connect(destAddress)
//            }
//        } catch (ex: java.lang.Exception) {
//            if (AppDebug.IS_DEBUG) {
//                ex.printStackTrace(System.err)
//            }
//            DebugLog.e("TcpProxyServer onAccepted catch an exception: %s", ex)
//            if (localTunnel != null) {
//                localTunnel.dispose()
//            }
//        }
    }

}