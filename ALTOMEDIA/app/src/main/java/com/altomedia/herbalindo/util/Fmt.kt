package com.altomedia.herbalindo.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Fmt {

    private val idLocale = Locale("in", "ID")

    /** Formats an integral Rupiah amount, e.g. 125500 -> "Rp125.500". */
    fun rupiah(amount: Long): String = "Rp" + thousands(amount)

    /** Formats a numeric amount with Indonesian thousand separators. */
    fun thousands(value: Long): String {
        val negative = value < 0
        val digits = Math.abs(value).toString()
        val sb = StringBuilder()
        for ((i, c) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(c)
        }
        return if (negative) "-$sb" else sb.toString()
    }

    fun points(value: Long): String = thousands(value) + " Poin"

    private val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", idLocale)
    private val dateFmt = SimpleDateFormat("dd MMM yyyy", idLocale)

    fun dateTime(date: Date?): String = date?.let { dateTimeFmt.format(it) } ?: "-"
    fun date(date: Date?): String = date?.let { dateFmt.format(it) } ?: "-"

    /** Business day key in WIB (UTC+7), used to bucket daily tasks and ad limits. */
    fun dayKey(date: Date = Date()): String {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        return fmt.format(date)
    }

    fun dayKey(calendar: Calendar): String = dayKey(calendar.time)

    /** Human-readable order number, e.g. ORD-20260920-000123. */
    fun orderNumber(sequence: Long, date: Date = Date()): String {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        return "ORD-${fmt.format(date)}-%06d".format(sequence)
    }

    fun minutesSeconds(totalSeconds: Long): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d".format(m, s)
    }

    /** Turns a status enum name into a friendly Indonesian label. */
    fun statusLabel(status: String): String = when (status) {
        "PENDING" -> "Menunggu"
        "WAITING_PAYMENT" -> "Menunggu Pembayaran"
        "PAID" -> "Dibayar"
        "PROCESSING" -> "Diproses"
        "SHIPPED" -> "Dikirim"
        "DELIVERED" -> "Diterima"
        "COMPLETED" -> "Selesai"
        "CANCELLED" -> "Dibatalkan"
        "REFUNDED" -> "Refund"
        "UNPAID" -> "Belum Bayar"
        "WAITING_CONFIRMATION" -> "Menunggu Konfirmasi"
        "FAILED" -> "Gagal"
        "REVIEW" -> "Ditinjau"
        "APPROVED" -> "Disetujui"
        "REJECTED" -> "Ditolak"
        else -> status
    }

    fun maskPhone(phone: String): String = when {
        phone.length >= 7 -> phone.take(4) + "****" + phone.takeLast(3)
        phone.isEmpty() -> "-"
        else -> phone
    }

    /** Masks all but the last four digits of a bank/e-wallet number. */
    fun maskAccount(account: String): String =
        if (account.length > 4) "****" + account.takeLast(4) else account
}