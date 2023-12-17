package com.ns.testvpnservice.monitor

import java.net.InetAddress
import java.nio.channels.SocketChannel
import kotlin.concurrent.thread

object SessionManager {
    private val sessionStore: LinkedHashMap<Int, Session> = linkedMapOf()

    class Session(val localPort:Int, val remotePort:Int, val remoteAddress: InetAddress) {}

    fun createSession(localPort: Int, remotePort: Int, remoteAddress: InetAddress): Session {
        val sess = Session(localPort, remotePort, remoteAddress)

        sessionStore[localPort] = sess

        return sess
    }

    fun getSession(localPort: Int): Session? {
        return sessionStore[localPort]
    }
}