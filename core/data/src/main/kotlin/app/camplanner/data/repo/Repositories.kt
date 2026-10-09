package app.camplanner.data.repo

import app.camplanner.data.db.AssignmentEntity
import app.camplanner.data.db.CamPlannerDatabase
import app.camplanner.data.db.CheckInEntity
import app.camplanner.data.db.ExerciseEntity
import app.camplanner.data.db.ExerciseSetEntity
import app.camplanner.data.db.ReadingLogEntity
import app.camplanner.data.db.RunLogEntity
import app.camplanner.model.Assignment
import app.camplanner.model.BuiltInExercises
import app.camplanner.model.Exercise
import app.camplanner.model.ExerciseGroup
import app.camplanner.model.ExerciseSet
import app.camplanner.model.ExerciseUnit
import app.camplanner.model.ReadingLog
import app.camplanner.model.RunLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate

class HomeworkRepository(private val db: CamPlannerDatabase) {
    private val dao = db.assignments()

    fun observeAll(): Flow<List<Assignment>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun all(): List<Assignment> = dao.all().map { it.toModel() }

    suspend fun get(id: Long): Assignment? = dao.get(id)?.toModel()

    suspend fun save(a: Assignment): Long {
        val rowId = dao.upsert(
        AssignmentEntity(
            id = a.id,
            title = a.title.trim(),
            subjectId = a.subjectId,
            description = a.description.trim(),
            deadlineUtc = a.deadline.toEpochMilli(),
            done = a.done,
            completedAtUtc = if (a.done) (a.completedAt ?: Instant.now()).toEpochMilli() else null,
        ),
    )
        // @Upsert reports -1 when it updated an existing row.
        return if (a.id != 0L) a.id else rowId
    }

    suspend fun setDone(id: Long, done: Boolean) =
        dao.setDone(id, done, if (done) System.currentTimeMillis() else null)

    suspend fun delete(id: Long) = dao.delete(id)
}

class ReadingRepository(private val db: CamPlannerDatabase) {
    fun observeAll(): Flow<List<ReadingLog>> = db.reading().observeAll().map { list -> list.map { it.toModel() } }

    fun observeDay(date: LocalDate): Flow<List<ReadingLog>> =
        db.reading().observeDay(date.toEpochDay()).map { list -> list.map { it.toModel() } }

    suspend fun setPages(date: LocalDate, subjectId: Long, pages: Int?) {
        if (pages == null || pages <= 0) db.reading().delete(date.toEpochDay(), subjectId)
        else db.reading().upsert(ReadingLogEntity(date.toEpochDay(), subjectId, pages))
    }
}

class FitnessRepository(private val db: CamPlannerDatabase) {
    private val dao = db.fitness()

    /** Seeds the built-in, dorm-friendly exercise set on first run. */
    suspend fun ensureSeeded() {
        if (dao.exerciseCount() > 0) return
        dao.insertExercises(
            BuiltInExercises.all.mapIndexed { i, b ->
                ExerciseEntity(
                    builtInKey = b.key, name = b.name, unit = b.unit.name, exerciseGroup = b.group.name,
                    active = b.activeByDefault, dailyTarget = b.defaultTarget, sortOrder = i,
                )
            },
        )
    }

    fun observeExercises(): Flow<List<Exercise>> = dao.observeExercises().map { list -> list.map { it.toModel() } }

    suspend fun exercises(): List<Exercise> = dao.exercises().map { it.toModel() }

    fun observeSets(): Flow<List<ExerciseSet>> = dao.observeSets().map { list -> list.map { it.toModel() } }

    suspend fun setsOn(date: LocalDate): List<ExerciseSet> = dao.setsOn(date.toEpochDay()).map { it.toModel() }

    fun observeRuns(): Flow<List<RunLog>> = dao.observeRuns().map { list -> list.map { it.toModel() } }

    suspend fun log(exerciseId: Long, amount: Int, date: LocalDate) {
        if (amount <= 0) return
        dao.insertSet(ExerciseSetEntity(exerciseId = exerciseId, epochDay = date.toEpochDay(), amount = amount, loggedAtUtc = System.currentTimeMillis()))
    }

    suspend fun deleteSet(id: Long) = dao.deleteSet(id)

    suspend fun saveExercise(exercise: Exercise) = dao.upsertExercise(exercise.toEntity())

    suspend fun addCustom(name: String, unit: ExerciseUnit, target: Int) {
        dao.upsertExercise(
            ExerciseEntity(
                builtInKey = null, name = name.trim(), unit = unit.name, exerciseGroup = ExerciseGroup.CUSTOM.name,
                active = true, dailyTarget = target, sortOrder = dao.maxSortOrder() + 1,
            ),
        )
    }

    suspend fun deleteCustom(id: Long) = dao.deleteCustomExercise(id)

    suspend fun logRun(date: LocalDate, distanceMeters: Int, durationSeconds: Int) =
        dao.insertRun(RunLogEntity(epochDay = date.toEpochDay(), distanceMeters = distanceMeters, durationSeconds = durationSeconds))

    suspend fun deleteRun(id: Long) = dao.deleteRun(id)
}

class CheckInRepository(private val db: CamPlannerDatabase) {
    fun observeCompleted(date: LocalDate): Flow<Boolean> =
        db.checkIns().observe(date.toEpochDay()).map { it != null }

    suspend fun complete(date: LocalDate) =
        db.checkIns().upsert(CheckInEntity(date.toEpochDay(), System.currentTimeMillis()))
}
