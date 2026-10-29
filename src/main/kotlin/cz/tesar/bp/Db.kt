package cz.tesar.bp

import java.sql.Connection
import java.sql.DriverManager

object Db {
    private val url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/bakalarka"
    private val user = System.getenv("DB_USER") ?: "bp"
    private val password = System.getenv("DB_PASSWORD") ?: "bp"

    fun connect(): Connection = DriverManager.getConnection(url, user, password)
}

object DbCheck {
    private val requiredExtensions = listOf("pg_trgm", "fuzzystrmatch", "unaccent")

    fun run() {
        Db.connect().use { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT version()").use { rs ->
                    rs.next()
                    println("Database:   ${rs.getString(1).substringBefore(" on ")}")
                }

                val installed = mutableMapOf<String, String>()
                st.executeQuery("SELECT extname, extversion FROM pg_extension").use { rs ->
                    while (rs.next()) installed[rs.getString(1)] = rs.getString(2)
                }
                for (ext in requiredExtensions) {
                    val version = installed[ext]
                    println("Extension:  %-14s %s".format(ext, version?.let { "OK ($it)" } ?: "MISSING"))
                }

                st.executeQuery(
                    """
                    SELECT round(similarity('Wonderwall', 'Wonderwal')::numeric, 3),
                           levenshtein('Wonderwall', 'Wonderwal'),
                           immutable_unaccent('Příliš žluťoučký kůň')
                    """.trimIndent()
                ).use { rs ->
                    rs.next()
                    println("Test:       similarity=${rs.getBigDecimal(1)}, levenshtein=${rs.getInt(2)}, unaccent='${rs.getString(3)}'")
                }
            }
        }
        println("All set.")
    }
}
