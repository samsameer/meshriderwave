/*
 * Mesh Rider Wave - Contact Repository Implementation
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 *
 * Military-Grade Contact Storage with Address Registry (Jan 2026)
 *
 * Features:
 * - In-memory cache with JSON persistence
 * - Full addressRegistry support with network type classification
 * - Backward compatible with legacy contacts (auto-migration)
 * - Thread-safe with Mutex
 *
 * Storage Format:
 * - V1 (Legacy): publicKey, name, addresses[]
 * - V2 (Current): publicKey, name, addressRegistry[], addresses[] (for compat)
 */

package com.doodlelabs.meshriderwave.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import com.doodlelabs.meshriderwave.domain.model.AddressRecord
import com.doodlelabs.meshriderwave.domain.model.Contact
import com.doodlelabs.meshriderwave.domain.model.DiscoverySource
import com.doodlelabs.meshriderwave.domain.model.NetworkType
import com.doodlelabs.meshriderwave.domain.repository.ContactRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ContactRepository {
    companion object {
        private const val TAG = "ContactRepository"
        private const val CONTACTS_FILE = "contacts.json"
        private const val BACKUP_FILE = "contacts_backup.json"
    }

    private val mutex = Mutex()
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    override val contacts: Flow<List<Contact>> = _contacts

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val contactsFile: File
        get() = File(context.filesDir, CONTACTS_FILE)

    private val backupFile: File
        get() = File(context.filesDir, BACKUP_FILE)

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
                val contacts = contactDtos.mapNotNull { dto ->
                    dto.toContact().also { contact ->
                        if (contact == null) {
                            Log.w(TAG, "Failed to parse contact: ${dto.name}")
                        }
                    }
                }
                _contacts.value = contacts
                Log.i(TAG, "Loaded ${contacts.size} contacts")

                // Check for and perform migration if needed
                val needsMigration = contacts.any { contact ->
                    contact.addressRegistry.isEmpty() && contact.addresses.isNotEmpty()
                }
                if (needsMigration) {
                    Log.i(TAG, "Migrating legacy contacts to addressRegistry format")
                    migrateContacts()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts", e)
            // Try loading from backup
            loadFromBackup()
        }
    }

    private fun loadFromBackup() {
        try {
            if (backupFile.exists()) {
                Log.i(TAG, "Attempting to load from backup")
                val jsonStr = backupFile.readText()
                val contactDtos = json.decodeFromString<List<ContactDto>>(jsonStr)
                _contacts.value = contactDtos.mapNotNull { it.toContact() }
                Log.i(TAG, "Loaded ${_contacts.value.size} contacts from backup")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts from backup", e)
        }
    }

    private fun saveContacts() {
        try {
            // Create backup before saving
            if (contactsFile.exists()) {
                contactsFile.copyTo(backupFile, overwrite = true)
            }

            val dtos = _contacts.value.map { ContactDto.fromContact(it) }
            contactsFile.writeText(json.encodeToString(dtos))
            Log.d(TAG, "Saved ${dtos.size} contacts")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save contacts", e)
        }
    }

    /**
     * Migrate legacy contacts to new addressRegistry format.
     * Converts plain addresses to AddressRecords with network type classification.
     */
    private fun migrateContacts() {
        val migratedContacts = _contacts.value.map { contact ->
            @Suppress("DEPRECATION")
            if (contact.addressRegistry.isEmpty() && contact.addresses.isNotEmpty()) {
                // Convert legacy addresses to address records
                val registry = contact.addresses.map { addr ->
                    AddressRecord.fromAddress(address = addr, source = DiscoverySource.MANUAL)
                }
                contact.copy(addressRegistry = registry)
            } else {
                contact
            }
        }
        _contacts.value = migratedContacts
        saveContacts()
        Log.i(TAG, "Migration complete")
    }

    // =========================================================================
    // DTOs FOR JSON SERIALIZATION
    // =========================================================================

    /**
     * DTO for Contact JSON serialization.
     *
     * Supports both legacy (V1) and new (V2) formats:
     * - V1: addresses[] only (will be migrated to addressRegistry)
     * - V2: addressRegistry[] + addresses[] (for backward compatibility)
     */
    @Serializable
    private data class ContactDto(
        val publicKey: String,  // Base64 encoded
        val name: String,
        val addressRegistry: List<AddressRecordDto> = emptyList(),
        val addresses: List<String> = emptyList(),  // Legacy, kept for backward compat
        val blocked: Boolean = false,
        val createdAt: Long = System.currentTimeMillis(),
        val lastSeenAt: Long? = null,
        val lastWorkingAddress: String? = null
    ) {
        /**
         * Convert DTO to domain Contact model.
         */
        fun toContact(): Contact? {
            return try {
                val decodedKey = Base64.decode(publicKey, Base64.NO_WRAP)
                if (decodedKey.size != Contact.PUBLIC_KEY_SIZE) {
                    Log.w(TAG, "Invalid public key size: ${decodedKey.size}")
                    return null
                }

                @Suppress("DEPRECATION")
                Contact(
                    publicKey = decodedKey,
                    name = name,
                    addressRegistry = addressRegistry.map { it.toAddressRecord() },
                    addresses = addresses,  // Keep for backward compatibility
                    blocked = blocked,
                    createdAt = createdAt,
                    lastSeenAt = lastSeenAt,
                    lastWorkingAddress = lastWorkingAddress
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to convert ContactDto to Contact: ${e.message}")
                null
            }
        }

        companion object {
            /**
             * Create DTO from domain Contact model.
             */
            fun fromContact(contact: Contact): ContactDto {
                @Suppress("DEPRECATION")
                return ContactDto(
                    publicKey = Base64.encodeToString(contact.publicKey, Base64.NO_WRAP),
                    name = contact.name,
                    addressRegistry = contact.addressRegistry.map { AddressRecordDto.fromAddressRecord(it) },
                    addresses = contact.addresses,  // Keep for backward compatibility
                    blocked = contact.blocked,
                    createdAt = contact.createdAt,
                    lastSeenAt = contact.lastSeenAt,
                    lastWorkingAddress = contact.lastWorkingAddress
                )
            }
        }
    }

    /**
     * DTO for AddressRecord JSON serialization.
     */
    @Serializable
    private data class AddressRecordDto(
        val address: String,
        val networkType: String,  // NetworkType.name
        val port: Int? = null,
        val discoveredAt: Long = System.currentTimeMillis(),
        val lastSuccessAt: Long? = null,
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val isActive: Boolean = true,
        val source: String = DiscoverySource.UNKNOWN.name
    ) {
        /**
         * Convert DTO to domain AddressRecord.
         */
        fun toAddressRecord(): AddressRecord {
            return AddressRecord(
                address = address,
                networkType = try {
                    NetworkType.valueOf(networkType)
                } catch (e: Exception) {
                    NetworkType.UNKNOWN
                },
                port = port,
                discoveredAt = discoveredAt,
                lastSuccessAt = lastSuccessAt,
                successCount = successCount,
                failureCount = failureCount,
                isActive = isActive,
                source = try {
                    DiscoverySource.valueOf(source)
                } catch (e: Exception) {
                    DiscoverySource.UNKNOWN
                }
            )
        }

        companion object {
            /**
             * Create DTO from domain AddressRecord.
             */
            fun fromAddressRecord(record: AddressRecord): AddressRecordDto {
                return AddressRecordDto(
                    address = record.address,
                    networkType = record.networkType.name,
                    port = record.port,
                    discoveredAt = record.discoveredAt,
                    lastSuccessAt = record.lastSuccessAt,
                    successCount = record.successCount,
                    failureCount = record.failureCount,
                    isActive = record.isActive,
                    source = record.source.name
                )
            }
        }
    }
}
