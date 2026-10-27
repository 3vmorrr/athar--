package org.athar.app.core.sync

import androidx.work.Constraints
import androidx.work.NetworkType

object BackgroundWorkConstraints {
    fun forBudget(budget: BackgroundWorkBudget): Constraints {
        budget.validated()
        return Constraints.Builder()
            .setRequiredNetworkType(
                if (budget.requiresNetwork) NetworkType.CONNECTED
                else NetworkType.NOT_REQUIRED,
            )
            .setRequiresBatteryNotLow(budget.requiresBatteryNotLow)
            .setRequiresStorageNotLow(budget.requiresStorageNotLow)
            .build()
    }
}
