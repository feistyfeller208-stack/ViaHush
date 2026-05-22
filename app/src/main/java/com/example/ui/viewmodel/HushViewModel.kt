package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.model.StatusStory
import com.example.data.model.StatusReply
import com.example.data.repository.HushRepository
import com.example.data.repository.UserStatusGroup
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.Serializable

sealed interface UiState<out T> {
    object Idle : UiState<Nothing>
    object Loading : UiState<Nothing>
    data class Success<out T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class HushViewModel(private val repository: HushRepository) : ViewModel() {

    // Current User
    val currentUserState: StateFlow<User?> = repository.currentUserFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Status lifespan control (default is 24 hours, but customizable for real-time testing)
    val statusLifespanMs = MutableStateFlow(24 * 60 * 60 * 1000L) // 24 hours default

    // All Status Replies/Comments StateFlow
    val allRepliesState: StateFlow<List<StatusReply>> = repository.allRepliesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Real-time ticker flow that emits every 2 seconds to refresh countdowns & trigger expirations
    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            kotlinx.coroutines.delay(2000)
        }
    }

    // Social Story Feed (Direct & Second degree) with real-time age-based expiration
    val statusGroupsState: StateFlow<List<UserStatusGroup>> = combine(
        repository.statusGroupsFlow,
        statusLifespanMs,
        tickerFlow
    ) { groups, lifespan, now ->
        groups.map { group ->
            group.copy(
                statuses = group.statuses.filter {
                    val age = now - it.timestamp
                    age < lifespan
                }
            )
        }.filter { it.statuses.isNotEmpty() || it.relationship == "Me" }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateLifespan(lifespan: Long) {
        statusLifespanMs.value = lifespan
    }

    // All Users in the system (for contact discovery / directory)
    val allUsersState: StateFlow<List<User>> = repository.allUsers
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // All contacts saved by the user
    val contactsState: StateFlow<List<com.example.data.model.ContactRelation>> = repository.allContacts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _scannedContacts = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val scannedContacts: StateFlow<List<Pair<String, String>>> = _scannedContacts.asStateFlow()

    // Active Chat flow stateholder
    private val _activeChatPartner = MutableStateFlow<String?>(null)
    val activeChatPartner: StateFlow<String?> = _activeChatPartner.asStateFlow()

    val activeChatMessages: StateFlow<List<StatusReply>> = _activeChatPartner
        .flatMapLatest { partnerPhone ->
            if (partnerPhone == null) flowOf(emptyList())
            else repository.getChatFlow(partnerPhone)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Flow for all DM channels/threads (for DM list view)
    val chatChannelsState: StateFlow<List<ChatChannel>> = combine(
        repository.allRepliesFlow,
        repository.allUsers,
        repository.currentUserFlow
    ) { replies, users, currentUser ->
        if (currentUser == null) return@combine emptyList<ChatChannel>()
        val myPhone = currentUser.phoneNumber
        val userMap = users.associateBy { it.phoneNumber }

        // Group replies by partner
        val partnerPhones = replies.map {
            if (it.senderPhone == myPhone) it.receiverPhone else it.senderPhone
        }.distinct().filter { it != myPhone }

        partnerPhones.mapNotNull { partnerPhone ->
            val partnerUser = userMap[partnerPhone] ?: return@mapNotNull null
            val partnerReplies = replies.filter {
                (it.senderPhone == myPhone && it.receiverPhone == partnerPhone) ||
                (it.senderPhone == partnerPhone && it.receiverPhone == myPhone)
            }.sortedByDescending { it.timestamp }

            val lastReply = partnerReplies.firstOrNull() ?: return@mapNotNull null
            ChatChannel(
                partner = partnerUser,
                lastMessage = lastReply.replyMessage,
                lastMessageTimestamp = lastReply.timestamp
            )
        }.sortedByDescending { it.lastMessageTimestamp }
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Creation State
    private val _postStatusState = MutableStateFlow<UiState<Boolean>>(UiState.Idle)
    val postStatusState = _postStatusState.asStateFlow()

    init {
        // Run seed check
        viewModelScope.launch {
            repository.seedDatabaseIfEmpty()
        }
    }

    fun setChatPartner(phone: String?) {
        _activeChatPartner.value = phone
    }

    fun updateUserProfile(name: String, emoji: String, colorHex: String, bio: String) {
        viewModelScope.launch {
            val current = currentUserState.value ?: return@launch
            val updated = current.copy(
                name = name,
                avatarEmoji = emoji,
                avatarColorHex = colorHex,
                bio = bio
            )
            repository.updateUser(updated)
        }
    }

    fun addLocalContact(phone: String, nickname: String) {
        viewModelScope.launch {
            repository.addContact(phone, nickname)
        }
    }

    fun removeLocalContact(phone: String) {
        viewModelScope.launch {
            repository.deleteContact(phone)
        }
    }

    fun addOtherContactRelation(ownerPhone: String, contactPhone: String, nickname: String) {
        viewModelScope.launch {
            repository.addCustomContactRelation(ownerPhone, contactPhone, nickname)
        }
    }

    fun removeOtherContactRelation(ownerPhone: String, contactPhone: String) {
        viewModelScope.launch {
            repository.removeCustomContactRelation(ownerPhone, contactPhone)
        }
    }

    fun syncDeviceContacts(deviceContacts: List<Pair<String, String>>) {
        _scannedContacts.value = deviceContacts
        viewModelScope.launch {
            val users = allUsersState.value
            val me = currentUserState.value ?: return@launch
            val mePhone = me.phoneNumber

            for ((name, rawPhone) in deviceContacts) {
                val cleanedPhone = rawPhone.filter { it.isDigit() || it == '+' }
                if (cleanedPhone.isBlank() || cleanedPhone == mePhone) continue

                // Look for user whose phone ends with or matches cleanedPhone
                val matchingUser = users.find { u ->
                    val userCleaned = u.phoneNumber.filter { it.isDigit() || it == '+' }
                    userCleaned == cleanedPhone || (cleanedPhone.length >= 7 && userCleaned.endsWith(cleanedPhone))
                }

                if (matchingUser != null) {
                    repository.addContact(matchingUser.phoneNumber, name)
                }
            }
        }
    }

    fun postStatus(content: String, fontFamily: String, startHex: String, endHex: String, emojiSticker: String) {
        viewModelScope.launch {
            _postStatusState.value = UiState.Loading
            try {
                if (content.isNotBlank()) {
                    repository.createStatusStory(
                        content = content,
                        fontFamily = fontFamily,
                        startHex = startHex,
                        endHex = endHex,
                        emojiSticker = emojiSticker
                    )
                    _postStatusState.value = UiState.Success(true)
                } else {
                    _postStatusState.value = UiState.Error("Status cannot be blank")
                }
            } catch (e: Exception) {
                _postStatusState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun resetPostState() {
        _postStatusState.value = UiState.Idle
    }

    fun deleteStatusStory(statusId: Int) {
        viewModelScope.launch {
            repository.deleteStory(statusId)
        }
    }

    fun markStoryAsViewed(statusId: Int) {
        viewModelScope.launch {
            repository.viewStory(statusId)
        }
    }

    fun replyToStory(storyId: Int, receiverPhone: String, message: String) {
        viewModelScope.launch {
            if (message.isNotBlank()) {
                repository.replyToStatus(
                    statusId = storyId,
                    receiverPhone = receiverPhone,
                    message = message
                )
            }
        }
    }

    fun sendDirectMessage(receiverPhone: String, message: String) {
        viewModelScope.launch {
            if (message.isNotBlank()) {
                repository.replyToStatus(
                    statusId = -1, // -1 means regular chat message not bound to a status
                    receiverPhone = receiverPhone,
                    message = message
                )
            }
        }
    }
}

// Representing active conversations
data class ChatChannel(
    val partner: User,
    val lastMessage: String,
    val lastMessageTimestamp: Long
) : Serializable
