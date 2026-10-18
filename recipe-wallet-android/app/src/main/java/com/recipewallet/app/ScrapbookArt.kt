package com.recipewallet.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

private object FoodPhotoCache {
 private val images=mutableMapOf<String,androidx.compose.ui.graphics.ImageBitmap>()
 fun load(context:android.content.Context,file:String)=images.getOrPut(file){context.assets.open("$file-photo.jpg").use{BitmapFactory.decodeStream(it)}.asImageBitmap()}
}

// Shared offline photographs keep repeated recipe cards inexpensive on tablet memory.
private val Pencil = Color(0xFF653E4D)
private val StickerWhite = Color(0xFFFFFEFA)
private val FoodRose = Color(0xFFED9CA7)
private val FoodButter = Color(0xFFF2D881)
private val FoodCoral = Color(0xFFE98572)
private val FoodLeaf = Color(0xFF9AA36A)

/** A representative real food photograph, sized by its containing card. */
@Composable
internal fun FoodSticker(kind: String, modifier: Modifier = Modifier) {
    val context=LocalContext.current
    val file=when(kind){"noodles","bread"->"noodles";"pasta","tomato","berry"->"pasta";else->"bowl"}
    val photo=remember(file){FoodPhotoCache.load(context,file)}
    Image(photo,contentDescription="$file recipe inspiration photograph",modifier=modifier,contentScale=ContentScale.Crop)

}

/** Asymmetric, responsive scrapbook hero. No container, pot, or circular image backdrop. */
@Composable
internal fun FoodCollage(modifier: Modifier = Modifier) {
    FoodSticker("bowl",modifier)
}

/** A translucent torn tape strip, used as a decorative label pin. */
@Composable
internal fun WashiTape(modifier: Modifier = Modifier, color: Color = Color(0xFFD2A6BD)) {
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
        for(row in 1..3)drawLine(StickerWhite.copy(alpha=.3f),Offset(0f,h*row/4f),Offset(w,h*row/4f),2.dp.toPx())
        for (i in 1..16) {
            val x = w * i / 17f
            drawLine(StickerWhite.copy(alpha = .38f), Offset(x, h * .16f), Offset(x, h * .85f), 2.5.dp.toPx())
        }
    }
}
