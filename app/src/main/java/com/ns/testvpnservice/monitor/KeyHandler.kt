package com.ns.testvpnservice.monitor

import java.nio.channels.SelectionKey

interface KeyHandler {
    fun onKeyReady(key: SelectionKey)
}