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
 * Per-event filter configuration edited in [ClipboardChangedConfigActivity].
 *
 * [patterns] holds the user's regular expressions. The event fires when the clipboard
 * text partially matches any one of them; an empty/null list means "no filter", so
 * every clipboard change fires. Stored as `Array<String>` because that is one of the
 * types Tasker can persist in an input bundle (see `getForTaskerCompatibleInputTypes`).
 */
@TaskerInputRoot
class ClipboardEventInput @JvmOverloads constructor(
    // ignoreInStringBlurb: the library's default blurb would render the array via
    // Object.toString() ("[Ljava.lang.String;@..."); we format it ourselves in
    // ClipboardEventHelper.addToStringBlurb instead.
    @field:TaskerInputField("patterns", labelResIdName = "tasker_patterns_label", ignoreInStringBlurb = true)
    var patterns: Array<String>? = null,
)

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
    ): TaskerPluginResultCondition<ClipboardEventUpdate> {
        update ?: return TaskerPluginResultConditionUnsatisfied()
        if (clipboardTextMatches(update.text, input.regular.patterns)) {
            return TaskerPluginResultConditionSatisfied(context, update)
        }
        return TaskerPluginResultConditionUnsatisfied()
    }
}

/**
 * Decides whether [text] passes the configured regex filter.
 *
 * An empty (or null) [patterns] list means "no filter" and passes everything. Otherwise
 * the text passes when it partially matches any pattern (`containsMatchIn`, no multiline
 * or dot-all flags). A malformed pattern matches nothing instead of throwing, so one bad
 * entry can never break clipboard dispatch.
 */
private fun clipboardTextMatches(text: String?, patterns: Array<String>?): Boolean {
    if (patterns.isNullOrEmpty()) return true
    if (text == null) return false
    return patterns.any { pattern ->
        runCatching { Regex(pattern).containsMatchIn(text) }.getOrDefault(false)
    }
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
        val patterns = input.regular.patterns?.filter { it.isNotBlank() }
        if (patterns.isNullOrEmpty()) {
            blurbBuilder.append(context.getString(R.string.tasker_blurb))
        } else {
            blurbBuilder.append(
                context.getString(R.string.tasker_blurb_filtered, patterns.joinToString("\n"))
            )
        }
    }
}
