package com.boomi.flow.services.boomi.mdh.common;

import com.boomi.flow.services.boomi.mdh.match.FuzzyMatchDetailsConstants;
import com.boomi.flow.services.boomi.mdh.match.MatchEntityResponse;
import com.boomi.flow.services.boomi.mdh.quarantine.QuarantineEntry;
import com.boomi.flow.services.boomi.mdh.quarantine.QuarantineEntryConstants;
import com.boomi.flow.services.boomi.mdh.records.GoldenRecord;
import com.boomi.flow.services.boomi.mdh.records.GoldenRecordConstants;
import com.boomi.flow.services.boomi.mdh.universes.Universe;
import com.google.common.base.Strings;
import com.manywho.sdk.api.ContentType;
import com.manywho.sdk.api.run.elements.type.MObject;
import com.manywho.sdk.api.run.elements.type.Property;

import java.util.*;
import java.util.stream.Collectors;

public class Entities {

    public static MObject createGoldenRecordMObject(String universeId, GoldenRecord record) {
        MObject mObject = record.getMObject();

        if (mObject == null) {
            return null;
        }

        List<Property> properties = mObject.getProperties();

        // this part is only for Golden Records
        List<MObject> mObjectLinks = record.getLinks().stream()
                .map(Entities::createMObjectForLink)
                .collect(Collectors.toList());

        properties.add(new Property(GoldenRecordConstants.LINKS_FIELD, mObjectLinks));
        properties.add(new Property(GoldenRecordConstants.RECORD_ID_FIELD, record.getRecordId()));
        properties.add(new Property(GoldenRecordConstants.CREATED_DATE_FIELD, EngineCompatibleDates.format(record.getCreatedDate())));
        properties.add(new Property(GoldenRecordConstants.UPDATED_DATE_FIELD, EngineCompatibleDates.format(record.getUpdatedDate())));

        String developerName = universeId + "-golden-record";
        MObject mObjectToReturn = new MObject(developerName, record.getRecordId(), properties);
        mObjectToReturn.setTypeElementBindingDeveloperName(developerName);

        return mObjectToReturn;
    }

    public static MObject createQuarantineMObject(String universeId, QuarantineEntry entry) {
        List<Property> properties = new ArrayList<>();
        if (entry.getEntity() != null) {
            for (Property property :entry.getEntity().getProperties()) {
                if (isValidProperty(property)) {
                    properties.add(property);
                }
            }
        }

        properties.add(new Property(QuarantineEntryConstants.CAUSE_FIELD, entry.getCause()));
        properties.add(new Property(QuarantineEntryConstants.CREATED_DATE_FIELD, EngineCompatibleDates.format(entry.getCreatedDate())));
        properties.add(new Property(QuarantineEntryConstants.END_DATE_FIELD, EngineCompatibleDates.format(entry.getEndDate())));
        properties.add(new Property(QuarantineEntryConstants.REASON_FIELD, entry.getReason()));
        properties.add(new Property(QuarantineEntryConstants.RESOLUTION_FIELD, entry.getResolution()));
        properties.add(new Property(QuarantineEntryConstants.TRANSACTION_ID_FIELD, entry.getTransactionId()));
        properties.add(new Property(QuarantineEntryConstants.SOURCE_ENTITY_ID_FIELD, entry.getSourceEntityId()));
        properties.add(new Property(QuarantineEntryConstants.SOURCE_ID_FIELD, entry.getSourceId()));

        return new MObject(universeId + "-quarantine", entry.getTransactionId(), properties);
    }

    // we are dealing with data that has problems, if the mobject doesn't have properties the engine will fail
    private static boolean isValidProperty(Property property) {
        if (property.getObjectData() == null || property.getObjectData().size() < 1) {
            return true;
        }

        for (MObject object :property.getObjectData()) {
            if (object.getProperties() == null || object.getProperties().size() < 1) {
                return false;
            }
        }

        return true;
    }

    public static MObject setRandomUniqueIdIfEmpty(MObject object, String idField, boolean isModel) {
        // We are requesting an object without id
        String id = UUID.randomUUID().toString();

        // Set the ID property, so it can be referenced in a Flow
        for (Property property : object.getProperties()) {
            if (property.getDeveloperName().equals(idField) && Strings.isNullOrEmpty(object.getExternalId()) && isModel) {
                property.setContentValue(id);
            }

            if (property.getObjectData() != null) {
                for(MObject propertyObject : property.getObjectData()) {
                    /// Set the object's external ID in the child objects (field groups), which is only used inside Flow itself
                    setRandomUniqueIdIfEmpty(propertyObject, null, false);
                }
            }
        }

        if (Strings.isNullOrEmpty(object.getExternalId())) {
            // Set the object's external ID too, which is only used inside Flow itself
            object.setExternalId(id);
        }

        return object;
    }

    public static MObject createMatchMObject(String universeId, Universe universe, MatchEntityResponse.MatchResult result) {

        Property propertiesMatched = new Property(FuzzyMatchDetailsConstants.MATCH, new ArrayList<>());
        Property propertiesDuplicated = new Property(FuzzyMatchDetailsConstants.DUPLICATE, new ArrayList<>());
        Property propertiesAlreadyLinked = new Property(FuzzyMatchDetailsConstants.ALREADY_LINKED, new ArrayList<>());

        if ("SUCCESS".equals(result.getStatus())) {
            propertiesMatched = new Property(FuzzyMatchDetailsConstants.MATCH, prepareEachObjectForFlow(result.getMatch(), universe));
            propertiesDuplicated = new Property(FuzzyMatchDetailsConstants.DUPLICATE, prepareEachObjectForFlow(result.getDuplicate(), universe));
        } else if ("ALREADY_LINKED".equals(result.getStatus())) {
            MObject alreadyLinkedObject = createAlreadyLinkedObject(universe, result.getEntity(), result.getIdResource());

            prepreObjectForFlow(alreadyLinkedObject, universe);

            propertiesAlreadyLinked = new Property(FuzzyMatchDetailsConstants.ALREADY_LINKED, alreadyLinkedObject);
        }

        List<Property> properties = result.getEntity().getProperties();

        properties.add(new Property(GoldenRecordConstants.SOURCE_ID_FIELD, result.getIdResource()));
        properties.add(new Property(FuzzyMatchDetailsConstants.FUZZY_MATCH_DETAILS, (MObject) null));

        properties.add(propertiesMatched);
        properties.add(propertiesDuplicated);
        properties.add(propertiesAlreadyLinked);

        String externalId = result.getEntity().getProperties().stream()
                .filter(p -> p.getDeveloperName().equals(universe.getIdField()))
                .map(Property::getContentValue)
                .findFirst()
                .orElse(UUID.randomUUID().toString());

        MObject object = new MObject(universeId + "-match", externalId, properties);
        object.setTypeElementBindingDeveloperName(object.getDeveloperName());

        return object;
    }

    private static MObject createAlreadyLinkedObject(Universe universe, MObject object, String idResource) {
        List<Property> copyOfProperties = object.getProperties()
                .stream()
                .map(Entities::copyProperty)
                .collect(Collectors.toList());

        copyOfProperties.add(new Property(GoldenRecordConstants.SOURCE_ID_FIELD, idResource));
        copyOfProperties.add(new Property(FuzzyMatchDetailsConstants.FUZZY_MATCH_DETAILS, (MObject) null));

        copyOfProperties.add(new Property(FuzzyMatchDetailsConstants.MATCH, new ArrayList<>()));
        copyOfProperties.add(new Property(FuzzyMatchDetailsConstants.DUPLICATE, new ArrayList<>()));
        copyOfProperties.add(new Property(FuzzyMatchDetailsConstants.ALREADY_LINKED, new ArrayList<>()));

        return new MObject(universe.getId() + "-match", object.getExternalId(), copyOfProperties);
    }

    private static Property copyProperty(Property propertyToCopy) {
        if (propertyToCopy.getContentType() == ContentType.Object || propertyToCopy.getContentType() == ContentType.List) {
            return new Property(propertyToCopy.getDeveloperName(), propertyToCopy.getObjectData());
        } else {
            return new Property(propertyToCopy.getDeveloperName(), propertyToCopy.getContentValue());
        }
    }

    private static List<MObject> prepareEachObjectForFlow(List<MObject> objects, Universe universe) {
        return objects
                .stream()
                .peek(object -> prepreObjectForFlow(object, universe))
                .collect(Collectors.toList());
    }

    private static void prepreObjectForFlow(MObject object, Universe universe) {
        if (Strings.isNullOrEmpty(object.getExternalId())) {
            String externalId = object.getProperties()
                    .stream()
                    .filter(p -> universe.getIdField().equals(p.getDeveloperName()))
                    .map(Property::getContentValue)
                    .findFirst()
                    .orElse(UUID.randomUUID().toString());

            object.setExternalId(externalId);
            object.setTypeElementBindingDeveloperName(universe.getId()+"-match");
        }
    }

    private static MObject createMObjectForLink(GoldenRecord.Link link) {
        List<Property> linkProperties = new ArrayList<>();

        linkProperties.add(new Property("Source", link.getSource()));
        linkProperties.add(new Property("Entity ID", link.getEntityId()));
        linkProperties.add(new Property("Established Date", EngineCompatibleDates.format(link.getEstablishedDate())));

        MObject linkObject = new MObject(GoldenRecordConstants.LINK, link.getEntityId(), linkProperties);
        linkObject.setTypeElementBindingDeveloperName(linkObject.getDeveloperName());

        return linkObject;
    }

    public static String extractFieldIdValueOrRandomGenerate(MObject object) {
        return object.getProperties()
                .stream()
                .filter(p -> GoldenRecordConstants.ENTITY_ID_FIELD.equals(p.getDeveloperName()) && Strings.isNullOrEmpty(p.getContentValue()) == false)
                .findFirst()
                .map(Property::getContentValue)
                .orElse(UUID.randomUUID().toString());
    }

    public static String addingModelPrefix(String modelName, String fieldGroupName) {
        return modelName + " - " + fieldGroupName;
    }

    public static String removeModelPrefix(String fullChildTypeName, String modelName) {
        int removeUntilIndex = (modelName + " - ").length();

        return fullChildTypeName.substring(removeUntilIndex);
    }
}
