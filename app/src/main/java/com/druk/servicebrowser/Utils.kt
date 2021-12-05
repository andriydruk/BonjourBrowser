/*
 * Copyright (C) 2015 Andriy Druk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.druk.servicebrowser

import java.text.SimpleDateFormat
import java.util.*

object Utils {

    private const val TIME_FORMAT = "HH:mm:ss"

    fun formatTime(timestamp: Long?): String {
        if (timestamp == null) {
            return ""
        }
        val cal = Calendar.getInstance()
        val tz = cal.timeZone
        val sdf = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
        sdf.timeZone = tz
        return sdf.format(Date(timestamp))
    }
}