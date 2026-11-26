package com.yzp.videolearning

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LearningProgressRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val jobs = mutableListOf<Job>()
    private var clock = 1000L

    private data class Storage(val store: DataStore<Preferences>, val job: Job)

    private fun storage(file: File): Storage {
        val job = SupervisorJob()
        jobs += job
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        )
        return Storage(store, job)
    }

    private fun repository(storage: Storage) = LearningProgressRepository(storage.store) { clock }

    @After fun closeStores() = runBlocking {
        jobs.forEach { it.cancelAndJoin() }
    }

    @Test fun markOpenedKeepsExistingPositionAndCompletion() = runBlocking {
        val repo = repository(storage(File(folder.root, "open.preferences_pb")))
        repo.markOpened("video-a")
        repo.savePosition("video-a", 128_000L)
        repo.setCompleted("video-a", true)
        clock = 2000L
        repo.markOpened("video-a")
        assertEquals(LearningProgress(128_000L, true, 2000L), repo.progress.first()["video-a"])
    }

    @Test fun completeAndUndoPreservePositionTimestampAndOtherVideos() = runBlocking {
        val repo = repository(storage(File(folder.root, "completion.preferences_pb")))
        repo.markOpened("video-a")
        repo.savePosition("video-a", 12_000L)
        repo.markOpened("video-b")
        repo.savePosition("video-b", 90_000L)
        val before = repo.progress.first()
        clock = 9000L
        repo.setCompleted("video-a", true)
        assertEquals(before["video-a"]!!.copy(completed = true), repo.progress.first()["video-a"])
        repo.setCompleted("video-a", false)
        assertEquals(before, repo.progress.first())
    }

    @Test fun missingOrNegativePositionDoesNotOverwriteKnownPosition() = runBlocking {
        val repo = repository(storage(File(folder.root, "missing.preferences_pb")))
        repo.markOpened("video-a")
        repo.savePosition("video-a", 42_000L)
        val before = repo.progress.first()
        repo.savePosition("video-a", null)
        repo.savePosition("video-a", -1L)
        repo.savePosition("unseen-video", null)
        assertEquals(before, repo.progress.first())
        // An explicit seek to zero is valid, unlike missing telemetry.
        repo.savePosition("video-a", 0L)
        assertEquals(0L, repo.progress.first()["video-a"]!!.lastPositionMs)
    }

    @Test fun progressSurvivesStoreRecreationAndCatalogReordering() = runBlocking {
        val file = File(folder.root, "recreated.preferences_pb")
        val firstStore = storage(file)
        val firstRepo = repository(firstStore)
        firstRepo.markOpened("316505362")
        firstRepo.savePosition("316505362", 65_432L)
        firstRepo.setCompleted("316505362", true)
        clock = 5000L
        firstRepo.markOpened("320985510")
        firstRepo.savePosition("320985510", 7000L)
        val expected = firstRepo.progress.first()
        firstStore.job.cancelAndJoin()

        // A new DataStore reads the serialized data from disk, without reusing its cache.
        val secondRepo = repository(storage(file))
        val restored = secondRepo.progress.first()
        assertEquals(expected, restored)
        val reorderedVideoIds = listOf("320985510", "316505362", "316505362")
        val rows = reorderedVideoIds.map { restored[it] }
        assertEquals(7000L, rows[0]!!.lastPositionMs)
        assertEquals(LearningProgress(65_432L, true, 1000L), rows[1])
        assertEquals(rows[1], rows[2])
    }

    @Test fun clearPreferencesRemovesProgressForNewRepository() = runBlocking {
        val existing = storage(File(folder.root, "clear.preferences_pb"))
        val repo = repository(existing)
        repo.markOpened("video-a")
        repo.setCompleted("video-a", true)
        existing.store.edit { it.clear() }
        assertTrue(repository(existing).progress.first().isEmpty())
    }

    @Test fun invalidJsonOrBlankVideoIdDoesNotCreateBogusProgress() = runBlocking {
        val existing = storage(File(folder.root, "invalid.preferences_pb"))
        existing.store.edit { it[stringPreferencesKey("progress_json")] = "invalid-json" }
        val repo = repository(existing)
        assertTrue(repo.progress.first().isEmpty())
        repo.markOpened("")
        repo.setCompleted(" ", true)
        assertTrue(repo.progress.first().isEmpty())
        repo.markOpened("real-video")
        assertEquals(1000L, repo.progress.first()["real-video"]!!.lastOpenedAt)
        assertFalse(repo.progress.first()["real-video"]!!.completed)
    }
}
