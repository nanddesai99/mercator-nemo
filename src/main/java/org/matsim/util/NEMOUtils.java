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

package org.matsim.util;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.scenario.ScenarioUtils;
import playground.vsp.analysis.modules.modalAnalyses.modalShare.ModalShareControlerListener;
import playground.vsp.analysis.modules.modalAnalyses.modalShare.ModalShareEventHandler;
import playground.vsp.analysis.modules.modalAnalyses.modalTripTime.ModalTravelTimeControlerListener;
import playground.vsp.analysis.modules.modalAnalyses.modalTripTime.ModalTripTravelTimeHandler;
import playground.vsp.cadyts.marginals.prep.DistanceBin;
import playground.vsp.cadyts.marginals.prep.DistanceDistribution;

import java.util.Map;

/**
 * Created by amit on 29.01.18.
 */

public final class NEMOUtils {

    public static final String TRANSIT_NETWORK_PREFIX = "pt_";

    public static final String NEMO_EPSG = "EPSG:25832";

    //shape files
    public static final String NRW_MUNICIPALITY_SHAPE_FILE = "../../repos/shared-svn/projects/nemo_mercator/data/original_files/shapeFiles/sourceShape_NRW/dvg2gem_nw.shp";
    public static final String Ruhr_MUNICIPALITY_SHAPE_FILE ="../../repos/shared-svn/projects/nemo_mercator/data/original_files/shapeFiles/shapeFile_Ruhrgebiet/dvg2gem_ruhrgebiet.shp";
    public static final String Ruhr_PLZ_SHAPE_FILE = "../../repos/shared-svn/projects/nemo_mercator/data/original_files/shapeFiles/plzBasedPopulation/plz-gebiete_Ruhrgebiet/plz-gebiete_Ruhrgebiet_withPopulation.shp";
    public static final String Ruhr_BOUNDARY_SHAPE_FILE ="../../repos/shared-svn/projects/nemo_mercator/data/original_files/shapeFiles/shapeFile_Ruhrgebiet/ruhrgebiet_boundary.shp";
    public static final String CORINE_LANDCOVER_NRW_SHAPE_FILE = "../../repos/shared-svn/projects/nemo_mercator/data/original_files/shapeFiles/CORINE_landcover_nrw/corine_nrw_src_clc12.shp";

    //feature key for above shape
    public static final String MUNICIPALITY_SHAPE_FEATURE_KEY = "KN";
    public static final String Ruhr_PLZ_SHAPE_FEATURE_KEY = "plz";
    //feature key for CORINE_LANDCOVER -- LandCoverUtils.CORINE_LANDCOVER_TAG_ID;

    // following is derived from the excel-sheet (repos/shared-svn/projects/nemo_mercator/doc/50_conceptual/trip_distances.xlsx)
    public static double RUHR_CAR_SHARE = 0.471;
    public static double RUHR_PT_SHARE = 0.158;
    public static double SAMPLE_SIZE = 0.01;

    // copying few methods from agarwalamit to depend only on 'vsp'

    public static double round(double number, int decimalPlace){
        double multiplier = Math.pow(10, decimalPlace);
        return Math.round(number * multiplier) / multiplier;
    }

    /**
     * Returns scenario containing only network file location.
     */
    public static Scenario loadScenarioFromNetwork(String networkFile) {
        Config config = new Config();
        config.addCoreModules();
        config.network().setInputFile(networkFile);
        return ScenarioUtils.loadScenario(config);
    }

    /**
     * Returns scenario containing only plans file location.
     */
    public static Scenario loadScenarioFromPlans(String plansFile) {
        Config config = new Config();
        config.addCoreModules();
        config.plans().setInputFile(plansFile);
        return ScenarioUtils.loadScenario(config);
    }

    public static DistanceDistribution getDistanceDistributionWithSeparatePt(double carCountScaleFactor, PlansCalcRouteConfigGroup plansCalcRouteConfigGroup,
                                                                             boolean mergeShortDistanceBins, boolean useEssenBochumrefereceData) {
        DistanceDistribution inputDistanceDistribution = new DistanceDistribution();
        Map<String, PlansCalcRouteConfigGroup.ModeRoutingParams> modeRoutingParamsMap = plansCalcRouteConfigGroup.getModeRoutingParams();

        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes(
                TransportMode.car, getBeelineDistanceFactor(modeRoutingParamsMap, TransportMode.car, 1.3));
        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes(
                TransportMode.pt, getBeelineDistanceFactor(modeRoutingParamsMap, TransportMode.pt, 1.3));
        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes(
                TransportMode.bike, getBeelineDistanceFactor(modeRoutingParamsMap, TransportMode.bike, 1.3));
        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes(
                TransportMode.walk, getBeelineDistanceFactor(modeRoutingParamsMap, TransportMode.walk, 1.3));
        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes(
                TransportMode.ride, getBeelineDistanceFactor(modeRoutingParamsMap, TransportMode.ride, 1.3));

        inputDistanceDistribution.setModeToScalingFactor(TransportMode.car, carCountScaleFactor);
        inputDistanceDistribution.setModeToScalingFactor(TransportMode.pt, 100.0);
        inputDistanceDistribution.setModeToScalingFactor(TransportMode.bike, 100.0);
        inputDistanceDistribution.setModeToScalingFactor(TransportMode.ride, 100.0);
        inputDistanceDistribution.setModeToScalingFactor(TransportMode.walk, 100.0);

        // we are always using essen-bochum reference data from shared-svn/projects/nemo_mercartor/doc/50_conceptual/trip_distances.xlsx
        inputDistanceDistribution.addToDistribution(TransportMode.car, new DistanceBin.DistanceRange(0, 1000.), 320099);
        inputDistanceDistribution.addToDistribution(TransportMode.pt, new DistanceBin.DistanceRange(0, 1000), 29416);
        inputDistanceDistribution.addToDistribution(TransportMode.bike, new DistanceBin.DistanceRange(0.0, 1000.), 88247.0);
        inputDistanceDistribution.addToDistribution(TransportMode.walk, new DistanceBin.DistanceRange(0.0, 1000.), 1660012.0);
        inputDistanceDistribution.addToDistribution(TransportMode.ride, new DistanceBin.DistanceRange(0.0, 1000.), 120346.0);

        inputDistanceDistribution.addToDistribution(TransportMode.car, new DistanceBin.DistanceRange(1000.0, 3000.), 1292600);
        inputDistanceDistribution.addToDistribution(TransportMode.pt, new DistanceBin.DistanceRange(1000.0, 3000.), 389262);
        inputDistanceDistribution.addToDistribution(TransportMode.bike, new DistanceBin.DistanceRange(1000.0, 3000.), 233988.0);
        inputDistanceDistribution.addToDistribution(TransportMode.walk, new DistanceBin.DistanceRange(1000.0, 3000.), 969147.0);
        inputDistanceDistribution.addToDistribution(TransportMode.ride, new DistanceBin.DistanceRange(1000.0, 3000.), 522180.0);

        inputDistanceDistribution.addToDistribution(TransportMode.car, new DistanceBin.DistanceRange(3000.0, 5000.), 1112761);
        inputDistanceDistribution.addToDistribution(TransportMode.pt, new DistanceBin.DistanceRange(3000.0, 5000.), 446320);
        inputDistanceDistribution.addToDistribution(TransportMode.bike, new DistanceBin.DistanceRange(3000.0, 5000.), 151014.0);
        inputDistanceDistribution.addToDistribution(TransportMode.walk, new DistanceBin.DistanceRange(3000.0, 5000.), 176786.0);
        inputDistanceDistribution.addToDistribution(TransportMode.ride, new DistanceBin.DistanceRange(3000.0, 5000.), 369415.0);

        inputDistanceDistribution.addToDistribution(TransportMode.car, new DistanceBin.DistanceRange(5000.0, 10000.), 1540620);
        inputDistanceDistribution.addToDistribution(TransportMode.pt, new DistanceBin.DistanceRange(5000.0, 10000.), 584702);
        inputDistanceDistribution.addToDistribution(TransportMode.bike, new DistanceBin.DistanceRange(5000.0, 10000.), 63506.0);
        inputDistanceDistribution.addToDistribution(TransportMode.walk, new DistanceBin.DistanceRange(5000.0, 10000.), 36089.0);
        inputDistanceDistribution.addToDistribution(TransportMode.ride, new DistanceBin.DistanceRange(5000.0, 10000.), 508044.0);

        inputDistanceDistribution.addToDistribution(TransportMode.car, new DistanceBin.DistanceRange(10000.0, 1000000.), 2259539);
        inputDistanceDistribution.addToDistribution(TransportMode.pt, new DistanceBin.DistanceRange(10000.0, 1000000.), 634865);
        inputDistanceDistribution.addToDistribution(TransportMode.bike, new DistanceBin.DistanceRange(10000.0, 1000000.), 51604.0);
        inputDistanceDistribution.addToDistribution(TransportMode.walk, new DistanceBin.DistanceRange(10000.0, 1000000.), 0.0);
        inputDistanceDistribution.addToDistribution(TransportMode.ride, new DistanceBin.DistanceRange(10000.0, 1000000.), 326112.0);

        return inputDistanceDistribution;
    }

    private static double getBeelineDistanceFactor(
            Map<String, PlansCalcRouteConfigGroup.ModeRoutingParams> modeRoutingParams,
            String modeKey,
            double defaultValue) {
        return modeRoutingParams.containsKey(modeKey) ? modeRoutingParams.get(modeKey).getBeelineDistanceFactor() : defaultValue;
    }

    public static DistanceDistribution getDistanceDistribution(double carCountScaleFactor, PlansCalcRouteConfigGroup plansCalcRouteConfigGroup,
                                                               boolean mergeShortDistanceBins, boolean useEssenBochumReferenceData) {
        DistanceDistribution inputDistanceDistribution = new DistanceDistribution();

        Map<String, PlansCalcRouteConfigGroup.ModeRoutingParams> modeRoutingParams = plansCalcRouteConfigGroup.getModeRoutingParams();

        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes("car", modeRoutingParams.containsKey("car") ? modeRoutingParams.get("car").getBeelineDistanceFactor() : 1.3); //+pt;
        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes("bike",modeRoutingParams.containsKey("bike") ? modeRoutingParams.get("bike").getBeelineDistanceFactor() : 1.3);
        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes("walk",modeRoutingParams.containsKey("walk") ? modeRoutingParams.get("walk").getBeelineDistanceFactor() : 1.3);
        inputDistanceDistribution.setBeelineDistanceFactorForNetworkModes("ride",modeRoutingParams.containsKey("ride") ? modeRoutingParams.get("ride").getBeelineDistanceFactor() : 1.3);

        inputDistanceDistribution.setModeToScalingFactor("car", carCountScaleFactor );
        inputDistanceDistribution.setModeToScalingFactor("bike", 100.0);
        inputDistanceDistribution.setModeToScalingFactor("walk", 100.0);
        inputDistanceDistribution.setModeToScalingFactor("ride", 100.0 );

        if (useEssenBochumReferenceData) {
            if (mergeShortDistanceBins) {

                inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(0.0,3000.),1681862.0+349515.0);
                inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(0.0,3000.),233988.0+88247.0);
                inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(0.0,3000.),969147.0+1660012.0);
                inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(0.0,3000.),522180.0+120346.0);

            } else {

                inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(0.0,1000.),349515.0); //car+PT
                inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(0.0,1000.),88247.0);
                inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(0.0,1000.),1660012.0);
                inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(0.0,1000.),120346.0);

                inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(1000.0,3000.),1681862.0);
                inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(1000.0,3000.),233988.0);
                inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(1000.0,3000.),969147.0);
                inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(1000.0,3000.),522180.0);

            }

            inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(3000.0,5000.),1559081.0);
            inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(3000.0,5000.),151014.0);
            inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(3000.0,5000.),176786.0);
            inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(3000.0,5000.),369415.0);

            inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(5000.0,10000.),2125322.0);
            inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(5000.0,10000.),63506.0);
            inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(5000.0,10000.),36089.0);
            inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(5000.0,10000.),508044.0);

            inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(10000.0,1000000.),2894404.0);
            inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(10000.0,1000000.),51604.0);
            inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(10000.0,1000000.),0.0);
            inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(10000.0,1000000.),326112.0);
            return inputDistanceDistribution;

        } else { // Essen as representative for urban parts in Ruhr and ...

            if (mergeShortDistanceBins) {

                inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(0.0,3000.),1547035.0+245472.0);
                inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(0.0,3000.),213707.0+62279.0);
                inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(0.0,3000.),1000502.0+1379412.0);
                inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(0.0,3000.),392418.0+60623.0);

            } else {

                inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(0.0,1000.),245472.0); //car+PT
                inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(0.0,1000.),62279.0);
                inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(0.0,1000.),1379412.0);
                inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(0.0,1000.),60623.0);

                inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(1000.0,3000.),1547035.0);
                inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(1000.0,3000.),213707.0);
                inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(1000.0,3000.),1000502.0);
                inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(1000.0,3000.),392418.0);

            }

            inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(3000.0,5000.),1604974.0);
            inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(3000.0,5000.),132017.0);
            inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(3000.0,5000.),191563.0);
            inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(3000.0,5000.),337532.0);

            inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(5000.0,10000.),2189502.0);
            inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(5000.0,10000.),90592.0);
            inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(5000.0,10000.),45296.0);
            inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(5000.0,10000.),442931.0);

            inputDistanceDistribution.addToDistribution("car", new DistanceBin.DistanceRange(10000.0,1000000.),2959574.0);
            inputDistanceDistribution.addToDistribution("bike", new DistanceBin.DistanceRange(10000.0,1000000.),52738.0);
            inputDistanceDistribution.addToDistribution("walk", new DistanceBin.DistanceRange(10000.0,1000000.),0.0);
            inputDistanceDistribution.addToDistribution("ride", new DistanceBin.DistanceRange(10000.0,1000000.),306624.0);
            return inputDistanceDistribution;
        }
    }

    public static AbstractModule createModalShareAnalysis() {
        return new AbstractModule() {
            @SuppressWarnings("PointlessBinding")
            @Override
            public void install() {
                this.bind(ModalShareEventHandler.class);
                this.addControlerListenerBinding().to(ModalShareControlerListener.class);

                this.bind(ModalTripTravelTimeHandler.class);
                this.addControlerListenerBinding().to(ModalTravelTimeControlerListener.class);
            }
        };
    }
}