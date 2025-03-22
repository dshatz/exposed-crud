package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.DefaultText
import com.dshatz.exposeddataclass.Entity
import com.dshatz.exposeddataclass.Id
import com.dshatz.exposeddataclass.References
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

@Entity
data class Movie(
    @Id(autoGenerate = true) val id: Long,
    val title: String,

    @DefaultText("01-01-1970")
    val createdAt: String = "01-01-1970",

    val originalTitle: String?,

    @References(Director::class)
    val directorId: Long
)