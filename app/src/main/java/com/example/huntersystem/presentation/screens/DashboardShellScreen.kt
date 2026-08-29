package com.example.huntersystem.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.huntersystem.application.Command
import com.example.huntersystem.domain.model.Quest
import com.example.huntersystem.domain.model.QuestStatus
import com.example.huntersystem.domain.model.QuestTier
import com.example.huntersystem.presentation.theme.BackgroundDark
import com.example.huntersystem.presentation.theme.BorderDark
import com.example.huntersystem.presentation.theme.DangerRed
import com.example.huntersystem.presentation.theme.HunterGold
import com.example.huntersystem.presentation.theme.NeonBlue
import com.example.huntersystem.presentation.theme.NeonCyan
import com.example.huntersystem.presentation.theme.SuccessGreen
import com.example.huntersystem.presentation.theme.SurfaceDark
import com.example.huntersystem.presentation.theme.SurfaceVariantDark
import com.example.huntersystem.presentation.theme.TextMuted
import com.example.huntersystem.presentation.theme.TextPrimary
import com.example.huntersystem.presentation.theme.TextSecondary
import com.example.huntersystem.state.AppState
import com.example.huntersystem.state.Selectors

enum class QuestFilterTab(val label: String) {
  ALL("ALL"),
  ACTIVE("ACTIVE"),
  PENDING("PENDING"),
  COMPLETED("COMPLETED")
}

@Composable
fun DashboardShellScreen(
  state: AppState,
  onDispatch: (Command) -> Unit,
  modifier: Modifier = Modifier
) {
  val xpProgress = Selectors.xpProgressPercent(state.progression.xp, state.progression.level)
  val xpRequired = Selectors.xpRequiredForNextLevel(state.progression.level)
  val actionCue = Selectors.nextActionCue(state)

  var selectedTab by remember { mutableStateOf(QuestFilterTab.ACTIVE) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = BackgroundDark
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .padding(16.dp)
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // SYSTEM HEADER
      SystemHeaderCard()

      // PLAYER STATUS CARD (HUD)
      PlayerHudCard(
        state = state,
        xpProgress = xpProgress,
        xpRequired = xpRequired
      )

      // SYSTEM DIRECTIVE CARD
      SystemDirectiveCard(actionCue = actionCue)

      // QUEST BOARD (STEP 3 CORE SYSTEM)
      QuestCreationCard(onDispatch = onDispatch)

      QuestBoardCard(
        state = state,
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onDispatch = onDispatch
      )

      // ACHIEVEMENTS (STEP 7 SYSTEM)
      AchievementsCard(state = state)

      // ARCHITECTURE FOUNDATION DIAGNOSTICS
      FoundationDiagnosticsCard(state = state)

      // VERIFICATION CONTROLS
      FoundationActionsCard(onDispatch = onDispatch)
    }
  }
}

@Composable
private fun SystemHeaderCard() {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("system_header_card"),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "[ SYSTEM INTERFACE v1.0 ]",
          style = MaterialTheme.typography.labelSmall,
          color = NeonCyan
        )
        Surface(
          color = NeonCyan.copy(alpha = 0.15f),
          shape = RoundedCornerShape(4.dp)
        ) {
          Text(
            text = "INITIALIZED",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = NeonCyan
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "HUNTER SYSTEM",
        style = MaterialTheme.typography.headlineMedium,
        color = TextPrimary
      )
    }
  }
}

@Composable
private fun PlayerHudCard(
  state: AppState,
  xpProgress: Float,
  xpRequired: Int
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("player_hud_card"),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, BorderDark),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "HUNTER IDENTIFICATION",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
          )
          Text(
            text = state.profile.displayName,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
          )
        }

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Gold Balance Badge
          Box(
            modifier = Modifier
              .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
              .border(1.dp, HunterGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = "${Selectors.currentGold(state)} G",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.Monospace,
              color = HunterGold
            )
          }

          // Rank Badge
          Box(
            modifier = Modifier
              .border(2.dp, HunterGold, RoundedCornerShape(8.dp))
              .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Text(
              text = "${state.progression.rank}-RANK",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = HunterGold
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))
      HorizontalDivider(color = BorderDark)
      Spacer(modifier = Modifier.height(16.dp))

      // Level and XP progress
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "LEVEL ${state.progression.level}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = NeonCyan
        )
        Text(
          text = "${state.progression.xp} / $xpRequired XP",
          style = MaterialTheme.typography.bodyMedium,
          fontFamily = FontFamily.Monospace,
          color = TextSecondary
        )
      }

      Spacer(modifier = Modifier.height(8.dp))
      LinearProgressIndicator(
        progress = { xpProgress },
        modifier = Modifier
          .fillMaxWidth()
          .height(8.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = NeonCyan,
        trackColor = SurfaceVariantDark
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Stats row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Box(modifier = Modifier.weight(1f)) {
          StatBadge(label = "TODAY", value = "${state.daily.completedCount} DONE", color = NeonCyan)
        }
        Box(modifier = Modifier.weight(1f)) {
          StatBadge(label = "DAY GOLD", value = "+${Selectors.dailyEarnedGold(state)} G", color = HunterGold)
        }
        Box(modifier = Modifier.weight(1f)) {
          StatBadge(label = "STREAK", value = "${state.streak.current} DAYS", color = NeonBlue)
        }
        Box(modifier = Modifier.weight(1f)) {
          StatBadge(label = "BEST", value = "${state.streak.best} DAYS", color = HunterGold)
        }
      }
    }
  }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(SurfaceVariantDark, RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
      fontFamily = FontFamily.Monospace,
      color = color
    )
  }
}

@Composable
private fun SystemDirectiveCard(actionCue: String) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("system_directive_card"),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, NeonBlue.copy(alpha = 0.5f)),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "ACTIVE DIRECTIVE",
        style = MaterialTheme.typography.labelSmall,
        color = NeonBlue
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = actionCue,
        style = MaterialTheme.typography.bodyLarge,
        color = TextPrimary
      )
    }
  }
}

@Composable
private fun QuestCreationCard(onDispatch: (Command) -> Unit) {
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var selectedTier by remember { mutableStateOf(QuestTier.N) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("quest_creation_card"),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, BorderDark),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "GENERATE DIRECTIVE / QUEST",
          style = MaterialTheme.typography.labelSmall,
          color = NeonCyan
        )
        Text(
          text = "+${selectedTier.baseReward.xp} XP / +${selectedTier.baseReward.gold} G",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          color = HunterGold
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("create_quest_title_input"),
        placeholder = { Text("Enter quest objective...", color = TextMuted) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          focusedBorderColor = NeonCyan,
          unfocusedBorderColor = BorderDark,
          focusedContainerColor = SurfaceVariantDark,
          unfocusedContainerColor = SurfaceVariantDark
        ),
        shape = RoundedCornerShape(8.dp)
      )

      Spacer(modifier = Modifier.height(8.dp))

      OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("create_quest_desc_input"),
        placeholder = { Text("Quest parameters or notes (optional)...", color = TextMuted) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          focusedBorderColor = NeonBlue,
          unfocusedBorderColor = BorderDark,
          focusedContainerColor = SurfaceVariantDark,
          unfocusedContainerColor = SurfaceVariantDark
        ),
        shape = RoundedCornerShape(8.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Tier Selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        QuestTier.entries.forEach { tier ->
          val isSelected = selectedTier == tier
          val tierColor = when (tier) {
            QuestTier.E -> TextSecondary
            QuestTier.N -> NeonCyan
            QuestTier.H -> HunterGold
            QuestTier.S -> DangerRed
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSelected) tierColor.copy(alpha = 0.2f) else SurfaceVariantDark)
              .border(
                1.dp,
                if (isSelected) tierColor else BorderDark,
                RoundedCornerShape(6.dp)
              )
              .clickable { selectedTier = tier }
              .padding(vertical = 8.dp)
              .testTag("quest_tier_${tier.code}"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "${tier.code}-RANK",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) tierColor else TextMuted
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = {
          if (title.isNotBlank()) {
            onDispatch(
              Command.CreateQuest(
                title = title.trim(),
                description = description.trim(),
                tier = selectedTier,
                startImmediately = true
              )
            )
            title = ""
            description = ""
          }
        },
        enabled = title.isNotBlank(),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("add_quest_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = NeonCyan,
          contentColor = BackgroundDark,
          disabledContainerColor = SurfaceVariantDark,
          disabledContentColor = TextMuted
        ),
        shape = RoundedCornerShape(8.dp)
      ) {
        Icon(imageVector = Icons.Default.Add, contentDescription = "Add Directive", modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("ISSUE QUEST DIRECTIVE", fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
private fun QuestBoardCard(
  state: AppState,
  selectedTab: QuestFilterTab,
  onTabSelected: (QuestFilterTab) -> Unit,
  onDispatch: (Command) -> Unit
) {
  val allQuests = Selectors.allQuests(state)
  val displayedQuests = when (selectedTab) {
    QuestFilterTab.ALL -> allQuests
    QuestFilterTab.ACTIVE -> Selectors.activeQuests(state)
    QuestFilterTab.PENDING -> Selectors.pendingQuests(state)
    QuestFilterTab.COMPLETED -> Selectors.completedQuests(state)
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("quest_board_card"),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, BorderDark),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "QUEST OBJECTIVE BOARD",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = TextPrimary
        )
        Text(
          text = "${allQuests.size} TOTAL",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          color = TextSecondary
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Tab selector
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        QuestFilterTab.entries.forEach { tab ->
          val isSelected = selectedTab == tab
          val count = when (tab) {
            QuestFilterTab.ALL -> allQuests.size
            QuestFilterTab.ACTIVE -> Selectors.activeQuests(state).size
            QuestFilterTab.PENDING -> Selectors.pendingQuests(state).size
            QuestFilterTab.COMPLETED -> Selectors.completedQuests(state).size
          }

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(6.dp))
              .background(if (isSelected) NeonBlue.copy(alpha = 0.2f) else SurfaceVariantDark)
              .border(1.dp, if (isSelected) NeonBlue else BorderDark, RoundedCornerShape(6.dp))
              .clickable { onTabSelected(tab) }
              .padding(vertical = 6.dp)
              .testTag("quest_filter_${tab.name.lowercase()}"),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "${tab.label} ($count)",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
              color = if (isSelected) NeonCyan else TextMuted
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (displayedQuests.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("empty_quests_state"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "No quests matching '${selectedTab.label}' filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          displayedQuests.forEach { quest ->
            QuestItemCard(
              quest = quest,
              onDispatch = onDispatch
            )
          }
        }
      }
    }
  }
}

@Composable
private fun QuestItemCard(
  quest: Quest,
  onDispatch: (Command) -> Unit
) {
  val tierColor = when (quest.tier) {
    QuestTier.E -> TextSecondary
    QuestTier.N -> NeonCyan
    QuestTier.H -> HunterGold
    QuestTier.S -> DangerRed
  }

  val statusColor = when (quest.status) {
    QuestStatus.PENDING -> TextMuted
    QuestStatus.ACTIVE -> NeonCyan
    QuestStatus.COMPLETED -> SuccessGreen
    QuestStatus.FAILED -> DangerRed
    QuestStatus.CANCELLED -> TextSecondary
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("quest_card_${quest.id}"),
    colors = CardDefaults.cardColors(containerColor = SurfaceVariantDark),
    border = BorderStroke(1.dp, if (quest.status == QuestStatus.ACTIVE) NeonBlue.copy(alpha = 0.6f) else BorderDark),
    shape = RoundedCornerShape(8.dp)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // Tier badge
          Surface(
            color = tierColor.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, tierColor),
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = "${quest.tier.code}-RANK",
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = tierColor
            )
          }

          // Status badge
          Surface(
            color = statusColor.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp)
          ) {
            Text(
              text = quest.status.name,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              color = statusColor
            )
          }
        }

        // Reward Pill
        Text(
          text = "+${quest.reward.xp} XP / +${quest.reward.gold} G",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          color = HunterGold
        )
      }

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = quest.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )

      if (quest.description.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = quest.description,
          style = MaterialTheme.typography.bodySmall,
          color = TextSecondary
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Action Controls
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (quest.status == QuestStatus.PENDING) {
          Button(
            onClick = { onDispatch(Command.StartQuest(quest.id)) },
            modifier = Modifier
              .height(36.dp)
              .padding(end = 6.dp)
              .testTag("start_quest_${quest.id}"),
            colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = TextPrimary),
            shape = RoundedCornerShape(6.dp)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("START", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }

        if (!quest.status.isTerminal) {
          Button(
            onClick = { onDispatch(Command.CompleteQuest(quest.id)) },
            modifier = Modifier
              .height(36.dp)
              .padding(end = 6.dp)
              .testTag("complete_quest_${quest.id}"),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = BackgroundDark),
            shape = RoundedCornerShape(6.dp)
          ) {
            Icon(Icons.Default.Check, contentDescription = "Complete", modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("COMPLETE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = { onDispatch(Command.FailQuest(quest.id)) },
            modifier = Modifier
              .height(36.dp)
              .padding(end = 6.dp)
              .testTag("fail_quest_${quest.id}"),
            border = BorderStroke(1.dp, DangerRed),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text("FAIL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }

          OutlinedButton(
            onClick = { onDispatch(Command.CancelQuest(quest.id)) },
            modifier = Modifier
              .height(36.dp)
              .padding(end = 6.dp)
              .testTag("cancel_quest_${quest.id}"),
            border = BorderStroke(1.dp, TextMuted),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text("CANCEL", fontSize = 11.sp)
          }
        }

        IconButton(
          onClick = { onDispatch(Command.DeleteQuest(quest.id)) },
          modifier = Modifier
            .size(36.dp)
            .testTag("delete_quest_${quest.id}")
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete Quest",
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
private fun AchievementsCard(state: AppState) {
  val unlockedCount = Selectors.unlockedAchievementCount(state)
  val totalCount = Selectors.totalAchievementCount()
  val allAchievements = Selectors.allAchievements()

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("achievements_card"),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, BorderDark),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "HUNTER MILESTONES & ACHIEVEMENTS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
        }
        Text(
          text = "$unlockedCount / $totalCount UNLOCKED",
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          color = HunterGold
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        allAchievements.forEach { def ->
          val isUnlocked = Selectors.isAchievementUnlocked(state, def.id)
          val unlockTimestamp = Selectors.achievementUnlockTimestamp(state, def.id)

          val itemBorder = if (isUnlocked) HunterGold.copy(alpha = 0.5f) else BorderDark
          val itemBackground = if (isUnlocked) SurfaceVariantDark else SurfaceVariantDark.copy(alpha = 0.5f)

          Card(
            modifier = Modifier
              .fillMaxWidth()
              .testTag("achievement_item_${def.id}"),
            colors = CardDefaults.cardColors(containerColor = itemBackground),
            border = BorderStroke(1.dp, itemBorder),
            shape = RoundedCornerShape(8.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Text(
                    text = def.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) TextPrimary else TextMuted
                  )
                  Surface(
                    color = if (isUnlocked) HunterGold.copy(alpha = 0.15f) else SurfaceVariantDark,
                    border = BorderStroke(1.dp, if (isUnlocked) HunterGold else BorderDark),
                    shape = RoundedCornerShape(4.dp)
                  ) {
                    Text(
                      text = def.category.name,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      style = MaterialTheme.typography.labelSmall,
                      fontWeight = FontWeight.SemiBold,
                      color = if (isUnlocked) HunterGold else TextMuted
                    )
                  }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = def.description,
                  style = MaterialTheme.typography.bodySmall,
                  color = if (isUnlocked) TextSecondary else TextMuted
                )
              }

              Spacer(modifier = Modifier.width(8.dp))

              Surface(
                color = if (isUnlocked) SuccessGreen.copy(alpha = 0.15f) else SurfaceVariantDark,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (isUnlocked) SuccessGreen else BorderDark)
              ) {
                Text(
                  text = if (isUnlocked) "UNLOCKED" else "LOCKED",
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = if (isUnlocked) SuccessGreen else TextMuted
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FoundationDiagnosticsCard(state: AppState) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("foundation_diagnostics_card"),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, BorderDark),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "SYSTEM ARCHITECTURE STATE",
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted
      )
      Spacer(modifier = Modifier.height(8.dp))

      DiagnosticRow("Schema Version", "v${state.schemaVersion}")
      DiagnosticRow("Profile ID", state.profile.id)
      DiagnosticRow("Active Calendar Date", state.daily.activeDate)
      DiagnosticRow("State Revision", "#${state.metadata.revision}")
      DiagnosticRow("Total Quests Registered", "${state.quests.order.size}")
      DiagnosticRow("Active Quests", "${Selectors.activeQuests(state).size}")
      DiagnosticRow("Completed Quests", "${Selectors.completedQuests(state).size}")
      DiagnosticRow("Storage Boundary", "Internal JSON Local-First")
    }
  }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontFamily = FontFamily.Monospace,
      color = TextPrimary
    )
  }
}

@Composable
private fun FoundationActionsCard(onDispatch: (Command) -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("foundation_actions_card"),
    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
    border = BorderStroke(1.dp, BorderDark),
    shape = RoundedCornerShape(12.dp)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
        text = "FOUNDATION VERIFICATION CONTROLS",
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted
      )
      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Button(
          onClick = { onDispatch(Command.ResetProfile("Shadow Monarch")) },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("reset_profile_button"),
          colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = BackgroundDark),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("RESET STATE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        OutlinedButton(
          onClick = { onDispatch(Command.UpdateDisplayName("Hunter Sung")) },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("update_name_button"),
          border = BorderStroke(1.dp, NeonBlue),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text("UPDATE NAME", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
      }
    }
  }
}
