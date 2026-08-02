/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.common.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException

/**
 * Binds to the main application's remote service and keeps the connection recoverable.
 */
fun Context.bindFcitxRemoteService(
    mainApplicationId: String,
    onDisconnect: () -> Unit = {},
    onConnected: (IFcitxRemoteService) -> Unit
): FcitxRemoteConnection {
    return FcitxRemoteConnection(
        applicationContext,
        Intent("$mainApplicationId.IPC").apply {
            setPackage(mainApplicationId)
        },
        onConnected,
        onDisconnect
    ).also { it.bind() }
}

/**
 * Maintains a binding to the main application's remote service until [close] is called.
 */
class FcitxRemoteConnection internal constructor(
    private val context: Context,
    private val intent: Intent,
    private val onConnected: (IFcitxRemoteService) -> Unit,
    private val onDisconnected: () -> Unit
) : ServiceConnection {
    private val handler = Handler(Looper.getMainLooper())
    private val rebind = Runnable { bind() }

    private var binding = false
    private var closed = false

    var remoteService: IFcitxRemoteService? = null
        private set

    internal fun bind() {
        if (binding || closed) return
        binding = context.bindService(intent, this, Context.BIND_AUTO_CREATE)
        if (!binding) {
            onDisconnected()
            handler.postDelayed(rebind, REBIND_DELAY_MS)
        }
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        val connectedService = IFcitxRemoteService.Stub.asInterface(service)
        remoteService = connectedService
        try {
            onConnected(connectedService)
        } catch (_: RemoteException) {
            remoteService = null
            onDisconnected()
            releaseBinding()
            handler.postDelayed(rebind, REBIND_DELAY_MS)
        }
    }

    override fun onServiceDisconnected(name: ComponentName) {
        remoteService = null
        onDisconnected()
    }

    override fun onBindingDied(name: ComponentName) {
        remoteService = null
        onDisconnected()
        releaseBinding()
        bind()
    }

    override fun onNullBinding(name: ComponentName) {
        remoteService = null
        onDisconnected()
        releaseBinding()
    }

    /**
     * Permanently releases the service binding and cancels pending rebind attempts.
     */
    fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacks(rebind)
        releaseBinding()
        remoteService = null
    }

    private fun releaseBinding() {
        if (!binding) return
        context.unbindService(this)
        binding = false
    }

    private companion object {
        const val REBIND_DELAY_MS = 1_000L
    }
}
