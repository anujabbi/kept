package com.example.kept.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Star
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.kept.core.domain.ProofType

object HabitIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "run" to Icons.Outlined.DirectionsRun,
        "book" to Icons.Outlined.MenuBook,
        "sleep" to Icons.Outlined.Bedtime,
        "music" to Icons.Outlined.MusicNote,
        "study" to Icons.Outlined.School,
        "tidy" to Icons.Outlined.CleaningServices,
        "water" to Icons.Outlined.LocalDrink,
        "gym" to Icons.Outlined.FitnessCenter,
        "calm" to Icons.Outlined.SelfImprovement,
        "art" to Icons.Outlined.Brush,
        "write" to Icons.Outlined.Edit,
        "star" to Icons.Outlined.Star,
    )

    fun of(key: String): ImageVector = all.firstOrNull { it.first == key }?.second ?: Icons.Outlined.Star
}

data class HabitTemplate(val title: String, val iconKey: String, val proofType: ProofType, val targetValue: Int, val unit: String) {
    val subtitle: String get() = when (proofType) {
        ProofType.TIMER -> "$targetValue min"
        ProofType.MANUAL -> if (targetValue > 1) "$targetValue $unit" else unit
        ProofType.PHOTO -> "photo proof"
    }
}

object HabitTemplates {
    val list = listOf(
        HabitTemplate("Exercise", "run", ProofType.TIMER, 30, "min"),
        HabitTemplate("Read", "book", ProofType.MANUAL, 10, "pages"),
        HabitTemplate("Practice", "music", ProofType.TIMER, 20, "min"),
        HabitTemplate("Study", "study", ProofType.TIMER, 45, "min"),
        HabitTemplate("Tidy room", "tidy", ProofType.PHOTO, 1, "photo"),
        HabitTemplate("Sleep by 11", "sleep", ProofType.MANUAL, 1, "check-in"),
    )
}
