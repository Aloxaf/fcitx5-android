/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.plugin.tasker

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.joaomgcd.taskerpluginlibrary.TaskerPluginConstants
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputInfos
import net.dinglisch.android.tasker.TaskerPlugin

/**
 * Builds and broadcasts a Tasker `REQUEST_QUERY` event from a context that may be
 * running in the background.
 *
 * `TaskerPluginRunnerCondition.requestQuery` (the helper bundled with
 * `taskerpluginlibrary`) first attempts `startService(...)` and, on failure, falls
 * back to `context.sendBroadcast(intent)` with neither a target package nor a
 * component. From a background process on Android 8+ both paths fail silently:
 *   * background `startService` throws `IllegalStateException` (caught and ignored
 *     by the library);
 *   * implicit broadcasts are no longer delivered to manifest-declared receivers
 *     in other apps.
 *
 * Our plugin runs as a bound service inside a normally-backgrounded process, so we
 * have to dispatch the broadcast explicitly. We resolve every receiver registered
 * for the action and target each one by component, which Android always allows
 * regardless of background state.
 *
 * The intent payload (extras, pass-through bundle, message ID) is constructed via
 * the same public helpers `TaskerPluginRunnerCondition` uses, so Tasker sees an
 * identical intent.
 */
internal fun <T : Activity> Context.dispatchTaskerEvent(
    configActivity: Class<T>,
    update: Any,
) {
    val intent = Intent(TaskerPluginConstants.ACTION_REQUEST_QUERY).apply {
        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        putExtra(TaskerPluginConstants.EXTRA_ACTIVITY, configActivity.name)
        TaskerPlugin.Event.addPassThroughMessageID(this)
        TaskerPlugin.Event.addPassThroughData(this, update.toUpdateBundle(this@dispatchTaskerEvent))
    }

    val receivers = packageManager.queryBroadcastReceivers(
        Intent(TaskerPluginConstants.ACTION_REQUEST_QUERY),
        0,
    )
    if (receivers.isEmpty()) {
        // No package visible to us declares the receiver. Best-effort fallback:
        // explicitly target stock Tasker by package name. Still a valid send because
        // the package is whitelisted in our <queries>.
        intent.setPackage(STOCK_TASKER_PACKAGE)
        sendBroadcast(intent)
        return
    }
    receivers.forEach { resolve ->
        val info = resolve.activityInfo
        intent.component = ComponentName(info.packageName, info.name)
        sendBroadcast(intent)
    }
}

/** Mirrors the private `getUpdateBundle` in `TaskerPluginRunnerCondition`. */
private fun Any.toUpdateBundle(context: Context) =
    TaskerInputInfos.fromInput(context, this).bundle.apply {
        putString(TaskerPluginConstants.EXTRA_CONDITION_UPDATE_CLASS, this@toUpdateBundle::class.java.name)
    }

private const val STOCK_TASKER_PACKAGE = "net.dinglisch.android.taskerm"
