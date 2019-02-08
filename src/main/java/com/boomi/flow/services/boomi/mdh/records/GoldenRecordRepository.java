package com.boomi.flow.services.boomi.mdh.records;

import com.boomi.flow.services.boomi.mdh.ApplicationConfiguration;
import com.boomi.flow.services.boomi.mdh.client.MdhClient;
import com.boomi.flow.services.boomi.mdh.common.ListFilters;
import com.manywho.sdk.api.run.elements.type.ListFilter;
import com.manywho.sdk.api.run.elements.type.ListFilterWhere;
import com.manywho.sdk.api.run.elements.type.MObject;
import com.manywho.sdk.api.run.elements.type.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GoldenRecordRepository {
    private final static Logger LOGGER = LoggerFactory.getLogger(GoldenRecordRepository.class);

    private final MdhClient client;

    @Inject
    public GoldenRecordRepository(MdhClient client) {
        this.client = client;
    }

    public List<MObject> findAll(ApplicationConfiguration configuration, String universe, ListFilter filter) {
        LOGGER.info("Loading golden records for the universe {} from the Atom at {} with the username {}", universe, configuration.getAtomHostname(), configuration.getAtomUsername());

        var request = new GoldenRecordQueryRequest();

        // TODO: Cleanup everything in this filter block cause it's super ugly
        if (filter != null) {

            if (filter.hasOrderBy()) {
                var sort = new GoldenRecordQueryRequest.Sort();

                for (var orderBy : filter.getOrderBy()) {
                    sort.getFields().add(new GoldenRecordQueryRequest.Sort.Field()
                            .setFieldId(orderBy.getColumnName())
                            .setDirection(orderBy.getDirection())
                    );
                }

                request.setSort(sort);
            }

            if (filter.hasWhere()) {
                var queryFilter = new GoldenRecordQueryRequest.Filter();

                // Created date
                var createdDates = ListFilters.findEnumerableFilters(filter.getWhere(), GoldenRecordConstants.CREATED_DATE_FIELD);
                if (createdDates.isEmpty() == false) {
                    var dateFilter = new GoldenRecordQueryRequest.Filter.DateFilter();

                    createdDates.forEach(createDateFilter(dateFilter));

                    queryFilter.setCreatedDate(dateFilter);
                }

                // Updated date
                var updatedDates = ListFilters.findEnumerableFilters(filter.getWhere(), GoldenRecordConstants.UPDATED_DATE_FIELD);
                if (updatedDates.isEmpty() == false) {
                    var dateFilter = new GoldenRecordQueryRequest.Filter.DateFilter();

                    updatedDates.forEach(createDateFilter(dateFilter));

                    queryFilter.setUpdatedDate(dateFilter);
                }

                // Field values
                var entityFields = filter.getWhere().stream()
                        .sorted(Comparator.comparing(ListFilterWhere::getColumnName))
                        .dropWhile(where -> Arrays.asList(GoldenRecordConstants.CREATED_DATE_FIELD, GoldenRecordConstants.UPDATED_DATE_FIELD).contains(where.getColumnName()))
                        .collect(Collectors.toList());

                if (entityFields.isEmpty() == false) {
                    var fieldFilters = queryFilter.getFieldValues();

                    for (var field : entityFields) {

                        String operator;

                        switch (field.getCriteriaType()) {
                            case Contains:
                                operator = "CONTAINS";

                                break;
                            case EndsWith:
                                operator = "ENDS_WITH";

                                break;
                            case Equal:
                                operator = "EQUALS";

                                break;
                            case GreaterThan:
                                operator = "GREATER_THAN";

                                break;
                            case GreaterThanOrEqual:
                                operator = "GREATER_THAN_EQUAL";

                                break;
                            case IsEmpty:
                                // TODO: Check if this is correct
                                operator = "IS_NULL";

                                break;
                            case LessThan:
                                operator = "LESS_THAN";

                                break;
                            case LessThanOrEqual:
                                operator = "LESS_THAN_EQUAL";

                                break;
                            case NotEqual:
                                operator = "NOT_EQUAL_TO";

                                break;
                            case StartsWith:
                                operator = "STARTS_WITH";

                                break;
                            default:
                                throw new RuntimeException("An unsupported criteria type of " + field.getCriteriaType() + " was given for the column " + field.getColumnName());
                        }

                        fieldFilters.add(new GoldenRecordQueryRequest.Filter.FieldValue()
                                .setFieldId(field.getColumnName())
                                .setOperator(operator)
                                .setValue(field.getContentValue())
                        );
                    }
                }

                request.setFilter(queryFilter);
            }
        }

        var result = client.queryGoldenRecords(configuration.getAtomHostname(), configuration.getAtomUsername(), configuration.getAtomPassword(), universe, request);
        if (result == null || result.getRecords() == null || result.getResultCount() == 0) {
            return new ArrayList<>();
        }

        return result.getRecords().stream()
                .map(entry -> createGoldenRecordObject(universe, entry))
                .collect(Collectors.toList());
    }

    private static MObject createGoldenRecordObject(String universe, GoldenRecord record) {
        return createEntityMObject(universe, record.getRecordId(), record.getFields());
    }

    private static Consumer<ListFilterWhere> createDateFilter(GoldenRecordQueryRequest.Filter.DateFilter dateFilter) {
        return where -> {
            var date = OffsetDateTime.parse(where.getContentValue());

            switch (where.getCriteriaType()) {
                case Equal:
                    dateFilter
                            .setFrom(date)
                            .setTo(date);

                    break;

                case GreaterThan:
                case GreaterThanOrEqual:
                    dateFilter.setFrom(date);
                    break;

                case LessThan:
                case LessThanOrEqual:
                    dateFilter.setTo(date);
                    break;

                default:
                    throw new RuntimeException("The criteria type " + where.getCriteriaType() + " is not supported for date fields");
            }
        };
    }

    // TODO: Dedupe this and below
    private static MObject createEntityMObject(String universe, String id, Map<String, Map<String, Object>> entity) {
        if (entity.isEmpty()) {
            return null;
        }

        var entry = entity.entrySet().iterator().next();

        var properties = createPropertiesFromMap(entry.getValue());

        return new MObject(String.format("%s Golden Record", entry.getKey()), id, properties);
    }

    private static List<Property> createPropertiesFromMap(Map<String, Object> map) {
        // We don't really support any nested objects or lists yet, so we filter them out and map to a type property
        return map.entrySet().stream()
                .filter(field -> (field.getValue() instanceof Map) == false)
                .map(field -> new Property(field.getKey(), field.getValue()))
                .collect(Collectors.toList());
    }

    public List<MObject> create(ApplicationConfiguration configuration, String universe, List<MObject> object) {
        return null;
    }
}
