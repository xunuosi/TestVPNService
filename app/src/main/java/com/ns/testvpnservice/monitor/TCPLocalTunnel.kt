package com.ns.testvpnservice.monitor

import android.util.Log
import com.ns.testvpnservice.service.MyVPNService
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentLinkedQueue

class TCPLocalTunnel(private val localChannel: SocketChannel, val bindLocalSession: SessionManager.Session, val attachSelector: Selector) : KeyHandler {
    private lateinit var remoteTunnel: TCPRemoteTunnel
    private var msgQueue = ConcurrentLinkedQueue<ByteBuffer>()
    companion object {
        private const val TAG = "TCPLocalTunnel"
    }

    fun bindRemoteTunnel(tunnel: TCPRemoteTunnel) {
        this.remoteTunnel = tunnel
    }

    override fun onKeyReady(key: SelectionKey) {
        if (key.isWritable) {
            Log.d(TAG, "onKeyReady:isWriteable")
            handleOnWriteable()
        } else if (key.isReadable) {
            Log.d(TAG, "onKeyReady:isReadable")
            handleOnReadable()
        }
    }

    private fun handleOnReadable() {
        val buffer = ByteBuffer.allocate(MyVPNService.MTU_SIZE)
        val hasReadSize = this.localChannel.read(buffer)
        if (hasReadSize > 0) {
            buffer.flip()
            remoteTunnel.sendToRemote(buffer)
        } else {
            handleClose()
        }
    }

    private fun handleClose() {
        this.localChannel.close()
        remoteTunnel.handleLocalTunnelHasClose()
    }

    private fun handleOnWriteable() {
        val buffer = msgQueue.poll() ?: return
        while (buffer.hasRemaining()) {
            this.localChannel.write(buffer)
        }
        if (msgQueue.isEmpty()) {
            attachSelector.wakeup()
            localChannel.register(attachSelector, SelectionKey.OP_READ, this)
        }
    }

    fun onRemoteTunnelConnected() {
        if (localChannel.isBlocking) {
            localChannel.configureBlocking(false)
        }
        attachSelector.wakeup()
        localChannel.register(attachSelector, SelectionKey.OP_READ, this)
    }

    fun sendToLocal(buffer: ByteBuffer) {
        msgQueue.offer(buffer)
        attachSelector.wakeup()
        localChannel.register(attachSelector, SelectionKey.OP_WRITE or SelectionKey.OP_READ, this)
    }

    fun handleRemoteTunnelHasClose() {
        this.localChannel.close()
    }
}