package com.sentinel.guardian.ui.screens.features

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.sentinel.guardian.Contact
import com.sentinel.guardian.ContactCategory
import com.sentinel.guardian.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ContactsFeatures {

    // این تابع به صورت معلق (suspend) تعریف شده تا در یک کوروتین اجرا شود
    suspend fun getAllContacts(context: Context): List<Contact> {
        val contactsList = mutableListOf<Contact>()
        // اجرای کوئری در یک نخ پس‌زمینه (IO)
        withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )

            cursor?.use { // استفاده از use برای بستن خودکار cursor
                val idIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex =
                    it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)


                while (it.moveToNext()) {
                    // بررسی نامعتبر نبودن ایندکس‌ها
                    if (nameIndex < 0 || numberIndex < 0) continue

                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)

                    contactsList.add(Contact(id, name, number.replace("\\s+".toRegex(), "")))
                }
            }
        }
        return contactsList
    }

    @SuppressLint("Range")
    fun loadContacts(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Read contacts permission not granted.")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val resolver = context.contentResolver
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            val tempContacts = mutableListOf<Contact>()
            cursor?.use {
                while (it.moveToNext()) {
                    val id =
                        it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
                    val name =
                        it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val phoneNumber =
                        it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    tempContacts.add(Contact(id, name, phoneNumber.replace("\\s+".toRegex(), "")))
                }
            }
            withContext(Dispatchers.Main) {
//                contactsList.clear()
//                contactsList.addAll(tempContacts.distinctBy { it.phoneNumber })
//                Log.d(TAG, "Loaded ${contactsList.size} contacts.")
            }
        }

    }

    fun contactsList(category: ContactCategory? = null): List<Contact> {
        TODO("")
    }

    suspend fun getEmergencyContacts(context: Context): List<Contact> {
//        return contactsList().filter { it.category == ContactCategory.EMERGENCY }
        return getAllContacts(context)
    }

}