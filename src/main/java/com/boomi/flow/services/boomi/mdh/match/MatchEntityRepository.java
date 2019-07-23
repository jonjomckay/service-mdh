package com.boomi.flow.services.boomi.mdh.match;

import com.boomi.flow.services.boomi.mdh.ApplicationConfiguration;
import com.boomi.flow.services.boomi.mdh.client.MdhClient;
import com.boomi.flow.services.boomi.mdh.records.GoldenRecordConstants;
import com.boomi.flow.services.boomi.mdh.common.BatchUpdateRequest;
import com.google.common.base.Strings;
import com.manywho.sdk.api.run.ServiceProblemException;
import com.manywho.sdk.api.run.elements.type.MObject;
import com.manywho.sdk.api.run.elements.type.Property;

import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;

public class MatchEntityRepository {

    private final MdhClient client;

    @Inject
    public MatchEntityRepository(MdhClient mdhClient) {
        this.client = mdhClient;
    }

    public List<MObject> matchEntity(ApplicationConfiguration configuration, String universeId, List<MObject> objects)  {
        var universe = client.findUniverse(configuration.getAtomHostname(), configuration.getAtomUsername(), configuration.getAtomPassword(), universeId);
        List<MatchEntityResponse.MatchResult> results = new ArrayList<>();

        // TODO: This isn't correct - it would be great to be able to get the actual ID field name (or make a global standard named one)
        String idField = universe.getLayout().getIdXPath()
                .split("/")
                [2];

        for (var object : objects) {
            if (Strings.isNullOrEmpty(object.getExternalId())) {
                // We are requesting an object without id
                var id = UUID.randomUUID().toString();

                // Set the ID property, so it can be referenced in a Flow
                for (var property : object.getProperties()) {
                    if (property.getDeveloperName().equals(idField)) {
                        property.setContentValue(id);
                    }
                }

                // Set the object's external ID too, which is only used inside Flow itself
                object.setExternalId(id);
            }
        }

        var objectsBySource = objects.stream()
                .collect(Collectors.groupingBy(object -> object.getProperties()
                        .stream()
                        .filter(property -> property.getDeveloperName().equals(GoldenRecordConstants.SOURCE_ID_FIELD))
                        .map(Property::getContentValue)
//                        .filter(source -> source != null && source.isEmpty() == false)
                        .findFirst()
                        .orElseThrow(() -> new ServiceProblemException(400, "No Source ID was given for the record to update"))));

        for (var sourceGroup : objectsBySource.entrySet()) {
            // TODO: Check if we should be setting this to a default value, or error if no source was set
            var sourceId = sourceGroup.getKey().isBlank()
                    ? GoldenRecordConstants.DEFAULT_SOURCE_ID
                    : sourceGroup.getKey();

            var entities = sourceGroup.getValue().stream()
                    .map(entity -> {
                        // Map all the properties to fields, except our "internal" ones
                        var fields = entity.getProperties().stream()
                                .filter(property -> property.getDeveloperName().startsWith("___") == false)
                                .filter(property -> property.getContentValue() != null)
                                .collect(Collectors.toMap(
                                        Property::getDeveloperName,
                                        property -> (Object) property.getContentValue()
                                ));

                        fields.put(idField, entity.getExternalId());

                        return new BatchUpdateRequest.Entity()
                                .setOp(null)
                                .setName(universe.getLayout().getModel().getName())
                                .setFields(fields);
                    })
                    .collect(Collectors.toList());

            // Now we ned to load the match entity information
            var updateRequest = new BatchUpdateRequest()
                    .setSource(sourceId)
                    .setEntities(entities);


            MatchEntityResponse matchResponse = client.queryMatchEntity(configuration.getAtomHostname(),
                    configuration.getAtomUsername(), configuration.getAtomPassword(), universe.getId().toString(),
                    updateRequest);

            List<MatchEntityResponse.MatchResult> matchesResult = matchResponse.getMatchResults();


            results.addAll(matchesResult);
        }

        objects.forEach(object -> addMatchResult(object, universe.getName(), idField, results));

        return objects;
    }

    private static void addMatchResult(MObject object, String universe, String idField, List<MatchEntityResponse.MatchResult> matchResults) {

        Property propertyMatches = object.getProperties().stream()
                .filter(property -> property.getDeveloperName().equals(FuzzyMatchDetialsConstants.MATCH_FIELD))
                .findFirst()
                .orElseGet(() -> {
                    Property p = new Property(FuzzyMatchDetialsConstants.MATCH_FIELD, new ArrayList<>());
                    object.getProperties().add(p);

                    return p;
                });

        Property propertyDuplicates = object.getProperties().stream()
                .filter(property -> property.getDeveloperName().equals(FuzzyMatchDetialsConstants.DUPLICATE_FIELD))
                .findFirst()
                .orElseGet(() -> {
                    Property p = new Property(FuzzyMatchDetialsConstants.DUPLICATE_FIELD, new ArrayList<>());
                    object.getProperties().add(p);

                    return p;
                });


        Property propertyAlreadyLinked = object.getProperties().stream()
                .filter(property -> property.getDeveloperName().equals(FuzzyMatchDetialsConstants.ALREADY_LINKED_FIELD))
                .findFirst()
                .orElseGet(() -> {
                    Property p = new Property(FuzzyMatchDetialsConstants.ALREADY_LINKED_FIELD, new ArrayList<>());
                    object.getProperties().add(p);

                    return p;
                });

        matchResults.stream()
                .filter(matchResult -> object.getExternalId().equals(((HashMap)(matchResult.getEntity().get("entity").get(universe))).get(idField)))
                .forEach(matchResult -> {
                    addMatchesToProperty(propertyMatches, universe, matchResult.getMatch(), true);
                    addMatchesToProperty(propertyDuplicates, universe, matchResult.getDuplicate(), true);
                    addMatchesToProperty(propertyAlreadyLinked, universe, matchResult.getDuplicate(), false);
                });
    }

    private static void addMatchesToProperty(Property propertyMatches, String universe, List<Map<String, Object>> matchResults, boolean addFuzzyMatchDetails){
        matchResults.forEach(matchResult -> {
            var object = new MObject(universe);
            ((Map<String, Object>) matchResult.get(universe)).entrySet().stream()
                    .forEach(stringObjectEntry -> {
                        var property = new Property(stringObjectEntry.getKey(), stringObjectEntry.getValue());
                        object.getProperties().add(property);
                    });

            if (addFuzzyMatchDetails) {
                addFuzzyMatchDetails(object, (HashMap<String, Object>) matchResult.get(FuzzyMatchDetialsConstants.FUZZY_MATCH_DETAILS));
            }
            propertyMatches.getObjectData().add(object);
        });

    }

    private static void addFuzzyMatchDetails(MObject object, Map<String, Object> result) {
        var fuzzyMatchEmpty = new MObject(FuzzyMatchDetialsConstants.FUZZY_MATCH_DETAILS);

        var properties = new ArrayList<Property>();
        properties.add(new Property("field", result.get("field")));
        properties.add(new Property("first", result.get("first")));
        properties.add(new Property("second", result.get("second")));
        properties.add(new Property("method", result.get("method")));
        properties.add(new Property("matchStrength", result.get("matchStrength")));
        properties.add(new Property("threshold", result.get("threshold")));
        fuzzyMatchEmpty.setProperties(properties);

        object.getProperties().add(new Property(FuzzyMatchDetialsConstants.FUZZY_MATCH_DETAILS, fuzzyMatchEmpty));
    }
}
