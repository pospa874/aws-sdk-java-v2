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

package software.amazon.awssdk.enhanced.dynamodb.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.annotations.NotThreadSafe;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.annotations.ThreadSafe;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.model.ConsumedCapacity;
import software.amazon.awssdk.services.dynamodb.model.ItemCollectionMetrics;

/**
 * Defines the elements returned by DynamoDB from a {@code TransactWriteItemsOperation} operation, such as
 * {@link DynamoDbEnhancedClient#transactWriteItems(TransactWriteItemsEnhancedRequest)}
 */
@SdkPublicApi
@ThreadSafe
public final class TransactWriteItemsEnhancedResponse<T> {
    private final T items;
    private final List<ConsumedCapacity> consumedCapacity;
    private final Map<String, List<ItemCollectionMetrics>> itemCollectionMetrics;

    private TransactWriteItemsEnhancedResponse(Builder<T> builder) {
        this.items = builder.items;
        this.consumedCapacity = builder.consumedCapacity;
        this.itemCollectionMetrics = builder.itemCollectionMetrics;
    }

    public T items() {
        return items;
    }

    /**
     * The capacity units consumed by the {@code UpdateItem} operation.
     *
     * @see TransactWriteItemsEnhancedResponse#consumedCapacity() for more information.
     */
    public List<ConsumedCapacity> consumedCapacity() {
        return consumedCapacity;
    }

    /**
     * Information about item collections, if any, that were affected by the {@code UpdateItem} operation.
     *
     * @see TransactWriteItemsEnhancedResponse#itemCollectionMetrics() for more information.
     */
    public Map<String, List<ItemCollectionMetrics>> itemCollectionMetrics() {
        return itemCollectionMetrics;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TransactWriteItemsEnhancedResponse<?> that = (TransactWriteItemsEnhancedResponse<?>) o;
        return Objects.equals(items, that.items) &&
               Objects.equals(consumedCapacity, that.consumedCapacity) &&
               Objects.equals(itemCollectionMetrics, that.itemCollectionMetrics);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(items);
        result = 31 * result + Objects.hashCode(consumedCapacity);
        result = 31 * result + Objects.hashCode(itemCollectionMetrics);
        return result;
    }

    /**
     * Creates a newly initialized builder for a request object.
     */
    public static <T> TransactWriteItemsEnhancedResponse.Builder<T> builder(Class<? extends T> clzz) {
        return new TransactWriteItemsEnhancedResponse.Builder<>();
    }

    @NotThreadSafe
    public static final class Builder<T> {
        public T items;
        private List<ConsumedCapacity> consumedCapacity;
        private Map<String, List<ItemCollectionMetrics>> itemCollectionMetrics;

        public Builder<T> items(T items) {
            this.items = items;
            return this;
        }

        public Builder<T> consumedCapacity(List<ConsumedCapacity> consumedCapacity) {
            this.consumedCapacity = consumedCapacity;
            return this;
        }

        public Builder<T> itemCollectionMetrics(Map<String, List<ItemCollectionMetrics>> itemCollectionMetrics) {
            this.itemCollectionMetrics = itemCollectionMetrics;
            return this;
        }

        public TransactWriteItemsEnhancedResponse<T> build() {
            return new TransactWriteItemsEnhancedResponse<T>(this);
        }
    }
}