package app.phoneshop.manager.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.delay

val Indigo = Color(0xFF4F46E5)
val Violet = Color(0xFF7C3AED)
val Teal = Color(0xFF0891B2)
val Ink = Color(0xFF18181B)
val Muted = Color(0xFF52525B)
val Good = Color(0xFF15803D)
val Warn = Color(0xFF9A3412)
val Bad = Color(0xFFB91C1C)

val BrandBrush = Brush.linearGradient(listOf(Indigo, Violet, Teal))

/** True when the phone has animations turned off; the app then skips its own motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun PhoneShopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Indigo,
            onPrimary = Color.White,
            secondary = Violet,
            background = Color.Transparent,
            surface = Color.White,
            onSurface = Ink,
        ),
        content = content,
    )
}

@Composable
fun GradientBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFFEEF2FF), Color(0xFFF5F3FF), Color(0xFFECFEFF)))
            )
            .drawBehind {
                val topCenter = Offset(size.width, 0f)
                val bottomCenter = Offset(0f, size.height)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x66A5B4FC), Color.Transparent),
                        center = topCenter,
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = topCenter,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x5967E8F9), Color.Transparent),
                        center = bottomCenter,
                        radius = size.width,
                    ),
                    radius = size.width,
                    center = bottomCenter,
                )
            },
        content = content,
    )
}

/** Frosted-glass card: translucent white, light edge, soft coloured shadow. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    var m = modifier
        .shadow(6.dp, shape, ambientColor = Indigo.copy(alpha = 0.15f), spotColor = Indigo.copy(alpha = 0.2f))
        .background(Color.White.copy(alpha = 0.58f), shape)
        .border(1.dp, Color.White.copy(alpha = 0.8f), shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(14.dp), content = content)
}

/** Rises and fades in when it first appears. Pass the list position for a staggered feel. */
@Composable
fun Modifier.riseIn(index: Int = 0): Modifier {
    if (LocalReduceMotion.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(8) * 50L)
        progress.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 40f
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed && !reduce) 0.96f else 1f, label = "press")
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .shadow(8.dp, shape, ambientColor = Violet.copy(alpha = 0.3f), spotColor = Violet.copy(alpha = 0.4f))
            .background(BrandBrush, shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val scale by animateFloatAsState(if (pressed && !reduce) 0.96f else 1f, label = "press")
    val shape = RoundedCornerShape(14.dp)
    val tint = if (danger) Bad else Indigo
    Box(
        modifier
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .background(Color.White.copy(alpha = 0.6f), shape)
            .border(BorderStroke(1.dp, tint.copy(alpha = 0.5f)), shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val base = Modifier.heightIn(min = 40.dp)
    val styled = if (selected) {
        base.background(BrandBrush, shape)
    } else {
        base
            .background(Color.White.copy(alpha = 0.6f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.85f), shape)
    }
    Box(
        styled
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.White else Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    readOnly: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        readOnly = readOnly,
        isError = error != null,
        supportingText = if (error != null) {
            { Text(error) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.75f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
            focusedBorderColor = Indigo,
            unfocusedBorderColor = Indigo.copy(alpha = 0.25f),
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Tap to choose one option from a list. */
@Composable
fun PickerField(
    label: String,
    value: String,
    options: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        GlassField(value = value, onChange = {}, label = label, readOnly = true)
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button) { open = true }
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            title = { Text(label) },
            text = {
                LazyColumn {
                    items(options) { option ->
                        Text(
                            option,
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onPick(option)
                                    open = false
                                }
                                .padding(vertical = 14.dp),
                            fontSize = 16.sp,
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.62f))
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ink)
            }
        } else {
            Box(Modifier.size(8.dp))
        }
        Text(
            title,
            Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        actions()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier.padding(top = 6.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF3F3F46),
        letterSpacing = 0.5.sp,
    )
}

@Composable
fun StatusPill(text: String, background: Color, foreground: Color) {
    Box(
        Modifier
            .background(background, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

val ScreenPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
val ListSpacing = Arrangement.spacedBy(10.dp)
