package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.*

@Entity
data class Category(
    @Id(autoGenerate = true)
    val id: Long,
    @Default("false")
    val adult: Boolean = false
)
