package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "users")
data class User(
    @PrimaryKey val phoneNumber: String,
    val name: String,
    val avatarEmoji: String,
    val avatarColorHex: String,
    val bio: String = "Listening...",
    val isCurrentUser: Boolean = false
) : Serializable

@Entity(tableName = "contacts")
data class ContactRelation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val ownerPhone: String, // The user whose contact book this resides in
    val contactPhone: String, // The phone number of the contact
    val contactNickName: String // The name they save them as
) : Serializable

@Entity(tableName = "statuses")
data class StatusStory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val creatorPhone: String,
    val content: String,
    val backgroundType: String = "gradient", // "gradient" or "solid" or "dark"
    val startColorHex: String = "#FF512F",
    val endColorHex: String = "#DD2476",
    val textColorHex: String = "#FFFFFF",
    val fontFamily: String = "Serif", // "Serif", "Sans", "Cursive", "Mono"
    val emojiSticker: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val views: Int = 0
) : Serializable

@Entity(tableName = "status_replies")
data class StatusReply(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val senderPhone: String,
    val receiverPhone: String,
    val originalStatusId: Int,
    val replyMessage: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
) : Serializable
