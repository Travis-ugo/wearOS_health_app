package com.example.healthapp.presentation

import android.content.ContentValues
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.ExerciseType
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material.ContentAlpha
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.healthapp.presentation.theme.HealthAppTheme
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        val healthClient = HealthServices.getClient(this.applicationContext)

        val exerciseClient = healthClient.exerciseClient

        val viewModel = ExerciseClientViewModel(exerciseClient)

        setContent {
            WearApp(viewModel)
        }
    }
}

@Composable
fun WearApp(viewModel: ExerciseClientViewModel) {
    val state by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if( result.all { it.value } ){
            Log.d(ContentValues.TAG, "All request permissions granted")
        }
    }
    
    LaunchedEffect(Unit) {
        launch {
            permissionLauncher.launch(viewModel.permission)
        }
    }

    HealthAppTheme {
        Box {
            if (state.selectedExerciseType != null) {
                if (state.exerciseStarted) {
                    ExerciseInProgressScreen(
                        duration = state.duration.toString(),
                        goalsAchieved = state.goalsAccomplished,
                        mileStone = state.milestoneAccomplished,
                        heartRate = state.heartRate,
                        isPaused = state.exercisePaused,
                        onEndClick=  { viewModel.endWorkOut() },
                        onPauseClick=  { viewModel.pauseWorkOut() },
                        onResumeClick=  { viewModel.resumeWorkOut() },
                    )
                } else {
                    Log.d("MainActivity", "selectedExerciseType is not null")
                    ExerciseTypeScreen(
                        exerciseType = state.selectedExerciseType!!,
                        onStart = {viewModel.maybeStartExercise(state.selectedExerciseType!!)},
                        onBackClicked = {
                            viewModel.setSelectedExerciseType(null)
                        })
                }

            } else {
                Log.d("MainActivity", "selectedExerciseType is null")
                SupportedExerciseList(
                    exerciseTypes = state.compatibilities?.supportedExerciseTypes?.toList().orEmpty(),
                    onClick = { viewModel.setSelectedExerciseType(it) }
                )
            }
        }
    }
}



@Composable
fun ExerciseTypeScreen(
    exerciseType: ExerciseType,
    onStart: () -> Unit,
    onBackClicked: () -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                BackButton {
                    onBackClicked()
                }
                Row(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clickable { onStart() }
                        .border(
                            2.dp,
                            color = MaterialTheme.colors.primary,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        textAlign = TextAlign.Center,
                        text = "Start exercise",
                        fontSize = MaterialTheme.typography.body2.fontSize,
                    )

                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

            }
        }

        item {
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                text = convertToTitleCase(exerciseType.name),
            )
        }
    }

}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(26.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowBackIosNew,
            contentDescription = "Back",
            tint = Color.White
        )
    }
}
@Composable
fun ExerciseTypeButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    backgroundColor: Color = MaterialTheme.colors.primary,
    contentColor: Color = MaterialTheme.colors.onPrimary
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled, onClick = onClick)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(8.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = convertToTitleCase(text),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
            fontSize = MaterialTheme.typography.body1.fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = contentColor.copy(alpha = if (enabled) ContentAlpha.medium else ContentAlpha.disabled)
        )
    }
}

@Composable
fun SupportedExerciseList(exerciseTypes: List<ExerciseType>, onClick: (ExerciseType) -> Unit) {
    return ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        itemsIndexed(exerciseTypes)  {_, exerciseType ->
            ExerciseTypeButton(
                onClick = {onClick(exerciseType)},
                text = convertToTitleCase(exerciseType.name),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ExerciseInProgressScreen(
    duration: String,
    goalsAchieved: Int,
    mileStone: Int,
    heartRate: Double,
    isPaused: Boolean,
    onEndClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
) {

    Column(
        modifier =  Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row{
            if (goalsAchieved != 0) {
                repeat(goalsAchieved) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(
                                CircleShape
                            )
                            .background(MaterialTheme.colors.secondary)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(12.dp))
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        Row {
            if (goalsAchieved != 0) {
                repeat(mileStone) {
                    Box(modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colors.secondary))
                }
            } else {
                Spacer(modifier = Modifier.size(12.dp))
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        if (!isPaused) {
            Text(text = "Active duration: $duration ms")
            Row {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "favorite",
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(text =  String.format(Locale.US, "%.2f", heartRate))
            }
        } else {
            Text(text = "You are doing great")
            Text(text = "Exercise paused")
        }
        Spacer(modifier = Modifier.size(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
                EndButton {onEndClick() }
            if (isPaused) ResumeButton {
                onResumeClick()
            } else PauseButton {
                onPauseClick()
            }

        }
    }
}

@Composable
fun EndButton(onEndClick: () -> Unit) {
    IconButton(
        onClick = onEndClick,
        modifier = Modifier
            .size(26.dp)
            .background(Color.Gray, CircleShape)
    ) {
        Icon(
            Icons.Filled.Stop,
            contentDescription = "End",
            tint = Color.White
        )
    }
}

@Composable
fun ResumeButton(onResumeClick: () -> Unit) {
    IconButton(
        onClick = onResumeClick,
        modifier = Modifier
            .size(26.dp)
            .background(Color.Gray, CircleShape)
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = Color.White
        )
    }
}

@Composable
fun PauseButton(onPauseClick: () -> Unit) {
    IconButton(
        onClick = onPauseClick,
        modifier = Modifier
            .size(26.dp)
            .background(Color.Gray, CircleShape)
    ) {
        Icon(
            imageVector = Icons.Filled.Pause,
            contentDescription = "Pause",
            tint = Color.White
        )
    }
}

fun convertToTitleCase(input: String): String {
    val words = input.split("_")
    val output = StringBuilder()

    for (word in words) {
        if (word.isNotEmpty()) {
            output.append(word.substring(0, 1).uppercase())
                .append(word.substring(1).lowercase())
                .append(" ")
        }
    }

    return output.toString().trim()
}