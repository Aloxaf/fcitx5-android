/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.plugin.tasker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput

/**
 * Lets the user manage the per-event regex filter (see [ClipboardEventInput]).
 *
 * Tasker opens this activity when configuring the "Fcitx5 Clipboard Changed" event.
 * Each pattern lives in its own editable row so the user never has to delimit patterns
 * with newlines; an empty configuration means "fire on every clipboard change".
 *
 * Save/restore follows the standard taskerpluginlibrary contract: [assignFromInput]
 * restores the saved patterns into rows, and pressing Back collects the rows back into
 * [inputForTasker] via the helper.
 *
 * Saving is wired to back rather than the sample's `onKeyDown(KEYCODE_BACK)` because at
 * this targetSdk the back gesture may be delivered through either dispatch path. We cover
 * both: an [OnBackInvokedCallback] (API 33+, the predictive-back path) and [onBackPressed]
 * (the legacy path). The two are mutually exclusive at runtime, so exactly one fires per
 * back and the edited rows are always written back via [ClipboardEventHelper.onBackPressed].
 */
class ClipboardChangedConfigActivity : Activity(), TaskerPluginConfig<ClipboardEventInput> {

    override val context: Context get() = applicationContext

    private val taskerHelper by lazy { ClipboardEventHelper(this) }

    // Holds one [patternRow] per regex; the single source of truth for the edited patterns.
    private lateinit var patternContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContentView())
        // Predictive-back path: when this callback is registered the framework routes back
        // here and skips onBackPressed(). Registering is a no-op on devices/apps where the
        // OnBackInvokedCallback system is inactive, which then use onBackPressed() below.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { saveAndFinish() }
        }
        taskerHelper.onCreate()
    }

    // Legacy back path; only reached when the predictive-back callback above is inactive.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        saveAndFinish()
    }

    /**
     * Writes the edited patterns back to Tasker and finishes on success. On a validation
     * failure the helper returns without finishing, leaving the user on the screen to fix it.
     */
    private fun saveAndFinish() {
        taskerHelper.onBackPressed()
    }

    override fun assignFromInput(input: TaskerInput<ClipboardEventInput>) {
        patternContainer.removeAllViews()
        val patterns = input.regular.patterns?.filter { it.isNotBlank() }
        if (patterns.isNullOrEmpty()) {
            addPatternRow(null)
        } else {
            patterns.forEach { addPatternRow(it) }
        }
    }

    override val inputForTasker: TaskerInput<ClipboardEventInput>
        get() {
            val patterns = patternRows()
                .mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }
                .toTypedArray()
            return TaskerInput(ClipboardEventInput(patterns.takeIf { it.isNotEmpty() }))
        }

    private fun buildContentView(): View {
        val padding = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            // targetSdk 35+ forces edge-to-edge, so content would otherwise draw behind
            // the status/title bar (top) and navigation bar (bottom). Inset the system
            // bars on top of the base content margin. systemWindowInset* is deprecated but
            // is the only variant usable down to minSdk 23.
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION")
                v.setPadding(
                    padding + insets.systemWindowInsetLeft,
                    padding + insets.systemWindowInsetTop,
                    padding + insets.systemWindowInsetRight,
                    padding + insets.systemWindowInsetBottom,
                )
                insets
            }
        }

        root.addView(TextView(this).apply {
            setText(R.string.tasker_filter_hint)
            setTextAppearance(android.R.style.TextAppearance_Small)
        })

        patternContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            addView(patternContainer)
        }
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
        ).apply { weight = 1f; topMargin = dp(8) })

        root.addView(Button(this).apply {
            setText(R.string.tasker_add_pattern)
            setOnClickListener { addPatternRow(null) }
        })

        return root
    }

    /** Appends a row with an [EditText] (prefilled with [pattern]) plus a remove button. */
    private fun addPatternRow(pattern: String?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val edit = EditText(this).apply {
            // Single-line so the value maps cleanly to one regex; no newline delimiting.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
            setHint(R.string.tasker_pattern_hint)
            setText(pattern)
        }
        row.addView(edit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            setText(R.string.tasker_remove_pattern)
            setOnClickListener { patternContainer.removeView(row) }
        })
        patternContainer.addView(row)
    }

    /** The [EditText] of every pattern row, in display order. */
    private fun patternRows(): List<EditText> =
        (0 until patternContainer.childCount).mapNotNull { i ->
            (patternContainer.getChildAt(i) as? ViewGroup)?.getChildAt(0) as? EditText
        }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    override fun getIntent(): Intent? = super.getIntent()
}
