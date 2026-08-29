package com.example.huntersystem.domain.model

data class Metadata(
  val lastSavedAt: String? = null,
  val lastEventId: String? = null,
  val revision: Long = 1L
)
