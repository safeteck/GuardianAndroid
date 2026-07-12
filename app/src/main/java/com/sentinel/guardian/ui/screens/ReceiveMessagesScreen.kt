package com.sentinel.guardian.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sentinel.guardian.R
import com.sentinel.guardian.features.FeaturesServices
import com.sentinel.guardian.features.MessageRepository
import com.sentinel.guardian.features.StoredMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
data class Message(
    val senderName: String,
    val text: String,
    val timestamp: Long,
    @Transient val avatar: ImageVector = Icons.Default.Person // Default value, will be reassigned
)

// UPDATED ViewModel: Constructor now only takes Application
class MessagesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MessageRepository

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isReceiverOn = MutableStateFlow(false)
    val isReceiverOn: StateFlow<Boolean> = _isReceiverOn


    init {
        loadMessages()
    }

    fun loadMessages() {
        viewModelScope.launch {
            // UPDATED: Use getApplication() to get the safe application context
            val context = getApplication<Application>()
            _messages.value = repository.loadMessages(context)
                .map { storedMessage ->
                    Message(
                        senderName = storedMessage.sender,
                        text = storedMessage.body,
                        timestamp = storedMessage.timestamp
                    )
                }
        }
    }

    private fun saveMessages() {
        viewModelScope.launch {
            // UPDATED: Use getApplication() to get the safe application context
            val context = getApplication<Application>()
            repository.saveMessages(
                context,
                _messages.value.map { message ->
                    StoredMessage(
                        sender = message.senderName,
                        body = message.text,
                        timestamp = message.timestamp
                    )
                })
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            // UPDATED: Use getApplication() to get the safe application context
            val context = getApplication<Application>()
            repository.clearMessages(context)
            _messages.value = emptyList()
        }
    }

    fun toggleReceiver() {
        _isReceiverOn.value = !_isReceiverOn.value
    }

    // Function to add new messages, which now automatically saves them.
    fun addNewMessage(message: Message) {
        // Only add new messages if the receiver is on.
        if (_isReceiverOn.value) {
            // Add new message and ensure the list is sorted by time
            _messages.value = (_messages.value + message).sortedBy { it.timestamp }
            saveMessages() // Automatically save changes
        }
    }
}

// --- Custom Colors ---
val ScreenBackground = Color.White
val MessageBubbleColor = Color(0xFFEFF3F7)
val SenderNameColor = Color(0xFF6A7E8D)
val AvatarBackgroundColor = Color(0xFFFBEAE4)
val DarkTextColor = Color(0xFF1E1E1E)
val ActionRed = Color(0xFFED6A6A)

// --- Main Screen Composable ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(viewModel: MessagesViewModel = viewModel()) {
    val messages by viewModel.messages.collectAsState()
    val isReceiverOn by viewModel.isReceiverOn.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(id = R.string.messages_title),
                        fontWeight = FontWeight.Bold,
                        color = DarkTextColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: Handle back */ }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back_button_cd),
                            tint = DarkTextColor
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            viewModel.toggleReceiver()
                            if (!isReceiverOn) {
                                FeaturesServices.enableReceiveSmsReceiverManifestService(context)
                            } else {
                                FeaturesServices.disableReceiveSmsReceiverManifestService(context)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isReceiverOn) AvatarBackgroundColor else MessageBubbleColor,
                            contentColor = DarkTextColor
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val buttonTextRes =
                            if (isReceiverOn) R.string.turn_off_receiver else R.string.turn_on_message_receiver
                        Text(
                            text = stringResource(id = buttonTextRes),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ScreenBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(id = R.string.no_messages),
                        fontSize = 18.sp,
                        color = SenderNameColor
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    reverseLayout = true // Show latest messages at the bottom
                ) {
                    val groupedMessages = messages.groupBy { formatDate(it.timestamp, context) }

                    groupedMessages.forEach { (date, messagesInGroup) ->
                        // The items are reversed, so we add the header after the messages for that date
                        items(
                            items = messagesInGroup.reversed(), // display messages in correct order
                            key = { it.timestamp } // Use a unique key
                        ) { message ->
                            MessageItem(message = message)
                        }
                        item(key = date) { // Use a unique key for the header
                            DateHeader(date = date)
                        }
                    }
                }
            }
            Divider(color = MessageBubbleColor, thickness = 1.dp)
            ActionButtons(
                onLoad = { viewModel.loadMessages() },
                onClear = { viewModel.clearMessages() }
            )
        }
    }
}

// Helper function to format timestamp into a readable date string like "Today"
private fun formatDate(timestamp: Long, context: Context): String {
    val messageCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = Calendar.getInstance()

    if (messageCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
        messageCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    ) {
        return context.getString(R.string.date_today)
    }

    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (messageCal.get(Calendar.YEAR) == yesterdayCal.get(Calendar.YEAR) &&
        messageCal.get(Calendar.DAY_OF_YEAR) == yesterdayCal.get(Calendar.DAY_OF_YEAR)
    ) {
        return context.getString(R.string.date_yesterday)
    }

    return SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
}

// --- Reusable UI Components ---

@Composable
fun DateHeader(date: String) {
    Text(
        text = date,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = DarkTextColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        textAlign = TextAlign.Center
    )
}

@Composable
fun MessageItem(message: Message) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.clickable { /* TODO: Handle click */ }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AvatarBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = message.avatar,
                contentDescription = stringResource(id = R.string.avatar_cd, message.senderName),
                tint = DarkTextColor.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            )
        }
        Column {
            Text(
                text = message.senderName,
                color = SenderNameColor,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 20.dp,
                    bottomEnd = 20.dp,
                    bottomStart = 20.dp
                ),
                color = MessageBubbleColor
            ) {
                Text(
                    text = message.text,
                    color = DarkTextColor,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
fun ActionButtons(onLoad: () -> Unit, onClear: () -> Unit) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        ActionButton(
            text = stringResource(id = R.string.load_from_disk),
            icon = Icons.Outlined.FileUpload,
            onClick = onLoad,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onClear,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ActionRed,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(id = R.string.clear_all_cd)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.clear_all_messages),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MessageBubbleColor,
            contentColor = DarkTextColor
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp)
    ) {
        Icon(imageVector = icon, contentDescription = text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}


// --- Preview Composable ---

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
fun MessagesScreenPreview() {
    // We need a dummy ViewModel for the preview
    val application = LocalContext.current.applicationContext as Application
    // UPDATED: ViewModel instantiation now only requires the application context
    val viewModel = MessagesViewModel(application)
    // Manually set receiver to on for preview purposes
    viewModel.toggleReceiver()
    // Add sample data to the preview ViewModel
    viewModel.addNewMessage(
        Message(
            "Liam Carter",
            "I'm at the park.",
            System.currentTimeMillis(),
            Icons.Default.Person
        )
    )
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -1)
    viewModel.addNewMessage(
        Message(
            "Zoe",
            "See you tomorrow!",
            calendar.timeInMillis,
            Icons.Default.Person
        )
    )
    MessagesScreen(viewModel = viewModel)
}