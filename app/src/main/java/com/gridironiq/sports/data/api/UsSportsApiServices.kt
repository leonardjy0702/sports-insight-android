package com.gridironiq.sports.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * 1. CollegeFootballData API v2 (https://api.collegefootballdata.com)
 * Primary source for NCAAF advanced sabermetrics: EPA/play, SP+ ratings, Success Rate, Havoc, H2H history.
 */
interface CfbdApiService {
    @GET("stats/season/advanced")
    suspend fun getAdvancedSeasonStats(
        @Header("Authorization") bearerToken: String,
        @Query("year") year: Int,
        @Query("team") team: String? = null
    ): List<CfbdAdvancedStatDto>

    @GET("ratings/sp")
    suspend fun getSpPlusRatings(
        @Header("Authorization") bearerToken: String,
        @Query("year") year: Int,
        @Query("team") team: String? = null
    ): List<CfbdSpRatingDto>

    @GET("teams/matchup")
    suspend fun getHeadToHeadMatchup(
        @Header("Authorization") bearerToken: String,
        @Query("team1") team1: String,
        @Query("team2") team2: String
    ): CfbdMatchupHistoryDto
}

@Serializable
data class CfbdAdvancedStatDto(
    val season: Int,
    val team: String,
    val conference: String? = null,
    val offense: CfbdUnitMetricsDto? = null,
    val defense: CfbdUnitMetricsDto? = null
)

@Serializable
data class CfbdUnitMetricsDto(
    val ppa: Double? = null,         // Predicted Points Added / EPA per play
    val successRate: Double? = null, // Efficiency success rate (0.0 - 1.0)
    val explosiveness: Double? = null,
    val havoc: CfbdHavocDto? = null
)

@Serializable
data class CfbdHavocDto(
    val total: Double? = null,
    val frontSeven: Double? = null,
    val db: Double? = null
)

@Serializable
data class CfbdSpRatingDto(
    val year: Int,
    val team: String,
    val rating: Double? = null,
    val ranking: Int? = null
)

@Serializable
data class CfbdMatchupHistoryDto(
    val team1: String,
    val team2: String,
    val team1Wins: Int,
    val team2Wins: Int,
    val ties: Int
)

/**
 * 2. ESPN Public Site API (https://site.api.espn.com/apis/site/v2/sports/football/college-football/)
 * Zero-configuration public API providing live NCAAF scoreboard, injuries, depth chart status, and ESPN FPI.
 */
interface EspnPublicApiService {
    @GET("scoreboard")
    suspend fun getCollegeFootballScoreboard(
        @Query("groups") divisionGroup: Int = 80, // 80 = FBS, 81 = FCS
        @Query("limit") limit: Int = 25
    ): EspnScoreboardResponseDto

    @GET("summary")
    suspend fun getGameSummaryAndInjuries(
        @Query("event") eventId: String
    ): EspnGameSummaryDto
}

@Serializable
data class EspnScoreboardResponseDto(
    val events: List<EspnEventDto> = emptyList()
)

@Serializable
data class EspnEventDto(
    val id: String,
    val name: String,
    val shortName: String? = null
)

@Serializable
data class EspnGameSummaryDto(
    val injuries: List<EspnTeamInjuriesDto> = emptyList(),
    val predictor: EspnPredictorDto? = null
)

@Serializable
data class EspnTeamInjuriesDto(
    val team: EspnTeamRefDto? = null,
    val injuries: List<EspnInjuryItemDto> = emptyList()
)

@Serializable
data class EspnTeamRefDto(
    val id: String? = null,
    val abbreviation: String? = null,
    val displayName: String? = null
)

@Serializable
data class EspnInjuryItemDto(
    val status: String? = null,
    val athlete: EspnAthleteDto? = null
)

@Serializable
data class EspnAthleteDto(
    val displayName: String? = null,
    val position: EspnPositionDto? = null
)

@Serializable
data class EspnPositionDto(
    val abbreviation: String? = null
)

@Serializable
data class EspnPredictorDto(
    val header: String? = null,
    val homeTeam: EspnPredictionProbDto? = null,
    val awayTeam: EspnPredictionProbDto? = null
)

@Serializable
data class EspnPredictionProbDto(
    val gameProjection: String? = null
)

/**
 * 3. The Odds API v4 (https://api.the-odds-api.com/v4/)
 * Provides live US sportsbook spreads, moneylines, and totals (DraftKings, FanDuel, BetMGM, Caesars).
 */
interface TheOddsApiService {
    @GET("sports/americanfootball_ncaaf/odds")
    suspend fun getNcaafMarketOdds(
        @Query("apiKey") apiKey: String,
        @Query("regions") regions: String = "us",
        @Query("markets") markets: String = "h2h,spreads,totals",
        @Query("oddsFormat") oddsFormat: String = "american"
    ): List<OddsEventDto>
}

@Serializable
data class OddsEventDto(
    val id: String,
    @SerialName("home_team") val homeTeam: String,
    @SerialName("away_team") val awayTeam: String
)

/**
 * 4. Open-Meteo Weather API (https://api.open-meteo.com/v1/)
 * Keyless stadium weather telemetry for wind speed, temperature, and precipitation impact on passing/kicking.
 */
interface OpenMeteoWeatherService {
    @GET("forecast")
    suspend fun getStadiumWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,precipitation_probability,wind_speed_10m",
        @Query("temperature_unit") tempUnit: String = "fahrenheit",
        @Query("wind_speed_unit") windUnit: String = "mph"
    ): OpenMeteoResponseDto
}

@Serializable
data class OpenMeteoResponseDto(
    val latitude: Double,
    val longitude: Double
)
