package com.example.shortcut

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import kotlin.math.roundToInt

/**
 * Interactive Image Cropper dialog for notification background artwork.
 * Allows users to pinch-to-zoom, drag/pan, adjust aspect ratio, and select
 * the exact visible region of their image before applying it as the notification background.
 */
@Composable
fun NotificationImageCropperDialog(
    imageUri: Uri,
    accentColor: Color = Color(0xFFFF6B35),
    onDismiss: () -> Unit,
    onCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current

    val sourceBitmap = remember(imageUri) {
        loadBitmapWithOrientation(context, imageUri)
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectedAspectRatio by remember { mutableStateOf(16f / 9f) } // 16:9 default, toggleable to 2:1

    val signalOrange = accentColor
    val darkBackdrop = Color(0xFF070A10)
    val cardSurface = Color(0xFF141926)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(darkBackdrop)
        ) {
            if (sourceBitmap != null) {
                val srcW = sourceBitmap.width.toFloat()
                val srcH = sourceBitmap.height.toFloat()

                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Top Bar Header (Fixed height, always visible)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear),
                                tint = Color.White
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "CROP BACKGROUND",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Adjust visible notification area",
                                color = Color(0xFF9EABC0),
                                fontSize = 11.sp
                            )
                        }

                        // Placeholder to balance the close button on left
                        Spacer(modifier = Modifier.size(40.dp))
                    }

                    // 2. Middle Interactive Cropping Canvas (Takes available weight, never overflows)
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val containerWidth = constraints.maxWidth.toFloat()
                        val containerHeight = constraints.maxHeight.toFloat()

                        val horizontalMargin = 24f
                        val verticalMargin = 16f
                        val availableWidth = (containerWidth - horizontalMargin * 2).coerceAtLeast(100f)
                        val availableHeight = (containerHeight - verticalMargin * 2).coerceAtLeast(100f)

                        var frameWidth = availableWidth
                        var frameHeight = frameWidth / selectedAspectRatio

                        if (frameHeight > availableHeight) {
                            frameHeight = availableHeight
                            frameWidth = frameHeight * selectedAspectRatio
                        }

                        // Fit scale so image minimally covers the entire crop frame
                        val baseFitScale = maxOf(frameWidth / srcW, frameHeight / srcH)
                        val effectiveScale = baseFitScale * scale

                        val curImgW = srcW * effectiveScale
                        val curImgH = srcH * effectiveScale

                        val maxPanX = ((curImgW - frameWidth) / 2f).coerceAtLeast(0f)
                        val maxPanY = ((curImgH - frameHeight) / 2f).coerceAtLeast(0f)

                        val clampedOffset = Offset(
                            x = offset.x.coerceIn(-maxPanX, maxPanX),
                            y = offset.y.coerceIn(-maxPanY, maxPanY)
                        )

                        val cX = containerWidth / 2f
                        val cY = containerHeight / 2f

                        // Interactive Gesture Area
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(sourceBitmap, selectedAspectRatio) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                                    val newEffective = baseFitScale * newScale
                                    val newImgW = srcW * newEffective
                                    val newImgH = srcH * newEffective
                                    val curMaxX = ((newImgW - frameWidth) / 2f).coerceAtLeast(0f)
                                    val curMaxY = ((newImgH - frameHeight) / 2f).coerceAtLeast(0f)

                                    scale = newScale
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-curMaxX, curMaxX),
                                        y = (offset.y + pan.y).coerceIn(-curMaxY, curMaxY)
                                    )
                                }
                            }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val drawLeft = (cX + clampedOffset.x) - (curImgW / 2f)
                                val drawTop = (cY + clampedOffset.y) - (curImgH / 2f)

                                // 1. Draw source image
                                drawImage(
                                    image = sourceBitmap.asImageBitmap(),
                                    dstOffset = IntOffset(drawLeft.roundToInt(), drawTop.roundToInt()),
                                    dstSize = IntSize(curImgW.roundToInt(), curImgH.roundToInt()),
                                    filterQuality = FilterQuality.High
                                )

                                // 2. Crop rectangle boundaries
                                val cropLeft = cX - (frameWidth / 2f)
                                val cropTop = cY - (frameHeight / 2f)
                                val cropRight = cX + (frameWidth / 2f)
                                val cropBottom = cY + (frameHeight / 2f)

                                // 3. Dark mask outside crop window
                                val maskPath = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addRect(Rect(0f, 0f, size.width, size.height))
                                    addRoundRect(
                                        RoundRect(
                                            left = cropLeft,
                                            top = cropTop,
                                            right = cropRight,
                                            bottom = cropBottom,
                                            cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                                        )
                                    )
                                }
                                drawPath(maskPath, color = Color(0xDD05070F))

                                // 4. Rule-of-thirds grid inside crop window
                                val stepX = frameWidth / 3f
                                val stepY = frameHeight / 3f
                                val gridColor = Color.White.copy(alpha = 0.22f)
                                val gridStroke = Stroke(width = 1.dp.toPx())

                                drawLine(gridColor, Offset(cropLeft + stepX, cropTop), Offset(cropLeft + stepX, cropBottom), gridStroke.width)
                                drawLine(gridColor, Offset(cropLeft + stepX * 2, cropTop), Offset(cropLeft + stepX * 2, cropBottom), gridStroke.width)
                                drawLine(gridColor, Offset(cropLeft, cropTop + stepY), Offset(cropRight, cropTop + stepY), gridStroke.width)
                                drawLine(gridColor, Offset(cropLeft, cropTop + stepY * 2), Offset(cropRight, cropTop + stepY * 2), gridStroke.width)

                                // 5. Outer glow and border for crop window
                                drawRoundRect(
                                    color = signalOrange.copy(alpha = 0.4f),
                                    topLeft = Offset(cropLeft - 1.dp.toPx(), cropTop - 1.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(frameWidth + 2.dp.toPx(), frameHeight + 2.dp.toPx()),
                                    cornerRadius = CornerRadius(15.dp.toPx(), 15.dp.toPx()),
                                    style = Stroke(width = 3.dp.toPx())
                                )

                                drawRoundRect(
                                    color = signalOrange,
                                    topLeft = Offset(cropLeft, cropTop),
                                    size = androidx.compose.ui.geometry.Size(frameWidth, frameHeight),
                                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        // Apply Action Button floating at bottom right of canvas area or anchored
                    }

                    // 3. Bottom Control Panel & Apply Action (Always pinned and visible inside the viewport)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(cardSurface.copy(alpha = 0.95f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Aspect Ratio selector & Reset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val ratios = listOf(
                                    Pair("16:9 Banner", 16f / 9f),
                                    Pair("2:1 Cover", 2f / 1f)
                                )
                                ratios.forEach { (label, ratio) ->
                                    val isSelected = kotlin.math.abs(selectedAspectRatio - ratio) < 0.05f
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) signalOrange.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
                                            .border(1.dp, if (isSelected) signalOrange else Color.Transparent, RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedAspectRatio = ratio
                                                scale = 1f
                                                offset = Offset.Zero
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) signalOrange else Color.White.copy(alpha = 0.7f),
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            // Reset Zoom/Pan Button
                            IconButton(
                                onClick = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Zoom & Pan",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Zoom Slider & Quick Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { scale = (scale - 0.25f).coerceAtLeast(1f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Zoom Out",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Slider(
                                value = scale,
                                onValueChange = { scale = it },
                                valueRange = 1f..5f,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = signalOrange,
                                    activeTrackColor = signalOrange,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )

                            IconButton(
                                onClick = { scale = (scale + 0.25f).coerceAtMost(5f) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoom In",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Full-Width Primary Action Buttons: Cancel and Apply
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            Button(
                                onClick = {
                                    val cropped = performCropDirect(
                                        source = sourceBitmap,
                                        aspectRatio = selectedAspectRatio,
                                        userScale = scale,
                                        userPan = offset
                                    )
                                    if (cropped != null) {
                                        onCropped(cropped)
                                    } else {
                                        onCropped(sourceBitmap)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = signalOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Apply Cover Art",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = signalOrange)
                }
            }
        }
    }
}

/**
 * Calculates the exact crop rectangle on the source bitmap directly from
 * aspect ratio, user pan, and zoom, returning a crisp high-resolution Bitmap.
 */
private fun performCropDirect(
    source: Bitmap,
    aspectRatio: Float,
    userScale: Float,
    userPan: Offset
): Bitmap? {
    return try {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        // Base crop window centered on source bitmap with requested aspect ratio
        var baseW = srcW
        var baseH = baseW / aspectRatio
        if (baseH > srcH) {
            baseH = srcH
            baseW = baseH * aspectRatio
        }

        // As user zooms in, the cropped region of the source image becomes smaller
        val finalCropW = baseW / userScale
        val finalCropH = baseH / userScale

        // The maximum distance the user could pan relative to the cropped frame
        val maxSourcePanX = ((srcW - finalCropW) / 2f).coerceAtLeast(0f)
        val maxSourcePanY = ((srcH - finalCropH) / 2f).coerceAtLeast(0f)

        // Standardize pan offset to source bitmap coordinate space
        val normPanX = (userPan.x / 100f * (srcW / 4f)).coerceIn(-maxSourcePanX, maxSourcePanX)
        val normPanY = (userPan.y / 100f * (srcH / 4f)).coerceIn(-maxSourcePanY, maxSourcePanY)

        val cropCenterX = (srcW / 2f) - normPanX
        val cropCenterY = (srcH / 2f) - normPanY

        val finalLeft = (cropCenterX - (finalCropW / 2f)).coerceIn(0f, srcW - finalCropW)
        val finalTop = (cropCenterY - (finalCropH / 2f)).coerceIn(0f, srcH - finalCropH)

        val cropWInt = finalCropW.roundToInt().coerceIn(1, (srcW - finalLeft).roundToInt())
        val cropHInt = finalCropH.roundToInt().coerceIn(1, (srcH - finalTop).roundToInt())

        val cropped = Bitmap.createBitmap(
            source,
            finalLeft.roundToInt(),
            finalTop.roundToInt(),
            cropWInt,
            cropHInt
        )

        // Standardize output to crisp 1280x720 or matching aspect ratio
        val targetWidth = 1280
        val targetHeight = (targetWidth / aspectRatio).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Calculates the exact crop rectangle on the source bitmap based on the
 * user's pan, zoom, and frame dimensions, returning a pristine high-resolution Bitmap.
 */
private fun performCrop(
    source: Bitmap,
    frameWidth: Float,
    frameHeight: Float,
    scale: Float,
    panOffset: Offset,
    containerWidth: Float,
    containerHeight: Float,
    topOffset: Float
): Bitmap? {
    return try {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()

        val curImgW = srcW * scale
        val curImgH = srcH * scale

        val cX = containerWidth / 2f
        val cY = topOffset + (containerHeight / 2f)

        val cropLeftOnScreen = cX - (frameWidth / 2f)
        val cropTopOnScreen = cY - (frameHeight / 2f)

        val imgLeftOnScreen = (cX + panOffset.x) - (curImgW / 2f)
        val imgTopOnScreen = (cY + panOffset.y) - (curImgH / 2f)

        val relCropX = (cropLeftOnScreen - imgLeftOnScreen) / scale
        val relCropY = (cropTopOnScreen - imgTopOnScreen) / scale
        val relCropW = frameWidth / scale
        val relCropH = frameHeight / scale

        val finalX = relCropX.coerceIn(0f, srcW - 1f)
        val finalY = relCropY.coerceIn(0f, srcH - 1f)
        val finalW = relCropW.coerceIn(1f, srcW - finalX)
        val finalH = relCropH.coerceIn(1f, srcH - finalY)

        val cropped = Bitmap.createBitmap(
            source,
            finalX.roundToInt(),
            finalY.roundToInt(),
            finalW.roundToInt(),
            finalH.roundToInt()
        )

        // Standardize output to crisp 1280x720 or matching aspect ratio
        val targetWidth = 1280
        val targetHeight = (targetWidth / (frameWidth / frameHeight)).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(cropped, targetWidth, targetHeight, true)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * Loads a bitmap from URI and applies EXIF orientation if present.
 */
private fun loadBitmapWithOrientation(context: Context, uri: Uri): Bitmap? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val rawBitmap = BitmapFactory.decodeStream(inputStream) ?: return null

        try {
            context.contentResolver.openInputStream(uri)?.use { exifStream ->
                val exif = ExifInterface(exifStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                }
                return Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            // Ignore EXIF failure, fallback to rawBitmap
        }
        rawBitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
