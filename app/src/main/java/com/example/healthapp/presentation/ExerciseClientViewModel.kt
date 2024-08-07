package com.example.healthapp.presentation
import android.util.Log
import androidx.health.services.client.data.*
import androidx.health.services.client.ExerciseClient
import androidx.health.services.client.ExerciseUpdateCallback
import androidx.health.services.client.data.ExerciseCapabilities
import androidx.health.services.client.endExercise
import androidx.health.services.client.getCapabilities
import androidx.health.services.client.getCurrentExerciseInfo
import androidx.health.services.client.pauseExercise
import androidx.health.services.client.resumeExercise
import androidx.health.services.client.startExercise
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant


data class ExerciseClientViewState(
    val compatibilities: ExerciseCapabilities? = null,
    val selectedExerciseType: ExerciseType? = null,
    val exerciseStarted: Boolean = false,
    val exercisePaused: Boolean = false,
    val goalsAccomplished: Int = 0,
    val duration: Long = 0L,
    val heartRate: Double = 0.0,
    val  milestoneAccomplished: Int = 0,
) {
    val exerciseCompatibilities = selectedExerciseType?.let {
        compatibilities?.getExerciseTypeCapabilities(it)
    }
}


class ExerciseClientViewModel(private val exerciseClient: ExerciseClient) : ViewModel() {
    private val _uiState = MutableStateFlow(ExerciseClientViewState())
    val uiState: StateFlow<ExerciseClientViewState> = _uiState

    val permission = arrayOf(
        android.Manifest.permission.BODY_SENSORS,
        android.Manifest.permission.ACTIVITY_RECOGNITION,
    )

    private val exerciseCallback = object : ExerciseUpdateCallback {
        override fun onAvailabilityChanged(dataType: DataType<*, *>, availability: Availability) {
            Log.d("Exercise app", "onAvailabilityChanged $availability")
        }

        override fun onExerciseUpdateReceived(update: ExerciseUpdate) {
            val exerciseStateInfo = update.exerciseStateInfo
            val activeDuration = update.activeDurationCheckpoint
            val latestMetrics = update.latestMetrics
            val latestGoals = update.latestAchievedGoals
            val latestMilestoneMarkerSummary = update.latestMilestoneMarkerSummaries

            val (inProgress, paused) = when {
                exerciseStateInfo.state.isPaused -> Pair(true, true)
                exerciseStateInfo.state.isResuming -> Pair(true, false)
                exerciseStateInfo.state.isEnded -> Pair(false, false)
                exerciseStateInfo.state == ExerciseState.ACTIVE -> Pair(true, false)
                else -> Pair(false, false) // Default case
            }

            if (inProgress) {
                val duration = if (activeDuration != null) {
                    (
                            (Instant.now().toEpochMilli() - activeDuration.time.toEpochMilli()) +
                                    activeDuration.activeDuration.toMillis()
                            )
                } else 0

                _uiState.value = _uiState.value.copy(
                    exerciseStarted = true,
                    exercisePaused = paused,
                    goalsAccomplished = _uiState.value.goalsAccomplished + latestGoals.size,
                    milestoneAccomplished = _uiState.value.milestoneAccomplished + latestMilestoneMarkerSummary.size,
                    duration = duration,
                    heartRate = latestMetrics.getData(DataType.HEART_RATE_BPM_STATS)?.average
                        ?: 0.0,
                )
                // Handle the case where the exercise is in progress

            } else {
                _uiState.value = _uiState.value.copy(
                    exerciseStarted = false,
                    exercisePaused = false,
                    goalsAccomplished = 0,
                    duration = 0L,
                    heartRate = 0.0,
                    milestoneAccomplished = 0,
                )
            }
        }

        override fun onLapSummaryReceived(lapSummary: ExerciseLapSummary) {
            TODO("Not yet implemented")
        }

        override fun onRegistered() {
            Log.d("Exercise app", "Registered")
        }

        override fun onRegistrationFailed(throwable: Throwable) {
            Log.d("Exercise app", "onRegistrationFailed", throwable)
        }
    }

    init {
        getCapabilities()
    }

    private fun getCapabilities() {
        viewModelScope.launch {
            try {
                val compatibilities = exerciseClient.getCapabilities()
                _uiState.value = _uiState.value.copy(compatibilities = compatibilities)
            } catch (e: Exception) {
                Log.e("ExerciseClientViewModel", "Error getting capabilities", e)
            }
        }
    }

    fun setSelectedExerciseType(exerciseType: ExerciseType?) {
        _uiState.value = _uiState.value.copy(selectedExerciseType = exerciseType)
    }

    fun maybeStartExercise(exerciseType: ExerciseType) {
        viewModelScope.launch {
            val exerciseInfo = exerciseClient.getCurrentExerciseInfo()
            val canStart = when (exerciseInfo.exerciseTrackedStatus) {
                ExerciseTrackedStatus.OTHER_APP_IN_PROGRESS -> false
                ExerciseTrackedStatus.OWNED_EXERCISE_IN_PROGRESS -> false
                ExerciseTrackedStatus.NO_EXERCISE_IN_PROGRESS -> true
                else -> false
            }

            if (canStart) {
                startExercise(exerciseType)
            }
        }
    }


    private suspend fun startExercise(exerciseType: ExerciseType) {
        val dataType = setOf(
            DataType.HEART_RATE_BPM,
            DataType.HEART_RATE_BPM_STATS,
            DataType.CALORIES_TOTAL,
        )

        val durationGoal = ExerciseGoal.createOneTimeGoal(
            DataTypeCondition(
                dataType = DataType.ACTIVE_EXERCISE_DURATION_TOTAL,
                threshold = 60,
                comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL
            )
        )

        val milestone = ExerciseGoal.createMilestone(
            DataTypeCondition(
                dataType = DataType.ACTIVE_EXERCISE_DURATION_TOTAL,
                threshold = 60,
                comparisonType = ComparisonType.GREATER_THAN_OR_EQUAL
            ),
            period = 60,
        )

        val config = ExerciseConfig(
            exerciseType = exerciseType,
            dataTypes = dataType,
            isAutoPauseAndResumeEnabled = false,
            isGpsEnabled = false,
            exerciseGoals = mutableListOf(durationGoal, milestone)
        )
        exerciseClient.setUpdateCallback(callback = exerciseCallback)
        exerciseClient.startExercise(config)
    }

    fun pauseWorkOut() {
        viewModelScope.launch {
            exerciseClient.pauseExercise()
        }
    }

    fun resumeWorkOut() {
        viewModelScope.launch {
            exerciseClient.resumeExercise()
        }
    }

    fun endWorkOut() {
        viewModelScope.launch {
            exerciseClient.endExercise()
        }
    }
}