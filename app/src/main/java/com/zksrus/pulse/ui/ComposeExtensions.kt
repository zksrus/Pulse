package com.zksrus.pulse.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * Convenience wrapper around [collectAsStateWithLifecycle]. Centralized here so screens can
 * collect any [StateFlow] without repeating the import and initial-value dance.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateLifecycle(): State<T> = collectAsStateWithLifecycle()
