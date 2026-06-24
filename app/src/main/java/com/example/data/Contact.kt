package com.example.data

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

data class Contact(
    val id: String,
    val name: String,
    val phoneNumber: String,
    val isSimulated: Boolean = false
) {
    val initials: String
        get() = name.split(" ")
            .filter { it.isNotEmpty() }
            .map { it.first().uppercase() }
            .take(2)
            .joinToString("")
}

object ContactLoader {
    val simulatedContacts = listOf(
        Contact("sim_1", "Alex Rivers", "+15550100", true),
        Contact("sim_2", "Brooke Vance", "+15550111", true),
        Contact("sim_3", "Chris Sterling", "+15550122", true),
        Contact("sim_4", "Dana Thorne", "+15550133", true),
        Contact("sim_5", "Evan Wright", "+15550144", true)
    )

    fun loadDeviceContacts(context: Context): List<Contact> {
        val contactsList = mutableListOf<Contact>()
        val resolver = context.contentResolver
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            val cursor = resolver.query(uri, projection, null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")
            cursor?.use {
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext()) {
                    val id = if (idIndex >= 0) it.getString(idIndex) else ""
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val number = if (numberIndex >= 0) it.getString(numberIndex) else ""
                    if (number.isNotEmpty() && contactsList.none { c -> c.phoneNumber == number }) {
                        contactsList.add(Contact(id = id, name = name, phoneNumber = number))
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("ContactLoader", "Contact permission not granted", e)
        } catch (e: Exception) {
            Log.e("ContactLoader", "Error reading contacts", e)
        }

        return contactsList
    }
}
