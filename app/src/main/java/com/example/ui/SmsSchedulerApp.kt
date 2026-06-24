package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.data.Contact
import com.example.data.ScheduledSms
import com.example.data.SmsMessage
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsSchedulerApp(viewModel: SmsViewModel) {
    val context = LocalContext.current
    val scheduledSmsList by viewModel.scheduledSmsList.collectAsState()
    val messageHistory by viewModel.messageHistory.collectAsState()
    val contacts by viewModel.contacts.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var activeThreadPhone by remember { mutableStateOf<String?>(null) }
    var activeThreadName by remember { mutableStateOf<String?>(null) }

    // Dialog state for scheduling a new message
    var showScheduleDialog by remember { mutableStateOf(false) }
    var prefilledContactForSchedule by remember { mutableStateOf<Contact?>(null) }

    // Dialog state for contact detail
    var selectedContactDetail by remember { mutableStateOf<Contact?>(null) }

    // Simulated SMS panel visibility
    var showSimulatorPanel by remember { mutableStateOf(false) }

    // Permission tracking
    val permissionsToRequest = remember {
        mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] == true
        val contactsGranted = permissions[Manifest.permission.READ_CONTACTS] == true
        viewModel.setPermissionStatus(smsGranted)
        Toast.makeText(
            context,
            if (smsGranted) "Permissions updated successfully!" else "SMS Send permission is highly recommended for full features.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Check permission status on start
    LaunchedEffect(Unit) {
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.setPermissionStatus(allGranted)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SMS Scheduler",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Send, Receive & Schedule",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Simulation toggle button
                    IconButton(
                        onClick = { showSimulatorPanel = !showSimulatorPanel },
                        modifier = Modifier.testTag("simulator_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Developer Simulator Lab",
                            tint = if (showSimulatorPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            if (activeThreadPhone == null) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Chat, contentDescription = "Conversations") },
                        label = { Text("Chats") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.Default.Schedule, contentDescription = "Schedules") },
                        label = { Text("Scheduled") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.People, contentDescription = "Contacts") },
                        label = { Text("Contacts") }
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeThreadPhone == null && selectedTab == 1) {
                ExtendedFloatingActionButton(
                    text = { Text("Schedule SMS") },
                    icon = { Icon(Icons.Default.AddAlarm, contentDescription = "Schedule SMS") },
                    onClick = {
                        prefilledContactForSchedule = null
                        showScheduleDialog = true
                    },
                    modifier = Modifier.testTag("schedule_sms_fab")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Permission Alert Banner if missing
            val hasSendSmsPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasSendSmsPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Permissions Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Grant SMS permissions to send, receive and schedule messages securely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { permissionLauncher.launch(permissionsToRequest) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("grant_permissions_button")
                        ) {
                            Text("Grant", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Interactive simulation overlay panel
            AnimatedVisibility(
                visible = showSimulatorPanel,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SimulatorLabPanel(
                    contacts = contacts,
                    onSimulateMessage = { sender, text ->
                        val matchingContact = contacts.find { it.phoneNumber == sender || it.name.equals(sender, ignoreCase = true) }
                        val senderName = matchingContact?.name ?: sender
                        val senderPhone = matchingContact?.phoneNumber ?: sender
                        viewModel.simulateReceivedSms(senderPhone, senderName, text)
                        Toast.makeText(context, "Simulated SMS received!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Main Contents switching based on selectedTab and activeThread selection
            Box(modifier = Modifier.weight(1f)) {
                if (activeThreadPhone != null) {
                    ConversationThreadScreen(
                        phoneNumber = activeThreadPhone!!,
                        contactName = activeThreadName,
                        viewModel = viewModel,
                        onBack = {
                            activeThreadPhone = null
                            activeThreadName = null
                        }
                    )
                } else {
                    when (selectedTab) {
                        0 -> ConversationsTab(
                            messageHistory = messageHistory,
                            contacts = contacts,
                            onThreadSelect = { phone, name ->
                                activeThreadPhone = phone
                                activeThreadName = name
                            },
                            onClearHistory = {
                                viewModel.clearMessageHistory()
                            }
                        )
                        1 -> ScheduledTab(
                            scheduledSmsList = scheduledSmsList,
                            onCancel = { id -> viewModel.cancelScheduledSms(id) },
                            onDelete = { id -> viewModel.deleteScheduledSms(id) }
                        )
                        2 -> ContactsTab(
                            contacts = contacts,
                            onContactSelect = { contact ->
                                selectedContactDetail = contact
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal dialogs
    if (showScheduleDialog) {
        ScheduleSmsDialog(
            contacts = contacts,
            prefilledContact = prefilledContactForSchedule,
            onDismiss = { showScheduleDialog = false },
            onSchedule = { phone, name, msg, timeMs ->
                viewModel.scheduleSms(phone, name, msg, timeMs)
                showScheduleDialog = false
                Toast.makeText(context, "Message scheduled successfully!", Toast.LENGTH_LONG).show()
                selectedTab = 1 // Switch to schedules tab so they can see it instantly!
            }
        )
    }

    if (selectedContactDetail != null) {
        ContactDetailDialog(
            contact = selectedContactDetail!!,
            onDismiss = { selectedContactDetail = null },
            onSendImmediately = { contact ->
                selectedContactDetail = null
                activeThreadPhone = contact.phoneNumber
                activeThreadName = contact.name
                selectedTab = 0
            },
            onScheduleForLater = { contact ->
                selectedContactDetail = null
                prefilledContactForSchedule = contact
                showScheduleDialog = true
            }
        )
    }
}

// ==========================================
// TAB 1: CONVERSATIONS / CHATS
// ==========================================
@Composable
fun ConversationsTab(
    messageHistory: List<SmsMessage>,
    contacts: List<Contact>,
    onThreadSelect: (String, String?) -> Unit,
    onClearHistory: () -> Unit
) {
    // Group messages by phone number
    val threads = remember(messageHistory) {
        messageHistory.groupBy { it.phoneNumber }.map { (phone, messages) ->
            val latestMessage = messages.maxByOrNull { it.timestamp }!!
            val contact = contacts.find { it.phoneNumber == phone }
            ThreadItemData(
                phoneNumber = phone,
                contactName = contact?.name ?: messages.firstOrNull { it.contactName != null }?.contactName,
                latestMessageText = latestMessage.messageText,
                latestTimestamp = latestMessage.timestamp,
                unreadCount = 0 // Simulating standard unread behavior
            )
        }.sortedByDescending { it.latestTimestamp }
    }

    if (threads.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = "No conversations",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No Chats Yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Use the Contacts tab to start a new chat, or use the Simulator Lab (top icon) to simulate an incoming SMS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Conversations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(threads) { thread ->
                    ThreadListItem(thread = thread, onClick = {
                        onThreadSelect(thread.phoneNumber, thread.contactName)
                    })
                }
            }
        }
    }
}

data class ThreadItemData(
    val phoneNumber: String,
    val contactName: String?,
    val latestMessageText: String,
    val latestTimestamp: Long,
    val unreadCount: Int
)

@Composable
fun ThreadListItem(thread: ThreadItemData, onClick: () -> Unit) {
    val dateString = remember(thread.latestTimestamp) {
        val formatter = SimpleDateFormat("hh:mm a", Locale.getDefault())
        formatter.format(Date(thread.latestTimestamp))
    }

    val initials = remember(thread.contactName, thread.phoneNumber) {
        val name = thread.contactName ?: "Unknown"
        name.split(" ")
            .filter { it.isNotEmpty() }
            .map { it.first().uppercase() }
            .take(2)
            .joinToString("")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Initials Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.ifEmpty { "#" },
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = thread.contactName ?: thread.phoneNumber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = thread.latestMessageText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ==========================================
// SINGLE CONVERSATION / CHAT WINDOW
// ==========================================
@Composable
fun ConversationThreadScreen(
    phoneNumber: String,
    contactName: String?,
    viewModel: SmsViewModel,
    onBack: () -> Unit
) {
    val messagesFlow = remember(phoneNumber) { viewModel.repository.getMessagesForContact(phoneNumber) }
    val messages by messagesFlow.collectAsState(initial = emptyList())
    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Scroll to bottom when a new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Custom thread header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contactName ?: phoneNumber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                if (contactName != null) {
                    Text(
                        text = phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Simulate trigger from this contact
            IconButton(
                onClick = {
                    viewModel.simulateReceivedSms(
                        phoneNumber,
                        contactName,
                        "Hey, received your message! Let's meet."
                    )
                },
                modifier = Modifier.testTag("quick_simulate_reply_button")
            ) {
                Icon(Icons.Default.Quickreply, contentDescription = "Simulate Reply", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // Chat Bubble list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message)
            }
        }

        // Input bottom bar
        Surface(
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Text message") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field"),
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    ),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            val msgToSend = textInput.trim()
                            textInput = ""
                            viewModel.sendSmsImmediately(
                                phoneNumber = phoneNumber,
                                contactName = contactName,
                                messageText = msgToSend
                            ) { success, error ->
                                if (!success) {
                                    Toast.makeText(context, "Failed: $error", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("send_sms_button"),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: SmsMessage) {
    val formatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = formatter.format(Date(message.timestamp))

    val bubbleColor = if (message.isIncoming) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }

    val textColor = if (message.isIncoming) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    val alignment = if (message.isIncoming) Alignment.Start else Alignment.End

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isIncoming) 4.dp else 16.dp,
                bottomEnd = if (message.isIncoming) 16.dp else 4.dp
            )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.messageText,
                    color = textColor,
                    fontSize = 15.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

// ==========================================
// TAB 2: SCHEDULED MESSAGES
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduledTab(
    scheduledSmsList: List<ScheduledSms>,
    onCancel: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    if (scheduledSmsList.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddAlarm,
                    contentDescription = "No schedules",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No Scheduled SMS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap the 'Schedule SMS' button below to compose a message, choose a recipient, and set the perfect future delivery date and time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Scheduled Messages Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(scheduledSmsList) { sms ->
                    ScheduledSmsCard(
                        sms = sms,
                        onCancel = { onCancel(sms.id) },
                        onDelete = { onDelete(sms.id) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScheduledSmsCard(
    sms: ScheduledSms,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("EEE, MMM dd, yyyy hh:mm a", Locale.getDefault()) }
    val scheduledTimeStr = formatter.format(Date(sms.scheduledTime))

    val statusColor = when (sms.status) {
        "PENDING" -> MaterialTheme.colorScheme.primaryContainer
        "SENT" -> Color(0xFFD4EDDA) // soft green
        "FAILED" -> Color(0xFFF8D7DA) // soft red
        else -> MaterialTheme.colorScheme.surfaceVariant // grey/cancelled
    }

    val statusTextColor = when (sms.status) {
        "PENDING" -> MaterialTheme.colorScheme.onPrimaryContainer
        "SENT" -> Color(0xFF155724)
        "FAILED" -> Color(0xFF721C24)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = sms.contactName.ifEmpty { sms.phoneNumber },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (sms.contactName.isNotEmpty()) {
                        Text(
                            text = sms.phoneNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status Badge
                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("status_badge_${sms.id}")
                ) {
                    Text(
                        text = sms.status,
                        color = statusTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // SMS body
            Text(
                text = sms.message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom actions and time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "Time icon",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = scheduledTimeStr,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row {
                    if (sms.status == "PENDING") {
                        TextButton(
                            onClick = onCancel,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("cancel_sms_${sms.id}")
                        ) {
                            Icon(Icons.Default.CancelScheduleSend, contentDescription = "Cancel Schedule", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", fontSize = 12.sp)
                        }
                    }

                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("delete_sms_${sms.id}")
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Schedule", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontSize = 12.sp)
                    }
                }
            }

            if (sms.status == "FAILED" && !sms.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reason: ${sms.errorMessage}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ==========================================
// TAB 3: CONTACTS
// ==========================================
@Composable
fun ContactsTab(
    contacts: List<Contact>,
    onContactSelect: (Contact) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) {
            contacts
        } else {
            contacts.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.phoneNumber.contains(searchQuery)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search local contacts...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("contacts_search_input"),
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        if (filteredContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "No results",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No contacts found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredContacts) { contact ->
                    ContactListItem(contact = contact, onClick = { onContactSelect(contact) })
                }
            }
        }
    }
}

@Composable
fun ContactListItem(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Initials Avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (contact.isSimulated) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.secondaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.initials,
                color = if (contact.isSimulated) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = contact.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                if (contact.isSimulated) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Lab",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = contact.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==========================================
// MODAL DIALOG: SCHEDULE NEW SMS
// ==========================================
@Composable
fun ScheduleSmsDialog(
    contacts: List<Contact>,
    prefilledContact: Contact?,
    onDismiss: () -> Unit,
    onSchedule: (String, String, String, Long) -> Unit
) {
    var recipientPhone by remember { mutableStateOf(prefilledContact?.phoneNumber ?: "") }
    var recipientName by remember { mutableStateOf(prefilledContact?.name ?: "") }
    var messageText by remember { mutableStateOf("") }

    // Date/Time state
    val calendar = remember { Calendar.getInstance().apply { add(Calendar.MINUTE, 10) } } // default is 10 mins from now
    var year by remember { mutableStateOf(calendar.get(Calendar.YEAR)) }
    var month by remember { mutableStateOf(calendar.get(Calendar.MONTH)) }
    var day by remember { mutableStateOf(calendar.get(Calendar.DAY_OF_MONTH)) }
    var hour by remember { mutableStateOf(calendar.get(Calendar.HOUR_OF_DAY)) }
    var minute by remember { mutableStateOf(calendar.get(Calendar.MINUTE)) }

    // Recipient Selector Dropdown
    var showContactDropdown by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Schedule New SMS",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Recipient Row
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = if (recipientName.isNotEmpty()) "$recipientName ($recipientPhone)" else recipientPhone,
                        onValueChange = {
                            recipientPhone = it
                            recipientName = "" // clear name if manually editing phone number
                        },
                        label = { Text("To (Recipient)") },
                        trailingIcon = {
                            IconButton(onClick = { showContactDropdown = true }) {
                                Icon(Icons.Default.ContactPage, contentDescription = "Select Contact")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("schedule_recipient_field"),
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = showContactDropdown,
                        onDismissRequest = { showContactDropdown = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("— Close Select —", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                            onClick = { showContactDropdown = false }
                        )
                        contacts.forEach { contact ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(contact.name, fontWeight = FontWeight.Medium)
                                        Text(contact.phoneNumber, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    recipientPhone = contact.phoneNumber
                                    recipientName = contact.name
                                    showContactDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Message Text
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Message body") },
                    placeholder = { Text("Write your SMS content here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("schedule_body_field"),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Delivery Target Time header
                Text(
                    text = "Delivery Date & Time",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Date selectors (Quick selection for simplicity & high custom layout quality)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dateOptions = listOf("Today", "Tomorrow", "In 2 Days")
                    var selectedDateOption by remember { mutableStateOf(0) }

                    dateOptions.forEachIndexed { idx, option ->
                        val isSel = selectedDateOption == idx
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                selectedDateOption = idx
                                val tempCal = Calendar.getInstance()
                                if (idx == 1) tempCal.add(Calendar.DAY_OF_YEAR, 1)
                                if (idx == 2) tempCal.add(Calendar.DAY_OF_YEAR, 2)
                                year = tempCal.get(Calendar.YEAR)
                                month = tempCal.get(Calendar.MONTH)
                                day = tempCal.get(Calendar.DAY_OF_MONTH)
                            },
                            label = { Text(option) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Hours and Minutes selectors (Using premium custom drop-downs or arrows)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Hour selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hour (0-23)", style = MaterialTheme.typography.bodySmall)
                        var expandedHour by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { expandedHour = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(String.format("%02d", hour))
                        }
                        DropdownMenu(expanded = expandedHour, onDismissRequest = { expandedHour = false }) {
                            (0..23).forEach { h ->
                                DropdownMenuItem(
                                    text = { Text(String.format("%02d", h)) },
                                    onClick = {
                                        hour = h
                                        expandedHour = false
                                    }
                                )
                            }
                        }
                    }

                    // Minute selector
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Minute (0-59)", style = MaterialTheme.typography.bodySmall)
                        var expandedMin by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = { expandedMin = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(String.format("%02d", minute))
                        }
                        DropdownMenu(expanded = expandedMin, onDismissRequest = { expandedMin = false }) {
                            listOf(0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55).forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(String.format("%02d", m)) },
                                    onClick = {
                                        minute = m
                                        expandedMin = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (recipientPhone.isBlank()) {
                                return@Button
                            }
                            if (messageText.isBlank()) {
                                return@Button
                            }

                            val scheduleCalendar = Calendar.getInstance().apply {
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            // If scheduled time is in the past, push it slightly in the future or warning
                            if (scheduleCalendar.timeInMillis <= System.currentTimeMillis()) {
                                scheduleCalendar.add(Calendar.MINUTE, 5) // set 5 mins from now
                            }

                            onSchedule(
                                recipientPhone.trim(),
                                recipientName.trim(),
                                messageText.trim(),
                                scheduleCalendar.timeInMillis
                            )
                        },
                        modifier = Modifier.testTag("dialog_schedule_action_button")
                    ) {
                        Text("Schedule")
                    }
                }
            }
        }
    }
}

// ==========================================
// MODAL DIALOG: CONTACT DETAILS ACTIONS
// ==========================================
@Composable
fun ContactDetailDialog(
    contact: Contact,
    onDismiss: () -> Unit,
    onSendImmediately: (Contact) -> Unit,
    onScheduleForLater: (Contact) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large circle badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.initials,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Button immediate reply
                Button(
                    onClick = { onSendImmediately(contact) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_detail_send_now"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ChatBubble, contentDescription = "Send now")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chat / Send Now")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Button schedule later
                OutlinedButton(
                    onClick = { onScheduleForLater(contact) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contact_detail_schedule_later"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AddAlarm, contentDescription = "Schedule later")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Schedule SMS")
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}

// ==========================================
// COMPONENT: DEVELOPER SIMULATION LAB PANEL
// ==========================================
@Composable
fun SimulatorLabPanel(
    contacts: List<Contact>,
    onSimulateMessage: (String, String) -> Unit
) {
    var simulatorSender by remember { mutableStateOf("") }
    var simulatorText by remember { mutableStateOf("") }
    var expandedDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Science,
                        contentDescription = "Sim lab",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lab: Simulate Received SMS",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "TESTING TOOL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sender Input with contacts helper dropdown
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = simulatorSender,
                    onValueChange = { simulatorSender = it },
                    label = { Text("Sender phone or name") },
                    trailingIcon = {
                        IconButton(onClick = { expandedDropdown = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose contact")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("simulator_sender_input"),
                    singleLine = true
                )

                DropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false }
                ) {
                    contacts.forEach { contact ->
                        DropdownMenuItem(
                            text = { Text("${contact.name} (${contact.phoneNumber})") },
                            onClick = {
                                simulatorSender = contact.phoneNumber
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body text
            OutlinedTextField(
                value = simulatorText,
                onValueChange = { simulatorText = it },
                label = { Text("Incoming SMS Text body") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("simulator_text_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (simulatorSender.isNotBlank() && simulatorText.isNotBlank()) {
                        onSimulateMessage(simulatorSender.trim(), simulatorText.trim())
                        simulatorText = "" // clear text, keep sender for convenience
                    }
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("simulator_receive_action_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Simulate SMS")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Deliver Simulated SMS")
            }
        }
    }
}
