/*
 * Mesh Rider Wave - Contact Repository Implementation
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * In-memory storage with JSON persistence (like Meshenger)
 */

package com.doodlelabs.meshriderwave.data.repository

import android.content.Context
import android.util.Base64
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ContactRepository {

    private val mutex = Mutex()
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    override val contacts: Flow<List<Contact>> = _contacts

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val contactsFile: File
        get() = File(context.filesDir, "contacts.json")

    init {
        loadContacts()
    }

    override suspend fun addContact(contact: Contact) {
        mutex.withLock {
            val current = _contacts.value.toMutableList()
            // Replace if exists
            current.removeAll { it.publicKey.contentEquals(contact.publicKey) }
            current.add(contact)
            _contacts.value = current
            saveContacts()
        }
    }

    override suspend fun updateContact(contact: Contact) {
        addContact(contact) // Same logic
    }

    override suspend fun deleteContact(publicKey: ByteArray) {
        mutex.withLock {
            _contacts.value = _contacts.value.filter { !it.publicKey.contentEquals(publicKey) }
            saveContacts()
        }
    }

    override suspend fun getContacts(): List<Contact> {
        return _contacts.value
    }

    override suspend fun getContactByPublicKey(publicKey: ByteArray): Contact? {
        return _contacts.value.find { it.publicKey.contentEquals(publicKey) }
    }

    override suspend fun getContactByDeviceId(deviceId: String): Contact? {
        return _contacts.value.find { contact ->
            contact.deviceId.equals(deviceId, ignoreCase = true)
        }
    }

    override suspend fun updateLastSeen(publicKey: ByteArray, address: String?) {
        mutex.withLock {
            val current = _contacts.value.toMutableList()
            val index = current.indexOfFirst { it.publicKey.contentEquals(publicKey) }
            if (index >= 0) {
                current[index] = current[index].copy(
                    lastSeenAt = System.currentTimeMillis(),
                    lastWorkingAddress = address ?: current[index].lastWorkingAddress
                )
                _contacts.value = current
                saveContacts()
            }
        }
    }

    override suspend fun setBlocked(publicKey: ByteArray, blocked: Boolean) {
        mutex.withLock {
            val current = _contacts.value.toMutableList()
            val index = current.indexOfFirst { it.publicKey.contentEquals(publicKey) }
            if (index >= 0) {
                current[index] = current[index].copy(blocked = blocked)
                _contacts.value = current
                saveContacts()
            }
        }
    }

    private fun loadContacts() {
        try {
            if (contactsFile.exists()) {
                val jsonStr = contactsFile.readText()
                val contactDtos = json.decodeFromString<List<ContactDto>>(jsonStr)
                _contacts.value = contactDtos.mapNotNull { it.toContact() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveContacts() {
        try {
            val dtos = _contacts.value.map { ContactDto.fromContact(it) }
            contactsFile.writeText(json.encodeToString(dtos))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * DTO for JSON serialization
     */
    @kotlinx.serialization.Serializable
    private data class ContactDto(
        val publicKey: String,  // Base64
        val name: String,
        val addresses: List<String>,
        val blocked: Boolean = false,
        val createdAt: Long,
        val lastSeenAt: Long? = null,
        val lastWorkingAddress: String? = null
    ) {
        fun toContact(): Contact? {
            return try {
                Contact(
                    publicKey = Base64.decode(publicKey, Base64.NO_WRAP),
                    name = name,
                    addresses = addresses,
                    blocked = blocked,
                    createdAt = createdAt,
                    lastSeenAt = lastSeenAt,
                    lastWorkingAddress = lastWorkingAddress
                )
            } catch (e: Exception) {
                null
            }
        }

        companion object {
            fun fromContact(contact: Contact): ContactDto {
                return ContactDto(
                    publicKey = Base64.encodeToString(contact.publicKey, Base64.NO_WRAP),
                    name = contact.name,
                    addresses = contact.addresses,
                    blocked = contact.blocked,
                    createdAt = contact.createdAt,
                    lastSeenAt = contact.lastSeenAt,
                    lastWorkingAddress = contact.lastWorkingAddress
                )
            }
        }
    }
}
