![](https://github.com/dshatz/exposed-crud/actions/workflows/build.yaml/badge.svg)
# Exposed (JetBrains) CRUD repository generator. 

This is a KSP processor to simplify working with [Jetbrains Exposed](https://github.com/JetBrains/Exposed).
Loosely inspired by JPA annotations.

You define a Kotlin `data class` holding your data and use annotations to mark Primary Keys, Relationships and others.

Based on your annotations this library will generate:
 - Exposed table DSL.
 - A `CrudRepository` class with strongly-typed CRUD methods based on your data class.
 - An additional data class ending with `_Data` that contains all properties except the auto-incrementing ones.

[Sample data classes](https://github.com/dshatz/exposed-crud/tree/main/sample/src/main/kotlin/com/dshatz/exposed_crud/models)

[Sample usage (test class)](https://github.com/dshatz/exposed-crud/blob/main/sample/src/test/kotlin/com/dshatz/exposed_crud/TestDB.kt)

## Installation
```kotlin
plugins {
  id("com.google.devtools.ksp") version "2.1.20-1.0.31"
}

dependencies {
  ksp("com.dshatz.exposed-crud:processor:1.0.0")
  implementation("com.dshatz.exposed-crud:lib:1.0.0")
}
```

## Usage
### 1. Define your entity with `@Entity`
Making a property nullable (e.g. `originalTitle: String?`) will make it nullable in the database as well.

```kotlin
@Entity
data class Movie(
    val id: Long,
    val title: String,
    val createdAt: String,
    val originalTitle: String?,
    val directorId: Long,
    val categoryId: Long,
)
```

### 2. Specify the primary key with @Id
Set optional parameter `autoGenerate = true` to mark this column as auto-incrementing. Default `false`.

For composite primary keys, annotate multiple properties with `@Id`. Note that in that case auto-incrementing is not supported.
```kotlin
@Entity
data class Movie(
    @Id(autoGenerate = true) val id: Long,
    val title: String,
    val createdAt: String,
    val originalTitle: String?,
    val directorId: Long,
    val categoryId: Long,
)
```

### 3. Specify default values
To specify a default value, use `@DefaultText(text: String)` for Strings and `@Default(literal: String)` for numbers and booleans.

Additionally, specify the default values using Kotlin property initializer for convenience.

```kotlin
@Entity
data class Movie(
    @Id(autoGenerate = true) val id: Long,
    val title: String,
    @DefaultText("01-01-1970")
    val createdAt: String = "01-01-1970",
    val originalTitle: String?,
    val directorId: Long,
    val categoryId: Long,
)
```

### 4. Foreign keys

Annotate the foreign key columns with `@ForeignKey(<your other entity data class>::class)`. 

By default the foreign key will reference the primary key of the given entity. In case the other entity has a composite key, or you want to specify another target column, pass another parameter to `@ForeignKey` with the name of the target column.

```kotlin
@Entity
data class Movie(
    @Id(autoGenerate = true) val id: Long,
    val title: String,
    @DefaultText("01-01-1970")
    val createdAt: String = "01-01-1970",
    val originalTitle: String?,
    @ForeignKey(Director::class)
    val directorId: Long,
)
```

### 5. Relationships
Once the foreign key is defined, you can optionally bind the related entity to a local property with `@References`.

```kotlin
annotation class References(val related: KClass<*>, vararg val fkColumns: String)
```

Pass the related entity class and the names of the local columns annotated with `@ForeignKey`.

```kotlin
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
    val director: Director? = null,
)
```

## Working with the CRUD repository
The repository can be accessed using an extension property on the generated Exposed table DSL. 
For example, `MovieTable.repo`.



