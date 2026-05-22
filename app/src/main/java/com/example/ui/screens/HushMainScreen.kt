package com.example.ui.screens

import android.widget.Toast
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.User
import com.example.data.model.StatusStory
import com.example.data.model.StatusReply
import com.example.data.model.ContactRelation
import com.example.data.repository.UserStatusGroup
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatChannel
import com.example.ui.viewmodel.HushViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Safe Hex Color Parser
fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color.LightGray
    }
}

// Convert timestamp to readable time
fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60 * 1000 -> "Just now"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
        else -> SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HushMainScreen(viewModel: HushViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // State bindings
    val currentUser by viewModel.currentUserState.collectAsState()
    val statusGroups by viewModel.statusGroupsState.collectAsState()
    val allUsers by viewModel.allUsersState.collectAsState()
    val contacts by viewModel.contactsState.collectAsState()
    val activeChatPartnerPhone by viewModel.activeChatPartner.collectAsState()
    val chatChannelMessages by viewModel.activeChatMessages.collectAsState()
    val chatChannels by viewModel.chatChannelsState.collectAsState()
    val allReplies by viewModel.allRepliesState.collectAsState()
    
    // Navigation tab
    var currentTab by remember { mutableStateOf("Feed") } // "Feed", "ChatList", "Directory"

    // Dialog & Detail states
    var selectedStatusGroupForViewer by remember { mutableStateOf<UserStatusGroup?>(null) }
    var showCreateStatusScreen by remember { mutableStateOf(false) }

    // Floating UI trigger feedback
    val postStatusState by viewModel.postStatusState.collectAsState()
    LaunchedEffect(postStatusState) {
        if (postStatusState is UiState.Success) {
            Toast.makeText(context, "Status shared", Toast.LENGTH_SHORT).show()
            showCreateStatusScreen = false
            viewModel.resetPostState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
    ) {
        // Main structural layout with proper edge-to-edge scaffolding
        Scaffold(
            bottomBar = {
                if (activeChatPartnerPhone == null && selectedStatusGroupForViewer == null && !showCreateStatusScreen) {
                    NavigationBar(
                        containerColor = SlateCard,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        NavigationBarItem(
                            selected = currentTab == "Feed",
                            onClick = { currentTab = "Feed" },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Feed") },
                            label = { Text("Feed", fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HushViolet,
                                selectedTextColor = HushViolet,
                                indicatorColor = SlateCardSecondary,
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText
                            ),
                            modifier = Modifier.testTag("nav_feed")
                        )
                        NavigationBarItem(
                            selected = currentTab == "ChatList",
                            onClick = { currentTab = "ChatList" },
                            icon = { 
                                BadgedBox(badge = {
                                    if (chatChannels.isNotEmpty()) {
                                        Badge(containerColor = HushViolet) { Text(chatChannels.size.toString()) }
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "Whispers")
                                }
                            },
                            label = { Text("Whispers", fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = HushViolet,
                                selectedTextColor = HushViolet,
                                indicatorColor = SlateCardSecondary,
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText
                            ),
                            modifier = Modifier.testTag("nav_chats")
                        )
                        NavigationBarItem(
                            selected = currentTab == "Directory",
                            onClick = { currentTab = "Directory" },
                            icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Local Contacts") },
                            label = { Text("Discover", fontWeight = FontWeight.SemiBold) },
                            colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = HushViolet,
                                        selectedTextColor = HushViolet,
                                        indicatorColor = SlateCardSecondary,
                                        unselectedIconColor = MutedText,
                                        unselectedTextColor = MutedText
                            ),
                            modifier = Modifier.testTag("nav_simulate")
                        )
                    }
                }
            },
            containerColor = MidnightBlack,
            floatingActionButton = {
                if (currentTab == "Feed" && activeChatPartnerPhone == null && selectedStatusGroupForViewer == null && !showCreateStatusScreen) {
                    FloatingActionButton(
                        onClick = { showCreateStatusScreen = true },
                        containerColor = HushViolet,
                        contentColor = Color.White,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("add_status_fab")
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add Status")
                    }
                }
            }
        ) { innerPadding ->
            // Active window switching setup
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    "Feed" -> {
                        val statusLifespan by viewModel.statusLifespanMs.collectAsState()
                        FeedScreen(
                            statusGroups = statusGroups,
                            currentUser = currentUser,
                            allReplies = allReplies,
                            allUsers = allUsers,
                            statusLifespan = statusLifespan,
                            onLifespanChange = { viewModel.updateLifespan(it) },
                            onOpenStories = { group -> selectedStatusGroupForViewer = group },
                            onCreateStatus = { showCreateStatusScreen = true },
                            onReplyToStory = { storyId, receiverPhone, message ->
                                viewModel.replyToStory(storyId, receiverPhone, message)
                            },
                            onDeleteStory = { storyId ->
                                viewModel.deleteStatusStory(storyId)
                            },
                            onLikeStory = { storyId ->
                                viewModel.markStoryAsViewed(storyId)
                            },
                            onUpdateProfile = { name, emoji, colorHex, bio ->
                                viewModel.updateUserProfile(name, emoji, colorHex, bio)
                            }
                        )
                    }
                    "ChatList" -> WhispersChatListScreen(
                        channels = chatChannels,
                        onOpenChat = { partnerPhone -> viewModel.setChatPartner(partnerPhone) }
                    )
                    "Directory" -> ContactDirectoryScreen(
                        allUsers = allUsers,
                        contacts = contacts,
                        currentUser = currentUser,
                        onAddContact = { p, nick -> viewModel.addLocalContact(p, nick) },
                        onRemoveContact = { p -> viewModel.removeLocalContact(p) },
                        onSyncContacts = { list -> viewModel.syncDeviceContacts(list) },
                        onAddOtherContactRelation = { owner, target, nick -> viewModel.addOtherContactRelation(owner, target, nick) },
                        onRemoveOtherContactRelation = { owner, target -> viewModel.removeOtherContactRelation(owner, target) }
                    )
                }
            }
        }

        // 1. FULLSCREEN STORY/STATUS PLAYER (Animated Slide-Up overlay inside Hush stack)
        AnimatedVisibility(
            visible = selectedStatusGroupForViewer != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            selectedStatusGroupForViewer?.let { group ->
                FullscreenStatusViewer(
                    statusGroup = group,
                    onClose = { selectedStatusGroupForViewer = null },
                    onReply = { storyId, replyMsg ->
                        viewModel.replyToStory(
                            storyId = storyId,
                            receiverPhone = group.user.phoneNumber,
                            message = replyMsg
                        )
                        Toast.makeText(context, "Whisper sent", Toast.LENGTH_SHORT).show()
                    },
                    onViewStory = { storyId -> viewModel.markStoryAsViewed(storyId) }
                )
            }
        }

        // 2. STATUS CREATOR SCREEN OVERLAY
        AnimatedVisibility(
            visible = showCreateStatusScreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            StatusCreatorScreen(
                onClose = { showCreateStatusScreen = false },
                onPost = { text, font, start, end, emoji ->
                    viewModel.postStatus(text, font, start, end, emoji)
                }
            )
        }

        // 3. PRIVATE SECURE CHAT OVERLAY (Insta DM whisper thread)
        AnimatedVisibility(
            visible = activeChatPartnerPhone != null,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            activeChatPartnerPhone?.let { partnerPhone ->
                val partnerUser = allUsers.find { it.phoneNumber == partnerPhone }
                if (partnerUser != null) {
                    DirectMessageChatScreen(
                        partner = partnerUser,
                        messages = chatChannelMessages,
                        onBack = { viewModel.setChatPartner(null) },
                        onSendMessage = { msg -> viewModel.sendDirectMessage(partnerPhone, msg) }
                    )
                }
            }
        }
    }
}

// ==================== FEED SCREEN ====================
data class InstagramPostItem(
    val user: User,
    val relationship: String,
    val intermediaryName: String,
    val story: StatusStory,
    val originalGroup: UserStatusGroup
)

@Composable
fun FeedScreen(
    statusGroups: List<UserStatusGroup>,
    currentUser: User?,
    allReplies: List<StatusReply>,
    allUsers: List<User>,
    statusLifespan: Long,
    onLifespanChange: (Long) -> Unit,
    onOpenStories: (UserStatusGroup) -> Unit,
    onCreateStatus: () -> Unit,
    onReplyToStory: (Int, String, String) -> Unit, // storyId, receiverPhone, message
    onDeleteStory: (Int) -> Unit,
    onLikeStory: (Int) -> Unit,
    onUpdateProfile: (String, String, String, String) -> Unit
) {
    val myGroup = statusGroups.find { it.relationship == "Me" }
    var showSettingsDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Flatten all active status stories into a list of Instagram posts, sorted newest-first!
    val instagramPosts = remember(statusGroups) {
        statusGroups.flatMap { group ->
            group.statuses.map { story ->
                InstagramPostItem(
                    user = group.user,
                    relationship = group.relationship,
                    intermediaryName = group.intermediaryName,
                    story = story,
                    originalGroup = group
                )
            }
        }.sortedByDescending { it.story.timestamp }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Custom Title Banner
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "hush",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = (-1).sp,
                                fontFamily = FontFamily.Serif
                            ),
                            color = Color.White
                        )
                        Text(
                            text = " .",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = HushViolet
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateCardSecondary)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Encrypted State",
                                tint = HushViolet,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CONTACT ONLY",
                                fontSize = 11.sp,
                                color = HushViolet,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SlateCardSecondary)
                                .testTag("settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Personal Profile & Stealth About Banner
        currentUser?.let { me ->
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSettingsDialog = true }
                        .testTag("my_profile_badge")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(me.avatarColorHex.toColor())
                                .border(1.5.dp, HushViolet, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = me.avatarEmoji, fontSize = 20.sp)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            // Name
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = me.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ME",
                                    color = HushViolet,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier
                                        .background(HushViolet.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(2.dp))
                            
                            // Bio
                            Text(
                                text = me.bio,
                                color = MutedText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Customize Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateCardSecondary)
                                .clickable { showSettingsDialog = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Profile",
                                tint = HushViolet,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Edit",
                                color = HushViolet,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }



        // Expiry control card has been moved into the Settings dialog to declutter the home feed page!

        // Section Title: Home Feed
        item {
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Render feed lists
        if (instagramPosts.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AllInbox,
                            contentDescription = "No stories",
                            tint = MutedText,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No silent statuses found.",
                            color = LightText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add contacts or post an update to see stories here.",
                            color = MutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else {
            items(instagramPosts, key = { "post_${it.story.id}" }) { post ->
                InstagramPostCard(
                    post = post,
                    allReplies = allReplies,
                    allUsers = allUsers,
                    statusLifespan = statusLifespan,
                    onOpenFullscreen = { onOpenStories(post.originalGroup) },
                    onReply = { inlineText -> onReplyToStory(post.story.id, post.user.phoneNumber, inlineText) },
                    onDelete = { onDeleteStory(post.story.id) },
                    onLike = { onLikeStory(post.story.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSettingsDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSettingsDialog = false }) {
            var editName by remember { mutableStateOf(currentUser?.name ?: "") }
            var editBio by remember { mutableStateOf(currentUser?.bio ?: "") }
            var editEmoji by remember { mutableStateOf(currentUser?.avatarEmoji ?: "🤐") }
            var editColorHex by remember { mutableStateOf(currentUser?.avatarColorHex ?: "#2A3B4C") }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SlateCard,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Settings & Profile",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            ),
                            color = Color.White
                        )
                        IconButton(
                            onClick = { showSettingsDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedText, modifier = Modifier.size(16.dp))
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // Profile Section
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(editColorHex.toColor())
                                    .border(2.dp, HushViolet.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(editEmoji, fontSize = 24.sp)
                            }

                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Display Name", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HushViolet,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedTextColor = LightText,
                                    unfocusedTextColor = LightText,
                                    focusedLabelColor = HushViolet,
                                    unfocusedLabelColor = MutedText
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("edit_profile_name")
                            )
                        }

                        OutlinedTextField(
                            value = editBio,
                            onValueChange = { editBio = it },
                            label = { Text("About / Whisper Bio", fontSize = 11.sp) },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HushViolet,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = LightText,
                                unfocusedTextColor = LightText,
                                focusedLabelColor = HushViolet,
                                unfocusedLabelColor = MutedText
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("edit_profile_bio")
                        )

                        Column {
                            Text("SELECT PROFILE AVATAR EMOJI", fontSize = 11.sp, color = MutedText, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            val emojis = listOf("🤐", "🤫", "🕵️", "🦁", "🦄", "🚀", "🛸", "👾", "🔮", "🎨")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(emojis) { currentEmoji ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(if (currentEmoji == editEmoji) HushViolet.copy(alpha = 0.25f) else SlateCardSecondary)
                                            .border(1.dp, if (currentEmoji == editEmoji) HushViolet else Color.Transparent, CircleShape)
                                            .clickable { editEmoji = currentEmoji },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(currentEmoji, fontSize = 18.sp)
                                    }
                                }
                            }
                        }

                        Column {
                            Text("SELECT AVATAR BG COLOR", fontSize = 11.sp, color = MutedText, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            val colorsList = listOf("#1E1E1E", "#10B981", "#3B82F6", "#8B5CF6", "#EC4899", "#F59E0B", "#EF4444", "#2A3B4C")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(colorsList) { hex ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(hex.toColor())
                                            .border(2.dp, if (hex == editColorHex) Color.White else Color.Transparent, CircleShape)
                                            .clickable { editColorHex = hex }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (editName.isNotBlank()) {
                                    onUpdateProfile(editName, editEmoji, editColorHex, editBio)
                                    Toast.makeText(context, "Hush Profile updated! 🤫", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Display name cannot be blank.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HushViolet, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("save_profile_btn")
                        ) {
                            Text("Save Profile Details", fontWeight = FontWeight.Bold)
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // Lifespan Section
                    Column {
                        Text(
                            text = "STATUS EXPIRY LIFESPAN",
                            fontSize = 11.sp,
                            color = HushViolet,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Choose how long your shared statuses last before disappearing.",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        val options = listOf(
                            Triple("10s", 10000L, "Demo"),
                            Triple("1 Min", 60000L, "Fast"),
                            Triple("1 Hour", 3600000L, "Mid"),
                            Triple("24 Hours", 86400000L, "Standard")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            options.forEach { (label, duration, subLabel) ->
                                val isSelected = statusLifespan == duration
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) HushViolet else SlateCardSecondary)
                                        .clickable { onLifespanChange(duration) }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) MidnightBlack else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = subLabel,
                                            color = if (isSelected) MidnightBlack.copy(alpha = 0.6f) else MutedText,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // Other Functions (Privacy & Network Mode)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "PRIVACY OPTIONS",
                            fontSize = 11.sp,
                            color = HushViolet,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Contact Isolation Mode", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Isolate status feed strictly to saved contacts.", color = MutedText, fontSize = 11.sp)
                            }
                            var mockToggle1 by remember { mutableStateOf(true) }
                            androidx.compose.material3.Switch(
                                checked = mockToggle1,
                                onCheckedChange = { mockToggle1 = it },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = MidnightBlack,
                                    checkedTrackColor = HushViolet,
                                    uncheckedThumbColor = MutedText,
                                    uncheckedTrackColor = SlateCardSecondary
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Stealth Views", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Prevent contacts from seeing when you view status.", color = MutedText, fontSize = 11.sp)
                            }
                            var mockToggle2 by remember { mutableStateOf(false) }
                            androidx.compose.material3.Switch(
                                checked = mockToggle2,
                                onCheckedChange = { mockToggle2 = it },
                                colors = androidx.compose.material3.SwitchDefaults.colors(
                                    checkedThumbColor = MidnightBlack,
                                    checkedTrackColor = HushViolet,
                                    uncheckedThumbColor = MutedText,
                                    uncheckedTrackColor = SlateCardSecondary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InstagramPostCard(
    post: InstagramPostItem,
    allReplies: List<StatusReply>,
    allUsers: List<User>,
    statusLifespan: Long,
    onOpenFullscreen: () -> Unit,
    onReply: (String) -> Unit,
    onDelete: () -> Unit,
    onLike: () -> Unit
) {
    var replyText by remember { mutableStateOf("") }
    val context = LocalContext.current
    var isLiked by remember { mutableStateOf(false) }

    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            post.story.startColorHex.toColor(),
            post.story.endColorHex.toColor()
        )
    )

    // Calculate real-time countdown timer
    val now = System.currentTimeMillis()
    val timeLeftMs = statusLifespan - (now - post.story.timestamp)
    val timeLeftText = remember(timeLeftMs, statusLifespan) {
        if (timeLeftMs <= 0) {
            "Expiring now..."
        } else if (timeLeftMs < 60 * 1000) {
            "${timeLeftMs / 1000}s left"
        } else if (timeLeftMs < 60 * 60 * 1000) {
            "${timeLeftMs / (60 * 1000)}m left"
        } else {
            val hrs = timeLeftMs / (60 * 60 * 1000)
            val mins = (timeLeftMs % (60 * 60 * 1000)) / (60 * 1000)
            "${hrs}h ${mins}m left"
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("instagram_post_${post.story.id}"),
        border = BorderStroke(
            1.dp,
            if (post.relationship == "Second-Degree Contact") HushViolet.copy(alpha = 0.25f)
            else HushViolet.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Colored Avatar ring (IG/WhatsApp style combined)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            val colorRing = if (post.relationship == "Second-Degree Contact") {
                                HushViolet
                            } else if (post.relationship == "Me") {
                                HushViolet
                            } else {
                                HushViolet
                            }
                            drawArc(
                                color = colorRing,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                        .padding(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(post.user.avatarColorHex.toColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = post.user.avatarEmoji, fontSize = 18.sp)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = post.user.name,
                            color = LightText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))

                        // Relationship Label Badge
                        val badgeColor = if (post.relationship == "Second-Degree Contact") HushViolet else HushViolet
                        val displayText = if (post.relationship == "Second-Degree Contact") {
                            "via ${post.intermediaryName}"
                        } else if (post.relationship == "Me") {
                            "Me"
                        } else {
                            "Direct"
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = badgeColor.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = displayText.uppercase(),
                                color = badgeColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatTimeAgo(post.story.timestamp),
                        color = MutedText,
                        fontSize = 11.sp
                    )
                }

                // Delete button for owner text posts
                if (post.relationship == "Me") {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(34.dp).testTag("delete_post_${post.story.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Story",
                            tint = Color.Red.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Large Styled Story Card Post (Instagram standard style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(gradientBrush)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background subtle sticker emoji
                if (post.story.emojiSticker.isNotBlank()) {
                    Text(
                        text = post.story.emojiSticker,
                        fontSize = 90.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 4.dp, end = 4.dp)
                            .alpha(0.18f)
                    )
                }

                // Floating real-time status countdown tag (Top-Left corner of post image)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Time Left",
                            tint = HushViolet,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = timeLeftText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Main post typographic text content
                val postFont = when (post.story.fontFamily) {
                    "Mono" -> FontFamily.Monospace
                    "Cursive" -> FontFamily.Cursive
                    "Sans" -> FontFamily.SansSerif
                    else -> FontFamily.Serif
                }
                
                Text(
                    text = post.story.content,
                    color = post.story.textColorHex.toColor(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    fontFamily = postFont,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action row & stats (Likes, DMs, Views counts)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        isLiked = !isLiked
                        onLike() // trigger simulated visit/view
                        if (isLiked) {
                            Toast.makeText(context, "Liked", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.size(36.dp).testTag("like_post_${post.story.id}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like Status",
                        tint = if (isLiked) Color.Red else LightText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // View count icon and label
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "Views",
                        tint = MutedText,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${post.story.views} views",
                        color = MutedText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Inline comments/replies listing (Only exists when feed/post is viewed)
            val postReplies = remember(allReplies, post.story.id) {
                allReplies.filter { it.originalStatusId == post.story.id }
            }

            if (postReplies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "REPLIES & COMMENTS",
                    fontSize = 11.sp,
                    color = HushViolet,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    postReplies.forEach { reply ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            val replySender = allUsers.find { it.phoneNumber == reply.senderPhone }
                            val emoji = replySender?.avatarEmoji ?: "💬"
                            val color = replySender?.avatarColorHex?.toColor() ?: SlateCardSecondary
                            val displayName = replySender?.name ?: reply.senderPhone

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            ) {
                                Text(text = emoji, fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = displayName,
                                        color = LightText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = formatTimeAgo(reply.timestamp),
                                        color = MutedText,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = reply.replyMessage,
                                    color = LightText.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Quick DM/Whisper input row (Instagram feed story interaction style!)
            if (post.relationship != "Me") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateCardSecondary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    TextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = {
                            Text(
                                "Quick reply to their hush...",
                                color = MutedText,
                                fontSize = 12.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = HushViolet
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inline_reply_input_${post.story.id}"),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onReply(replyText)
                                replyText = ""
                            }
                        },
                        modifier = Modifier.size(32.dp).testTag("inline_reply_send_${post.story.id}")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Reply",
                            tint = HushViolet,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}


// ==================== DISCOVERY GRAPH SCREEN (EXCELLENT UX) ====================
@Composable
fun DiscoveryGraphScreen(
    statusGroups: List<UserStatusGroup>,
    currentUser: User?,
    allUsers: List<User>,
    contacts: List<ContactRelation>
) {
    val myPhone = currentUser?.phoneNumber ?: ""
    val userMap = allUsers.associateBy { it.phoneNumber }

    // Direct Contacts Phone set
    val directPhoneRelations = contacts.filter { it.ownerPhone == myPhone }
    val directPhones = directPhoneRelations.map { it.contactPhone }.toSet()

    // Map of second degree paths: SecondDegreePhone -> DirectIntermediaryUser
    val secondaryChainList = remember(contacts, allUsers, currentUser) {
        val result = mutableListOf<Triple<User, User, User>>() // Me -> Intermediary -> SecondDegree
        if (currentUser == null) return@remember result

        for (contact in contacts) {
            // If direct contact of mine
            if (contact.ownerPhone == myPhone) {
                val directPhone = contact.contactPhone
                val directUser = userMap[directPhone] ?: continue

                // Find ALL contacts of this direct contact which are not Me and not my direct contacts
                val secondDegreeRelations = contacts.filter { it.ownerPhone == directPhone }
                for (secondRel in secondDegreeRelations) {
                    val candidatePhone = secondRel.contactPhone
                    if (candidatePhone != myPhone && candidatePhone !in directPhones) {
                        val secondUser = userMap[candidatePhone] ?: continue
                        result.add(Triple(currentUser, directUser, secondUser))
                    }
                }
            }
        }
        result
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Hush Discovery Path",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = Color.White
            )
            Text(
                text = "Connection paths for mutual statuses.",
                color = MutedText,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            // Explanatory premium M3 Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCardSecondary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = "Contact Hub",
                        tint = HushViolet,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "How Discovery Works in Hush",
                            color = LightText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "You see stories from contacts and mutual connections while keeping your identity private.",
                            color = MutedText,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // Direct Connections Visualization
        item {
            Text(
                text = "YOUR DIRECT LEGS (1ST DEGREE)",
                fontSize = 11.sp,
                color = HushViolet,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (directPhones.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No direct contacts saved yet. Add some contacts from the Discover tab.",
                            color = MutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(directPhones.toList()) { directPhone ->
                val user = userMap[directPhone]
                if (user != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SlateCard)
                            .border(1.dp, Borders, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Graph visual node
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(currentUser?.avatarColorHex?.toColor() ?: HushViolet),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = currentUser?.avatarEmoji ?: "🦁", fontSize = 16.sp)
                        }

                        // Connecting Path line
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .padding(horizontal = 8.dp)
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawLine(
                                    color = HushViolet,
                                    start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                                    end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                                    strokeWidth = 3.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(10f, 10f), 0f
                                    )
                                )
                            }
                            // Glow dot
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(HushViolet.copy(alpha = 0.15f))
                                    .border(1.dp, HushViolet, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("DIRECT", fontSize = 8.sp, color = HushViolet, fontWeight = FontWeight.Bold)
                            }
                        }

                        // End node
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(100.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(user.avatarColorHex.toColor()),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = user.avatarEmoji, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = user.name.split(" ").firstOrNull() ?: "",
                                fontSize = 11.sp,
                                color = LightText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Second Degree Connections Visualization
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "SECOND-DEGREE PATHS (2ND DEGREE)",
                fontSize = 11.sp,
                color = HushViolet,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (secondaryChainList.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No secondary connections found yet.",
                            color = MutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(secondaryChainList) { (me, intermediary, target) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SlateCard)
                        .border(1.dp, Borders, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Node 1: Me
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(me.avatarColorHex.toColor()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = me.avatarEmoji, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("You", fontSize = 10.sp, color = MutedText, maxLines = 1)
                    }

                    // Vector link 1
                    Box(modifier = Modifier.weight(1f).height(20.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = HushViolet,
                                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    // Node 2: Intermediary (Direct contact introducing them)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(68.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(intermediary.avatarColorHex.toColor()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = intermediary.avatarEmoji, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(intermediary.name.split(" ").first(), fontSize = 10.sp, color = HushViolet, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    // Vector link 2
                    Box(modifier = Modifier.weight(1f).height(20.dp)) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(
                                color = HushViolet,
                                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2),
                                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    }

                    // Node 3: Target Discoverable User
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(74.dp)) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(target.avatarColorHex.toColor()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = target.avatarEmoji, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(target.name.split(" ").first(), fontSize = 10.sp, color = HushViolet, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}


// ==================== WHISPERS CHAT LIST SCREEN (IG STYLE DMs) ====================
@Composable
fun WhispersChatListScreen(
    channels: List<ChatChannel>,
    onOpenChat: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Whispers",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (channels.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = "No chats",
                            tint = MutedText,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No private whispers yet.",
                            color = LightText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Replies to shared statuses will show up here.",
                            color = MutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else {
            items(channels) { channel ->
                Card(
                    onClick = { onOpenChat(channel.partner.phoneNumber) },
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("chat_row_${channel.partner.phoneNumber}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(channel.partner.avatarColorHex.toColor()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = channel.partner.avatarEmoji, fontSize = 20.sp)
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Text Info
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = channel.partner.name,
                                    color = LightText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = formatTimeAgo(channel.lastMessageTimestamp),
                                    color = MutedText,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = channel.lastMessage,
                                color = MutedText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}


// ==================== DIRECT MESSAGE CHAT SCREEN ====================
@Composable
fun DirectMessageChatScreen(
    partner: User,
    messages: List<com.example.data.model.StatusReply>,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Slide down to match last message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateCard)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(partner.avatarColorHex.toColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = partner.avatarEmoji, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = partner.name, color = LightText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                        Text(text = partner.bio, color = HushViolet, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        tint = HushViolet,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                color = SlateCard,
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Write a whisper...", color = MutedText, fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MidnightBlack,
                            unfocusedContainerColor = MidnightBlack,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText,
                            cursorColor = HushViolet,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = HushViolet),
                        modifier = Modifier.size(46.dp).testTag("chat_send_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MidnightBlack, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
        containerColor = MidnightBlack
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { msg ->
                    val isMe = msg.senderPhone != partner.phoneNumber
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isMe) HushViolet else SlateCard
                            ),
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMe) 16.dp else 2.dp,
                                bottomEnd = if (isMe) 2.dp else 16.dp
                            ),
                            modifier = Modifier.widthIn(max = 280.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (msg.originalStatusId != -1) {
                                    // Replied status story context!
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MidnightBlack.copy(alpha = 0.3f))
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "Replied to Status 🤫",
                                            fontSize = 11.sp,
                                            color = if (isMe) Color.White.copy(alpha = 0.6f) else HushViolet,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                Text(
                                    text = msg.replyMessage,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestamp)),
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==================== DISCOVERY ENGINE & INVITATION CENTER ====================
// Helper to read on-device phone contacts
fun scanDeviceContacts(context: android.content.Context): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    try {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null,
            null,
            null
        )
        cursor?.use {
            val nameIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = it.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                if (nameIdx >= 0 && numIdx >= 0) {
                    val name = it.getString(nameIdx) ?: ""
                    val number = it.getString(numIdx) ?: ""
                    if (name.isNotBlank() && number.isNotBlank()) {
                        list.add(Pair(name, number))
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

@Composable
fun ContactDirectoryScreen(
    allUsers: List<User>,
    contacts: List<ContactRelation>,
    currentUser: User?,
    onAddContact: (String, String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onSyncContacts: (List<Pair<String, String>>) -> Unit,
    onAddOtherContactRelation: (String, String, String) -> Unit,
    onRemoveOtherContactRelation: (String, String) -> Unit
) {
    val myPhone = currentUser?.phoneNumber ?: ""
    val nonMeUsers = allUsers.filter { it.phoneNumber != myPhone }
    val directPhones = contacts.filter { it.ownerPhone == myPhone }.map { it.contactPhone }.toSet()

    var inviteName by remember { mutableStateOf("") }
    var invitePhone by remember { mutableStateOf("") }
    var exploringUser by remember { mutableStateOf<User?>(null) }
    
    val context = LocalContext.current

    // Runtime Permission Launcher for real contacts access
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val deviceContacts = scanDeviceContacts(context)
            if (deviceContacts.isNotEmpty()) {
                onSyncContacts(deviceContacts)
                Toast.makeText(context, "Successfully synced ${deviceContacts.size} contacts from your phone's address book! 🤫", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "No contacts found in Android contacts database. Try simulating sync instead!", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Permission to scan contacts was declined.", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Contacts",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                ),
                color = Color.White
            )
            Text(
                text = "Scan your contacts to find people on Hush.",
                color = MutedText,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Section: Real contact detection engine (Sync local phonebook)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCardSecondary),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, HushViolet.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth().testTag("sync_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "FIND CONTACTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HushViolet,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Find contacts already on Hush.",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Scan Real Contacts Button
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HushViolet, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("scan_real_contacts_btn")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Contacts, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Device", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        
                    }
                }
            }
        }

        // Section: Invitation Form
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth().testTag("invite_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "ADD MANUALLY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HushViolet,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add someone by name and number.",
                        fontSize = 12.sp,
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inviteName,
                        onValueChange = { inviteName = it },
                        label = { Text("Friend's Full Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g. John Doe", color = MutedText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HushViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = HushViolet,
                            unfocusedLabelColor = MutedText,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("invite_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = invitePhone,
                        onValueChange = { invitePhone = it },
                        label = { Text("Friend's Phone Number", fontSize = 12.sp) },
                        placeholder = { Text("e.g. +14150009999", color = MutedText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HushViolet,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = HushViolet,
                            unfocusedLabelColor = MutedText,
                            focusedTextColor = LightText,
                            unfocusedTextColor = LightText
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("invite_phone_input")
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (inviteName.isNotBlank() && invitePhone.isNotBlank()) {
                                onAddContact(invitePhone.trim(), inviteName.trim())
                                Toast.makeText(context, "$inviteName added to your connections.", Toast.LENGTH_LONG).show()
                                inviteName = ""
                                invitePhone = ""
                            } else {
                                Toast.makeText(context, "Please fill in both name and phone fields.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SlateCardSecondary, contentColor = LightText),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("send_invite_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section Title: Discover Network friends
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your Network",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MutedText,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
        }

        items(nonMeUsers) { user ->
            val isDirect = user.phoneNumber in directPhones

            Card(
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, if (isDirect) HushViolet.copy(alpha = 0.3f) else Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exploringUser = user }
                    .testTag("directory_item_${user.phoneNumber}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(user.avatarColorHex.toColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = user.avatarEmoji, fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user.name,
                            color = LightText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = user.phoneNumber, color = MutedText, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isDirect) "Contact" else "Mutual",
                                color = HushViolet,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MutedText.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // Button switcher
                    if (isDirect) {
                        IconButton(
                            onClick = { onRemoveContact(user.phoneNumber) },
                            modifier = Modifier.testTag("remove_contact_${user.phoneNumber}")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    } else {
                        IconButton(
                            onClick = { onAddContact(user.phoneNumber, user.name) },
                            modifier = Modifier.testTag("add_contact_${user.phoneNumber}")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add", tint = HushViolet)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Exploring the circle of another contact dialog
    if (exploringUser != null) {
        val exprUser = exploringUser!!
        val exprUserContacts = contacts.filter { it.ownerPhone == exprUser.phoneNumber }
        
        val candidates = allUsers.filter { u ->
            u.phoneNumber != exprUser.phoneNumber && 
            u.phoneNumber != myPhone &&
            exprUserContacts.none { it.contactPhone == u.phoneNumber }
        }

        AlertDialog(
            onDismissRequest = { exploringUser = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(exprUser.avatarColorHex.toColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(exprUser.avatarEmoji, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = exprUser.name, color = LightText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Hush Circle Explorer", color = HushViolet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Explore contacts saved inside ${exprUser.name}'s private address book to map 2nd-degree paths.",
                        color = MutedText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

                    Text(
                        text = "SAVED CONNECTIONS (${exprUserContacts.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = HushViolet,
                        letterSpacing = 0.5.sp
                    )

                    if (exprUserContacts.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MidnightBlack, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No contacts saved by ${exprUser.name} yet. Link candidates below to build the paths!",
                                color = LightText,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        exprUserContacts.forEach { relation ->
                            val targetUser = allUsers.find { it.phoneNumber == relation.contactPhone }
                            val isMeDirect = relation.contactPhone in directPhones
                            val isMeSelf = relation.contactPhone == myPhone

                            Card(
                                colors = CardDefaults.cardColors(containerColor = SlateCardSecondary),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background((targetUser?.avatarColorHex ?: "#444444").toColor()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(targetUser?.avatarEmoji ?: "👤", fontSize = 14.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = relation.contactNickName,
                                            color = LightText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = relation.contactPhone,
                                            color = MutedText,
                                            fontSize = 10.sp
                                        )
                                        if (isMeSelf) {
                                            Text("Saves you back (Mutual link) 🗝️", color = HushViolet, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        } else if (isMeDirect) {
                                            Text("Direct in your circle too", color = HushViolet, fontSize = 9.sp)
                                        } else {
                                            Text("Accessible via ${exprUser.name}", color = HushViolet, fontSize = 9.sp)
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (!isMeDirect && !isMeSelf) {
                                            IconButton(
                                                onClick = {
                                                    onAddContact(relation.contactPhone, relation.contactNickName)
                                                    Toast.makeText(context, "${relation.contactNickName} saved to your connections!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.PersonAdd, contentDescription = "Add Me", tint = HushViolet, modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                onRemoveOtherContactRelation(exprUser.phoneNumber, relation.contactPhone)
                                                Toast.makeText(context, "Link severed with ${relation.contactNickName}.", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.LinkOff, contentDescription = "Sever Link", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (candidates.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
                        Text(
                            text = "SIMULATE SECRET CONNECTION FOR ${exprUser.name.uppercase()}:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = HushViolet,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Tap a user below to simulate ${exprUser.name} saving them in their phonebook. This sets up private 2nd-degree feeds!",
                            color = MutedText,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            items(candidates) { candidate ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable {
                                            onAddOtherContactRelation(
                                                exprUser.phoneNumber,
                                                candidate.phoneNumber,
                                                candidate.name.substringBefore(" ")
                                            )
                                            Toast.makeText(context, "${exprUser.name} saved ${candidate.name}!", Toast.LENGTH_SHORT).show()
                                        }
                                        .width(54.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(candidate.avatarColorHex.toColor())
                                            .border(1.dp, HushViolet.copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(candidate.avatarEmoji, fontSize = 18.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = candidate.name.substringBefore(" "),
                                        color = LightText,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { exploringUser = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = HushViolet)
                ) {
                    Text("Close Explorer", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SlateCard,
            shape = RoundedCornerShape(20.dp)
        )
    }
}



// ==================== STATUS CREATOR SCREEN ====================
@Composable
fun StatusCreatorScreen(
    onClose: () -> Unit,
    onPost: (String, String, String, String, String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var emojiInput by remember { mutableStateOf("") }

    // Named mood atmospheres
    data class Mood(val name: String, val start: String, val end: String)
    val moods = listOf(
        Mood("Hush",     "#03001e", "#7303c0"),
        Mood("Heat",     "#c94b4b", "#4b134f"),
        Mood("Drift",    "#0f2027", "#2c5364"),
        Mood("Reign",    "#1a1a1a", "#b8860b"),
        Mood("Bloom",    "#614385", "#516395"),
        Mood("Volt",     "#0d0d0d", "#4a00e0"),
        Mood("Midnight", "#0f0c29", "#302b63"),
        Mood("Calm",     "#134e5e", "#71b280")
    )
    var selectedMoodIndex by remember { mutableStateOf(0) }

    val fonts = listOf(
        Pair("Serif", FontFamily.Serif),
        Pair("Monospace", FontFamily.Monospace),
        Pair("Cursive", FontFamily.Cursive),
        Pair("SansSerif", FontFamily.SansSerif)
    )
    var selectedFontIndex by remember { mutableStateOf(0) }

    val selectedMood = moods[selectedMoodIndex]
    val brush = Brush.linearGradient(
        colors = listOf(selectedMood.start.toColor(), selectedMood.end.toColor())
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.testTag("status_close")) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                Text(
                    text = "New Status",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif
                )
                Button(
                    onClick = {
                        if (text.isNotBlank()) {
                            onPost(
                                text,
                                fonts[selectedFontIndex].first,
                                selectedMood.start,
                                selectedMood.end,
                                emojiInput.trim()
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.testTag("status_post_btn")
                ) {
                    Text("Post", fontWeight = FontWeight.Bold)
                }
            }

            // Canvas: emoji hero + text input
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    // Big emoji display
                    if (emojiInput.isNotBlank()) {
                        Text(
                            text = emojiInput,
                            fontSize = 80.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Text input
                    TextField(
                        value = text,
                        onValueChange = { if (it.length <= 120) text = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontFamily = fonts[selectedFontIndex].second
                        ),
                        placeholder = {
                            Text(
                                "Type how you feel...",
                                modifier = Modifier.fillMaxWidth(),
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color.White.copy(alpha = 0.45f),
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    fontFamily = fonts[selectedFontIndex].second
                                )
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("story_input_text")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${text.length}/120",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }
            }

            // Bottom tray
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Emoji input field
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Emoji", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = emojiInput,
                        onValueChange = { emojiInput = it.take(4) },
                        placeholder = { Text("e.g. 🔥 or 🌙✨", color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Mood selector
                Column {
                    Text("Mood", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(moods.size) { i ->
                            val mood = moods[i]
                            val isSelected = i == selectedMoodIndex
                            Box(
                                modifier = Modifier
                                    .size(width = 56.dp, height = 34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(listOf(mood.start.toColor(), mood.end.toColor()))
                                    )
                                    .border(
                                        if (isSelected) 2.dp else 0.dp,
                                        if (isSelected) Color.White else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedMoodIndex = i },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mood.name,
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Font selector
                Column {
                    Text("Style", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        fonts.forEachIndexed { i, (label, family) ->
                            val isSelected = i == selectedFontIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.1f))
                                    .clickable { selectedFontIndex = i }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = family
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



// ==================== FULLSCREEN STATUS PLAYBACK VIEWER ====================
@Composable
fun FullscreenStatusViewer(
    statusGroup: UserStatusGroup,
    onClose: () -> Unit,
    onReply: (Int, String) -> Unit,
    onViewStory: (Int) -> Unit
) {
    val stories = statusGroup.statuses
    if (stories.isEmpty()) {
        onClose()
        return
    }

    var storyIndex by remember { mutableStateOf(0) }
    var currentReplyText by remember { mutableStateOf("") }
    
    val currentStory = stories[storyIndex]
    val coroutineScope = rememberCoroutineScope()
    
    // Auto increment timer state
    var progressVal by remember { mutableStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }

    // Broadcast that story was viewed
    LaunchedEffect(currentStory.id) {
        onViewStory(currentStory.id)
    }

    // Timer Loop Effect
    LaunchedEffect(storyIndex, isPaused) {
        if (!isPaused) {
            val stepTime = 50 // ms
            val totalTime = 4000 // 4 seconds total story duration
            val maxSteps = totalTime / stepTime
            
            while (progressVal < 1f) {
                delay(stepTime.toLong())
                if (!isPaused) {
                    progressVal += (1f / maxSteps)
                }
            }
            
            // Advance index or close
            if (storyIndex < stories.size - 1) {
                progressVal = 0f
                storyIndex++
            } else {
                onClose()
            }
        }
    }

    val brush = Brush.linearGradient(
        colors = listOf(
            currentStory.startColorHex.toColor(),
            currentStory.endColorHex.toColor()
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPaused = true
                        tryAwaitRelease()
                        isPaused = false
                    },
                    onTap = { offset ->
                        // Left tapped go back, right tapped go next
                        if (offset.x < size.width / 3) {
                            if (storyIndex > 0) {
                                progressVal = 0f
                                storyIndex--
                            } else {
                                onClose()
                            }
                        } else {
                            if (storyIndex < stories.size - 1) {
                                progressVal = 0f
                                storyIndex++
                            } else {
                                onClose()
                            }
                        }
                    }
                )
            }
    ) {
        // High fidelity gradients layout
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Instgram Status segment progress bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (i in stories.indices) {
                        val segProgress = when {
                            i < storyIndex -> 1f
                            i == storyIndex -> progressVal
                            else -> 0f
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(segProgress)
                                    .background(Color.White)
                            )
                        }
                    }
                }

                // Subtitle Header info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(statusGroup.user.avatarColorHex.toColor()),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = statusGroup.user.avatarEmoji, fontSize = 18.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = statusGroup.user.name,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        // Instagram/WhatsApp discover indicator
                        val connectionColor = if (statusGroup.relationship == "Second-Degree Contact") HushViolet else HushViolet
                        val connectionLabel = if (statusGroup.relationship == "Second-Degree Contact") {
                            "via ${statusGroup.intermediaryName}"
                        } else if (statusGroup.relationship == "Me") {
                            "Your story"
                        } else {
                            "Direct"
                        }
                        Text(
                            text = connectionLabel,
                            color = connectionColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = formatTimeAgo(currentStory.timestamp),
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Big Centered visual content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        if (currentStory.emojiSticker.isNotBlank()) {
                            Text(text = currentStory.emojiSticker, fontSize = 72.sp)
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        val fontFamily = when (currentStory.fontFamily) {
                            "Mono" -> FontFamily.Monospace
                            "Cursive" -> FontFamily.Cursive
                            "Sans" -> FontFamily.SansSerif
                            else -> FontFamily.Serif
                        }
                        
                        Text(
                            text = currentStory.content,
                            color = currentStory.textColorHex.toColor(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            textAlign = TextAlign.Center,
                            fontFamily = fontFamily,
                            lineHeight = 36.sp,
                            modifier = Modifier.fillMaxWidth().testTag("story_main_text")
                        )
                    }
                }

                // Instagram Reply input overlay
                if (statusGroup.relationship != "Me") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                )
                            )
                            .padding(bottom = 24.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextField(
                                value = currentReplyText,
                                onValueChange = { currentReplyText = it },
                                placeholder = { Text("Send private whisper...", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.15f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reply_input")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (currentReplyText.isNotBlank()) {
                                        onReply(currentStory.id, currentReplyText)
                                        currentReplyText = ""
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .testTag("reply_send_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Reply",
                                    tint = MidnightBlack
                                )
                            }
                        }
                    }
                } else {
                    // Current user show views count at bottom (premium WhatsApp style feature in Instagram layout!)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MidnightBlack.copy(alpha = 0.6f))
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Visibility, contentDescription = "Views", tint = HushViolet, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${currentStory.views} views",
                                color = LightText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
