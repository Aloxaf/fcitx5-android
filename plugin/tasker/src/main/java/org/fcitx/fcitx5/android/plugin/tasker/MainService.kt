/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.plugin.tasker

import android.util.Log
import org.fcitx.fcitx5.android.common.FcitxPluginService
import org.fcitx.fcitx5.android.common.ipc.FcitxRemoteConnection
import org.fcitx.fcitx5.android.common.ipc.IClipboardEntryTransformer
import org.fcitx.fcitx5.android.common.ipc.bindFcitxRemoteService

/**
 * Bridges Fcitx5's clipboard observation hook to Tasker's event system.
 *
 * Fcitx5 has no first-class "clipboard observer" API for plugins, but it does have
 * `IClipboardEntryTransformer`, which is invoked synchronously for every new clipboard
 * entry. We register a high-priority transformer that returns the text unchanged and
 * uses the call as a notification to fire a Tasker event with the clipboard payload.
 */
class MainService : FcitxPluginService() {

    private lateinit var connection: FcitxRemoteConnection

    private val transformer = object : IClipboardEntryTransformer.Stub() {
        // `description` doubles as the registration key on the fcitx side
        // (see FcitxRemoteService.registerClipboardEntryTransformer).
        override fun getDescription(): String = TRANSFORMER_DESCRIPTION

        // Run before any other transformer (e.g. ClearURLs at priority 100) so we
        // forward the unmodified clipboard text to Tasker.
        override fun getPriority(): Int = Int.MAX_VALUE

        override fun transform(clipboardText: String): String {
            try {
                applicationContext.dispatchTaskerEvent(
                    ClipboardChangedConfigActivity::class.java,
                    ClipboardEventUpdate(clipboardText, System.currentTimeMillis())
                )
            } catch (t: Throwable) {
                // Never fail the transformer chain because of Tasker dispatch:
                // an unhandled exception here would corrupt fcitx's clipboard pipeline.
                Log.w(TAG, "Failed to dispatch clipboard event to Tasker", t)
            }
            return clipboardText
        }
    }

    override fun start() {
        connection = bindFcitxRemoteService(BuildConfig.MAIN_APPLICATION_ID) {
            Log.d(TAG, "Bound to fcitx remote, registering clipboard transformer")
            it.registerClipboardEntryTransformer(transformer)
        }
    }

    override fun stop() {
        runCatching {
            connection.remoteService?.unregisterClipboardEntryTransformer(transformer)
        }
        unbindService(connection)
        Log.d(TAG, "Unbound from fcitx remote")
    }

    companion object {
        private const val TAG = "TaskerBridgeService"

        // Must be globally unique among installed clipboard transformers; fcitx5
        // dedupes by description string.
        private const val TRANSFORMER_DESCRIPTION = "Tasker Bridge"
    }
}
