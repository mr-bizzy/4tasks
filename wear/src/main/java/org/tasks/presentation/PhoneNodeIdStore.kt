package org.tasks.presentation

import android.content.Context
import com.google.android.horologist.data.TargetNodeId

private const val PREFS_NAME = "phone_connection"
private const val KEY_NODE_ID = "phone_node_id"

fun Context.savePhoneNodeId(nodeId: String) {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_NODE_ID, nodeId)
        .apply()
}

fun Context.clearPhoneNodeId() {
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .remove(KEY_NODE_ID)
        .apply()
}

fun Context.phoneTargetNodeId(): TargetNodeId {
    val nodeId = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_NODE_ID, null)
    return if (nodeId != null) {
        TargetNodeId.SpecificNodeId(nodeId)
    } else {
        TargetNodeId.PairedPhone
    }
}
