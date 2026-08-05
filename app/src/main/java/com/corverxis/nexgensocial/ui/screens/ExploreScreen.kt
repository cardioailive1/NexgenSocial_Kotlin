package com.corverxis.nexgensocial.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corverxis.nexgensocial.data.*
import com.corverxis.nexgensocial.network.ApiClient
import com.corverxis.nexgensocial.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen() {
    var section by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<User>>(emptyList()) }
    var jobs by remember { mutableStateOf<List<JobPosting>>(emptyList()) }
    var listings by remember { mutableStateOf<List<MarketListing>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Debounced so typing doesn't fire a request per keystroke.
    LaunchedEffect(section, query) {
        delay(300)
        val suffix = if (query.isBlank()) "" else
            "?q=" + java.net.URLEncoder.encode(query, "UTF-8")
        runCatching {
            when (section) {
                0 -> users = ApiClient.get<UsersResponse>("/api/users$suffix").users
                1 -> jobs = ApiClient.get<JobsResponse>("/api/jobs$suffix").jobs
                else -> listings = ApiClient.get<ListingsResponse>("/api/marketplace$suffix").listings
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Explore") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )

            TabRow(selectedTabIndex = section) {
                listOf("People", "Jobs", "Market").forEachIndexed { index, label ->
                    Tab(selected = section == index, onClick = { section = index },
                        text = { Text(label) })
                }
            }

            LazyColumn(
                Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (section) {
                    0 -> items(users, key = { it.id }) { PersonRow(it, scope) }
                    1 -> items(jobs, key = { it.id }) { JobRow(it) }
                    else -> items(listings, key = { it.id }) { ListingRow(it) }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(user: User, scope: kotlinx.coroutines.CoroutineScope) {
    var following by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(user.avatarUrl, user.username, 44.dp)
            Column(Modifier.weight(1f)) {
                Text(user.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("@${user.username}", fontSize = 12.sp, color = Slate400)
                user.bio?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, color = Slate300, maxLines = 1)
                }
            }
            OutlinedButton(onClick = {
                scope.launch {
                    runCatching {
                        if (following) ApiClient.delete("/api/follows/${user.username}")
                        else ApiClient.post<EmptyResponse>("/api/follows/${user.username}")
                    }.onSuccess { following = !following }
                }
            }) { Text(if (following) "Following" else "Follow", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun JobRow(job: JobPosting) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(job.companyName, fontSize = 13.sp, color = Slate300)
            Text(
                listOfNotNull(job.location, job.arrangement?.lowercase()).joinToString(" · "),
                fontSize = 12.sp, color = Slate400,
            )
            job.salaryText?.let {
                Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Cyan300)
            } ?: Text("Salary not disclosed", fontSize = 11.sp, color = Slate400)
        }
    }
}

@Composable
private fun ListingRow(listing: MarketListing) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (listing.media.isNotEmpty()) MediaCarousel(listing.media)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(listing.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(listing.priceText, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, color = Cyan300)
            }
            Text(listing.description, fontSize = 12.sp, color = Slate400, maxLines = 2)
        }
    }
}
