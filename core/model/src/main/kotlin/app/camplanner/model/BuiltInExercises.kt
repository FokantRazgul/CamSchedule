package app.camplanner.model

/** Seed data for the exercise table. Everything here is quiet enough for a dorm room: no jumps, no equipment. */
data class BuiltInExercise(
    val key: String,
    val name: String,
    val unit: ExerciseUnit,
    val group: ExerciseGroup,
    val defaultTarget: Int,
    val activeByDefault: Boolean,
)

object BuiltInExercises {
    const val PUSH_UPS = "pushups"
    const val PULL_UPS = "pullups"

    val all: List<BuiltInExercise> = listOf(
        BuiltInExercise(PUSH_UPS, "Push-ups", ExerciseUnit.REPS, ExerciseGroup.UPPER, 100, true),
        BuiltInExercise(PULL_UPS, "Pull-ups", ExerciseUnit.REPS, ExerciseGroup.UPPER, 100, true),
        BuiltInExercise("plank", "Plank", ExerciseUnit.SECONDS, ExerciseGroup.ABS, 90, true),
        BuiltInExercise("leg_raises", "Leg raises", ExerciseUnit.REPS, ExerciseGroup.ABS, 30, true),
        BuiltInExercise("crunches", "Crunches", ExerciseUnit.REPS, ExerciseGroup.ABS, 40, false),
        BuiltInExercise("hollow_hold", "Hollow hold", ExerciseUnit.SECONDS, ExerciseGroup.ABS, 45, false),
        BuiltInExercise("squats", "Squats", ExerciseUnit.REPS, ExerciseGroup.LEGS, 60, true),
        BuiltInExercise("lunges", "Lunges", ExerciseUnit.REPS, ExerciseGroup.LEGS, 40, false),
        BuiltInExercise("wall_sit", "Wall sit", ExerciseUnit.SECONDS, ExerciseGroup.LEGS, 60, false),
        BuiltInExercise("glute_bridges", "Glute bridges", ExerciseUnit.REPS, ExerciseGroup.LEGS, 40, true),
        BuiltInExercise("calf_raises", "Calf raises", ExerciseUnit.REPS, ExerciseGroup.LEGS, 60, false),
        BuiltInExercise(
            "bulgarian_split_squats", "Bulgarian split squats (chair)", ExerciseUnit.REPS, ExerciseGroup.LEGS, 24, false,
        ),
    )
}
