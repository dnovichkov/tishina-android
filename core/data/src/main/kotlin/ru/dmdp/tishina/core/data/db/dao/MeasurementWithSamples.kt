package ru.dmdp.tishina.core.data.db.dao

import androidx.room.Embedded
import androidx.room.Relation
import ru.dmdp.tishina.core.data.db.entity.MeasurementEntity
import ru.dmdp.tishina.core.data.db.entity.SampleEntity

/**
 * Room `@Relation` aggregate returned by [MeasurementDao.getDetailsById]. Lets Room run
 * a single optimized join (one SELECT for `measurements`, one for `samples`) instead of
 * forcing the repository to stitch the two together manually.
 */
data class MeasurementWithSamples(
    @Embedded val measurement: MeasurementEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "measurementId",
    )
    val samples: List<SampleEntity>,
)
