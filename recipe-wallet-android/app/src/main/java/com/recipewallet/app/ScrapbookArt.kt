package com.recipewallet.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

// Drawn as real vectors: these stay crisp on a judge's tablet and work fully offline.
private val Pencil = Color(0xFF653E4D)
private val StickerWhite = Color(0xFFFFFEFA)
private val FoodRose = Color(0xFFED9CA7)
private val FoodButter = Color(0xFFF2D881)
private val FoodCoral = Color(0xFFE98572)
private val FoodLeaf = Color(0xFF9AA36A)

/** A paper-cut food character with an accessible food label and no background disc. */
@Composable
internal fun FoodSticker(kind: String, modifier: Modifier = Modifier) {
    val label = when (kind) {
        "noodles" -> "Little noodle nest"
        "pasta" -> "Pasta and tomato sticker"
        "bowl" -> "Chickpea and avocado collage"
        "avo" -> "Smiling avocado sticker"
        "berry" -> "Strawberry sticker"
        "bread" -> "Happy toast sticker"
        "lemon" -> "Lemon sticker"
        else -> "Tomato sticker"
    }
    Canvas(modifier.defaultMinSize(72.dp, 72.dp).semantics { contentDescription = label }) {
        val factor = min(size.width, size.height) / 200f
        withTransform({
            translate((size.width - 200f * factor) / 2f, (size.height - 200f * factor) / 2f)
            scale(factor, factor, Offset.Zero)
        }) {
            when (kind) {
                "noodles" -> noodleSticker()
                "pasta" -> pastaSticker()
                "bowl" -> produceSticker()
                "avo" -> avocadoSticker()
                "berry" -> berrySticker()
                "bread" -> breadSticker()
                "lemon" -> lemonSticker()
                else -> tomatoSticker()
            }
        }
    }
}

/** Asymmetric, responsive scrapbook hero. No container, pot, or circular image backdrop. */
@Composable
internal fun FoodCollage(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.defaultMinSize(minHeight = 250.dp)) {
        val foodSize = (maxWidth * .40f).coerceIn(90.dp, 142.dp)
        WashiTape(Modifier.align(Alignment.TopCenter).offset(x = (-16).dp, y = 4.dp)
            .size(84.dp, 23.dp).graphicsLayer { rotationZ = -8f })
        FoodSticker("lemon", Modifier.align(Alignment.TopStart).offset(x = 5.dp, y = 13.dp)
            .size(foodSize).graphicsLayer { rotationZ = -16f })
        FoodSticker("bread", Modifier.align(Alignment.TopEnd).offset(x = (-5).dp, y = 12.dp)
            .size(foodSize * .88f).graphicsLayer { rotationZ = 11f })
        FoodSticker("avo", Modifier.align(Alignment.Center).offset(x = 17.dp, y = 8.dp)
            .size(foodSize * 1.13f).graphicsLayer { rotationZ = 12f })
        FoodSticker("tomato", Modifier.align(Alignment.BottomStart).offset(x = 13.dp, y = (-5).dp)
            .size(foodSize * .77f).graphicsLayer { rotationZ = -10f })
        Text("a little joy,\non your plate", color = Pencil, fontSize = 17.sp,
            lineHeight = 21.sp, fontFamily = FontFamily.Cursive, fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-1).dp, y = (-15).dp)
                .graphicsLayer { rotationZ = -6f }
                .background(Color(0xFFFFEABD), RoundedCornerShape(3.dp)).padding(9.dp))
        Canvas(Modifier.align(Alignment.CenterStart).offset(x = 3.dp, y = 8.dp).size(29.dp)) {
            val p = Path().apply {
                moveTo(size.width * .2f, 0f); lineTo(size.width * .35f, size.height * .37f)
                moveTo(size.width * .55f, 0f); lineTo(size.width * .63f, size.height * .28f)
                moveTo(0f, size.height * .7f); lineTo(size.width * .3f, size.height * .8f)
            }
            drawPath(p, Pencil.copy(alpha = .65f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** A translucent torn tape strip, used as a decorative label pin. */
@Composable
internal fun WashiTape(modifier: Modifier = Modifier, color: Color = Color(0xFFE8B9C5)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * .025f, 0f); lineTo(w * .98f, h * .05f)
            lineTo(w * .96f, h * .25f); lineTo(w, h * .46f)
            lineTo(w * .96f, h * .70f); lineTo(w * .985f, h)
            lineTo(0f, h * .94f); lineTo(w * .025f, h * .7f)
            lineTo(0f, h * .5f); lineTo(w * .025f, h * .3f); close()
        }
        drawPath(path, color.copy(alpha = .75f))
        for (i in 1..8) {
            val x = w * i / 9f
            drawLine(StickerWhite.copy(alpha = .38f), Offset(x, h * .16f), Offset(x - w * .06f, h * .85f), 1.5.dp.toPx())
        }
    }
}

private fun DrawScope.paperShape(path: Path, color: Color, outline: Float = 2.5f) {
    drawPath(path, StickerWhite, style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(path, color)
    drawPath(path, Pencil, style = Stroke(outline, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.paperOval(color: Color, left: Float, top: Float, width: Float, height: Float, edge: Boolean = true) {
    val origin = Offset(left, top)
    val dimensions = Size(width, height)
    if (edge) drawOval(StickerWhite, origin, dimensions, style = Stroke(13f))
    drawOval(color, origin, dimensions)
    drawOval(Pencil, origin, dimensions, style = Stroke(2.4f))
}

private fun DrawScope.foodFace(cx: Float, cy: Float, span: Float = 23f) {
    drawOval(Pencil, Offset(cx - span - 2f, cy - 4f), Size(4.5f, 7f))
    drawOval(Pencil, Offset(cx + span - 2f, cy - 4f), Size(4.5f, 7f))
    drawArc(Pencil, 12f, 156f, false, Offset(cx - 9f, cy - 2f), Size(18f, 11f), style = Stroke(2.2f, cap = StrokeCap.Round))
    drawOval(FoodRose.copy(alpha = .6f), Offset(cx - span - 12f, cy + 6f), Size(13f, 6f))
    drawOval(FoodRose.copy(alpha = .6f), Offset(cx + span, cy + 6f), Size(13f, 6f))
}

private fun DrawScope.leaf(cx: Float, cy: Float, flip: Float = 1f) {
    val p = Path().apply {
        moveTo(cx, cy)
        cubicTo(cx - 3f * flip, cy - 26f, cx + 29f * flip, cy - 35f, cx + 36f * flip, cy - 35f)
        cubicTo(cx + 38f * flip, cy - 15f, cx + 19f * flip, cy + 2f, cx, cy)
        close()
    }
    paperShape(p, FoodLeaf, 2f)
    drawLine(Pencil.copy(alpha = .7f), Offset(cx + 4f * flip, cy - 4f), Offset(cx + 27f * flip, cy - 26f), 1.5f, StrokeCap.Round)
}

private fun DrawScope.tomatoSticker() {
    val body = Path().apply {
        moveTo(100f, 58f)
        cubicTo(65f, 35f, 25f, 59f, 24f, 104f)
        cubicTo(22f, 149f, 58f, 175f, 99f, 166f)
        cubicTo(147f, 177f, 178f, 146f, 176f, 105f)
        cubicTo(175f, 61f, 139f, 36f, 100f, 58f)
        close()
    }
    paperShape(body, FoodCoral)
    drawOval(Color(0xFFFFC7AF), Offset(42f, 76f), Size(18f, 28f))
    val crown = Path().apply {
        moveTo(98f, 62f); lineTo(67f, 51f); lineTo(88f, 45f); lineTo(83f, 27f)
        lineTo(104f, 44f); lineTo(128f, 31f); lineTo(118f, 51f); lineTo(143f, 61f)
        lineTo(116f, 65f); lineTo(105f, 79f); close()
    }
    paperShape(crown, FoodLeaf, 2f)
    foodFace(103f, 118f)
}

private fun DrawScope.lemonSticker() {
    val p = Path().apply {
        moveTo(33f, 113f)
        cubicTo(28f, 100f, 38f, 88f, 46f, 87f)
        cubicTo(69f, 43f, 124f, 39f, 154f, 75f)
        cubicTo(174f, 74f, 179f, 91f, 165f, 103f)
        cubicTo(159f, 148f, 95f, 171f, 58f, 141f)
        cubicTo(47f, 141f, 33f, 132f, 33f, 113f); close()
    }
    paperShape(p, FoodButter)
    leaf(122f, 59f)
    drawArc(StickerWhite.copy(alpha = .75f), 202f, 60f, false, Offset(53f, 65f), Size(91f, 71f), style = Stroke(5f, cap = StrokeCap.Round))
    foodFace(108f, 111f, 20f)
    listOf(Offset(65f, 120f), Offset(71f, 132f), Offset(140f, 96f), Offset(146f, 105f)).forEach {
        drawCircle(Color(0xFFD3B15F), 1.5f, it)
    }
}

private fun DrawScope.avocadoSticker() {
    val p = Path().apply {
        moveTo(104f, 21f)
        cubicTo(71f, 15f, 72f, 58f, 48f, 89f)
        cubicTo(9f, 142f, 48f, 182f, 102f, 181f)
        cubicTo(156f, 183f, 190f, 142f, 154f, 89f)
        cubicTo(133f, 58f, 135f, 24f, 104f, 21f); close()
    }
    paperShape(p, Color(0xFF8E9A64))
    withTransform({ scale(.84f, .86f, Offset(101f, 103f)) }) {
        drawPath(p, Color(0xFFE3DEA3))
        drawPath(p, Pencil.copy(alpha = .45f), style = Stroke(1.8f))
    }
    paperOval(Color(0xFFBA8667), 71f, 105f, 61f, 58f, edge = false)
    drawArc(Color(0xFFE4B196), 195f, 95f, false, Offset(83f, 116f), Size(35f, 34f), style = Stroke(4f, cap = StrokeCap.Round))
    foodFace(102f, 83f, 18f)
}

private fun DrawScope.berrySticker() {
    val p = Path().apply {
        moveTo(101f, 59f)
        cubicTo(63f, 36f, 26f, 64f, 39f, 108f)
        cubicTo(48f, 141f, 79f, 169f, 102f, 183f)
        cubicTo(132f, 165f, 158f, 139f, 168f, 103f)
        cubicTo(178f, 62f, 143f, 38f, 101f, 59f); close()
    }
    paperShape(p, Color(0xFFECA0A8))
    val top = Path().apply {
        moveTo(103f, 62f); lineTo(64f, 48f); lineTo(87f, 46f); lineTo(85f, 25f)
        lineTo(105f, 43f); lineTo(126f, 25f); lineTo(122f, 46f)
        lineTo(148f, 50f); lineTo(119f, 64f); lineTo(104f, 84f); close()
    }
    paperShape(top, FoodLeaf, 2f)
    foodFace(104f, 111f, 20f)
    listOf(Offset(61f, 89f), Offset(146f, 90f), Offset(70f, 132f), Offset(140f, 130f), Offset(89f, 150f), Offset(114f, 151f)).forEach {
        drawLine(Color(0xFFFFE5AD), it, Offset(it.x - 2f, it.y + 6f), 3.1f, StrokeCap.Round)
    }
}

private fun DrawScope.breadSticker() {
    val p = Path().apply {
        moveTo(42f, 89f)
        cubicTo(13f, 68f, 35f, 29f, 68f, 31f)
        cubicTo(91f, 11f, 130f, 19f, 140f, 35f)
        cubicTo(180f, 30f, 188f, 75f, 159f, 91f)
        lineTo(160f, 164f); quadraticBezierTo(158f, 178f, 144f, 178f)
        lineTo(58f, 176f); quadraticBezierTo(41f, 177f, 42f, 161f); close()
    }
    paperShape(p, Color(0xFFD8A477))
    withTransform({ scale(.83f, .82f, Offset(102f, 102f)) }) {
        drawPath(p, Color(0xFFF9E4BC))
        drawPath(p, Pencil.copy(alpha = .35f), style = Stroke(1.5f))
    }
    drawRoundRect(FoodButter, Offset(78f, 75f), Size(46f, 35f), CornerRadius(6f, 6f))
    drawRoundRect(Color(0xFFD0AD60), Offset(78f, 75f), Size(46f, 35f), CornerRadius(6f, 6f), style = Stroke(1.5f))
    foodFace(101f, 128f, 22f)
    listOf(Offset(64f, 69f), Offset(142f, 67f), Offset(64f, 145f), Offset(143f, 146f)).forEach { drawCircle(Color(0xFFD7B68B), 2f, it) }
}

private fun DrawScope.noodleSticker() {
    val mound = Path().apply {
        moveTo(32f, 145f)
        cubicTo(12f, 124f, 31f, 91f, 54f, 84f)
        cubicTo(56f, 43f, 100f, 37f, 123f, 63f)
        cubicTo(157f, 46f, 177f, 82f, 166f, 104f)
        cubicTo(193f, 127f, 174f, 158f, 142f, 163f)
        cubicTo(101f, 176f, 63f, 166f, 32f, 145f); close()
    }
    paperShape(mound, FoodButter)
    val strands = listOf(
        floatArrayOf(38f, 111f, 115f, 17f, 153f, 115f),
        floatArrayOf(35f, 126f, 98f, 55f, 157f, 131f),
        floatArrayOf(49f, 141f, 102f, 85f, 159f, 145f),
        floatArrayOf(58f, 94f, 74f, 71f, 132f, 91f),
        floatArrayOf(62f, 155f, 109f, 125f, 136f, 157f)
    )
    strands.forEach { v ->
        val line = Path().apply { moveTo(v[0], v[1]); quadraticBezierTo(v[2], v[3], v[4], v[5]) }
        drawPath(line, Color(0xFFBF9156), style = Stroke(3f, cap = StrokeCap.Round))
    }
    leaf(128f, 76f)
    foodFace(100f, 123f, 23f)
}

private fun DrawScope.pastaSticker() {
    // Two farfalle pieces and a tiny tomato, rather than a cooking pot.
    val bow = Path().apply {
        moveTo(36f, 45f); lineTo(81f, 64f); lineTo(132f, 42f)
        lineTo(127f, 58f); lineTo(135f, 70f); lineTo(127f, 83f); lineTo(133f, 100f)
        lineTo(83f, 81f); lineTo(35f, 101f); lineTo(41f, 82f)
        lineTo(33f, 70f); lineTo(41f, 57f); close()
    }
    paperShape(bow, FoodButter)
    drawLine(Color(0xFFBF9156), Offset(83f, 64f), Offset(83f, 81f), 4f, StrokeCap.Round)
    withTransform({ translate(34f, 59f); rotate(-14f, Offset(85f, 73f)); scale(.83f, .83f, Offset(85f, 73f)) }) {
        paperShape(bow, Color(0xFFF4DCA0))
        foodFace(83f, 72f, 15f)
    }
    withTransform({ translate(100f, 108f); scale(.43f, .43f, Offset.Zero) }) { tomatoSticker() }
}

private fun DrawScope.produceSticker() {
    // A loose ingredients collage: no bowl or pot silhouette.
    withTransform({ translate(-2f, 2f); scale(.87f, .87f, Offset.Zero) }) { avocadoSticker() }
    listOf(Offset(139f, 130f), Offset(166f, 109f), Offset(164f, 151f)).forEach { o ->
        paperOval(Color(0xFFE4BF8D), o.x - 14f, o.y - 13f, 28f, 26f)
        drawArc(Pencil.copy(alpha = .55f), 180f, 155f, false, Offset(o.x - 4f, o.y - 5f), Size(10f, 11f), style = Stroke(1.8f))
    }
    leaf(140f, 94f)
}
