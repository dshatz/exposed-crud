package com.dshatz.exposed_crud.models

import com.dshatz.exposed_crud.*

@Entity
data class Movie(
    @Id(autoGenerate = true) val id: Long,
    val title: String,

    @DefaultText("01-01-1970")
    val createdAt: String = "01-01-1970",

    val originalTitle: String?,

    @ForeignKey(Director::class)
    val directorId: Long,

    @ForeignKey(Category::class)
    val categoryId: Long,

    @References(Director::class, "directorId")
    val director: Director? = null,

    @References(Category::class, "categoryId")
    val category: Category? = null

)