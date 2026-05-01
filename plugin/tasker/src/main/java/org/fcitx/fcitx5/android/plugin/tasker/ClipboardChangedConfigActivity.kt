/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.plugin.tasker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput

/**
 * Tasker opens this activity when the user picks "Fcitx5 Clipboard Changed" in the
 * event picker. We have nothing to configure, so we immediately call
 * `finishForTasker()` (via the helper) which writes the runner/input metadata back
 * into the result intent and finishes. Declared with `Theme.NoDisplay` in the manifest.
 */
class ClipboardChangedConfigActivity : Activity(), TaskerPluginConfig<ClipboardEventInput> {

    override val context: Context get() = applicationContext

    override fun assignFromInput(input: TaskerInput<ClipboardEventInput>) = Unit

    override val inputForTasker get() = TaskerInput(ClipboardEventInput())

    override fun getIntent(): Intent? = super.getIntent()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ClipboardEventHelper(this).finishForTasker()
    }
}
