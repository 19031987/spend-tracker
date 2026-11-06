package com.spendtracker.app.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtracker.app.data.GoalContributionEntity
import com.spendtracker.app.data.GoalEntity
import com.spendtracker.app.data.PlanningRepository
import com.spendtracker.app.domain.goals.GoalCalculator
import com.spendtracker.app.domain.goals.GoalProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class GoalsUiState(
    val goals: List<GoalProgress> = emptyList(),
    val selectedGoalId: Long? = null,
    val selectedGoal: GoalProgress? = null,
    /** Contributions of the selected goal, newest first. */
    val selectedContributions: List<GoalContributionEntity> = emptyList(),
    val totalSavedMinor: Long = 0L,
    val totalTargetMinor: Long = 0L,
    /** Raw (non-archived) entities, used by the edit sheet for starting amounts. */
    val goalEntities: List<GoalEntity> = emptyList()
)

class GoalsViewModel(private val planningRepository: PlanningRepository) : ViewModel() {

    private val selectedId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<GoalsUiState> = combine(
        planningRepository.observeGoals(),
        planningRepository.observeContributions(),
        selectedId
    ) { goals: List<GoalEntity>, contributions: List<GoalContributionEntity>, selected: Long? ->
        val zone: ZoneId = ZoneId.systemDefault()
        val progress: List<GoalProgress> =
            GoalCalculator.summary(goals, contributions, LocalDate.now(zone), zone)
        val selectedGoal: GoalProgress? = progress.firstOrNull { it.goalId == selected }
        GoalsUiState(
            goals = progress,
            selectedGoalId = selectedGoal?.goalId,
            selectedGoal = selectedGoal,
            selectedContributions = if (selectedGoal == null) {
                emptyList()
            } else {
                contributions
                    .filter { it.goalId == selectedGoal.goalId }
                    .sortedWith(compareByDescending<GoalContributionEntity> { it.timestamp }.thenByDescending { it.id })
            },
            totalSavedMinor = progress.sumOf { it.savedMinor },
            totalTargetMinor = progress.sumOf { it.targetMinor },
            goalEntities = goals.filter { !it.archived }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    fun createGoal(
        name: String,
        emoji: String,
        colorHex: String,
        targetMinor: Long,
        targetDate: Long?,
        startingMinor: Long
    ) {
        viewModelScope.launch {
            planningRepository.createGoal(name.trim(), emoji, colorHex, targetMinor, targetDate, startingMinor)
        }
    }

    fun updateGoal(
        id: Long,
        name: String,
        emoji: String,
        colorHex: String,
        targetMinor: Long,
        targetDate: Long?
    ) {
        viewModelScope.launch {
            val existing: GoalEntity = planningRepository.observeGoals().first()
                .firstOrNull { it.id == id } ?: return@launch
            planningRepository.updateGoal(
                existing.copy(
                    name = name.trim(),
                    emoji = emoji,
                    colorHex = colorHex,
                    targetMinor = targetMinor,
                    targetDate = targetDate
                )
            )
        }
    }

    fun archiveGoal(id: Long) {
        viewModelScope.launch {
            val existing: GoalEntity = planningRepository.observeGoals().first()
                .firstOrNull { it.id == id } ?: return@launch
            planningRepository.updateGoal(existing.copy(archived = true))
            selectedId.update { current -> if (current == id) null else current }
        }
    }

    fun deleteGoal(id: Long) {
        viewModelScope.launch {
            planningRepository.deleteGoal(id)
            selectedId.update { current -> if (current == id) null else current }
        }
    }

    fun addMoney(goalId: Long, amountMinor: Long, note: String?) {
        if (amountMinor <= 0L) return
        viewModelScope.launch {
            planningRepository.addContribution(goalId, amountMinor, note?.trim()?.ifBlank { null })
        }
    }

    fun withdraw(goalId: Long, amountMinor: Long, note: String?) {
        if (amountMinor <= 0L) return
        viewModelScope.launch {
            planningRepository.addContribution(goalId, -amountMinor, note?.trim()?.ifBlank { null })
        }
    }

    fun deleteContribution(id: Long) {
        viewModelScope.launch { planningRepository.deleteContribution(id) }
    }

    fun openGoal(id: Long) {
        selectedId.value = id
    }

    fun closeGoal() {
        selectedId.value = null
    }
}
