/*
 * Mesh Rider Wave - PTT Training & Gamification
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * MILITARY-GRADE Training System
 * - Gamified PTT etiquette training
 * - Rank progression system
 * - Achievement badges
 * - Performance analytics
 * - Scenario-based exercises
 */

package com.doodlelabs.meshriderwave.ptt

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MILITARY-GRADE PTT Training Engine
 *
 * Features:
 * - Rank progression (Recruit → Operator → Senior Operator → Lead Operator → Commander)
 * - Achievement badges for milestones
 * - XP system based on usage and performance
 * - Training scenarios with scoring
 * - Performance analytics dashboard
 *
 * Based on military gamification research:
 * - Increases engagement and skill retention
 * - Provides positive reinforcement
 * - Tracks progress over time
 *
 * Research Sources:
 * - "Gamification in Support of Decision Making in Military Higher Education"
 * - "Gamification as a Tool for Developing Professional Competencies"
 */
@Singleton
class PttTrainingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MeshRider:Training"
        private const val PREFS_NAME = "ptt_training"

        // XP values
        private const val XP_FIRST_TRANSMISSION = 10
        private const val XP_PER_TRANSMISSION = 1
        private const val XP_PER_MINUTE_TRANSMISSION = 5
        private const val XP_EMERGENCY_HANDLED = 50
        private const val XP_TRAINING_SCENARIO = 100
        private const val XP_PER_BADGE = 25

        // Streak bonus
        private const val STREAK_BONUS_MULTIPLIER = 1.5
        private const val MIN_STREAK_DAYS = 3
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // User progress state
    private val _currentXp = MutableStateFlow(0)
    val currentXp: StateFlow<Int> = _currentXp.asStateFlow()

    private val _currentRank = MutableStateFlow(PttRank.RECRUIT)
    val currentRank: StateFlow<PttRank> = _currentRank.asStateFlow()

    private val _unlockedBadges = MutableStateFlow<Set<PttBadge>>(emptySet())
    val unlockedBadges: StateFlow<Set<PttBadge>> = _unlockedBadges.asStateFlow()

    private val _dailyStreak = MutableStateFlow(0)
    val dailyStreak: StateFlow<Int> = _dailyStreak.asStateFlow()

    private val _lastActiveDate = MutableStateFlow<Instant?>(null)
    val lastActiveDate: StateFlow<Instant?> = _lastActiveDate.asStateFlow()

    // Training scenario state
    private val _activeScenario = MutableStateFlow<TrainingScenario?>(null)
    val activeScenario: StateFlow<TrainingScenario?> = _activeScenario.asStateFlow()

    private val _scenarioProgress = MutableStateFlow(ScenarioProgress?>(null))
    val scenarioProgress: StateFlow<ScenarioProgress?> = _scenarioProgress.asStateFlow()

    // Statistics
    private val _totalTransmissions = MutableStateFlow(0)
    val totalTransmissions: StateFlow<Int> = _totalTransmissions.asStateFlow()

    private val _totalTransmitTimeMs = MutableStateFlow(0L)
    val totalTransmitTimeMs: StateFlow<Long> = _totalTransmitTimeMs.asStateFlow()

    private val _emergenciesHandled = MutableStateFlow(0)
    val emergenciesHandled: StateFlow<Int> = _emergenciesHandled.asStateFlow()

    private val _scenariosCompleted = MutableStateFlow(0)
    val scenariosCompleted: StateFlow<Int> = _scenariosCompleted.asStateFlow()

    // Scope for operations
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        loadProgress()
    }

    /**
     * Initialize training engine
     */
    fun initialize() {
        loadProgress()
        checkDailyStreak()
    }

    /**
     * Record PTT transmission
     *
     * Awards XP based on transmission duration and frequency
     *
     * @param durationMs Transmission duration in milliseconds
     * @return XP earned
     */
    fun recordTransmission(durationMs: Long): Int {
        var xpEarned = 0

        // First transmission bonus
        if (_totalTransmissions.value == 0) {
            xpEarned += XP_FIRST_TRANSMISSION
            checkAndUnlockBadge(PttBadge.FIRST_TRANSMISSION)
        }

        // Per-transmission XP
        xpEarned += XP_PER_TRANSMISSION

        // Duration-based XP
        val minutes = (durationMs / 60000.0).toInt()
        xpEarned += minutes * XP_PER_MINUTE_TRANSMISSION

        // Update stats
        _totalTransmissions.value++
        _totalTransmitTimeMs.value += durationMs

        // Streak bonus
        if (_dailyStreak.value >= MIN_STREAK_DAYS) {
            xpEarned = (xpEarned * STREAK_BONUS_MULTIPLIER).toInt()
        }

        // Award XP
        addXp(xpEarned)

        // Check for badges
        if (_totalTransmissions.value >= 50) {
            checkAndUnlockBadge(PttBadge.RADIO_DISCIPLINE)
        }
        if (_totalTransmissions.value >= 100) {
            checkAndUnlockBadge(PttBadge.VETERAN)
        }

        return xpEarned
    }

    /**
     * Record emergency handled
     *
     * @return XP earned
     */
    fun recordEmergencyHandled(): Int {
        _emergenciesHandled.value++
        addXp(XP_EMERGENCY_HANDLED)

        if (_emergenciesHandled.value >= 5) {
            checkAndUnlockBadge(PttBadge.EMERGENCY_RESPONDER)
        }

        return XP_EMERGENCY_HANDLED
    }

    /**
     * Start training scenario
     *
     * @param scenario Training scenario to start
     * @return true if started successfully
     */
    fun startScenario(scenario: TrainingScenario): Boolean {
        if (_activeScenario.value != null) {
            return false // Scenario already active
        }

        _activeScenario.value = scenario
        _scenarioProgress.value = ScenarioProgress(
            scenario = scenario,
            startTime = Instant.now(),
            stepsCompleted = 0,
            totalSteps = scenario.totalSteps,
            mistakes = 0,
            score = 0.0f
        )

        return true
    }

    /**
     * Complete scenario step
     *
     * @param success true if step completed successfully
     * @return current score
     */
    fun completeStep(success: Boolean): Float {
        val progress = _scenarioProgress.value ?: return 0f

        val newStepsCompleted = progress.stepsCompleted + 1
        val newMistakes = if (!success) progress.mistakes + 1 else progress.mistakes

        // Calculate score
        val successRate = (newStepsCompleted - newMistakes).toFloat() / newStepsCompleted.toFloat()
        val timeBonus = calculateTimeBonus(progress)

        _scenarioProgress.value = progress.copy(
            stepsCompleted = newStepsCompleted,
            mistakes = newMistakes,
            score = successRate * 100 + timeBonus
        )

        // Check if scenario complete
        if (newStepsCompleted >= progress.totalSteps) {
            completeScenario()
        }

        return _scenarioProgress.value?.score ?: 0f
    }

    /**
     * Complete training scenario
     *
     * @return final score and XP earned
     */
    fun completeScenario(): ScenarioResult? {
        val progress = _scenarioProgress.value ?: return null
        val scenario = progress.scenario

        _scenariosCompleted.value++

        // Calculate XP based on score
        val xpMultiplier = when {
            progress.score >= 90 -> 2.0
            progress.score >= 70 -> 1.5
            progress.score >= 50 -> 1.0
            else -> 0.5
        }

        val xpEarned = (XP_TRAINING_SCENARIO * xpMultiplier).toInt()
        addXp(xpEarned)

        val result = ScenarioResult(
            scenario = scenario,
            score = progress.score,
            xpEarned = xpEarned,
            mistakes = progress.mistakes,
            duration = java.time.Duration.between(progress.startTime, Instant.now())
        )

        // Check for badges
        if (progress.score >= 90) {
            checkAndUnlockBadge(PttBadge.SCORE_MASTER)
        }
        when (scenario) {
            TrainingScenario.NightOps -> checkAndUnlockBadge(PttBadge.NIGHT_OPS)
            TrainingScenario.VehicleComms -> checkAndUnlockBadge(PttBadge.VEHICLE_COMM_SPECIALIST)
            else -> {}
        }

        // Clear active scenario
        _activeScenario.value = null
        _scenarioProgress.value = null

        return result
    }

    /**
     * Cancel active scenario
     */
    fun cancelScenario() {
        _activeScenario.value = null
        _scenarioProgress.value = null
    }

    /**
     * Add XP and check for rank up
     */
    private fun addXp(amount: Int) {
        val newXp = _currentXp.value + amount
        _currentXp.value = newXp

        // Check for rank up
        checkRankUp(newXp)
    }

    /**
     * Check if user should rank up
     */
    private fun checkRankUp(xp: Int) {
        val newRank = PttRank.values().reversed().find { xp >= it.xpRequired }
            ?: PttRank.RECRUIT

        if (newRank != _currentRank.value) {
            _currentRank.value = newRank
            saveProgress()
        }
    }

    /**
     * Check and unlock badge
     */
    private fun checkAndUnlockBadge(badge: PttBadge): Boolean {
        if (_unlockedBadges.value.contains(badge)) {
            return false // Already unlocked
        }

        val newBadges = _unlockedBadges.value.toMutableSet()
        newBadges.add(badge)
        _unlockedBadges.value = newBadges

        addXp(XP_PER_BADGE)
        saveProgress()

        return true
    }

    /**
     * Calculate time bonus for scenario
     */
    private fun calculateTimeBonus(progress: ScenarioProgress): Float {
        val elapsed = java.time.Duration.between(progress.startTime, Instant.now()).seconds
        val expectedSeconds = progress.scenario.expectedDurationSeconds

        return when {
            elapsed <= expectedSeconds * 0.5 -> 20f // Fast completion
            elapsed <= expectedSeconds -> 10f // Good timing
            else -> 0f // Too slow
        }
    }

    /**
     * Check and update daily streak
     */
    private fun checkDailyStreak() {
        val now = Instant.now()
        val lastActive = _lastActiveDate.value

        if (lastActive == null) {
            // First active day
            _dailyStreak.value = 1
            _lastActiveDate.value = now
            return
        }

        val daysSinceLastActive = java.time.Duration.between(lastActive, now).toDays()

        when {
            daysSinceLastActive == 1L -> {
                // Consecutive day
                _dailyStreak.value++
                _lastActiveDate.value = now
            }
            daysSinceLastActive <= 0L -> {
                // Same day, no change
            }
            else -> {
                // Streak broken
                _dailyStreak.value = 1
                _lastActiveDate.value = now
            }
        }
    }

    /**
     * Get XP to next rank
     */
    fun getXpToNextRank(): Int {
        val currentRank = _currentRank.value
        val ranks = PttRank.values().toList()
        val currentIndex = ranks.indexOf(currentRank)

        return if (currentIndex < ranks.size - 1) {
            val nextRank = ranks[currentIndex + 1]
            (nextRank.xpRequired - _currentXp.value).coerceAtLeast(0)
        } else {
            0 // Max rank
        }
    }

    /**
     * Get rank progress percentage
     */
    fun getRankProgress(): Float {
        val currentRank = _currentRank.value
        val ranks = PttRank.values().toList()
        val currentIndex = ranks.indexOf(currentRank)

        return if (currentIndex < ranks.size - 1) {
            val nextRank = ranks[currentIndex + 1]
            val rangeStart = currentRank.xpRequired
            val rangeEnd = nextRank.xpRequired
            val current = _currentXp.value

            ((current - rangeStart).toFloat() / (rangeEnd - rangeStart).toFloat())
                .coerceIn(0f, 1f)
        } else {
            1f // Max rank
        }
    }

    /**
     * Get training statistics
     */
    fun getStats(): TrainingStats {
        return TrainingStats(
            currentXp = _currentXp.value,
            currentRank = _currentRank.value,
            unlockedBadges = _unlockedBadges.value.size,
            totalBadges = PttBadge.entries.size,
            dailyStreak = _dailyStreak.value,
            totalTransmissions = _totalTransmissions.value,
            totalTransmitTimeMs = _totalTransmitTimeMs.value,
            totalTransmitTimeFormatted = formatDuration(_totalTransmitTimeMs.value),
            emergenciesHandled = _emergenciesHandled.value,
            scenariosCompleted = _scenariosCompleted.value,
            xpToNextRank = getXpToNextRank(),
            rankProgress = getRankProgress()
        )
    }

    /**
     * Format duration to readable string
     */
    private fun formatDuration(ms: Long): String {
        val hours = ms / 3600000
        val minutes = (ms % 3600000) / 60000
        val seconds = (ms % 60000) / 1000

        return when {
            hours > 0 -> String.format("%dh %dm", hours, minutes)
            minutes > 0 -> String.format("%dm %ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }

    /**
     * Save progress to preferences
     */
    private fun saveProgress() {
        prefs.edit().apply {
            putInt("xp", _currentXp.value)
            putString("rank", _currentRank.value.name)
            putInt("streak", _dailyStreak.value)
            putLong("lastActive", _lastActiveDate.value?.toEpochMilli() ?: 0)
            putInt("transmissions", _totalTransmissions.value)
            putLong("transmitTime", _totalTransmitTimeMs.value)
            putInt("emergencies", _emergenciesHandled.value)
            putInt("scenarios", _scenariosCompleted.value)

            // Save badges
            val badgesArray = org.json.JSONArray()
            _unlockedBadges.value.forEach { badgesArray.put(it.name) }
            putString("badges", badgesArray.toString())
        }.apply()
    }

    /**
     * Load progress from preferences
     */
    private fun loadProgress() {
        _currentXp.value = prefs.getInt("xp", 0)

        val rankName = prefs.getString("rank", PttRank.RECRUIT.name)
        _currentRank.value = try {
            PttRank.valueOf(rankName ?: PttRank.RECRUIT.name)
        } catch (e: Exception) {
            PttRank.RECRUIT
        }

        _dailyStreak.value = prefs.getInt("streak", 0)

        val lastActiveMs = prefs.getLong("lastActive", 0)
        _lastActiveDate.value = if (lastActiveMs > 0) Instant.ofEpochMilli(lastActiveMs) else null

        _totalTransmissions.value = prefs.getInt("transmissions", 0)
        _totalTransmitTimeMs.value = prefs.getLong("transmitTime", 0)
        _emergenciesHandled.value = prefs.getInt("emergencies", 0)
        _scenariosCompleted.value = prefs.getInt("scenarios", 0)

        // Load badges
        val badgesJson = prefs.getString("badges", null)
        val badgesSet = mutableSetOf<PttBadge>()
        badgesJson?.let {
            try {
                val badgesArray = org.json.JSONArray(it)
                for (i in 0 until badgesArray.length()) {
                    val badgeName = badgesArray.getString(i)
                    badgesSet.add(PttBadge.valueOf(badgeName))
                }
            } catch (e: Exception) {
                // Ignore invalid badges
            }
        }
        _unlockedBadges.value = badgesSet
    }

    /**
     * Reset all progress (for testing/debugging)
     */
    fun resetProgress() {
        prefs.edit().clear().apply()
        _currentXp.value = 0
        _currentRank.value = PttRank.RECRUIT
        _unlockedBadges.value = emptySet()
        _dailyStreak.value = 0
        _totalTransmissions.value = 0
        _totalTransmitTimeMs.value = 0
        _emergenciesHandled.value = 0
        _scenariosCompleted.value = 0
    }

    /**
     * Cleanup
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * PTT Rank levels
 */
enum class PttRank(val xpRequired: Int, val title: String, val icon: String) {
    COMMANDER(5000, "Commander", "⭐⭐⭐⭐⭐"),
    LEAD_OPERATOR(1500, "Lead Operator", "⭐⭐⭐⭐"),
    SENIOR_OPERATOR(500, "Senior Operator", "⭐⭐⭐"),
    OPERATOR(100, "Operator", "⭐⭐"),
    RECRUIT(0, "Recruit", "⭐")
}

/**
 * PTT Achievement Badges
 */
enum class PttBadge(val title: String, val description: String, val icon: String) {
    // Basic milestones
    FIRST_TRANSMISSION("First Words", "Complete your first PTT transmission", "🎙️"),
    VETERAN("Veteran", "Complete 100+ transmissions", "🏅"),
    RADIO_DISCIPLINE("Radio Discipline", "Zero accidental transmissions for 50 uses", "📻"),

    // Emergency & special operations
    EMERGENCY_RESPONDER("Emergency Responder", "Handle 5 emergency calls", "🚨"),
    NIGHT_OPS("Night Ops", "Complete training at night", "🌙"),
    VEHICLE_COMM_SPECIALIST("Vehicle Comm Specialist", "Maintain clear comms in vehicle", "🚙"),

    // Performance badges
    CLEAR_COMMS("Clear Comms", "Maintain 90%+ audio quality for 10 transmissions", "🔊"),
    SCORE_MASTER("Score Master", "Achieve 90%+ in training scenario", "🎯"),

    // Special badges
    EARLY_ADOPTER("Early Adopter", "Use PTT during beta period", "🚀"),
    COMMENDATION("Commendation", "Receive commendation from superior officer", "🎖️")
}

/**
 * Training scenarios
 */
sealed class TrainingScenario(
    val name: String,
    val description: String,
    val totalSteps: Int,
    val expectedDurationSeconds: Long
) {
    data object BasicTransmission : TrainingScenario(
        "Basic Transmission",
        "Learn proper PTT button usage and radio etiquette",
        5,
        60
    )

    data object EmergencyProtocol : TrainingScenario(
        "Emergency Protocol",
        "Practice emergency activation and response",
        8,
        120
    )

    data object CoordinatedAssault : TrainingScenario(
        "Coordinated Assault",
        "Team communication during simulated assault",
        12,
        180
    )

    data object VehicleComms : TrainingScenario(
        "Vehicle Communications",
        "Maintain clear comms while in vehicle (simulated noise)",
        6,
        90
    )

    data object NightOps : TrainingScenario(
        "Night Operations",
        "PTT usage in low-light conditions",
        5,
        90
    )

    data object StealthMode : TrainingScenario(
        "Stealth Mode",
        "Whispered communications with noise suppression",
        4,
        60
    )
}

/**
 * Scenario progress
 */
data class ScenarioProgress(
    val scenario: TrainingScenario,
    val startTime: Instant,
    val stepsCompleted: Int,
    val totalSteps: Int,
    val mistakes: Int,
    val score: Float
)

/**
 * Scenario completion result
 */
data class ScenarioResult(
    val scenario: TrainingScenario,
    val score: Float,
    val xpEarned: Int,
    val mistakes: Int,
    val duration: java.time.Duration
)

/**
 * Training statistics
 */
data class TrainingStats(
    val currentXp: Int,
    val currentRank: PttRank,
    val unlockedBadges: Int,
    val totalBadges: Int,
    val dailyStreak: Int,
    val totalTransmissions: Int,
    val totalTransmitTimeMs: Long,
    val totalTransmitTimeFormatted: String,
    val emergenciesHandled: Int,
    val scenariosCompleted: Int,
    val xpToNextRank: Int,
    val rankProgress: Float
)
