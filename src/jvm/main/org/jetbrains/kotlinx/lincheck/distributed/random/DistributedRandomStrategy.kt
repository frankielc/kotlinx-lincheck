/*
 * Lincheck
 *
 * Copyright (C) 2019 - 2021 JetBrains s.r.o.
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

package org.jetbrains.kotlinx.lincheck.distributed.random

import org.jetbrains.kotlinx.lincheck.distributed.*
import org.jetbrains.kotlinx.lincheck.execution.ExecutionResult
import org.jetbrains.kotlinx.lincheck.execution.ExecutionScenario
import org.jetbrains.kotlinx.lincheck.runner.CompletedInvocationResult
import org.jetbrains.kotlinx.lincheck.strategy.IncorrectResultsFailure
import org.jetbrains.kotlinx.lincheck.strategy.LincheckFailure
import org.jetbrains.kotlinx.lincheck.strategy.toLincheckFailure
import org.jetbrains.kotlinx.lincheck.verifier.Verifier
import java.lang.reflect.Method

fun ExecutionResult.newResult(stateRepresentation: String?): ExecutionResult = ExecutionResult(
    initResults,
    afterInitStateRepresentation,
    parallelResultsWithClock,
    stateRepresentation,
    postResults,
    afterPostStateRepresentation
)

internal var canCrashBeforeAccessingDatabase = false

internal class DistributedRandomStrategy<Message, DB>(
    testCfg: DistributedCTestConfiguration<Message, DB>,
    testClass: Class<*>,
    scenario: ExecutionScenario,
    validationFunctions: List<Method>,
    stateRepresentationFunction: Method?,
    verifier: Verifier
) : DistributedStrategy<Message, DB>(
    testCfg,
    testClass,
    scenario,
    validationFunctions,
    stateRepresentationFunction,
    verifier
) {
    private val probability = ProbabilityModel(testCfg)
    private val runner = DistributedRunner(this, testCfg, testClass, validationFunctions, stateRepresentationFunction)

    init {
        try {
            runner.initialize()
        } catch (t: Throwable) {
            runner.close()
            throw t
        }
    }

    override fun tryCrash(iNode: Int) {
        if (testCfg.addressResolver.crashTypeForNode(iNode) != CrashMode.NO_CRASHES
            && probability.nodeFailed()
            && failureManager.canCrash(iNode)
        ) {
            failureManager.crashNode(iNode)
            throw CrashError()
        }
    }

    override fun onMessageSent(sender: Int, receiver: Int, messageId: Int) {
        tryCrash(sender)
    }

    override fun beforeDatabaseAccess(iNode: Int) {
        if (!canCrashBeforeAccessingDatabase) return
        tryCrash(iNode)
    }

    override fun next(taskManager: TaskManager): Task? {
        val tasks = taskManager.tasks
        val timeTasks = taskManager.timeTasks
        if (tasks.isEmpty() && (runner.hasAllResults()
                    && timeTasks.all { it is PeriodicTimer })
        ) return null
        val time = taskManager.time
        var tasksToProcess: List<Task>
        do {
            tasksToProcess =
                timeTasks.filter { time > it.time || probability.poissonProbability(it.time - time) } + tasks
        } while (tasksToProcess.isEmpty())
        val task = tasksToProcess.random(probability.rand)
        taskManager.removeTask(task)
        return task
    }

    override fun reset() {
        val crashExpectation = 3
        probability.reset(crashExpectation)
        failureManager.reset()
    }

    override fun run(): LincheckFailure? {
        println(scenario)
        runner.use { runner ->
            // Run invocations
            for (invocation in 0 until testCfg.invocationsPerIteration) {

                reset()
                val ir = runner.run()

                when (ir) {
                    is CompletedInvocationResult -> {
                        if (!verifier.verifyResults(scenario, ir.results)) {
                            val stateRepresentation = runner.constructStateRepresentation()
                            return IncorrectResultsFailure(
                                scenario,
                                ir.results.newResult(stateRepresentation)
                            ).also {
                                runner.storeEventsToFile(it)
                            }
                        }
                    }
                    else -> {
                        return ir.toLincheckFailure(scenario).also {
                            runner.storeEventsToFile(it)
                        }
                    }
                }
            }
            return null
        }
    }

    override fun tryAddPartitionBeforeSend(sender: Int, receiver: Int, messageId: Int): Boolean {
        if (testCfg.addressResolver.partitionTypeForNode(sender) != NetworkPartitionMode.NONE
            && probability.isNetworkPartition()
            && failureManager.canAddPartition(sender, receiver)
        ) {
            val partitionResult = failureManager.partition(sender, receiver)
            runner.onPartition(partitionResult.firstPart, partitionResult.secondPart, partitionResult.partitionId)
            return true
        }
        return false
    }

    override fun getMessageRate(sender: Int, receiver: Int, messageId: Int): Int = probability.duplicationRate()

    override fun choosePartitionComponent(nodes: List<Int>, limit: Int): List<Int> =
        probability.partition(nodes, limit)

    override fun getRecoverTimeout(taskManager: TaskManager): Int {
        val maxTimeout = taskManager.timeTasks.maxOfOrNull { it.time } ?: ProbabilityModel.DEFAULT_RECOVER_TIMEOUT
        return probability.recoverTimeout(maxTimeout)
    }

    override fun recoverPartition(firstPart: List<Int>, secondPart: List<Int>) {
        failureManager.removePartition(firstPart, secondPart)
    }

    override fun shouldRecover(iNode: Int): Boolean {
        return when (testCfg.addressResolver.crashTypeForNode(iNode)) {
            CrashMode.NO_RECOVER -> false
            CrashMode.ALL_NODES_RECOVER -> true
            CrashMode.MIXED -> probability.nodeRecovered()
            else -> throw IllegalArgumentException()
        }
    }
}