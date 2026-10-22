package com.ralledger.ui.planning

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ralledger.ui.receivables.ReceivablesScreen
import com.ralledger.ui.receivables.ReceivablesViewModel
import com.ralledger.ui.subscriptions.SubscriptionsScreen
import com.ralledger.ui.subscriptions.SubscriptionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningScreen(
    receivablesViewModel: ReceivablesViewModel,
    subscriptionsViewModel: SubscriptionsViewModel
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Receivables", "Subscriptions")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = "Financial Planning",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Manage uncollected dues & recurring subscriptions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> ReceivablesScreen(viewModel = receivablesViewModel)
            1 -> SubscriptionsScreen(viewModel = subscriptionsViewModel)
        }
    }
}
