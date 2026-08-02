/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import org.fcitx.fcitx5.android.common.ipc.IClipboardEntryTransformer
import org.fcitx.fcitx5.android.common.ipc.IFcitxRemoteService
import org.fcitx.fcitx5.android.core.data.DataManager
import org.fcitx.fcitx5.android.core.reloadPinyinDict
import org.fcitx.fcitx5.android.core.reloadQuickPhrase
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.clipboard.ClipboardManager
import org.fcitx.fcitx5.android.utils.Const
import timber.log.Timber

class FcitxRemoteService : Service() {

    private data class RegisteredClipboardTransformer(
        val description: String,
        val priority: Int,
        val transformer: IClipboardEntryTransformer,
        val binder: IBinder,
        val deathRecipient: IBinder.DeathRecipient,
    )

    private val clipboardTransformerLock = Any()
    private val clipboardTransformersByDescription =
        mutableMapOf<String, RegisteredClipboardTransformer>()

    @Volatile
    private var clipboardTransformers = emptyList<RegisteredClipboardTransformer>()

    private fun transformClipboard(source: String): String {
        var result = source
        clipboardTransformers.forEach {
            try {
                result = it.transformer.transform(result)!!
            } catch (e: Exception) {
                Timber.w("Exception while calling clipboard transformer '${it.description}'")
                Timber.w(e)
            }
        }
        return result
    }

    private fun updateClipboardManagerLocked() {
        clipboardTransformers = clipboardTransformersByDescription.values
            .sortedByDescending { it.priority }
        ClipboardManager.transformer =
            if (clipboardTransformers.isEmpty()) null else ::transformClipboard
        Timber.d(
            "All clipboard transformers: ${
                clipboardTransformers.joinToString { it.description }
            }"
        )
    }

    private val binder = object : IFcitxRemoteService.Stub() {
        override fun getVersionName(): String = Const.versionName

        override fun getPid(): Int = Process.myPid()

        override fun getLoadedPlugins(): MutableMap<String, String> =
            DataManager.getLoadedPlugins().map {
                it.packageName to it.versionName
            }.let { mutableMapOf<String, String>().apply { putAll(it) } }

        override fun restartFcitx() {
            FcitxDaemon.restartFcitx()
        }

        override fun registerClipboardEntryTransformer(transformer: IClipboardEntryTransformer) {
            val description = runCatching { transformer.description }.getOrNull()
            if (description.isNullOrBlank()) {
                Timber.w("Cannot register ClipboardEntryTransformer of null or empty description")
                return
            }

            val priority = runCatching { transformer.priority }.getOrElse {
                Timber.w(it, "Cannot read priority of ClipboardEntryTransformer '$description'")
                return
            }

            Timber.d("registerClipboardEntryTransformer: $description")
            val transformerBinder = transformer.asBinder()
            val deathRecipient = IBinder.DeathRecipient {
                synchronized(clipboardTransformerLock) {
                    val registered = clipboardTransformersByDescription[description]
                    if (registered?.binder != transformerBinder) return@synchronized
                    clipboardTransformersByDescription.remove(description)
                    updateClipboardManagerLocked()
                }
            }

            val previous = synchronized(clipboardTransformerLock) {
                val registered = clipboardTransformersByDescription[description]
                if (registered?.binder == transformerBinder) return

                runCatching {
                    transformerBinder.linkToDeath(deathRecipient, 0)
                }.onFailure {
                    Timber.w(it, "Cannot monitor ClipboardEntryTransformer '$description'")
                }.getOrElse {
                    return
                }

                clipboardTransformersByDescription.put(
                    description,
                    RegisteredClipboardTransformer(
                        description,
                        priority,
                        transformer,
                        transformerBinder,
                        deathRecipient,
                    )
                ).also {
                    updateClipboardManagerLocked()
                }
            }

            previous?.let {
                runCatching { it.binder.unlinkToDeath(it.deathRecipient, 0) }
            }
        }

        override fun unregisterClipboardEntryTransformer(transformer: IClipboardEntryTransformer) {
            val transformerBinder = transformer.asBinder()
            val removed = synchronized(clipboardTransformerLock) {
                val entry = clipboardTransformersByDescription.entries
                    .firstOrNull { it.value.binder == transformerBinder }
                    ?: return@synchronized null
                clipboardTransformersByDescription.remove(entry.key).also {
                    updateClipboardManagerLocked()
                }
            } ?: return

            Timber.d("unregisterClipboardEntryTransformer: ${removed.description}")
            runCatching {
                removed.binder.unlinkToDeath(removed.deathRecipient, 0)
            }
        }

        override fun reloadPinyinDict() {
            FcitxDaemon.getFirstConnectionOrNull()?.runIfReady { reloadPinyinDict() }
        }

        override fun reloadQuickPhrase() {
            FcitxDaemon.getFirstConnectionOrNull()?.runIfReady { reloadQuickPhrase() }
        }
    }

    override fun onCreate() {
        Timber.d("FcitxRemoteService onCreate")
        super.onCreate()
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.d("FcitxRemoteService onBind: $intent")
        return binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Timber.d("FcitxRemoteService onUnbind: $intent")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Timber.d("FcitxRemoteService onDestroy")
        val registered = synchronized(clipboardTransformerLock) {
            clipboardTransformersByDescription.values.toList().also {
                clipboardTransformersByDescription.clear()
                updateClipboardManagerLocked()
            }
        }
        registered.forEach {
            runCatching { it.binder.unlinkToDeath(it.deathRecipient, 0) }
        }
        super.onDestroy()
    }
}
