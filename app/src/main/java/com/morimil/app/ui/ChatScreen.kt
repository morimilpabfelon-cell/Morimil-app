package com.morimil.app.ui

import androidx.compose.runtime.Composable

/**
 * Compatibility entry point. The canonical chat surface is
 * [ChatScreenPolished], which enforces the intrinsic-trimotor boundary and
 * presents external provider output only as unverified advisory text.
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    ChatScreenPolished(viewModel)
}
