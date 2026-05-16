package com.streamvault.app.ui.screens.sports

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.streamvault.app.R
import com.streamvault.app.ui.components.shell.AppNavigationChrome
import com.streamvault.app.ui.components.shell.AppScreenScaffold
import android.content.Intent
import android.net.Uri
import com.streamvault.app.ui.design.AppColors
import com.streamvault.app.ui.design.FocusSpec

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: SportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = stringResource(R.string.nav_sports),
        subtitle = null,
        navigationChrome = AppNavigationChrome.TopBar,
        compactHeader = true,
        showScreenHeader = false
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // League + View pills
            LazyRow(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                val leagues = listOf("NBA", "NHL", "MLB", "NFL", "MLS")
                items(leagues) { league ->
                    Pill(
                        text = league,
                        selected = league.lowercase() == uiState.league,
                        selectedColor = AppColors.Brand,
                        onClick = { viewModel.selectLeague(league.lowercase()) },
                        iconUrl = "https://a.espncdn.com/i/teamlogos/leagues/500/${league.lowercase()}.png"
                    )
                }
                item { Spacer(Modifier.width(4.dp)) }
                val views = listOf("Today" to "today", "Standings" to "standings", "Playoffs" to "playoffs")
                items(views) { (label, view) ->
                    Pill(
                        text = label,
                        selected = view == uiState.view,
                        selectedColor = AppColors.Brand,
                        onClick = { viewModel.selectView(view) }
                    )
                }
            }

            // Content
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading...", color = AppColors.TextTertiary, fontSize = 18.sp)
                    }
                }
                uiState.boxScore != null -> BoxScoreView(uiState.boxScore!!, onBack = { viewModel.clearBoxScore() })
                uiState.view == "today" -> TodayView(uiState, viewModel, uriHandler, context)
                uiState.view == "standings" -> StandingsView(uiState)
                uiState.view == "playoffs" -> PlayoffsView(uiState, viewModel)
            }
        }
    }
}

// ── Reusable Pill ──────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(text: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit, iconUrl: String? = null) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) selectedColor else AppColors.SurfaceElevated,
            contentColor = if (selected) AppColors.TextPrimary else AppColors.TextSecondary,
            focusedContainerColor = if (selected) selectedColor else AppColors.SurfaceEmphasis
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FocusSpec.FocusedScale),
        modifier = Modifier.height(36.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (iconUrl != null) {
                AsyncImage(model = iconUrl, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Today View ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TodayView(uiState: SportsUiState, viewModel: SportsViewModel, uriHandler: androidx.compose.ui.platform.UriHandler, context: android.content.Context) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val live = uiState.games.filter { it.state == "in" }
        val pre = uiState.games.filter { it.state == "pre" }
        val post = uiState.games.filter { it.state == "post" }

        if (live.isNotEmpty()) { item { SectionLabel("LIVE", AppColors.Live) } }
        items(live, key = { "l_${it.eventId}" }) { game -> GameCard(game, viewModel, context) }
        if (pre.isNotEmpty()) { item { SectionLabel("UPCOMING", AppColors.Brand) } }
        items(pre, key = { "p_${it.eventId}" }) { game -> GameCard(game, viewModel, context) }
        if (post.isNotEmpty()) { item { SectionLabel("FINAL", AppColors.TextTertiary) } }
        items(post, key = { "f_${it.eventId}" }) { game -> GameCard(game, viewModel, context) }

        if (uiState.games.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                Text("No games today", color = AppColors.TextTertiary, fontSize = 18.sp)
            }}
        }

        // News
        if (uiState.news.isNotEmpty()) {
            item { SectionLabel("HEADLINES", AppColors.Brand) }
            items(uiState.news, key = { it.headline.hashCode() }) { article ->
                Surface(
                    onClick = { if (article.link.isNotBlank()) uriHandler.openUri(article.link) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = AppColors.Surface,
                        focusedContainerColor = AppColors.SurfaceEmphasis),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = FocusSpec.FocusedScale),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (article.imageUrl.isNotBlank()) {
                            AsyncImage(model = article.imageUrl, contentDescription = null,
                                modifier = Modifier.size(56.dp, 38.dp).clip(RoundedCornerShape(6.dp)))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(article.headline, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, color = AppColors.TextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(text, modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp),
        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
}

// ── Game Card (single focusable, click=watch/boxscore, long-press=options) ──

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameCard(game: SportsGame, viewModel: SportsViewModel, context: android.content.Context) {
    val isLive = game.state == "in"
    val isFinal = game.state == "post"
    var showMenu by remember { mutableStateOf(false) }

    // Build YouTube URL
    val ytUrl = remember(game.eventId) {
        val dateStr = try {
            val d = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).parse(game.date.take(16))
            if (d != null) java.text.SimpleDateFormat("MMM d yyyy", java.util.Locale.US).format(d) else ""
        } catch (_: Exception) { "" }
        "https://www.youtube.com/results?search_query=${java.net.URLEncoder.encode("${game.awayTeam} vs ${game.homeTeam} highlights $dateStr", "UTF-8")}"
    }

    Surface(
        onClick = {
            if (isFinal) viewModel.loadBoxScore(game.eventId)
            else viewModel.findAndWatchGame(game)
        },
        onLongClick = { showMenu = !showMenu },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (isLive) 2.dp else 0.dp, if (isLive) AppColors.Live else Color.Transparent)),
            focusedBorder = Border(BorderStroke(FocusSpec.BorderWidth, AppColors.Brand))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = FocusSpec.FocusedScale),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            // Teams + Score
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Away
                if (game.awayLogo.isNotBlank()) {
                    AsyncImage(model = game.awayLogo, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(game.awayTeam, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (game.awayRecord.isNotBlank()) Text(game.awayRecord, fontSize = 11.sp, color = AppColors.TextTertiary)
                }

                // Score or VS
                if (game.state != "pre") {
                    Text("${game.awayScore} - ${game.homeScore}",
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (isLive) AppColors.Success else AppColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp))
                } else {
                    Text("VS", fontSize = 14.sp, color = AppColors.TextTertiary, modifier = Modifier.padding(horizontal = 10.dp))
                }

                // Home
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(game.homeTeam, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    if (game.homeRecord.isNotBlank()) Text(game.homeRecord, fontSize = 11.sp, color = AppColors.TextTertiary)
                }
                if (game.homeLogo.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    AsyncImage(model = game.homeLogo, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape))
                }
            }

            // Status + Series
            Text(game.status, Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = when (game.state) { "in" -> AppColors.Live; "pre" -> AppColors.Brand; else -> AppColors.TextTertiary })
            if (game.seriesNote.isNotBlank()) {
                Text(game.seriesNote, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    fontSize = 11.sp, color = AppColors.Brand)
            }

            // Hint
            Text(
                if (isFinal) "Press for box score · Hold for highlights" else "Press to watch · Hold for options",
                Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
                fontSize = 11.sp, color = AppColors.TextDisabled
            )

            // Long-press menu
            if (showMenu) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    Pill("Watch", false, AppColors.Brand, onClick = {
                        viewModel.findAndWatchGame(game)
                        showMenu = false
                    })
                    Spacer(Modifier.width(6.dp))
                    Pill("Box Score", false, AppColors.SurfaceAccent, onClick = { viewModel.loadBoxScore(game.eventId); showMenu = false })
                    if (isFinal) {
                        Spacer(Modifier.width(6.dp))
                        Pill("Highlights", false, AppColors.Live, onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl))) } catch (_: Exception) {}
                            showMenu = false
                        })
                    }
                }
            }
        }
    }
}

// ── Standings View (side-by-side conferences) ──────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StandingsView(uiState: SportsUiState) {
    if (uiState.conferences.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No standings data", color = AppColors.TextTertiary)
        }
        return
    }

    if (uiState.conferences.size >= 2) {
        // Side-by-side
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (conf in uiState.conferences.take(2)) {
                ConferenceColumn(conf, Modifier.weight(1f))
            }
        }
    } else {
        // Single conference
        ConferenceColumn(uiState.conferences.first(), Modifier.fillMaxSize().padding(horizontal = 16.dp))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConferenceColumn(conf: StandingsConference, modifier: Modifier) {
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Header
        item(key = "hdr_${conf.name}") {
            Text(conf.name, modifier = Modifier.padding(bottom = 6.dp),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        }
        // Column labels
        item(key = "cols_${conf.name}") {
            Row(Modifier.fillMaxWidth().background(AppColors.SurfaceElevated, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).padding(vertical = 6.dp, horizontal = 6.dp)) {
                Text("#", Modifier.width(24.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary, textAlign = TextAlign.Center)
                Text("Team", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary)
                Text("W", Modifier.width(28.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary, textAlign = TextAlign.Center)
                Text("L", Modifier.width(28.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary, textAlign = TextAlign.Center)
                Text("STR", Modifier.width(32.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary, textAlign = TextAlign.Center)
            }
        }
        // Team rows
        items(conf.teams.size, key = { "t_${conf.name}_$it" }) { idx ->
            val team = conf.teams[idx]
            val isPlayoff = (team.seed.toIntOrNull() ?: 99) <= 8
            val isLast = idx == conf.teams.size - 1
            Surface(
                onClick = {},
                shape = ClickableSurfaceDefaults.shape(if (isLast) RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp) else RoundedCornerShape(0.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (idx % 2 == 0) AppColors.Surface else Color.Transparent,
                    focusedContainerColor = AppColors.SurfaceEmphasis
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = FocusSpec.FocusedScale),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(vertical = 5.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(team.seed.ifBlank { "${idx + 1}" }, Modifier.width(24.dp), fontSize = 12.sp, textAlign = TextAlign.Center,
                        fontWeight = if (isPlayoff) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPlayoff) AppColors.Brand else AppColors.TextTertiary)
                    if (team.logo.isNotBlank()) {
                        AsyncImage(model = team.logo, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(team.abbr, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                    Text(team.wins, Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.Center, color = AppColors.TextPrimary)
                    Text(team.losses, Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.Center, color = AppColors.TextPrimary)
                    Text(team.streak, Modifier.width(32.dp), fontSize = 11.sp, textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        color = if (team.streak.startsWith("W")) AppColors.Success else AppColors.Live)
                }
            }
        }
    }
}

// ── Playoffs View (delegates to PlayoffBracketView) ─────────────────────

@Composable
private fun PlayoffsView(uiState: SportsUiState, viewModel: SportsViewModel) {
    val bracket = uiState.bracketData

    if (bracket == null && uiState.playoffBracket.isEmpty() && !uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("${uiState.league.uppercase()} playoff data not available", color = AppColors.TextTertiary, fontSize = 18.sp)
        }
        return
    }

    if (bracket != null) {
        PlayoffBracketView(bracket)
    }
}

// ── Box Score View ─────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BoxScoreView(boxScore: BoxScoreData, onBack: () -> Unit) {
    // Show key stat columns: PTS, REB, AST, FG, 3PT, +/-
    val showCols = listOf("PTS", "REB", "AST", "FG", "3PT", "+/-")
    val colIndices = showCols.mapNotNull { col -> boxScore.statLabels.indexOf(col).takeIf { it >= 0 } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header with back + score + team logos
        item {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = AppColors.SurfaceElevated,
                    focusedContainerColor = AppColors.SurfaceEmphasis),
                scale = ClickableSurfaceDefaults.scale(focusedScale = FocusSpec.FocusedScale),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("< Back to Scores", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Brand)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (boxScore.awayLogo.isNotBlank()) {
                            AsyncImage(model = boxScore.awayLogo, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("${boxScore.awayAbbr}  ${boxScore.awayTotal}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary)
                        Text("  —  ", fontSize = 20.sp, color = AppColors.TextTertiary)
                        Text("${boxScore.homeTotal}  ${boxScore.homeAbbr}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary)
                        if (boxScore.homeLogo.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            AsyncImage(model = boxScore.homeLogo, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape))
                        }
                    }
                }
            }
        }

        // Quarter scores
        if (boxScore.homeQuarters.isNotEmpty()) {
            item {
                Surface(
                    onClick = {},
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated, focusedContainerColor = AppColors.SurfaceEmphasis),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text("", Modifier.weight(1f))
                            for (q in boxScore.homeQuarters.indices) {
                                Text(if (q < 4) "Q${q+1}" else "OT${q-3}", Modifier.width(32.dp), textAlign = TextAlign.Center,
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary)
                            }
                            Text("T", Modifier.width(36.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        }
                        for ((abbr, quarters, total) in listOf(
                            Triple(boxScore.awayAbbr, boxScore.awayQuarters, boxScore.awayTotal),
                            Triple(boxScore.homeAbbr, boxScore.homeQuarters, boxScore.homeTotal)
                        )) {
                            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                                Text(abbr, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
                                for (s in quarters) Text(s, Modifier.width(32.dp), textAlign = TextAlign.Center, fontSize = 13.sp, color = AppColors.TextPrimary)
                                Text(total, Modifier.width(36.dp), textAlign = TextAlign.Center, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = AppColors.TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // Team stats comparison
        item {
            Surface(
                onClick = {},
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated, focusedContainerColor = AppColors.SurfaceEmphasis),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("Stat", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        Text(boxScore.homeAbbr, Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        Text(boxScore.awayAbbr, Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                    }
                    for (stat in boxScore.stats) {
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(stat.name, Modifier.weight(1f), fontSize = 13.sp, color = AppColors.TextSecondary)
                            Text(stat.homeValue, Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            Text(stat.awayValue, Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        }
                    }
                }
            }
        }

        // Player stats — each player as its own focusable row for d-pad scrolling
        val teamData = listOf(
            BoxScoreTeamData(boxScore.homeAbbr, boxScore.homePlayers, boxScore.homeTotals, boxScore.homeLogo),
            BoxScoreTeamData(boxScore.awayAbbr, boxScore.awayPlayers, boxScore.awayTotals, boxScore.awayLogo)
        )
        for (td in teamData) {
            if (td.players.isEmpty()) continue
            val starters = td.players.filter { it.isStarter && !it.didNotPlay }
            val bench = td.players.filter { !it.isStarter && !it.didNotPlay }

            // Team header + column labels
            item(key = "hdr_${td.abbr}") {
                Column(Modifier.fillMaxWidth().background(AppColors.SurfaceElevated, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(12.dp)) {
                    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (td.logo.isNotBlank()) {
                            AsyncImage(model = td.logo, contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(td.abbr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                        Text("  STARTERS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary, letterSpacing = 1.sp)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text("Player", Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary)
                        for (i in colIndices) {
                            Text(boxScore.statLabels[i], Modifier.width(40.dp), textAlign = TextAlign.Center,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AppColors.TextTertiary)
                        }
                    }
                }
            }

            // Starter rows
            items(starters.size, key = { "s_${td.abbr}_$it" }) { idx ->
                PlayerStatRowFocusable(starters[idx], colIndices)
            }

            // Bench header
            if (bench.isNotEmpty()) {
                item(key = "bench_${td.abbr}") {
                    Column(Modifier.fillMaxWidth().background(AppColors.SurfaceElevated).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Divider))
                        Text("BENCH", Modifier.padding(top = 6.dp, bottom = 2.dp), fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, color = AppColors.TextTertiary, letterSpacing = 1.sp)
                    }
                }

                // Bench rows
                items(bench.size, key = { "b_${td.abbr}_$it" }) { idx ->
                    PlayerStatRowFocusable(bench[idx], colIndices)
                }
            }

            // Totals row
            if (td.totals.isNotEmpty()) {
                item(key = "tot_${td.abbr}") {
                    Column(Modifier.fillMaxWidth().background(AppColors.SurfaceElevated, RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Spacer(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Brand.copy(alpha = 0.3f)))
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text("TOTAL", Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            for (i in colIndices) {
                                Text(td.totals.getOrElse(i) { "" }, Modifier.width(40.dp), textAlign = TextAlign.Center,
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
                            }
                        }
                    }
                }
            }

            // Spacer between teams
            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

private data class BoxScoreTeamData(val abbr: String, val players: List<PlayerBoxScore>, val totals: List<String>, val logo: String)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerStatRowFocusable(player: PlayerBoxScore, colIndices: List<Int>) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(0.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            // Headshot
            if (player.headshot.isNotBlank()) {
                AsyncImage(model = player.headshot, contentDescription = null,
                    modifier = Modifier.size(36.dp).clip(CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            // Name + position
            Column(Modifier.weight(1f)) {
                Text(player.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${player.position} #${player.jersey}", fontSize = 10.sp, color = AppColors.TextTertiary)
            }
            // Stats
            for (i in colIndices) {
                Text(player.stats.getOrElse(i) { "-" }, Modifier.width(40.dp), textAlign = TextAlign.Center,
                    fontSize = 13.sp, color = AppColors.TextPrimary)
            }
        }
    }
}
