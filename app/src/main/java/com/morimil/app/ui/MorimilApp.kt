package com.morimil.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MorimilApp(viewModel: MorimilViewModel = viewModel()) {
    MorimilTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val localIdentity by viewModel.localIdentity.collectAsStateWithLifecycle()
            if (localIdentity == null) {
                OnboardingScreen(viewModel)
            } else {
                MainTabsScaffold(viewModel)
            }
        }
    }
}
