package com.example.tutorial

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Steps in the live interactive tour taking the user from place to place across Orbit.
 */
enum class TourStep(
    val stepIndex: Int,
    val totalSteps: Int = 4
) {
    ACTIVATE_ORBIT(1),
    ORBIT_RING_FEATURES(2),
    FIND_SHORTCUT_SECTION(3),
    PIN_SYSTEM_SHORTCUTS(4)
}

/**
 * A live interactive guide overlay that visibly navigates the user through the app,
 * explaining Orbit activation and System Shade Shortcuts step by step.
 */
@Composable
fun LiveTourGuideOverlay(
    currentStep: TourStep,
    isServiceRunning: Boolean,
    onStepChange: (TourStep) -> Unit,
    onDismissTour: () -> Unit,
    modifier: Modifier = Modifier
) {
    val signalOrange = Color(0xFFFF6B35)
    val electricBlue = Color(0xFF3D9BFF)
    val inkLight = Color(0xFFEEF0F6)
    val inkDim = Color(0xFF8E94A8)
    val cardBackground = Color(0xFF0F1424)

    // Pulsing halo animation for visual spotlight
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(interactionSource = null, indication = null) { /* Block touches outside card */ }
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = when (currentStep) {
            TourStep.ACTIVATE_ORBIT -> Alignment.Center
            TourStep.ORBIT_RING_FEATURES -> Alignment.Center
            TourStep.FIND_SHORTCUT_SECTION -> Alignment.Center
            TourStep.PIN_SYSTEM_SHORTCUTS -> Alignment.Center
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(cardBackground)
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(signalOrange.copy(alpha = 0.8f), electricBlue.copy(alpha = 0.35f))
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(22.dp)
        ) {
            // Top Bar: Step pills and Skip button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Progress Indicators (4 steps)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..4).forEach { stepNum ->
                        val isActive = stepNum == currentStep.stepIndex
                        val isPast = stepNum < currentStep.stepIndex
                        Box(
                            modifier = Modifier
                                .width(if (isActive) 24.dp else 10.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        isActive -> signalOrange
                                        isPast -> electricBlue
                                        else -> Color.White.copy(alpha = 0.15f)
                                    }
                                )
                        )
                    }
                }

                TextButton(
                    onClick = onDismissTour,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.live_tour_skip),
                        color = inkDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Step Badge & Icon Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(
                            when (currentStep) {
                                TourStep.ACTIVATE_ORBIT -> signalOrange.copy(alpha = pulseAlpha * 0.3f)
                                TourStep.ORBIT_RING_FEATURES -> electricBlue.copy(alpha = pulseAlpha * 0.3f)
                                TourStep.FIND_SHORTCUT_SECTION -> signalOrange.copy(alpha = pulseAlpha * 0.3f)
                                TourStep.PIN_SYSTEM_SHORTCUTS -> electricBlue.copy(alpha = pulseAlpha * 0.3f)
                            }
                        )
                        .border(
                            1.dp,
                            if (currentStep == TourStep.ACTIVATE_ORBIT || currentStep == TourStep.FIND_SHORTCUT_SECTION)
                                signalOrange.copy(alpha = pulseAlpha)
                            else
                                electricBlue.copy(alpha = pulseAlpha),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (currentStep) {
                            TourStep.ACTIVATE_ORBIT -> Icons.Default.PlayCircle
                            TourStep.ORBIT_RING_FEATURES -> Icons.Default.RadioButtonChecked
                            TourStep.FIND_SHORTCUT_SECTION -> Icons.Default.AltRoute
                            TourStep.PIN_SYSTEM_SHORTCUTS -> Icons.Default.NotificationsActive
                        },
                        contentDescription = null,
                        tint = if (currentStep == TourStep.ACTIVATE_ORBIT || currentStep == TourStep.FIND_SHORTCUT_SECTION) signalOrange else electricBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = when (currentStep) {
                            TourStep.ACTIVATE_ORBIT -> stringResource(id = R.string.live_tour_step1_badge)
                            TourStep.ORBIT_RING_FEATURES -> stringResource(id = R.string.live_tour_step2_badge)
                            TourStep.FIND_SHORTCUT_SECTION -> stringResource(id = R.string.live_tour_step3_badge)
                            TourStep.PIN_SYSTEM_SHORTCUTS -> stringResource(id = R.string.live_tour_step4_badge)
                        },
                        color = signalOrange,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when (currentStep) {
                            TourStep.ACTIVATE_ORBIT -> stringResource(id = R.string.live_tour_step1_title)
                            TourStep.ORBIT_RING_FEATURES -> stringResource(id = R.string.live_tour_step2_title)
                            TourStep.FIND_SHORTCUT_SECTION -> stringResource(id = R.string.live_tour_step3_title)
                            TourStep.PIN_SYSTEM_SHORTCUTS -> stringResource(id = R.string.live_tour_step4_title)
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Informational Description
            Text(
                text = when (currentStep) {
                    TourStep.ACTIVATE_ORBIT -> stringResource(id = R.string.live_tour_step1_desc)
                    TourStep.ORBIT_RING_FEATURES -> stringResource(id = R.string.live_tour_step2_desc)
                    TourStep.FIND_SHORTCUT_SECTION -> stringResource(id = R.string.live_tour_step3_desc)
                    TourStep.PIN_SYSTEM_SHORTCUTS -> stringResource(id = R.string.live_tour_step4_desc)
                },
                color = inkLight.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 18.5.sp
            )

            // Step-Specific Live Context Cards
            when (currentStep) {
                TourStep.ACTIVATE_ORBIT -> {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isServiceRunning) Color(0x1800E676) else Color(0x18FF6B35))
                            .border(1.dp, if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.4f) else signalOrange.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isServiceRunning) Color(0xFF00E676) else signalOrange)
                            )
                            Text(
                                text = if (isServiceRunning) stringResource(id = R.string.live_tour_step1_active) else stringResource(id = R.string.live_tour_step1_inactive),
                                color = if (isServiceRunning) Color(0xFF00E676) else signalOrange,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                TourStep.FIND_SHORTCUT_SECTION -> {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, electricBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = electricBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.shortcut_tutorial_where_path),
                                color = electricBlue,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                TourStep.PIN_SYSTEM_SHORTCUTS -> {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.05f))
                            .border(1.dp, signalOrange.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = signalOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(id = R.string.live_tour_step4_callout),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Buttons Row: Back, Next/Finish
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep.stepIndex > 1) {
                    OutlinedButton(
                        onClick = {
                            val prevStep = when (currentStep) {
                                TourStep.ACTIVATE_ORBIT -> TourStep.ACTIVATE_ORBIT
                                TourStep.ORBIT_RING_FEATURES -> TourStep.ACTIVATE_ORBIT
                                TourStep.FIND_SHORTCUT_SECTION -> TourStep.ORBIT_RING_FEATURES
                                TourStep.PIN_SYSTEM_SHORTCUTS -> TourStep.FIND_SHORTCUT_SECTION
                            }
                            onStepChange(prevStep)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = inkLight),
                        border = ButtonDefaults.outlinedButtonBorder().copy(
                            brush = Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.2f), Color.White.copy(alpha = 0.1f)))
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.live_tour_back),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Button(
                    onClick = {
                        if (currentStep == TourStep.PIN_SYSTEM_SHORTCUTS) {
                            onDismissTour()
                        } else {
                            val nextStep = when (currentStep) {
                                TourStep.ACTIVATE_ORBIT -> TourStep.ORBIT_RING_FEATURES
                                TourStep.ORBIT_RING_FEATURES -> TourStep.FIND_SHORTCUT_SECTION
                                TourStep.FIND_SHORTCUT_SECTION -> TourStep.PIN_SYSTEM_SHORTCUTS
                                TourStep.PIN_SYSTEM_SHORTCUTS -> TourStep.PIN_SYSTEM_SHORTCUTS
                            }
                            onStepChange(nextStep)
                        }
                    },
                    modifier = Modifier.weight(if (currentStep.stepIndex > 1) 1.5f else 1f),
                    colors = ButtonDefaults.buttonColors(containerColor = signalOrange),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        text = if (currentStep == TourStep.PIN_SYSTEM_SHORTCUTS) {
                            stringResource(id = R.string.live_tour_finish)
                        } else {
                            stringResource(id = R.string.live_tour_next)
                        },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (currentStep == TourStep.PIN_SYSTEM_SHORTCUTS) Icons.Default.Check else Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                }
            }
        }
    }
}
