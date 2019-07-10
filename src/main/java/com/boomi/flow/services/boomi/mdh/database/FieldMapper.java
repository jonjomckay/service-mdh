package com.boomi.flow.services.boomi.mdh.database;

import com.boomi.flow.services.boomi.mdh.universes.Universe;
import com.manywho.sdk.api.ContentType;
import com.manywho.sdk.api.draw.elements.type.TypeElement;
import com.manywho.sdk.api.draw.elements.type.TypeElementBinding;
import com.manywho.sdk.api.draw.elements.type.TypeElementProperty;
import com.manywho.sdk.api.draw.elements.type.TypeElementPropertyBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

class FieldMapper {
    private final static Logger LOGGER = LoggerFactory.getLogger(FieldMapper.class);

    static void collectTypes(List<Universe.Layout.Model.Element> elements, String name, String typePrettyName, String universeName,
                             String universeId, List<TypeElement> typeCollected, boolean isModel) {

        List<TypeElementProperty> properties = new ArrayList<>();
        List<TypeElementPropertyBinding> propertyBindings = new ArrayList<>();

        // TODO: This doesn't add an ID field... is that a problem? It's only in layout->fields, not layout->model->elements
        for (var element : elements) {

            var contentType = fieldTypeToContentType(element.getType(), element.isRepeatable());
            if (contentType == null) {
                continue;
            }
            var prettyName = element.getPrettyName();

            if (ContentType.Object.equals(contentType) || ContentType.List.equals(contentType)) {
                prettyName = TypeNameGenerator.createChildTypeName(element.getPrettyName(), typePrettyName);
                // collect type, and continue searching for more subtypes
                collectTypes(element.getElements(), element.getName(), prettyName, universeName, universeId, typeCollected,false);
            }

            propertyBindings.add(new TypeElementPropertyBinding(prettyName, element.getName()));
            properties.add(new TypeElementProperty(prettyName, contentType, prettyName));
        }

        List<TypeElementBinding> bindings = new ArrayList<>();
        var developerSummary = "The model for the " + universeName + " universe";

        if (isModel == false) {
            developerSummary = typePrettyName;
            bindings.add(new TypeElementBinding(typePrettyName, developerSummary, universeId, propertyBindings));
        } else {
            bindings.add(new TypeElementBinding(typePrettyName, developerSummary, universeId, propertyBindings));
        }


        var typeElement = new TypeElement(typePrettyName, properties, bindings);

        typeCollected.add(typeElement);
    }

    private static ContentType fieldTypeToContentType(String type, boolean repeatable) {
        if (repeatable && "CONTAINER".equals(type)) {
            return ContentType.List;
        }

        switch (type) {
            case "STRING":
                return ContentType.String;
            case "INTEGER":
                return ContentType.Number;
            case "FLOAT":
                return ContentType.Number;
            case "DATETIME":
                return ContentType.DateTime;
            case "BOOLEAN":
                return ContentType.Boolean;
            case "CONTAINER":
                return ContentType.Object;
            case "ENUMERATION":
                return ContentType.String;
            case "REFERENCE":
                return ContentType.String;
            case "CLOB":
                return ContentType.String;
            default:
                LOGGER.warn("Encountered an unsupported element type of {}", type);

                return null;
        }
    }
}
