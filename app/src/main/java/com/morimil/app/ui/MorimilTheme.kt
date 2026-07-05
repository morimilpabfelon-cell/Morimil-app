package com.morimil.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.morimil.app.core.memory.MemoryBacklink
import com.morimil.app.core.memory.MemoryBacklinkGraphBuilder
import com.morimil.app.data.genesis.CurrentMobileAppCapabilities
import com.morimil.app.data.genesis.GenesisIdentitySource
import com.morimil.app.data.local.MemoryEventEntity
import com.morimil.app.data.local.MemoryLinkEntity
import com.morimil.app.data.local.MigrationRecordEntity
import com.morimil.app.data.local.RecallScheduleEntity
import com.morimil.app.runtime.RestCycleScheduleStatus
import java.util.Locale

private val MorimilLightColors = lightColorScheme(
    primary = Color(0xFF245C37),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EACF),
    onPrimaryContainer = Color(0xFF06210F),
    secondary = Color(0xFF6B5B1E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E2A6),
    onSecondaryContainer = Color(0xFF211A02),
    tertiary = Color(0xFFB7791F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDAA),
    onTertiaryContainer = Color(0xFF2A1700),
    background = Color(0xFFFAF7EF),
    onBackground = Color(0xFF1C1B17),
    surface = Color(0xFFFFFBF4),
    onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE0E4D7),
    onSurfaceVariant = Color(0xFF44483F),
    error = Color(0xFFBA1A1A)
)

private val MorimilDarkColors = darkColorScheme(
    primary = Color(0xFF9DD7A6),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF0D4A25),
    onPrimaryContainer = Color(0xFFC8EACF),
    secondary = Color(0xFFD8C77C),
    onSecondary = Color(0xFF393000),
    secondaryContainer = Color(0xFF514700),
    onSecondaryContainer = Color(0xFFF5E6A4),
    tertiary = Color(0xFFFFC36F),
    onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF624000),
    onTertiaryContainer = Color(0xFFFFDDAA),
    background = Color(0xFF11140F),
    onBackground = Color(0xFFE3E3D8),
    surface = Color(0xFF171A14),
    onSurface = Color(0xFFE3E3D8),
    surfaceVariant = Color(0xFF3F453C),
    onSurfaceVariant = Color(0xFFC5C9BD),
    error = Color(0xFFFFB4AB)
)

@Composable
fun MorimilTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MorimilDarkColors
        else -> MorimilLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
