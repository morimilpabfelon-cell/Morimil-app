package com.morimil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.morimil.app.reasoning.HybridAuthorityPresentation
import com.morimil.app.reasoning.HybridAuthorityPresentationStatus
import com.morimil.app.reasoning.HybridAuthorityPresentationStore

@Composable
fun ChatScreenWithAuthorityStatus(viewModel: ChatViewModel) {
    val presentation by HybridAuthorityPresentationStore.lastPresentation.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(morimilBackgroundBrush())
    ) {
        HybridAuthorityStatusSurface(
            presentation = presentation,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        Box(modifier = Modifier.weight(1f)) {
            ChatScreenPolished(viewModel)
        }
    }
}

@Composable
internal fun HybridAuthorityStatusSurface(
    presentation: HybridAuthorityPresentation,
    modifier: Modifier = Modifier
) {
    val containerColor = when (presentation.status) {
        HybridAuthorityPresentationStatus.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
        HybridAuthorityPresentationStatus.ACCEPTED_DETERMINISTIC,
        HybridAuthorityPresentationStatus.ACCEPTED_STRICT_CONSENSUS -> MaterialTheme.colorScheme.secondaryContainer
        HybridAuthorityPresentationStatus.ABSTAINED -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (presentation.status) {
        HybridAuthorityPresentationStatus.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
        HybridAuthorityPresentationStatus.ACCEPTED_DETERMINISTIC,
        HybridAuthorityPresentationStatus.ACCEPTED_STRICT_CONSENSUS -> MaterialTheme.colorScheme.onSecondaryContainer
        HybridAuthorityPresentationStatus.ABSTAINED -> MaterialTheme.colorScheme.onErrorContainer
    }
    val description = "${presentation.headline}. Ruta: ${presentation.routeLabel}. ${presentation.explanation}"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = presentation.headline,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor
            )
            Text(
                text = "RUTA: ${presentation.routeLabel.uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = contentColor
            )
            Text(
                text = presentation.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor
            )
        }
    }
}
