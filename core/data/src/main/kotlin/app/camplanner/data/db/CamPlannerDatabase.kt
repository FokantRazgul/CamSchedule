package app.camplanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubjectEntity::class,
        SubjectAliasEntity::class,
        EventEntity::class,
        EventOverrideEntity::class,
        LocationEntity::class,
        AssignmentEntity::class,
        ReadingLogEntity::class,
        ExerciseEntity::class,
        ExerciseSetEntity::class,
        RunLogEntity::class,
        CheckInEntity::class,
        ScheduledAlarmEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CamPlannerDatabase : RoomDatabase() {
    abstract fun subjects(): SubjectDao
    abstract fun events(): EventDao
    abstract fun locations(): LocationDao
    abstract fun assignments(): AssignmentDao
    abstract fun reading(): ReadingDao
    abstract fun fitness(): FitnessDao
    abstract fun checkIns(): CheckInDao
    abstract fun scheduledAlarms(): ScheduledAlarmDao

    companion object {
        @Volatile private var instance: CamPlannerDatabase? = null

        /** One instance per process: receivers, workers and the UI all share it. */
        fun get(context: Context): CamPlannerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CamPlannerDatabase::class.java, "camplanner.db")
                .build()
                .also { instance = it }
        }
    }
}
