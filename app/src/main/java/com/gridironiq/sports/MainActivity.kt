package com.gridironiq.sports

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gridironiq.sports.data.model.FactorAttribution
import com.gridironiq.sports.data.model.FactorWeights
import com.gridironiq.sports.data.model.LlmPredictionResult
import com.gridironiq.sports.data.model.MatchupTelemetry
import com.gridironiq.sports.data.repository.SportsTelemetryRepository
import com.gridironiq.sports.domain.llm.GeminiPredictionEngine
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = SportsTelemetryRepository()
        val geminiEngine = GeminiPredictionEngine(geminiApiKey = BuildConfig.GEMINI_API_KEY)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0B0F17)
                ) {
                    GridironIqScreen(
                        repository = repository,
                        engine = geminiEngine
                    )
                }
            }
        }
    }
}

@Composable
fun GridironIqScreen(
    repository: SportsTelemetryRepository,
    engine: GeminiPredictionEngine
) {
    val scope = rememberCoroutineScope()
    var matchups by remember { mutableStateOf(repository.getFeaturedCollegeFootballMatchups()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var weights by remember { mutableStateOf(FactorWeights()) }
    var prediction by remember { mutableStateOf<LlmPredictionResult?>(null) }

    val activeMatchup = matchups[selectedIndex]

    LaunchedEffect(activeMatchup, weights) {
        prediction = engine.evaluateMatchup(activeMatchup, weights, invokeLiveGemini = false)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "GridironIQ • NCAAF & US Sports AI",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Multi-API Telemetry (CFBD + ESPN + Odds + Open-Meteo) & Gemini LLM Decision Inspector",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        // Matchup Selector Row
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matchups.forEachIndexed { idx, m ->
                    val selected = idx == selectedIndex
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedIndex = idx },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) Color(0xFF1E293B) else Color(0xFF111827)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "${m.away.rank} ${m.away.abbr} @ ${m.home.rank} ${m.home.abbr}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Spread: ${m.home.abbr} ${m.vegasSpreadHome}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Prediction Winner Banner
        prediction?.let { pred ->
            item {
                PredictionBannerCard(matchup = activeMatchup, pred = pred, onRunLiveGemini = {
                    scope.launch {
                        prediction = engine.evaluateMatchup(activeMatchup, weights, invokeLiveGemini = true)
                    }
                })
            }

            // Factor-by-Factor Attribution Waterfall
            item {
                Text(
                    text = "How the LLM Chose the Winner (Factor Attribution)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
            items(pred.factorAttributions) { factor ->
                FactorAttributionCard(factor = factor, awayAbbr = activeMatchup.away.abbr, homeAbbr = activeMatchup.home.abbr)
            }

            // Interactive Injury Overrides
            item {
                Text(
                    text = "Injury & Availability Telemetry (Toggle to Simulate)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeMatchup.injuries.forEachIndexed { injIdx, inj ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "[${if (inj.team == "home") activeMatchup.home.abbr else activeMatchup.away.abbr}] ${inj.player}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${inj.clinicalNote} (Impact: ${inj.impactPoints} pts)",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Switch(
                                    checked = inj.isAvailableToPlay,
                                    onCheckedChange = { isChecked ->
                                        val updatedInjuries = activeMatchup.injuries.toMutableList()
                                        updatedInjuries[injIdx] = inj.copy(isAvailableToPlay = isChecked)
                                        val updatedList = matchups.toMutableList()
                                        updatedList[selectedIndex] = activeMatchup.copy(injuries = updatedInjuries)
                                        matchups = updatedList
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Cited Chain-of-Thought Narrative
            item {
                Text(
                    text = "Gemini LLM Cited Decision Explanation",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
            items(pred.narrativeCitations) { block ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = block.heading, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF38BDF8))
                        Text(text = "[Source: ${block.apiCitationTag}]", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF94A3B8))
                        Text(text = block.bodyText, fontSize = 12.sp, color = Color(0xFFE2E8F0))
                    }
                }
            }
        }
    }
}

@Composable
fun PredictionBannerCard(
    matchup: MatchupTelemetry,
    pred: LlmPredictionResult,
    onRunLiveGemini: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${matchup.away.rank} ${matchup.away.abbr}", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = "${pred.projectedAwayScore}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(text = "${"%.1f".format(pred.awayWinProbability)}% WP", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "LLM WINNER PICK", fontSize = 10.sp, color = Color(0xFF94A3B8))
                    Text(
                        text = pred.predictedWinnerName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Text(text = pred.confidenceTier, fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "${matchup.home.rank} ${matchup.home.abbr}", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = "${pred.projectedHomeScore}", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(text = "${"%.1f".format(pred.homeWinProbability)}% WP", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }

            Button(
                onClick = onRunLiveGemini,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Re-Analyze with Google Gemini 2.5 Flash")
            }
        }
    }
}

@Composable
fun FactorAttributionCard(
    factor: FactorAttribution,
    awayAbbr: String,
    homeAbbr: String
) {
    val favorsHome = factor.winProbShiftHome >= 0
    val favoredTeam = if (favorsHome) homeAbbr else awayAbbr
    val shiftMag = abs(factor.winProbShiftHome)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = factor.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                Text(
                    text = "$favoredTeam +${"%.1f".format(shiftMag)}% WP",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (favorsHome) Color(0xFF38BDF8) else Color(0xFFF59E0B)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1E293B))
            ) {
                val fraction = (shiftMag / 25.0).coerceIn(0.05, 1.0).toFloat()
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .background(if (favorsHome) Color(0xFF38BDF8) else Color(0xFFF59E0B))
                )
            }
            Text(
                text = "${factor.metricComparisonSummary} [${factor.apiSource}]",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8),
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
