/*
 * #%L
 * Lincheck
 * %%
 * Copyright (C) 2015 - 2018 Devexperts, LLC
 * Copyright (C) 2019-2020 JetBrains s.r.o.
 * %%
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
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

package org.jetbrains.kotlinx.lincheck.runner;

import org.jetbrains.kotlinx.lincheck.*;
import org.jetbrains.kotlinx.lincheck.execution.*;
import org.jetbrains.kotlinx.lincheck.strategy.*;
import org.objectweb.asm.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/**
 * Runner determines how to run your concurrent test. In order to support techniques
 * like fibers, it may require code transformation, so {@link #needsTransformation()}
 * method has to return {@code true} and {@link #createTransformer(ClassVisitor)}
 * one has to be implemented.
 */
public abstract class Runner {
    protected final ExecutionScenario scenario;
    protected Class<?> testClass;
    protected final List<Method> validationFunctions;
    protected final Strategy strategy;
    public ExecutionClassLoader classLoader;

    protected final AtomicInteger completedOrSuspendedThreads = new AtomicInteger(0);

    protected Runner(Strategy strategy, Class<?> testClass, List<Method> validationFunctions) {
        this.scenario = strategy.getScenario();
        this.testClass = testClass;
        this.strategy = strategy;
        this.validationFunctions = validationFunctions;
        doTransformTestClass();
    }

    public void transformTestClass() {
        doTransformTestClass();
    }

    private void doTransformTestClass() {
        this.classLoader = (this.needsTransformation() || strategy.needsTransformation()) ?
                new TransformationClassLoader(strategy, this) : new ExecutionClassLoader();
        this.testClass = loadClass(testClass.getTypeName());
        this.classLoader = (this.needsTransformation() || strategy.needsTransformation()) ?
                new TransformationClassLoader(strategy, this) : new ExecutionClassLoader();
        this.testClass = loadClass(testClass.getTypeName());
    }

    /**
     * Returns current state representation of the test instance based on provided via StateRepresentation annotation function.
     * Returns null if StateRepresentation annotation was not used.
     */
    public String getStateRepresentation() {
        return null;
    }

    /**
     * Loads class using runner's class loader
     */
    private Class<?> loadClass(String className) {
        try {
            return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Cannot load class " + className, e);
        }
    }

    /**
     * Creates required for this runner transformer.
     * Throws {@link UnsupportedOperationException} by default.
     *
     * @return class visitor which transform the code due to support this runner.
     */
    public ClassVisitor createTransformer(ClassVisitor cv) {
        throw new UnsupportedOperationException(getClass() + " runner does not transform classes");
    }

    /**
     * This method has to return {@code true} if code transformation is required for runner.
     * Returns {@code false} by default.
     */
    public boolean needsTransformation() {
        return false;
    }

    /**
     * Runs the next invocation.
     */
    public abstract InvocationResult run();

    /**
     * This method is invoked by every test thread as the first operation.
     * @param threadId number of invoking thread
     */
    public void onStart(int threadId) {}

    /**
     * This method is invoked by every test thread as the last operation
     * if no exception has been thrown.
     * @param threadId number of invoking thread
     */
    public void onFinish(int threadId) {}

    /**
     * This method is invoked by a test thread
     * if an exception has been thrown.
     * @param threadId number of invoking thread
     */
    public void onFailure(int threadId, Throwable e) {}

    /**
     * This method is invoked by a test thread
     * if a coroutine was suspended
     * @param threadId number of invoking thread
     */
    void afterCoroutineSuspended(int threadId) {
        throw new UnsupportedOperationException("Coroutines are not supported");
    }

    /**
     * This method is invoked by a test thread
     * if a coroutine was resumed
     * @param threadId number of invoking thread
     */
    void beforeCoroutineResumed(int threadId) {
        throw new UnsupportedOperationException("Coroutines are not supported");
    }

    /**
     * This method is invoked by a test thread
     * if the current coroutine can be resumed
     * @param threadId number of invoking thread
     * @param iActor number of actor invoked
     */
    public boolean canResumeCoroutine(int threadId, int iActor) {
        throw new UnsupportedOperationException("Coroutines are not supported");
    }

    /**
     * Is invoked before each actor execution in a thread.
     */
    public void onActorStart(int threadId) {
        strategy.onActorStart(threadId);
    }

    /**
     * Closes used for this runner resources.
     */
    public void close() {}

    /**
     * @return whether all scenario threads are completed or suspended
     */
    public boolean isParallelExecutionCompleted() {
        return completedOrSuspendedThreads.get() == scenario.getThreads();
    }
}
