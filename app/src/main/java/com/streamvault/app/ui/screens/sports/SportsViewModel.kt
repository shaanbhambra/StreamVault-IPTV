package com.streamvault.app.ui.screens.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

data class BoxScoreData(
    val homeAbbr: String, val awayAbbr: String,
    val homeQuarters: List<String>, val awayQuarters: List<String>,
    val homeTotal: String, val awayTotal: String,
    val stats: List<BoxScoreStat>
)

data class PlayoffSeries(
    val roundLabel: String, // "East 1st Round", "West Semifinals", etc.
    val team1Abbr: String, val team1Name: String, val team1Logo: String,
    val team2Abbr: String, val team2Name: String, val team2Logo: String,
    val team1Wins: Int, val team2Wins: Int,
    val seriesNote: String, // "DET wins series 4-3"
    val isComplete: Boolean,
    val games: List<PlayoffGame>
)

data class PlayoffGame(
    val awayAbbr: String, val awayScore: String,
    val homeAbbr: String, val homeScore: String,
    val status: String
)

data class SportsUiState(
    val league: String = "nba",
    val view: String = "today",
    val games: List<SportsGame> = emptyList(),
    val conferences: List<StandingsConference> = emptyList(),
    val news: List<NewsArticle> = emptyList(),
    val playoffBracket: List<PlayoffSeries> = emptyList(),
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
            "playoffs" -> loadScores(league)
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

                // Stats from boxscore
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

                _uiState.update { it.copy(boxScore = BoxScoreData(homeAbbr, awayAbbr, homeQ, awayQ, homeTotal, awayTotal, stats)) }
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
                        homeLogo = homeTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "",
                        homeColor = homeTeam.optString("color", "333"), homeRecord = home.optJSONArray("records")?.optJSONObject(0)?.optString("summary", "") ?: "",
                        awayTeam = awayTeam.getString("displayName"), awayAbbr = awayTeam.getString("abbreviation"),
                        awayScore = away.optString("score", "0"),
                        awayLogo = awayTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "",
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
                // Fetch last 30 days of playoff games
                val cal = java.util.Calendar.getInstance()
                val endDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(cal.time)
                cal.add(java.util.Calendar.DAY_OF_YEAR, -45)
                val startDate = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(cal.time)

                val raw = fetchUrl("https://site.api.espn.com/apis/site/v2/sports/$sportPath/scoreboard?dates=$startDate-$endDate&limit=200")
                val data = JSONObject(raw)
                val events = data.optJSONArray("events") ?: JSONArray()

                // Group games by series matchup
                data class RawGame(val awayAbbr: String, val awayName: String, val awayLogo: String, val awayScore: String,
                                   val homeAbbr: String, val homeName: String, val homeLogo: String, val homeScore: String,
                                   val status: String, val roundLabel: String, val seriesNote: String)

                val seriesMap = mutableMapOf<String, MutableList<RawGame>>()

                for (i in 0 until events.length()) {
                    val event = events.getJSONObject(i)
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

                    val roundLabel = comp.optJSONArray("notes")?.let { notes ->
                        for (n in 0 until notes.length()) notes.getJSONObject(n).optString("headline", "").takeIf { it.isNotBlank() }?.let { return@let it }; null
                    } ?: ""
                    val seriesNote = comp.optJSONObject("series")?.optString("summary", "") ?: ""

                    val key = listOf(awayTeam.getString("abbreviation"), homeTeam.getString("abbreviation")).sorted().joinToString("-")

                    // Store the round label from the FIRST game that has one (don't overwrite with empty)
                    val existing = seriesMap[key]
                    if (existing != null && existing.isNotEmpty() && roundLabel.isBlank()) {
                        // Keep existing round label, don't overwrite
                    }

                    seriesMap.getOrPut(key) { mutableListOf() }.add(RawGame(
                        awayAbbr = awayTeam.getString("abbreviation"), awayName = awayTeam.getString("displayName"),
                        awayLogo = awayTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "",
                        awayScore = away.optString("score", "0"),
                        homeAbbr = homeTeam.getString("abbreviation"), homeName = homeTeam.getString("displayName"),
                        homeLogo = homeTeam.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "",
                        homeScore = home.optString("score", "0"),
                        status = statusType.getString("shortDetail"),
                        roundLabel = roundLabel, seriesNote = seriesNote
                    ))
                }

                // Build PlayoffSeries list
                val bracket = seriesMap.map { (_, games) ->
                    val lastGame = games.last()
                    val note = games.mapNotNull { it.seriesNote.takeIf { n -> n.isNotBlank() } }.lastOrNull() ?: ""
                    val label = games.mapNotNull { it.roundLabel.takeIf { n -> n.isNotBlank() } }.firstOrNull() ?: ""
                    // Extract round name (e.g., "East Semifinals" from "East Semifinals - Game 6")
                    val roundName = label.replace(Regex("\\s*-\\s*Game\\s*\\d+"), "").trim()

                    // Parse wins
                    var t1Wins = 0; var t2Wins = 0
                    val t1 = lastGame.awayAbbr; val t2 = lastGame.homeAbbr
                    val wm = Regex("(\\w+)\\s+wins?\\s+series\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
                    val lm = Regex("(\\w+)\\s+leads?\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
                    val tm = Regex("tied\\s+(\\d+)-(\\d+)", RegexOption.IGNORE_CASE).find(note)
                    when {
                        wm != null -> { if (wm.groupValues[1].uppercase() == t1.uppercase()) { t1Wins = wm.groupValues[2].toInt(); t2Wins = wm.groupValues[3].toInt() } else { t2Wins = wm.groupValues[2].toInt(); t1Wins = wm.groupValues[3].toInt() } }
                        lm != null -> { if (lm.groupValues[1].uppercase() == t1.uppercase()) { t1Wins = lm.groupValues[2].toInt(); t2Wins = lm.groupValues[3].toInt() } else { t2Wins = lm.groupValues[2].toInt(); t1Wins = lm.groupValues[3].toInt() } }
                        tm != null -> { t1Wins = tm.groupValues[1].toInt(); t2Wins = t1Wins }
                    }

                    PlayoffSeries(
                        roundLabel = roundName.ifBlank { "Playoffs" },
                        team1Abbr = t1, team1Name = lastGame.awayName, team1Logo = lastGame.awayLogo,
                        team2Abbr = t2, team2Name = lastGame.homeName, team2Logo = lastGame.homeLogo,
                        team1Wins = t1Wins, team2Wins = t2Wins,
                        seriesNote = note, isComplete = note.contains("wins"),
                        games = games.map { PlayoffGame(it.awayAbbr, it.awayScore, it.homeAbbr, it.homeScore, it.status) }
                    )
                }.sortedBy { s ->
                    // Sort: Finals > Conf Finals > Semifinals > 1st Round
                    when {
                        s.roundLabel.contains("Final", true) && !s.roundLabel.contains("Semi", true) -> 0
                        s.roundLabel.contains("Semi", true) -> 1
                        s.roundLabel.contains("1st", true) || s.roundLabel.contains("First", true) -> 2
                        else -> 3
                    }
                }

                _uiState.update { it.copy(playoffBracket = bracket, isLoading = false, isPostseason = bracket.isNotEmpty()) }
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
                            logo = team.optJSONArray("logos")?.optJSONObject(0)?.optString("href", "") ?: "",
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
