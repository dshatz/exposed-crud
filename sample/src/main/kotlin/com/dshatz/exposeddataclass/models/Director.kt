package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.Entity
import com.dshatz.exposeddataclass.Id

@Entity
data class Director(
    @Id(true)
    val id: Long,
    val name: String,
)