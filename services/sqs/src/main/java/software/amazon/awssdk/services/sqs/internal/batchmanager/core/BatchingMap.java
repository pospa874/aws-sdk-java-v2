/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.awssdk.services.sqs.internal.batchmanager.core;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import software.amazon.awssdk.annotations.SdkInternalApi;

/**
 * Outer map maps a batchKey (ex. queueUrl, overrideConfig etc.) to a nested BatchingGroupMap map.
 *
 * @param <RequestT> the type of an outgoing response
 */
@SdkInternalApi
public final class BatchingMap<RequestT, ResponseT> {

    private final int maxBatchKeys;
    private final int maxBufferSize;

    private final BiFunction<Integer, ScheduledFuture<?>, BatchBuffer<RequestT, ResponseT>> bufferSupplier;
    private final Map<String, BatchBuffer<RequestT, ResponseT>> batchContextMap;

    public BatchingMap(int maxBatchKeys, int maxBufferSize,
                       BiFunction<Integer, ScheduledFuture<?>, BatchBuffer<RequestT, ResponseT>> bufferSupplier) {
        this.batchContextMap = new ConcurrentHashMap<>();
        this.maxBatchKeys = maxBatchKeys;
        this.maxBufferSize = maxBufferSize;
        this.bufferSupplier = bufferSupplier;
    }

    public void put(String batchKey, Supplier<ScheduledFuture<?>> scheduleFlush, RequestT request,
                    CompletableFuture<ResponseT> response) throws IllegalStateException {
        batchContextMap.computeIfAbsent(batchKey, k -> {
            if (batchContextMap.size() == maxBatchKeys) {
                throw new IllegalStateException("Reached MaxBatchKeys of: " + maxBatchKeys);
            }
            return bufferSupplier.apply(maxBufferSize, scheduleFlush.get());
        }).put(request, response);
    }

    public void putScheduledFlush(String batchKey, ScheduledFuture<?> scheduledFlush) {
        batchContextMap.get(batchKey).putScheduledFlush(scheduledFlush);
    }

    public void forEach(BiConsumer<String, BatchBuffer<RequestT, ResponseT>> action) {
        batchContextMap.forEach(action);
    }

    public Map<String, BatchingExecutionContext<RequestT, ResponseT>> flushableRequests(String batchKey,
                                                                                        int maxBatchItems) {
        return batchContextMap.get(batchKey).flushableRequests(maxBatchItems);
    }

    public Map<String, BatchingExecutionContext<RequestT, ResponseT>> flushableScheduledRequests(String batchKey,
                                                                                                 int maxBatchItems) {
        return batchContextMap.get(batchKey).flushableScheduledRequests(maxBatchItems);
    }

    public void cancelScheduledFlush(String batchKey) {
        batchContextMap.get(batchKey).cancelScheduledFlush();
    }

    public void clear() {
        for (Map.Entry<String, BatchBuffer<RequestT, ResponseT>> entry : batchContextMap.entrySet()) {
            String key = entry.getKey();
            entry.getValue().clear();
            batchContextMap.remove(key);
        }
        batchContextMap.clear();
    }
}