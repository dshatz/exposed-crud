package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.Entity
import com.dshatz.exposeddataclass.Id

@Entity
data class Category(
    @Id(autoGenerate = true)
    val id: Long
)
