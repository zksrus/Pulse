package com.zksrus.pulse

import android.content.Context
import android.util.Log
import com.huawei.hms.hihealth.HiHealthOptions
import com.huawei.hms.hihealth.HuaweiHiHealth
import com.huawei.hms.hihealth.data.DataType
import com.huawei.hms.hihealth.data.Field
import com.huawei.hms.hihealth.options.ReadOptions
import com.huawei.hms.support.api.client.HuaweiApiAvailability
import com.huawei.hms.support.account.HuaweiIdAuthManager
import com.huawei.hms.support.account.request.HuaweiIdAuthParams
import com.huawei.hms.support.account.request.HuaweiIdAuthParamsHelper
import com.huawei.hms.support.account.AuthHuaweiId
import com.huawei.hms.health.Scopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manager for Huawei Health Kit (Health Service Kit) API.
 * Handles authentication and reading step count data from Huawei fitness bands.
 *
 * Uses:
 * - HuaweiHiHealth.getHiHealthStore() for accessing Health Kit data
 * - DataType.DT_SUMMARY_DATA_STEP_COUNT for step count data
 * - Field.FIELD_STEPS for extracting step count values
 * - ReadOptions for time-range queries
 */
class HuaweiHealthManager(private val context: Context) {

    companion object {
        private const val TAG = "HuaweiHealth"
    }

    private var healthStore: com.huawei.hms.hihealth.HiHealthStore? = null
    private var authHuaweiId: AuthHuaweiId? = null

    /**
     * Initialize Health Kit and sign in.
     * Returns true if signed in, false if demo mode needed.
     */
    suspend fun initialize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Check if HMS is available
            val availability = HuaweiApiAvailability.getInstance()
            val resultCode = availability.isHuaweiMobileServicesAvailable(context)
            if (resultCode != com.huawei.hms.common.ConnectionResult.SUCCESS) {
                Log.w(TAG, "HMS not available (code=$resultCode), using demo mode")
                return@withContext Result.success(false)
            }

            // Sign in with Huawei ID
            val scopeList = listOf(
                Scope(Scopes.HEALTHKIT_STEP_BOTH),
                Scope(Scopes.HEALTHKIT_HEARTRATE_BOTH),
                Scope(Scopes.HEALTHKIT_HEIGHTWEIGHT_BOTH)
            )

            val authParamsHelper = HuaweiIdAuthParamsHelper(HuaweiIdAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            val authParams = authParamsHelper
                .setIdToken()
                .setAccessToken()
                .setScopeList(scopeList)
                .createParams()

            val authService = HuaweiIdAuthManager.getService(context, authParams)

            // Try silent sign-in first
            val silentTask = authService.silentSignIn()
            val taskResult = silentTask.result

            if (taskResult != null && taskResult.isSuccess) {
                Log.d(TAG, "Silent sign-in succeeded")
                authHuaweiId = taskResult
                initHealthStore()
                return@withContext Result.success(true)
            }

            // Need user interaction for sign-in
            Log.d(TAG, "Silent sign-in failed, need user interaction")
            return@withContext Result.success(false)
        } catch (e: Exception) {
            Log.e(TAG, "Health Kit init failed", e)
            return@withContext Result.failure(e)
        }
    }

    private fun initHealthStore() {
        val healthOptions = HiHealthOptions.Builder()
            .addDataType(DataType.DT_SUMMARY_DATA_STEP_COUNT, HiHealthOptions.ACCESS_READ)
            .build()

        healthStore = HuaweiHiHealth.getHiHealthStore(context, healthOptions)
    }

    /**
     * Get step count for today.
     */
    suspend fun getTodaySteps(): Result<StepData> = withContext(Dispatchers.IO) {
        try {
            val store = healthStore
            if (store == null) {
                // Return demo data if Health Kit is not initialized
                return@withContext Result.success(
                    StepData(
                        steps = (1000..8000).random().toLong(),
                        goal = 10000L,
                        calories = (30..300).random(),
                        distance = (0.5..6.0).random(),
                        isDemo = true
                    )
                )
            }

            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis
            val endTime = System.currentTimeMillis()

            val readOptions = ReadOptions.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .readDataType(DataType.DT_SUMMARY_DATA_STEP_COUNT)
                .build()

            val readTask = store.readFitnessData(readOptions)
            val response = readTask.result

            if (response.isSuccess) {
                val dataPoints = response.dataPoints
                var totalSteps = 0L

                for (point in dataPoints) {
                    val steps = point.getValue(Field.FIELD_STEPS)
                    totalSteps += steps.intValue()
                }

                val stepData = StepData(
                    steps = totalSteps,
                    goal = 10000L,
                    calories = (totalSteps * 0.04).toInt(),
                    distance = totalSteps * 0.000762, // average stride
                    isDemo = false
                )

                return@withContext Result.success(stepData)
            } else {
                Log.e(TAG, "Read failed: ${response.statusCode}")
                return@withContext Result.failure(
                    Exception("Read failed: ${response.statusCode}")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getTodaySteps failed", e)
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get step count for a specific date range.
     */
    suspend fun getStepsForRange(
        startTimeMs: Long,
        endTimeMs: Long
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val store = healthStore
                ?: return@withContext Result.success((500..3000).random().toLong())

            val readOptions = ReadOptions.Builder()
                .setTimeInterval(startTimeMs, endTimeMs, TimeUnit.MILLISECONDS)
                .readDataType(DataType.DT_SUMMARY_DATA_STEP_COUNT)
                .build()

            val readTask = store.readFitnessData(readOptions)
            val response = readTask.result

            if (response.isSuccess) {
                var totalSteps = 0L
                for (point in response.dataPoints) {
                    totalSteps += point.getValue(Field.FIELD_STEPS).intValue()
                }
                return@withContext Result.success(totalSteps)
            } else {
                return@withContext Result.failure(Exception("Read failed"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Get weekly step data for charts.
     */
    suspend fun getWeeklySteps(): Result<List<DailySteps>> = withContext(Dispatchers.IO) {
        try {
            val calendar = Calendar.getInstance()
            val today = calendar.clone() as Calendar
            calendar.add(Calendar.DAY_OF_YEAR, -6)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val startTime = calendar.timeInMillis

            today.set(Calendar.HOUR_OF_DAY, 23)
            today.set(Calendar.MINUTE, 59)
            today.set(Calendar.SECOND, 59)
            val endTime = today.timeInMillis

            val store = healthStore
            val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())

            if (store == null) {
                // Demo data for charts
                val weeklyData = (0..6).map { dayOffset ->
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -(6 - dayOffset))
                    DailySteps(
                        date = dateFormat.format(cal.time),
                        steps = (500..12000).random().toLong()
                    )
                }
                return@withContext Result.success(weeklyData)
            }

            val readOptions = ReadOptions.Builder()
                .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                .readDataType(DataType.DT_SUMMARY_DATA_STEP_COUNT)
                .build()

            val response = store.readFitnessData(readOptions).result

            if (response.isSuccess) {
                val dailyMap = mutableMapOf<Int, Long>()

                for (point in response.dataPoints) {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = point.getStartTime(TimeUnit.MILLISECONDS)
                    }
                    val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                    val steps = point.getValue(Field.FIELD_STEPS).intValue().toLong()
                    dailyMap[dayOfYear] = (dailyMap[dayOfYear] ?: 0L) + steps
                }

                val weeklyData = (0..6).map { dayOffset ->
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -(6 - dayOffset))
                    val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                    DailySteps(
                        date = dateFormat.format(cal.time),
                        steps = dailyMap[dayOfYear] ?: 0L
                    )
                }

                return@withContext Result.success(weeklyData)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Sign out from Huawei Health Kit.
     */
    fun signOut() {
        try {
            healthStore = null
            authHuaweiId = null
            Log.d(TAG, "Signed out from Health Kit")
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
        }
    }
}

data class StepData(
    val steps: Long,
    val goal: Long,
    val calories: Int,
    val distance: Double,
    val isDemo: Boolean
)

data class DailySteps(
    val date: String,
    val steps: Long
)
