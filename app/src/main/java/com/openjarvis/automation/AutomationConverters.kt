package com.openjarvis.automation

import androidx.room.TypeConverter
import com.openjarvis.automation.AutomationManager.AutomationSchedule

class AutomationConverters {

    @TypeConverter
    fun fromSchedule(s: AutomationSchedule): String = when (s) {
        is AutomationSchedule.Daily -> "D:${s.hour}:${s.minute}"
        is AutomationSchedule.Weekly -> "W:${s.dayOfWeek}:${s.hour}:${s.minute}"
        is AutomationSchedule.Interval -> "I:${s.intervalMs}"
        is AutomationSchedule.Once -> "O:${s.atMs}"
    }

    @TypeConverter
    fun toSchedule(v: String): AutomationSchedule {
        val p = v.split(":")
        return when (p[0]) {
            "W" -> AutomationSchedule.Weekly(p[1].toInt(), p[2].toInt(), p[3].toInt())
            "I" -> AutomationSchedule.Interval(p[1].toLong())
            "O" -> AutomationSchedule.Once(p[1].toLong())
            else -> AutomationSchedule.Daily(p[1].toInt(), p[2].toInt())
        }
    }
}
