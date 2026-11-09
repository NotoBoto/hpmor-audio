package io.github.notoboto.unison.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

/*
 * Material motion as used by Mihon:
 *  - bottom tabs: "fade through", 200 ms (old content fades out fast, new one fades in with a slight scale-up);
 *  - pushing/popping screens: "shared axis X", 300 ms (30 dp horizontal slide + short cross-fade).
 */
private const val TAB_MS = 200
private const val AXIS_MS = 300
private const val SLIDE_PX = 90   // ≈ 30 dp on a typical phone

fun fadeThroughIn(): EnterTransition =
    fadeIn(tween((TAB_MS * 0.65).toInt(), delayMillis = (TAB_MS * 0.35).toInt(), easing = LinearOutSlowInEasing)) +
        scaleIn(tween((TAB_MS * 0.65).toInt(), delayMillis = (TAB_MS * 0.35).toInt(), easing = LinearOutSlowInEasing), initialScale = 0.92f)

fun fadeThroughOut(): ExitTransition = fadeOut(tween((TAB_MS * 0.35).toInt(), easing = FastOutLinearInEasing))

fun sharedAxisXIn(forward: Boolean): EnterTransition =
    slideInHorizontally(tween(AXIS_MS, easing = FastOutSlowInEasing)) { if (forward) SLIDE_PX else -SLIDE_PX } +
        fadeIn(tween(AXIS_MS * 2 / 3, delayMillis = AXIS_MS / 3, easing = LinearOutSlowInEasing))

fun sharedAxisXOut(forward: Boolean): ExitTransition =
    slideOutHorizontally(tween(AXIS_MS, easing = FastOutSlowInEasing)) { if (forward) -SLIDE_PX else SLIDE_PX } +
        fadeOut(tween(AXIS_MS / 3, easing = FastOutLinearInEasing))
