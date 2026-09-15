package com.example.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.AppCache
import com.example.AppInfo
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutNotificationScreen(
    onDismiss: (() -> Unit)? = null,
    onRequestNotificationPermission: () -> Unit,
    isNotificationPermissionGranted: Boolean,
    isEmbedded: Boolean = false
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val signalOrange = Color(0xFFFF6B35)
    val inkLight = Color(0xFFEEF0F6)
    val inkDim = Color(0xFF5A6178)
    val cardBg = Color(0xFF151D33)
    val containerBg = Color(0xFF0D1220)

    // Load state from SharedPreferences
    var isEnabled by remember { mutableStateOf(ShortcutNotificationPreferences.isEnabled(context)) }
    var isOngoing by remember { mutableStateOf(ShortcutNotificationPreferences.isOngoing(context)) }
    var targetPackage by remember { mutableStateOf(ShortcutNotificationPreferences.getTargetPackage(context)) }
    var targetAppName by remember { mutableStateOf(ShortcutNotificationPreferences.getTargetAppName(context)) }
    var customTitle by remember { mutableStateOf(ShortcutNotificationPreferences.getRawTitle(context)) }
    var customBody by remember { mutableStateOf(ShortcutNotificationPreferences.getRawBody(context)) }
    var selectedIconType by remember { mutableStateOf(ShortcutNotificationPreferences.getIconType(context)) }

    var bannerVersion by remember { mutableStateOf(ShortcutNotificationPreferences.getBannerVersion(context)) }
    var bannerBitmap by remember(bannerVersion) { mutableStateOf(ShortcutNotificationPreferences.loadBannerBitmap(context)) }

    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }

    val bannerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingCropUri = uri
            showCropDialog = true
        }
    }

    var showAppPicker by remember { mutableStateOf(false) }

    // Resolve current selected AppInfo for icon preview
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = AppCache.getApps(context)
            withContext(Dispatchers.Main) {
                installedApps = apps
                if (targetPackage.isNotBlank() && targetAppName.isBlank()) {
                    val match = apps.find { it.packageName == targetPackage }
                    if (match != null) {
                        targetAppName = match.label
                        ShortcutNotificationPreferences.setTargetApp(context, targetPackage, match.label)
                    }
                }
            }
        }
    }

    val currentAppInfo = remember(targetPackage, installedApps) {
        installedApps.find { it.packageName == targetPackage }
    }

    // Helper to persist and sync live notification
    fun syncSettings() {
        ShortcutNotificationPreferences.saveAll(
            context = context,
            enabled = isEnabled,
            ongoing = isOngoing,
            pkg = targetPackage,
            appName = targetAppName,
            title = customTitle,
            body = customBody,
            iconType = selectedIconType
        )
        ShortcutNotificationManager.syncServiceState(context)
    }

    Box(
        modifier = if (isEmbedded) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxSize()
                .background(containerBg)
                .statusBarsPadding()
                .navigationBarsPadding()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = if (isEmbedded) 0.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isEmbedded && onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = inkLight
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shortcut_notif_track),
                        color = signalOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = stringResource(R.string.shortcut_notif_title_screen),
                        color = inkLight,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isEnabled) signalOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            1.dp,
                            if (isEnabled) signalOrange.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isEnabled) stringResource(R.string.shortcut_notif_active) else stringResource(R.string.shortcut_notif_disabled),
                        color = if (isEnabled) signalOrange else inkDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Activation Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.07f)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Enable Notification Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isEnabled) signalOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isEnabled) signalOrange else inkDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.shortcut_notif_enable_toggle),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.shortcut_notif_enable_desc),
                                color = inkDim,
                                fontSize = 12.sp
                            )
                        }

                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                if (checked && !isNotificationPermissionGranted) {
                                    onRequestNotificationPermission()
                                }
                                isEnabled = checked
                                syncSettings()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = signalOrange,
                                uncheckedThumbColor = inkDim,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Ongoing / Non-swipeable Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isOngoing) signalOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOngoing) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = if (isOngoing) signalOrange else inkDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.shortcut_notif_ongoing_toggle),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.shortcut_notif_ongoing_desc),
                                color = inkDim,
                                fontSize = 12.sp
                            )
                        }

                        Switch(
                            checked = isOngoing,
                            enabled = isEnabled,
                            onCheckedChange = { checked ->
                                isOngoing = checked
                                syncSettings()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = signalOrange,
                                uncheckedThumbColor = inkDim,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 2: Live Preview Card (System Notification Shade Replica)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.shortcut_notif_live_preview_header),
                    color = signalOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = stringResource(R.string.shortcut_notif_system_shade),
                    color = inkDim,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            NotificationLivePreviewCard(
                targetAppName = targetAppName,
                title = if (customTitle.isNotBlank()) customTitle else (if (targetAppName.isNotBlank()) targetAppName else "App Shortcut"),
                body = customBody,
                iconType = selectedIconType,
                isOngoing = isOngoing,
                currentAppInfo = currentAppInfo,
                bannerBitmap = bannerBitmap,
                accentColor = signalOrange
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section 3: Target Application Selector
            Text(
                text = stringResource(R.string.shortcut_notif_target_app_header),
                color = signalOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAppPicker = true },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.07f)))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentAppInfo?.iconBitmap != null) {
                        Image(
                            bitmap = currentAppInfo.iconBitmap,
                            contentDescription = targetAppName,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(signalOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Apps,
                                contentDescription = null,
                                tint = signalOrange,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (targetAppName.isNotBlank()) targetAppName else stringResource(R.string.shortcut_notif_no_app_selected),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (targetPackage.isNotBlank()) targetPackage else stringResource(R.string.shortcut_notif_tap_to_choose_app),
                            color = inkDim,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(signalOrange.copy(alpha = 0.15f))
                            .border(1.dp, signalOrange.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.shortcut_notif_choose_app_btn),
                            color = signalOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 4: Notification Background Image (Full Cover)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.shortcut_notif_banner_header),
                    color = signalOrange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(signalOrange.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.shortcut_notif_banner_badge),
                        color = signalOrange,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.07f)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.shortcut_notif_banner_desc),
                        color = inkDim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (bannerBitmap != null) {
                        // Current cropped background preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                                .border(1.dp, signalOrange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            Image(
                                bitmap = bannerBitmap!!.asImageBitmap(),
                                contentDescription = "Active Notification Background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Overlay label chip
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Full Background Applied",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { bannerPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = signalOrange),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.shortcut_notif_replace_banner),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    ShortcutNotificationPreferences.deleteBannerImage(context)
                                    bannerBitmap = null
                                    if (isEnabled) {
                                        ShortcutNotificationManager.syncServiceState(context)
                                    }
                                    Toast.makeText(context, "Background removed", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.shortcut_notif_remove_banner),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // Empty background upload placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.03f))
                                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(12.dp))
                                .clickable { bannerPickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(signalOrange.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Crop,
                                        contentDescription = null,
                                        tint = signalOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = stringResource(R.string.shortcut_notif_upload_banner),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.shortcut_notif_banner_hint),
                                    color = inkDim,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 5: Custom Text Input Fields
            Text(
                text = stringResource(R.string.shortcut_notif_text_header),
                color = signalOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.07f)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Notification Title
                    Text(
                        text = stringResource(R.string.shortcut_notif_title_field),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customTitle,
                        onValueChange = {
                            customTitle = it
                            syncSettings()
                        },
                        placeholder = {
                            Text(
                                text = if (targetAppName.isNotBlank()) targetAppName else stringResource(R.string.shortcut_notif_title_placeholder),
                                color = inkDim
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (customTitle.isNotBlank()) {
                                IconButton(onClick = {
                                    customTitle = ""
                                    syncSettings()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear),
                                        tint = inkDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = signalOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = signalOrange
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Notification Body
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.shortcut_notif_body_field),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Optional",
                            color = inkDim,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customBody,
                        onValueChange = {
                            customBody = it
                            syncSettings()
                        },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.shortcut_notif_body_optional_hint),
                                color = inkDim
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (customBody.isNotBlank()) {
                                IconButton(onClick = {
                                    customBody = ""
                                    syncSettings()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear),
                                        tint = inkDim,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = signalOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = signalOrange
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Chips: "App Name & Icon Only" vs presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (customBody.isBlank()) signalOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                .border(
                                    1.dp,
                                    if (customBody.isBlank()) signalOrange else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    customBody = ""
                                    syncSettings()
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.shortcut_notif_app_name_only),
                                color = if (customBody.isBlank()) signalOrange else inkLight,
                                fontSize = 11.sp,
                                fontWeight = if (customBody.isBlank()) FontWeight.Bold else FontWeight.Medium
                            )
                        }

                        listOf(
                            stringResource(R.string.shortcut_notif_chip_tap_open),
                            stringResource(R.string.shortcut_notif_chip_quick_launch)
                        ).forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (customBody == preset) signalOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                    .border(
                                        1.dp,
                                        if (customBody == preset) signalOrange else Color.White.copy(alpha = 0.08f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        customBody = preset
                                        syncSettings()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = preset,
                                    color = if (customBody == preset) signalOrange else inkDim,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section 5: Icon Selection
            Text(
                text = stringResource(R.string.shortcut_notif_icon_header),
                color = signalOrange,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Option 1: Native App Icon
                IconOptionCard(
                    title = stringResource(R.string.shortcut_notif_icon_app),
                    subtitle = stringResource(R.string.shortcut_notif_icon_app_desc),
                    isSelected = selectedIconType == ShortcutNotificationPreferences.ICON_TYPE_APP,
                    accentColor = signalOrange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedIconType = ShortcutNotificationPreferences.ICON_TYPE_APP
                        syncSettings()
                    }
                ) {
                    if (currentAppInfo?.iconBitmap != null) {
                        Image(
                            bitmap = currentAppInfo.iconBitmap,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            tint = signalOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Option 2: Orbit Icon
                IconOptionCard(
                    title = stringResource(R.string.shortcut_notif_icon_orbit),
                    subtitle = stringResource(R.string.shortcut_notif_icon_orbit_desc),
                    isSelected = selectedIconType == ShortcutNotificationPreferences.ICON_TYPE_ORBIT,
                    accentColor = signalOrange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedIconType = ShortcutNotificationPreferences.ICON_TYPE_ORBIT
                        syncSettings()
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bubble_atom_core),
                        contentDescription = null,
                        tint = signalOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Option 3: Minimal Glyph
                IconOptionCard(
                    title = stringResource(R.string.shortcut_notif_icon_minimal),
                    subtitle = stringResource(R.string.shortcut_notif_icon_minimal_desc),
                    isSelected = selectedIconType == ShortcutNotificationPreferences.ICON_TYPE_MINIMAL,
                    accentColor = signalOrange,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        selectedIconType = ShortcutNotificationPreferences.ICON_TYPE_MINIMAL
                        syncSettings()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Launch,
                        contentDescription = null,
                        tint = signalOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Section 6: Primary Save Button
            Button(
                onClick = {
                    // Persist all current configurations
                    ShortcutNotificationPreferences.saveAll(
                        context = context,
                        enabled = isEnabled,
                        ongoing = isOngoing,
                        pkg = targetPackage,
                        appName = targetAppName,
                        title = customTitle,
                        body = customBody,
                        iconType = selectedIconType
                    )
                    if (bannerBitmap != null) {
                        ShortcutNotificationPreferences.saveBannerBitmap(context, bannerBitmap!!)
                    } else {
                        ShortcutNotificationPreferences.deleteBannerImage(context)
                    }

                    // Instantly apply / update the persistent notification
                    if (isEnabled) {
                        ShortcutNotificationManager.syncServiceState(context)
                    }

                    Toast.makeText(
                        context,
                        context.getString(R.string.shortcut_notif_saved_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = signalOrange),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.shortcut_notif_save_btn),
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    // App Picker Bottom Sheet / Dialog
    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            selectedPackage = targetPackage,
            onDismiss = { showAppPicker = false },
            onSelectApp = { app ->
                targetPackage = app.packageName
                val previousAppName = targetAppName
                targetAppName = app.label
                // If title was empty or was using previous app name, update to new app name
                if (customTitle.isBlank() || customTitle == previousAppName || customTitle == "Quick Launch" || customTitle == "App Shortcut") {
                    customTitle = app.label
                }
                ShortcutNotificationPreferences.setTargetApp(context, app.packageName, app.label)
                ShortcutNotificationPreferences.setTitle(context, customTitle)
                ShortcutNotificationPreferences.setBody(context, customBody)
                syncSettings()
                showAppPicker = false
                Toast.makeText(context, "${app.label} set as shortcut target", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Image Cropper Tool Dialog
    if (showCropDialog && pendingCropUri != null) {
        NotificationImageCropperDialog(
            imageUri = pendingCropUri!!,
            accentColor = signalOrange,
            onDismiss = {
                showCropDialog = false
                pendingCropUri = null
            },
            onCropped = { croppedBitmap ->
                val saved = ShortcutNotificationPreferences.saveBannerBitmap(context, croppedBitmap)
                if (saved) {
                    bannerBitmap = croppedBitmap
                    bannerVersion = ShortcutNotificationPreferences.getBannerVersion(context)
                    if (isEnabled) {
                        ShortcutNotificationManager.syncServiceState(context)
                    }
                    Toast.makeText(context, "Cropped background applied!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save cropped image", Toast.LENGTH_SHORT).show()
                }
                showCropDialog = false
                pendingCropUri = null
            }
        )
    }
}

/**
 * High-fidelity representation of the Android System Notification Shade
 * with Full Background / Media Cover image styling.
 */
@Composable
fun NotificationLivePreviewCard(
    targetAppName: String,
    title: String,
    body: String,
    iconType: String,
    isOngoing: Boolean,
    currentAppInfo: AppInfo?,
    bannerBitmap: Bitmap? = null,
    accentColor: Color
) {
    val shadeBg = Color(0xFF1B202E)
    val textColor = Color(0xFFF1F3F9)
    val textDim = Color(0xFF949CB2)

    val textShadow = if (bannerBitmap != null) {
        androidx.compose.ui.graphics.Shadow(
            color = Color.Black.copy(alpha = 0.95f),
            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
            blurRadius = 6f
        )
    } else {
        androidx.compose.ui.graphics.Shadow.None
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (bannerBitmap != null) Color.Black else shadeBg),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = if (bannerBitmap != null) 0.18f else 0.08f))
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Full Background Image (Media Cover style)
            if (bannerBitmap != null) {
                Image(
                    bitmap = bannerBitmap.asImageBitmap(),
                    contentDescription = "Notification Background Artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )

                // Dark Contrast Scrim to guarantee sufficient readability
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.52f),
                                    Color.Black.copy(alpha = 0.82f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Notification Shade Header: App source + timestamp + lock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Orbit small icon
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bubble_atom_core),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(14.dp)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = stringResource(R.string.app_name),
                        color = if (bannerBitmap != null) Color.White.copy(alpha = 0.85f) else textDim,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                    )

                    Text(
                        text = " • ",
                        color = if (bannerBitmap != null) Color.White.copy(alpha = 0.85f) else textDim,
                        fontSize = 11.5.sp
                    )

                    Text(
                        text = stringResource(R.string.shortcut_notif_shade_label),
                        color = if (bannerBitmap != null) Color.White.copy(alpha = 0.85f) else textDim,
                        fontSize = 11.5.sp,
                        style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                    )

                    Text(
                        text = " • now",
                        color = if (bannerBitmap != null) Color.White.copy(alpha = 0.65f) else textDim.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (bannerBitmap != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.22f))
                                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Cover Art",
                                color = accentColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    if (isOngoing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = if (bannerBitmap != null) 0.15f else 0.05f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.shortcut_notif_ongoing_toggle),
                                tint = accentColor,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.shortcut_notif_ongoing_chip),
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main Notification Content Row: App Icon + App Name (Title) overlay
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Large Icon Preview
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (bannerBitmap != null) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.05f))
                            .border(1.dp, Color.White.copy(alpha = if (bannerBitmap != null) 0.25f else 0.08f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (iconType) {
                            ShortcutNotificationPreferences.ICON_TYPE_APP -> {
                                if (currentAppInfo?.iconBitmap != null) {
                                    Image(
                                        bitmap = currentAppInfo.iconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier.size(34.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Apps,
                                        contentDescription = null,
                                        tint = accentColor,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            ShortcutNotificationPreferences.ICON_TYPE_ORBIT -> {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_bubble_atom_core),
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            ShortcutNotificationPreferences.ICON_TYPE_MINIMAL -> {
                                Icon(
                                    imageVector = Icons.Default.Launch,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Title and Body overlaid with high-contrast text styling
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = textColor,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                        if (body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = body,
                                color = if (bannerBitmap != null) Color.White.copy(alpha = 0.9f) else textDim,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                            )
                        }
                    }

                    // Mini Chevron / Action Indicator
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = if (bannerBitmap != null) Color.White.copy(alpha = 0.85f) else textDim.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun IconOptionCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    iconContent: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) accentColor.copy(alpha = 0.15f) else Color(0xFF151D33))
            .border(
                1.dp,
                if (isSelected) accentColor else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                iconContent()
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = if (isSelected) Color.White else Color(0xFF8E94A8),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF5A6178),
                fontSize = 10.sp
            )
        }
    }
}

/**
 * Search-enabled modal dialog for selecting any installed application.
 */
@Composable
fun AppPickerDialog(
    apps: List<AppInfo>,
    selectedPackage: String,
    onDismiss: () -> Unit,
    onSelectApp: (AppInfo) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val signalOrange = Color(0xFFFF6B35)
    val inkLight = Color(0xFFEEF0F6)
    val inkDim = Color(0xFF5A6178)
    val cardBg = Color(0xFF151D33)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color.White.copy(alpha = 0.08f)))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    // Dialog Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.shortcut_notif_select_app_title),
                                color = inkLight,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${apps.size} installed applications",
                                color = inkDim,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear),
                                tint = inkLight
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(text = stringResource(R.string.shortcut_notif_search_apps_hint), color = inkDim) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = signalOrange
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.clear),
                                        tint = inkDim
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = signalOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = signalOrange
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // App List
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = app.packageName == selectedPackage
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) signalOrange.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.02f))
                                    .border(
                                        1.dp,
                                        if (isSelected) signalOrange else Color.White.copy(alpha = 0.05f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelectApp(app) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.iconBitmap != null) {
                                    Image(
                                        bitmap = app.iconBitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(signalOrange.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Apps,
                                            contentDescription = null,
                                            tint = signalOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        color = Color.White,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = app.packageName,
                                        color = inkDim,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = signalOrange,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
