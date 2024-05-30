package software.amazon.awssdk.enhanced.dynamodb.functionaltests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.bind.ValidationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.extensions.AutoGeneratedTimestampRecordExtension;
import software.amazon.awssdk.enhanced.dynamodb.functionaltests.models.NestedRecordWithUpdateBehavior;
import software.amazon.awssdk.enhanced.dynamodb.functionaltests.models.RecordWithUpdateBehaviors;
import software.amazon.awssdk.enhanced.dynamodb.internal.client.ExtensionResolver;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

public class UpdateBehaviorTest extends LocalDynamoDbSyncTestBase {
    private static final Instant INSTANT_1 = Instant.parse("2020-05-03T10:00:00Z");
    private static final Instant INSTANT_2 = Instant.parse("2020-05-03T10:05:00Z");
    private static final Instant FAR_FUTURE_INSTANT = Instant.parse("9999-05-03T10:05:00Z");

    private static final TableSchema<RecordWithUpdateBehaviors> TABLE_SCHEMA =
            TableSchema.fromClass(RecordWithUpdateBehaviors.class);

    private final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(getDynamoDbClient()).extensions(
            Stream.concat(ExtensionResolver.defaultExtensions().stream(),
                          Stream.of(AutoGeneratedTimestampRecordExtension.create())).collect(Collectors.toList()))
            .build();

    private final DynamoDbTable<RecordWithUpdateBehaviors> mappedTable =
            enhancedClient.table(getConcreteTableName("table-name"), TABLE_SCHEMA);

    @Before
    public void createTable() {
        mappedTable.createTable(r -> r.provisionedThroughput(getDefaultProvisionedThroughput()));
    }

    @After
    public void deleteTable() {
        getDynamoDbClient().deleteTable(r -> r.tableName(getConcreteTableName("table-name")));
    }

    @Test
    public void updateBehaviors_firstUpdate() {
        Instant currentTime = Instant.now();
        RecordWithUpdateBehaviors record = new RecordWithUpdateBehaviors();
        record.setId("id123");
        record.setCreatedOn(INSTANT_1);
        record.setLastUpdatedOn(INSTANT_2);
        mappedTable.updateItem(record);

        RecordWithUpdateBehaviors persistedRecord = mappedTable.getItem(record);

        assertThat(persistedRecord.getVersion()).isEqualTo(1L);

        assertThat(persistedRecord.getCreatedOn()).isEqualTo(INSTANT_1);
        assertThat(persistedRecord.getLastUpdatedOn()).isEqualTo(INSTANT_2);
        assertThat(persistedRecord.getLastAutoUpdatedOn()).isAfterOrEqualTo(currentTime);
        assertThat(persistedRecord.getFormattedLastAutoUpdatedOn().getEpochSecond())
            .isGreaterThanOrEqualTo(currentTime.getEpochSecond());

        assertThat(persistedRecord.getLastAutoUpdatedOnMillis().getEpochSecond()).isGreaterThanOrEqualTo(currentTime.getEpochSecond());
        assertThat(persistedRecord.getCreatedAutoUpdateOn()).isAfterOrEqualTo(currentTime);
    }

    @Test
    public void updateBehaviors_secondUpdate() {
        Instant beforeUpdateInstant = Instant.now();
        RecordWithUpdateBehaviors record = new RecordWithUpdateBehaviors();
        record.setId("id123");
        record.setCreatedOn(INSTANT_1);
        record.setLastUpdatedOn(INSTANT_2);
        mappedTable.updateItem(record);
        RecordWithUpdateBehaviors persistedRecord = mappedTable.getItem(record);

        assertThat(persistedRecord.getVersion()).isEqualTo(1L);
        Instant firstUpdatedTime = persistedRecord.getLastAutoUpdatedOn();
        Instant createdAutoUpdateOn = persistedRecord.getCreatedAutoUpdateOn();
        assertThat(firstUpdatedTime).isAfterOrEqualTo(beforeUpdateInstant);
        assertThat(persistedRecord.getFormattedLastAutoUpdatedOn().getEpochSecond())
            .isGreaterThanOrEqualTo(beforeUpdateInstant.getEpochSecond());

        record.setVersion(1L);
        record.setCreatedOn(INSTANT_2);
        record.setLastUpdatedOn(INSTANT_2);
        mappedTable.updateItem(record);

        persistedRecord = mappedTable.getItem(record);
        assertThat(persistedRecord.getVersion()).isEqualTo(2L);
        assertThat(persistedRecord.getCreatedOn()).isEqualTo(INSTANT_1);
        assertThat(persistedRecord.getLastUpdatedOn()).isEqualTo(INSTANT_2);

        Instant secondUpdatedTime = persistedRecord.getLastAutoUpdatedOn();
        assertThat(secondUpdatedTime).isAfterOrEqualTo(firstUpdatedTime);
        assertThat(persistedRecord.getCreatedAutoUpdateOn()).isEqualTo(createdAutoUpdateOn);
    }

    @Test
    public void updateBehaviors_removal() {
        RecordWithUpdateBehaviors record = new RecordWithUpdateBehaviors();
        record.setId("id123");
        record.setCreatedOn(INSTANT_1);
        record.setLastUpdatedOn(INSTANT_2);
        record.setLastAutoUpdatedOn(FAR_FUTURE_INSTANT);
        mappedTable.updateItem(record);
        RecordWithUpdateBehaviors persistedRecord = mappedTable.getItem(record);
        Instant createdAutoUpdateOn = persistedRecord.getCreatedAutoUpdateOn();
        assertThat(persistedRecord.getLastAutoUpdatedOn()).isBefore(FAR_FUTURE_INSTANT);

        record.setVersion(1L);
        record.setCreatedOn(null);
        record.setLastUpdatedOn(null);
        record.setLastAutoUpdatedOn(null);
        mappedTable.updateItem(record);

        persistedRecord = mappedTable.getItem(record);
        assertThat(persistedRecord.getCreatedOn()).isNull();
        assertThat(persistedRecord.getLastUpdatedOn()).isNull();
        assertThat(persistedRecord.getLastAutoUpdatedOn()).isNotNull();
        assertThat(persistedRecord.getCreatedAutoUpdateOn()).isEqualTo(createdAutoUpdateOn);
    }

    @Test
    public void updateBehaviors_transactWriteItems_secondUpdate() {
        RecordWithUpdateBehaviors record = new RecordWithUpdateBehaviors();
        record.setId("id123");
        record.setCreatedOn(INSTANT_1);
        record.setLastUpdatedOn(INSTANT_2);
        record.setLastAutoUpdatedOn(INSTANT_2);
        RecordWithUpdateBehaviors firstUpdatedRecord = mappedTable.updateItem(record);

        record.setVersion(1L);
        record.setCreatedOn(INSTANT_2);
        record.setLastUpdatedOn(INSTANT_2);
        record.setLastAutoUpdatedOn(INSTANT_2);
        enhancedClient.transactWriteItems(r -> r.addUpdateItem(mappedTable, record));

        RecordWithUpdateBehaviors persistedRecord = mappedTable.getItem(record);
        assertThat(persistedRecord.getCreatedOn()).isEqualTo(INSTANT_1);
        assertThat(persistedRecord.getLastUpdatedOn()).isEqualTo(INSTANT_2);
        assertThat(persistedRecord.getLastAutoUpdatedOn()).isAfterOrEqualTo(INSTANT_2);
        assertThat(persistedRecord.getCreatedAutoUpdateOn()).isEqualTo(firstUpdatedRecord.getCreatedAutoUpdateOn());
    }

    @Test
    public void updateBehaviors_nested() {

        NestedRecordWithUpdateBehavior nestedRecord = new NestedRecordWithUpdateBehavior();
        nestedRecord.setId("id456");
        nestedRecord.setNestedCounter(5L);
        nestedRecord.setNestedUpdateBehaviorAttribute("TEST_BEHAVIOUR_ATTRIBUTE");
        nestedRecord.setNestedTimeAttribute(INSTANT_1);

        RecordWithUpdateBehaviors record = new RecordWithUpdateBehaviors();
        record.setId("id123");
        record.setNestedRecord(nestedRecord);

        mappedTable.putItem(record);

        NestedRecordWithUpdateBehavior updatedNestedRecord = new NestedRecordWithUpdateBehavior();
        updatedNestedRecord.setNestedCounter(10L);

        RecordWithUpdateBehaviors update_record = new RecordWithUpdateBehaviors();
        update_record.setId("id123");
        update_record.setVersion(1L);
        update_record.setNestedRecord(updatedNestedRecord);

        mappedTable.updateItem(r -> r.item(update_record).ignoreNulls(true));

        RecordWithUpdateBehaviors persistedRecord = mappedTable.getItem(r -> r.key(k -> k.partitionValue("id123")));

        assertThat(persistedRecord.getVersion()).isEqualTo(2L);
        assertThat(persistedRecord.getNestedRecord()).isNotNull();
        assertThat(persistedRecord.getNestedRecord().getNestedCounter()).isEqualTo(10L);
        assertThat(persistedRecord.getNestedRecord().getNestedUpdateBehaviorAttribute()).isEqualTo("TEST_BEHAVIOUR_ATTRIBUTE");
        assertThat(persistedRecord.getNestedRecord().getNestedTimeAttribute()).isEqualTo(INSTANT_1);
    }

    @Test
    public void updateBehaviors_multi_level_nested() {

        NestedRecordWithUpdateBehavior nestedRecord1 = new NestedRecordWithUpdateBehavior();
        nestedRecord1.setId("id789");
        nestedRecord1.setNestedCounter(50L);
        nestedRecord1.setNestedUpdateBehaviorAttribute("TEST_BEHAVIOUR_ATTRIBUTE");
        nestedRecord1.setNestedTimeAttribute(INSTANT_1);

        NestedRecordWithUpdateBehavior nestedRecord2 = new NestedRecordWithUpdateBehavior();
        nestedRecord2.setId("id456");
        nestedRecord2.setNestedCounter(5L);
        nestedRecord2.setNestedUpdateBehaviorAttribute("TEST_BEHAVIOUR_ATTRIBUTE");
        nestedRecord2.setNestedTimeAttribute(INSTANT_1);
        nestedRecord2.setNestedRecord(nestedRecord1);

        RecordWithUpdateBehaviors record = new RecordWithUpdateBehaviors();
        record.setId("id123");
        record.setNestedRecord(nestedRecord2);

        mappedTable.putItem(record);

        NestedRecordWithUpdateBehavior updatedNestedRecord2 = new NestedRecordWithUpdateBehavior();
        updatedNestedRecord2.setNestedCounter(100L);

        NestedRecordWithUpdateBehavior updatedNestedRecord1 = new NestedRecordWithUpdateBehavior();
        updatedNestedRecord1.setNestedRecord(updatedNestedRecord2);

        RecordWithUpdateBehaviors update_record = new RecordWithUpdateBehaviors();
        update_record.setId("id123");
        update_record.setVersion(1L);
        update_record.setNestedRecord(updatedNestedRecord1);

        mappedTable.updateItem(r -> r.item(update_record).ignoreNulls(true));

        RecordWithUpdateBehaviors persistedRecord = mappedTable.getItem(r -> r.key(k -> k.partitionValue("id123")));

        assertThat(persistedRecord.getVersion()).isEqualTo(2L);
        assertThat(persistedRecord.getNestedRecord()).isNotNull();
        assertThat(persistedRecord.getNestedRecord().getNestedRecord()).isNotNull();
        assertThat(persistedRecord.getNestedRecord().getNestedRecord().getNestedCounter()).isEqualTo(100L);
        //assertThat(persistedRecord.getNestedRecord().getNestedRecord().getNestedUpdateBehaviorAttribute()).isEqualTo("TEST_BEHAVIOUR_ATTRIBUTE");
        assertThat(persistedRecord.getNestedRecord().getNestedRecord().getNestedTimeAttribute()).isEqualTo(INSTANT_1);
    }

    /**
     * Currently, nested records are not updated through extensions.
     */
    @Test
    public void updateNonexistentField_nested() {
        NestedRecordWithUpdateBehavior nestedRecord = new NestedRecordWithUpdateBehavior();
        nestedRecord.setId("id456");

        RecordWithUpdateBehaviors record = new RecordWithUpdateBehaviors();
        record.setId("id123");
        record.setCreatedOn(INSTANT_1);
        record.setLastUpdatedOn(INSTANT_2);
        record.setNestedRecord(nestedRecord);
        assertThatThrownBy(() ->mappedTable.updateItem(record))
            .isInstanceOf(DynamoDbException.class)
            .hasMessageContaining("The document path provided in the update expression is invalid for update");
    }
}
