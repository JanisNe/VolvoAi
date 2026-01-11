package com.example.volvoai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.example.volvoai.db.ScanHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


enum class AppScreen { HISTORY, SCAN, EMPTY1, EMPTY2 }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShell(
    scanContent: @Composable () -> Unit,
    historyContent: @Composable () -> Unit
) {
    var screen by remember { mutableStateOf(AppScreen.HISTORY) } // start = History
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))

                NavigationDrawerItem(
                    label = { Text("Scan") },
                    selected = screen == AppScreen.SCAN,
                    onClick = {
                        screen = AppScreen.SCAN
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("History") },
                    selected = screen == AppScreen.HISTORY,
                    onClick = {
                        screen = AppScreen.HISTORY
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Empty") },
                    selected = screen == AppScreen.EMPTY1,
                    onClick = {
                        screen = AppScreen.EMPTY1
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    label = { Text("Empty") },
                    selected = screen == AppScreen.EMPTY2,
                    onClick = {
                        screen = AppScreen.EMPTY2
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (screen) {
                                AppScreen.SCAN -> "Scan"
                                AppScreen.HISTORY -> "History"
                                else -> "Empty"
                            }
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (screen) {
                    AppScreen.SCAN -> scanContent()
                    AppScreen.HISTORY -> historyContent()
                    AppScreen.EMPTY1 -> Text("Empty", modifier = Modifier.padding(16.dp))
                    AppScreen.EMPTY2 -> Text("Empty", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryScreenPlaceholder() {
    Text("History (tukšs pagaidām)", modifier = Modifier.padding(16.dp))
}
@Composable
fun HistoryScreen(items: List<ScanHistoryEntity>) {
    var selected by remember { mutableStateOf<ScanHistoryEntity?>(null) }

    if (items.isEmpty()) {
        Text("History (tukšs pagaidām)", modifier = Modifier.padding(16.dp))
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        items(items) { it ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { selected = it },
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(it.partName, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(formatTime(it.scannedAtMillis), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    selected?.let { s ->
        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("OK") } },
            title = { Text("Detaļas info") },
            text = {
                Column {
                    Text("DB id: ${s.id}")
                    Text("Manufacturer part ID: ${s.manufacturerPartId}")
                    Text("Cena: ${s.priceEurText}")
                    Text("Kur pirkt: ${s.buyLink}")
                }
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(ms))
}
