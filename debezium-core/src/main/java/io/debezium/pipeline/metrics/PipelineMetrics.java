/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.pipeline.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.kafka.connect.data.Struct;

import io.debezium.annotation.ThreadSafe;
import io.debezium.connector.base.ChangeEventQueueMetrics;
import io.debezium.connector.common.CdcSourceTaskContext;
import io.debezium.data.Envelope.Operation;
import io.debezium.metrics.Metrics;
import io.debezium.pipeline.ConnectorEvent;
import io.debezium.pipeline.source.spi.DataChangeEventListener;
import io.debezium.pipeline.source.spi.EventMetadataProvider;
import io.debezium.pipeline.spi.OffsetContext;
import io.debezium.schema.DataCollectionId;
import io.debezium.util.Clock;

/**
 * Base for metrics implementations.
 *
 * @author Randall Hauch, Jiri Pechanec
 */
@ThreadSafe
public abstract class PipelineMetrics extends Metrics implements DataChangeEventListener, ChangeEventSourceMetricsMXBean {

    protected final EventMetadataProvider metadataProvider;
    protected final AtomicLong totalNumberOfEventsSeen = new AtomicLong();
    protected final AtomicLong totalNumberOfCreateEventsSeen = new AtomicLong();
    protected final AtomicLong totalNumberOfUpdateEventsSeen = new AtomicLong();
    protected final AtomicLong totalNumberOfDeleteEventsSeen = new AtomicLong();
    private final AtomicLong numberOfEventsFiltered = new AtomicLong();
    protected final AtomicLong numberOfErroneousEvents = new AtomicLong();
    protected final AtomicLong lastEventTimestamp = new AtomicLong(-1);
    private volatile String lastEvent;

    protected final Clock clock;
    private final ChangeEventQueueMetrics changeEventQueueMetrics;
    protected final CdcSourceTaskContext taskContext;

    protected <T extends CdcSourceTaskContext> PipelineMetrics(T taskContext, String contextName, ChangeEventQueueMetrics changeEventQueueMetrics,
                                                               EventMetadataProvider metadataProvider) {
        super(taskContext, contextName);
        this.taskContext = taskContext;
        this.clock = taskContext.getClock();
        this.changeEventQueueMetrics = changeEventQueueMetrics;
        this.metadataProvider = metadataProvider;
    }

    @Override
    public void onEvent(DataCollectionId source, OffsetContext offset, Object key, Struct value, Operation operation) {
        updateCommonEventMetrics(operation);
        lastEvent = metadataProvider.toSummaryString(source, offset, key, value);
    }

    private void updateCommonEventMetrics() {
        updateCommonEventMetrics(null);
    }

    private void updateCommonEventMetrics(Operation operation) {
        totalNumberOfEventsSeen.incrementAndGet();
        lastEventTimestamp.set(clock.currentTimeInMillis());

        if (operation != null) {
            switch (operation) {
                case CREATE:
                    totalNumberOfCreateEventsSeen.incrementAndGet();
                    break;
                case UPDATE:
                    totalNumberOfUpdateEventsSeen.incrementAndGet();
                    break;
                case DELETE:
                    totalNumberOfDeleteEventsSeen.incrementAndGet();
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onFilteredEvent(String event) {
        numberOfEventsFiltered.incrementAndGet();
        updateCommonEventMetrics();
    }

    @Override
    public void onFilteredEvent(String event, Operation operation) {
        numberOfEventsFiltered.incrementAndGet();
        updateCommonEventMetrics(operation);
    }

    @Override
    public void onErroneousEvent(String event) {
        numberOfErroneousEvents.incrementAndGet();
        updateCommonEventMetrics();
    }

    @Override
    public void onErroneousEvent(String event, Operation operation) {
        numberOfErroneousEvents.incrementAndGet();
        updateCommonEventMetrics(operation);
    }

    @Override
    public void onConnectorEvent(ConnectorEvent event) {
    }

    @Override
    public String getLastEvent() {
        return lastEvent;
    }

    @Override
    public long getMilliSecondsSinceLastEvent() {
        return (lastEventTimestamp.get() == -1) ? -1 : (clock.currentTimeInMillis() - lastEventTimestamp.get());
    }

    @Override
    public long getTotalNumberOfEventsSeen() {
        return totalNumberOfEventsSeen.get();
    }

    @Override
    public long getTotalNumberOfCreateEventsSeen() {
        return totalNumberOfCreateEventsSeen.get();
    }

    @Override
    public long getTotalNumberOfUpdateEventsSeen() {
        return totalNumberOfUpdateEventsSeen.get();
    }

    @Override
    public long getTotalNumberOfDeleteEventsSeen() {
        return totalNumberOfDeleteEventsSeen.get();
    }

    @Override
    public long getNumberOfEventsFiltered() {
        return numberOfEventsFiltered.get();
    }

    @Override
    public long getNumberOfErroneousEvents() {
        return numberOfErroneousEvents.get();
    }

    @Override
    public void reset() {
        totalNumberOfEventsSeen.set(0);
        totalNumberOfCreateEventsSeen.set(0);
        totalNumberOfUpdateEventsSeen.set(0);
        totalNumberOfDeleteEventsSeen.set(0);
        lastEventTimestamp.set(-1);
        numberOfEventsFiltered.set(0);
        numberOfErroneousEvents.set(0);
        lastEvent = null;
    }

    @Override
    public int getQueueTotalCapacity() {
        return changeEventQueueMetrics.totalCapacity();
    }

    @Override
    public int getQueueRemainingCapacity() {
        return changeEventQueueMetrics.remainingCapacity();
    }

    @Override
    public long getMaxQueueSizeInBytes() {
        return changeEventQueueMetrics.maxQueueSizeInBytes();
    }

    @Override
    public long getCurrentQueueSizeInBytes() {
        return changeEventQueueMetrics.currentQueueSizeInBytes();
    }

}
