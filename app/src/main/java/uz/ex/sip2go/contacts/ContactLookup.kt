package uz.ex.sip2go.contacts

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.PhoneLookup
import android.util.Log
import androidx.core.content.ContextCompat

object ContactLookup {
    private const val TAG = "ContactLookup"

    fun lookup(context: Context, rawNumber: String): ContactMatch? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val number = rawNumber.trim()
        if (number.isEmpty()) return null
        val digits = digitsOnly(number)
        if (digits.isEmpty()) return null

        if (digits.length >= 7) {
            lookupDirect(context, number)?.let { return it }
        }
        return lookupByDigits(context, digits)
    }

    fun lookupName(context: Context, rawNumber: String): String? = lookup(context, rawNumber)?.name

    fun loadContactPhoto(context: Context, contactId: Long): Bitmap? {
        if (contactId <= 0L) return null
        val resolver = context.contentResolver

        try {
            val displayPhotoUri = ContentUris.withAppendedId(
                ContactsContract.DisplayPhoto.CONTENT_URI,
                contactId,
            )
            resolver.openInputStream(displayPhotoUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { return it }
            }
        } catch (e: Exception) {
            Log.d(TAG, "DisplayPhoto failed for $contactId: ${e.message}")
        }

        try {
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            for (preferHighres in listOf(true, false)) {
                ContactsContract.Contacts.openContactPhotoInputStream(
                    resolver,
                    contactUri,
                    preferHighres,
                )?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { return it }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "openContactPhotoInputStream failed for $contactId: ${e.message}")
        }

        try {
            val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            resolver.query(
                contactUri,
                arrayOf(ContactsContract.Contacts.PHOTO_URI),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val photoUri = cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                    if (photoUri != null) {
                        resolver.openInputStream(photoUri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)?.let { return it }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "PHOTO_URI load failed for $contactId: ${e.message}")
        }

        return null
    }

    private fun lookupDirect(context: Context, number: String): ContactMatch? {
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(
                PhoneLookup.DISPLAY_NAME,
                PhoneLookup._ID,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: return null
                val contactId = cursor.getLong(1)
                if (contactId <= 0L) return null
                return ContactMatch(name = name, contactId = contactId)
            }
        }
        return null
    }

    private fun lookupByDigits(context: Context, digits: String): ContactMatch? {
        if (digits.isEmpty()) return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            var suffixMatch: ContactMatch? = null
            while (cursor.moveToNext()) {
                val contactDigits = digitsOnly(cursor.getString(numIdx) ?: continue)
                val name = cursor.getString(nameIdx) ?: continue
                val contactId = cursor.getLong(idIdx)
                if (contactDigits == digits) {
                    return ContactMatch(name, contactId)
                }
                if (digits.length >= 7 && contactDigits.endsWith(digits)) {
                    suffixMatch = ContactMatch(name, contactId)
                }
            }
            return suffixMatch
        }
        return null
    }

    fun digitsOnly(value: String): String = value.filter { it.isDigit() }

    fun loadContacts(context: Context): List<PhoneContact> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY,
        )
        val seen = mutableSetOf<String>()
        val contacts = mutableListOf<PhoneContact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.SORT_KEY_PRIMARY,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx)?.trim().orEmpty()
                val number = cursor.getString(numIdx)?.trim().orEmpty()
                if (name.isEmpty() || number.isEmpty()) continue
                val contactId = cursor.getLong(idIdx)
                val key = "$contactId:$number"
                if (!seen.add(key)) continue
                contacts.add(
                    PhoneContact(
                        id = contactId,
                        name = name,
                        number = number,
                        normalizedDigits = digitsOnly(number),
                    ),
                )
            }
        }
        return contacts
    }

    fun initials(label: String): String {
        return label.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { part -> part.first().uppercaseChar().toString() }
            .ifEmpty { "?" }
    }
}
