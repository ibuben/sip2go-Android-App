package uz.ex.sip2go.contacts

data class ContactMatch(
    val name: String,
    val contactId: Long,
)

data class PhoneContact(
    val id: Long,
    val name: String,
    val number: String,
    val normalizedDigits: String,
)
