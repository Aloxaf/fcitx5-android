/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.plugin.tasker

import android.content.Context
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionEvent
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelper
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputField
import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionUnsatisfied

/**
 * No user-configurable filter for now; the event always fires on every clipboard change.
 * Defined as an empty class so we can later add filter fields without breaking saved tasks.
 */
@TaskerInputRoot
class ClipboardEventInput

/**
 * Carries the per-event payload. Used both as the `update` sent through Tasker's
 * pass-through bundle AND as the output object exposed as Tasker variables.
 *
 * Fields use `var` and a no-arg constructor because the library reflects on them.
 */
@TaskerInputRoot
@TaskerOutputObject
class ClipboardEventUpdate @JvmOverloads constructor(
    @field:TaskerInputField("text")
    @get:TaskerOutputVariable(
        name = "text",
        labelResIdName = "tasker_text_label",
        htmlLabelResIdName = "tasker_text_html_label"
    )
    var text: String? = null,

    @field:TaskerInputField("timestamp")
    @get:TaskerOutputVariable(
        name = "timestamp",
        labelResIdName = "tasker_timestamp_label"
    )
    var timestamp: Long? = null,
)

class ClipboardEventRunner :
    TaskerPluginRunnerConditionEvent<ClipboardEventInput, ClipboardEventUpdate, ClipboardEventUpdate>() {

    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<ClipboardEventInput>,
        update: ClipboardEventUpdate?,
    ): TaskerPluginResultCondition<ClipboardEventUpdate> =
        update?.let { TaskerPluginResultConditionSatisfied(context, it) }
            ?: TaskerPluginResultConditionUnsatisfied()
}

class ClipboardEventHelper(config: TaskerPluginConfig<ClipboardEventInput>) :
    TaskerPluginConfigHelper<ClipboardEventInput, ClipboardEventUpdate, ClipboardEventRunner>(config) {
    override val runnerClass = ClipboardEventRunner::class.java
    override val inputClass = ClipboardEventInput::class.java
    override val outputClass = ClipboardEventUpdate::class.java

    override fun addToStringBlurb(
        input: TaskerInput<ClipboardEventInput>,
        blurbBuilder: StringBuilder,
    ) {
        blurbBuilder.append(context.getString(R.string.tasker_blurb))
    }
}
