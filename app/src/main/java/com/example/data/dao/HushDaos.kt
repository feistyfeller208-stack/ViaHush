package com.example.data.dao

import androidx.room.*
import com.example.data.model.User
import com.example.data.model.ContactRelation
import com.example.data.model.StatusStory
import com.example.data.model.StatusReply
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUser(): User?

    @Query("SELECT * FROM users WHERE isCurrentUser = 1")
    fun getCurrentUserFlow(): Flow<User?>

    @Query("SELECT * FROM users WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Update
    suspend fun updateUser(user: User)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts")
    fun getAllContactsFlow(): Flow<List<ContactRelation>>

    @Query("SELECT * FROM contacts WHERE ownerPhone = :ownerPhone")
    suspend fun getContactsForUser(ownerPhone: String): List<ContactRelation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactRelation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactRelation>)

    @Query("DELETE FROM contacts WHERE ownerPhone = :ownerPhone AND contactPhone = :contactPhone")
    suspend fun removeContact(ownerPhone: String, contactPhone: String)
}

@Dao
interface StatusDao {
    @Query("SELECT * FROM statuses ORDER BY timestamp DESC")
    fun getAllStatusesFlow(): Flow<List<StatusStory>>

    @Query("SELECT * FROM statuses WHERE creatorPhone = :phone ORDER BY timestamp DESC")
    fun getStatusesForUserFlow(phone: String): Flow<List<StatusStory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatus(status: StatusStory): Long

    @Query("DELETE FROM statuses WHERE id = :statusId")
    suspend fun deleteStatus(statusId: Int)

    @Query("UPDATE statuses SET views = views + 1 WHERE id = :statusId")
    suspend fun incrementViews(statusId: Int)
}

@Dao
interface StatusReplyDao {
    @Query("SELECT * FROM status_replies WHERE (senderPhone = :p1 AND receiverPhone = :p2) OR (senderPhone = :p2 AND receiverPhone = :p1) ORDER BY timestamp ASC")
    fun getChatFlow(p1: String, p2: String): Flow<List<StatusReply>>

    @Query("SELECT * FROM status_replies ORDER BY timestamp DESC")
    fun getAllRepliesFlow(): Flow<List<StatusReply>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReply(reply: StatusReply): Long
}
