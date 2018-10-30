/* *********************************************************************** *
 * project: org.matsim.*
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2017 by the members listed in the COPYING,        *
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

/**
 * 
 */
package org.matsim.scenarioCreation;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import lombok.val;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.counts.Counts;
import org.matsim.counts.CountsWriter;
import org.matsim.scenarioCreation.counts.NemoLongTermCountsCreator;
import org.matsim.scenarioCreation.counts.NemoShortTermCountsCreator;
import org.matsim.scenarioCreation.counts.RawDataVehicleTypes;
import org.matsim.scenarioCreation.network.NemoNetworkCreator;
import org.matsim.util.NEMOUtils;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author tschlenther
 *
 */
	class NemoScenarioCreator {

	public static Logger log = Logger.getLogger(NemoScenarioCreator.class.getName());

    private final static String INPUT_OSMFILE = "/projects/nemo_mercator/data/matsim_input/zz_archive/network/06042018/NRW_completeTransportNet.osm.gz";

    private final static String INPUT_LONGTERM_COUNT_DATA_ROOT_DIR = "/projects/nemo_mercator/data/original_files/counts_rohdaten/dauerzaehlstellen";
    private final static String INPUT_COUNT_NODES_MAPPING_CSV = "/projects/nemo_mercator/data/matsim_input/zz_archive/counts/mapmatching/OSMNodeIDs_Dauerzaehlstellen.csv";

    private final static String INPUT_SHORTTERM_COUNT_DATA_ROOT_DIR = "/projects/nemo_mercator/data/original_files/counts_rohdaten/verkehrszaehlung_2015/complete_Data";
//	private final static String INPUT_SHORTTERM_COUNT_MAPPING_CSV = "data/input/counts/mapmatching/Nemo_kurzfristZaehlstellen_OSMNodeIDs_UTM33N-Master.csv";
private final static String INPUT_SHORTTERM_COUNT_MAPPING_CSV = "/projects/nemo_mercator/data/matsim_input/zz_archive/counts/mapmatching/Nemo_kurzfristZaehlstellen_OSMNodeIDs_UTM33N-allStationsInclNotFound.csv";
    private final static String INPUT_NETWORK_SHAPE_FILTER = "/projects/nemo_mercator/data/original_files/shapeFiles/shapeFile_Ruhrgebiet/ruhrgebiet_boundary.shp";
    private static String INPUT_NETWORK = null;//"/projects/nemo_mercator/data/matsim_input/2018-05-03_vspDefault_OSM_net/2018-05-03_NRW_coarse_filteredcleaned_network.xml.gz";	// a network can be read in, in case it already exists, so network creation can be skipped or only simplifying and cleaning can be performed
	
	private static boolean doSimplify = false;
    private static boolean doCleaning = true;
	private final static String networkCoordinateSystem = NEMOUtils.NEMO_EPSG;

    private static String OUTPUT_DIR_NETWORK = "/projects/nemo_mercator/data/matsim_input/network/";
	private static String OUTPUT_PREFIX_NETWORK = "";
    private static String OUTPUT_COUNTS_DIR = "./projects/nemo_mercator/data/matsim_input/network/";
	
	//	dates are included in aggregation									year, month, dayOfMonth
	private final static LocalDate firstDayOfDataAggregation = LocalDate.of(2014, 1, 1);
	private final static LocalDate lastDayOfDataAggregation = LocalDate.of(2016, 12, 31);
	
	private static List<LocalDate> datesToIgnore = new ArrayList<LocalDate>();
	
	public static void main(String[] args) {

        val arguments = new NemoScenarioCreator.InputArguments();
        JCommander.newBuilder().addObject(arguments).build().parse(args);

		setOutputPaths();
		Network network = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getNetwork();
		
		/*
		 * create the network, use NemoNetworkCreator if new network is needed
		 * 
		 * 1) create new network, parsing from an osm file
		 * 2) work with an existing network 
		 *		a) keep network as it is and only parse it in
		 *		b) parse given network, give and give it to NemoNetworkCreator so it can simplify or clean it
		 */
		
		if (INPUT_NETWORK==null) {
			//create new network (1)
            NemoNetworkCreator netCreator = new NemoNetworkCreator(
                    arguments.svnDir + INPUT_OSMFILE,
                    Arrays.asList(arguments.svnDir + INPUT_COUNT_NODES_MAPPING_CSV, arguments.svnDir + INPUT_SHORTTERM_COUNT_MAPPING_CSV),
                    networkCoordinateSystem,
                    arguments.svnDir + OUTPUT_DIR_NETWORK,
                    "2018-05-03_NRW_coarse_");
            netCreator.setShapeFileToFilter(arguments.svnDir + INPUT_NETWORK_SHAPE_FILTER);
			netCreator.createNetwork(doSimplify, doCleaning);
			netCreator.writeNetwork();
		} else {
			//working with existing network (2)
			//a)
			MatsimNetworkReader netReader = new MatsimNetworkReader(network);
			netReader.readFile(INPUT_NETWORK);
			if (doCleaning || doSimplify ){
				//b)
				NemoNetworkCreator netCreator = new NemoNetworkCreator(network,INPUT_OSMFILE,
						Arrays.asList(INPUT_COUNT_NODES_MAPPING_CSV,INPUT_SHORTTERM_COUNT_MAPPING_CSV),networkCoordinateSystem,OUTPUT_DIR_NETWORK,OUTPUT_PREFIX_NETWORK);
				netCreator.setShapeFileToFilter(INPUT_NETWORK_SHAPE_FILTER);
				netCreator.createNetwork(doSimplify, doCleaning);
			}
		}

		/*	specify which counts files you want to generate:
		 * 	the raw data contains different columns, they are to be found in the RawDataVehicleTypes enum
		 * 	you can hand over the count creator an array
		 * 		- for each element, one count file is generated
		 * 		- columns can be summed up, e.g. you can aggregate motorbikes and bicycles by saying "Mot+Rad"
		 * 			=>always use ";" to connect two vehicle types		!!!
		 * 		- you can relate one column to several combinations, see example below
		 * 
		 * 	in this example, 4 count files would be generated, the third would contain the averages of the sum of cars and bikes
		 * 
		 * String[] combinations = new String[4];
		 * combinations[0] = RawDataVehicleTypes.Pkw.toString();
		 * combinations[1] = RawDataVehicleTypes.Rad.toString();
		 * combinations[2] = RawDataVehicleTypes.Pkw.toString() + ";" + RawDataVehicleTypes.Rad.toString();
		 * combinations[3] = RawDataVehicleTypes.SV.toString();
		 */
		
		String[] combinations = new String[4];
		combinations[0] = RawDataVehicleTypes.Pkw.toString();
//		combinations[1] = RawDataVehicleTypes.Rad.toString();
//		combinations[2] = RawDataVehicleTypes.SV.toString();

        Set<String> columnCombinations = new HashSet<>(Arrays.asList(RawDataVehicleTypes.Pkw.toString()));

		Map<String, Counts<Link>> countsPerColumnCombination = new HashMap<>();
		for(String combination: combinations){
			if(combination!=null){
				countsPerColumnCombination.put(combination, new Counts<Link>());
			}
		}

		//instantiate NemoCountsCreator
        val longTermCountsCreator = new NemoLongTermCountsCreator.Builder()
                .setSvnDir(arguments.svnDir)
                .withNetwork(network)
                .withColumnCombination(columnCombinations)
                .useCountsBetween(firstDayOfDataAggregation, lastDayOfDataAggregation)
                .withIgnoredDates(datesToIgnore)
                .withStationIdsToOmit(5002l, 5025l)
                .build();
        val longTermCounts = longTermCountsCreator.run();

		
		/*specify analysis dates.. any data before firstDayOfDataAggregation and after lastDayOfDataAggregation are ignored.
		you can specify a list of ignored dates (e.g. school holidays) , but his will only have an effect on long term stations!! **/
        //longTermCountsCreator.setFirstDayOfAnalysis(firstDayOfDataAggregation);
        //longTermCountsCreator.setLastDayOfAnalysis(lastDayOfDataAggregation);
        //longTermCountsCreator.setDatesToIgnore(datesToIgnore);
		
		/*if you want to omit specific count data, you can add a list of their station numbers (see rohdaten)**/
//		longTermCountsCreator.setCountingStationsToOmit(new ArrayList<Long>());
//		longTermCountsCreator.addToStationsToOmit(lll);

		//list counting stations that might lead to some calibration problems or where data/localization is not clear
        //longTermCountsCreator.addToStationsToOmit(5002l);
        //longTermCountsCreator.addToStationsToOmit(5025l);		//not clear where exactly the counting station is located (hauptfahrbahn?)
		
		/*specify which months to analyze by setting monthRange_min and monthRange_max. 1 stands for january, 12 for december*/
//		longTermCountsCreator.setMonthRange_min(monthRange_min);
		
		/*specify which weekdays you want to analyze by modifiying weekRange_min and weekRange_max. 1 stands for monday, 7 stands for sunday*/
//		longTermCountsCreator.setWeekRangeMax(weekRange_max);

        //longTermCountsCreator.run();

		//short term
        val shortTermCountsCreator = new NemoShortTermCountsCreator.Builder()
                .setSvnDir(arguments.svnDir)
                .withNetwork(network)
                .withColumnCombination(columnCombinations)
                .build();
        val shortTermCounts = shortTermCountsCreator.run();

        //NemoShortTermCountsCreator shortTermCounts = new NemoShortTermCountsCreator(countsPerColumnCombination, network, INPUT_SHORTTERM_COUNT_DATA_ROOT_DIR ,
        //		INPUT_SHORTTERM_COUNT_MAPPING_CSV, OUTPUT_COUNTS_DIR + "shortTerm/", 2011, 2015);
        //shortTermCounts.setDatesToIgnore(datesToIgnore);

		//remember to set week day range again!
//		shortTermCounts.setWeekRangeMax(weekRange_max);
        //shortTermCounts.run();

		//write output
		writeOutput(OUTPUT_COUNTS_DIR, "allCounts", countsPerColumnCombination);
	}

	public static void writeOutput(String outputPath, String countFilesName, Map<String,Counts<Link>> countsPerColumnCombination){

		String out = "NemoCounts_data_";
		for(String combination : countsPerColumnCombination.keySet()){
			CountsWriter writer = new CountsWriter(countsPerColumnCombination.get(combination));
			log.info("writing " + combination + " counts to " + outputPath + out + countFilesName + "_" + combination + ".xml");
			writer.write(outputPath + out + countFilesName + "_" + combination + ".xml");
			log.info("finished writing " + combination + " data");
		}
		log.info("....finished writing all output data...");
	}

	
	private static void setOutputPaths(){
		SimpleDateFormat logTime = new SimpleDateFormat("ddMMyyyy");
        String creationTime = logTime.format(Calendar.getInstance().getTime());
		OUTPUT_COUNTS_DIR += creationTime + "/"; 
		OUTPUT_PREFIX_NETWORK += "Nemo_Network_" + creationTime;
		if(doSimplify) OUTPUT_PREFIX_NETWORK += "_simpl";
		if(doCleaning) OUTPUT_PREFIX_NETWORK += "_clean";
		OUTPUT_PREFIX_NETWORK += "_" + networkCoordinateSystem.replace(":", "_");
		
	}

    private static class InputArguments {

        @Parameter(names = "-svnDir", required = true)
        private String svnDir;
    }
	
}
