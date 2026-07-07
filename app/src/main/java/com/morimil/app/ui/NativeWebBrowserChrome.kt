package com.morimil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val NativeWebWindowColor = Color(0xFF1E1E23)

@Composable
internal fun NativeWebBrowserChrome(
    currentUrl: String,
    isLoading: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NativeWebWindowColor),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.width(126.dp).height(18.dp),
                color = BraveTabActive,
                shape = RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(text = "◈", fontSize = 7.sp, color = BraveAccent)
                    Text(
                        text = tabTitle(currentUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = BraveToolbarText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "×", fontSize = 8.sp, color = BraveToolbarText)
                }
            }
            BrowserChromeButton(label = "+", enabled = true, onClick = onHome)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "−", color = BraveToolbarMuted, fontSize = 8.sp)
            Text(text = "□", color = BraveToolbarMuted, fontSize = 7.sp)
            Text(text = "×", color = BraveToolbarMuted, fontSize = 8.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .background(BraveToolbar)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserChromeButton(label = "‹", enabled = canGoBack, onClick = onBack)
            BrowserChromeButton(label = "›", enabled = canGoForward, onClick = onForward)
            BrowserChromeButton(label = if (isLoading) "×" else "⟳", enabled = true, onClick = onRefresh)
            Text(text = "▱", color = BraveToolbarMuted, fontSize = 8.sp)
            Surface(
                modifier = Modifier.height(18.dp).weight(1f),
                color = BraveAddress,
                shape = RoundedCornerShape(9.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "⌕", color = BraveToolbarMuted, fontSize = 7.sp)
                    Text(
                        text = addressText(currentUrl),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = BraveToolbarText,
                        fontWeight = FontWeight.Medium,
                        fontSize = 8.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(text = "◈", color = BraveAccent, fontSize = 7.sp)
                    Text(text = "▴", color = BraveToolbarMuted, fontSize = 7.sp)
                }
            }
            Text(text = "☆", color = BraveToolbarMuted, fontSize = 8.sp)
            Text(text = "≡", color = BraveToolbarMuted, fontSize = 8.sp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(BraveBookmarkBar)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = "▦", color = BraveToolbarMuted, fontSize = 7.sp)
            Text(
                text = "Para acceder de forma rápida, coloca marcadores aquí",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = BraveToolbarText,
                fontSize = 6.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BrowserChromeButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.width(18.dp).height(18.dp)
    ) {
        Text(text = label, fontSize = 8.sp)
    }
}

private fun addressText(url: String): String {
    val clean = displayUrl(url)
    return when {
        clean.isBlank() -> "Buscar en Brave o escribir una URL"
        clean == "about:blank" -> "Buscar en Brave o escribir una URL"
        clean == "search.brave.com" -> "Buscar en Brave o escribir una URL"
        else -> clean
    }
}

private fun tabTitle(url: String): String {
    val clean = displayUrl(url)
    return when {
        clean.isBlank() -> "Nueva pestaña"
        clean == "about:blank" -> "Nueva pestaña"
        clean.startsWith("search.brave.com") -> "Nueva pestaña"
        else -> clean.substringBefore('/').ifBlank { clean }
    }
}

private fun displayUrl(url: String): String {
    return url
        .removePrefix("https://")
        .removePrefix("http://")
        .removeSuffix("/")
}

private val BraveToolbar = Color(0xFF313138)
private val BraveBookmarkBar = Color(0xFF39393F)
private val BraveTabActive = Color(0xFF3A3A41)
private val BraveAddress = Color(0xFF1D1D23)
private val BraveToolbarText = Color(0xFFE7E7EA)
private val BraveToolbarMuted = Color(0xFFA8A8AF)
private val BraveAccent = Color(0xFFFF5A1F)
