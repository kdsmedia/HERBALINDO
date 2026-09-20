package com.altomedia.herbalindo.data

import com.altomedia.herbalindo.Config
import com.altomedia.herbalindo.data.model.OpResult
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * Every callable that mutates value (points, balance, referral bonus, stock, order
 * state) lives behind this object. The corresponding Firestore rules deny client
 * writes to those fields, so these functions are the only way to change them.
 */
object Api {

    private val functions: FirebaseFunctions by lazy {
        FirebaseFunctions.getInstance(Config.FUNCTIONS_REGION)
    }

    private suspend fun call(name: String, payload: Map<String, Any?>): OpResult<Map<String, Any?>> =
        try {
            val result = functions.getHttpsCallable(name).call(payload).await()
            @Suppress("UNCHECKED_CAST")
            val data = (result.data as? Map<String, Any?>) ?: emptyMap()
            OpResult.Success(data)
        } catch (t: Throwable) {
            OpResult.Failure(t)
        }

    // ------------------------------------------------------------ registration

    suspend fun registerProfile(
        name: String,
        email: String,
        phone: String,
        referralId: String,
    ): OpResult<Map<String, Any?>> = call(
        "registerProfile",
        mapOf(
            "name" to name,
            "email" to email,
            "phone" to phone,
            "referralId" to referralId,
        )
    )

    suspend fun validateReferralId(referralId: String): OpResult<Map<String, Any?>> =
        call("validateReferralId", mapOf("referralId" to referralId))

    // -------------------------------------------------------------- daily tasks

    suspend fun dailyCheckIn(): OpResult<Map<String, Any?>> = call("dailyCheckIn", emptyMap())

    suspend fun rewardAd(adUnit: String): OpResult<Map<String, Any?>> =
        call("rewardAd", mapOf("adUnit" to adUnit, "clientConfirmed" to true))

    // ------------------------------------------------------------------- orders

    suspend fun createOrder(
        items: List<Map<String, Any?>>,
        recipientName: String,
        phone: String,
        address: String,
        city: String,
        postalCode: String,
        note: String,
    ): OpResult<Map<String, Any?>> = call(
        "createOrder",
        mapOf(
            "items" to items,
            "shippingAddress" to mapOf(
                "recipientName" to recipientName,
                "phone" to phone,
                "address" to address,
                "city" to city,
                "postalCode" to postalCode,
            ),
            "note" to note,
        )
    )

    suspend fun submitPaymentProof(orderId: String): OpResult<Map<String, Any?>> =
        call("submitPaymentProof", mapOf("orderId" to orderId))

    // -------------------------------------------------------------- withdrawals

    suspend fun requestWithdrawal(
        amount: Long,
        method: String,
        destination: String,
        holderName: String,
    ): OpResult<Map<String, Any?>> = call(
        "requestWithdrawal",
        mapOf(
            "amount" to amount,
            "method" to method,
            "destination" to destination,
            "holderName" to holderName,
        )
    )

    // -------------------------------------------------------------- admin calls

    suspend fun confirmPayment(orderId: String): OpResult<Map<String, Any?>> =
        call("confirmPayment", mapOf("orderId" to orderId))

    suspend fun updateOrderStatus(
        orderId: String,
        status: String,
        courier: String? = null,
        trackingNumber: String? = null,
        note: String? = null,
    ): OpResult<Map<String, Any?>> = call(
        "updateOrderStatus",
        mapOf(
            "orderId" to orderId,
            "status" to status,
            "courier" to courier,
            "trackingNumber" to trackingNumber,
            "note" to note,
        ).filterValues { it != null }
    )

    suspend fun refundOrder(orderId: String, reason: String): OpResult<Map<String, Any?>> =
        call("refundOrder", mapOf("orderId" to orderId, "reason" to reason))

    suspend fun processWithdrawal(
        withdrawalId: String,
        status: String,
        note: String,
    ): OpResult<Map<String, Any?>> = call(
        "processWithdrawal",
        mapOf("withdrawalId" to withdrawalId, "status" to status, "note" to note)
    )

    suspend fun adjustUserPoints(
        userId: String,
        delta: Long,
        reason: String,
    ): OpResult<Map<String, Any?>> =
        call("adjustUserPoints", mapOf("userId" to userId, "delta" to delta, "reason" to reason))

    suspend fun adjustStock(
        productId: String,
        delta: Long,
        reason: String,
    ): OpResult<Map<String, Any?>> =
        call("adjustStock", mapOf("productId" to productId, "delta" to delta, "reason" to reason))

    suspend fun saveProduct(fields: Map<String, Any?>): OpResult<Map<String, Any?>> =
        call("saveProduct", fields)

    suspend fun setProductStatus(productId: String, status: String): OpResult<Map<String, Any?>> =
        call("setProductStatus", mapOf("productId" to productId, "status" to status))

    suspend fun saveSettings(fields: Map<String, Any?>): OpResult<Map<String, Any?>> =
        call("saveSettings", fields)

    suspend fun setUserStatus(userId: String, status: String): OpResult<Map<String, Any?>> =
        call("setUserStatus", mapOf("userId" to userId, "status" to status))

    suspend fun claimFirstAdmin(): OpResult<Map<String, Any?>> = call("claimFirstAdmin", emptyMap())

    /** Converts a FirebaseFunctionsException into a message worth showing a user. */
    fun message(error: Throwable): String {
        val ffe = error as? FirebaseFunctionsException
        return when {
            ffe?.message?.isNotBlank() == true -> ffe.message!!
            error.message?.isNotBlank() == true -> error.message!!
            else -> "Terjadi kesalahan. Coba lagi."
        }
    }
}