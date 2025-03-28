package com.dshatz.exposed_crud.models

import com.dshatz.exposed_crud.BackReference
import com.dshatz.exposed_crud.Entity
import com.dshatz.exposed_crud.Id

@Entity
data class Director(
    @Id(true)
    val id: Long = -1,
    val name: String,

    @BackReference(Movie::class)
    val movies: List<Movie>? = null
)