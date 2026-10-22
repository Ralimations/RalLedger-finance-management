package com.ralledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ralledger.domain.model.JournalEntry
import com.ralledger.domain.model.JournalLine
import com.ralledger.domain.model.LineType
import com.ralledger.domain.model.Money

@Entity(tableName = "journal_entries")
data class JournalEntryEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val description: String,
    val transactionType: String,
    val amountCentavos: Long,
    val feeCentavos: Long,
    val primaryAccountId: String,
    val destinationAccountId: String?,
    val categoryId: String?,
    val counterparty: String,
    val notes: String
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["journalEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["journalEntryId"]),
        Index(value = ["accountId"])
    ]
)
data class JournalLineEntity(
    @PrimaryKey val id: String,
    val journalEntryId: String,
    val accountId: String,
    val lineType: String, // LineType.name
    val amountCentavos: Long
) {
    fun toDomainModel(): JournalLine {
        return JournalLine(
            id = id,
            journalEntryId = journalEntryId,
            accountId = accountId,
            lineType = LineType.valueOf(lineType),
            amount = Money(amountCentavos)
        )
    }
}
