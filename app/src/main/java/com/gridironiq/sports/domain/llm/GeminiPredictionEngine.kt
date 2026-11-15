package com.gridironiq.sports.domain.llm

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.gridironiq.sports.data.model.CitedExplanationBlock
import com.gridironiq.sports.data.model.FactorAttribution
import com.gridironiq.sports.data.model.FactorWeights
import com.gridironiq.sports.data.model.LlmPredictionResult
import com.gridironiq.sports.data.model.MatchupTelemetry
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Transparent Sabermetric + Google Gemini (`gemini-2.5-flash`) Prediction Engine.
 *
 * Ensures complete transparency into HOW the LLM picks the winner:
 * 1. Computes exact, auditable factor-by-factor win probability shifts from each US Sports API metric
 *    (EPA/play, SP+, Injury Impact Points, Havoc Rate, 3rd Down %, Turnover Margin, Stadium Weather).
 * 2. Feeds the structured feature vector + computed shifts into Google Gemini 2.5 Flash to synthesize
 *    a metric-cited chain-of-thought narrative explaining why the winner was chosen.
 */
class GeminiPredictionEngine(
    private val geminiApiKey: String = ""
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Evaluates the matchup deterministically first so every percentage point shift is auditable,
     * and optionally enriches the explanation using live Google Gemini (`gemini-2.5-flash`).
     */
    suspend fun evaluateMatchup(
        matchup: MatchupTelemetry,
        weights: FactorWeights,
        invokeLiveGemini: Boolean = false
    ): LlmPredictionResult {
        val totalW = (weights.efficiencyWeight + weights.injuryWeight +
                weights.trenchWeight + weights.turnoverFormWeight + weights.situationalWeight)
            .coerceAtLeast(1f)

        val normEff = (weights.efficiencyWeight / totalW) / 0.30f
        val normInj = (weights.injuryWeight / totalW) / 0.25f
        val normTrench = (weights.trenchWeight / totalW) / 0.15f
        val normForm = (weights.turnoverFormWeight / totalW) / 0.15f
        val normSit = (weights.situationalWeight / totalW) / 0.15f

        val away = matchup.away
        val home = matchup.home

        // 1. Efficiency Differential (CFBD EPA/Play + SP+ + Success Rate)
        val homeNetEpa = home.epaOff - home.epaDef
        val awayNetEpa = away.epaOff - away.epaDef
        val epaDiff = (homeNetEpa - awayNetEpa) * 38.0
        val spDiff = (home.spPlus - away.spPlus) * 1.35
        val srDiff = ((home.successRateOff - away.successRateOff) + (away.successRateDef - home.successRateDef)) * 0.45
        val rawEffShift = (epaDiff * 0.45 + spDiff * 0.35 + srDiff * 0.20) * normEff

        // 2. Player Injuries & QB Availability Shift (ESPN /summary Injury Feed)
        val homeMissingPts = matchup.injuries.filter { it.team == "home" && !it.isAvailableToPlay }.sumOf { it.impactPoints }
        val awayMissingPts = matchup.injuries.filter { it.team == "away" && !it.isAvailableToPlay }.sumOf { it.impactPoints }
        val rawInjShift = ((awayMissingPts - homeMissingPts) * 2.4) * normInj

        // 3. Trench Warfare (CFBD Havoc Rate, Sacks/Game, 3rd Down %)
        val havocDiff = (home.havocRateDef - away.havocRateDef) * 0.85
        val sackDiff = (home.sacksPerGame - away.sacksPerGame) * 2.2
        val thirdDownDiff = (home.thirdDownPct - away.thirdDownPct) * 0.45
        val rawTrenchShift = (havocDiff + sackDiff + thirdDownDiff) * 0.65 * normTrench

        // 4. Turnovers & Red Zone TD Efficiency (NCAA / ESPN Boxscore)
        val toDiff = (home.turnoverMargin - away.turnoverMargin) * 0.55
        val rzDiff = (home.redZoneTdPct - away.redZoneTdPct) * 0.35
        val rawFormShift = (toDiff + rzDiff) * normForm

        // 5. Situational & Stadium Weather (Open-Meteo API)
        val weatherMod = if (!matchup.weather.isDome && matchup.weather.windMph >= 14) {
            if (home.havocRateDef >= away.havocRateDef) 1.4 else -1.4
        } else 0.0
        val rawSitShift = (4.2 + weatherMod) * normSit

        val totalShift = rawEffShift + rawInjShift + rawTrenchShift + rawFormShift + rawSitShift
        val homeWinProb = min(92.0, max(8.0, 50.0 + totalShift))
        val awayWinProb = 100.0 - homeWinProb

        val impliedHomeMargin = (homeWinProb - 50.0) * 0.36
        val rawHomeScore = ((matchup.overUnder / 2.0) + (impliedHomeMargin / 2.0)).roundToInt()
        val rawAwayScore = ((matchup.overUnder / 2.0) - (impliedHomeMargin / 2.0)).roundToInt()

        val homeWins = homeWinProb >= 50.0
        val winner = if (homeWins) home else away
        val winnerProb = max(homeWinProb, awayWinProb)

        val factors = listOf(
            FactorAttribution(
                id = "eff",
                title = "Offensive & Defensive Efficiency (EPA/Play & SP+)",
                apiSource = "CFBD /stats/season/advanced",
                normalizedWeightPct = ((weights.efficiencyWeight / totalW) * 100).roundToInt(),
                winProbShiftHome = rawEffShift,
                metricComparisonSummary = "${home.abbr} Net EPA ${formatSigned(homeNetEpa)} (SP+ ${home.spPlus}) vs ${away.abbr} Net EPA ${formatSigned(awayNetEpa)} (SP+ ${away.spPlus})"
            ),
            FactorAttribution(
                id = "inj",
                title = "Starter Injury & QB Availability Impact",
                apiSource = "ESPN /summary Injuries API",
                normalizedWeightPct = ((weights.injuryWeight / totalW) * 100).roundToInt(),
                winProbShiftHome = rawInjShift,
                metricComparisonSummary = "Unavailable starter impact: ${away.abbr} -${"%.1f".format(awayMissingPts)} pts vs ${home.abbr} -${"%.1f".format(homeMissingPts)} pts"
            ),
            FactorAttribution(
                id = "trench",
                title = "Trench Warfare (Havoc Rate, Sacks & 3rd Down %)",
                apiSource = "CFBD & ESPN Advanced Boxscore",
                normalizedWeightPct = ((weights.trenchWeight / totalW) * 100).roundToInt(),
                winProbShiftHome = rawTrenchShift,
                metricComparisonSummary = "Def Havoc: ${home.abbr} ${home.havocRateDef}% (${home.sacksPerGame} sacks/g) vs ${away.abbr} ${away.havocRateDef}% (${away.sacksPerGame} sacks/g)"
            ),
            FactorAttribution(
                id = "form",
                title = "Turnover Margin, Red Zone TD% & H2H History",
                apiSource = "NCAA / ESPN Standings API",
                normalizedWeightPct = ((weights.turnoverFormWeight / totalW) * 100).roundToInt(),
                winProbShiftHome = rawFormShift,
                metricComparisonSummary = "TO Margin: ${home.abbr} (${home.turnoverMargin}) vs ${away.abbr} (${away.turnoverMargin}) • RZ TD%: ${home.redZoneTdPct}% vs ${away.redZoneTdPct}%"
            ),
            FactorAttribution(
                id = "sit",
                title = "Home Field Environment & Stadium Weather",
                apiSource = "Open-Meteo Stadium Forecast API",
                normalizedWeightPct = ((weights.situationalWeight / totalW) * 100).roundToInt(),
                winProbShiftHome = rawSitShift,
                metricComparisonSummary = "${matchup.venue} • ${matchup.weather.tempFahrenheit}°F, ${matchup.weather.windMph} mph wind (${matchup.weather.condition})"
            )
        )

        val defaultCitations = buildDefaultCitedNarrative(matchup, winner.name, winnerProb, factors, awayMissingPts, homeMissingPts)

        if (invokeLiveGemini && geminiApiKey.isNotBlank()) {
            return try {
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = geminiApiKey,
                    generationConfig = generationConfig {
                        temperature = 0.2f
                    }
                )
                val prompt = buildGeminiPrompt(matchup, factors, winner.name, winnerProb)
                val response = model.generateContent(prompt)
                val geminiText = response.text ?: ""
                val liveBlocks = geminiText.split("\n\n").filter { it.isNotBlank() }.mapIndexed { idx, para ->
                    CitedExplanationBlock(
                        heading = "Gemini 2.5 Flash Analytical Insight #${idx + 1}",
                        apiCitationTag = "Google Gemini LLM + Multi-API Telemetry",
                        bodyText = para.trim()
                    )
                }
                LlmPredictionResult(
                    predictedWinnerName = winner.name,
                    predictedWinnerAbbr = winner.abbr,
                    homeWinProbability = homeWinProb,
                    awayWinProbability = awayWinProb,
                    projectedHomeScore = if (rawHomeScore == rawAwayScore && homeWins) rawHomeScore + 1 else rawHomeScore,
                    projectedAwayScore = if (rawHomeScore == rawAwayScore && !homeWins) rawAwayScore + 1 else rawAwayScore,
                    impliedHomeMargin = impliedHomeMargin,
                    confidenceTier = when {
                        winnerProb >= 68.0 -> "High Confidence"
                        winnerProb >= 57.0 -> "Moderate-High"
                        else -> "Tight Toss-Up"
                    },
                    factorAttributions = factors,
                    narrativeCitations = liveBlocks.ifEmpty { defaultCitations },
                    isLiveGeminiResponse = true
                )
            } catch (e: Exception) {
                LlmPredictionResult(
                    predictedWinnerName = winner.name,
                    predictedWinnerAbbr = winner.abbr,
                    homeWinProbability = homeWinProb,
                    awayWinProbability = awayWinProb,
                    projectedHomeScore = rawHomeScore,
                    projectedAwayScore = rawAwayScore,
                    impliedHomeMargin = impliedHomeMargin,
                    confidenceTier = "Moderate-High",
                    factorAttributions = factors,
                    narrativeCitations = defaultCitations,
                    isLiveGeminiResponse = false
                )
            }
        }

        return LlmPredictionResult(
            predictedWinnerName = winner.name,
            predictedWinnerAbbr = winner.abbr,
            homeWinProbability = homeWinProb,
            awayWinProbability = awayWinProb,
            projectedHomeScore = if (rawHomeScore == rawAwayScore && homeWins) rawHomeScore + 1 else rawHomeScore,
            projectedAwayScore = if (rawHomeScore == rawAwayScore && !homeWins) rawAwayScore + 1 else rawAwayScore,
            impliedHomeMargin = impliedHomeMargin,
            confidenceTier = when {
                winnerProb >= 68.0 -> "High Confidence"
                winnerProb >= 57.0 -> "Moderate-High"
                else -> "Tight Toss-Up"
            },
            factorAttributions = factors,
            narrativeCitations = defaultCitations,
            isLiveGeminiResponse = false
        )
    }

    fun buildGeminiPrompt(
        matchup: MatchupTelemetry,
        factors: List<FactorAttribution>,
        winnerName: String,
        winnerProb: Double
    ): String {
        val serializedMatchup = json.encodeToString(matchup)
        val serializedFactors = json.encodeToString(factors)
        return """
            You are GridironIQ, a quantitative College Football (NCAAF) and US Sports AI analyst.
            Explain clearly in 3 paragraphs why the model predicts $winnerName (${"%.1f".format(winnerProb)}% win probability).
            Cite exact metrics from the JSON telemetry below (EPA/play, SP+, Success Rate, Havoc Rate, specific injured players, turnover margin, and stadium weather).
            
            MATCHUP TELEMETRY:
            $serializedMatchup
            
            COMPUTED FACTOR ATTRIBUTIONS:
            $serializedFactors
        """.trimIndent()
    }

    private fun buildDefaultCitedNarrative(
        m: MatchupTelemetry,
        winnerName: String,
        winnerProb: Double,
        factors: List<FactorAttribution>,
        awayMissingPts: Double,
        homeMissingPts: Double
    ): List<CitedExplanationBlock> {
        val topFactor = factors.maxByOrNull { abs(it.winProbShiftHome) } ?: factors.first()
        val unavailableList = m.injuries.filter { !it.isAvailableToPlay }
        val injurySummary = if (unavailableList.isEmpty()) {
            "All primary starters are currently marked active on the pregame depth chart."
        } else {
            unavailableList.joinToString(", ") { "${it.player} (${it.position}, -${it.impactPoints} pt impact)" }
        }

        return listOf(
            CitedExplanationBlock(
                heading = "1. Primary Decision Driver: ${topFactor.title}",
                apiCitationTag = topFactor.apiSource,
                bodyText = "The model selects $winnerName (${"%.1f".format(winnerProb)}% win probability). " +
                        "The largest single swing (${"%.1f".format(abs(topFactor.winProbShiftHome))}% WP) stems from ${topFactor.title}: " +
                        "${m.home.abbr} posts +${m.home.epaOff} Off EPA/play, ${m.home.epaDef} Def EPA/play, and a ${m.home.spPlus} SP+ rating vs " +
                        "${m.away.abbr}'s +${m.away.epaOff} Off EPA/play, ${m.away.epaDef} Def EPA/play, and ${m.away.spPlus} SP+ rating."
            ),
            CitedExplanationBlock(
                heading = "2. Starter Injury & Roster Availability Adjustment",
                apiCitationTag = "ESPN /summary Injuries API",
                bodyText = "Injury telemetry shifts win probability by ${"%.1f".format(abs(factors[1].winProbShiftHome))}%. " +
                        "Sidelined/unavailable personnel factored into this projection: $injurySummary."
            ),
            CitedExplanationBlock(
                heading = "3. Trench Warfare, Turnovers & Stadium Environment",
                apiCitationTag = "CFBD + Open-Meteo + The Odds API",
                bodyText = "On high-leverage downs, ${m.home.abbr} generates a ${m.home.havocRateDef}% defensive havoc rate (${m.home.sacksPerGame} sacks/g) " +
                        "and converts ${m.home.thirdDownPct}% of 3rd downs vs ${m.away.abbr}'s ${m.away.havocRateDef}% havoc rate and ${m.away.thirdDownPct}% 3rd-down conversion. " +
                        "Kickoff weather at ${m.venue} (${m.weather.tempFahrenheit}°F, ${m.weather.windMph} mph wind) and turnover margin (${m.home.abbr} ${m.home.turnoverMargin} vs ${m.away.abbr} ${m.away.turnoverMargin}) complete the composite edge."
            )
        )
    }

    private fun formatSigned(value: Double): String =
        if (value >= 0) "+${"%.2f".format(value)}" else "%.2f".format(value)
}
