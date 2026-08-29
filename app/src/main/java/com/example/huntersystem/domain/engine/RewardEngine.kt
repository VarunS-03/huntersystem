package com.example.huntersystem.domain.engine

data class RewardCalculationResult(
  val updatedGold: Int,
  val delta: Int
)

object RewardEngine {

  /**
   * Calculates the economic impact of completing a quest.
   * Ensures non-negative rewards and maintains the gold balance invariant (gold >= 0).
   */
  fun applyReward(currentGold: Int, rewardGold: Int): RewardCalculationResult {
    val validReward = rewardGold.coerceAtLeast(0)
    val newGold = currentGold + validReward
    return RewardCalculationResult(
      updatedGold = newGold,
      delta = validReward
    )
  }

  /**
   * Calculates the economic impact of a quest penalty (e.g. on failure).
   * Ensures gold balance never drops below 0 (economy invariant: gold >= 0).
   */
  fun applyPenalty(currentGold: Int, penaltyGold: Int): RewardCalculationResult {
    val validPenalty = penaltyGold.coerceAtLeast(0)
    val newGold = (currentGold - validPenalty).coerceAtLeast(0)
    val actualDeduction = currentGold - newGold
    return RewardCalculationResult(
      updatedGold = newGold,
      delta = -actualDeduction
    )
  }

  /**
   * Calculates direct manual or external gold grant.
   */
  fun applyDirectGain(currentGold: Int, amount: Int): RewardCalculationResult {
    val validAmount = amount.coerceAtLeast(0)
    val newGold = currentGold + validAmount
    return RewardCalculationResult(
      updatedGold = newGold,
      delta = validAmount
    )
  }
}
