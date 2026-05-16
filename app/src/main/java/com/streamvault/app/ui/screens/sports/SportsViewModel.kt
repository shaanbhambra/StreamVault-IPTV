package com.streamvault.app.ui.screens.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class SportsGame(
    val eventId: String,
    val date: String,
    val homeTeam: String, val homeAbbr: String, val homeScore: String,
    val homeLogo: String, val homeColor: String, val homeRecord: String,
    val awayTeam: String, val awayAbbr: String, val awayScore: String,
    val awayLogo: String, val awayColor: String, val awayRecord: String,
    val status: String, val state: String, // pre, in, post
    val seriesNote: String, // "Series tied 3-3", "SA wins series 4-2", etc.
    val seriesGameLabel: String = "", // "East Semifinals - Game 6"
    val homeQuarters: List<String> = emptyList(),
    val awayQuarters: List<String> = emptyList()
)

data class StandingsTeam(
    val name: String, val abbr: String, val logo: String,
    val wins: String, val losses: String, val pct: String,
    val streak: String, val seed: String, val last10: String
)

data class StandingsConference(val name: String, val teams: List<StandingsTeam>)

data class NewsArticle(
    val headline: String, val description: String,
    val imageUrl: String, val link: String
)

data class BoxScoreStat(val name: String, val homeValue: String, val awayValue: String)

data class PlayerBoxScore(
    val name: String,
    val headshot: String,
    val position: String,
    val jersey: String,
    val isStarter: Boolean,
    val stats: List<String>,
    val didNotPlay: Boolean = false
)

data class BoxScoreData(
    val homeAbbr: String, val awayAbbr: String,
    val homeLogo: String, val awayLogo: String,
    val homeQuarters: List<String>, val awayQuarters: List<String>,
    val homeTotal: String, val awayTotal: String,
    val stats: List<BoxScoreStat>,
    val statLabels: List<String> = emptyList(),
    val homePlayers: List<PlayerBoxScore> = emptyList(),
    val awayPlayers: List<PlayerBoxScore> = emptyList(),
    val homeTotals: List<String> = emptyList(),
    val awayTotals: List<String> = emptyList()
)

data class PlayoffSeries(
    val roundLabel: String, // "East 1st Round", "West Semifinals", etc.
    val team1Abbr: String, val team1Name: String, val team1Logo: String,
    val team2Abbr: String, val team2Name: String, val team2Logo: String,
    val team1Wins: Int, val team2Wins: Int,
    val team1Seed: Int = 0,
    val team2Seed: Int = 0,
    val seriesNote: String, // "DET wins series 4-3"
    val isComplete: Boolean,
    val games: List<PlayoffGame>
)

data class PlayoffGame(
    val awayAbbr: String, val awayScore: String,
    val homeAbbr: String, val homeScore: String,
    val status: String
)

data class BracketData(
    val westR1: List<PlayoffSeries>,    // 4 matchups
    val westR2: List<PlayoffSeries>,    // 2 matchups
    val westFinal: PlayoffSeries?,
    val eastR1: List<PlayoffSeries>,    // 4 matchups
    val eastR2: List<PlayoffSeries>,    // 2 matchups
    val eastFinal: PlayoffSeries?,
    val championship: PlayoffSeries?
)

data class SportsUiState(
    val league: String = "nba",
    val view: String = "today",
    val games: List<SportsGame> = emptyList(),
    val conferences: List<StandingsConference> = emptyList(),
    val news: List<NewsArticle> = emptyList(),
    val playoffBracket: List<PlayoffSeries> = emptyList(),
    val bracketData: BracketData? = null,
    val boxScore: BoxScoreData? = null,
    val isLoading: Boolean = false,
    val isPostseason: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SportsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SportsUiState())
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()

    init {
        loadScores("nba")
    }

    fun selectLeague(league: String) {
        _uiState.update { it.copy(league = league) }
        when (_uiState.value.view) {
            "today" -> loadScores(league)
            "standings" -> loadStandings(league)
            "playoffs" -> loadPlayoffBracket(league)
        }
    }

    fun selectView(view: String) {
        _uiState.update { it.copy(view = view) }
        when (view) {
            "today" -> loadScores(_uiState.value.league)
            "standings" -> loadStandings(_uiState.value.league)
            "playoffs" -> loadPlayoffBracket(_uiState.value.league)
        }
    }

    fun loadBoxScore(eventId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val league = _uiState.value.league
                val sportPath = mapLeague(league)
                val raw = fetchUrl("https://site.api.espn.com/apis/site/v2/sports/$sportPath/summary?event=$eventId")
                val data = JSONObject(raw)

                val boxscore = data.optJSONObject("boxscore")
                val header = data.optJSONObject("header")

                var homeAbbr = ""; var awayAbbr = ""
                val homeQ = mutableListOf<String>(); val awayQ = mutableListOf<String>()
                var homeTotal = ""; var awayTotal = ""
                val stats = mutableListOf<BoxScoreStat>()

                // Quarters from header
                header?.optJSONArray("competitions")?.optJSONObject(0)?.let { comp ->
                    val competitors = comp.optJSONArray("competitors") ?: return@let
                    for (i in 0 until competitors.length()) {
                        val c = competitors.getJSONObject(i)
                        val abbr = c.optJSONObject("team")?.optString("abbreviation", "?") ?: "?"
                        val total = c.optString("score", "0")
                        val ls = c.optJSONArray("linescores") ?: JSONArray()
                        val quarters = mutableListOf<String>()
                        for (q in 0 until ls.length()) quarters.add(ls.getJSONObject(q).optString("displayValue", "0"))
                        if (c.optString("homeAway") == "home") {
                            homeAbbr = abbr; homeTotal = total; homeQ.addAll(quarters)
                        } else {
                            awayAbbr = abbr; awayTotal = total; awayQ.addAll(quarters)
                        }
                    }
                }

                // Team stats from boxscore
                boxscore?.optJSONArray("teams")?.let { teams ->
                    if (teams.length() >= 2) {
                        val t0 = teams.getJSONObject(0).optJSONArray("statistics") ?: JSONArray()
                        val t1 = teams.getJSONObject(1).optJSONArray("statistics") ?: JSONArray()
                        val keyStats = listOf("Field Goal %", "Three Point %", "Free Throw %", "Rebounds", "Assists", "Steals", "Blocks", "Turnovers")
                        for (statName in keyStats) {
                            var v0 = ""; var v1 = ""
                            for (j in 0 until t0.length()) {
                                if (t0.getJSONObject(j).optString("label") == statName) v0 = t0.getJSONObject(j).optString("displayValue", "-")
                            }
                            for (j in 0 until t1.length()) {
                                if (t1.getJSONObject(j).optString("label") == statName) v1 = t1.getJSONObject(j).optString("displayValue", "-")
                            }
                            if (v0.isNotEmpty() || v1.isNotEmpty()) stats.add(BoxScoreStat(statName, v0, v1))
                        }
                    }
                }

                // Player stats from boxscore
                var statLabels = listOf<String>()
                val homePlayers = mutableListOf<PlayerBoxScore>()
                val awayPlayers = mutableListOf<PlayerBoxScore>()
                var homeTotals = listOf<String>()
                var awayTotals = listOf<String>()
                var homeLogo = ""; var awayLogo = ""

                boxscore?.optJSONArray("players")?.let { playerGroups ->
                    for (g in 0 until playerGroups.length()) {
                        val group = playerGroups.getJSONObject(g)
                        val teamObj = group.optJSONObject("team")
                        val teamAbbr = teamObj?.optString("abbreviation", "") ?: ""
                        val teamLogoUrl = teamObj?.optString("logo", "") ?: ""
                        val isHome = teamAbbr == homeAbbr
                        if (isHome) homeLogo = teamLogoUrl else awayLogo = teamLogoUrl

                        val statCats = group.optJSONArray("statistics") ?: continue
                        if (statCats.length() == 0) continue
                        val cat = statCats.getJSONObject(0)

                        // Get labels (first time only)
                        if (statLabels.isEmpty()) {
                            val labels = cat.optJSONArray("labels") ?: JSONArray()
                            statLabels = (0 until labels.length()).map { labels.getString(it) }
                        }

                        // Get totals
                        val totalsArr = cat.optJSONArray("totals") ?: JSONArray()
                        val totals = (0 until totalsArr.length()).map { totalsArr.getString(it) }
                        if (isHome) homeTotals = totals else awayTotals = totals

                        // Get individual players
                        val athletes = cat.optJSONArray("athletes") ?: continue
                        val playerList = if (isHome) homePlayers else awayPlayers
                        for (a in 0 until athletes.length()) {
                            val pObj = athletes.getJSONObject(a)
                            val ath = pObj.optJSONObject("athlete") ?: continue
                            val dnp = pObj.optBoolean("didNotPlay", false)
                            val pStats = pObj.optJSONArray("stats") ?: JSONArray()
                            playerList.add(PlayerBoxScore(
                                name = ath.optString("displayName", "?"),
                                headshot = ath.optJSONObject("headshot")?.optString("href", "") ?: "",
                                position = ath.optJSONObject("position")?.optString("abbreviation", "") ?: "",
                                jersey = ath.optString("jersey", ""),
                                isStarter = pObj.optBoolean("starter", false),
                                stats = (0 until pStats.length()).map { pStats.getString(it) },
                                didNotPlay = dnp
                            ))
                        }
                    }
                }

                _uiState.update { it.copy(boxScore = BoxScoreData(
                    homeAbbr, awayAbbr, homeLogo, awayLogo, homeQ, awayQ, homeTotal, awayTotal, stats,
                    statLabels, homePlayers, awayPlayers, homeTotals, awayTotals
                )) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Box score failed: ${e.message}") }
            }
        }
    }

    fun clearBoxScore() { _uiState.update { it.copy(boxScore = null) } }

    fun findAndWatchGame(game: SportsGame) {
        // This triggers a search via the debug API to find and play the IPTV stream
        // The debug API's quick_switch handles searching channel names for team names
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val query = "${game.awayAbbr} ${game.homeAbbr}"
                val url = java.net.URL("http://127.0.0.1:8585/quick_switch")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 3000
                conn.readTimeout = 5000
                conn.outputStream.write("{\"query\":\"$query\"}".toByteArray())
                val code = conn.responseCode
                conn.disconnect()
                if (code != 200) {
                    // Fallback: try team names
                    val url2 = java.net.URL("http://127.0.0.1:8585/quick_switch")
                    val conn2 = url2.openConnection() as java.net.HttpURLConnection
                    conn2.requestMethod = "POST"
                    conn2.setRequestProperty("Content-Type", "application/json")
                    conn2.doOutput = true
                    conn2.connectTimeout = 3000
                    conn2.readTimeout = 5000
                    conn2.outputStream.write("{\"query\":\"${game.homeTeam.split(" ").last()}\"}".toByteArray())
                    conn2.responseCode
                    conn2.disconnect()
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadScores(league: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sportPath = mapLeague(league)
                val raw = fetchUrl("https://site.api.espn.com/apis/site/v2/sports/$sportPath/scoreboard")
                val data = JSONObject(raw)
                val events = data.optJSONArray("events") ?: JSONArray()
                val seasonObj = data.optJSONArray("leagues")?.optJSONObject(0)?.optJSONObject("season")
                val seasonTypeObj = seasonObj?.optJSONObject("type")
                val seasonType = seasonTypeObj?.optInt("type", 2) ?: seasonObj?.optInt("type", 2) ?: 2

                val games = mutableListOf<SportsGame>()
                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
                    val comp = event.getJSONArray("competitions").getJSONObject(0)
                    val competitors = comp.getJSONArray("competitors")
                    val status = comp.getJSONObject("status").getJSONObject("type")
                    val home = competitors.getJSONObject(0)
                    val away = competitors.getJSONObject(1)
                    val homeTeam = home.getJSONObject("team")
                    val awayTeam = away.getJSONObject("team")

                    val hq = mutableListOf<String>(); val aq = mutableListOf<String>()
                    home.optJSONArray("linescores")?.let { ls -> for (q in 0 until ls.length()) hq.add(ls.getJSONObject(q).optString("displayValue", "0")) }
                    away.optJSONArray("linescores")?.let { ls -> for (q in 0 until ls.length()) aq.add(ls.getJSONObject(q).optString("displayValue", "0")) }

                    // Get series info - try series.summary first, then notes
                    val seriesNote = comp.optJSONObject("series")?.optString("summary", "")?.takeIf { it.isNotBlank() }
                        ?: comp.optJSONArray("notes")?.let { notes ->
                            for (n in 0 until notes.length()) {
                                val h = notes.getJSONObject(n).optString("headline", "")
                                if (h.isNotBlank()) return@let h
                            }; null
                        } ?: ""

                    games.add(SportsGame(
                        eventId = event.getString("id"), date = event.getString("date"),
                        homeTeam = homeTeam.getString("displayName"), homeAbbr = homeTeam.getString("abbreviation"),
                        homeScore = home.optString("score", "0"),
                        homeLogo = homeTeam.optString("logo", "").ifBlank { homeTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "" },
                        homeColor = homeTeam.optString("color", "333"), homeRecord = home.optJSONArray("records")?.optJSONObject(0)?.optString("summary", "") ?: "",
                        awayTeam = awayTeam.getString("displayName"), awayAbbr = awayTeam.getString("abbreviation"),
                        awayScore = away.optString("score", "0"),
                        awayLogo = awayTeam.optString("logo", "").ifBlank { awayTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "" },
                        awayColor = awayTeam.optString("color", "333"), awayRecord = away.optJSONArray("records")?.optJSONObject(0)?.optString("summary", "") ?: "",
                        status = status.getString("shortDetail"), state = status.getString("state"),
                        seriesNote = seriesNote,
                        seriesGameLabel = comp.optJSONArray("notes")?.let { notes ->
                            for (n in 0 until notes.length()) notes.getJSONObject(n).optString("headline", "").takeIf { it.isNotBlank() }?.let { return@let it }; null
                        } ?: "",
                        homeQuarters = hq, awayQuarters = aq
                    ))
                }

                // Also load news
                val newsRaw = try { fetchUrl("https://site.api.espn.com/apis/site/v2/sports/$sportPath/news") } catch (_: Exception) { "{}" }
                val newsData = JSONObject(newsRaw)
                val articles = newsData.optJSONArray("articles") ?: JSONArray()
                val newsList = mutableListOf<NewsArticle>()
                for (i in 0 until minOf(articles.length(), 6)) {
                    val a = articles.getJSONObject(i)
                    val img = a.optJSONArray("images")?.optJSONObject(0)?.optString("url", "") ?: ""
                    val link = a.optJSONObject("links")?.optJSONObject("web")?.optString("href", "") ?: ""
                    newsList.add(NewsArticle(a.getString("headline"), a.optString("description", ""), img, link))
                }

                _uiState.update { it.copy(games = games, news = newsList, isLoading = false, isPostseason = seasonType == 3) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun loadPlayoffBracket(league: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sportPath = mapLeague(league)

                // Use seasontype=3 with date range starting from mid-April
                // (per ESPN API docs: seasontype 1=pre, 2=regular, 3=postseason)
                val dateFmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                val now = java.util.Calendar.getInstance()
                val endDate = dateFmt.format(now.time)
                val month = now.get(java.util.Calendar.MONTH)
                val yr = if (month < 3) now.get(java.util.Calendar.YEAR) - 1 else now.get(java.util.Calendar.YEAR)
                val startDate = "${yr}0401"

                val gamesDeferred = async {
                    fetchUrl("https://site.api.espn.com/apis/site/v2/sports/$sportPath/scoreboard?seasontype=3&dates=$startDate-$endDate&limit=300")
                }
                val standingsDeferred = async {
                    try { fetchUrl("https://site.api.espn.com/apis/v2/sports/$sportPath/standings") } catch (_: Exception) { "{}" }
                }

                val raw = gamesDeferred.await()
                val standingsRaw = standingsDeferred.await()

                // Build seed lookup from standings: abbr -> seed
                val seedMap = mutableMapOf<String, Int>()
                try {
                    val standingsData = JSONObject(standingsRaw)
                    val children = standingsData.optJSONArray("children") ?: JSONArray()
                    for (i in 0 until children.length()) {
                        val entries = children.getJSONObject(i).optJSONObject("standings")?.optJSONArray("entries") ?: continue
                        for (j in 0 until entries.length()) {
                            val entry = entries.getJSONObject(j)
                            val abbr = entry.getJSONObject("team").getString("abbreviation")
                            val stats = entry.optJSONArray("stats") ?: continue
                            for (k in 0 until stats.length()) {
                                val s = stats.getJSONObject(k)
                                if (s.getString("name") == "playoffSeed") {
                                    seedMap[abbr] = s.optString("displayValue", "0").toIntOrNull() ?: 0
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                val data = JSONObject(raw)
                val allEvents = mutableListOf<JSONObject>()
                val events = data.optJSONArray("events") ?: JSONArray()
                for (i in 0 until events.length()) allEvents.add(events.getJSONObject(i))

                // Group games by series matchup
                data class RawGame(
                    val awayAbbr: String, val awayName: String, val awayLogo: String, val awayScore: String, val awaySeed: Int,
                    val homeAbbr: String, val homeName: String, val homeLogo: String, val homeScore: String, val homeSeed: Int,
                    val status: String, val roundLabel: String, val seriesNote: String
                )

                val seriesMap = mutableMapOf<String, MutableList<RawGame>>()

                for (event in allEvents) {
                    val comp = event.getJSONArray("competitions").getJSONObject(0)
                    val competitors = comp.getJSONArray("competitors")
                    val statusType = comp.getJSONObject("status").getJSONObject("type")

                    // Only playoff games
                    val seriesType = comp.optJSONObject("series")?.optString("type", "") ?: ""
                    if (seriesType != "playoff") continue

                    val home = competitors.getJSONObject(0)
                    val away = competitors.getJSONObject(1)
                    val homeTeam = home.getJSONObject("team")
                    val awayTeam = away.getJSONObject("team")

                    val homeAbbr = homeTeam.getString("abbreviation")
                    val awayAbbr = awayTeam.getString("abbreviation")

                    // Try to get seed from competitor object first, fall back to standings
                    val homeSeed = home.optInt("seed", 0).takeIf { it > 0 }
                        ?: home.optJSONObject("curatedRank")?.optInt("current", 0)?.takeIf { it > 0 }
                        ?: seedMap[homeAbbr] ?: 0
                    val awaySeed = away.optInt("seed", 0).takeIf { it > 0 }
                        ?: away.optJSONObject("curatedRank")?.optInt("current", 0)?.takeIf { it > 0 }
                        ?: seedMap[awayAbbr] ?: 0

                    val roundLabel = comp.optJSONArray("notes")?.let { notes ->
                        for (n in 0 until notes.length()) {
                            val h = notes.getJSONObject(n).optString("headline", "")
                            if (h.isNotBlank()) return@let h
                        }; null
                    } ?: ""
                    val seriesNote = comp.optJSONObject("series")?.optString("summary", "") ?: ""

                    val key = listOf(awayAbbr, homeAbbr).sorted().joinToString("-")

                    seriesMap.getOrPut(key) { mutableListOf() }.add(RawGame(
                        awayAbbr = awayAbbr, awayName = awayTeam.getString("displayName"),
                        awayLogo = awayTeam.optString("logo", "").ifBlank { awayTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "" },
                        awayScore = away.optString("score", "0"), awaySeed = awaySeed,
                        homeAbbr = homeAbbr, homeName = homeTeam.getString("displayName"),
                        homeLogo = homeTeam.optString("logo", "").ifBlank { homeTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "" },
                        homeScore = home.optString("score", "0"), homeSeed = homeSeed,
                        status = statusType.getString("shortDetail"),
                        roundLabel = roundLabel, seriesNote = seriesNote
                    ))
                }

                // Build PlayoffSeries list
                val bracket = seriesMap.map { (_, games) ->
                    val lastGame = games.last()
                    val note = games.mapNotNull { it.seriesNote.takeIf { n -> n.isNotBlank() } }.lastOrNull() ?: ""
                    val label = games.mapNotNull { it.roundLabel.takeIf { n -> n.isNotBlank() } }.firstOrNull() ?: ""
                    val roundName = label.replace(Regex("\\s*-\\s*Game\\s*\\d+"), "").trim()

                    // Get best seed values across all games in series
                    val allAbbrs = games.flatMap { listOf(it.awayAbbr, it.homeAbbr) }.distinct()
                    val seedByAbbr = mutableMapOf<String, Int>()
                    for (g in games) {
                        if (g.awaySeed > 0) seedByAbbr.putIfAbsent(g.awayAbbr, g.awaySeed)
                        if (g.homeSeed > 0) seedByAbbr.putIfAbsent(g.homeAbbr, g.homeSeed)
                    }

                    // Determine team1 (higher seed = lower number) and team2
                    val abbr1 = lastGame.awayAbbr
                    val abbr2 = lastGame.homeAbbr
                    val seed1 = seedByAbbr[abbr1] ?: seedMap[abbr1] ?: 0
                    val seed2 = seedByAbbr[abbr2] ?: seedMap[abbr2] ?: 0

                    // Parse wins
                    var t1Wins = 0; var t2Wins = 0
                    val wm = Regex("(\\w+)\\s+wins?\\s+series\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
                    val lm = Regex("(\\w+)\\s+leads?\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
                    val tm = Regex("tied\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
                    when {
                        wm != null -> { if (wm.groupValues[1].uppercase() == abbr1.uppercase()) { t1Wins = wm.groupValues[2].toInt(); t2Wins = wm.groupValues[3].toInt() } else { t2Wins = wm.groupValues[2].toInt(); t1Wins = wm.groupValues[3].toInt() } }
                        lm != null -> { if (lm.groupValues[1].uppercase() == abbr1.uppercase()) { t1Wins = lm.groupValues[2].toInt(); t2Wins = lm.groupValues[3].toInt() } else { t2Wins = lm.groupValues[2].toInt(); t1Wins = lm.groupValues[3].toInt() } }
                        tm != null -> { t1Wins = tm.groupValues[1].toInt(); t2Wins = t1Wins }
                    }

                    // Ensure higher seed (lower number) is team1 for consistent display
                    val shouldSwap = seed1 > 0 && seed2 > 0 && seed1 > seed2
                    if (shouldSwap) {
                        PlayoffSeries(
                            roundLabel = roundName.ifBlank { "Playoffs" },
                            team1Abbr = abbr2, team1Name = lastGame.homeName, team1Logo = lastGame.homeLogo,
                            team2Abbr = abbr1, team2Name = lastGame.awayName, team2Logo = lastGame.awayLogo,
                            team1Wins = t2Wins, team2Wins = t1Wins,
                            team1Seed = seed2, team2Seed = seed1,
                            seriesNote = note, isComplete = note.contains("wins"),
                            games = games.map { PlayoffGame(it.awayAbbr, it.awayScore, it.homeAbbr, it.homeScore, it.status) }
                        )
                    } else {
                        PlayoffSeries(
                            roundLabel = roundName.ifBlank { "Playoffs" },
                            team1Abbr = abbr1, team1Name = lastGame.awayName, team1Logo = lastGame.awayLogo,
                            team2Abbr = abbr2, team2Name = lastGame.homeName, team2Logo = lastGame.homeLogo,
                            team1Wins = t1Wins, team2Wins = t2Wins,
                            team1Seed = seed1, team2Seed = seed2,
                            seriesNote = note, isComplete = note.contains("wins"),
                            games = games.map { PlayoffGame(it.awayAbbr, it.awayScore, it.homeAbbr, it.homeScore, it.status) }
                        )
                    }
                }

                // Split into structured BracketData
                val west = bracket.filter { it.roundLabel.contains("West", true) }
                val east = bracket.filter { it.roundLabel.contains("East", true) }
                val finals = bracket.filter {
                    !it.roundLabel.contains("West", true) && !it.roundLabel.contains("East", true) &&
                    (it.roundLabel.contains("Final", true) || it.roundLabel.contains("Champion", true))
                }

                fun isR1(s: PlayoffSeries) = s.roundLabel.contains("1st", true) || s.roundLabel.contains("First", true)
                fun isSemis(s: PlayoffSeries) = s.roundLabel.contains("Semi", true) || s.roundLabel.contains("2nd", true) || s.roundLabel.contains("Second", true)
                fun isConfFinal(s: PlayoffSeries) = (s.roundLabel.contains("Final", true) || s.roundLabel.contains("3rd", true) || s.roundLabel.contains("Third", true)) && !s.roundLabel.contains("Semi", true)

                // Sort R1 by bracket position based on higher seed
                fun bracketSort(s: PlayoffSeries): Int {
                    val minSeed = minOf(s.team1Seed, s.team2Seed).takeIf { it > 0 } ?: return 99
                    return when (minSeed) { 1 -> 0; 4 -> 1; 3 -> 2; 2 -> 3; else -> minSeed }
                }
                // Sort later rounds by the minimum seed (higher-seeded matchup on top)
                fun laterRoundSort(s: PlayoffSeries): Int {
                    return minOf(s.team1Seed, s.team2Seed).takeIf { it > 0 } ?: 99
                }

                val bracketData = BracketData(
                    westR1 = west.filter { isR1(it) }.sortedBy { bracketSort(it) },
                    westR2 = west.filter { isSemis(it) }.sortedBy { laterRoundSort(it) },
                    westFinal = west.firstOrNull { isConfFinal(it) },
                    eastR1 = east.filter { isR1(it) }.sortedBy { bracketSort(it) },
                    eastR2 = east.filter { isSemis(it) }.sortedBy { laterRoundSort(it) },
                    eastFinal = east.firstOrNull { isConfFinal(it) },
                    championship = finals.firstOrNull()
                )

                // Keep flat list for backward compat, sorted same as before
                val sortedBracket = bracket.sortedBy { s ->
                    when {
                        isConfFinal(s) && !s.roundLabel.contains("West", true) && !s.roundLabel.contains("East", true) -> 0
                        isConfFinal(s) -> 1
                        isSemis(s) -> 2
                        isR1(s) -> 3
                        else -> 4
                    }
                }

                _uiState.update { it.copy(
                    playoffBracket = sortedBracket,
                    bracketData = bracketData,
                    isLoading = false,
                    isPostseason = bracket.isNotEmpty()
                ) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Playoffs: ${e.message}") }
            }
        }
    }

    private fun loadStandings(league: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sportPath = mapLeague(league)
                val raw = fetchUrl("https://site.api.espn.com/apis/v2/sports/$sportPath/standings")
                val data = JSONObject(raw)
                val children = data.optJSONArray("children") ?: JSONArray()
                val conferences = mutableListOf<StandingsConference>()

                for (i in 0 until children.length()) {
                    val conf = children.getJSONObject(i)
                    val entries = conf.optJSONObject("standings")?.optJSONArray("entries") ?: continue
                    val teams = mutableListOf<StandingsTeam>()
                    for (j in 0 until entries.length()) {
                        val entry = entries.getJSONObject(j)
                        val team = entry.getJSONObject("team")
                        val stats = entry.optJSONArray("stats") ?: JSONArray()
                        val sm = mutableMapOf<String, String>()
                        for (k in 0 until stats.length()) {
                            val s = stats.getJSONObject(k)
                            sm[s.getString("name")] = s.optString("displayValue", "")
                        }
                        teams.add(StandingsTeam(
                            name = team.getString("displayName"), abbr = team.getString("abbreviation"),
                            logo = team.optString("logo", "").ifBlank { team.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "" },
                            wins = sm["wins"] ?: "0", losses = sm["losses"] ?: "0",
                            pct = sm["winPercent"] ?: ".000", streak = sm["streak"] ?: "-",
                            seed = sm["playoffSeed"] ?: "", last10 = sm["Last Ten Games"] ?: ""
                        ))
                    }
                    // Sort by playoff seed (numeric)
                    val sortedTeams = teams.sortedBy { it.seed.toIntOrNull() ?: 99 }
                    conferences.add(StandingsConference(conf.getString("name"), sortedTeams))
                }

                _uiState.update { it.copy(conferences = conferences, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun mapLeague(league: String): String = when (league.lowercase()) {
        "nba" -> "basketball/nba"; "nhl" -> "hockey/nhl"; "mlb" -> "baseball/mlb"
        "nfl" -> "football/nfl"; "mls" -> "soccer/usa.1"; else -> "basketball/nba"
    }

    private fun fetchUrl(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        val text = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return text
    }
}
