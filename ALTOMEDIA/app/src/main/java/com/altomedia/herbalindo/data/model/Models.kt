package com.altomedia.herbalindo.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/** Result of an async operation, so the UI can surface real errors instead of failing silently. */
sealed class OpResult<out T> {
    data class Success<T>(val data: T) : OpResult<T>()
    data class Failure(val error: Throwable) : OpResult<Nothing>()
}

enum class UserRole { MEMBER, ADMIN }
enum class AccountStatus { ACTIVE, INACTIVE, SUSPENDED }
enum class OrderStatus {
    PENDING, WAITING_PAYMENT, PAID, PROCESSING, SHIPPED, DELIVERED, COMPLETED, CANCELLED, REFUNDED
}
enum class PaymentStatus { UNPAID, WAITING_CONFIRMATION, PAID, FAILED, REFUNDED }
enum class ReferralStatus { PENDING, VERIFIED, CANCELLED }
enum class WithdrawalStatus { PENDING, REVIEW, APPROVED, REJECTED, PAID }

data class User(
    @DocumentId val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val referralId: String = "",
    val referredBy: String? = null,
    val role: String = UserRole.MEMBER.name,
    val status: String = AccountStatus.ACTIVE.name,
    val verified: Boolean = false,
    val points: Long = 0L,
    val balance: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
) {
    @get:Exclude val isAdmin: Boolean get() = role == UserRole.ADMIN.name
    @get:Exclude val isActive: Boolean get() = status == AccountStatus.ACTIVE.name

    companion object {
        const val POINTS_PER_RUPIAH = 10_000L
    }
}

data class Product(
    @DocumentId val productId: String = "",
    val name: String = "",
    val sku: String = "",
    val categoryId: String = "",
    val description: String = "",
    val composition: String = "",
    val usage: String = "",
    val warning: String = "",
    val price: Long = 0L,
    val promoPrice: Long = 0L,
    val stock: Long = 0L,
    val minStock: Long = 0L,
    val weight: Long = 0L,
    val imageUrl: String = "",
    val points: Long = 0L,
    val status: String = "ACTIVE",
    val promoActive: Boolean = false,
    val promoStart: Date? = null,
    val promoEnd: Date? = null,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
) {
    @get:Exclude val effectivePrice: Long
        get() = if (promoActive && promoPrice in 1 until price) promoPrice else price
    @get:Exclude val isActive: Boolean get() = status == "ACTIVE"
    @get:Exclude val inStock: Boolean get() = stock > 0
}

data class Category(
    @DocumentId val categoryId: String = "",
    val name: String = "",
    val order: Long = 0L,
)

data class CartLine(
    val productId: String = "",
    val name: String = "",
    val imageUrl: String = "",
    val price: Long = 0L,
    val points: Long = 0L,
    val qty: Long = 1L,
    val weight: Long = 0L,
) {
    @get:Exclude val subtotal: Long get() = price * qty
    @get:Exclude val pointsEarned: Long get() = points * qty
    @get:Exclude val totalWeight: Long get() = weight * qty
}

data class ShippingAddress(
    val recipientName: String = "",
    val phone: String = "",
    val address: String = "",
    val city: String = "",
    val postalCode: String = "",
)

data class Order(
    @DocumentId val orderId: String = "",
    val userId: String = "",
    val orderNumber: String = "",
    val items: List<OrderItem> = emptyList(),
    val subtotal: Long = 0L,
    val shippingCost: Long = 0L,
    val total: Long = 0L,
    val pointsEarned: Long = 0L,
    val paymentStatus: String = PaymentStatus.UNPAID.name,
    val paymentMethod: String = "QRIS",
    val orderStatus: String = OrderStatus.PENDING.name,
    val shippingCourier: String = "",
    val trackingNumber: String = "",
    val shippingAddress: ShippingAddress = ShippingAddress(),
    val note: String = "",
    val qrPayload: String = "",
    val referralBonusApplied: Boolean = false,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
)

data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val price: Long = 0L,
    val qty: Long = 0L,
    val points: Long = 0L,
) {
    @get:Exclude val subtotal: Long get() = price * qty
}

data class Payment(
    @DocumentId val paymentId: String = "",
    val orderId: String = "",
    val userId: String = "",
    val amount: Long = 0L,
    val method: String = "QRIS",
    val payload: String = "",
    val status: String = PaymentStatus.UNPAID.name,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val paidAt: Date? = null,
)

data class PointsLedger(
    @DocumentId val entryId: String = "",
    val userId: String = "",
    val delta: Long = 0L,
    val reason: String = "",
    val refId: String = "",
    val balanceAfter: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null,
)

data class Referral(
    @DocumentId val referralId: String = "",
    val inviterId: String = "",
    val invitedUserId: String = "",
    val status: String = ReferralStatus.PENDING.name,
    val qualifyingOrderId: String = "",
    val bonusPoints: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val verifiedAt: Date? = null,
)

data class AdReward(
    @DocumentId val rewardId: String = "",
    val userId: String = "",
    val dayKey: String = "",
    val points: Long = 0L,
    val adUnit: String = "",
    @ServerTimestamp val createdAt: Date? = null,
)

data class DailyTask(
    @DocumentId val taskId: String = "",
    val userId: String = "",
    val dayKey: String = "",
    val checkedIn: Boolean = false,
    val adsWatched: Long = 0L,
    val checkinPoints: Long = 0L,
    @ServerTimestamp val updatedAt: Date? = null,
)

data class Withdrawal(
    @DocumentId val withdrawalId: String = "",
    val userId: String = "",
    val amount: Long = 0L,
    val method: String = "",
    val destination: String = "",
    val holderName: String = "",
    val status: String = WithdrawalStatus.PENDING.name,
    val adminId: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val processedAt: Date? = null,
)

data class StockMovement(
    @DocumentId val movementId: String = "",
    val productId: String = "",
    val delta: Long = 0L,
    val reason: String = "",
    val adminId: String = "",
    val stockAfter: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null,
)

data class AdminLog(
    @DocumentId val logId: String = "",
    val adminId: String = "",
    val action: String = "",
    val target: String = "",
    val amount: Long = 0L,
    val detail: String = "",
    @ServerTimestamp val timestamp: Date? = null,
)

/** Singleton document at settings/app holding every tunable business rule. */
data class AppSettings(
    val checkinPoints: Long = 10L,
    val pointsPerAd: Long = 5L,
    val maxAdsPerDay: Long = 20L,
    val referralBonusPoints: Long = 5_000L,
    val minWithdrawalRupiah: Long = 50_000L,
    val pointsPerRupiah: Long = 10_000L,
    val maxWithdrawalsPerDay: Long = 1L,
    val shippingCostFlat: Long = 15_000L,
    val freeShippingMin: Long = 0L,
    val referralRequiresPurchase: Boolean = true,
    val referralMinOrderTotal: Long = 0L,
    val maintenanceMode: Boolean = false,
    val supportEmail: String = "altomediaindonesia@gmail.com",
    val adminWhatsapp: String = "",
    val updatedAt: Date? = null,
) {
    @get:Exclude val minWithdrawalPoints: Long
        get() = minWithdrawalRupiah * pointsPerRupiah / 1_000L
}