package org.tasks.data

import android.app.Activity
import org.tasks.activities.GoogleTaskListSettingsActivity
import org.tasks.caldav.CaldavCalendarSettingsActivity
import org.tasks.caldav.LocalListSettingsActivity
import org.tasks.data.entity.CaldavAccount
import org.tasks.opentasks.OpenTasksListSettingsActivity
import org.tasks.sync.microsoft.MicrosoftListSettingsActivity

fun CaldavAccount.isTasksSubscription(): Boolean {
    return isTasksOrg && !isPaymentRequired() && !isLoggedOut()
}

fun CaldavAccount.listSettingsClass(): Class<out Activity> = when(accountType) {
    // An old EteSync account (gone since 0.1.17) no longer has a server: its lists are edited as local ones.
    CaldavAccount.TYPE_LOCAL, CaldavAccount.TYPE_ETEBASE -> LocalListSettingsActivity::class.java
    CaldavAccount.TYPE_OPENTASKS -> OpenTasksListSettingsActivity::class.java
    CaldavAccount.TYPE_MICROSOFT -> MicrosoftListSettingsActivity::class.java
    CaldavAccount.TYPE_GOOGLE_TASKS -> GoogleTaskListSettingsActivity::class.java
    else -> CaldavCalendarSettingsActivity::class.java
}
