package com.altomedia.herbalindo.data

import android.content.Context
import com.altomedia.herbalindo.data.model.AdminLog
import com.altomedia.herbalindo.data.model.AppSettings
import com.altomedia.herbalindo.data.model.Category
import com.altomedia.herbalindo.data.model.OpResult
import com.altomedia.herbalindo.data.model.Product
import com.altomedia.herbalindo.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Collection names shared by every data access path. */
object Col {
    const val USERS = "users"
    const val PRODUCTS = "products"
    const val CATEGORIES = "categories"
    const val ORDERS = "orders"
    const val ORDER_ITEMS = "order_items"
    const val PAYMENTS = "payments"
    const val POINTS_LEDGER = "points_ledger"
    const val REFERRALS = "referrals"
    const val AD_REWARDS = "ad_rewards"
    const val DAILY_TASKS = "daily_tasks"
    const val WITHDRAWALS = "withdrawals"
    const val SETTINGS = "settings"
    const val ADMIN_LOGS = "admin_logs"
    const val STOCK_MOVEMENTS = "stock_movements"
}

/**
 * Thin Firestore access layer. Business-rule mutations (points, balance, referrals,
 * stock) are intentionally delegated to Cloud Functions so the client cannot forge them.
 */
object Repository {

    lateinit var db: FirebaseFirestore
        private set

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        db = FirebaseFirestore.getInstance()
    }

    fun context(): Context = appContext

    // ---------------------------------------------------------------- settings

    /** Live app settings; falls back to defaults when the document has not been created yet. */
    fun settingsFlow(): Flow<AppSettings> = callbackFlow {
        val reg = db.collection(Col.SETTINGS).document("app")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(AppSettings())
                    return@addSnapshotListener
                }
                trySend(snap?.toObject(AppSettings::class.java) ?: AppSettings())
            }
        awaitClose { reg.remove() }
    }

    suspend fun getSettings(): AppSettings = try {
        db.collection(Col.SETTINGS).document("app").get().await()
            .toObject(AppSettings::class.java) ?: AppSettings()
    } catch (t: Throwable) {
        AppSettings()
    }

    // ------------------------------------------------------------------- users

    suspend fun getUser(uid: String): User? =
        db.collection(Col.USERS).document(uid).get().await().toObject(User::class.java)

    fun userFlow(uid: String): Flow<User?> = callbackFlow {
        val reg = db.collection(Col.USERS).document(uid)
            .addSnapshotListener { snap, _ ->
                val user = snap?.toObject(User::class.java)
                cacheUser(user)
                trySend(user)
            }
        awaitClose { reg.remove() }
    }

    /** Resolves the inviter document by its public 6-digit referral code. */
    suspend fun findUserByReferralId(referralId: String): User? {
        val snap = db.collection(Col.USERS)
            .whereEqualTo("referralId", referralId)
            .limit(1)
            .get().await()
        return snap.documents.firstOrNull()?.toObject(User::class.java)
    }

    suspend fun listUsers(limit: Long = 200): List<User> =
        db.collection(Col.USERS).orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit).get().await().documents.mapNotNull { it.toObject(User::class.java) }

    /** Admin roster; sorted newest first because createdAt ordering needs an index. */
    fun usersFlow(): Flow<List<User>> = callbackFlow {
        val reg = db.collection(Col.USERS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents?.mapNotNull { it.toObject(User::class.java) }
                        ?.sortedByDescending { it.createdAt ?: java.util.Date(0) }
                        ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    /** A single member document for the admin detail screen. */
    suspend fun getUserOnce(uid: String): User? = getUser(uid)

    suspend fun searchUsers(query: String): List<User> {
        val q = query.trim()
        if (q.isEmpty()) return listUsers(100)
        val byReferral = db.collection(Col.USERS).whereEqualTo("referralId", q).get().await()
            .documents.mapNotNull { it.toObject(User::class.java) }
        val byPhone = db.collection(Col.USERS).whereEqualTo("phone", q).get().await()
            .documents.mapNotNull { it.toObject(User::class.java) }
        val byEmail = db.collection(Col.USERS).whereEqualTo("email", q).get().await()
            .documents.mapNotNull { it.toObject(User::class.java) }
        return (byReferral + byPhone + byEmail).distinctBy { it.uid }
    }

    suspend fun setUserStatus(uid: String, status: String) {
        db.collection(Col.USERS).document(uid).update(
            mapOf("status" to status, "updatedAt" to System.currentTimeMillis())
        ).await()
    }

    // ---------------------------------------------------------------- products

    fun productsFlow(): Flow<List<Product>> = callbackFlow {
        val reg = db.collection(Col.PRODUCTS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toObject(Product::class.java) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun activeProductsFlow(): Flow<List<Product>> = callbackFlow {
        val reg = db.collection(Col.PRODUCTS)
            .whereEqualTo("status", "ACTIVE")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snap?.documents?.mapNotNull { it.toObject(Product::class.java) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    fun productFlow(productId: String): Flow<Product?> = callbackFlow {
        val reg = db.collection(Col.PRODUCTS).document(productId)
            .addSnapshotListener { snap, _ -> trySend(snap?.toObject(Product::class.java)) }
        awaitClose { reg.remove() }
    }

    suspend fun getProduct(productId: String): Product? =
        db.collection(Col.PRODUCTS).document(productId).get().await()
            .toObject(Product::class.java)

    suspend fun listProducts(): List<Product> =
        db.collection(Col.PRODUCTS).get().await().documents
            .mapNotNull { it.toObject(Product::class.java) }

    /** Refreshes a single product snapshot, used after stock-changing operations. */
    suspend fun refreshProduct(productId: String): Product? = getProduct(productId)

    fun categoriesFlow(): Flow<List<Category>> = callbackFlow {
        val reg = db.collection(Col.CATEGORIES)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.mapNotNull { it.toObject(Category::class.java) } ?: emptyList())
            }
        awaitClose { reg.remove() }
    }

    // ------------------------------------------------------------------ orders

    fun ordersFlow(userId: String): Flow<List<com.altomedia.herbalindo.data.model.Order>> = callbackFlow {
        val reg = db.collection(Col.ORDERS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents?.mapNotNull { it.toObject(com.altomedia.herbalindo.data.model.Order::class.java) }
                        ?.sortedByDescending { it.createdAt ?: it.updatedAt ?: java.util.Date(0) }
                        ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    /** Admin view: every order, newest first, optionally filtered by status. */
    fun allOrdersFlow(status: String? = null): Flow<List<com.altomedia.herbalindo.data.model.Order>> = callbackFlow {
        val query = db.collection(Col.ORDERS)
            .let { if (status.isNullOrBlank() || status == "ALL") it else it.whereEqualTo("orderStatus", status) }
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(
                snap?.documents?.mapNotNull { it.toObject(com.altomedia.herbalindo.data.model.Order::class.java) }
                    ?.sortedByDescending { it.createdAt ?: java.util.Date(0) }
                    ?: emptyList()
            )
        }
        awaitClose { reg.remove() }
    }

    suspend fun getOrder(orderId: String): com.altomedia.herbalindo.data.model.Order? =
        db.collection(Col.ORDERS).document(orderId).get().await()
            .toObject(com.altomedia.herbalindo.data.model.Order::class.java)

    fun orderFlow(orderId: String): Flow<com.altomedia.herbalindo.data.model.Order?> = callbackFlow {
        val reg = db.collection(Col.ORDERS).document(orderId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(com.altomedia.herbalindo.data.model.Order::class.java))
            }
        awaitClose { reg.remove() }
    }

    // -------------------------------------------------------------- withdrawals

    fun withdrawalsFlow(userId: String): Flow<List<com.altomedia.herbalindo.data.model.Withdrawal>> = callbackFlow {
        val reg = db.collection(Col.WITHDRAWALS)
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents?.mapNotNull {
                        it.toObject(com.altomedia.herbalindo.data.model.Withdrawal::class.java)
                    }?.sortedByDescending { it.createdAt ?: java.util.Date(0) } ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    fun allWithdrawalsFlow(status: String? = null): Flow<List<com.altomedia.herbalindo.data.model.Withdrawal>> = callbackFlow {
        val query = db.collection(Col.WITHDRAWALS)
            .let { if (status.isNullOrBlank() || status == "ALL") it else it.whereEqualTo("status", status) }
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(
                snap?.documents?.mapNotNull {
                    it.toObject(com.altomedia.herbalindo.data.model.Withdrawal::class.java)
                }?.sortedByDescending { it.createdAt ?: java.util.Date(0) } ?: emptyList()
            )
        }
        awaitClose { reg.remove() }
    }

    // ------------------------------------------------------------- points ledger

    fun ledgerFlow(userId: String, limit: Long = 100): Flow<List<com.altomedia.herbalindo.data.model.PointsLedger>> =
        callbackFlow {
            val reg = db.collection(Col.POINTS_LEDGER)
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    trySend(
                        snap?.documents?.mapNotNull {
                            it.toObject(com.altomedia.herbalindo.data.model.PointsLedger::class.java)
                        }?.sortedByDescending { it.createdAt ?: java.util.Date(0) }
                            ?.take(limit.toInt()) ?: emptyList()
                    )
                }
            awaitClose { reg.remove() }
        }

    // ----------------------------------------------------------------- referrals

    fun referralsAsInviterFlow(userId: String): Flow<List<com.altomedia.herbalindo.data.model.Referral>> = callbackFlow {
        val reg = db.collection(Col.REFERRALS)
            .whereEqualTo("inviterId", userId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents?.mapNotNull {
                        it.toObject(com.altomedia.herbalindo.data.model.Referral::class.java)
                    }?.sortedByDescending { it.createdAt ?: java.util.Date(0) } ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    // ---------------------------------------------------------------- daily task

    fun dailyTaskFlow(userId: String, dayKey: String): Flow<com.altomedia.herbalindo.data.model.DailyTask?> =
        callbackFlow {
            val reg = db.collection(Col.DAILY_TASKS).document("${userId}_$dayKey")
                .addSnapshotListener { snap, _ ->
                    trySend(snap?.toObject(com.altomedia.herbalindo.data.model.DailyTask::class.java))
                }
            awaitClose { reg.remove() }
        }

    // ------------------------------------------------------------------- admin

    fun adminLogsFlow(limit: Long = 200): Flow<List<AdminLog>> = callbackFlow {
        val reg = db.collection(Col.ADMIN_LOGS)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents?.mapNotNull { it.toObject(AdminLog::class.java) }
                        ?.sortedByDescending { it.timestamp ?: java.util.Date(0) }
                        ?.take(limit.toInt()) ?: emptyList()
                )
            }
        awaitClose { reg.remove() }
    }

    fun stockMovementsFlow(productId: String? = null): Flow<List<com.altomedia.herbalindo.data.model.StockMovement>> =
        callbackFlow {
            val query = db.collection(Col.STOCK_MOVEMENTS)
                .let { if (productId.isNullOrBlank()) it else it.whereEqualTo("productId", productId) }
            val reg = query.addSnapshotListener { snap, err ->
                if (err != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snap?.documents?.mapNotNull {
                        it.toObject(com.altomedia.herbalindo.data.model.StockMovement::class.java)
                    }?.sortedByDescending { it.createdAt ?: java.util.Date(0) } ?: emptyList()
                )
            }
            awaitClose { reg.remove() }
        }

    // ------------------------------------------------------------ raw helpers

    suspend fun countOf(collection: String): Long =
        db.collection(collection).get().await().size().toLong()

    suspend fun countWhere(collection: String, field: String, value: Any): Long =
        db.collection(collection).whereEqualTo(field, value).get().await().size().toLong()

    /** Wraps a suspending Firestore call so callers can render a real error state. */
    suspend fun <T> attempt(block: suspend () -> T): OpResult<T> = try {
        OpResult.Success(block())
    } catch (t: Throwable) {
        OpResult.Failure(t)
    }

    // ------------------------------------------------------- cached profile

    @Volatile
    private var cachedUser: User? = null

    /** Last successfully loaded profile, used to prefill forms without a network wait. */
    fun userCache(uid: String): User? = cachedUser?.takeIf { it.uid == uid }

    // ------------------------------------------------------- callable bridge

    /**
     * Creates an order through the backend, which re-reads prices and stock.
     * Returns the new order id and number for the payment screen.
     */
    suspend fun createOrder(
        items: List<Map<String, Any?>>,
        recipientName: String,
        phone: String,
        address: String,
        city: String,
        postalCode: String,
        note: String,
    ): OpResult<CreateOrderResult> = try {
        val response = Api.createOrder(
            items = items,
            recipientName = recipientName,
            phone = phone,
            address = address,
            city = city,
            postalCode = postalCode,
            note = note,
        )
        when (response) {
            is OpResult.Success -> OpResult.Success(
                CreateOrderResult(
                    orderId = response.data["orderId"]?.toString().orEmpty(),
                    orderNumber = response.data["orderNumber"]?.toString().orEmpty(),
                    total = (response.data["grandTotal"] as? Number)?.toLong() ?: 0L,
                    pointsEarned = (response.data["pointsEarned"] as? Number)?.toLong() ?: 0L,
                )
            )
            is OpResult.Failure -> OpResult.Failure(response.error)
        }
    } catch (t: Throwable) {
        OpResult.Failure(t)
    }

    /** User-facing message for any callable or network failure. */
    fun friendlyMessage(error: Throwable): String = Api.message(error)

    /** Refreshes the in-memory profile cache; called by the flows that already observe it. */
    internal fun cacheUser(user: User?) {
        cachedUser = user
    }

    data class CreateOrderResult(
        val orderId: String,
        val orderNumber: String,
        val total: Long,
        val pointsEarned: Long,
    )
}