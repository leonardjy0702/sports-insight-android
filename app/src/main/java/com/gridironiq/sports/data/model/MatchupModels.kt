package com.gridironiq.sports.data.model

import kotlinx.serialization.Serializable

/**
 * Unified telemetry snapshot combining all US Public Sports APIs for a College Football (NCAAF) matchup:
 * 1. CollegeFootballData API (CFBD): EPA/play, SP+ rating, Success Rate, Havoc Rate, Returning Production
 * 2. ESPN Public Site API: Live Scoreboard, Injury Report, Depth Chart Status, ESPN FPI Predictor
 * 3. The Odds API: Consensus US Sportsbook Spread, Over/Under, Implied Market Win Probability
 * 4. Open-Meteo Weather API: Kickoff temperature, sustained wind speed, precipitation probability
 */
@Serializable
data class MatchupTelemetry(
    val id: String,
    val conference: String,
    val venue: String,
    val weather: StadiumWeather,
    val vegasSpreadHome: Double,
    val overUnder: Double,
    val vegasImpliedHomeProb: Double,
    val espnFpiHomeProb: Double,
    val away: TeamSabermetrics,
    val home: TeamSabermetrics,
    val h2h: HeadToHeadHistory,
    val injuries: List<PlayerInjuryStatus>
)

@Serializable
data class TeamSabermetrics(
    val abbr: String,
    val name: String,
    val rank: String,
    val record: String,
    val colorHex: Long,
    val epaOff: Double,          // CFBD: Offensive Expected Points Added per play
    val epaDef: Double,          // CFBD: Defensive Expected Points Added allowed per play (negative is better)
    val spPlus: Double,          // CFBD: Bill Connelly SP+ overall efficiency rating
    val successRateOff: Double,  // CFBD: % of plays meeting down/distance efficiency thresholds
    val successRateDef: Double,  // CFBD: Defensive success rate allowed
    val explosiveness: Double,   // CFBD: IsoPPP (points added on successful plays)
    val havocRateDef: Double,    // CFBD: % of plays with TFL, forced fumble, INT, or PBU
    val thirdDownPct: Double,    // ESPN Boxscore: 3rd down conversion rate
    val redZoneTdPct: Double,    // ESPN/NCAA: Red zone touchdown conversion percentage
    val turnoverMargin: Int,     // NCAA Standings: Net season turnover margin
    val sacksPerGame: Double,    // ESPN Team Stats: Sacks generated per game
    val yardsPerPlayOff: Double, // ESPN Team Stats: Offensive yards per play
    val yardsPerPlayDef: Double, // ESPN Team Stats: Defensive yards allowed per play
    val recentForm: List<String>
)

@Serializable
data class PlayerInjuryStatus(
    val id: String,
    val team: String,            // "away" or "home"
    val player: String,
    val position: String,
    val officialStatus: String,  // "Active", "Probable", "Questionable", "Doubtful", "Out"
    val impactPoints: Double,    // Point spread value of this player (e.g., QB1 = 6.5 pts, LT1 = 2.6 pts)
    val isAvailableToPlay: Boolean,
    val clinicalNote: String
)

@Serializable
data class StadiumWeather(
    val tempFahrenheit: Int,
    val windMph: Int,
    val condition: String,
    val precipProbabilityPct: Int,
    val isDome: Boolean
)

@Serializable
data class HeadToHeadHistory(
    val allTimeSeries: String,
    val lastMeetingSummary: String,
    val currentStreak: String
)

/**
 * Transparent user-adjustable weights for the LLM & Sabermetric reasoning engine.
 */
@Serializable
data class FactorWeights(
    val efficiencyWeight: Float = 30f,   // EPA/Play, SP+, Success Rate
    val injuryWeight: Float = 25f,       // Starter & QB availability
    val trenchWeight: Float = 15f,       // Havoc Rate, Sacks, 3rd Down %
    val turnoverFormWeight: Float = 15f, // Turnover Margin, Red Zone TD%, H2H
    val situationalWeight: Float = 15f   // Home field advantage, Stadium Weather
)

/**
 * Structured output returned by the Google Gemini LLM (`responseSchema`) and deterministic fallback.
 * Explains *why* the winner is picked and quantifies each metric's exact contribution.
 */
@Serializable
data class LlmPredictionResult(
    val predictedWinnerName: String,
    val predictedWinnerAbbr: String,
    val homeWinProbability: Double,
    val awayWinProbability: Double,
    val projectedHomeScore: Int,
    val projectedAwayScore: Int,
    val impliedHomeMargin: Double,
    val confidenceTier: String,
    val factorAttributions: List<FactorAttribution>,
    val narrativeCitations: List<CitedExplanationBlock>,
    val isLiveGeminiResponse: Boolean = false
)

@Serializable
data class FactorAttribution(
    val id: String,
    val title: String,
    val apiSource: String,
    val normalizedWeightPct: Int,
    val winProbShiftHome: Double, // Signed percentage points (+ favors Home, - favors Away)
    val metricComparisonSummary: String
)

@Serializable
data class CitedExplanationBlock(
    val heading: String,
    val apiCitationTag: String,
    val bodyText: String
)
