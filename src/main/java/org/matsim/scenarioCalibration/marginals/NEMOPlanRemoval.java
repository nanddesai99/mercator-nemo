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

import java.util.Objects;
import javax.inject.Inject;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.replanning.selectors.PlanSelector;
import org.matsim.core.replanning.selectors.WorstPlanForRemovalSelector;

/**
 * The idea is to keep the initial plans which are marked with 'true' corresponding to 'intial_plan_attribute_name' attribute.
 *
 * Created by amit on 11.06.18.
 */

public class NEMOPlanRemoval implements PlanSelector<Plan, Person> {

    public static final String nemo_plan_remover = "NEMO_PLANS_REMOVAL";
    public static final String plan_attribute_name = "initial_plan_attribute_name";
    public static final String plan_attribute_prefix = "initial_plan_attribute_";

    private final WorstPlanForRemovalSelector delegate;

    @Inject
    public NEMOPlanRemoval (StrategyConfigGroup strategyConfigGroup){
        this.delegate = new WorstPlanForRemovalSelector();
        if ( strategyConfigGroup.getMaxAgentPlanMemorySize() < 12) {
            Logger.getLogger(NEMOPlanRemoval.class).warn("A plans remover is used which keeps the initial plans or at least their copy \n " +
                    "and maximum number of plans in the choice set is limited to "+ strategyConfigGroup.getMaxAgentPlanMemorySize()+
            ".\n Lower number of plans in choice set is likely to end up in infinite loop. Setting it to 15.");
            strategyConfigGroup.setMaxAgentPlanMemorySize(15);
        }
    }

    @Override
    public Plan selectPlan(HasPlansAndId<Plan, Person> member) {
        Plan plan = delegate.selectPlan(member);
        Object value = plan.getAttributes().getAttribute(plan_attribute_name);
        // check if there exists a plan with same index

        int occurrences = (int) member.getPlans()
                                      .stream()
                                      .filter(pl -> Objects.equals(pl.getAttributes().getAttribute(plan_attribute_name),
                                              value))
                                      .count();
        if (occurrences>=2) return plan;
        else return null;
    }
}