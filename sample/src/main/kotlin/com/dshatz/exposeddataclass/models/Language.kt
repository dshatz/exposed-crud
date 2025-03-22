package com.dshatz.exposeddataclass.models

import com.dshatz.exposeddataclass.Entity
import com.dshatz.exposeddataclass.Id

@Entity
data class Language(
    @Id val code: String
)