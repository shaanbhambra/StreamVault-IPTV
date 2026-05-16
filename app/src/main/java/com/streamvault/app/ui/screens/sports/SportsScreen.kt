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

private val Purple = Color(0xFF6C5CE7)
private val Green = Color(0xFF00B894)
private val Red = Color(0xFFE74C3C)
private val LightPurple = Color(0xFFA29BFE)
private val DimText = Color.White.copy(alpha = 0.5f)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: SportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

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
                        selectedColor = Purple,
                        onClick = { viewModel.selectLeague(league.lowercase()) }
                    )
                }
                item { Spacer(Modifier.width(4.dp)) }
                val views = listOf("Today" to "today", "Standings" to "standings", "Playoffs" to "playoffs")
                items(views) { (label, view) ->
                    Pill(
                        text = label,
                        selected = view == uiState.view,
                        selectedColor = LightPurple,
                        onClick = { viewModel.selectView(view) }
                    )
                }
            }

            // Content
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading...", color = DimText, fontSize = 16.sp)
                    }
                }
                uiState.boxScore != null -> BoxScoreView(uiState.boxScore!!, onBack = { viewModel.clearBoxScore() })
                uiState.view == "today" -> TodayView(uiState, viewModel, uriHandler)
                uiState.view == "standings" -> StandingsView(uiState)
                uiState.view == "playoffs" -> PlayoffsView(uiState, viewModel)
            }
        }
    }
}

// ── Reusable Pill ──────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun Pill(text: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) selectedColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = if (selected) selectedColor else Color.White.copy(alpha = 0.15f)
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier.height(36.dp)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, modifier = Modifier.padding(horizontal = 18.dp),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Today View ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TodayView(uiState: SportsUiState, viewModel: SportsViewModel, uriHandler: androidx.compose.ui.platform.UriHandler) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val live = uiState.games.filter { it.state == "in" }
        val pre = uiState.games.filter { it.state == "pre" }
        val post = uiState.games.filter { it.state == "post" }

        if (live.isNotEmpty()) { item { SectionLabel("LIVE", Red) } }
        items(live, key = { "l_${it.eventId}" }) { game -> GameCard(game, viewModel, uriHandler) }
        if (pre.isNotEmpty()) { item { SectionLabel("UPCOMING", LightPurple) } }
        items(pre, key = { "p_${it.eventId}" }) { game -> GameCard(game, viewModel, uriHandler) }
        if (post.isNotEmpty()) { item { SectionLabel("FINAL", DimText) } }
        items(post, key = { "f_${it.eventId}" }) { game -> GameCard(game, viewModel, uriHandler) }

        if (uiState.games.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(60.dp), contentAlignment = Alignment.Center) {
                Text("No games today", color = DimText, fontSize = 16.sp)
            }}
        }

        // News
        if (uiState.news.isNotEmpty()) {
            item { SectionLabel("HEADLINES", Purple) }
            items(uiState.news, key = { it.headline.hashCode() }) { article ->
                Surface(
                    onClick = { if (article.link.isNotBlank()) uriHandler.openUri(article.link) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.04f),
                        focusedContainerColor = Color.White.copy(alpha = 0.1f)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (article.imageUrl.isNotBlank()) {
                            AsyncImage(model = article.imageUrl, contentDescription = null,
                                modifier = Modifier.size(56.dp, 38.dp).clip(RoundedCornerShape(6.dp)))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(article.headline, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
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
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
}

// ── Game Card (single focusable, click=watch/boxscore, long-press=options) ──

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameCard(game: SportsGame, viewModel: SportsViewModel, uriHandler: androidx.compose.ui.platform.UriHandler) {
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
            containerColor = Color.White.copy(alpha = 0.05f),
            focusedContainerColor = Color.White.copy(alpha = 0.1f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (isLive) 2.dp else 0.dp, if (isLive) Red else Color.Transparent)),
            focusedBorder = Border(BorderStroke(2.dp, Purple))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            // Teams + Score
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Away
                if (game.awayLogo.isNotBlank()) {
                    AsyncImage(model = game.awayLogo, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(game.awayTeam, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (game.awayRecord.isNotBlank()) Text(game.awayRecord, fontSize = 10.sp, color = DimText)
                }

                // Score or VS
                if (game.state != "pre") {
                    Text("${game.awayScore} - ${game.homeScore}",
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (isLive) Green else Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp))
                } else {
                    Text("VS", fontSize = 13.sp, color = DimText, modifier = Modifier.padding(horizontal = 10.dp))
                }

                // Home
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(game.homeTeam, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                    if (game.homeRecord.isNotBlank()) Text(game.homeRecord, fontSize = 10.sp, color = DimText)
                }
                if (game.homeLogo.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    AsyncImage(model = game.homeLogo, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape))
                }
            }

            // Status + Series
            Text(game.status, Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = when (game.state) { "in" -> Red; "pre" -> LightPurple; else -> DimText })
            if (game.seriesNote.isNotBlank()) {
                Text(game.seriesNote, Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    fontSize = 10.sp, color = LightPurple)
            }

            // Hint
            Text(
                if (isFinal) "Press for box score · Hold for highlights" else "Press to watch · Hold for options",
                Modifier.fillMaxWidth().padding(top = 4.dp), textAlign = TextAlign.Center,
                fontSize = 9.sp, color = Color.White.copy(alpha = 0.3f)
            )

            // Long-press menu
            if (showMenu) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                    Pill("Watch", false, Purple) { viewModel.findAndWatchGame(game); showMenu = false }
                    Spacer(Modifier.width(6.dp))
                    Pill("Box Score", false, MaterialTheme.colorScheme.surfaceVariant) { viewModel.loadBoxScore(game.eventId); showMenu = false }
                    if (isFinal) {
                        Spacer(Modifier.width(6.dp))
                        Pill("Highlights", false, Red) { uriHandler.openUri(ytUrl); showMenu = false }
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
            Text("No standings data", color = DimText)
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
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(1.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        // Header
        item(key = "hdr_${conf.name}") {
            Text(conf.name, modifier = Modifier.padding(bottom = 6.dp),
                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        // Column labels
        item(key = "cols_${conf.name}") {
            Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)).padding(vertical = 6.dp, horizontal = 6.dp)) {
                Text("#", Modifier.width(24.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DimText, textAlign = TextAlign.Center)
                Text("Team", Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DimText)
                Text("W", Modifier.width(28.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DimText, textAlign = TextAlign.Center)
                Text("L", Modifier.width(28.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DimText, textAlign = TextAlign.Center)
                Text("STR", Modifier.width(32.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DimText, textAlign = TextAlign.Center)
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
                    containerColor = if (idx % 2 == 0) Color.White.copy(alpha = 0.03f) else Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.12f)
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(vertical = 5.dp, horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(team.seed.ifBlank { "${idx + 1}" }, Modifier.width(24.dp), fontSize = 12.sp, textAlign = TextAlign.Center,
                        fontWeight = if (isPlayoff) FontWeight.Bold else FontWeight.Normal,
                        color = if (isPlayoff) Purple else DimText)
                    if (team.logo.isNotBlank()) {
                        AsyncImage(model = team.logo, contentDescription = null, modifier = Modifier.size(18.dp).clip(CircleShape))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(team.abbr, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text(team.wins, Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color.White)
                    Text(team.losses, Modifier.width(28.dp), fontSize = 12.sp, textAlign = TextAlign.Center, color = Color.White)
                    Text(team.streak, Modifier.width(32.dp), fontSize = 11.sp, textAlign = TextAlign.Center,
                        fontWeight = FontWeight.SemiBold,
                        color = if (team.streak.startsWith("W")) Green else Red)
                }
            }
        }
    }
}

// ── Playoffs Bracket (horizontal tree: West ← Championship → East) ─────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayoffsView(uiState: SportsUiState, viewModel: SportsViewModel) {
    if (uiState.playoffBracket.isEmpty() && !uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("${uiState.league.uppercase()} playoff data not available", color = DimText, fontSize = 16.sp)
        }
        return
    }

    // Split by conference
    val west = remember(uiState.playoffBracket) { uiState.playoffBracket.filter { it.roundLabel.contains("West", true) } }
    val east = remember(uiState.playoffBracket) { uiState.playoffBracket.filter { it.roundLabel.contains("East", true) } }
    val westR1 = west.filter { it.roundLabel.contains("1st", true) || it.roundLabel.contains("First", true) }
    val westR2 = west.filter { it.roundLabel.contains("Semi", true) }
    val westFinal = west.filter { it.roundLabel.contains("Final", true) && !it.roundLabel.contains("Semi", true) }
    val eastR1 = east.filter { it.roundLabel.contains("1st", true) || it.roundLabel.contains("First", true) }
    val eastR2 = east.filter { it.roundLabel.contains("Semi", true) }
    val eastFinal = east.filter { it.roundLabel.contains("Final", true) && !it.roundLabel.contains("Semi", true) }
    val finals = uiState.playoffBracket.filter { !it.roundLabel.contains("West", true) && !it.roundLabel.contains("East", true) && (it.roundLabel.contains("Final", true) || it.roundLabel.contains("Champion", true)) }

    // Horizontal scrollable bracket
    Row(
        modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(12.dp))

        // West 1st Round
        BracketRound("WEST 1ST ROUND", westR1)
        Spacer(Modifier.width(12.dp))

        // West Semis
        BracketRound("WEST SEMIS", westR2)
        Spacer(Modifier.width(12.dp))

        // West Finals
        BracketRound("WEST FINALS", westFinal)
        Spacer(Modifier.width(16.dp))

        // Championship
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CHAMPIONSHIP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD700), letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            if (finals.isNotEmpty()) {
                BracketCard(finals.first())
            } else {
                Box(Modifier.width(220.dp).background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(16.dp),
                    contentAlignment = Alignment.Center) {
                    Text("TBD", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DimText)
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // East Finals
        BracketRound("EAST FINALS", eastFinal)
        Spacer(Modifier.width(12.dp))

        // East Semis
        BracketRound("EAST SEMIS", eastR2)
        Spacer(Modifier.width(12.dp))

        // East 1st Round
        BracketRound("EAST 1ST ROUND", eastR1)
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun BracketRound(title: String, seriesList: List<PlayoffSeries>) {
    Column(
        modifier = Modifier.width(220.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightPurple, letterSpacing = 1.sp)
        if (seriesList.isEmpty()) {
            Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp)).padding(12.dp),
                contentAlignment = Alignment.Center) {
                Text("TBD", fontSize = 13.sp, color = DimText)
            }
        } else {
            for (series in seriesList) {
                BracketCard(series)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BracketCard(series: PlayoffSeries) {
    val winner1 = series.isComplete && series.team1Wins > series.team2Wins
    val winner2 = series.isComplete && series.team2Wins > series.team1Wins

    Surface(
        onClick = { /* Could expand for game details */ },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.12f)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (!series.isComplete) 1.dp else 0.dp,
                if (!series.isComplete) LightPurple.copy(alpha = 0.4f) else Color.Transparent)),
            focusedBorder = Border(BorderStroke(2.dp, Purple))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(8.dp)) {
            // Series note header
            Text(series.seriesNote.ifBlank { "Best of 7" }, fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                color = if (series.isComplete) Green else LightPurple)

            Spacer(Modifier.height(4.dp))

            // Team 1
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (series.team1Logo.isNotBlank()) {
                    AsyncImage(model = series.team1Logo, contentDescription = null,
                        modifier = Modifier.size(18.dp).clip(CircleShape))
                    Spacer(Modifier.width(4.dp))
                }
                Text(series.team1Name, Modifier.weight(1f), fontSize = 12.sp,
                    fontWeight = if (winner1) FontWeight.Bold else FontWeight.Normal,
                    color = if (winner1) Color.White else Color.White.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${series.team1Wins}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (winner1) Color.White else Color.White.copy(alpha = 0.6f))
                if (winner1) Text(" ◄", fontSize = 10.sp, color = Green)
            }

            Spacer(Modifier.height(2.dp))

            // Team 2
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (series.team2Logo.isNotBlank()) {
                    AsyncImage(model = series.team2Logo, contentDescription = null,
                        modifier = Modifier.size(18.dp).clip(CircleShape))
                    Spacer(Modifier.width(4.dp))
                }
                Text(series.team2Name, Modifier.weight(1f), fontSize = 12.sp,
                    fontWeight = if (winner2) FontWeight.Bold else FontWeight.Normal,
                    color = if (winner2) Color.White else Color.White.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${series.team2Wins}", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (winner2) Color.White else Color.White.copy(alpha = 0.6f))
                if (winner2) Text(" ◄", fontSize = 10.sp, color = Green)
            }
        }
    }
}

// ── Box Score View ─────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BoxScoreView(boxScore: BoxScoreData, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header with back + score
        item {
            Surface(
                onClick = onBack,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color.White.copy(alpha = 0.1f)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("< Back to Scores", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Purple)
                    Spacer(Modifier.height(6.dp))
                    Text("${boxScore.awayAbbr}  ${boxScore.awayTotal}  —  ${boxScore.homeTotal}  ${boxScore.homeAbbr}",
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }

        // Quarter scores
        if (boxScore.homeQuarters.isNotEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("", Modifier.weight(1f))
                        for (q in boxScore.homeQuarters.indices) {
                            Text(if (q < 4) "Q${q+1}" else "OT${q-3}", Modifier.width(32.dp), textAlign = TextAlign.Center,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DimText)
                        }
                        Text("T", Modifier.width(36.dp), textAlign = TextAlign.Center, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    for ((abbr, quarters, total) in listOf(
                        Triple(boxScore.awayAbbr, boxScore.awayQuarters, boxScore.awayTotal),
                        Triple(boxScore.homeAbbr, boxScore.homeQuarters, boxScore.homeTotal)
                    )) {
                        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                            Text(abbr, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            for (s in quarters) Text(s, Modifier.width(32.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = Color.White)
                            Text(total, Modifier.width(36.dp), textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Stats table
        item {
            Column(Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp)).padding(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                    Text("Stat", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(boxScore.homeAbbr, Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(boxScore.awayAbbr, Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                for (stat in boxScore.stats) {
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(stat.name, Modifier.weight(1f), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                        Text(stat.homeValue, Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(stat.awayValue, Modifier.width(56.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
