package com.ns.testvpnservice.monitor


import android.util.Log
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

class TCPTunnel(val localSocket: Socket, val bindLocalSession: SessionManager.Session, val attachSelector: Selector) : KeyHandler {
    companion object {
        private const val TAG = "TCPTunnel"
    }
    private val remoteSocketChannel: SocketChannel
    private val remoteAddress: SocketAddress
    init {
        remoteAddress = InetSocketAddress(localSocket.inetAddress, bindLocalSession.remotePort)
        remoteSocketChannel = SocketChannel.open()
        remoteSocketChannel.configureBlocking(false)
    }
    fun start() {
        remoteSocketChannel.register(attachSelector, SelectionKey.OP_CONNECT, this)
        remoteSocketChannel.connect(remoteAddress)
        Log.d(TAG, "conv session:$bindLocalSession create remote connect")
    }

    fun getRemoteSocket(): Socket {
        return remoteSocketChannel.socket()
    }

    override fun onKeyReady(key: SelectionKey) {
        Log.d(TAG, "onKeyReady:$key")
    }
}