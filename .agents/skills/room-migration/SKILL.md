---
name: room-migration
description: >-
  Procédures pour modifier les entités Room Database, générer et exporter les schémas JSON, écrire et tester les migrations de base de données dans CinéLog.
---

# Skill : Gestion des Migrations Room Database pour CinéLog

Ce guide décrit la procédure obligatoire pour toute modification du modèle de données local Room (`AppDatabase`) sans corrompre les données des utilisateurs.

## 📂 Emplacements Clés

- **Schémas JSON exportés** : `app/schemas/com.example.data.AppDatabase/`
- **Classe de base de données** : `app/src/main/java/com/example/data/AppDatabase.kt`
- **Entités** : `app/src/main/java/com/example/data/entity/` ou `app/src/main/java/com/example/data/model/`
- **Tests de migration** : `app/src/test/java/com/example/data/MigrationTest.kt`

---

## 📋 Étapes d'une Migration

### Étape 1 : Modifier l'entité Room
Ajouter ou modifier les colonnes dans la classe annotée `@Entity` :
```kotlin
@Entity(tableName = "diary_entries")
data class DiaryEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val rating: Float,
    // Nouvelle colonne :
    val notes: String? = null
)
```

### Étape 2 : Incrémenter la version de `AppDatabase`
Dans `AppDatabase.kt` :
```kotlin
@Database(
    entities = [DiaryEntryEntity::class, ...],
    version = CURRENT_VERSION + 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase()
```

### Étape 3 : Écrire l'objet `Migration`
Définir la requête SQL de mise à jour :
```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE diary_entries ADD COLUMN notes TEXT DEFAULT NULL")
    }
}
```
Ajouter la migration dans le builder de base de données :
```kotlin
Room.databaseBuilder(context, AppDatabase::class.java, "cinelog.db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

### Étape 4 : Exporter le nouveau schéma
Compiler le projet pour que le processeur KSP/KAPT génère le nouveau fichier JSON dans `app/schemas/` :
```bash
./gradlew kspDebugKotlin # ou ./gradlew compileDebugKotlin
```

### Étape 5 : Écrire et exécuter le test de migration
Dans `MigrationTest.kt`, utiliser `MigrationTestHelper` pour vérifier que la transition d'un schéma à l'autre préserve les enregistrements existants :
```bash
./gradlew testDebugUnitTest --tests "com.example.data.MigrationTest"
```
