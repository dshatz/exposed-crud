package com.dshatz.exposed_crud.models

import com.dshatz.exposed_crud.BackReference
import com.dshatz.exposed_crud.Default
import com.dshatz.exposed_crud.Entity
import com.dshatz.exposed_crud.Id

@Entity
data class Category(
    @Id(autoGenerate = true)
    val id: Long = -1,
    @Default("false")
    val adult: Boolean = false,

    @BackReference(CategoryTranslations::class)
    val translations: List<CategoryTranslations>? = null,

    @BackReference(Movie::class)
    val movies: List<Movie>? = null
)
