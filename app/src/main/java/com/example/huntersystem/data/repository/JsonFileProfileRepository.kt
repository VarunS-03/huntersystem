package com.example.huntersystem.data.repository

import com.example.huntersystem.domain.repository.ProfileRepository
import com.example.huntersystem.state.AppState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class JsonFileProfileRepository(
  private val storageDirectory: File,
  private val fileName: String = "hunter_system_profile_v1.json"
) : ProfileRepository {

  private val moshi: Moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val adapter = moshi.adapter(AppState::class.java).indent("  ")

  private val targetFile: File
    get() = File(storageDirectory, fileName)

  private val backupFile: File
    get() = File(storageDirectory, "$fileName.bak")

  override suspend fun loadState(): Result<AppState> = withContext(Dispatchers.IO) {
    try {
      if (!targetFile.exists()) {
        val defaultState = AppState.createDefault()
        saveStateInternal(defaultState)
        return@withContext Result.success(defaultState)
      }

      val content = targetFile.readText(Charsets.UTF_8)
      val parsedState = adapter.fromJson(content)
      if (parsedState != null) {
        Result.success(parsedState)
      } else {
        recoverFromBackup()
      }
    } catch (e: Exception) {
      recoverFromBackup()
    }
  }

  override suspend fun saveState(state: AppState): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      saveStateInternal(state)
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun clearState(): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      if (targetFile.exists()) targetFile.delete()
      if (backupFile.exists()) backupFile.delete()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun exportState(): Result<String> = withContext(Dispatchers.IO) {
    try {
      val state = loadState().getOrThrow()
      Result.success(adapter.toJson(state))
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  override suspend fun importState(serialized: String): Result<AppState> = withContext(Dispatchers.IO) {
    try {
      val state = adapter.fromJson(serialized)
        ?: return@withContext Result.failure(IllegalArgumentException("Invalid JSON format for AppState"))
      saveStateInternal(state)
      Result.success(state)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun saveStateInternal(state: AppState) {
    if (!storageDirectory.exists()) {
      storageDirectory.mkdirs()
    }

    val jsonString = adapter.toJson(state)

    // Write to atomic temporary file first
    val tempFile = File(storageDirectory, "$fileName.tmp")
    tempFile.writeText(jsonString, Charsets.UTF_8)

    // Backup current file if exists
    if (targetFile.exists()) {
      try {
        targetFile.copyTo(backupFile, overwrite = true)
      } catch (_: IOException) {}
    }

    // Atomic move temp to target
    if (!tempFile.renameTo(targetFile)) {
      // Fallback copy if renameTo fails
      tempFile.copyTo(targetFile, overwrite = true)
      tempFile.delete()
    }
  }

  private fun recoverFromBackup(): Result<AppState> {
    return try {
      if (backupFile.exists()) {
        val backupContent = backupFile.readText(Charsets.UTF_8)
        val backupState = adapter.fromJson(backupContent)
        if (backupState != null) {
          saveStateInternal(backupState)
          return Result.success(backupState)
        }
      }
      val fallbackDefault = AppState.createDefault()
      saveStateInternal(fallbackDefault)
      Result.success(fallbackDefault)
    } catch (e: Exception) {
      val emergencyState = AppState.createDefault()
      Result.success(emergencyState)
    }
  }
}
