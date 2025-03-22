package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.*

@Entity
data class Movie(
    @Id(autoGenerate = true) val id: Long,
    val title: String,

    @DefaultText("01-01-1970")
    val createdAt: String = "01-01-1970",

    val originalTitle: String?,

    @ForeignKey(Director::class)
    val directorId: Long,

    @References(Director::class, "directorId")
    val director: Director? = null

)