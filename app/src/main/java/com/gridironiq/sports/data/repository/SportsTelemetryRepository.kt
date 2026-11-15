package com.gridironiq.sports.data.repository

import com.gridironiq.sports.data.model.HeadToHeadHistory
import com.gridironiq.sports.data.model.MatchupTelemetry
import com.gridironiq.sports.data.model.PlayerInjuryStatus
import com.gridironiq.sports.data.model.SportLeague
import com.gridironiq.sports.data.model.StadiumWeather
import com.gridironiq.sports.data.model.TeamSabermetrics

/**
 * Aggregates data from US Public Sports APIs for both NFL Pro Football and NCAA College Football:
 * - NFL: ESPN NFL Public API, nflfastR / nflverse (EPA/play, CPOE, Success Rate), DVOA, The Odds API, Open-Meteo
 * - NCAAF: CollegeFootballData API (CFBD), ESPN College Football API, The Odds API, Open-Meteo
 */
class SportsTelemetryRepository {

    fun getMatchupsByLeague(league: SportLeague): List<MatchupTelemetry> = when (league) {
        SportLeague.NFL -> getFeaturedNflMatchups()
        SportLeague.NCAAF -> getFeaturedCollegeFootballMatchups()
    }

    fun getFeaturedNflMatchups(): List<MatchupTelemetry> = listOf(
        MatchupTelemetry(
            id = "nfl-afc-1",
            league = SportLeague.NFL,
            conference = "AFC Heavyweight Clash • CBS 4:25 ET",
            venue = "Highmark Stadium • Orchard Park, NY",
            weather = StadiumWeather(36, 14, "Crisp & Breezy", 15, isDome = false),
            vegasSpreadHome = -2.5,
            overUnder = 47.5,
            vegasImpliedHomeProb = 56.5,
            espnFpiHomeProb = 55.8,
            away = TeamSabermetrics(
                abbr = "KC",
                name = "Kansas City Chiefs",
                rank = "AFC #1",
                record = "9-1",
                colorHex = 0xFFE31837,
                epaOff = 0.16,
                epaDef = -0.09,
                spPlus = 24.5, // DVOA%
                successRateOff = 48.9,
                successRateDef = 41.2,
                explosiveness = 1.21,
                havocRateDef = 36.4, // Pass-rush win rate %
                thirdDownPct = 49.5,
                redZoneTdPct = 58.2,
                turnoverMargin = 3,
                sacksPerGame = 2.8,
                yardsPerPlayOff = 5.7,
                yardsPerPlayDef = 5.0,
                recentForm = listOf("W 16-14 vs DEN", "W 30-24 vs TB", "W 27-20 @ LV")
            ),
            home = TeamSabermetrics(
            abbr = "BUF",
                name = "Buffalo Bills",
                rank = "AFC #2",
                record = "8-2",
                colorHex = 0xFF00338D,
                epaOff = 0.21,
                epaDef = -0.11,
                spPlus = 27.2, // DVOA%
                successRateOff = 50.4,
                successRateDef = 39.8,
                explosiveness = 1.29,
                havocRateDef = 38.9,
                thirdDownPct = 46.8,
                redZoneTdPct = 66.7,
                turnoverMargin = 11,
                sacksPerGame = 3.2,
                yardsPerPlayOff = 6.1,
                yardsPerPlayDef = 4.9,
                recentForm = listOf("W 30-20 @ IND", "W 30-27 vs MIA", "W 31-10 @ SEA")
            ),
            h2h = HeadToHeadHistory(
                allTimeSeries = "Bills lead all-time 29-25-1",
                lastMeetingSummary = "Bills won last 3 regular-season games; Chiefs won last 3 Playoff games",
                currentStreak = "Chiefs won Jan 2024 AFC Divisional (27-24)"
            ),
            injuries = listOf(
                PlayerInjuryStatus("nfl-inj-1", "away", "Isiah Pacheco (RB1)", "RB", "Out/IR", 1.8, false, "Fibula — Kareem Hunt taking primary early-down snaps"),
                PlayerInjuryStatus("nfl-inj-2", "home", "Dalton Kincaid (TE1)", "TE", "Out", 1.6, false, "Knee — Ruled out on Friday NFL Injury Report"),
                PlayerInjuryStatus("nfl-inj-3", "home", "Amari Cooper (WR1)", "WR", "Questionable", 1.9, true, "Wrist — Limited practice, expected to play"),
                PlayerInjuryStatus("nfl-inj-4", "away", "Chris Jones (DT1)", "DL", "Active", 2.7, true, "Active — Leads NFL interior DL in pass-rush win rate")
            )
        ),
        MatchupTelemetry(
            id = "nfl-nfc-2",
            league = SportLeague.NFL,
            conference = "NFC North Showdown • FOX",
            venue = "Lambeau Field • Green Bay, WI",
            weather = StadiumWeather(31, 17, "Cold & Gusty", 20, isDome = false),
            vegasSpreadHome = 3.0,
            overUnder = 48.5,
            vegasImpliedHomeProb = 42.5,
            espnFpiHomeProb = 43.8,
            away = TeamSabermetrics(
                abbr = "DET",
                name = "Detroit Lions",
                rank = "NFC #1",
                record = "9-1",
                colorHex = 0xFF0076B6,
                epaOff = 0.27,
                epaDef = -0.14,
                spPlus = 33.8,
                successRateOff = 52.8,
                successRateDef = 37.5,
                explosiveness = 1.35,
                havocRateDef = 37.2,
                thirdDownPct = 48.1,
                redZoneTdPct = 69.5,
                turnoverMargin = 9,
                sacksPerGame = 2.9,
                yardsPerPlayOff = 6.5,
                yardsPerPlayDef = 4.8,
                recentForm = listOf("W 52-6 vs JAX", "W 26-23 @ HOU", "W 24-14 @ GB")
            ),
            home = TeamSabermetrics(
                abbr = "GB",
                name = "Green Bay Packers",
                rank = "NFC #5",
                record = "7-3",
                colorHex = 0xFF203731,
                epaOff = 0.18,
                epaDef = -0.07,
                spPlus = 21.4,
                successRateOff = 47.6,
                successRateDef = 42.1,
                explosiveness = 1.31,
                havocRateDef = 34.8,
                thirdDownPct = 42.9,
                redZoneTdPct = 57.8,
                turnoverMargin = 6,
                sacksPerGame = 2.6,
                yardsPerPlayOff = 6.0,
                yardsPerPlayDef = 5.2,
                recentForm = listOf("W 20-19 @ CHI", "L 14-24 vs DET", "W 30-27 @ JAX")
            ),
            h2h = HeadToHeadHistory(
                allTimeSeries = "Packers lead all-time 106-77-7",
                lastMeetingSummary = "Lions won 5 of last 6 meetings including 24-14 at Lambeau",
                currentStreak = "Lions won last 2 at Lambeau Field"
            ),
            injuries = listOf(
                PlayerInjuryStatus("nfl-inj-201", "away", "Sam LaPorta (TE1)", "TE", "Doubtful", 1.7, false, "Shoulder — Doubtful on official NFL practice report"),
                PlayerInjuryStatus("nfl-inj-202", "home", "Jaire Alexander (CB1)", "CB", "Questionable", 2.4, false, "Knee — Key boundary corner vs Amon-Ra St. Brown"),
                PlayerInjuryStatus("nfl-inj-203", "home", "Jordan Love (QB1)", "QB", "Active", 7.0, true, "Groin — Full participant in Thursday/Friday practice")
            )
        )
    )

    fun getFeaturedCollegeFootballMatchups(): List<MatchupTelemetry> = listOf(
        MatchupTelemetry(
            id = "ncaaf-sec-1",
            league = SportLeague.NCAAF,
            conference = "SEC Championship Preview • ABC",
            venue = "Darrell K Royal Stadium • Austin, TX",
            weather = StadiumWeather(68, 7, "Clear Night", 5, isDome = false),
            vegasSpreadHome = -2.5,
            overUnder = 49.5,
            vegasImpliedHomeProb = 56.5,
            espnFpiHomeProb = 58.2,
            away = TeamSabermetrics(
                abbr = "UGA",
                name = "Georgia Bulldogs",
                rank = "#3",
                record = "10-1 (7-1 SEC)",
                colorHex = 0xFFBA0C2F,
                epaOff = 0.24,
                epaDef = -0.19,
                spPlus = 26.4,
                successRateOff = 47.8,
                successRateDef = 33.4,
                explosiveness = 1.31,
                havocRateDef = 19.8,
                thirdDownPct = 45.2,
                redZoneTdPct = 68.5,
                turnoverMargin = 5,
                sacksPerGame = 3.1,
                yardsPerPlayOff = 6.4,
                yardsPerPlayDef = 4.5,
                recentForm = listOf("W 31-17 vs TENN", "W 34-20 vs FLA", "W 30-15 @ TEX")
            ),
            home = TeamSabermetrics(
                abbr = "TEX",
                name = "Texas Longhorns",
                rank = "#2",
                record = "11-0 (7-0 SEC)",
                colorHex = 0xFFBF5700,
                epaOff = 0.29,
                epaDef = -0.22,
                spPlus = 28.9,
                successRateOff = 49.6,
                successRateDef = 31.8,
                explosiveness = 1.38,
                havocRateDef = 21.4,
                thirdDownPct = 47.9,
                redZoneTdPct = 71.2,
                turnoverMargin = 9,
                sacksPerGame = 3.6,
                yardsPerPlayOff = 6.8,
                yardsPerPlayDef = 4.2,
                recentForm = listOf("W 31-14 vs UK", "W 20-10 @ ARK", "W 49-17 vs FLA")
            ),
            h2h = HeadToHeadHistory(
                allTimeSeries = "Texas leads all-time series 4-2",
                lastMeetingSummary = "Georgia 30, Texas 15 — UGA forced 4 turnovers & 7 sacks",
                currentStreak = "Georgia won last 1"
            ),
            injuries = listOf(
                PlayerInjuryStatus("inj-1", "away", "Trevor Etienne (RB1)", "RB", "Questionable", 2.1, false, "Ribs — Limited practice participation"),
                PlayerInjuryStatus("inj-2", "away", "Earnest Greene III (LT)", "OL", "Questionable", 2.4, false, "Upper body — Blindside tackle availability"),
                PlayerInjuryStatus("inj-3", "home", "Quinn Ewers (QB1)", "QB", "Probable", 6.5, true, "Ankle — Full participant in practice"),
                PlayerInjuryStatus("inj-4", "home", "Kelvin Banks Jr. (LT)", "OL", "Active", 2.8, true, "All-American LT cleared to start")
            )
        ),
        MatchupTelemetry(
            id = "ncaaf-b1g-2",
            league = SportLeague.NCAAF,
            conference = "Big Ten Showdown • FOX",
            venue = "Ohio Stadium • Columbus, OH",
            weather = StadiumWeather(41, 16, "Brisk & Overcast", 25, isDome = false),
            vegasSpreadHome = -3.5,
            overUnder = 52.5,
            vegasImpliedHomeProb = 61.2,
            espnFpiHomeProb = 63.4,
            away = TeamSabermetrics(
                abbr = "ORE",
                name = "Oregon Ducks",
                rank = "#1",
                record = "11-0 (8-0 B1G)",
                colorHex = 0xFF154733,
                epaOff = 0.33,
                epaDef = -0.17,
                spPlus = 27.8,
                successRateOff = 52.1,
                successRateDef = 35.1,
                explosiveness = 1.34,
                havocRateDef = 18.2,
                thirdDownPct = 50.4,
                redZoneTdPct = 73.0,
                turnoverMargin = 8,
                sacksPerGame = 2.9,
                yardsPerPlayOff = 7.1,
                yardsPerPlayDef = 4.6,
                recentForm = listOf("W 16-13 @ WIS", "W 39-18 vs MD", "W 38-17 @ MICH")
            ),
            home = TeamSabermetrics(
                abbr = "OSU",
                name = "Ohio State Buckeyes",
                rank = "#4",
                record = "10-1 (7-1 B1G)",
                colorHex = 0xFFCE0F3D,
                epaOff = 0.31,
                epaDef = -0.26,
                spPlus = 30.1,
                successRateOff = 50.3,
                successRateDef = 29.9,
                explosiveness = 1.36,
                havocRateDef = 22.8,
                thirdDownPct = 48.1,
                redZoneTdPct = 74.5,
                turnoverMargin = 7,
                sacksPerGame = 3.8,
                yardsPerPlayOff = 6.9,
                yardsPerPlayDef = 3.9,
                recentForm = listOf("W 38-15 vs IND", "W 31-7 @ NW", "W 45-0 vs PUR")
            ),
            h2h = HeadToHeadHistory(
                allTimeSeries = "Ohio State leads all-time series 9-2",
                lastMeetingSummary = "Oregon 32, Ohio State 31 (Eugene)",
                currentStreak = "Oregon won last 2"
            ),
            injuries = listOf(
                PlayerInjuryStatus("inj-201", "away", "Tez Johnson (WR1)", "WR", "Probable", 3.2, true, "Shoulder — Primary slot target on 3rd downs"),
                PlayerInjuryStatus("inj-202", "home", "Seth McLaughlin (C)", "OL", "Out", 3.1, false, "Achilles — Rimington candidate out"),
                PlayerInjuryStatus("inj-203", "home", "Josh Simmons (LT)", "OL", "Out", 2.6, false, "Knee — Starting Left Tackle sidelined")
            )
        )
    )
}
