package com.gridironiq.sports.domain.llm

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import com.gridironiq.sports.data.model.CitedExplanationBlock
import com.gridironiq.sports.data.model.FactorAttribution
import com.gridironiq.sports.data.model.FactorWeights
import com.gridironiq.sports.data.model.LlmPredictionResult
import com.gridironiq.sports.data.model.MatchupTelemetry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Structured JSON schema returned by Google Gemini 2.5 Flash (`responseMimeType = "application/json"`)
 * so that Gemini directly updates the predicted winner, win probability, projected score,
 * factor attribution shifts, and cited explanations.
 */
@Serializable
data class GeminiStructuredAssessmentDto(
    val homeWinProb: Double,
    val awayScore: Int,
    val homeScore: Int,
    val efficiencyShiftHome: Double,
    val injuryShiftHome: Double,
    val trenchShiftHome: Double,
    val turnoverShiftHome: Double,
    val situationalShiftHome: Double,
    val paragraph1: String,
    val paragraph2: String
)

class GeminiPredictionEngine(
    private val geminiApiKey: String = ""
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

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
        val ratingLabel = matchup.league.ratingLabel

        // 1. Efficiency Differential (EPA/Play + DVOA%/SP+ + Vegas Implied Line)
        val homeNetEpa = home.epaOff - home.epaDef
        val awayNetEpa = away.epaOff - away.epaDef
        val spreadShift = (-matchup.vegasSpreadHome) * 2.1
        val rawEffShift = (((homeNetEpa - awayNetEpa) * 32.0) * 0.45 +
                ((home.spPlus - away.spPlus) * 0.65) * 0.30 +
                spreadShift * 0.25) * normEff

        // 2. Player Injuries & QB Availability Shift (ESPN /summary Injury Feed)
        val homeMissingPts = matchup.injuries.filter { it.team == "home" && !it.isAvailableToPlay }.sumOf { it.impactPoints }
        val awayMissingPts = matchup.injuries.filter { it.team == "away" && !it.isAvailableToPlay }.sumOf { it.impactPoints }
        val rawInjShift = ((awayMissingPts - homeMissingPts) * 2.4) * normInj

        // 3. Trench Warfare (Pass-Rush Win % / Havoc Rate, Sacks/Game, 3rd Down %)
        val havocDiff = (home.havocRateDef - away.havocRateDef) * 0.55
        val sackDiff = (home.sacksPerGame - away.sacksPerGame) * 1.8
        val rawTrenchShift = (havocDiff + sackDiff) * 0.6 * normTrench

        // 4. Turnovers & Red Zone TD Efficiency
        val toDiff = (home.turnoverMargin - away.turnoverMargin) * 0.45
        val rzDiff = (home.redZoneTdPct - away.redZoneTdPct) * 0.25
        val rawFormShift = (toDiff + rzDiff) * normForm

        // 5. Situational & Stadium Weather (Open-Meteo API)
        val weatherMod = if (!matchup.weather.isDome && matchup.weather.windMph >= 15) 1.2 else 0.0
        val rawSitShift = (2.8 + weatherMod) * normSit

        val totalShift = rawEffShift + rawInjShift + rawTrenchShift + rawFormShift + rawSitShift
        val baseHomeWinProb = min(92.0, max(8.0, 50.0 + totalShift))
        val baseAwayWinProb = 100.0 - baseHomeWinProb

        val impliedHomeMargin = (baseHomeWinProb - 50.0) * 0.34
        val rawHomeScore = ((matchup.overUnder / 2.0) + (impliedHomeMargin / 2.0)).roundToInt()
        val rawAwayScore = ((matchup.overUnder / 2.0) - (impliedHomeMargin / 2.0)).roundToInt()

        val baseFactors = listOf(
            FactorAttribution(
                id = "eff",
                title = "Efficiency (EPA/Play & $ratingLabel)",
                apiSource = if (matchup.league.name == "NFL") "nflfastR + DVOA" else "CFBD /stats/season/advanced",
                normalizedWeightPct = ((weights.efficiencyWeight / totalW) * 100).roundToInt(),
                winProbShiftHome = rawEffShift,
                metricComparisonSummary = "${home.abbr} $ratingLabel ${home.spPlus} vs ${away.abbr} $ratingLabel ${away.spPlus}"
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
                title = "Trench Warfare (Pass-Rush/Havoc & Sacks)",
                apiSource = "ESPN & Advanced Boxscore",
                normalizedWeightPct = ((weights.trenchWeight / totalW) * 100).roundToInt(),
                winProbShiftHome = rawTrenchShift,
                metricComparisonSummary = "Pressure/Havoc: ${home.abbr} ${home.havocRateDef}% (${home.sacksPerGame} sacks/g) vs ${away.abbr} ${away.havocRateDef}%"
            ),
            FactorAttribution(
                id = "form",
                title = "Turnover Margin & Red Zone TD%",
                apiSource = "ESPN Standings API",
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
                metricComparisonSummary = "${matchup.venue} • ${matchup.weather.tempFahrenheit}°F, ${matchup.weather.windMph} mph wind"
            )
        )

        val homeWinsBase = baseHomeWinProb >= 50.0
        val baseWinner = if (homeWinsBase) home else away
        val baseWinnerProb = max(baseHomeWinProb, baseAwayWinProb)
        val defaultCitations = buildDefaultCitedNarrative(matchup, baseWinner.name, baseWinnerProb, baseFactors)

        if (invokeLiveGemini && geminiApiKey.isNotBlank()) {
            return try {
                val model = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = geminiApiKey,
                    generationConfig = generationConfig {
                        temperature = 0.2f
                        responseMimeType = "application/json"
                    }
                )
                val prompt = buildStructuredGeminiPrompt(matchup, weights, baseFactors)
                val response = model.generateContent(prompt)
                val rawJson = (response.text ?: "").trim().removePrefix("```json").removeSuffix("```").trim()
                val dto = json.decodeFromString<GeminiStructuredAssessmentDto>(rawJson)

                val geminiHomeWp = dto.homeWinProb.coerceIn(5.0, 95.0)
                val geminiAwayWp = 100.0 - geminiHomeWp
                val geminiHomeWins = geminiHomeWp >= 50.0
                val geminiWinner = if (geminiHomeWins) home else away
                val geminiWinnerProb = max(geminiHomeWp, geminiAwayWp)

                val updatedFactors = listOf(
                    baseFactors[0].copy(winProbShiftHome = dto.efficiencyShiftHome, apiSource = "Gemini 2.5 + ${baseFactors[0].apiSource}"),
                    baseFactors[1].copy(winProbShiftHome = dto.injuryShiftHome, apiSource = "Gemini 2.5 + ESPN Injuries"),
                    baseFactors[2].copy(winProbShiftHome = dto.trenchShiftHome, apiSource = "Gemini 2.5 + Trench Stats"),
                    baseFactors[3].copy(winProbShiftHome = dto.turnoverShiftHome, apiSource = "Gemini 2.5 + Boxscore"),
                    baseFactors[4].copy(winProbShiftHome = dto.situationalShiftHome, apiSource = "Gemini 2.5 + Open-Meteo")
                )

                val liveBlocks = listOf(
                    CitedExplanationBlock(
                        heading = "✨ Gemini 2.5 Flash Efficiency & Matchup Assessment",
                        apiCitationTag = "Google Gemini Structured JSON Output",
                        bodyText = dto.paragraph1
                    ),
                    CitedExplanationBlock(
                        heading = "✨ Gemini 2.5 Flash Injury & Trench Breakdown",
                        apiCitationTag = "Google Gemini + ESPN Injury Telemetry",
                        bodyText = dto.paragraph2
                    )
                )

                LlmPredictionResult(
                    predictedWinnerName = geminiWinner.name,
                    predictedWinnerAbbr = geminiWinner.abbr,
                    homeWinProbability = geminiHomeWp,
                    awayWinProbability = geminiAwayWp,
                    projectedHomeScore = dto.homeScore,
                    projectedAwayScore = dto.awayScore,
                    impliedHomeMargin = (dto.homeScore - dto.awayScore).toDouble(),
                    confidenceTier = when {
                        geminiWinnerProb >= 68.0 -> "High Confidence (Gemini Live)"
                        geminiWinnerProb >= 57.0 -> "Moderate-High (Gemini Live)"
                        else -> "Tight Toss-Up (Gemini Live)"
                    },
                    factorAttributions = updatedFactors,
                    narrativeCitations = liveBlocks,
                    isLiveGeminiResponse = true
                )
            } catch (e: Exception) {
                LlmPredictionResult(
                    predictedWinnerName = baseWinner.name,
                    predictedWinnerAbbr = baseWinner.abbr,
                    homeWinProbability = baseHomeWinProb,
                    awayWinProbability = baseAwayWinProb,
                    projectedHomeScore = rawHomeScore,
                    projectedAwayScore = rawAwayScore,
                    impliedHomeMargin = impliedHomeMargin,
                    confidenceTier = "Baseline Fallback",
                    factorAttributions = baseFactors,
                    narrativeCitations = defaultCitations,
                    isLiveGeminiResponse = false
                )
            }
        }

        return LlmPredictionResult(
            predictedWinnerName = baseWinner.name,
            predictedWinnerAbbr = baseWinner.abbr,
            homeWinProbability = baseHomeWinProb,
            awayWinProbability = baseAwayWinProb,
            projectedHomeScore = if (rawHomeScore == rawAwayScore && homeWinsBase) rawHomeScore + 1 else rawHomeScore,
            projectedAwayScore = if (rawHomeScore == rawAwayScore && !homeWinsBase) rawAwayScore + 1 else rawAwayScore,
            impliedHomeMargin = impliedHomeMargin,
            confidenceTier = when {
                baseWinnerProb >= 68.0 -> "High Confidence"
                baseWinnerProb >= 57.0 -> "Moderate-High"
                else -> "Tight Toss-Up"
            },
            factorAttributions = baseFactors,
            narrativeCitations = defaultCitations,
            isLiveGeminiResponse = false
        )
    }

    private fun buildStructuredGeminiPrompt(
        matchup: MatchupTelemetry,
        weights: FactorWeights,
        baselineFactors: List<FactorAttribution>
    ): String {
        val serializedMatchup = json.encodeToString(matchup)
        val serializedWeights = json.encodeToString(weights)
        val serializedBaseline = json.encodeToString(baselineFactors)
        return """
            You are GridironIQ, a quantitative ${matchup.league.displayName} AI analyst.
            Evaluate the matchup telemetry, active/out injuries, stadium weather, and user factor weights below.
            Return ONLY a valid JSON object with these exact keys:
            {
              "homeWinProb": <Double between 5.0 and 95.0>,
              "awayScore": <Int projected score>,
              "homeScore": <Int projected score>,
              "efficiencyShiftHome": <Double signed win prob shift>,
              "injuryShiftHome": <Double signed win prob shift>,
              "trenchShiftHome": <Double signed win prob shift>,
              "turnoverShiftHome": <Double signed win prob shift>,
              "situationalShiftHome": <Double signed win prob shift>,
              "paragraph1": "<String citing EPA/play, ${matchup.league.ratingLabel}, and efficiency>",
              "paragraph2": "<String citing specific injured players, trench pressure, and score projection>"
            }
            
            MATCHUP TELEMETRY:
            $serializedMatchup
            
            USER WEIGHTS:
            $serializedWeights
            
            BASELINE FACTORS:
            $serializedBaseline
        """.trimIndent()
    }

    private fun buildDefaultCitedNarrative(
        m: MatchupTelemetry,
        winnerName: String,
        winnerProb: Double,
        factors: List<FactorAttribution>
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
                        "${m.home.abbr} posts +${m.home.epaOff} Off EPA/play, ${m.home.epaDef} Def EPA/play, and a ${m.home.spPlus} ${m.league.ratingLabel} rating vs " +
                        "${m.away.abbr}'s +${m.away.epaOff} Off EPA/play, ${m.away.epaDef} Def EPA/play, and ${m.away.spPlus} ${m.league.ratingLabel} rating."
            ),
            CitedExplanationBlock(
                heading = "2. Starter Injury & Roster Availability Adjustment",
                apiCitationTag = "ESPN /summary Injuries API",
                bodyText = "Injury telemetry shifts win probability by ${"%.1f".format(abs(factors[1].winProbShiftHome))}%. " +
                        "Sidelined/unavailable personnel factored into this projection: $injurySummary."
            )
        )
    }
}
