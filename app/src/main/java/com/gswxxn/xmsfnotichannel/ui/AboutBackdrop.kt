package com.gswxxn.xmsfnotichannel.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * 关于页的渐变光斑背景。
 *
 * HyperCeiler 的关于页背景是一段 AGSL `RuntimeShader`（`bg_frag.glsl`，
 * 6KB，含 HSL 转换、柏林噪声、四点光斑与阴影合成），由
 * `RenderEffect.createShaderEffect()` 挂到 View 上，再用 Folme 弹簧动画驱动。
 *
 * 本项目不复刻那套实现，原因很实际：**上一版掉帧就是 RuntimeShader +
 * 离屏层造成的**。这里改用最朴素的 `Canvas` + 三层径向渐变光斑，
 * 由一条 24 秒的无限过渡缓慢漂移：
 *
 * - 全程只有 3 次 `drawCircle`（径向渐变）＋ 1 次 `drawRect`（底色）；
 * - 不产生离屏 layer、不做模糊、不上着色器；
 * - 只作用在关于页这一屏，这里没有列表，不参与滚动。
 *
 * 视觉上接近 HyperCeiler 那种「柔和彩光在背景里缓慢游动」的观感。
 */
@Composable
fun AboutBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val transition = rememberInfiniteTransition(label = "aboutBackdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    // 底色：浅色走暖白到淡蓝，深色走深灰到深蓝
    val baseColors = if (dark) {
        listOf(Color(0xFF15161C), Color(0xFF1B1A26), Color(0xFF201A24))
    } else {
        listOf(Color(0xFFFDFBFF), Color(0xFFF3F0FB), Color(0xFFFBF3F6))
    }

    // 光斑配色：粉 / 蓝 / 紫，对应 HyperCeiler 的蓝紫粉渐变
    val blobColors = if (dark) {
        listOf(Color(0xFF5B4B8A), Color(0xFF3A5C8F), Color(0xFF8A5478))
    } else {
        listOf(Color(0xFFC9A7E8), Color(0xFFA8C4F0), Color(0xFFF0BFD8))
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawRect(
                brush = Brush.verticalGradient(
                    colors = baseColors,
                    startY = 0f,
                    endY = h,
                )
            )

            val baseRadius = minOf(w, h) * 0.62f
            repeat(blobColors.size) { index ->
                // 每个光斑相位错开 120°，形成互相追逐渐变的效果
                val angle = phase + index * (2f * Math.PI.toFloat() / blobColors.size)
                val cx = w * (0.5f + 0.30f * cos(angle))
                val cy = h * (0.30f + 0.20f * sin(angle * 0.85f))
                val radius = baseRadius * (0.9f + 0.1f * sin(angle * 1.3f))
                val alpha = (if (dark) 0.55f else 0.62f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            blobColors[index].copy(alpha = alpha),
                            blobColors[index].copy(alpha = 0f),
                        ),
                        center = Offset(cx, cy),
                        radius = radius,
                    ),
                    radius = radius,
                    center = Offset(cx, cy),
                )
            }
        }
        content()
    }
}