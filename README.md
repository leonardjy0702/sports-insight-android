# GridironIQ — College Football (NCAAF) & US Sports AI Predictor (Android)

GridironIQ is an Android mobile application (built with **Kotlin + Jetpack Compose + Retrofit2 + Google Gemini 2.5 Flash**) and interactive mobile web prototype that aggregates **US Public Sports APIs** to predict College Football and US sports game winners and transparently explain **how** the LLM arrives at every prediction.

## Key Capabilities

1. **Multi-API US Sports Telemetry Aggregation (`data/api/UsSportsApiServices.kt`)**:
   - **CollegeFootballData API (CFBD v2)**: Advanced season sabermetrics (`/stats/season/advanced` for Offensive/Defensive EPA/play, Success Rate, Explosiveness, Havoc Rate), SP+ ratings (`/ratings/sp`), and historical series records (`/teams/matchup`).
   - **ESPN Public Site API**: Keyless real-time NCAAF scoreboard (`/scoreboard?groups=80`), pregame injury reports & starter status (`/summary?event={id}`), and ESPN FPI pregame win probabilities.
   - **The Odds API (v4)**: Live US sportsbook consensus spreads, moneylines, and totals (`/v4/sports/americanfootball_ncaaf/odds`).
   - **Open-Meteo Stadium Forecast API**: Keyless stadium GPS kickoff temperature, wind speed/gusts, and precipitation probability (`/v1/forecast`).
2. **Auditable Factor-by-Factor LLM Decision Engine (`domain/llm/GeminiPredictionEngine.kt`)**:
   - Decomposes the win-probability shift from a 50/50 baseline into five transparent categories:
     1. **Offensive & Defensive Efficiency (EPA/Play, SP+, Success Rate)** — Default 30%
     2. **Starter Injury & QB Availability Impact** — Default 25%
     3. **Trench Warfare (Defensive Havoc Rate, Sacks/Game, 3rd Down %)** — Default 15%
     4. **Turnover Margin, Red Zone TD% & Head-to-Head History** — Default 15%
     5. **Situational Environment & Stadium Weather** — Default 15%
   - Interfaces with **Google Gemini (`gemini-2.5-flash`)** to synthesize a 3-part narrative where every claim cites the exact API source and metric.
3. **Interactive Injury & Weight Simulator (`MainActivity.kt`)**:
   - Toggle any injured or questionable starter (e.g., QB1, Left Tackle, RB1, CB1) between *Active* and *Out* to watch the win probability, projected score, and LLM explanation update in real time.

## Setup & API Keys (`local.properties`)

Add your optional keys to `local.properties` in the project root:

```properties
GEMINI_API_KEY=your_google_gemini_api_key
CFBD_API_KEY=your_collegefootballdata_bearer_key
ODDS_API_KEY=your_the_odds_api_key
```

> Note: ESPN's Public Site API and Open-Meteo Weather API require **no API keys** and work out-of-the-box.
