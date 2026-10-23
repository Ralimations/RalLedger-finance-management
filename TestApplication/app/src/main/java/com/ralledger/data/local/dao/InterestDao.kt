package com.ralledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ralledger.data.local.entity.InterestCreditEntity
import com.ralledger.data.local.entity.InterestProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterestDao {
    @Query("SELECT * FROM interest_products ORDER BY isArchived ASC, maturityDate ASC, createdAt DESC")
    fun getProductsFlow(): Flow<List<InterestProductEntity>>

    @Query("SELECT * FROM interest_products ORDER BY id ASC")
    suspend fun getAllProducts(): List<InterestProductEntity>

    @Query("SELECT * FROM interest_products WHERE id = :id")
    suspend fun getProductById(id: String): InterestProductEntity?

    @Query("SELECT * FROM interest_products WHERE accountId = :accountId LIMIT 1")
    suspend fun getProductForAccount(accountId: String): InterestProductEntity?

    @Query("SELECT COUNT(*) FROM interest_credits WHERE productId = :productId")
    suspend fun countCredits(productId: String): Long

    @Query("SELECT COUNT(*) FROM interest_products WHERE accountId = :accountId")
    suspend fun countProductsForAccount(accountId: String): Long

    @Query("SELECT COUNT(*) FROM interest_credits WHERE taxExpenseCategoryId = :categoryId")
    suspend fun countCreditsForTaxCategory(categoryId: String): Long

    @Query("UPDATE interest_credits SET taxExpenseCategoryId = :targetId WHERE taxExpenseCategoryId = :sourceId")
    suspend fun reassignTaxCategory(sourceId: String, targetId: String)

    @Query("SELECT * FROM interest_credits ORDER BY creditedAt DESC, id ASC")
    fun getCreditsFlow(): Flow<List<InterestCreditEntity>>

    @Query("SELECT * FROM interest_credits ORDER BY id ASC")
    suspend fun getAllCredits(): List<InterestCreditEntity>

    @Query("SELECT * FROM interest_credits WHERE journalEntryId = :journalEntryId LIMIT 1")
    suspend fun getCreditByJournalEntry(journalEntryId: String): InterestCreditEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: InterestProductEntity)

    @Update
    suspend fun updateProduct(product: InterestProductEntity)

    @Query("DELETE FROM interest_products WHERE id = :id")
    suspend fun deleteProduct(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCredit(credit: InterestCreditEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProducts(products: List<InterestProductEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCredits(credits: List<InterestCreditEntity>)

    @Update
    suspend fun updateCredit(credit: InterestCreditEntity)

    @Query("DELETE FROM interest_credits")
    suspend fun clearCredits()

    @Query("DELETE FROM interest_products")
    suspend fun clearProducts()
}
