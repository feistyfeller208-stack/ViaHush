package com.example.data.repository

import com.example.data.dao.UserDao
import com.example.data.dao.ContactDao
import com.example.data.dao.StatusDao
import com.example.data.dao.StatusReplyDao
import com.example.data.model.User
import com.example.data.model.ContactRelation
import com.example.data.model.StatusStory
import com.example.data.model.StatusReply
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.Serializable

// Container representing a story group for a user
data class UserStatusGroup(
    val user: User,
    val relationship: String, // "Me", "Direct Contact", "Second-Degree Contact" (via X)
    val intermediaryName: String = "", // e.g. "Alice" if second-degree
    val statuses: List<StatusStory>
) : Serializable

class HushRepository(
    private val userDao: UserDao,
    private val contactDao: ContactDao,
    private val statusDao: StatusDao,
    private val statusReplyDao: StatusReplyDao
) {
    val allUsers: Flow<List<User>> = userDao.getAllUsersFlow()
    val allContacts: Flow<List<ContactRelation>> = contactDao.getAllContactsFlow()
    val allStatuses: Flow<List<StatusStory>> = statusDao.getAllStatusesFlow()
    val currentUserFlow: Flow<User?> = userDao.getCurrentUserFlow()
    val allRepliesFlow: Flow<List<StatusReply>> = statusReplyDao.getAllRepliesFlow()

    // Calculate Hush Social Feed combining Direct and Second-Degree connections reactively!
    val statusGroupsFlow: Flow<List<UserStatusGroup>> = combine(
        allUsers,
        allContacts,
        allStatuses,
        currentUserFlow
    ) { users, contacts, statuses, currentUser ->
        if (currentUser == null) return@combine emptyList()

        val userMap = users.associateBy { it.phoneNumber }
        val currentUserPhone = currentUser.phoneNumber

        // 1. Find direct contacts of current user
        val directContactRelations = contacts.filter { it.ownerPhone == currentUserPhone }
        val directContactPhones = directContactRelations.map { it.contactPhone }.toSet()
        val directContactPhoneToNick = directContactRelations.associate { it.contactPhone to it.contactNickName }

        // 2. Find second-degree contacts (contacts of my contacts)
        // Set of phones that are contacts of the people who are in my direct contacts
        val secondDegreePhoneToIntermediary = mutableMapOf<String, String>() // ContactPhone -> Intermediary Name

        for (contact in contacts) {
            // If the contact block owner is a direct contact of mine
            if (contact.ownerPhone in directContactPhones) {
                val candidatePhone = contact.contactPhone
                // Rules: Must not be Me, and must not be a direct contact already
                if (candidatePhone != currentUserPhone && candidatePhone !in directContactPhones) {
                    val intermediaryUser = userMap[contact.ownerPhone]
                    val intermediaryName = directContactPhoneToNick[contact.ownerPhone] ?: intermediaryUser?.name ?: "Someone"
                    // If not added yet, record it
                    if (!secondDegreePhoneToIntermediary.containsKey(candidatePhone)) {
                        secondDegreePhoneToIntermediary[candidatePhone] = intermediaryName
                    }
                }
            }
        }

        // 3. Group statuses by user
        val statusesByCreator = statuses.groupBy { it.creatorPhone }

        val groups = mutableListOf<UserStatusGroup>()

        // Add Me first if status exists
        val myStatuses = statusesByCreator[currentUserPhone] ?: emptyList()
        groups.add(
            UserStatusGroup(
                user = currentUser,
                relationship = "Me",
                statuses = myStatuses
            )
        )

        // Process other users who have statuses
        for ((creatorPhone, creatorStatuses) in statusesByCreator) {
            if (creatorPhone == currentUserPhone) continue
            val creatorUser = userMap[creatorPhone] ?: continue

            if (creatorPhone in directContactPhones) {
                val nickName = directContactPhoneToNick[creatorPhone] ?: creatorUser.name
                groups.add(
                    UserStatusGroup(
                        user = creatorUser.copy(name = nickName),
                        relationship = "Direct Contact",
                        statuses = creatorStatuses
                    )
                )
            } else if (creatorPhone in secondDegreePhoneToIntermediary.keys) {
                val intermediary = secondDegreePhoneToIntermediary[creatorPhone] ?: "Someone"
                groups.add(
                    UserStatusGroup(
                        user = creatorUser,
                        relationship = "Second-Degree Contact",
                        intermediaryName = intermediary,
                        statuses = creatorStatuses
                    )
                )
            }
        }

        groups
    }

    suspend fun getCurrentUser(): User? = userDao.getCurrentUser()

    suspend fun getUserByPhone(phone: String): User? = userDao.getUserByPhone(phone)

    suspend fun insertUser(user: User) = userDao.insertUser(user)

    suspend fun updateUser(user: User) = userDao.updateUser(user)

    suspend fun addContact(contactPhone: String, nickname: String) {
        val currentUser = getCurrentUser() ?: return
        val relation = ContactRelation(
            ownerPhone = currentUser.phoneNumber,
            contactPhone = contactPhone,
            contactNickName = nickname
        )
        contactDao.insertContact(relation)
    }

    suspend fun deleteContact(contactPhone: String) {
        val currentUser = getCurrentUser() ?: return
        contactDao.removeContact(currentUser.phoneNumber, contactPhone)
    }

    suspend fun addCustomContactRelation(ownerPhone: String, contactPhone: String, nickname: String) {
        val relation = ContactRelation(
            ownerPhone = ownerPhone,
            contactPhone = contactPhone,
            contactNickName = nickname
        )
        contactDao.insertContact(relation)
    }

    suspend fun removeCustomContactRelation(ownerPhone: String, contactPhone: String) {
        contactDao.removeContact(ownerPhone, contactPhone)
    }

    suspend fun createStatusStory(
        content: String,
        fontFamily: String = "Serif",
        startHex: String = "#8A2387",
        endHex: String = "#E94057",
        emojiSticker: String = ""
    ) {
        val currentUser = getCurrentUser() ?: return
        val status = StatusStory(
            creatorPhone = currentUser.phoneNumber,
            content = content,
            fontFamily = fontFamily,
            startColorHex = startHex,
            endColorHex = endHex,
            emojiSticker = emojiSticker
        )
        statusDao.insertStatus(status)
    }

    suspend fun deleteStory(statusId: Int) {
        statusDao.deleteStatus(statusId)
    }

    suspend fun viewStory(statusId: Int) {
        statusDao.incrementViews(statusId)
    }

    fun getChatFlow(otherPhone: String): Flow<List<StatusReply>> {
        return combine(currentUserFlow, statusReplyDao.getAllRepliesFlow()) { currentUser, allReplies ->
            val myPhone = currentUser?.phoneNumber ?: return@combine emptyList()
            allReplies.filter {
                (it.senderPhone == myPhone && it.receiverPhone == otherPhone) ||
                (it.senderPhone == otherPhone && it.receiverPhone == myPhone)
            }.sortedBy { it.timestamp }
        }
    }

    suspend fun replyToStatus(statusId: Int, receiverPhone: String, message: String) {
        val currentUser = getCurrentUser() ?: return
        val reply = StatusReply(
            senderPhone = currentUser.phoneNumber,
            receiverPhone = receiverPhone,
            originalStatusId = statusId,
            replyMessage = message
        )
        statusReplyDao.insertReply(reply)
    }

    // Comprehensive Seeder for rich initial graph & feed
    suspend fun seedDatabaseIfEmpty() {
        val existingUsers = userDao.getCurrentUser()
        if (existingUsers != null) return // Already seeded!

        // 1. Create System / Network Users
        val users = listOf(
            User(
                phoneNumber = "+15550001",
                name = "Leo (Me)",
                avatarEmoji = "🦁",
                avatarColorHex = "#20B2AA",
                bio = "Designing the future, one whisper at a time. ✨",
                isCurrentUser = true
            ),
            User(
                phoneNumber = "+15550002",
                name = "Alice Rivera",
                avatarEmoji = "🌸",
                avatarColorHex = "#FF6B6B",
                bio = "Keeping my story cozy. Shhh... 🤫🍟"
            ),
            User(
                phoneNumber = "+15550003",
                name = "Charlie Chen",
                avatarEmoji = "⚡",
                avatarColorHex = "#4ECDC4",
                bio = "Coffee, compiler, sleep. In that order."
            ),
            User(
                phoneNumber = "+15550004",
                name = "Bob Miller",
                avatarEmoji = "🍿",
                avatarColorHex = "#FFD166",
                bio = "Always down for taco Tuesdays! 🌮"
            ),
            User(
                phoneNumber = "+15550005",
                name = "Diana Prince",
                avatarEmoji = "🎮",
                avatarColorHex = "#1D3557",
                bio = "Casual gamer & late-night stargazer."
            ),
            User(
                phoneNumber = "+15550006",
                name = "Frank Ocean",
                avatarEmoji = "🥑",
                avatarColorHex = "#06D6A0",
                bio = "Analog dreams & warm summer soundtracks."
            )
        )
        userDao.insertUsers(users)

        // 2. Direct Contacts (Saved in Me's phonebook)
        val mePhone = "+15550001"
        val myContacts = listOf(
            ContactRelation(ownerPhone = mePhone, contactPhone = "+15550002", contactNickName = "Alice"),
            ContactRelation(ownerPhone = mePhone, contactPhone = "+15550003", contactNickName = "Charlie")
        )
        contactDao.insertContacts(myContacts)

        // 3. Alice's Contacts (introducing Bob to Me as Second-Degree)
        val aliceContacts = listOf(
            ContactRelation(ownerPhone = "+15550002", contactPhone = mePhone, contactNickName = "Leo"),
            ContactRelation(ownerPhone = "+15550002", contactPhone = "+15550004", contactNickName = "Bob Mc🍿")
        )
        contactDao.insertContacts(aliceContacts)

        // 4. Charlie's Contacts (introducing Diana to Me as Second-Degree)
        val charlieContacts = listOf(
            ContactRelation(ownerPhone = "+15550003", contactPhone = mePhone, contactNickName = "Leo"),
            ContactRelation(ownerPhone = "+15550003", contactPhone = "+15550005", contactNickName = "Diana Gamer")
        )
        contactDao.insertContacts(charlieContacts)

        // 5. Seeds some amazing visual statuses with beautiful Instagram gradients
        val statusStories = listOf(
            StatusStory(
                creatorPhone = "+15550002", // Alice
                content = "Just watched the sunset, the sky looks completely on fire. 🌅✨ Is anyone awake?",
                fontFamily = "Cursive",
                startColorHex = "#F3904F",
                endColorHex = "#3B4371",
                emojiSticker = "🌅",
                timestamp = System.currentTimeMillis() - 2 * 60 * 60 * 1000 // 2 hours ago
            ),
            StatusStory(
                creatorPhone = "+15550003", // Charlie
                content = "Writing some clean Kotlin code... compiling in 3(ish) hours. 💻🦖",
                fontFamily = "Mono",
                startColorHex = "#0F2027",
                endColorHex = "#2C5364",
                emojiSticker = "🦖",
                timestamp = System.currentTimeMillis() - 4 * 60 * 60 * 1000 // 4 hours ago
            ),
            StatusStory(
                creatorPhone = "+15550004", // Bob (Second degree!)
                content = "Who wants to catch up over coffee this Saturday? ☕ Looking for high caffeine!",
                fontFamily = "Sans",
                startColorHex = "#8A2387",
                endColorHex = "#E94057",
                emojiSticker = "☕",
                timestamp = System.currentTimeMillis() - 1 * 60 * 60 * 1000 // 1 hour ago
            ),
            StatusStory(
                creatorPhone = "+15550005", // Diana (Second degree!)
                content = "Just unlocked a legendary weapon in Elden Ring! 🗡️ Let's goooo!",
                fontFamily = "Serif",
                startColorHex = "#1F1C2C",
                endColorHex = "#928DAB",
                emojiSticker = "👑",
                timestamp = System.currentTimeMillis() - 30 * 60 * 1000 // 30 mins ago
            ),
            StatusStory(
                creatorPhone = "+15550001", // Me
                content = "Stealth launch of Hush 🤫 Only shared with actual contacts + friends of friends.",
                fontFamily = "Serif",
                startColorHex = "#03001e",
                endColorHex = "#7303c0",
                emojiSticker = "🤫",
                timestamp = System.currentTimeMillis() - 15 * 60 * 1000 // 15 mins ago
            )
        )

        for (status in statusStories) {
            statusDao.insertStatus(status)
        }
    }
}
