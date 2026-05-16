package com.streamvault.app.ui.screens.sports

import androidx.compose.foundation.*
import androidx.compose.foundation.focusable
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

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SportsScreen(
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: SportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            // League selector
            LazyRow(
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                val leagues = listOf("NBA", "NHL", "MLB", "NFL", "MLS")
                items(leagues) { league ->
                    val selected = league.lowercase() == uiState.league
                    Surface(
                        onClick = { viewModel.selectLeague(league.lowercase()) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(league, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Divider
                item { Spacer(Modifier.width(8.dp)) }

                // View selector
                val views = listOf("Today" to "today", "Standings" to "standings", "Playoffs" to "playoffs")
                items(views) { (label, view) ->
                    val selected = view == uiState.view
                    Surface(
                        onClick = { viewModel.selectView(view) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = if (selected) Color(0xFF6C5CE7) else MaterialTheme.colorScheme.surface,
                            contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Content
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (uiState.boxScore != null) {
                BoxScoreView(uiState.boxScore!!, onBack = { viewModel.clearBoxScore() })
            } else {
                when (uiState.view) {
                    "today" -> TodayView(uiState, viewModel)
                    "standings" -> StandingsView(uiState)
                    "playoffs" -> PlayoffsView(uiState, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TodayView(uiState: SportsUiState, viewModel: SportsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val live = uiState.games.filter { it.state == "in" }
        val pre = uiState.games.filter { it.state == "pre" }
        val post = uiState.games.filter { it.state == "post" }

        if (live.isNotEmpty()) {
            item { SectionHeader("LIVE", Color(0xFFE74C3C)) }
            items(live, key = { it.eventId }) { game -> GameCard(game, viewModel) }
        }
        if (pre.isNotEmpty()) {
            item { SectionHeader("UPCOMING", Color(0xFFA29BFE)) }
            items(pre, key = { it.eventId }) { game -> GameCard(game, viewModel) }
        }
        if (post.isNotEmpty()) {
            item { SectionHeader("FINAL", Color(0xFF636E72)) }
            items(post, key = { it.eventId }) { game -> GameCard(game, viewModel) }
        }

        if (uiState.games.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("No games today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                }
            }
        }

        // News
        if (uiState.news.isNotEmpty()) {
            item { SectionHeader("HEADLINES", MaterialTheme.colorScheme.primary) }
            items(uiState.news) { article ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (article.imageUrl.isNotBlank()) {
                        AsyncImage(model = article.imageUrl, contentDescription = null,
                            modifier = Modifier.size(64.dp, 44.dp).clip(RoundedCornerShape(6.dp)))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(article.headline, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        if (article.description.isNotBlank()) {
                            Text(article.description, fontSize = 11.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(text, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 1.sp)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GameCard(game: SportsGame, viewModel: SportsViewModel) {
    val isLive = game.state == "in"
    val isFinal = game.state == "post"

    Surface(
        onClick = { viewModel.loadBoxScore(game.eventId) },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (isLive) 2.dp else 1.dp,
                if (isLive) Color(0xFFE74C3C) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f), // Don't scale up on focus
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Away team
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                    if (game.awayLogo.isNotBlank()) {
                        AsyncImage(model = game.awayLogo, contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape))
                        Spacer(Modifier.width(8.dp))
                    }
                    Column {
                        Text(game.awayTeam, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        if (game.awayRecord.isNotBlank()) {
                            Text(game.awayRecord, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Score
                if (game.state != "pre") {
                    Text("${game.awayScore} - ${game.homeScore}",
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (isLive) Color(0xFF00B894) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp))
                } else {
                    Text("VS", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp))
                }

                // Home team
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(game.homeTeam, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        if (game.homeRecord.isNotBlank()) {
                            Text(game.homeRecord, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (game.homeLogo.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        AsyncImage(model = game.homeLogo, contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape))
                    }
                }
            }

            // Status
            Text(game.status, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = when (game.state) {
                    "in" -> Color(0xFFE74C3C); "pre" -> Color(0xFFA29BFE); else -> MaterialTheme.colorScheme.onSurfaceVariant
                })

            // Series note
            if (game.seriesNote.isNotBlank()) {
                Text(game.seriesNote, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFFA29BFE))
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (game.state != "post") {
                    Surface(
                        onClick = { viewModel.findAndWatchGame(game) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFF6C5CE7), contentColor = Color.White),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Watch", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Surface(
                    onClick = { viewModel.loadBoxScore(game.eventId) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Box Score", modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                if (game.state == "post") {
                    Spacer(Modifier.width(8.dp))
                    val gameDate = try {
                        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).let { sdf ->
                            val d = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US).parse(game.date.take(16))
                            if (d != null) java.text.SimpleDateFormat("MMM d yyyy", java.util.Locale.US).format(d) else ""
                        }
                    } catch (_: Exception) { "" }
                    val ytQuery = java.net.URLEncoder.encode("${game.awayTeam} vs ${game.homeTeam} highlights $gameDate", "UTF-8")
                    Surface(
                        onClick = { /* Open YouTube - needs UriHandler */ },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color(0xFFE74C3C), contentColor = Color.White),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Highlights", modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StandingsView(uiState: SportsUiState) {
    if (uiState.conferences.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No standings data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    // Flatten into a single scrollable list
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (conf in uiState.conferences) {
            // Conference header
            item(key = "conf_${conf.name}") {
                Text(conf.name, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            // Table header
            item(key = "header_${conf.name}") {
                Row(
                    Modifier.fillMaxWidth().focusable()
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                ) {
                    Text("#", Modifier.width(30.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    Text("Team", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f))
                    Text("W", Modifier.width(36.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    Text("L", Modifier.width(36.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    Text("PCT", Modifier.width(48.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    Text("STRK", Modifier.width(44.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                    Text("L10", Modifier.width(44.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f), textAlign = TextAlign.Center)
                }
            }

            // Team rows — each wrapped in Surface for d-pad focus
            items(conf.teams.size, key = { "team_${conf.name}_$it" }) { index ->
                val team = conf.teams[index]
                val isLast = index == conf.teams.size - 1
                val bgShape = if (isLast) RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp) else RoundedCornerShape(0.dp)
                val isPlayoffTeam = (team.seed.toIntOrNull() ?: 99) <= 8

                Surface(
                    onClick = { /* Could show team details in future */ },
                    shape = ClickableSurfaceDefaults.shape(bgShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (index % 2 == 0) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.02f),
                        focusedContainerColor = Color.White.copy(alpha = 0.12f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            team.seed.ifBlank { "${index + 1}" }, Modifier.width(30.dp),
                            fontSize = 13.sp, textAlign = TextAlign.Center,
                            fontWeight = if (isPlayoffTeam) FontWeight.Bold else FontWeight.Normal,
                            color = if (isPlayoffTeam) Color(0xFF6C5CE7) else Color.White.copy(alpha = 0.5f)
                        )
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            if (team.logo.isNotBlank()) {
                                AsyncImage(model = team.logo, contentDescription = null,
                                    modifier = Modifier.size(22.dp).clip(CircleShape))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(team.abbr, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                        Text(team.wins, Modifier.width(36.dp), fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.White)
                        Text(team.losses, Modifier.width(36.dp), fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.White)
                        Text(team.pct, Modifier.width(48.dp), fontSize = 13.sp, textAlign = TextAlign.Center, color = Color.White)
                        Text(team.streak, Modifier.width(44.dp), fontSize = 13.sp, textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            color = if (team.streak.startsWith("W")) Color(0xFF00B894) else Color(0xFFE74C3C))
                        Text(team.last10, Modifier.width(44.dp), fontSize = 12.sp, textAlign = TextAlign.Center,
                            color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayoffsView(uiState: SportsUiState, viewModel: SportsViewModel) {
    if (!uiState.isPostseason) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("${uiState.league.uppercase()} is not in playoffs",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        }
        return
    }

    // Group games by series
    val seriesMap = mutableMapOf<String, MutableList<SportsGame>>()
    for (game in uiState.games) {
        val key = listOf(game.awayAbbr, game.homeAbbr).sorted().joinToString("-")
        seriesMap.getOrPut(key) { mutableListOf() }.add(game)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("${uiState.league.uppercase()} PLAYOFFS", modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }

        items(seriesMap.entries.toList()) { (_, games) ->
            val g = games.first()
            val note = games.mapNotNull { it.seriesNote.takeIf { n -> n.isNotBlank() } }.lastOrNull() ?: ""
            val gameLabel = games.mapNotNull { it.seriesGameLabel.takeIf { n -> n.isNotBlank() } }.lastOrNull() ?: ""

            // Parse wins from series summary (e.g., "Series tied 3-3", "SA wins series 4-2", "SA leads 3-2")
            var awayWins = 0; var homeWins = 0
            val leadMatch = Regex("(\\w+)\\s+leads?\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
            val tiedMatch = Regex("tied\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
            val winsMatch = Regex("(\\w+)\\s+wins\\s+series\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
            when {
                winsMatch != null -> {
                    val winner = winsMatch.groupValues[1].uppercase()
                    if (winner == g.awayAbbr.uppercase()) { awayWins = winsMatch.groupValues[2].toInt(); homeWins = winsMatch.groupValues[3].toInt() }
                    else { homeWins = winsMatch.groupValues[2].toInt(); awayWins = winsMatch.groupValues[3].toInt() }
                }
                leadMatch != null -> {
                    val leader = leadMatch.groupValues[1].uppercase()
                    if (leader == g.awayAbbr.uppercase()) { awayWins = leadMatch.groupValues[2].toInt(); homeWins = leadMatch.groupValues[3].toInt() }
                    else { homeWins = leadMatch.groupValues[2].toInt(); awayWins = leadMatch.groupValues[3].toInt() }
                }
                tiedMatch != null -> { awayWins = tiedMatch.groupValues[1].toInt(); homeWins = awayWins }
            }

            Surface(
                onClick = { viewModel.loadBoxScore(g.eventId) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (g.awayLogo.isNotBlank()) AsyncImage(model = g.awayLogo, contentDescription = null, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(g.awayTeam, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("$awayWins - $homeWins", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 12.dp))
                        Text(g.homeTeam, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                        Spacer(Modifier.width(8.dp))
                        if (g.homeLogo.isNotBlank()) AsyncImage(model = g.homeLogo, contentDescription = null, modifier = Modifier.size(28.dp))
                    }

                    // Progress bar
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp))) {
                        val awayPct = if (awayWins + homeWins > 0) awayWins / 7f else 0f
                        val homePct = if (awayWins + homeWins > 0) homeWins / 7f else 0f
                        Box(Modifier.weight(maxOf(awayPct, 0.01f)).fillMaxHeight().background(Color(0xFF6C5CE7)))
                        Spacer(Modifier.weight(maxOf(1f - awayPct - homePct, 0.01f)).fillMaxHeight())
                        Box(Modifier.weight(maxOf(homePct, 0.01f)).fillMaxHeight().background(Color(0xFFE17055)))
                    }

                    if (gameLabel.isNotBlank()) {
                        Text(gameLabel, Modifier.fillMaxWidth().padding(top = 6.dp),
                            textAlign = TextAlign.Center, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFA29BFE))
                    }
                    Text(note.ifBlank { "Best of 7" }, Modifier.fillMaxWidth().padding(top = 2.dp),
                        textAlign = TextAlign.Center, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BoxScoreView(boxScore: BoxScoreData, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().clickable { onBack() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("< Back to Scores", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF6C5CE7))
                }
                Spacer(Modifier.height(8.dp))
                Text("${boxScore.awayAbbr}  ${boxScore.awayTotal}  —  ${boxScore.homeTotal}  ${boxScore.homeAbbr}",
                    fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }

        // Quarter scores
        if (boxScore.homeQuarters.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("", Modifier.weight(1f))
                        for (q in boxScore.homeQuarters.indices) {
                            val label = if (q < 4) "Q${q + 1}" else "OT${q - 3}"
                            Text(label, Modifier.width(36.dp), textAlign = TextAlign.Center,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("T", Modifier.width(40.dp), textAlign = TextAlign.Center,
                            fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    for ((abbr, quarters, total) in listOf(
                        Triple(boxScore.awayAbbr, boxScore.awayQuarters, boxScore.awayTotal),
                        Triple(boxScore.homeAbbr, boxScore.homeQuarters, boxScore.homeTotal)
                    )) {
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Text(abbr, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            for (score in quarters) {
                                Text(score, Modifier.width(36.dp), textAlign = TextAlign.Center, fontSize = 12.sp)
                            }
                            Text(total, Modifier.width(40.dp), textAlign = TextAlign.Center,
                                fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Stats
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(12.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    Text("Stat", Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = Color.White)
                    Text(boxScore.homeAbbr, Modifier.width(60.dp), textAlign = TextAlign.Center,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(boxScore.awayAbbr, Modifier.width(60.dp), textAlign = TextAlign.Center,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
}
                for (stat in boxScore.stats) {
                    Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(stat.name, Modifier.weight(1f), fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f))
                        Text(stat.homeValue, Modifier.width(60.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(stat.awayValue, Modifier.width(60.dp), textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
