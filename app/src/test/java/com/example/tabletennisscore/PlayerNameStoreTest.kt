package com.example.tabletennisscore

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNameStoreTest {

    private val prefs = InMemoryPreferences()
    private val store = PlayerNameStore(prefs)
    private val noDefaults = emptyList<String>()

    @Test
    fun emptyStoreWithoutDefaultsReturnsNothingAndSavesNothing() {
        assertEquals(emptyList<String>(), store.load(noDefaults))
        assertNull(prefs.getString(KEY, null))
    }

    @Test
    fun emptyStoreFallsBackToTheCurrentPlayersAndSavesThem() {
        assertEquals(listOf("Ann", "Bob"), store.load(listOf("bob", "ann")))
        assertEquals("""["Ann","Bob"]""", prefs.getString(KEY, null))
    }

    @Test
    fun savedNamesAreNotReplacedByDefaults() {
        store.replaceAll(listOf("Zed"))
        assertEquals(listOf("Zed"), store.load(listOf("Ann", "Bob")))
    }

    @Test
    fun replaceAllSortsDeduplicatesAndCleansNames() {
        store.replaceAll(listOf("  zed  ", "ann", "ANN", "", "bob   smith"))
        assertEquals(listOf("Ann", "Bob Smith", "Zed"), store.load(noDefaults))
    }

    @Test
    fun replaceAllKeepsTheFirstSpellingOfDuplicateNames() {
        store.replaceAll(listOf("ann", "Ann"))
        assertEquals(listOf("Ann"), store.load(noDefaults))
    }

    @Test
    fun addInsertsNameInSortedPosition() {
        store.replaceAll(listOf("Ann", "Cid"))
        store.add("bea", noDefaults)
        assertEquals(listOf("Ann", "Bea", "Cid"), store.load(noDefaults))
    }

    @Test
    fun addIgnoresNamesAlreadyPresentIgnoringCase() {
        store.replaceAll(listOf("Ann"))
        store.add("ANN", noDefaults)
        assertEquals(listOf("Ann"), store.load(noDefaults))
    }

    @Test
    fun addIgnoresBlankNames() {
        store.replaceAll(listOf("Ann"))
        store.add("   ", noDefaults)
        assertEquals(listOf("Ann"), store.load(noDefaults))
    }

    @Test
    fun addToAnEmptyStoreSavesDefaultsAlongWithTheNewName() {
        store.add("Cid", defaultNames = listOf("Ann", "Bob"))
        assertEquals(listOf("Ann", "Bob", "Cid"), store.load(noDefaults))
    }

    @Test
    fun corruptSavedDataGivesAnEmptyList() {
        prefs.edit().putString(KEY, "not json").apply()
        assertTrue(store.load(listOf("Ann")).isEmpty())
    }

    @Test
    fun longNamesAreCutToTheMaximumLength() {
        val longName = "a".repeat(GameViewModel.MAX_PLAYER_NAME_LENGTH + 10)
        store.replaceAll(listOf(longName))
        assertEquals(GameViewModel.MAX_PLAYER_NAME_LENGTH, store.load(noDefaults).single().length)
    }

    private companion object {
        const val KEY = "player_name_group_json"
    }
}

/** A minimal SharedPreferences that keeps its strings in memory, for JVM unit tests. */
internal class InMemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String?, defValue: String?): String? = values[key] as String? ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as Float? ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue
    override fun contains(key: String?): Boolean = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed.add(key) }
        override fun clear() = apply { clearAll = true }

        override fun commit(): Boolean {
            if (clearAll) values.clear()
            removed.forEach { values.remove(it) }
            values.putAll(pending)
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
