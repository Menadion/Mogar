package io.github.menadion.magus

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

// Debug builds only: runs the update check this second instead of waiting for its day.
//   adb shell am start -n io.github.menadion.magus/.UpdateCheckNowActivity
//
// Needed because the periodic job cannot be hurried from outside. Forcing it with
//   cmd jobscheduler run -f -n androidx.work.systemjobscheduler io.github.menadion.magus 1
// wakes WorkManager's service, but WorkManager then refuses the worker itself with "executed
// before schedule" and simply reschedules, so nothing runs and the log looks like a success.
// A one-time request of the same worker has no interval to be early for.
class UpdateCheckNowActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MogarUpdate", "enqueueing a one-off update check")
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<UpdateWorker>().build())
        finish()
    }
}
