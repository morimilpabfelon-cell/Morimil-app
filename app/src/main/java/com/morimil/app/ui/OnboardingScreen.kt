package com.morimil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(viewModel: MorimilViewModel) {
    val genesisResult by viewModel.genesisResult.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var alias by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val genesisReady = genesisResult?.isSuccess == true
    val genesisError = genesisResult?.exceptionOrNull()?.message

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFF4F2EA),
                        Color(0xFFE9EEDF)
                    )
                )
            )
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth())

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "&",
                    style = MaterialTheme.typography.displayLarge,
                    color = Color(0xFF11140F),
                    textAlign = TextAlign.Center
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Nombra tu instancia",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color(0xFF1C1B17),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Elige como se llamara esta instancia local.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF44483F),
                        textAlign = TextAlign.Center
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = Color(0xFFFFFBF4),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    TextField(
                        value = alias,
                        onValueChange = { alias = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "Pon el nombre de tu instancia",
                                color = Color(0xFF8B8A82)
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(32.dp),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1C1B17),
                            unfocusedTextColor = Color(0xFF1C1B17),
                            disabledTextColor = Color(0xFF1C1B17),
                            cursorColor = Color(0xFF245C37),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedPlaceholderColor = Color(0xFF8B8A82),
                            unfocusedPlaceholderColor = Color(0xFF8B8A82),
                            disabledPlaceholderColor = Color(0xFF8B8A82)
                        )
                    )
                }

                Button(
                    enabled = genesisReady && alias.isNotBlank() && !working,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFBA7517),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0x33BA7517),
                        disabledContentColor = Color(0x88FFFFFF)
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        working = true
                        status = null
                        scope.launch {
                            viewModel.bornInstance(alias.trim())
                                .onSuccess {
                                    status = "Instancia creada."
                                }
                                .onFailure { error ->
                                    status = error.message.orEmpty()
                                    working = false
                                }
                        }
                    }
                ) {
                    Text(
                        text = when {
                            working -> "Creando instancia..."
                            genesisReady -> "Crear instancia"
                            else -> "Preparando semilla local"
                        },
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                if (working) {
                    CircularProgressIndicator(color = Color(0xFFBA7517))
                }

                genesisError?.let { error ->
                    Text(
                        text = "Genesis no pudo cargarse: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                status?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF44483F),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Text(
                text = "LOCAL / PRIVATE / ONE-TIME INSTANCE",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF44483F),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}
