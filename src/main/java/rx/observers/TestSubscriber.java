/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.observers;

import java.util.*;
import java.util.concurrent.*;

import rx.*;
import rx.Observer;
import rx.annotations.Experimental;
import rx.exceptions.CompositeException;

/**
 * A {@code TestSubscriber} is a variety of {@link Subscriber} that you can use for unit testing, to perform
 * assertions, inspect received events, or wrap a mocked {@code Subscriber}.
 */
public class TestSubscriber<T> extends Subscriber<T> {

    private final TestObserver<T> testObserver;
    private final CountDownLatch latch = new CountDownLatch(1);
    private volatile Thread lastSeenThread;
    /** Holds the initial request value. */
    private final long initialRequest;
    /** The shared no-op observer. */
    private static final Observer<Object> INERT = new Observer<Object>() {

        @Override
        public void onCompleted() {
            // do nothing
        }

        @Override
        public void onError(Throwable e) {
            // do nothing
        }

        @Override
        public void onNext(Object t) {
            // do nothing
        }

    };

    /**
     * Constructs a TestSubscriber with the initial request to be requested from upstream.
     *
     * @param initialRequest the initial request value, negative value will revert to the default unbounded behavior
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @SuppressWarnings("unchecked")
    @Experimental
    public TestSubscriber(long initialRequest) {
        this((Observer<T>)INERT, initialRequest);
    }
    
    /**
     * Constructs a TestSubscriber with the initial request to be requested from upstream
     * and a delegate Observer to wrap.
     *
     * @param initialRequest the initial request value, negative value will revert to the default unbounded behavior
     * @param delegate the Observer instance to wrap
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber(Observer<T> delegate, long initialRequest) {
        if (delegate == null) {
            throw new NullPointerException();
        }
        this.testObserver = new TestObserver<T>(delegate);
        this.initialRequest = initialRequest;
    }

    public TestSubscriber(Subscriber<T> delegate) {
        this(delegate, -1);
    }

    public TestSubscriber(Observer<T> delegate) {
        this(delegate, -1);
    }

    public TestSubscriber() {
        this(-1);
    }
    
    @Experimental
    public static <T> TestSubscriber<T> create() {
        return new TestSubscriber<T>();
    }
    
    @Experimental
    public static <T> TestSubscriber<T> create(long initialRequest) {
        return new TestSubscriber<T>(initialRequest);
    }
    
    @Experimental
    public static <T> TestSubscriber<T> create(Observer<T> delegate, long initialRequest) {
        return new TestSubscriber<T>(delegate, initialRequest);
    }
    
    @Experimental
    public static <T> TestSubscriber<T> create(Subscriber<T> delegate) {
        return new TestSubscriber<T>(delegate);
    }
    
    @Experimental
    public static <T> TestSubscriber<T> create(Observer<T> delegate) {
        return new TestSubscriber<T>(delegate);
    }
    
    @Override
    public void onStart() {
        if  (initialRequest >= 0) {
            requestMore(initialRequest);
        }
    }

    /**
     * Notifies the Subscriber that the {@code Observable} has finished sending push-based notifications.
     * <p>
     * The {@code Observable} will not call this method if it calls {@link #onError}.
     */
    @Override
    public void onCompleted() {
        try {
            lastSeenThread = Thread.currentThread();
            testObserver.onCompleted();
        } finally {
            latch.countDown();
        }
    }

    /**
     * Returns the {@link Notification}s representing each time this {@link Subscriber} was notified of sequence
     * completion via {@link #onCompleted}, as a {@link List}.
     *
     * @return a list of Notifications representing calls to this Subscriber's {@link #onCompleted} method
     */
    public List<Notification<T>> getOnCompletedEvents() {
        return testObserver.getOnCompletedEvents();
    }

    /**
     * Notifies the Subscriber that the {@code Observable} has experienced an error condition.
     * <p>
     * If the {@code Observable} calls this method, it will not thereafter call {@link #onNext} or
     * {@link #onCompleted}.
     * 
     * @param e
     *          the exception encountered by the Observable
     */
    @Override
    public void onError(Throwable e) {
        try {
            lastSeenThread = Thread.currentThread();
            testObserver.onError(e);
        } finally {
            latch.countDown();
        }
    }

    /**
     * Returns the {@link Throwable}s this {@link Subscriber} was notified of via {@link #onError} as a
     * {@link List}.
     *
     * @return a list of the Throwables that were passed to this Subscriber's {@link #onError} method
     */
    public List<Throwable> getOnErrorEvents() {
        return testObserver.getOnErrorEvents();
    }

    /**
     * Provides the Subscriber with a new item to observe.
     * <p>
     * The {@code Observable} may call this method 0 or more times.
     * <p>
     * The {@code Observable} will not call this method again after it calls either {@link #onCompleted} or
     * {@link #onError}.
     * 
     * @param t
     *          the item emitted by the Observable
     */
    @Override
    public void onNext(T t) {
        lastSeenThread = Thread.currentThread();
        testObserver.onNext(t);
    }
    
    /**
     * Allows calling the protected {@link #request(long)} from unit tests.
     *
     * @param n the maximum number of items you want the Observable to emit to the Subscriber at this time, or
     *           {@code Long.MAX_VALUE} if you want the Observable to emit items at its own pace
     */
    public void requestMore(long n) {
        request(n);
    }

    /**
     * Returns the sequence of items observed by this {@link Subscriber}, as an ordered {@link List}.
     *
     * @return a list of items observed by this Subscriber, in the order in which they were observed
     */
    public List<T> getOnNextEvents() {
        return testObserver.getOnNextEvents();
    }

    /**
     * Asserts that a particular sequence of items was received by this {@link Subscriber} in order.
     *
     * @param items
     *          the sequence of items expected to have been observed
     * @throws AssertionError
     *          if the sequence of items observed does not exactly match {@code items}
     */
    public TestSubscriber assertReceivedOnNext(List<T> items) {
        testObserver.assertReceivedOnNext(items);
        return this;
    }

    /**
     * Asserts that a single terminal event occurred, either {@link #onCompleted} or {@link #onError}.
     *
     * @throws AssertionError
     *          if not exactly one terminal event notification was received
     */
    public TestSubscriber assertTerminalEvent() {
        testObserver.assertTerminalEvent();
        return this;
    }

    /**
     * Asserts that this {@code Subscriber} is unsubscribed.
     *
     * @throws AssertionError
     *          if this {@code Subscriber} is not unsubscribed
     */
    public TestSubscriber assertUnsubscribed() {
        if (!isUnsubscribed()) {
            throw new AssertionError("Not unsubscribed.");
        }
        return this;
    }

    /**
     * Asserts that this {@code Subscriber} has received no {@code onError} notifications.
     * 
     * @throws AssertionError
     *          if this {@code Subscriber} has received one or more {@code onError} notifications
     */
    public TestSubscriber assertNoErrors() {
        List<Throwable> onErrorEvents = getOnErrorEvents();
        if (onErrorEvents.size() > 0) {
            AssertionError ae = new AssertionError("Unexpected onError events: " + getOnErrorEvents().size());
            if (onErrorEvents.size() == 1) {
                ae.initCause(getOnErrorEvents().get(0));
            } else {
                ae.initCause(new CompositeException(onErrorEvents));
            }
            throw ae;
        }
        return this;
    }

    
    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification).
     *
     * @throws RuntimeException
     *          if the Subscriber is interrupted before the Observable is able to complete
     */
    public TestSubscriber awaitTerminalEvent() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
        return this;
    }

    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification), or until a timeout expires.
     *
     * @param timeout
     *          the duration of the timeout
     * @param unit
     *          the units in which {@code timeout} is expressed
     * @throws RuntimeException
     *          if the Subscriber is interrupted before the Observable is able to complete
     */
    public TestSubscriber awaitTerminalEvent(long timeout, TimeUnit unit) {
        try {
            latch.await(timeout, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted", e);
        }
        return this;
    }

    /**
     * Blocks until this {@link Subscriber} receives a notification that the {@code Observable} is complete
     * (either an {@code onCompleted} or {@code onError} notification), or until a timeout expires; if the
     * Subscriber is interrupted before either of these events take place, this method unsubscribes the
     * Subscriber from the Observable). If timeout expires then the Subscriber is unsubscribed from the Observable.
     *
     * @param timeout
     *          the duration of the timeout
     * @param unit
     *          the units in which {@code timeout} is expressed
     */
    public TestSubscriber awaitTerminalEventAndUnsubscribeOnTimeout(long timeout, TimeUnit unit) {
        try {
            boolean result = latch.await(timeout, unit);
            if (!result) {
                // timeout occurred
                unsubscribe();
            }
        } catch (InterruptedException e) {
            unsubscribe();
        }
        return this;
    }

    /**
     * Returns the last thread that was in use when an item or notification was received by this
     * {@link Subscriber}.
     *
     * @return the {@code Thread} on which this Subscriber last received an item or notification from the
     *         Observable it is subscribed to
     */
    public Thread getLastSeenThread() {
        return lastSeenThread;
    }
    
    /**
     * Asserts that there is exactly one completion event.
     *
     * @throws AssertionError if there were zero, or more than one, onCompleted events
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertCompleted() {
        int s = testObserver.getOnCompletedEvents().size();
        if (s == 0) {
            throw new AssertionError("Not completed!");
        } else
        if (s > 1) {
            throw new AssertionError("Completed multiple times: " + s);
        }
        return this;
    }

    /**
     * Asserts that there is no completion event.
     *
     * @throws AssertionError if there were one or more than one onCompleted events
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertNotCompleted() {
        int s = testObserver.getOnCompletedEvents().size();
        if (s == 1) {
            throw new AssertionError("Completed!");
        } else
        if (s > 1) {
            throw new AssertionError("Completed multiple times: " + s);
        }
        return this;
    }

    /**
     * Asserts that there is exactly one error event which is a subclass of the given class.
     *
     * @param clazz the class to check the error against.
     * @throws AssertionError if there were zero, or more than one, onError events, or if the single onError
     *                        event did not carry an error of a subclass of the given class
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertError(Class<? extends Throwable> clazz) {
        List<Throwable> err = testObserver.getOnErrorEvents();
        if (err.size() == 0) {
            throw new AssertionError("No errors");
        } else
        if (err.size() > 1) {
            AssertionError ae = new AssertionError("Multiple errors: " + err.size());
            ae.initCause(new CompositeException(err));
            throw ae;
        } else
        if (!clazz.isInstance(err.get(0))) {
            AssertionError ae = new AssertionError("Exceptions differ; expected: " + clazz + ", actual: " + err.get(0));
            ae.initCause(err.get(0));
            throw ae;
        }
        return this;
    }

    /**
     * Asserts that there is a single onError event with the exact exception.
     *
     * @param throwable the throwable to check
     * @throws AssertionError if there were zero, or more than one, onError events, or if the single onError
     *                        event did not carry an error that matches the specified throwable
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertError(Throwable throwable) {
        List<Throwable> err = testObserver.getOnErrorEvents();
        if (err.size() == 0) {
            throw new AssertionError("No errors");
        } else
        if (err.size() > 1) {
            AssertionError ae = new AssertionError("Multiple errors: " + err.size());
            ae.initCause(new CompositeException(err));
            throw ae;
        } else
        if (!throwable.equals(err.get(0))) {
            AssertionError ae = new AssertionError("Exceptions differ; expected: " + throwable + ", actual: " + err.get(0));
            ae.initCause(err.get(0));
            throw ae;
        }
        return this;
    }

    /**
     * Asserts that there are no onError and onCompleted events.
     *
     * @throws AssertionError if there was either an onError or onCompleted event
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertNoTerminalEvent() {
        List<Throwable> err = testObserver.getOnErrorEvents();
        int s = testObserver.getOnCompletedEvents().size();
        if (err.size() > 0 || s > 0) {
            if (err.isEmpty()) {
                throw new AssertionError("Found " + err.size() + " errors and " + s + " completion events instead of none");
            } else
            if (err.size() == 1) {
                AssertionError ae = new AssertionError("Found " + err.size() + " errors and " + s + " completion events instead of none");
                ae.initCause(err.get(0));
                throw ae;
            } else {
                AssertionError ae = new AssertionError("Found " + err.size() + " errors and " + s + " completion events instead of none");
                ae.initCause(new CompositeException(err));
                throw ae;
            }
        }
        return this;
    }

    /**
     * Asserts that there are no onNext events received.
     *
     * @throws AssertionError if there were any onNext events
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertNoValues() {
        int s = testObserver.getOnNextEvents().size();
        if (s > 0) {
            throw new AssertionError("No onNext events expected yet some received: " + s);
        }
        return this;
    }

    /**
     * Asserts that the given number of onNext events are received.
     *
     * @param count the expected number of onNext events
     * @throws AssertionError if there were more or fewer onNext events than specified by {@code count}
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertValueCount(int count) {
        int s = testObserver.getOnNextEvents().size();
        if (s != count) {
            throw new AssertionError("Number of onNext events differ; expected: " + count + ", actual: " + s);
        }
        return this;
    }
    
    /**
     * Asserts that the received onNext events, in order, are the specified items.
     *
     * @param values the items to check
     * @throws AssertionError if the items emitted do not exactly match those specified by {@code values}
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertValues(T... values) {
        assertReceivedOnNext(Arrays.asList(values));
        return this;
    }

    /**
     * Asserts that there is only a single received onNext event and that it marks the emission of a specific item.
     *
     * @param value the item to check
     * @throws AssertionError if the Observable does not emit only the single item specified by {@code value}
     * @since (if this graduates from "Experimental" replace this parenthetical with the release number)
     */
    @Experimental
    public TestSubscriber assertValue(T value) {
        assertReceivedOnNext(Collections.singletonList(value));
        return this;
    }
}
