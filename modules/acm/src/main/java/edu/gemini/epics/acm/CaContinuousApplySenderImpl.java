/*
 * Copyright (c) 2016-2021 Association of Universities for Research in Astronomy, Inc. (AURA)
 * For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause
 */

package edu.gemini.epics.acm;

import edu.gemini.epics.EpicsReader;
import edu.gemini.epics.EpicsWriter;
import edu.gemini.epics.api.ChannelListener;
import gov.aps.jca.CAException;
import gov.aps.jca.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CaContinuousApplySenderImpl<C extends Enum<C> & CarStateGeneric> implements ApplySenderWithResource {

    private static final Logger LOG = LoggerFactory.getLogger(CaContinuousApplySenderImpl.class
            .getName());

    private final String name;
    private final String description;

    private final CaApplyRecord apply;
    private final CaCarRecord<C> car;

    private final Boolean trace = Boolean.getBoolean("epics.apply.trace");

    private long timeout;
    private TimeUnit timeoutUnit;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> timeoutFuture;
    private final ChannelListener<Integer> valListener;
    private final ChannelListener<Integer> carClidListener;
    private final ChannelListener<C> carValListener;
    private State currentState;
    private static final State IdleState = new State() {
        @Override
        public String signature() { return "IdleState"; }

        @Override
        public State onApplyValChange(Integer val) {
            return this;
        }

        @Override
        public State onCarValChange(CarStateGeneric carState) {
            return this;
        }

        @Override
        public State onCarClidChange(Integer val) {
            return this;
        }

        @Override
        public State onTimeout() {
            return this;
        }
    };

    public CaContinuousApplySenderImpl(
            final String name,
            final String applyRecord,
            final String carRecord,
            final String description,
            final Class<C> carClass,
            final EpicsReader epicsReader,
            final EpicsWriter epicsWriter,
            final ScheduledExecutorService executor
            ) throws CAException {
        super();
        this.name = name;
        this.description = description;
        this.currentState = IdleState;
        this.executor = executor;

        apply = new CaApplyRecord(applyRecord, epicsReader, epicsWriter);
        apply.registerValListener(valListener = (String arg0, List<Integer> newVals) -> {
            if (newVals != null && !newVals.isEmpty()) {
                CaContinuousApplySenderImpl.this.onApplyValChange(newVals.get(0));
            }
        });

        car = new CaCarRecord<C>(carRecord, carClass, epicsReader);
        car.registerClidListener(carClidListener = (String arg0, List<Integer> newVals) -> {
            if (newVals != null && !newVals.isEmpty()) {
                CaContinuousApplySenderImpl.this.onCarClidChange(newVals.get(0));
            }
        });
        car.registerValListener(carValListener = (String arg0, List<C> newVals) -> {
            if (newVals != null && !newVals.isEmpty()) {
                CaContinuousApplySenderImpl.this.onCarValChange(newVals.get(0));
            }
        });

    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getApply() {
        return apply.getEpicsName();
    }

    @Override
    public String getCAR() {
        return car.getEpicsName();
    }

    @Override
    public void unbind() {

        executor.shutdown();

        try {
            apply.unregisterValListener(valListener);
        } catch (CAException e) {
            LOG.warn(e.getMessage());
        }
        try {
            car.unregisterClidListener(carClidListener);
        } catch (CAException e) {
            LOG.warn(e.getMessage());
        }
        try {
            car.unregisterValListener(carValListener);
        } catch (CAException e) {
            LOG.warn(e.getMessage());
        }

        apply.unbind();
        car.unbind();
    }

    @Override
    public synchronized CaCommandMonitor post() {
        CaCommandMonitorImpl cm = new CaCommandMonitorImpl();
        if (!currentState.equals(IdleState)) {
            failCommand(cm, new CaCommandInProgress());
        } else {
            try {
                CarStateGeneric carVal = car.getValValue();
                currentState = new WaitPreset(cm, carVal);

                apply.setDir(CadDirective.START);
                if (timeout > 0) {
                    timeoutFuture = executor.schedule(CaContinuousApplySenderImpl.this::onTimeout, timeout,
                            timeoutUnit);
                }
            } catch (CAException | TimeoutException e) {
                failCommand(cm, e);
                currentState = IdleState;
            }
        }

        return cm;
    }

    @Override
    public CaCommandMonitor postWait() throws InterruptedException {
        CaCommandMonitor cm = post();
        cm.waitDone();
        return cm;
    }

    @Override
    public CaCommandMonitor postCallback(final CaCommandListener callback) {
        CaCommandMonitor cm = post();
        cm.setCallback(callback);
        return cm;
    }

    private interface State {
        String signature();

        State onApplyValChange(Integer val);

        State onCarValChange(CarStateGeneric carState);

        State onCarClidChange(Integer val);

        State onTimeout();
    }

    private final class WaitPreset implements State {
        final CaCommandMonitorImpl cm;
        final CarStateGeneric carVal;
        final Integer carClid;

        WaitPreset(final CaCommandMonitorImpl cm, final CarStateGeneric carVal) {
            this.cm = cm;
            this.carVal = carVal;
            this.carClid = null;
        }

        private WaitPreset(final CaCommandMonitorImpl cm, final CarStateGeneric carVal, final Integer carClid) {
            this.cm = cm;
            this.carVal = carVal;
            this.carClid = carClid;
        }

        @Override
        public String signature() {
            return "WaitPreset(carState = " + carVal + ", carClid = " + carClid + ")";
        }

        @Override
        public State onApplyValChange(final Integer val) {
            if (val > 0) {
                if (carVal != null && val.equals(carClid)) {
                    if (carVal.isError()) {
                        failCommandWithCarError(cm);
                        return IdleState;
                    } else if (carVal.isBusy()) {
                        succeedCommand(cm);
                        return IdleState;
                    }
                }
                return new WaitStart(cm, val, carVal, carClid);
            } else {
                failCommandWithApplyError(cm);
                return IdleState;
            }
        }

        @Override
        public State onCarValChange(final CarStateGeneric val) {
            return new WaitPreset(cm, val, carClid);
        }

        @Override
        public State onCarClidChange(final Integer val) {
            return new WaitPreset(cm, carVal, val);
        }

        @Override
        public State onTimeout() {
            failCommand(cm, new TimeoutException());
            return IdleState;
        }
    }

    private final class WaitStart implements State {
        final CaCommandMonitorImpl cm;
        final int clid;
        final Integer carClid;
        final CarStateGeneric carState;

        WaitStart(
                final CaCommandMonitorImpl cm,
                final int clid,
                final CarStateGeneric carState,
                final Integer carClid) {
            this.cm = cm;
            this.clid = clid;
            this.carState = carState;
            this.carClid = carClid;
        }

        @Override
        public String signature() {
            return "WaitStart(clid = " + clid + ", carState = " + carState + ", carClid = " + carClid + ")";
        }

        @Override
        public State onApplyValChange(final Integer val) {
            if (val >= clid) {
                return new WaitStart(cm, val, carState, carClid);
            } else {
                failCommand(cm, new CaCommandPostError(
                        "Another command was triggered in apply record "
                                + apply.getEpicsName()));
                return IdleState;
            }
        }

        @Override
        public State onCarValChange(final CarStateGeneric val) {
            return checkOutConditions(val, carClid);
        }

        @Override
        public State onCarClidChange(final Integer val) {
            return checkOutConditions(carState, val);
        }

        @Override
        public State onTimeout() {
            failCommand(cm, new TimeoutException());
            return IdleState;
        }

        private State checkOutConditions(final CarStateGeneric carState,
                                         final Integer carClid) {
            if (carState != null && carClid != null && carClid >= clid) {
                if (carState.isError()) {
                    failCommandWithCarError(cm);
                    return IdleState;
                }
                if (carState.isBusy()) {
                    succeedCommand(cm);
                    return IdleState;
                }
            }
            return new WaitStart(cm, clid, carState, carClid);
        }

    }

    private synchronized void onApplyValChange(final Integer val) {
        if (val != null) {
            State oldState = currentState;
            currentState = currentState.onApplyValChange(val);
            if (currentState.equals(IdleState) && timeoutFuture != null) {
                timeoutFuture.cancel(true);
                timeoutFuture = null;
            }
            if(trace) LOG.debug("onApplyValChange(" + val + "): " + oldState.signature() + " -> " + currentState.signature());
        }
    }

    private synchronized void onCarClidChange(final Integer val) {
        if (val != null) {
            State oldState = currentState;
            currentState = currentState.onCarClidChange(val);
            if (currentState.equals(IdleState) && timeoutFuture != null) {
                timeoutFuture.cancel(true);
                timeoutFuture = null;
            }
            if(trace) LOG.debug("onCarClidChange(" + val + "): " + oldState.signature() + " -> " + currentState.signature());
        }
    }

    private synchronized void onCarValChange(final C carState) {
        if (carState != null) {
            State oldState = currentState;
            currentState = currentState.onCarValChange(carState);
            if (currentState.equals(IdleState) && timeoutFuture != null) {
                timeoutFuture.cancel(true);
                timeoutFuture = null;
            }
            if(trace) LOG.debug("onCarValChange(" + carState + "): " + oldState.signature() + " -> " + currentState.signature());
        }
    }

    private synchronized void onTimeout() {
        timeoutFuture = null;
        State oldState = currentState;
        currentState = currentState.onTimeout();
        if(trace) LOG.debug("onTimeout: " + oldState.signature() + " -> " + currentState.signature());
    }

    @Override
    public synchronized boolean isActive() {
        return !currentState.equals(IdleState);
    }

    @Override
    public synchronized void setTimeout(final long timeout, final TimeUnit timeUnit) {
        this.timeout = timeout;
        this.timeoutUnit = timeUnit;
    }

    private void succeedCommand(final CaCommandMonitorImpl cm) {
        executor.execute(cm::completeSuccess);
    }

    private void failCommand(final CaCommandMonitorImpl cm, final Exception ex) {
        executor.execute(() -> cm.completeFailure(ex));
    }

    private void failCommandWithApplyError(final CaCommandMonitorImpl cm) {
        // I found that if I try to read OMSS or MESS from the same thread that
        // is processing a channel notifications, the reads fails with a
        // timeout. But it works if the read is done later from another thread.
        executor.execute(() -> {
            String msg = null;
            try {
                msg = apply.getMessValue();
            } catch (CAException | TimeoutException e) {
                LOG.warn(e.getMessage());
            }
            cm.completeFailure(new CaCommandError(msg));
        });
    }

    private void failCommandWithCarError(final CaCommandMonitorImpl cm) {
        // I found that if I try to read OMSS or MESS from the same thread that
        // is processing a channel notifications, the reads fails with a
        // timeout. But it works if the read is done later from another thread.
        executor.execute(() -> {
            String msg = null;
            try {
                msg = car.getOmssValue();
            } catch (CAException | TimeoutException e) {
                LOG.warn(e.getMessage());
            }
            cm.completeFailure(new CaCommandError(msg));
        });
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void clear() throws TimeoutException {
        try {
            apply.setDir(CadDirective.CLEAR);
        } catch (CAException e) {
            LOG.warn(e.getMessage());
        }
    }

}
