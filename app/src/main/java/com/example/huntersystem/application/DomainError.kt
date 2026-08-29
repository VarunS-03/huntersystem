package com.example.huntersystem.application

sealed interface DomainError {
  val message: String

  data class InvalidInput(override val message: String) : DomainError
  data class NotFound(override val message: String) : DomainError
  data class InvalidStateTransition(override val message: String) : DomainError
  data class PersistenceFailure(override val message: String) : DomainError
  data class UnknownError(override val message: String) : DomainError
}
