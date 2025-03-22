package com.dshatz.exposeddataclass

import com.dshatz.exposeddataclass.models.*
import com.dshatz.exposeddataclass.models.CategoryTable.toEntityList
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TestDB {

    private lateinit var db: Database
    @BeforeTest
    fun init() {
        db = Database.connect("jdbc:sqlite:memory:", "org.sqlite.JDBC")
        transaction(db) {
            SchemaUtils.drop(DirectorTable, MovieTable, LanguageTable, CategoryTable, CategoryTranslationsTable)
            SchemaUtils.create(DirectorTable, MovieTable, LanguageTable, CategoryTable, CategoryTranslationsTable)
        }
        transaction(db) {
            SchemaUtils.listTables()
        }
    }

    /*@Test
    fun `test insert`() = transaction {
        *//*DirectorEntity.new {
            name = "Alfred"
        }
        val inserted = DirectorEntity.find(DirectorTable.name eq "Alfred").first()
        assertEquals("Alfred", inserted.name)*//*
    }*/

    @Test
    fun `typed select all`() {
        transaction(db) {
            DirectorTable.insert {
                it[name] = "Alfred"
            }
            val all = DirectorTable.repo.selectAll()
            assertEquals(1, all.size)
            assertEquals("Alfred", all.first().name)
        }
    }

    @Test
    fun `typed insert`() = transaction {
        DirectorTable.repo.create(Director_Data("Bob"))
        assertEquals("Bob", DirectorTable.repo.selectAll().first().name)
    }

    @Test
    fun `update custom where`() = transaction {
        DirectorTable.repo.create(Director_Data("Bob"))
        val id = DirectorTable.repo.selectAll().find { it.name == "Bob" }!!.id
        DirectorTable.repo.update({ DirectorTable.id eq id }, Director_Data("Marley"))

        assertEquals("Marley", DirectorTable.repo.selectAll().first().name)
    }

    @Test
    fun `update by simple primary key`() = transaction {
        val id = DirectorTable.repo.createReturning(Director_Data("Bob")).id
        DirectorTable.repo.update(Director(id, "Marley"))
        assertEquals("Marley", DirectorTable.repo.selectAll().first().name)
    }

    @Test
    fun `select where`() = transaction {
        val id = DirectorTable.repo.createReturning(Director_Data("Bob")).id
        val director = DirectorTable.repo.select().where {
            DirectorTable.name eq "Bob"
        }.first()

        assertEquals(id, director.id)
        assertEquals("Bob", director.name)
    }

    @Test
    fun `find by id`() = transaction {
        val directorId = DirectorTable.repo.createReturning(Director_Data("Alfred")).id

        val found = DirectorTable.repo.findById(directorId)
        assertNotNull(found)
        assertEquals("Alfred", found.name)
    }

    @Test
    fun `composite ids`(): Unit = transaction {
        LanguageTable.repo.create(Language("lv"))
        CategoryTable.repo.create(Category(0))
        CategoryTranslationsTable.repo.create(
            CategoryTranslations(
            0, "lv", "Latviski"
        )
        )
        val found = CategoryTranslationsTable.repo.findById(0, "lv")
        assertEquals("Latviski", found?.translation)
    }

    @Test
    fun `foreign key`(): Unit = transaction {
        val directorId = DirectorTable.repo.createReturning(Director_Data("Alfred")).id
        MovieTable.repo.create(Movie_Data("The Birds", "01-01-1963", null, directorId))
        val movie = MovieTable.repo.select().where(MovieTable.directorId eq directorId).first()
        assertEquals("The Birds", movie.title)
    }

    @Test
    fun `foreign key with ref`(): Unit = transaction {
        val directorId = DirectorTable.repo.createReturning(Director_Data("Alfred")).id
        MovieTable.repo.create(Movie_Data("The Birds", "01-01-1963", null, directorId))

        val movieWithDirector = MovieTable.repo.withRelated(DirectorTable).selectAll().first()
        assertEquals("Alfred", movieWithDirector.director?.name)
        assertEquals("The Birds", movieWithDirector.title)
    }


}
