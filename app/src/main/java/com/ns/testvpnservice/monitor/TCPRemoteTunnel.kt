package com.ns.testvpnservice.monitor


import android.util.Log
import com.ns.testvpnservice.service.MyVPNService
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue

class TCPRemoteTunnel(remoteInetAddress: InetAddress, val bindLocalSession: SessionManager.Session, val attachSelector: Selector) : KeyHandler {
    companion object {
        private const val TAG = "TCPRemoteTunnel"
    }
    private val remoteSocketChannel: SocketChannel
    private val remoteAddress: SocketAddress
    private lateinit var localTunnel: TCPLocalTunnel
    private var msgQueue = ConcurrentLinkedQueue<ByteBuffer>()
    init {
        remoteAddress = InetSocketAddress(remoteInetAddress, bindLocalSession.remotePort)
        remoteSocketChannel = SocketChannel.open()
        remoteSocketChannel.configureBlocking(false)
    }
    fun start() {
        remoteSocketChannel.register(attachSelector, SelectionKey.OP_CONNECT, this)
        remoteSocketChannel.connect(remoteAddress)
        Log.d(TAG, "conv session:$bindLocalSession create remote connect")
    }

    fun bindLocalTunnel(tunnel: TCPLocalTunnel) {
        localTunnel = tunnel
    }

    fun getRemoteSocket(): Socket {
        return remoteSocketChannel.socket()
    }

    override fun onKeyReady(key: SelectionKey) {
        if (key.isWritable) {
            Log.d(TAG, "onKeyReady:isWriteable")
            handleWriteable()
        } else if (key.isReadable) {
            Log.d(TAG, "onKeyReady:isReadable")
            handleOnReadable()
        } else if (key.isConnectable) {
            Log.i(TAG, "onKeyReady:isConnectable")
            handleOnConnectable()
        }
    }

    private fun handleOnReadable() {
        val buffer = ByteBuffer.allocate(MyVPNService.MTU_SIZE)
        val hasReadSize = this.remoteSocketChannel.read(buffer)
        if (hasReadSize > 0) {
            buffer.flip()
            localTunnel.sendToLocal(buffer)
        } else {
            handleClose()
        }
    }

    private fun handleClose() {
        remoteSocketChannel.close()
        localTunnel.handleRemoteTunnelHasClose()
    }

    private fun handleWriteable() {
        val buffer = msgQueue.poll() ?: return
        while (buffer.hasRemaining()) {
            this.remoteSocketChannel.write(buffer)
        }
        if (msgQueue.isEmpty()) {
            attachSelector.wakeup()
            remoteSocketChannel.register(attachSelector, SelectionKey.OP_READ, this)
        }
    }

    private fun handleOnConnectable() {
        if (remoteSocketChannel.finishConnect()) {
            onConnected()
            Log.i(TAG, "Connected to $remoteAddress")
        } else {
            Log.e(TAG, "Connect to $remoteAddress failed")
        }
    }

    private fun onConnected() {
        if (remoteSocketChannel.isBlocking) {
            remoteSocketChannel.configureBlocking(false)
        }
        attachSelector.wakeup()
        remoteSocketChannel.register(attachSelector, SelectionKey.OP_READ, this)
        localTunnel.onRemoteTunnelConnected()
    }

    fun sendToRemote(buffer: ByteBuffer) {
        msgQueue.offer(buffer)
        attachSelector.wakeup()
        remoteSocketChannel.register(attachSelector, SelectionKey.OP_WRITE or SelectionKey.OP_READ, this)
    }

    fun handleLocalTunnelHasClose() {
        this.remoteSocketChannel.close()
    }
}