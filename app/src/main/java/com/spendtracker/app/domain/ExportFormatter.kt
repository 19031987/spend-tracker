package com.spendtracker.app.domain

data class ExportRow(
    val id: Long,
    val date: String,
    val type: String,
    val account: String,
    val merchant: String?,
    val category: String,
    val group: String,
    val amountMinor: Long,
    val note: String?
)

data class ExportRule(val matchType: String, val pattern: String, val category: String)

object ExportFormatter {

    fun amount(minor: Long): String {
        val abs = Math.abs(minor)
        return "${if (minor < 0) "-" else ""}${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    fun toCsv(rows: List<ExportRow>): String {
        val header = listOf("id", "date", "type", "account", "merchant", "category", "group", "amount", "note")
        val lines = rows.map { r ->
            listOf(
                r.id.toString(), r.date, r.type, r.account, r.merchant.orEmpty(),
                r.category, r.group, amount(r.amountMinor), r.note.orEmpty()
            ).joinToString(",") { csvCell(it) }
        }
        return (listOf(header.joinToString(",")) + lines).joinToString("\r\n") + "\r\n"
    }

    fun toJson(exportedAt: String, rows: List<ExportRow>, rules: List<ExportRule>): String {
        val tx = rows.joinToString(",\n") { r ->
            "    {" + listOf(
                "\"id\": ${r.id}",
                "\"date\": ${q(r.date)}",
                "\"type\": ${q(r.type)}",
                "\"account\": ${q(r.account)}",
                "\"merchant\": ${r.merchant?.let(::q) ?: "null"}",
                "\"category\": ${q(r.category)}",
                "\"group\": ${q(r.group)}",
                "\"amount\": ${amount(r.amountMinor)}",
                "\"note\": ${r.note?.let(::q) ?: "null"}"
            ).joinToString(", ") + "}"
        }
        val ruleJson = rules.joinToString(",\n") { r ->
            "    {\"match\": ${q(r.matchType)}, \"pattern\": ${q(r.pattern)}, \"category\": ${q(r.category)}}"
        }
        return "{\n  \"exportedAt\": ${q(exportedAt)},\n  \"transactions\": [\n$tx\n  ],\n" +
            "  \"rules\": [\n$ruleJson\n  ]\n}\n"
    }

    private fun csvCell(value: String): String {
        // Prefix formula-looking text so spreadsheets never execute it.
        val safe = if (value.isNotEmpty() && value[0] in "=+@" ) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else safe
    }

    private fun q(value: String): String {
        val sb = StringBuilder("\"")
        for (c in value) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}
