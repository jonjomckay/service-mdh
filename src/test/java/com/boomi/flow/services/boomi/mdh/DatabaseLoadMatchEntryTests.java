package com.boomi.flow.services.boomi.mdh;

import com.boomi.flow.services.boomi.mdh.client.MdhClient;
import com.boomi.flow.services.boomi.mdh.database.MdhRawDatabase;
import com.boomi.flow.services.boomi.mdh.match.FuzzyMatchDetailsConstants;
import com.boomi.flow.services.boomi.mdh.match.MatchEntityRepository;
import com.boomi.flow.services.boomi.mdh.match.MatchEntityResponse;
import com.boomi.flow.services.boomi.mdh.quarantine.QuarantineRepository;
import com.boomi.flow.services.boomi.mdh.records.ElementIdFinder;
import com.boomi.flow.services.boomi.mdh.records.GoldenRecordRepository;
import com.boomi.flow.services.boomi.mdh.common.BatchUpdateRequest;
import com.boomi.flow.services.boomi.mdh.universes.Universe;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.manywho.sdk.api.run.elements.type.MObject;
import com.manywho.sdk.api.run.elements.type.ObjectDataType;
import com.manywho.sdk.api.run.elements.type.Property;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DatabaseLoadMatchEntryTests {
    @Mock
    private MdhClient client;

    private ObjectDataType objectDataType = new ObjectDataType()
            .setDeveloperName("12fa66f9-e14d-f642-878f-030b13b64731-match");

    @Test
    public void testLoadMatchEntityObjectsReturnsObject() {
        // Make sure we return the expected universe layout for the test
        when(client.findUniverse(any(), any(), any(), eq("12fa66f9-e14d-f642-878f-030b13b64731")))
                .thenReturn(new Universe()
                    .setId(UUID.fromString("12fa66f9-e14d-f642-878f-030b13b64731"))
                    .setName("testing")
                    .setLayout(new Universe.Layout()
                            .setIdXPath("/item/id")
                            .setModel(new Universe.Layout.Model()
                                    .setName("testing"))));

        when(client.queryMatchEntity(any(), any(), any(), eq("12fa66f9-e14d-f642-878f-030b13b64731"), eq(createBatchUpdateRequest())))
                .thenReturn(createMatchEntityResponse());

        // Update using the incoming object
        List<MObject> result = new MdhRawDatabase(new QuarantineRepository(client), new GoldenRecordRepository(client, new ElementIdFinder(null)),
                new MatchEntityRepository(client))
                .findAll(TestConstants.CONFIGURATION, objectDataType, null, null, Arrays.asList(createObjectToLoad1(), createObjectToLoad2()));

        verify(client)
                .queryMatchEntity(
                        TestConstants.CONFIGURATION.getHubHostname(),
                        TestConstants.CONFIGURATION.getHubUsername(),
                        TestConstants.CONFIGURATION.getHubToken(),
                        "12fa66f9-e14d-f642-878f-030b13b64731",
                        createBatchUpdateRequest()
                );

        assertThat(result.get(0), not(nullValue()));
        assertThat(result.get(0).getDeveloperName(), equalTo(objectDataType.getDeveloperName()));
        assertThat(result.get(0).getExternalId(), not(isEmptyOrNullString()));
        assertThat(result.get(0).getProperties(), hasSize(9));

        assertThat(result.get(0).getProperties().get(0).getDeveloperName(), equalTo("field 1"));
        assertThat(result.get(0).getProperties().get(0).getContentValue(), equalTo("some value 1"));

        assertThat(result.get(0).getProperties().get(1).getDeveloperName(), equalTo("field 2"));
        assertThat(result.get(0).getProperties().get(1).getContentValue(), equalTo("some value 2"));

        assertThat(result.get(0).getProperties().get(2).getDeveloperName(), equalTo("id"));
        assertThat(result.get(0).getProperties().get(2).getContentValue(), equalTo("4f23f8eb-984b-4e9b-9a52-d9ebaf11bb1"));

        assertThat(result.get(0).getProperties().get(3).getDeveloperName(), equalTo("field 3 1"));
        assertThat(result.get(0).getProperties().get(3).getContentValue(), nullValue());
        assertThat(result.get(0).getProperties().get(3).getObjectData(), notNullValue());
        assertThat(result.get(0).getProperties().get(3).getObjectData().get(0).getDeveloperName(), equalTo("field 3 1-child"));
        assertThat(result.get(0).getProperties().get(3).getObjectData().get(0).getProperties(), hasSize(1));
        assertThat(result.get(0).getProperties().get(3).getObjectData().get(0).getProperties().get(0).getDeveloperName(), equalTo("field 3 1 property"));

        assertThat(result.get(0).getProperties().get(4).getDeveloperName(), equalTo("___sourceId"));
        assertThat(result.get(0).getProperties().get(4).getContentValue(), equalTo("TESTING"));

        assertThat(result.get(0).getProperties().get(5).getDeveloperName(), equalTo(FuzzyMatchDetailsConstants.FUZZY_MATCH_DETAILS));

        // the root entity never have Fuzzy Match Details
        assertThat(result.get(0).getProperties().get(5).getObjectData(), hasSize(0));
        assertThat(result.get(0).getProperties().get(5).getContentValue(), nullValue());

        // matched entities
        Property matchedEntityProperty = result.get(0).getProperties().get(6);

        assertThat(matchedEntityProperty.getDeveloperName(), equalTo(FuzzyMatchDetailsConstants.MATCH));
        assertThat(matchedEntityProperty.getObjectData(), hasSize(1));
        assertThat(matchedEntityProperty.getObjectData().get(0).getDeveloperName(), equalTo("12fa66f9-e14d-f642-878f-030b13b64731-match"));

        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties(), hasSize(5));

        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(0).getDeveloperName(), equalTo("field 1"));
        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(0).getContentValue(), equalTo("some value 1"));

        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(1).getDeveloperName(), equalTo("field 2"));
        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(1).getContentValue(), equalTo("some value 2"));

        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(2).getDeveloperName(), equalTo("id"));
        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(2).getContentValue(), equalTo("4f23f8eb-984b-4e9b-9a52-d9ebaf11bb1"));

        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(3).getDeveloperName(), equalTo("field 3 1"));
        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(3).getContentValue(), nullValue());
        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(3).getObjectData().get(0).getDeveloperName(), equalTo("field 3 1-child"));
        assertThat(matchedEntityProperty.getObjectData().get(0).getProperties().get(3).getObjectData().get(0).getProperties().get(0).getContentValue(), equalTo("value property 3 value 1 1"));

        Property fuzzyMatchMatchedEntity = matchedEntityProperty.getObjectData().get(0).getProperties().get(4);
        assertThat(fuzzyMatchMatchedEntity.getDeveloperName(), equalTo(FuzzyMatchDetailsConstants.FUZZY_MATCH_DETAILS));
        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties(), hasSize(6));
        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(0).getDeveloperName(), equalTo("Field"));
        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(0).getContentValue(), equalTo("name"));

        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(1).getDeveloperName(), equalTo("First"));
        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(1).getContentValue(), equalTo("field 1"));

        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(2).getDeveloperName(), equalTo("Second"));
        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(2).getContentValue(), equalTo("field 2"));

        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(3).getDeveloperName(), equalTo("Method"));
        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(3).getContentValue(), equalTo("k"));

        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(4).getDeveloperName(), equalTo("Match Strength"));
        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(4).getContentValue(), equalTo("0.90666664"));

        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(5).getDeveloperName(), equalTo("Threshold"));
        assertThat(fuzzyMatchMatchedEntity.getObjectData().get(0).getProperties().get(5).getContentValue(), equalTo("0.85"));

        // duplicated entities
        Property duplicatedEntityProperty = result.get(0).getProperties().get(7);

        assertThat(duplicatedEntityProperty.getDeveloperName(), equalTo(FuzzyMatchDetailsConstants.DUPLICATE));
        assertThat(duplicatedEntityProperty.getObjectData(), hasSize(1));
        assertThat(duplicatedEntityProperty.getObjectData().get(0).getDeveloperName(), equalTo("12fa66f9-e14d-f642-878f-030b13b64731-match"));

        assertThat(duplicatedEntityProperty.getObjectData().get(0).getProperties().get(0).getDeveloperName(), equalTo("field 1"));
        assertThat(duplicatedEntityProperty.getObjectData().get(0).getProperties().get(0).getContentValue(), equalTo("some value 1"));

        assertThat(duplicatedEntityProperty.getObjectData().get(0).getProperties().get(1).getDeveloperName(), equalTo("field 2"));
        assertThat(duplicatedEntityProperty.getObjectData().get(0).getProperties().get(1).getContentValue(), equalTo("some value 2"));

        assertThat(duplicatedEntityProperty.getObjectData().get(0).getProperties().get(2).getDeveloperName(), equalTo("id"));
        assertThat(duplicatedEntityProperty.getObjectData().get(0).getProperties().get(2).getContentValue(), equalTo("4f23f8eb-984b-4e9b-9a52-d9ebaf11bb1"));

        Property fuzzyMatchDuplicatedEntity = duplicatedEntityProperty.getObjectData().get(0).getProperties().get(4);
        assertThat(fuzzyMatchDuplicatedEntity.getDeveloperName(), equalTo(FuzzyMatchDetailsConstants.FUZZY_MATCH_DETAILS));
        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties(), hasSize(6));
        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(0).getDeveloperName(), equalTo("Field"));
        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(0).getContentValue(), equalTo("name"));

        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(1).getDeveloperName(), equalTo("First"));
        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(1).getContentValue(), equalTo("field 1"));

        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(2).getDeveloperName(), equalTo("Second"));
        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(2).getContentValue(), equalTo("field 2"));

        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(3).getDeveloperName(), equalTo("Method"));
        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(3).getContentValue(), equalTo("k"));

        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(4).getDeveloperName(), equalTo("Match Strength"));
        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(4).getContentValue(), equalTo("0.90666664"));

        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(5).getDeveloperName(), equalTo("Threshold"));
        assertThat(fuzzyMatchDuplicatedEntity.getObjectData().get(0).getProperties().get(5).getContentValue(), equalTo("0.85"));

        // duplicated entities
        Property alreadyLinkedEntityProperty = result.get(1).getProperties().get(7);

        assertThat(alreadyLinkedEntityProperty.getDeveloperName(), equalTo(FuzzyMatchDetailsConstants.ALREADY_LINKED));
        assertThat(alreadyLinkedEntityProperty.getObjectData(), hasSize(1));
        assertThat(alreadyLinkedEntityProperty.getObjectData().get(0).getDeveloperName(), equalTo("12fa66f9-e14d-f642-878f-030b13b64731-match"));
        assertThat(alreadyLinkedEntityProperty.getObjectData().get(0).getProperties(), hasSize(3));
        assertThat(alreadyLinkedEntityProperty.getObjectData().get(0).getProperties().get(0).getDeveloperName(), equalTo("field 1"));
        assertThat(alreadyLinkedEntityProperty.getObjectData().get(0).getProperties().get(0).getContentValue(), equalTo("some value 1"));

        assertThat(alreadyLinkedEntityProperty.getObjectData().get(0).getProperties().get(1).getDeveloperName(), equalTo("field 2"));
        assertThat(alreadyLinkedEntityProperty.getObjectData().get(0).getProperties().get(1).getContentValue(), equalTo("some value 2"));

        assertThat(alreadyLinkedEntityProperty.getObjectData().get(0).getProperties().get(2).getDeveloperName(), equalTo("id"));
        assertThat(alreadyLinkedEntityProperty.getObjectData().get(0).getProperties().get(2).getContentValue(), equalTo("4f23f8eb-984b-4e9b-9a52-d9ebaf123456"));
    }

    private BatchUpdateRequest createBatchUpdateRequest() {
        BatchUpdateRequest updateRequest = new BatchUpdateRequest();
        updateRequest.setSource("TESTING");

        Map<String, Object> fields1 = new HashMap<>();
        fields1.put("id", "4f23f8eb-984b-4e9b-9a52-d9ebaf11bb1");
        fields1.put("field 1", "some value 11");
        fields1.put("field 2", "some value 12");
        Map<String, Object> property31 = new HashMap<>();
        property31.put("field 3 1 property", "value property 3 1");
        fields1.put("field 3 1", property31);

        BatchUpdateRequest.Entity entity1 = new BatchUpdateRequest.Entity();
        entity1.setName("testing");
        entity1.setFields(fields1);

        Map<String, Object> fields2 = new HashMap<>();
        fields2.put("id", "4f23f8eb-984b-4e9b-9a52-d9ebaf11bb2");
        fields2.put("field 1", "some value 21");
        fields2.put("field 2", "some value 22");

        Map<String, Object> property312 = new HashMap<>();
        property312.put("field 3 1 property", "value property 3 1");
        fields2.put("field 3 1", property31);

        BatchUpdateRequest.Entity entity2 = new BatchUpdateRequest.Entity();
        entity2.setName("testing");
        entity2.setFields(fields2);

        updateRequest.getEntities().add(entity1);
        updateRequest.getEntities().add(entity2);

        return updateRequest;
    }

    private Multimap<String, Object> createTestingEntity() {
        Multimap<String, Object> properties = ArrayListMultimap.create();
        properties.put("id", "4f23f8eb-984b-4e9b-9a52-d9ebaf11bb1");
        properties.put("field 1", "some value 1");
        properties.put("field 2", "some value 2");

        Multimap<String, Object> testing = ArrayListMultimap.create();
        testing.put("testing", properties);

        return testing;
    }

    private Multimap<String, Object> createTestingAlreadyLinkedEntity() {
        Multimap<String, Object> properties = ArrayListMultimap.create();
        properties.put("id", "4f23f8eb-984b-4e9b-9a52-d9ebaf123456");
        properties.put("field 1", "some value 1");
        properties.put("field 2", "some value 2");

        Multimap<String, Object> testing = ArrayListMultimap.create();
        testing.put("testing", properties);

        return testing;
    }

    private MatchEntityResponse createMatchEntityResponse() {
        MatchEntityResponse matchEntityResponse = new MatchEntityResponse();
        MatchEntityResponse.MatchResult matchResult = new MatchEntityResponse.MatchResult();
        matchResult.setStatus("SUCCESS");
        matchResult.setMatchRule("similar name found");
        matchResult.setEntity(createTestingEntity());
        matchResult.getEntity().put(FuzzyMatchDetailsConstants.FUZZY_MATCH_DETAILS, null);

        Multimap<String, Object> matchResultSuccess = ArrayListMultimap.create();
        Multimap<String, Object> testingProperties = ArrayListMultimap.create();

        testingProperties.put("id", "4f23f8eb-984b-4e9b-9a52-d9ebaf11bb1");
        testingProperties.put("field 1", "some value 1");
        testingProperties.put("field 2", "some value 2");
        Multimap<String, Object> field31 = ArrayListMultimap.create();
        field31.put("field 3 1 property", "value property 3 value 1 1");
        testingProperties.put("field 3 1", field31);


        Multimap<String, Object> fuzzyMatchDetailsProperties = ArrayListMultimap.create();
        fuzzyMatchDetailsProperties.put("field", "name");
        fuzzyMatchDetailsProperties.put("first", "field 1");
        fuzzyMatchDetailsProperties.put("second", "field 2");
        fuzzyMatchDetailsProperties.put("method", "k");
        fuzzyMatchDetailsProperties.put("matchStrength", "0.90666664");
        fuzzyMatchDetailsProperties.put("threshold", "0.85");

        matchResultSuccess.put("fuzzyMatchDetails", fuzzyMatchDetailsProperties);
        matchResultSuccess.put("testing", testingProperties);

        matchResult.setEntity(matchResultSuccess);
        matchResult.setMatch(Arrays.asList(matchResultSuccess));
        matchResult.setDuplicate(Arrays.asList(matchResultSuccess));

        MatchEntityResponse.MatchResult matchResultAlreadyLinked = new MatchEntityResponse.MatchResult();
        matchResultAlreadyLinked.setStatus("ALREADY_LINKED");
        matchResultAlreadyLinked.setMatchRule("similar name found");
        matchResultAlreadyLinked.setEntity(createTestingAlreadyLinkedEntity());

        matchEntityResponse.setMatchResults(Arrays.asList(matchResult, matchResultAlreadyLinked));

        return matchEntityResponse;
    }

    private MObject createObjectToLoad1() {
        // Construct the incoming object
        MObject object = new MObject(objectDataType.getDeveloperName());
        object.setExternalId("4f23f8eb-984b-4e9b-9a52-d9ebaf11bb1");
        object.getProperties().add(new Property("id", "4f23f8eb-984b-4e9b-9a52-d9ebaf11bb1"));
        object.getProperties().add(new Property("___sourceId", "TESTING"));
        object.getProperties().add(new Property("field 1", "some value 11"));
        object.getProperties().add(new Property("field 2", "some value 12"));
        MObject childObject = new MObject("field 3 object", Arrays.asList(new Property("field 3 1 property", "value property 3 1")));
        object.getProperties().add(new Property("field 3 1", Arrays.asList(childObject)));

        object.getProperties().add(new Property(FuzzyMatchDetailsConstants.FUZZY_MATCH_DETAILS, (MObject) null));

        return object;
    }

    private MObject createObjectToLoad2() {
        // Construct the incoming object
        MObject object = new MObject(objectDataType.getDeveloperName());
        object.setExternalId("4f23f8eb-984b-4e9b-9a52-d9ebaf11bb2");
        object.getProperties().add(new Property("id", "4f23f8eb-984b-4e9b-9a52-d9ebaf11bb2"));
        object.getProperties().add(new Property("___sourceId", "TESTING"));
        object.getProperties().add(new Property("field 1", "some value 21"));
        object.getProperties().add(new Property("field 2", "some value 22"));
        MObject childObject = new MObject("field 3 object", Arrays.asList(new Property("field 3 1 property", "value property 3 1")));
        object.getProperties().add(new Property("field 3 1", Arrays.asList(childObject)));
        object.getProperties().add(new Property(FuzzyMatchDetailsConstants.FUZZY_MATCH_DETAILS, (MObject) null));

        return object;
    }
}
