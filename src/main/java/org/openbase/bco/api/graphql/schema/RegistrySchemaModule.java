package org.openbase.bco.api.graphql.schema;

/*-
 * #%L
 * BCO GraphQL API
 * %%
 * Copyright (C) 2020 openbase.org
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import com.google.api.graphql.rejoiner.Arg;
import com.google.api.graphql.rejoiner.Mutation;
import com.google.api.graphql.rejoiner.Query;
import com.google.api.graphql.rejoiner.SchemaModule;
import com.google.common.collect.ImmutableList;
import graphql.schema.DataFetchingEnvironment;
import org.openbase.bco.api.graphql.BCOGraphQLContext;
import org.openbase.bco.registry.remote.Registries;
import org.openbase.jul.exception.CouldNotPerformException;
import org.openbase.jul.extension.type.processing.LabelProcessor;
import org.openbase.jul.pattern.Filter;
import org.openbase.jul.pattern.ListFilter;
import org.openbase.type.domotic.unit.UnitConfigType.UnitConfig;
import org.openbase.type.domotic.unit.UnitFilterType.UnitFilter;
import org.openbase.type.geometry.PoseType;
import org.openbase.type.language.LabelType;
import org.openbase.type.spatial.PlacementConfigType;
import org.openbase.type.spatial.ShapeType;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RegistrySchemaModule extends SchemaModule {

    @Query("unitConfig")
    UnitConfig getUnitConfigById(@Arg("id") String id) throws CouldNotPerformException, InterruptedException {
        return Registries.getUnitRegistry(true).getUnitConfigById(id);
    }

    @Query("unitConfigs")
    ImmutableList<UnitConfig> getUnitConfigs(@Arg("filter") UnitFilter unitFilter, @Arg("inclusiveDisabled") Boolean incluseDisabled) throws CouldNotPerformException, InterruptedException {
        return ImmutableList.copyOf(
                new UnitFilterImpl(unitFilter)
                        .filter(Registries.getUnitRegistry(true).getUnitConfigsFiltered(!incluseDisabled)));
    }

    private class UnitFilterImpl implements ListFilter<UnitConfig> {

        private final UnitFilter filter;
        private final UnitConfig properties;
        private final Filter andFilter, orFilter;

        public UnitFilterImpl(final UnitFilter filter) {
            this.filter = filter;
            this.properties = filter.getProperties();

            if (filter.hasAnd()) {
                this.andFilter = new UnitFilterImpl(filter.getAnd());
            } else {
                this.andFilter = null;
            }

            if (filter.hasOr()) {
                this.orFilter = new UnitFilterImpl(filter.getOr());
            } else {
                this.orFilter = null;
            }
        }

        @Override
        public boolean match(final UnitConfig unitConfig) {
            return (propertyMatch(unitConfig) && andFilterMatch(unitConfig)) || orFilterMatch(unitConfig);
        }

        public boolean propertyMatch(final UnitConfig unitConfig) {

            // filter by type
            if (properties.hasUnitType() && !(properties.getUnitType().equals(unitConfig.getUnitType()))) {
                return filter.getNot();
            }

            // filter by type
            if (properties.hasUnitType() && !(properties.getUnitType().equals(unitConfig.getUnitType()))) {
                return filter.getNot();
            }

            // filter by location
            if (properties.getPlacementConfig().hasLocationId() && !(properties.getPlacementConfig().getLocationId().equals(unitConfig.getPlacementConfig().getLocationId()))) {
                return filter.getNot();
            }

            // filter by location root
            if (properties.getLocationConfig().hasRoot() && !(properties.getLocationConfig().getRoot() == (unitConfig.getLocationConfig().getRoot()))) {
                return filter.getNot();
            }

            // filter by location type
            if (properties.getLocationConfig().hasLocationType() && !(properties.getLocationConfig().getLocationType() == (unitConfig.getLocationConfig().getLocationType()))) {
                return filter.getNot();
            }

            return !filter.getNot();
        }

        private boolean orFilterMatch(final UnitConfig unitConfig) {
            if (orFilter != null) {
                return orFilter.match(unitConfig);
            }
            return false;
        }

        private boolean andFilterMatch(final UnitConfig unitConfig) {
            if (andFilter != null) {
                return andFilter.match(unitConfig);
            }
            return true;
        }
    }

    @Mutation("updateUnitConfig")
    UnitConfig updateUnitConfig(@Arg("unitConfig") UnitConfig unitConfig) throws CouldNotPerformException, InterruptedException, TimeoutException, ExecutionException {
        final UnitConfig.Builder builder = Registries.getUnitRegistry(true).getUnitConfigById(unitConfig.getId()).toBuilder();
        builder.mergeFrom(unitConfig);
        return Registries.getUnitRegistry(true).getUnitConfigById(unitConfig.getId());
    }

    @Mutation("removeUnitConfig")
    UnitConfig removeUnitConfig(@Arg("unitId") String unitId) throws CouldNotPerformException, InterruptedException, TimeoutException, ExecutionException {
        final UnitConfig unitConfig = Registries.getUnitRegistry(true).getUnitConfigById(unitId);
        return Registries.getUnitRegistry(true).removeUnitConfig(unitConfig).get(5, TimeUnit.SECONDS);
    }

    @Mutation("registerUnitConfig")
    UnitConfig registerUnitConfig(@Arg("unitConfig") UnitConfig unitConfig) throws CouldNotPerformException, InterruptedException, TimeoutException, ExecutionException {
        return Registries.getUnitRegistry(true).registerUnitConfig(unitConfig).get(5, TimeUnit.SECONDS);
    }

    @Mutation("updateLabel")
    LabelType.Label updateLabel(@Arg("unitId") String unitId, @Arg("label") String label, DataFetchingEnvironment env) throws CouldNotPerformException, InterruptedException, TimeoutException, ExecutionException {
        final UnitConfig.Builder builder = Registries.getUnitRegistry(true).getUnitConfigById(unitId).toBuilder();

        final BCOGraphQLContext context = env.getContext();
        final String oldLabel = LabelProcessor.getBestMatch(context.getLanguageCode(), builder.getLabel());
        LabelProcessor.replace(builder.getLabelBuilder(), oldLabel, label);

        return Registries.getUnitRegistry().updateUnitConfig(builder.build()).get(5, TimeUnit.SECONDS).getLabel();
    }

    @Mutation("updateLocation")
    PlacementConfigType.PlacementConfig updateLocation(@Arg("unitId") String unitId, @Arg("locationId") String locationId) throws CouldNotPerformException, InterruptedException, TimeoutException, ExecutionException {
        final UnitConfig.Builder builder = Registries.getUnitRegistry(true).getUnitConfigById(unitId).toBuilder();
        builder.getPlacementConfigBuilder().setLocationId(locationId);
        return Registries.getUnitRegistry().updateUnitConfig(builder.build()).get(5, TimeUnit.SECONDS).getPlacementConfig();
    }

    @Mutation("updateFloorPlan")
    ShapeType.Shape updateFloorPlan(@Arg("locationId") String locationId, @Arg("shape") ShapeType.Shape shape) throws CouldNotPerformException, InterruptedException, ExecutionException, TimeoutException {
        final UnitConfig.Builder unitConfigBuilder = Registries.getUnitRegistry(true).getUnitConfigById(locationId).toBuilder();
        unitConfigBuilder.getPlacementConfigBuilder().getShapeBuilder().clearFloor().addAllFloor(shape.getFloorList());
        return Registries.getUnitRegistry().updateUnitConfig(unitConfigBuilder.build()).get(5, TimeUnit.SECONDS).getPlacementConfig().getShape();
    }

    @Mutation("updatePose")
    PoseType.Pose updatePose(@Arg("unitId") String unitId, @Arg("pose") PoseType.Pose pose) throws CouldNotPerformException, InterruptedException, TimeoutException, ExecutionException {
        final UnitConfig.Builder builder = Registries.getUnitRegistry(true).getUnitConfigById(unitId).toBuilder();
        builder.getPlacementConfigBuilder().clearPose().setPose(pose);
        return Registries.getUnitRegistry().updateUnitConfig(builder.build()).get(5, TimeUnit.SECONDS).getPlacementConfig().getPose();
    }

//    @Query("unitConfig") todo QueryType required in order to support multible arguments
//    UnitConfig getUnitConfigByAlias(@Arg("alias") String alias) throws CouldNotPerformException, InterruptedException {
//        return Registries.getUnitRegistry(true).getUnitConfigByAlias(alias);
//    }
}
