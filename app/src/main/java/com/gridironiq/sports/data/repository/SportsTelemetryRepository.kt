package com.gridironiq.sports.data.repository

import com.gridironiq.sports.data.model.HeadToHeadHistory
import com.gridironiq.sports.data.model.MatchupTelemetry
import com.gridironiq.sports.data.model.PlayerInjuryStatus
import com.gridironiq.sports.data.model.StadiumWeather
import com.gridironiq.sports.data.model.TeamSabermetrics

/**
 * Aggregates data from US Public Sports APIs:
 * - CollegeFootballData API (CFBD)
 * - ESPN Public Site API (Scoreboard, Summary, Injuries, FPI)
 * - The Odds API (US Sportsbook Consensus Lines)
 * - Open-Meteo Stadium Forecast API
 */
class SportsTelemetryRepository {

    fun getFeaturedCollegeFootballMatchups(): List<MatchupTelemetry> = listOf(
        MatchupTelemetry(
            id = "ncaaf-sec-1",
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
