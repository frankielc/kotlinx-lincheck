/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2022 JetBrains s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>
 */

package org.jetbrains.kotlinx.lincheck

// This is org.jetbrains.kotlinx.lincheck.IdeaPluginKt class

// Invoked by Lincheck after the minimization is applied. DO NOT FORGET TO TURN OFF THE RUNNER TIMEOUTS.
fun testFailed(trace: Array<String>) {
//    println(trace.contentToString())
}

fun replay(): Boolean {
    return false // should be replaced with `true` to replay the failure
}

fun beforeEvent(eventId: Int) {

}

fun onThreadChange() {}