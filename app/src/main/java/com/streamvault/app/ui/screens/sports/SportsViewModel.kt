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
    val seriesNote: String,
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

data class SportsUiState(
    val league: String = "nba",
    val view: String = "today", // today, standings, playoffs
    val games: List<SportsGame> = emptyList(),
    val conferences: List<StandingsConference> = emptyList(),
    val news: List<NewsArticle> = emptyList(),
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
            "playoffs" -> loadScores(_uiState.value.league)
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

    private fun loadScores(league: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sportPath = mapLeague(league)
                val raw = fetchUrl("https://site.api.espn.com/apis/site/v2/sports/$sportPath/scoreboard")
                val data = JSONObject(raw)
                val events = data.optJSONArray("events") ?: JSONArray()
                val seasonType = data.optJSONArray("leagues")?.optJSONObject(0)
                    ?.optJSONObject("season")?.optInt("type", 2) ?: 2

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

                    val seriesNote = comp.optJSONArray("notes")?.let { notes ->
                        for (n in 0 until notes.length()) {
                            val h = notes.getJSONObject(n).optString("headline", "")
                            if (h.contains("leads") || h.contains("tied") || h.contains("wins")) return@let h
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
                        seriesNote = seriesNote, homeQuarters = hq, awayQuarters = aq
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
                    conferences.add(StandingsConference(conf.getString("name"), teams))
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
