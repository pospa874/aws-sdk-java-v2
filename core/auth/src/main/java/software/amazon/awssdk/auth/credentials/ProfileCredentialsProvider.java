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

package software.amazon.awssdk.auth.credentials;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import software.amazon.awssdk.annotations.SdkPublicApi;
import software.amazon.awssdk.annotations.SdkTestInternalApi;
import software.amazon.awssdk.auth.credentials.internal.ProfileCredentialsUtils;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.identity.spi.AwsCredentialsIdentity;
import software.amazon.awssdk.identity.spi.AwsSessionCredentialsIdentity;
import software.amazon.awssdk.identity.spi.IdentityProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.ProfileFileLocation;
import software.amazon.awssdk.profiles.ProfileFileSupplier;
import software.amazon.awssdk.profiles.ProfileFileSystemSetting;
import software.amazon.awssdk.utils.IoUtils;
import software.amazon.awssdk.utils.SdkAutoCloseable;
import software.amazon.awssdk.utils.ToString;
import software.amazon.awssdk.utils.builder.CopyableBuilder;
import software.amazon.awssdk.utils.builder.ToCopyableBuilder;

/**
 * Credentials provider based on AWS configuration profiles. This loads credentials from a {@link ProfileFile}, allowing you to
 * share multiple sets of AWS security credentials between different tools like the AWS SDK for Java and the AWS CLI.
 *
 * <p>See http://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html</p>
 *
 * <p>If this credentials provider is loading assume-role credentials from STS, it should be cleaned up with {@link #close()} if
 * it is no longer being used.</p>
 *
 * @see ProfileFile
 */
/**
 * {@link IdentityProvider}{@code <}{@link AwsCredentialsIdentity}{@code >} that loads credentials from a {@link ProfileFile} in
 * {@code ~/.aws/config} and {@link ~/.aws/credentials}.
 *
 * <p>
 * This class process the profile files and delegate their configuration to other credential providers, based on the profile
 * files' contents. For a full guide on how to configure SDK credentials using a profile file, see
 * <a href="https://docs.aws.amazon.com/sdkref/latest/guide/file-format.html">the configuration file guide</a>.
 * The SDK determines which credential provider to delegate to based on the following ordered logic:
 * <ol>
 *     <li><b>{@link WebIdentityTokenFileCredentialsProvider}</b>: Used if the file contains {@code role_arn} and {@code
 *     web_identity_token_file}.</li>
 *     <li><b>{@link software.amazon.awssdk.services.sso.auth.SsoCredentialsProvider}</b>: Used if the file contains
 *     {@code sso_*} properties.</li>
 *     <li><b>{@link software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider}</b>: Used if the file contains the
 *     {@code role_arn} property.</li>
 *     <li><b>{@link ProcessCredentialsProvider}</b>: Used if the file contains the {@code credential_process} property.</li>
 *     <li><b>{@link StaticCredentialsProvider}</b> with <b><{@link AwsSessionCredentialsIdentity}</b>: Used if the file contains
 *     the {@code aws_session_token} property. </li>
 *     <li><b>{@link StaticCredentialsProvider}</b> with <b><{@link AwsCredentialsIdentity}</b>: Used if the file contains the
 *     {@code aws_access_key_id} property.</li>
 * </ol>
 *
 * <p>
 * There are system properties and environment variables that can control the behavior of this credential provider:
 * <ul>
 *     <li>The {@code aws.configFile} system property or {@code AWS_CONFIG_FILE} environment
 *     variable can be set to override the default location of the config file ({@code ~/.aws/config}).</li>
 *     <li>The {@code aws.sharedCredentialsFile} system property or {@code AWS_SHARED_CREDENTIALS_FILE} environment
 *     variable can be set to override the default location of the credentials file ({@code ~/.aws/credentials}).</li>
 *     <li>The {@code aws.profile} system property or {@code AWS_PROFILE} environment
 *     variable can be set to override the default profile used (literally, {@code default}).</li>
 *     <li>The {@code HOME} environment variable can be set to override the way the SDK interprets {@code ~/} in the
 *     configuration file or credentials file location. If {@code HOME} is not set, on Windows the SDK will also check
 *     {@code USERPROFILE}, and {@code HOMEDRIVE} + {@code HOMEPATH}. If none of these are set, on all platforms the SDK will
 *     then use the {@code user.home} system property.</li>
 * </ul>
 * <p>
 * This credential provider reads the profile once will not be updated if the file is changed. To monitor the file
 * for updates, you can provide a {@link ProfileFileSupplier} with {@link Builder#profileFile(Supplier)}.
 * <p>
 * This credentials provider is included in the {@link DefaultCredentialsProvider}.
 * <p>
 * This can be created using {@link #create()} or {@link #builder()}:
 * {@snippet :
 * ProfileCredentialsProvider credentialsProvider =
 *    ProfileCredentialsProvider.create();
 *
 * // or
 *
 * ProfileCredentialsProvider credentialsProvider =
 *     ProfileCredentialsProvider.create("custom-profile-name");
 *
 * // or
 *
 * ProfileCredentialsProvider credentialsProvider =
 *     ProfileCredentialsProvider.builder()
 *                               .profileFile(ProfileFile.defaultProfileFile())
 *                               .profileName("custom-profile-name")
 *                               .build();
 *
 * S3Client s3 = S3Client.builder()
 *                       .credentialsProvider(credentialsProvider)
 *                       .build();
 * }
 */
@SdkPublicApi
public final class ProfileCredentialsProvider
    implements AwsCredentialsProvider,
               SdkAutoCloseable,
               ToCopyableBuilder<ProfileCredentialsProvider.Builder, ProfileCredentialsProvider> {

    private volatile AwsCredentialsProvider credentialsProvider;
    private final RuntimeException loadException;
    private final Supplier<ProfileFile> profileFile;
    private volatile ProfileFile currentProfileFile;
    private final String profileName;
    private final Supplier<ProfileFile> defaultProfileFileLoader;

    private final Object credentialsProviderLock = new Object();

    /**
     * @see #builder()
     */
    private ProfileCredentialsProvider(BuilderImpl builder) {
        this.defaultProfileFileLoader = builder.defaultProfileFileLoader;

        RuntimeException thrownException = null;
        String selectedProfileName = null;
        Supplier<ProfileFile> selectedProfileSupplier = null;

        try {
            selectedProfileName = Optional.ofNullable(builder.profileName)
                                          .orElseGet(ProfileFileSystemSetting.AWS_PROFILE::getStringValueOrThrow);
            selectedProfileSupplier =
                Optional.ofNullable(builder.profileFile)
                        .orElseGet(() -> ProfileFileSupplier.fixedProfileFile(builder.defaultProfileFileLoader.get()));

        } catch (RuntimeException e) {
            // If we couldn't load the credentials provider for some reason, save an exception describing why. This exception
            // will only be raised on calls to resolveCredentials. We don't want to raise an exception here because it may be
            // expected (eg. in the default credential chain).
            thrownException = e;
        }

        this.loadException = thrownException;
        this.profileName = selectedProfileName;
        this.profileFile = selectedProfileSupplier;
    }

    /**
     * Create a {@link ProfileCredentialsProvider} using the {@link ProfileFile#defaultProfileFile()} and default profile name.
     * Use {@link #builder()} for defining a custom {@link ProfileCredentialsProvider}.
     */
    public static ProfileCredentialsProvider create() {
        return builder().build();
    }

    /**
     * Create a {@link ProfileCredentialsProvider} using the given profile name and {@link ProfileFile#defaultProfileFile()}. Use
     * {@link #builder()} for defining a custom {@link ProfileCredentialsProvider}.
     *
     * @param profileName the name of the profile to use from the {@link ProfileFile#defaultProfileFile()}
     */
    public static ProfileCredentialsProvider create(String profileName) {
        return builder().profileName(profileName).build();
    }

    /**
     * Get a builder for creating a custom {@link ProfileCredentialsProvider}.
     */
    public static Builder builder() {
        return new BuilderImpl();
    }

    @Override
    public AwsCredentials resolveCredentials() {
        if (loadException != null) {
            throw loadException;
        }

        ProfileFile cachedOrRefreshedProfileFile = refreshProfileFile();
        if (shouldUpdateCredentialsProvider(cachedOrRefreshedProfileFile)) {
            synchronized (credentialsProviderLock) {
                if (shouldUpdateCredentialsProvider(cachedOrRefreshedProfileFile)) {
                    currentProfileFile = cachedOrRefreshedProfileFile;
                    handleProfileFileReload(cachedOrRefreshedProfileFile);
                }
            }
        }

        return credentialsProvider.resolveCredentials();
    }

    private void handleProfileFileReload(ProfileFile profileFile) {
        credentialsProvider = createCredentialsProvider(profileFile, profileName);
    }

    private ProfileFile refreshProfileFile() {
        return profileFile.get();
    }

    private boolean shouldUpdateCredentialsProvider(ProfileFile profileFile) {
        return credentialsProvider == null || !Objects.equals(currentProfileFile, profileFile);
    }

    @Override
    public String toString() {
        return ToString.builder("ProfileCredentialsProvider")
                       .add("profileName", profileName)
                       .add("profileFile", currentProfileFile)
                       .build();
    }

    @Override
    public void close() {
        // The delegate credentials provider may be closeable (eg. if it's an STS credentials provider). In this case, we should
        // clean it up when this credentials provider is closed.
        IoUtils.closeIfCloseable(credentialsProvider, null);
    }

    @Override
    public Builder toBuilder() {
        return new BuilderImpl(this);
    }

    private AwsCredentialsProvider createCredentialsProvider(ProfileFile profileFile, String profileName) {
        // Load the profile and credentials provider
        return profileFile.profile(profileName)
                          .flatMap(p -> new ProfileCredentialsUtils(profileFile, p, profileFile::profile).credentialsProvider())
                          .orElseThrow(() -> {
                              String errorMessage = String.format("Profile file contained no credentials for " +
                                                                  "profile '%s': %s", profileName, profileFile);
                              return SdkClientException.builder().message(errorMessage).build();
                          });
    }

    /**
     * A builder for creating a custom {@link ProfileCredentialsProvider}.
     */
    public interface Builder extends CopyableBuilder<Builder, ProfileCredentialsProvider> {

        /**
         * Define the profile file that should be used by this credentials provider. By default, the
         * {@link ProfileFile#defaultProfileFile()} is used.
         * @see #profileFile(Supplier)
         */
        Builder profileFile(ProfileFile profileFile);

        /**
         * Similar to {@link #profileFile(ProfileFile)}, but takes a lambda to configure a new {@link ProfileFile.Builder}. This
         * removes the need to called {@link ProfileFile#builder()} and {@link ProfileFile.Builder#build()}.
         */
        Builder profileFile(Consumer<ProfileFile.Builder> profileFile);

        /**
         * Define the mechanism for loading profile files.
         *
         * @param profileFileSupplier Supplier interface for generating a ProfileFile instance.
         * @see #profileFile(ProfileFile) 
         */
        Builder profileFile(Supplier<ProfileFile> profileFileSupplier);

        /**
         * Define the name of the profile that should be used by this credentials provider. By default, the value in
         * {@link ProfileFileSystemSetting#AWS_PROFILE} is used.
         */
        Builder profileName(String profileName);

        /**
         * Create a {@link ProfileCredentialsProvider} using the configuration applied to this builder.
         */
        @Override
        ProfileCredentialsProvider build();
    }

    static final class BuilderImpl implements Builder {
        private Supplier<ProfileFile> profileFile;
        private String profileName;
        private Supplier<ProfileFile> defaultProfileFileLoader = ProfileFile::defaultProfileFile;

        BuilderImpl() {
        }

        BuilderImpl(ProfileCredentialsProvider provider) {
            this.profileName = provider.profileName;
            this.defaultProfileFileLoader = provider.defaultProfileFileLoader;
            this.profileFile = provider.profileFile;
        }

        @Override
        public Builder profileFile(ProfileFile profileFile) {
            return profileFile(Optional.ofNullable(profileFile)
                                       .map(ProfileFileSupplier::fixedProfileFile)
                                       .orElse(null));
        }

        public void setProfileFile(ProfileFile profileFile) {
            profileFile(profileFile);
        }

        @Override
        public Builder profileFile(Consumer<ProfileFile.Builder> profileFile) {
            return profileFile(ProfileFile.builder().applyMutation(profileFile).build());
        }

        @Override
        public Builder profileFile(Supplier<ProfileFile> profileFileSupplier) {
            this.profileFile = profileFileSupplier;
            return this;
        }

        public void setProfileFile(Supplier<ProfileFile> supplier) {
            profileFile(supplier);
        }

        @Override
        public Builder profileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        public void setProfileName(String profileName) {
            profileName(profileName);
        }

        @Override
        public ProfileCredentialsProvider build() {
            return new ProfileCredentialsProvider(this);
        }

        /**
         * Override the default configuration file to be used when the customer does not explicitly set
         * profileFile(ProfileFile) or profileFileSupplier(supplier);
         * {@link #profileFile(ProfileFile)}. Use of this method is
         * only useful for testing the default behavior.
         */
        @SdkTestInternalApi
        Builder defaultProfileFileLoader(Supplier<ProfileFile> defaultProfileFileLoader) {
            this.defaultProfileFileLoader = defaultProfileFileLoader;
            return this;
        }
    }

}
