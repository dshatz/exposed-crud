package com.dshatz.exposeddataclass

import com.dshatz.exposeddataclass.models.*
import com.dshatz.exposeddataclass.models.CategoryTable.toEntityList
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.*

class TestDB {

    private lateinit var db: Database
    @BeforeTest
    fun init() {
        db = Database.connect("jdbc:sqlite:memory:?foreign_keys=on", "org.sqlite.JDBC")
        transaction(db) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.drop(DirectorTable, MovieTable, LanguageTable, CategoryTable, CategoryTranslationsTable)
            SchemaUtils.create(DirectorTable, MovieTable, LanguageTable, CategoryTable, CategoryTranslationsTable)
        }
        transaction(db) {
            println(SchemaUtils.listTables())
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
        val lang = "lv"
        LanguageTable.repo.create(Language(lang))
        val catId = CategoryTable.repo.createReturning(Category_Data()).id
        CategoryTranslationsTable.repo.create(
            CategoryTranslations(
                catId, lang, "Latviski"
            )
        )
        val found = CategoryTranslationsTable.repo.findById(catId, lang)
        assertEquals("Latviski", found?.translation)

        val withTranslations = CategoryTable.repo.withRelated(CategoryTranslationsTable).findById(catId)
        assertNotNull(withTranslations)
    }

    @Test
    fun `foreign key`(): Unit = transaction {
        val directorId = DirectorTable.repo.createReturning(Director_Data("Alfred")).id
        LanguageTable.repo.insert(Language("lv"))
//        val catId = CategoryTable.repo.createWithRelated(Category_Data(0), CategoryTranslations(0, "lv", "Latviski")).id

        /*MovieTable.repo.create(Movie_Data("The Birds", "01-01-1963", null, directorId, catId))
        val movie = MovieTable.repo.select().where(MovieTable.directorId eq directorId).first()
        assertEquals("The Birds", movie.title)*/
    }

    @Test
    fun `foreign key with ref`(): Unit = transaction {
        val directorId = DirectorTable.repo.createReturning(Director_Data("Alfred")).id
        val categoryId = CategoryTable.repo.createReturning(Category_Data()).id
        println(CategoryTranslationsTable.repo.createWithRelated(
            CategoryTranslations(categoryId, "", "Latviski"),
            language = Language("lv")
        ))
        MovieTable.repo.create(Movie_Data("The Birds", "01-01-1963", null, directorId, categoryId))

        val movieWithDirector = MovieTable.repo.withRelated(DirectorTable).selectAll().first()
        assertEquals("Alfred", movieWithDirector.director?.name)
        assertEquals("The Birds", movieWithDirector.title)
    }

    @Test
    fun `insert with related`() = transaction {
        val movie = MovieTable.repo.createWithRelated(
            movie = Movie_Data("Die Hard", "",  null, -1, -1),
            director = Director_Data("John McTiernan"),
            category = Category_Data()
        )

        assertNotEquals(-1, movie.directorId)
        assertNotEquals(-1, movie.categoryId)
    }


}
