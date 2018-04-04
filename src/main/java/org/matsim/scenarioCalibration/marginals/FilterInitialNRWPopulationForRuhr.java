/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2018 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.scenarioCalibration.marginals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;
import org.matsim.NEMOUtils;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import playground.agarwalamit.utils.LoadMyScenarios;
import playground.vsp.demandde.cemdap.output.CemdapStops2MatsimPlansConverter;

/**
 * Take selected plans and persons who originate, terminate any trip to Ruhr boundary or pass through it.
 * This will also remove routes (see switch) so that a detailed network can be used.
 * <p>
 * Created by amit on 12.02.18.
 */

public class FilterInitialNRWPopulationForRuhr {

    private static final String plansFile = "../shared-svn/projects/nemo_mercator/data/matsim_input/2018-03-01_RuhrCalibration_withMarginals/plans_1pct_fullChoiceSet_coordsAssigned_splitActivities.xml.gz";

    private static final String outPlansFile = "../shared-svn/projects/nemo_mercator/data/matsim_input/2018-03-01_RuhrCalibration_withMarginals/plans_1pct_fullChoiceSet_coordsAssigned_splitActivities_filteredForRuhr.xml.gz";

    //some assumptions
    private static final boolean keepOnlySelectedPlans = false;

    private final Population outPopulation;

    private final Collection<String> zoneIds = new ArrayList<>();

    FilterInitialNRWPopulationForRuhr() {
        this.zoneIds.addAll(ShapeFileReader.getAllFeatures(NEMOUtils.Ruhr_MUNICIPALITY_SHAPE_FILE)
                                           .stream()
                                           .map(f -> (String) f.getAttribute(NEMOUtils.MUNICIPALITY_SHAPE_FEATURE_KEY))
                                           .collect(Collectors.toList()));

        this.zoneIds.addAll(ShapeFileReader.getAllFeatures(NEMOUtils.Ruhr_PLZ_SHAPE_FILE)
                                           .stream()
                                           .map(f -> (String) f.getAttribute(NEMOUtils.Ruhr_PLZ_SHAPE_FEATURE_KEY))
                                           .collect(Collectors.toList()));
        this.outPopulation = ScenarioUtils.loadScenario(ConfigUtils.createConfig()).getPopulation();
    }

    public static void main(String[] args) {
        Population inputPopulation = LoadMyScenarios.loadScenarioFromPlans(plansFile).getPopulation();
        new FilterInitialNRWPopulationForRuhr().processAndWritePlans(inputPopulation);
    }

    void processAndWritePlans(Population inputPopulation) {
        inputPopulation.getPersons().values().stream().filter(this::keepPerson).forEach(this::cloneAndAddPerson);

        new PopulationWriter(this.outPopulation).write(outPlansFile);
    }

    private boolean keepPerson(Person person) {
        if (keepOnlySelectedPlans){
            return person.getSelectedPlan()
                         .getPlanElements()
                         .stream().filter(Activity.class::isInstance)
                         .map(pe -> ((Activity) pe).getAttributes().getAttribute(CemdapStops2MatsimPlansConverter.activityZoneId_attributeKey))
                         .anyMatch(this.zoneIds::contains);
        } else {
            return person.getPlans()
                         .stream()
                         .flatMap(plan -> plan.getPlanElements().stream())
                         .filter(Activity.class::isInstance)
                         .map(pe -> ((Activity) pe).getAttributes().getAttribute(CemdapStops2MatsimPlansConverter.activityZoneId_attributeKey))
                         .anyMatch(this.zoneIds::contains);
        }
    }

    private void cloneAndAddPerson(Person person) { // only selected plan
        Person outPerson = this.outPopulation.getFactory().createPerson(person.getId());
        if (keepOnlySelectedPlans) {
            outPerson.addPlan(person.getSelectedPlan());
        } else {
            outPerson = person;
        }
        this.outPopulation.addPerson(outPerson);
    }
}