package com.gridironiq.sports

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.gridironiq.sports.data.model.SportLeague
import com.gridironiq.sports.data.repository.SportsTelemetryRepository
import com.gridironiq.sports.domain.llm.GeminiPredictionEngine
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 3-Tier Navigation Hierarchy:
 * 1. GATEWAY: Main App Hub to select NFL or College Football
 * 2. SUB_GATEWAY: League Weekly Slate listing ALL games for the week
 * 3. MATCHUP_DETAILS: Full AI Pick, Factor Waterfall, Injury Simulator & Metrics for the selected game
 */
enum class AppSection(val label: String) {
    GATEWAY("Gateway"),
    SUB_GATEWAY("Weekly Slate"),
    MATCHUP_DETAILS("Full Details")
}

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
                    GridironIqApp(
                        repository = repository,
                        engine = geminiEngine
                    )
                }
            }
        }
    }
}

@Composable
fun GridironIqApp(
    repository: SportsTelemetryRepository,
    engine: GeminiPredictionEngine
) {
    val scope = rememberCoroutineScope()
    var currentSection by remember { mutableStateOf(AppSection.GATEWAY) }
    var currentLeague by remember { mutableStateOf(SportLeague.NFL) }
    var nflMatchups by remember { mutableStateOf(repository.getFeaturedNflMatchups()) }
    var ncaafMatchups by remember { mutableStateOf(repository.getFeaturedCollegeFootballMatchups()) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var weights by remember { mutableStateOf(FactorWeights()) }
    var prediction by remember { mutableStateOf<LlmPredictionResult?>(null) }

    val activeList = if (currentLeague == SportLeague.NFL) nflMatchups else ncaafMatchups
    val safeIndex = selectedIndex.coerceIn(0, activeList.lastIndex)
    val activeMatchup = activeList[safeIndex]

    LaunchedEffect(activeMatchup, weights, currentLeague) {
        prediction = engine.evaluateMatchup(activeMatchup, weights, invokeLiveGemini = false)
    }

    Scaffold(
        containerColor = Color(0xFF0B0F17),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            currentSection = when (currentSection) {
                                AppSection.MATCHUP_DETAILS -> AppSection.SUB_GATEWAY
                                else -> AppSection.GATEWAY
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (currentSection == AppSection.MATCHUP_DETAILS) "← ${currentLeague.name} Slate" else "🏠 Gateway",
                            fontSize = 12.sp
                        )
                    }
                    Column {
                        Text(
                            text = when (currentSection) {
                                AppSection.GATEWAY -> "GridironIQ Gateway"
                                AppSection.SUB_GATEWAY -> "${currentLeague.displayName} Weekly Slate"
                                AppSection.MATCHUP_DETAILS -> "${activeMatchup.away.abbr} @ ${activeMatchup.home.abbr}"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "ESPN Weekly Schedule + Google Gemini 2.5 Flash",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF111827)) {
                AppSection.entries.forEach { section ->
                    NavigationBarItem(
                        selected = currentSection == section,
                        onClick = { currentSection = section },
                        icon = { Text(text = section.label.take(2), fontWeight = FontWeight.Bold) },
                        label = { Text(text = section.label, fontSize = 11.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentSection) {
                AppSection.GATEWAY -> {
                    GatewaySelectionScreen(
                        onSelectLeague = { league ->
                            currentLeague = league
                            selectedIndex = 0
                            currentSection = AppSection.SUB_GATEWAY
                        }
                    )
                }
                AppSection.SUB_GATEWAY -> {
                    LeagueWeeklySubGatewayScreen(
                        currentLeague = currentLeague,
                        matchups = activeList,
                        onSwitchLeague = {
                            currentLeague = it
                            selectedIndex = 0
                        },
                        onSelectGameForFullDetails = { idx ->
                            selectedIndex = idx
                            currentSection = AppSection.MATCHUP_DETAILS
                        }
                    )
                }
                AppSection.MATCHUP_DETAILS -> {
                    MatchupFullDetailsScreen(
                        activeMatchup = activeMatchup,
                        prediction = prediction,
                        onBackToSlate = { currentSection = AppSection.SUB_GATEWAY },
                        onToggleInjury = { injIdx, isAvailable ->
                            val updatedInjuries = activeMatchup.injuries.toMutableList()
                            updatedInjuries[injIdx] = updatedInjuries[injIdx].copy(isAvailableToPlay = isAvailable)
                            if (currentLeague == SportLeague.NFL) {
                                val list = nflMatchups.toMutableList()
                                list[safeIndex] = activeMatchup.copy(injuries = updatedInjuries)
                                nflMatchups = list
                            } else {
                                val list = ncaafMatchups.toMutableList()
                                list[safeIndex] = activeMatchup.copy(injuries = updatedInjuries)
                                ncaafMatchups = list
                            }
                        },
                        onRunLiveGemini = {
                            scope.launch {
                                prediction = engine.evaluateMatchup(activeMatchup, weights, invokeLiveGemini = true)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GatewaySelectionScreen(
    onSelectLeague: (SportLeague) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161F30)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "STEP 1: SELECT YOUR LEAGUE PORTAL",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "NFL & College Football AI Gateway",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Choose NFL or College Football to open the Weekly Games Sub-Gateway listing every matchup of the week.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLeague(SportLeague.NFL) }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "🏈 NFL Weekly Games Sub-Gateway", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = "Browse all weekly NFL matchups synced from ESPN. Select any game to open full EPA/play, DVOA%, official injuries & Gemini LLM assessment.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(text = "Open Full Weekly NFL Slate →", fontSize = 12.sp, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectLeague(SportLeague.NCAAF) }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "🎓 NCAA College Football Sub-Gateway", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = "Browse all weekly FBS & Top 25 matchups. Select any game to open CFBD SP+, EPA/play, Havoc Rate & Gemini LLM assessment.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(text = "Open Full Weekly NCAAF Slate →", fontSize = 12.sp, color = Color(0xFF38BDF8), fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/**
 * TIER 2: League Weekly Sub-Gateway Screen listing ALL games for the week.
 * Tapping any game opens the Full Matchup Details screen (`AppSection.MATCHUP_DETAILS`).
 */
@Composable
fun LeagueWeeklySubGatewayScreen(
    currentLeague: SportLeague,
    matchups: List<MatchupTelemetry>,
    onSwitchLeague: (SportLeague) -> Unit,
    onSelectGameForFullDetails: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${currentLeague.displayName} — All Weekly Games",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Select any matchup below to view full LLM & metric details",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SportLeague.entries.forEach { lg ->
                    FilterChip(
                        selected = lg == currentLeague,
                        onClick = { onSwitchLeague(lg) },
                        label = { Text(text = lg.displayName) }
                    )
                }
            }
        }

        itemsIndexed(matchups) { idx, m ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectGameForFullDetails(idx) }
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "Game #${idx + 1} • ${m.conference}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Line: ${m.home.abbr} ${m.vegasSpreadHome} • O/U ${m.overUnder}",
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "${m.away.name} (${m.away.record})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Text(text = "at ${m.home.name} (${m.home.record})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                        Text(
                            text = "View Details →",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }
            }
        }
    }
}

/**
 * TIER 3: Full Matchup Details View shown when a user selects a specific game from the Weekly Sub-Gateway.
 */
@Composable
fun MatchupFullDetailsScreen(
    activeMatchup: MatchupTelemetry,
    prediction: LlmPredictionResult?,
    onBackToSlate: () -> Unit,
    onToggleInjury: (Int, Boolean) -> Unit,
    onRunLiveGemini: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "← Back to All Weekly ${activeMatchup.league.name} Matchups",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8),
                modifier = Modifier.clickable { onBackToSlate() }
            )
        }

        prediction?.let { pred ->
            item {
                PredictionBannerCard(matchup = activeMatchup, pred = pred, onRunLiveGemini = onRunLiveGemini)
            }

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

            item {
                Text(
                    text = "Injury & Availability Simulator",
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
                                    onCheckedChange = { isChecked -> onToggleInjury(injIdx, isChecked) }
                                )
                            }
                        }
                    }
                }
            }

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
                    Text(
                        text = if (pred.isLiveGeminiResponse) "✨ GEMINI 2.5 LIVE PICK" else "BASELINE PICK",
                        fontSize = 10.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Text(
                        text = pred.predictedWinnerName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color.White
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
                Text(text = "✨ Run Gemini 2.5 Flash Assessment to Update Prediction")
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
