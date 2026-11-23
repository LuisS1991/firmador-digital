package com.lnantes.uifirmaelectronica.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lnantes.uifirmaelectronica.ui.theme.AppIcon
import com.lnantes.uifirmaelectronica.ui.theme.AppIconGlyph

@Composable
fun PrimaryActionButton(
    text: String,
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(onClick = onClick, enabled = enabled && !loading, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            AppIconGlyph(icon, MaterialTheme.colorScheme.onPrimary, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(if (loading) "Procesando…" else text)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    OutlinedButton(onClick = onClick, enabled = enabled && !loading, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            AppIconGlyph(icon, MaterialTheme.colorScheme.primary, Modifier.size(18.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(if (loading) "Procesando…" else text)
    }
}

@Composable
fun QuietActionButton(
    text: String,
    icon: AppIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        AppIconGlyph(icon, MaterialTheme.colorScheme.primary, Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text)
    }
}
