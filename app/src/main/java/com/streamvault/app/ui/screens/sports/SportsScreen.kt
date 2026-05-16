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

// ── Playoffs View ──────────────────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayoffsView(uiState: SportsUiState, viewModel: SportsViewModel) {
    if (!uiState.isPostseason) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("${uiState.league.uppercase()} is not in playoffs", color = DimText, fontSize = 16.sp)
        }
        return
    }

    // Group by series matchup
    val seriesMap = remember(uiState.games) {
        val map = mutableMapOf<String, MutableList<SportsGame>>()
        for (g in uiState.games) {
            val key = listOf(g.awayAbbr, g.homeAbbr).sorted().joinToString("-")
            map.getOrPut(key) { mutableListOf() }.add(g)
        }
        map
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("${uiState.league.uppercase()} PLAYOFFS", Modifier.padding(start = 16.dp, top = 8.dp),
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) }

        items(seriesMap.entries.toList(), key = { it.key }) { (_, games) ->
            val g = games.first()
            val note = games.mapNotNull { it.seriesNote.takeIf { n -> n.isNotBlank() } }.lastOrNull() ?: ""
            val label = games.mapNotNull { it.seriesGameLabel.takeIf { n -> n.isNotBlank() } }.lastOrNull() ?: ""

            // Parse series wins
            var awayW = 0; var homeW = 0
            val wm = Regex("(\\w+)\\s+wins\\s+series\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
            val lm = Regex("(\\w+)\\s+leads?\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
            val tm = Regex("tied\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
            when {
                wm != null -> { if (wm.groupValues[1].uppercase() == g.awayAbbr.uppercase()) { awayW = wm.groupValues[2].toInt(); homeW = wm.groupValues[3].toInt() } else { homeW = wm.groupValues[2].toInt(); awayW = wm.groupValues[3].toInt() } }
                lm != null -> { if (lm.groupValues[1].uppercase() == g.awayAbbr.uppercase()) { awayW = lm.groupValues[2].toInt(); homeW = lm.groupValues[3].toInt() } else { homeW = lm.groupValues[2].toInt(); awayW = lm.groupValues[3].toInt() } }
                tm != null -> { awayW = tm.groupValues[1].toInt(); homeW = awayW }
            }

            Surface(
                onClick = { viewModel.loadBoxScore(g.eventId) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.05f),
                    focusedContainerColor = Color.White.copy(alpha = 0.1f)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Purple))),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    // Round label
                    if (label.isNotBlank()) {
                        Text(label, Modifier.fillMaxWidth().padding(bottom = 6.dp), textAlign = TextAlign.Center,
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = LightPurple)
                    }
                    // Teams
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (g.awayLogo.isNotBlank()) {
                            AsyncImage(model = g.awayLogo, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(g.awayTeam, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("$awayW - $homeW", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp))
                        Text(g.homeTeam, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            color = Color.White, textAlign = TextAlign.End)
                        if (g.homeLogo.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            AsyncImage(model = g.homeLogo, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape))
                        }
                    }
                    // Progress bar
                    val awayPct = if (awayW + homeW > 0) awayW / 7f else 0f
                    val homePct = if (awayW + homeW > 0) homeW / 7f else 0f
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.08f))) {
                        if (awayPct > 0) Box(Modifier.weight(awayPct).fillMaxHeight().background(Purple))
                        Box(Modifier.weight(maxOf(1f - awayPct - homePct, 0.01f)).fillMaxHeight())
                        if (homePct > 0) Box(Modifier.weight(homePct).fillMaxHeight().background(Red))
                    }
                    // Status
                    Text(note.ifBlank { "Best of 7" }, Modifier.fillMaxWidth().padding(top = 4.dp),
                        textAlign = TextAlign.Center, fontSize = 11.sp, color = DimText)
                }
            }
        }

        if (seriesMap.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("No active playoff series", color = DimText)
            }}
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
