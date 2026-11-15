package com.gridironiq.sports.data.model

import kotlinx.serialization.Serializable

/**
 * Supported football leagues in the GridironIQ Gateway.
 */
@Serializable
enum class SportLeague(val displayName: String, val ratingLabel: String) {
    NFL("NFL Pro Football", "DVOA%"),
    NCAAF("NCAA College Football", "SP+")
}

/**
 * Unified telemetry snapshot combining all US Public Sports APIs for an NFL or NCAAF matchup:
 * - NFL: ESPN NFL Public API, nflfastR / nflverse (EPA/play, CPOE, Success Rate), DVOA, The Odds API, Open-Meteo
 * - NCAAF: CollegeFootballData API (CFBD), ESPN College Football API, The Odds API, Open-Meteo
 */
enum class SubGatewaySortOption(val label: String) {
    DATE_TIME("Date / Kickoff Time"),
    WIN_PROB_RATIO("Highest Win Probability % Ratio"),
    FOCUSED_BETS_FIRST("Focused / Bet-Initiated Games First"),
    VEGAS_VALUE_EDGE("Largest Model Edge vs. DraftKings Spread"),
    CLOSEST_TOSSUP("Tightest 50/50 Toss-Ups"),
    INJURY_IMPACT("Highest Starter Injury Impact"),
    OVER_UNDER_TOTAL("Highest Over/Under Total Points")
}

enum class SubGatewayFilterOption(val label: String) {
    ALL("All Weekly Games"),
    FOCUSED_ONLY("Bet-Initiated Only"),
    HIGH_CONFIDENCE("High Win % (≥60%)"),
    UPSET_VALUE("Underdog / Value Edge"),
    UPCOMING_ONLY("Upcoming Games"),
    ACTIVE_OR_FINAL("Live / Final (Hits & Misses)")
}

@Serializable
data class MatchupTelemetry(
    val id: String,
    val league: SportLeague = SportLeague.NCAAF,
    val scheduleDate: String = "Sun, Nov 15",
    val kickoffTime: String = "1:00 PM EST",
    val sortTimestamp: Long = 200L,
    val tvNetwork: String = "CBS",
    val conference: String,
    val venue: String,
    val weather: StadiumWeather,
    val vegasSpreadHome: Double,
    val overUnder: Double,
    val draftKingsLine: String = "",
    val vegasImpliedHomeProb: Double,
    val espnFpiHomeProb: Double,
    val isCompleted: Boolean = false,
    val isInProgress: Boolean = false,
    val liveClockDetail: String = "UPCOMING",
    val actualAwayScore: Int? = null,
    val actualHomeScore: Int? = null,
    val isBetInitiated: Boolean = false,
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
    val epaOff: Double,          // Offensive Expected Points Added per play (nflfastR / CFBD)
    val epaDef: Double,          // Defensive Expected Points Added allowed per play (negative is better)
    val spPlus: Double,          // Overall Power/Efficiency Rating (SP+ for NCAAF, DVOA% for NFL)
    val successRateOff: Double,  // % of plays meeting down/distance efficiency thresholds
    val successRateDef: Double,  // Defensive success rate allowed
    val explosiveness: Double,   // IsoPPP / Explosive play rate
    val havocRateDef: Double,    // Defensive Havoc % (NCAAF) or Pass-Rush Pressure/Win Rate % (NFL)
    val thirdDownPct: Double,    // 3rd down conversion rate
    val redZoneTdPct: Double,    // Red zone touchdown conversion percentage
    val turnoverMargin: Int,     // Net season turnover margin
    val sacksPerGame: Double,    // Sacks generated per game
    val yardsPerPlayOff: Double, // Offensive yards per play
    val yardsPerPlayDef: Double, // Defensive yards allowed per play
    val recentForm: List<String>
)

@Serializable
data class PlayerInjuryStatus(
    val id: String,
    val team: String,            // "away" or "home"
    val player: String,
    val position: String,
    val officialStatus: String,  // "Active", "Probable", "Questionable", "Doubtful", "Out", "IR"
    val impactPoints: Double,    // Point spread value of this player (e.g., NFL/CFB QB1 = 6.5 - 7.0 pts)
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

@Serializable
data class FactorWeights(
    val efficiencyWeight: Float = 30f,   // EPA/Play, DVOA/SP+, Success Rate
    val injuryWeight: Float = 25f,       // Starter & QB availability
    val trenchWeight: Float = 15f,       // Pass-Rush Win % / Havoc Rate, Sacks, 3rd Down %
    val turnoverFormWeight: Float = 15f, // Turnover Margin, Red Zone TD%, H2H
    val situationalWeight: Float = 15f   // Home field advantage, Stadium Weather
)

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
    val winProbShiftHome: Double,
    val metricComparisonSummary: String
)

@Serializable
data class CitedExplanationBlock(
    val heading: String,
    val apiCitationTag: String,
    val bodyText: String
)
