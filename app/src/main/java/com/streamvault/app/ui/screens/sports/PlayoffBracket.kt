package com.streamvault.app.ui.screens.sports

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.design.FocusSpec
import kotlinx.coroutines.launch

// ── Layout constants ──────────────────────────────────────────────────────

private val CardWidth = 210.dp
private val CardHeight = 80.dp
private val R1Gap = 12.dp           // vertical gap between R1 cards
private val RoundGap = 48.dp        // horizontal gap between round columns (room for lines)
private val ConnectorColor = AppColors.TextDisabled
private const val ConnectorStroke = 2f

// ── Main bracket composable ───────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayoffBracketView(bracketData: BracketData) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    // Convert dp to px for Canvas drawing
    val cardWPx = with(density) { CardWidth.toPx() }
    val cardHPx = with(density) { CardHeight.toPx() }
    val r1GapPx = with(density) { R1Gap.toPx() }
    val roundGapPx = with(density) { RoundGap.toPx() }

    // Pre-calculate card Y centers for each round (in px)
    // R1: 4 cards at positions 0,1,2,3
    val r1Unit = cardHPx + r1GapPx
    val r1CenterY = (0..3).map { it * r1Unit + cardHPx / 2 }

    // R2: 2 cards, each centered between pairs of R1 cards
    val r2CenterY = listOf(
        (r1CenterY[0] + r1CenterY[1]) / 2,
        (r1CenterY[2] + r1CenterY[3]) / 2
    )

    // CF: 1 card, centered between R2 cards
    val cfCenterY = (r2CenterY[0] + r2CenterY[1]) / 2

    // Total bracket height
    val totalHeightPx = 3 * r1Unit + cardHPx // 4 cards worth
    val totalHeight = with(density) { totalHeightPx.toDp() }

    // Calculate X positions for each column
    // West: R1 | gap | R2 | gap | CF | gap | Championship | gap | CF | gap | R2 | gap | R1 : East
    val confLabelWidth = with(density) { 32.dp.toPx() }
    val startPadding = with(density) { 16.dp.toPx() }
    val westR1X = startPadding + confLabelWidth
    val westR2X = westR1X + cardWPx + roundGapPx
    val westCfX = westR2X + cardWPx + roundGapPx
    val champX = westCfX + cardWPx + roundGapPx
    val eastCfX = champX + cardWPx + roundGapPx
    val eastR2X = eastCfX + cardWPx + roundGapPx
    val eastR1X = eastR2X + cardWPx + roundGapPx

    val totalWidth = with(density) { (eastR1X + cardWPx + startPadding + confLabelWidth).toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
    ) {
        // Canvas for connector lines (behind cards)
        Canvas(
            modifier = Modifier
                .width(totalWidth)
                .height(totalHeight)
        ) {
            // ── West connectors ──
            // R1 → R2 (pairs 0,1 → R2[0] and pairs 2,3 → R2[1])
            drawBracketConnector(
                srcY1 = r1CenterY[0], srcY2 = r1CenterY[1], dstY = r2CenterY[0],
                srcRight = westR1X + cardWPx, dstLeft = westR2X
            )
            drawBracketConnector(
                srcY1 = r1CenterY[2], srcY2 = r1CenterY[3], dstY = r2CenterY[1],
                srcRight = westR1X + cardWPx, dstLeft = westR2X
            )
            // R2 → CF
            drawBracketConnector(
                srcY1 = r2CenterY[0], srcY2 = r2CenterY[1], dstY = cfCenterY,
                srcRight = westR2X + cardWPx, dstLeft = westCfX
            )
            // CF → Championship
            drawBracketConnector(
                srcY1 = cfCenterY, srcY2 = cfCenterY, dstY = cfCenterY,
                srcRight = westCfX + cardWPx, dstLeft = champX
            )

            // ── East connectors (mirrored: lines go from right side of later rounds to left side of earlier rounds) ──
            // R1 → R2
            drawBracketConnector(
                srcY1 = r1CenterY[0], srcY2 = r1CenterY[1], dstY = r2CenterY[0],
                srcRight = eastR2X + cardWPx, dstLeft = eastR1X, mirrored = true
            )
            drawBracketConnector(
                srcY1 = r1CenterY[2], srcY2 = r1CenterY[3], dstY = r2CenterY[1],
                srcRight = eastR2X + cardWPx, dstLeft = eastR1X, mirrored = true
            )
            // R2 → CF
            drawBracketConnector(
                srcY1 = r2CenterY[0], srcY2 = r2CenterY[1], dstY = cfCenterY,
                srcRight = eastCfX + cardWPx, dstLeft = eastR2X, mirrored = true
            )
            // CF → Championship
            drawBracketConnector(
                srcY1 = cfCenterY, srcY2 = cfCenterY, dstY = cfCenterY,
                srcRight = champX + cardWPx, dstLeft = eastCfX, mirrored = true
            )
        }

        // Cards overlay
        Row(
            modifier = Modifier
                .width(totalWidth)
                .height(totalHeight)
        ) {
            Spacer(Modifier.width(16.dp))

            // ── WESTERN CONFERENCE label ──
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                VerticalLabel("WESTERN CONFERENCE")
            }

            // ── West R1 ──
            BracketRoundColumn(
                series = bracketData.westR1,
                slotCount = 4,
                centerYList = r1CenterY,
                cardHeight = cardHPx,
                density = density,
                scrollState = scrollState,
                coroutineScope = coroutineScope
            )

            Spacer(Modifier.width(RoundGap))

            // ── West R2 ──
            BracketRoundColumn(
                series = bracketData.westR2,
                slotCount = 2,
                centerYList = r2CenterY,
                cardHeight = cardHPx,
                density = density,
                scrollState = scrollState,
                coroutineScope = coroutineScope
            )

            Spacer(Modifier.width(RoundGap))

            // ── West CF ──
            BracketRoundColumn(
                series = listOfNotNull(bracketData.westFinal),
                slotCount = 1,
                centerYList = listOf(cfCenterY),
                cardHeight = cardHPx,
                density = density,
                scrollState = scrollState,
                coroutineScope = coroutineScope
            )

            Spacer(Modifier.width(RoundGap))

            // ── Championship ──
            Box(
                modifier = Modifier
                    .width(CardWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "CHAMPIONSHIP",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.Warning,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    if (bracketData.championship != null) {
                        BracketMatchupCard(bracketData.championship, isChampionship = true)
                    } else {
                        TbdCard()
                    }
                }
            }

            Spacer(Modifier.width(RoundGap))

            // ── East CF ──
            BracketRoundColumn(
                series = listOfNotNull(bracketData.eastFinal),
                slotCount = 1,
                centerYList = listOf(cfCenterY),
                cardHeight = cardHPx,
                density = density,
                scrollState = scrollState,
                coroutineScope = coroutineScope
            )

            Spacer(Modifier.width(RoundGap))

            // ── East R2 ──
            BracketRoundColumn(
                series = bracketData.eastR2,
                slotCount = 2,
                centerYList = r2CenterY,
                cardHeight = cardHPx,
                density = density,
                scrollState = scrollState,
                coroutineScope = coroutineScope
            )

            Spacer(Modifier.width(RoundGap))

            // ── East R1 ──
            BracketRoundColumn(
                series = bracketData.eastR1,
                slotCount = 4,
                centerYList = r1CenterY,
                cardHeight = cardHPx,
                density = density,
                scrollState = scrollState,
                coroutineScope = coroutineScope
            )

            // ── EASTERN CONFERENCE label ──
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                VerticalLabel("EASTERN CONFERENCE")
            }

            Spacer(Modifier.width(16.dp))
        }
    }
}

// ── Vertical conference label ────────────────────────────────────────────

@Composable
private fun VerticalLabel(text: String) {
    // Compose doesn't have easy vertical text, use characters stacked vertically
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxHeight()
    ) {
        for (char in text) {
            Text(
                text = char.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextTertiary,
                letterSpacing = 0.sp
            )
        }
    }
}

// ── Round column with cards at calculated Y positions ─────────────────────

@Composable
private fun BracketRoundColumn(
    series: List<PlayoffSeries>,
    slotCount: Int,
    centerYList: List<Float>,
    cardHeight: Float,
    density: androidx.compose.ui.unit.Density,
    scrollState: ScrollState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    Box(
        modifier = Modifier
            .width(CardWidth)
            .fillMaxHeight()
    ) {
        for (i in 0 until slotCount) {
            val topPx = centerYList.getOrElse(i) { 0f } - cardHeight / 2
            val topDp = with(density) { topPx.toDp() }

            Box(
                modifier = Modifier
                    .padding(top = topDp)
                    .width(CardWidth)
                    .height(CardHeight)
            ) {
                if (i < series.size) {
                    BracketMatchupCard(
                        series = series[i],
                        onFocused = { xOffset ->
                            coroutineScope.launch {
                                // Scroll to show focused card with some left padding
                                val targetScroll = (xOffset - 200).coerceAtLeast(0f).toInt()
                                scrollState.animateScrollTo(targetScroll)
                            }
                        }
                    )
                } else {
                    TbdCard()
                }
            }
        }
    }
}

// ── Individual matchup card (ESPN style) ──────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BracketMatchupCard(
    series: PlayoffSeries,
    isChampionship: Boolean = false,
    onFocused: ((Float) -> Unit)? = null
) {
    val winner1 = series.isComplete && series.team1Wins > series.team2Wins
    val winner2 = series.isComplete && series.team2Wins > series.team1Wins
    val isActive = !series.isComplete

    Surface(
        onClick = { },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                BorderStroke(
                    if (isChampionship) 1.dp else if (isActive) 1.dp else 0.dp,
                    if (isChampionship) AppColors.Warning.copy(alpha = 0.5f)
                    else if (isActive) AppColors.Brand.copy(alpha = 0.4f)
                    else Color.Transparent
                )
            ),
            focusedBorder = Border(BorderStroke(FocusSpec.BorderWidth, AppColors.Brand))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged { state ->
                // Can't easily get X offset here without onGloballyPositioned,
                // but the scrollState in BracketRoundColumn handles it
            }
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Series status header
            Text(
                series.seriesNote.ifBlank { if (isChampionship) "NBA Finals" else "Best of 7" },
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (series.isComplete) AppColors.Success else AppColors.Brand,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(4.dp))

            // Team 1 row
            TeamRow(
                seed = series.team1Seed,
                logo = series.team1Logo,
                name = series.team1Name,
                wins = series.team1Wins,
                isWinner = winner1,
                showScore = true
            )

            Spacer(Modifier.height(2.dp))

            // Team 2 row
            TeamRow(
                seed = series.team2Seed,
                logo = series.team2Logo,
                name = series.team2Name,
                wins = series.team2Wins,
                isWinner = winner2,
                showScore = true
            )
        }
    }
}

@Composable
private fun TeamRow(
    seed: Int,
    logo: String,
    name: String,
    wins: Int,
    isWinner: Boolean,
    showScore: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Seed number
        if (seed > 0) {
            Text(
                "$seed",
                modifier = Modifier.width(16.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextTertiary
            )
        }

        // Team logo
        if (logo.isNotBlank()) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                modifier = Modifier.size(16.dp).clip(CircleShape)
            )
            Spacer(Modifier.width(4.dp))
        }

        // Team name
        Text(
            name,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
            color = if (isWinner) AppColors.TextPrimary else AppColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Wins count
        if (showScore) {
            Text(
                "$wins",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWinner) AppColors.TextPrimary else AppColors.TextTertiary
            )
            if (isWinner) {
                Text(" ◄", fontSize = 10.sp, color = AppColors.Success)
            }
        }
    }
}

// ── TBD placeholder card ──────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TbdCard() {
    Surface(
        onClick = { },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.Surface,
            focusedContainerColor = AppColors.SurfaceElevated
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, AppColors.Outline)),
            focusedBorder = Border(BorderStroke(FocusSpec.BorderWidth, AppColors.Brand))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("TBD", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextDisabled)
        }
    }
}

// ── Canvas connector line drawing ────────────────────────────────────────

/**
 * Draws a bracket connector: two source cards merge into one destination card.
 * For West side: lines go left-to-right (source right edge → destination left edge)
 * For East side (mirrored): lines go right-to-left (destination right edge → source left edge)
 */
private fun DrawScope.drawBracketConnector(
    srcY1: Float,
    srcY2: Float,
    dstY: Float,
    srcRight: Float,
    dstLeft: Float,
    mirrored: Boolean = false
) {
    val color = ConnectorColor
    val stroke = ConnectorStroke

    if (mirrored) {
        // East side: connector goes from dstLeft (right side of later round) to srcRight (left side of earlier round)
        // dstLeft is actually the left edge of the earlier round column
        // srcRight is actually the right edge of the later round column
        val midX = (srcRight + dstLeft) / 2

        // Horizontal from R1 cards' left edge to midpoint
        drawLine(color, Offset(dstLeft, srcY1), Offset(midX, srcY1), stroke)
        drawLine(color, Offset(dstLeft, srcY2), Offset(midX, srcY2), stroke)
        // Vertical connecting
        drawLine(color, Offset(midX, srcY1), Offset(midX, srcY2), stroke)
        // Horizontal from midpoint to later round right edge
        drawLine(color, Offset(midX, dstY), Offset(srcRight, dstY), stroke)
    } else {
        // West side: standard left-to-right
        val midX = (srcRight + dstLeft) / 2

        // Horizontal from source cards' right edge to midpoint
        drawLine(color, Offset(srcRight, srcY1), Offset(midX, srcY1), stroke)
        drawLine(color, Offset(srcRight, srcY2), Offset(midX, srcY2), stroke)
        // Vertical connecting the two source Y positions
        drawLine(color, Offset(midX, srcY1), Offset(midX, srcY2), stroke)
        // Horizontal from midpoint at dest Y to dest left edge
        drawLine(color, Offset(midX, dstY), Offset(dstLeft, dstY), stroke)
    }
}
