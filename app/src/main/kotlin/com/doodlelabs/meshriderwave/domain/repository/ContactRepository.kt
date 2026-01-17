/*
 * Mesh Rider Wave - Contact Repository Interface
 * Copyright (C) 2024-2026 Jabbir Basha P. All Rights Reserved.
 */

package com.doodlelabs.meshriderwave.domain.repository

import com.doodlelabs.meshriderwave.domain.model.Contact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    val contacts: Flow<List<Contact>>

    suspend fun addContact(contact: Contact)
    suspend fun updateContact(contact: Contact)
    suspend fun deleteContact(publicKey: ByteArray)
    suspend fun getContacts(): List<Contact>
    suspend fun getContactByPublicKey(publicKey: ByteArray): Contact?
    suspend fun getContactByDeviceId(deviceId: String): Contact?
    suspend fun updateLastSeen(publicKey: ByteArray, address: String?)
    suspend fun setBlocked(publicKey: ByteArray, blocked: Boolean)
}
