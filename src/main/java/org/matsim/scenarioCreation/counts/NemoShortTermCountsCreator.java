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
package org.matsim.scenarioCreation.counts;


import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.FileVisitResult.TERMINATE;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.counts.Counts;
import org.matsim.scenarioCreation.UnZipFile;

/**
 * @author tschlenther
 *
 */
public class NemoShortTermCountsCreator extends NemoLongTermCountsCreator {

	/**
	 * @param network
	 * @param pathToCountDataRootDirectory
	 * @param pathToCountStationToOSMNodesMappingFile
	 * @param outputPath
	 */
	public NemoShortTermCountsCreator(Map<String,Counts> countsPerColumnCombination, Network network, String pathToCountDataRootDirectory,
									  String pathToCountStationToOSMNodesMappingFile, String outputPath, int firstYear, int lastYear) {
		super(countsPerColumnCombination, network, pathToCountDataRootDirectory, pathToCountStationToOSMNodesMappingFile, outputPath);

		super.setFirstDayOfAnalysis(LocalDate.of(firstYear,1,1));
		super.setLastDayOfAnalysis(LocalDate.of(lastYear,12,31));
	}

	@Override
	public void setFirstDayOfAnalysis(LocalDate day){
		throw new RuntimeException("Please dont set it for short term counting stations.");
	}

	@Override
	public void setLastDayOfAnalysis(LocalDate day){
		throw new RuntimeException("Please dont set it for short term counting stations.");
	}

	@Override
	public void run() {
		super.init();
		super.readData();
		  
		if(network!=null){
			readNodeIDsOfCountingStationsAndGetLinkIDs();
		}

		String description = "--Nemo short period count data--";
		SimpleDateFormat format = new SimpleDateFormat("YY_MM_dd_HHmmss");
		String now = format.format(Calendar.getInstance().getTime());
		description += "\n created: " + now;
		  
	  	convert(description);
		  
		finish();
		
	}
	
	@Override
	protected void analyzeYearDir(File rootDirOfYear, int currentYear) {
		log.info("Start analysis of directory " + rootDirOfYear.getPath());
		List<String> alreadyHandledCounts = new ArrayList<String>();
		UnZipFile unzipper = new UnZipFile();
		 File[] filesInRoot = rootDirOfYear.listFiles();
		 if (filesInRoot != null) {
		    for (File fileInRootDir : filesInRoot) {
		    	if(fileInRootDir.isDirectory() && !alreadyHandledCounts.contains(fileInRootDir.getName())){
		    		analyzeCountDirectory(fileInRootDir, currentYear);
		    	} else if(fileInRootDir.getName().endsWith("zip")){
    				String countID = fileInRootDir.getName().substring(0,fileInRootDir.getName().lastIndexOf("."));
    				if(!alreadyHandledCounts.contains(countID)){
    					try {
    						File unzippedFolder = unzipper.unZipFile(fileInRootDir);
    						analyzeCountDirectory(unzippedFolder, currentYear);

    						deleteFileOrFolder(unzippedFolder.toPath());
    					} catch (Exception e) {
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					}
    				}
		    	}
		    }
		  } else {
			  log.severe("something is wrong with the year directory .... please look here: " + rootDirOfYear.getAbsolutePath());
			  this.finish();
		  }
		
	}

	private void analyzeCountDirectory(File countDir, int currentYear) {
			File[] countData = countDir.listFiles();
			if(countData != null){
				try {
					for(File data : countData){
						if(data.getName().endsWith("xls")){
							if(!this.countingStationsToOmit.contains(Long.parseLong(data.getName().substring(0,8)))){
								readExcel(new FileInputStream(data), data.getName().substring(0, 8),currentYear);
							}
						}
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}else{
				log.severe("something is wrong with the count directory .... please look here: " + countDir.getAbsolutePath());
				this.finish();
			}
		
	}

	private void readExcel(FileInputStream fileInputStream, String countID, int year) {
		POIFSFileSystem fs;
		
		try {
			fs = new POIFSFileSystem(fileInputStream);
			HSSFWorkbook wb = new HSSFWorkbook(fs);
			HSSFSheet sheet = wb.getSheetAt(0);
			HSSFRow row;
			HSSFCell cell;
			
			String streetID = sheet.getRow(3).getCell(1).getStringCellValue();
			String name;
			if(!streetID.equals("")){
				name = countID + "_" + streetID ;
			}
			else{
				name = countID;
			}
			name = fixEncoding(name);
			
			row= sheet.getRow(20);
			Map<String,Integer> baseColumnsOfVehicleTypes = new HashMap<String,Integer>();
			for(int clm = row.getFirstCellNum(); clm < row.getLastCellNum(); clm++){
				cell = row.getCell(clm);
				if(this.allNeededColumnHeaders.contains(cell.getStringCellValue()) && !baseColumnsOfVehicleTypes.containsKey(cell.getStringCellValue())){
					baseColumnsOfVehicleTypes.put(cell.getStringCellValue(), clm);
				}
			}
			//need to do this manually for KFZ and SV, as they are written in small letters in the short term excel files
			if(allNeededColumnHeaders.contains(RawDataVehicleTypes.KFZ.toString())){
				baseColumnsOfVehicleTypes.put(RawDataVehicleTypes.KFZ.toString(), 5);
			}
			if(allNeededColumnHeaders.contains(RawDataVehicleTypes.SV.toString())){
				baseColumnsOfVehicleTypes.put(RawDataVehicleTypes.SV.toString(), 6);
			}
			
//			HourlyCountData kfzCountData = this.kfzCountingStationsData.get(countID);
//			HourlyCountData svCountData = this.kfzCountingStationsData.get(countID);
//			
//			if(kfzCountData == null){
//				kfzCountData = new HourlyCountData(name, null);	//ID = countID_countName_streetID
//			}
//			
//			if(svCountData == null){
//				svCountData = new HourlyCountData(name, null);		//ID = countID_countName_streetID
//			}
			
			
			for(int currentRow = 21; currentRow <= sheet.getLastRowNum(); currentRow ++){
				row = sheet.getRow(currentRow);
				
				int dayOfMonth = Integer.parseInt(row.getCell(0).getStringCellValue().substring(0, 2));
				int month = Integer.parseInt(row.getCell(0).getStringCellValue().substring(3, 5));
				
				LocalDate currentDate = LocalDate.of(year, month, dayOfMonth);
				
				cell = row.getCell(4);
				boolean isValidData = false;
				if(cell.getCellType() == 0){
//					System.out.println("cell type is numeric: value=" + cell.getNumericCellValue());
					
				} else if(cell.getCellType() == 1){
					if(cell.getStringCellValue().equals("-")){
						isValidData = true;
					}
				} else{
					System.out.println("cell type = " + cell.getCellType());
				}
				
				if(this.weekRange_min <= currentDate.getDayOfWeek().getValue() && currentDate.getDayOfWeek().getValue() <= this.weekRange_max
						&& isValidData){
					
					Map<String,Tuple<Integer,Integer>> trafficVolumesPerVehicleType = new HashMap<String,Tuple<Integer,Integer>>();
					int hour = Integer.parseInt(row.getCell(1).getStringCellValue().substring(0,2));
					
					for(String header: baseColumnsOfVehicleTypes.keySet()){
						int vlmDir1 = getIntegerValue(row.getCell(baseColumnsOfVehicleTypes.get(header)));
						int vlmDir2;
						if(header.equals(RawDataVehicleTypes.KFZ.toString()) || header.equals(RawDataVehicleTypes.SV)){
							vlmDir2 = getIntegerValue(row.getCell(baseColumnsOfVehicleTypes.get(header) + 2));
						} else{
							vlmDir2 = getIntegerValue(row.getCell(baseColumnsOfVehicleTypes.get(header) + 10));
						}
						trafficVolumesPerVehicleType.put(header, new Tuple<Integer,Integer>(vlmDir1,vlmDir2));
					}
					
					//calculate traffic volume for each combination, e.g. combination is. "Pkw+Rad"
					for(String combination : this.countingStationsData.keySet()){
						String[] headers = combination.split(";");
						Double sumDir1 = 0.;
						Double sumDir2 = 0.;
						for(String header : headers){
							sumDir1 += trafficVolumesPerVehicleType.get(header).getFirst();
							sumDir2 += trafficVolumesPerVehicleType.get(header).getSecond();
						}
						
						//get the HourlyCountData object and set volumes
						HourlyCountData data = this.countingStationsData.get(combination).get(countID);
						if(data == null){
							data = new HourlyCountData(name, null); //ID = countID_countName_streetID
						}
						
						data.computeAndSetVolume(true, hour, sumDir1);
						data.computeAndSetVolume(false, hour, sumDir2);
						this.countingStationsData.get(combination).put(countID,data);
					}

				}
			}
			wb.close();
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}
	int getIntegerValue (HSSFCell cell){
		if(cell.getCellType() == 0){
			return (int) cell.getNumericCellValue();
		}else{
			return Integer.parseInt(cell.getStringCellValue());
		}
	}

	public static void deleteFileOrFolder(final Path path) throws IOException {
		  Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
		    @Override public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
		      throws IOException {
		      Files.delete(file);
		      return CONTINUE;
		    }

		    @Override public FileVisitResult visitFileFailed(final Path file, final IOException e) {
		      return handleException(e);
		    }

		    private FileVisitResult handleException(final IOException e) {
		      e.printStackTrace(); // replace with more robust error handling
		      return TERMINATE;
		    }

		    @Override public FileVisitResult postVisitDirectory(final Path dir, final IOException e)
		      throws IOException {
		      if(e!=null)return handleException(e);
		      Files.delete(dir);
		      return CONTINUE;
		    }
		  });
		};
	
}
