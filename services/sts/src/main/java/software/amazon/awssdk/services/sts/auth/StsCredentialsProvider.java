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

package software.amazon.awssdk.services.sts.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import software.amazon.awssdk.annotations.NotThreadSafe;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.annotations.ThreadSafe;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.utils.Logger;
import software.amazon.awssdk.utils.SdkAutoCloseable;
import software.amazon.awssdk.utils.ToString;
import software.amazon.awssdk.utils.Validate;
import software.amazon.awssdk.utils.builder.CopyableBuilder;
import software.amazon.awssdk.utils.builder.ToCopyableBuilder;
import software.amazon.awssdk.utils.cache.CachedSupplier;
import software.amazon.awssdk.utils.cache.NonBlocking;
import software.amazon.awssdk.utils.cache.RefreshResult;


/**
 * An implementation of {@link AwsCredentialsProvider} that is extended within this package to provide support for periodically-
 * updated session credentials.
 *
 * <p>
 * See child classes for detailed usage documentation.
 */
@ThreadSafe
@SdkPublicApi
public abstract class StsCredentialsProvider implements AwsCredentialsProvider, SdkAutoCloseable {
    private static final Logger log = Logger.loggerFor(StsCredentialsProvider.class);

    private static final Duration DEFAULT_STALE_TIME = Duration.ofMinutes(1);
    private static final Duration DEFAULT_PREFETCH_TIME = Duration.ofMinutes(5);

    /**
     * The STS client that should be used for periodically updating the session credentials.
     */
    final StsClient stsClient;

    /**
     * The session cache that handles automatically updating the credentials when they get close to expiring.
     */
    private final CachedSupplier<AwsSessionCredentials> sessionCache;

    private final Duration staleTime;
    private final Duration prefetchTime;
    private final Boolean asyncCredentialUpdateEnabled;

    StsCredentialsProvider(BaseBuilder<?, ?> builder, String asyncThreadName) {
        this.stsClient = Validate.notNull(builder.stsClient, "STS client must not be null.");

        this.staleTime = Optional.ofNullable(builder.staleTime).orElse(DEFAULT_STALE_TIME);
        this.prefetchTime = Optional.ofNullable(builder.prefetchTime).orElse(DEFAULT_PREFETCH_TIME);

        this.asyncCredentialUpdateEnabled = builder.asyncCredentialUpdateEnabled;
        CachedSupplier.Builder<AwsSessionCredentials> cacheBuilder =
            CachedSupplier.builder(this::updateSessionCredentials)
                          .cachedValueName(toString());
        if (builder.asyncCredentialUpdateEnabled) {
            cacheBuilder.prefetchStrategy(new NonBlocking(asyncThreadName));
        }
        this.sessionCache = cacheBuilder.build();
    }

    /**
     * Update the expiring session credentials by calling STS. Invoked by {@link CachedSupplier} when the credentials
     * are close to expiring.
     */
    private RefreshResult<AwsSessionCredentials> updateSessionCredentials() {
        AwsSessionCredentials credentials = getUpdatedCredentials(stsClient);
        Instant actualTokenExpiration =
            credentials.expirationTime()
                       .orElseThrow(() -> new IllegalStateException("Sourced credentials have no expiration value"));

        return RefreshResult.builder(credentials)
                            .staleTime(actualTokenExpiration.minus(staleTime))
                            .prefetchTime(actualTokenExpiration.minus(prefetchTime))
                            .build();
    }

    @Override
    public AwsCredentials resolveCredentials() {
        AwsSessionCredentials credentials = sessionCache.get();
        credentials.expirationTime().ifPresent(t -> {
            log.debug(() -> "Using STS credentials with expiration time of " + t);
        });
        return credentials;
    }

    /**
     * Release resources held by this credentials provider. This must be called when you're done using the credentials provider if
     * {@link BaseBuilder#asyncCredentialUpdateEnabled(Boolean)} was set to {@code true}. This does not close the configured
     * {@link BaseBuilder#stsClient(StsClient)}.
     */
    @Override
    public void close() {
        sessionCache.close();
    }

    /**
     * @see BaseBuilder#staleTime(Duration)
     */
    public Duration staleTime() {
        return staleTime;
    }

    /**
     * @see BaseBuilder#prefetchTime(Duration)
     */
    public Duration prefetchTime() {
        return prefetchTime;
    }

    @Override
    public String toString() {
        return ToString.create(providerName());
    }

    /**
     * Implemented by a child class to call STS and get a new set of credentials to be used by this provider.
     */
    abstract AwsSessionCredentials getUpdatedCredentials(StsClient stsClient);

    abstract String providerName();

    /**
     * This is extended by child class's builders to share configuration across credential providers.
     */
    @NotThreadSafe
    @SdkPublicApi
    public abstract static class BaseBuilder<B extends BaseBuilder<B, T>, T extends ToCopyableBuilder<B, T>>
        implements CopyableBuilder<B, T> {
        private final Function<B, T> providerConstructor;

        private Boolean asyncCredentialUpdateEnabled = false;
        private StsClient stsClient;
        private Duration staleTime;
        private Duration prefetchTime;

        BaseBuilder(Function<B, T> providerConstructor) {
            this.providerConstructor = providerConstructor;
        }

        BaseBuilder(Function<B, T> providerConstructor, StsCredentialsProvider provider) {
            this.providerConstructor = providerConstructor;
            this.asyncCredentialUpdateEnabled = provider.asyncCredentialUpdateEnabled;
            this.stsClient = provider.stsClient;
            this.staleTime = provider.staleTime;
            this.prefetchTime = provider.prefetchTime;
        }

        /**
         * See child class documentation.
         */
        @SuppressWarnings("unchecked")
        public B stsClient(StsClient stsClient) {
            this.stsClient = stsClient;
            return (B) this;
        }

        /**
         * See child class documentation.
         */
        @SuppressWarnings("unchecked")
        public B asyncCredentialUpdateEnabled(Boolean asyncCredentialUpdateEnabled) {
            this.asyncCredentialUpdateEnabled = asyncCredentialUpdateEnabled;
            return (B) this;
        }

        /**
         * See child class documentation.
         */
        @SuppressWarnings("unchecked")
        public B staleTime(Duration staleTime) {
            this.staleTime = staleTime;
            return (B) this;
        }

        /**
         * See child class documentation.
         */
        @SuppressWarnings("unchecked")
        public B prefetchTime(Duration prefetchTime) {
            this.prefetchTime = prefetchTime;
            return (B) this;
        }
        
        /**
         * See child class documentation.
         */
        @SuppressWarnings("unchecked")
        public T build() {
            return providerConstructor.apply((B) this);
        }
    }
}
