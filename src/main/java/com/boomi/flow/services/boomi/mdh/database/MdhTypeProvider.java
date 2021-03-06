package com.boomi.flow.services.boomi.mdh.database;

import com.boomi.flow.services.boomi.mdh.ApplicationConfiguration;
import com.boomi.flow.services.boomi.mdh.universes.Universe;
import com.boomi.flow.services.boomi.mdh.universes.UniverseRepository;
import com.google.common.base.Strings;
import com.manywho.sdk.api.describe.DescribeServiceRequest;
import com.manywho.sdk.api.draw.elements.type.TypeElement;
import com.manywho.sdk.api.run.ServiceProblemException;
import com.manywho.sdk.services.types.TypeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class MdhTypeProvider implements TypeProvider<ApplicationConfiguration> {
    private final static Logger LOGGER = LoggerFactory.getLogger(MdhTypeProvider.class);

    private final UniverseRepository repository;

    @Inject
    public MdhTypeProvider(UniverseRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean doesTypeExist(ApplicationConfiguration configuration, String name) {
        if (name == null) {
            return false;
        }

        if (name.endsWith("-quarantine")) {
            return true;
        }

        if (name.endsWith("-golden-record")) {
            return true;
        }

        if (name.endsWith("-match")) {
            return true;
        }

        return false;
    }

    private static List<TypeElement> loadModelTypes(List<Universe> universes) {
        return universes.stream()
                .filter(universe -> universe.getLayout() != null && universe.getLayout().getModel() != null)
                .map(DescribeType::createModelTypes)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    @Override
    public List<TypeElement> describeTypes(ApplicationConfiguration configuration, DescribeServiceRequest request) {
        // TODO: Get this bug fixed in the system flows
        if (request.hasConfigurationValues() == false) {
            return new ArrayList<>();
        }

        if (Strings.isNullOrEmpty(configuration.getHubToken())) {
            throw new ServiceProblemException(400, "Hub Token can not be empty");
        }

        if (Strings.isNullOrEmpty(configuration.getHubUsername())) {
            throw new ServiceProblemException(400, "Hub Username can not be empty");
        }

        if (Strings.isNullOrEmpty(configuration.getHubHostname())) {
            throw new ServiceProblemException(400, "Hub Hostname can not be empty");
        }

        List<Universe> universes = repository.findAll(configuration.getHubHostname(), configuration.getHubUsername(), configuration.getHubToken());

        return loadModelTypes(universes);
    }
}
