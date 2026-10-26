package org.athar.app.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.athar.app.R
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable fun Brand(modifier: Modifier = Modifier) {
    val bitmap = ImageBitmap.imageResource(R.drawable.athar_logo)
    val description = stringResource(R.string.brand_description)
    val backdrop = if (MaterialTheme.colorScheme.surface.luminance() < 0.2f) Color(0xFFF4F3ED) else Color.Transparent
    // Display the official mark's documented alpha-safe viewport; original asset bytes remain untouched.
    Canvas(modifier.size(72.dp).background(backdrop, MaterialTheme.shapes.small).semantics { contentDescription = description }) {
        val scale = minOf((size.width - 8.dp.toPx()).coerceAtLeast(1f) / 628f, (size.height - 8.dp.toPx()).coerceAtLeast(1f) / 580f)
        val width = (628 * scale).toInt(); val height = (580 * scale).toInt()
        drawImage(bitmap, srcOffset = IntOffset(446, 261), srcSize = IntSize(628, 580), dstOffset = IntOffset(((size.width - width) / 2).toInt(), ((size.height - height) / 2).toInt()), dstSize = IntSize(width, height), filterQuality = FilterQuality.High)
    }
}
@Composable fun PasswordField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(value, onValue, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) AtharIcons.VisibilityOff else AtharIcons.Visibility, stringResource(if (visible) R.string.hide_password else R.string.show_password)) } })
}
@Composable fun LoadingScreen(modifier: Modifier = Modifier) { Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(); Text(stringResource(R.string.loading)) } } }
@Composable fun MessagePanel(message: String, error: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(message, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer); action?.invoke() } }
}
@Composable fun FailurePanel(message: String?, retry: () -> Unit) { MessagePanel(message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.network_error), true) { TextButton(onClick = retry) { Text(stringResource(R.string.retry)) } } }
