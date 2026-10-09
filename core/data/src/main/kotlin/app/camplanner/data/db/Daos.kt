package app.camplanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subject ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subject ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<SubjectEntity>

    @Query("SELECT * FROM subject_alias")
    suspend fun aliases(): List<SubjectAliasEntity>

    @Insert
    suspend fun insert(subject: SubjectEntity): Long

    @Update
    suspend fun update(subject: SubjectEntity)

    @Query("DELETE FROM subject WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(alias: SubjectAliasEntity)

    @Query("UPDATE subject_alias SET subjectId = :into WHERE subjectId = :from")
    suspend fun repointAliases(from: Long, into: Long)

    @Query("SELECT COALESCE(MAX(colorIndex), -1) FROM subject")
    suspend fun maxColorIndex(): Int
}

@Dao
interface EventDao {
    @Query(
        """SELECT e.*, o.travelMode AS overrideMode FROM event e
           LEFT JOIN event_override o ON o.eventKey = e.eventKey
           WHERE e.startUtc < :toUtc AND e.endUtc > :fromUtc ORDER BY e.startUtc""",
    )
    fun observeBetween(fromUtc: Long, toUtc: Long): Flow<List<EventWithOverride>>

    @Query(
        """SELECT e.*, o.travelMode AS overrideMode FROM event e
           LEFT JOIN event_override o ON o.eventKey = e.eventKey
           WHERE e.startUtc < :toUtc AND e.endUtc > :fromUtc ORDER BY e.startUtc""",
    )
    suspend fun between(fromUtc: Long, toUtc: Long): List<EventWithOverride>

    @Query(
        """SELECT e.*, o.travelMode AS overrideMode FROM event e
           LEFT JOIN event_override o ON o.eventKey = e.eventKey WHERE e.id = :id""",
    )
    fun observe(id: Long): Flow<EventWithOverride?>

    @Query(
        """SELECT e.*, o.travelMode AS overrideMode FROM event e
           LEFT JOIN event_override o ON o.eventKey = e.eventKey WHERE e.id = :id""",
    )
    suspend fun get(id: Long): EventWithOverride?

    @Query("SELECT * FROM event WHERE allDay = 0 ORDER BY startUtc")
    suspend fun allTimed(): List<EventEntity>

    @Query("SELECT MIN(startUtc) FROM event WHERE source = 'IMPORTED'")
    suspend fun firstImportedStart(): Long?

    @Query("SELECT MAX(endUtc) FROM event WHERE source = 'IMPORTED'")
    suspend fun lastImportedEnd(): Long?

    @Query("SELECT COUNT(*) FROM event WHERE source = 'IMPORTED'")
    fun observeImportedCount(): Flow<Int>

    @Insert
    suspend fun insertAll(events: List<EventEntity>)

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Query("DELETE FROM event WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM event WHERE source = 'IMPORTED'")
    suspend fun deleteImported()

    @Query("UPDATE event SET subjectId = :into WHERE subjectId = :from")
    suspend fun repointSubject(from: Long, into: Long)

    @Query("SELECT DISTINCT location FROM event WHERE location IS NOT NULL AND location != ''")
    suspend fun distinctLocations(): List<String>

    @Upsert
    suspend fun upsertOverride(override: EventOverrideEntity)
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM location ORDER BY rawText COLLATE NOCASE")
    fun observeAll(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM location")
    suspend fun all(): List<LocationEntity>

    @Query("SELECT * FROM location WHERE status = 'PENDING'")
    suspend fun pending(): List<LocationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(location: LocationEntity)

    @Upsert
    suspend fun upsert(location: LocationEntity)

    @Query("SELECT * FROM location WHERE `key` = :key")
    suspend fun get(key: String): LocationEntity?
}

@Dao
interface AssignmentDao {
    @Query("SELECT * FROM assignment ORDER BY deadlineUtc")
    fun observeAll(): Flow<List<AssignmentEntity>>

    @Query("SELECT * FROM assignment ORDER BY deadlineUtc")
    suspend fun all(): List<AssignmentEntity>

    @Query("SELECT * FROM assignment WHERE id = :id")
    suspend fun get(id: Long): AssignmentEntity?

    @Upsert
    suspend fun upsert(assignment: AssignmentEntity): Long

    @Query("UPDATE assignment SET done = :done, completedAtUtc = :at WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, at: Long?)

    @Query("DELETE FROM assignment WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE assignment SET subjectId = :into WHERE subjectId = :from")
    suspend fun repointSubject(from: Long, into: Long)
}

@Dao
interface ReadingDao {
    @Query("SELECT * FROM reading_log")
    fun observeAll(): Flow<List<ReadingLogEntity>>

    @Query("SELECT * FROM reading_log WHERE epochDay = :day")
    fun observeDay(day: Long): Flow<List<ReadingLogEntity>>

    @Query("SELECT * FROM reading_log WHERE subjectId = :subjectId")
    suspend fun forSubject(subjectId: Long): List<ReadingLogEntity>

    @Query("SELECT * FROM reading_log WHERE epochDay = :day AND subjectId = :subjectId")
    suspend fun get(day: Long, subjectId: Long): ReadingLogEntity?

    @Upsert
    suspend fun upsert(log: ReadingLogEntity)

    @Query("DELETE FROM reading_log WHERE epochDay = :day AND subjectId = :subjectId")
    suspend fun delete(day: Long, subjectId: Long)
}

@Dao
interface FitnessDao {
    @Query("SELECT * FROM exercise ORDER BY sortOrder")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise ORDER BY sortOrder")
    suspend fun exercises(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun exerciseCount(): Int

    @Insert
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Upsert
    suspend fun upsertExercise(exercise: ExerciseEntity)

    @Query("DELETE FROM exercise WHERE id = :id AND builtInKey IS NULL")
    suspend fun deleteCustomExercise(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM exercise")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM exercise_set ORDER BY loggedAtUtc")
    fun observeSets(): Flow<List<ExerciseSetEntity>>

    @Query("SELECT * FROM exercise_set WHERE epochDay = :day")
    suspend fun setsOn(day: Long): List<ExerciseSetEntity>

    @Insert
    suspend fun insertSet(set: ExerciseSetEntity)

    @Query("DELETE FROM exercise_set WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("SELECT * FROM run_log ORDER BY epochDay DESC, id DESC")
    fun observeRuns(): Flow<List<RunLogEntity>>

    @Insert
    suspend fun insertRun(run: RunLogEntity)

    @Query("DELETE FROM run_log WHERE id = :id")
    suspend fun deleteRun(id: Long)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM checkin WHERE epochDay = :day")
    fun observe(day: Long): Flow<CheckInEntity?>

    @Upsert
    suspend fun upsert(checkIn: CheckInEntity)
}

@Dao
interface ScheduledAlarmDao {
    @Query("SELECT * FROM scheduled_alarm")
    suspend fun all(): List<ScheduledAlarmEntity>

    @Upsert
    suspend fun upsert(alarm: ScheduledAlarmEntity)

    @Query("DELETE FROM scheduled_alarm WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM scheduled_alarm")
    suspend fun clear()
}
