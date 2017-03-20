package SoilJ.tools;

/**
 *SoilJ.tools is a collection of classes for SoilJ, 
 *a collection of ImageJ plugins for the semi-automatized processing of 3-D X-ray images of soil columns
 *Copyright 2014 2015 2016 2017 John Koestel
 *
 *This program is free software: you can redistribute it and/or modify
 *it under the terms of the GNU General Public License as published by
 *the Free Software Foundation, either version 3 of the License, or
 *(at your option) any later version.
 *
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License
 *along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import ij.IJ;
import ij.ImageListener;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.plugin.Concatenator;
import ij.plugin.Filters3D;
import ij.plugin.GaussianBlur3D;
import ij.plugin.PlugIn;
import ij.plugin.Slicer;
import ij.plugin.filter.GaussianBlur;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.apache.commons.math3.analysis.function.Logistic;
import org.apache.commons.math3.analysis.function.Logistic.Parametric;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.stat.StatUtils;
import org.apache.commons.math3.stat.descriptive.rank.Median;

import SoilJ.copiedTools.JParticleCounter;
import SoilJ.tools.ObjectDetector;
import SoilJ.tools.ObjectDetector.ColumnContainer;

/** 
 * ImageManipulator is one of the two main SoilJ classes (the other being ObjectDetector). 
 * It contains subroutines which changes image contents. 
 * 
 * @author John Koestel
 *
 */

public class ImageManipulator implements PlugIn {
	
	// carries out all sorts of image manipulations (illumination correction, changes tilt of object, applying filters..)
	
	public void run(String arg) {
		//nothing will be run here..		
	}
	
	///////////////////////////////////////////
	// classes
	///////////////////////////////////////////
	
	public ImagePlus calc3D(ImagePlus A, String nowGauge, MenuWaiter.Calc3DMenuReturn m3D, ImagePlus B) {
		
		StackCalculator sC= new StackCalculator();
		RoiHandler roi = new RoiHandler();
		InputOutput jIO = new InputOutput();
		
		ImagePlus outTiff0, outTiff = new ImagePlus();
		
		outTiff0 = null;		
		if (m3D.operation.equalsIgnoreCase("+")) outTiff0 = sC.add(A, B);
		if (m3D.operation.equalsIgnoreCase("-")) outTiff0 = sC.subtract(A, B);
		
		//clear outside 
		if (m3D.useInnerCircle) {	
			
			//read InnerCircle file
			ObjectDetector jOD = new ObjectDetector();
			ObjectDetector.ColCoords3D jCO = jOD.new ColCoords3D();
			int versio = jIO.checkInnerCircleFileVersion(nowGauge);			
			if (versio == 0) jCO = jIO.readInnerCircleVer0(nowGauge);	
			else jCO = jIO.readInnerCircleVer1(nowGauge);	
			
			ImageStack outStack = new ImageStack(outTiff0.getWidth(), outTiff0.getHeight()); 
			PolygonRoi[] nowRoi = roi.makeMeAPolygonRoiStack("inner", "manual", jCO, -1);
			
			for (int i = 0 ; i < outTiff0.getNSlices() ; i++) {
				
				outTiff0.setPosition(i+1);
				ImageProcessor nowIP = outTiff0.getProcessor();
				ImageProcessor outIP = nowIP.duplicate();
				
				outIP.setBackgroundValue(0d);
				outIP.fillOutside(nowRoi[i]);
				
				outStack.addSlice(outIP);
			}
			
			outTiff.setStack(outStack);
			
		}
		else outTiff = outTiff0;
		
		return outTiff;		
	}
	
	public class StackCalculator {
		
		public ImagePlus add(ImagePlus a, ImagePlus b) {
			
			ImageStack outStack = new ImageStack(a.getWidth(), a.getHeight()); 
			ImagePlus outTiff = new ImagePlus();
			
			for (int z = 0 ; z < a.getNSlices() ; z++) {
				
				a.setPosition(z + 1);
				b.setPosition(z + 1);
				
				ImageProcessor aIP = a.getProcessor();
				ImageProcessor bIP = b.getProcessor();
				
				ImageProcessor outIP = aIP.duplicate();
				
				for (int x = 0 ; x < a.getWidth() ; x++) {
					for (int y = 0 ; y < a.getHeight() ; y++) {
						int avox = aIP.getPixel(x, y);
						int bvox = bIP.getPixel(x, y);
						outIP.putPixel(x, y, avox + bvox);
					}
				}
				
				outStack.addSlice(outIP);
			}
			
			outTiff.setStack(outStack);
			
			return outTiff;
		}
		
		public ImagePlus subtract(ImagePlus a, ImagePlus b) {
			
			ImageStack outStack = new ImageStack(a.getWidth(), a.getHeight()); 
			ImagePlus outTiff = new ImagePlus();
			
			for (int z = 0 ; z < a.getNSlices() ; z++) {
				
				a.setPosition(z + 1);
				b.setPosition(z + 1);
				
				ImageProcessor aIP = a.getProcessor();
				ImageProcessor bIP = b.getProcessor();
				
				ImageProcessor outIP = aIP.duplicate();
				
				for (int x = 0 ; x < a.getWidth() ; x++) {
					for (int y = 0 ; y < a.getHeight() ; y++) {
						int avox = aIP.getPixel(x, y);
						int bvox = bIP.getPixel(x, y);
						outIP.putPixel(x, y, avox - bvox);
					}
				}
				
				outStack.addSlice(outIP);
			}
			
			outTiff.setStack(outStack);
			
			return outTiff;
		}
		
	}
		
	///////////////////////////////////////////////
	// Melitta
	//////////////////////////////////////////////
	
	public ImagePlus applyMedianFilterAndUnsharpMask(ImagePlus nowTiff, MenuWaiter.MedianFilterAndUnsharpMaskReturn mMUS) {				
		StackCalculator mSC = new StackCalculator();
		
		//construct some objects
		ImageStack zStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		ImageStack readyStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());		
		
		//define filter codes			
		int median3DFilter = 11; // 11 is the code for the 3D median filter..	
	
		//apply 3-D median filter		
		readyStack = nowTiff.getStack();
		if (mMUS.medianFilterSizeXDir > 1 & mMUS.medianFilterSizeYDir > 1 & mMUS.medianFilterSizeZDir > 1 ) {
			zStack = Filters3D.filter(readyStack, median3DFilter, mMUS.medianFilterSizeXDir, mMUS.medianFilterSizeYDir, mMUS.medianFilterSizeZDir);
		}
		else {
			zStack = readyStack;
		}
		ImagePlus zTiff = new ImagePlus();
		zTiff.setStack(zStack);		
	
		//apply 3-D unsharp mask
		ImagePlus blurTiff = zTiff.duplicate();
		GaussianBlur3D.blur(blurTiff, mMUS.uMaskStandardDeviationXDir, mMUS.uMaskStandardDeviationYDir, mMUS.uMaskStandardDeviationZDir);
		
		//calculate difference between blurTiff and original		
		ImagePlus diffTiff = mSC.subtract(zTiff,blurTiff);
		
		//and weighted mask
		ImageStack weightedStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		for (int i = 0 ; i < diffTiff.getNSlices() ; i++) {
			diffTiff.setPosition(i+1);
			ImageProcessor nowIP = diffTiff.getProcessor();
			nowIP.multiply(mMUS.uMaskSharpeningWeight);
			weightedStack.addSlice(nowIP);
		}
		ImagePlus unsharpMask = new ImagePlus();
		unsharpMask.setStack(weightedStack);
		
		//sharpened Image
		ImagePlus filtTiff = mSC.add(zTiff, unsharpMask);
		
		//return filtered 3D image
		return filtTiff;
	}
		
	///////////////////////////////////////////////
	// geo and illu correct
	//////////////////////////////////////////////
	
	/*public ColumnContainer carveOutSteelColumn(ImagePlus nowTiff, double wallThickness) {
	
		//construct some objects
		Median jMed = new Median();
		
		ObjectDetector jOD = new ObjectDetector();
		ObjectDetector.ColumnCoordinates prelimCC = null;
		ObjectDetector.ColumnCoordinates preciseCC = null;
		ObjectDetector.ColumnContainer colCon = jOD.new ColumnContainer();
		
		ImagePlus upTiff = new ImagePlus();
	
		//find the inner edge of the PVC column
		prelimCC = jOD.findOrientationSteelColumn(nowTiff);
		
		//init colCon
		colCon.nowTiff = nowTiff;
		colCon.prelimCC = prelimCC;
		
		//check if column is already upright					
		upTiff = putSteelColumnUprightInCenter(nowTiff, prelimCC);
		
		//upTiff.draw();
		//upTiff.show();
		
		//re-determine the column outlines
		colCon.preciseCC = jOD.findAllSteelWalls(upTiff, prelimCC, wallThickness);
	
		//set top of the soil column to 0
		preciseCC.topOfColumn = 0;
	
		//sample wall illumination and save it	
		double[] medianOfWallgrayValue0 = jOD.findMedianSteelgrayValues(upTiff, preciseCC);
		
		//evaluate gray values..
		int[] topBotHeight = jOD.postProcessSteelgrayValues(medianOfWallgrayValue0);
		
		//shift top and bottom accordingly..
		preciseCC.topOfColumn = topBotHeight[0];
		preciseCC.bottomOfColumn = topBotHeight[1];
		preciseCC.heightOfColumn = topBotHeight[2];
		double[] medianOfWallgrayValue = new double[preciseCC.heightOfColumn];
		for (int i = preciseCC.topOfColumn ; i < preciseCC.bottomOfColumn ; i++) medianOfWallgrayValue[i - preciseCC.topOfColumn] = medianOfWallgrayValue0[i]; 		
		preciseCC.steelgrayValue = jMed.evaluate(medianOfWallgrayValue);
		preciseCC.wallgrayValues = medianOfWallgrayValue0;
		
		//do illumination correction and cut away top and bottom
		colCon.cutTiff = doSteelIlluCorrAndCutOffEnds(upTiff, preciseCC);
			
		return colCon;
	
	}*/
	
	public ImagePlus beamDeHardenThis(ImagePlus nowTiff, String nowGaugePath, MenuWaiter.BeamDeHardeningReturn mBDH) {
		
		//init objects
		InputOutput jIO = new InputOutput();
		ObjectDetector jOD = new ObjectDetector(); 
		FitStuff fittings = new FitStuff();
		ImageManipulator jIM = new ImageManipulator();
		
		//init variables
		ImagePlus outTiff = new ImagePlus();		
		ObjectDetector.ColCoords3D jPCO = null;		
			
		//load gauge file
		//read InnerCircle file		
		int versio = jIO.checkInnerCircleFileVersion(nowGaugePath);			
		if (versio == 0) jPCO = jIO.readInnerCircleVer0(nowGaugePath);	
		else jPCO = jIO.readInnerCircleVer1(nowGaugePath);			
		
		//get 1D sample's brightness 
		//ObjectDetector.SampleBrightness myBri = jOD.getSampleBrightness(nowTiff, jPCO);
		
		//get radial illumination
		//radialgrayValues = jOD.getRadialPVCgrayValues(nowTiff, jPCO, radialMappingFactor, mBDH);
		
		//get modes of radial histograms
		ObjectDetector.RadialModes myModes = jOD.getRadialPVCIllumination(nowTiff, jPCO, mBDH);
		
		//fit a curve to the matrix brightness data..
		FitStuff.FittingResults myLogisticFit = fittings.fitGeneralizedLogisticFunction(myModes, mBDH.maxEval);
					
		//fill gaps and bad fits
		double[][] filteredGLFParams = jIM.fillGapsInFittedBeamHardeningCorrectionMap(myLogisticFit, myModes, mBDH);
		
		//assemble beam hardening correction map
		ImageProcessor blurIP = assembleCorrectionMapForBeamHardening(myModes, filteredGLFParams);
		
//		DisplayThings dT  = new DisplayThings();
//		dT.displayIP(blurIP, "blurIP");			
		
		//apply brightness correction to collected minima..
		myModes = applyBeamHardeningCorrection2AirPhaseGammaData(myModes, blurIP);		
		
		//fit a curve to the air-phase brightness data..
		FitStuff.FittingResults myHyperbolicFit = fittings.fitHyperbolicFunction2Params(myModes, mBDH.maxEval);
		
		//fill gaps and bad fits
		ImageProcessor gammaIP = assembleCorrectionMapForAirPhaseGamma(myLogisticFit, myHyperbolicFit, myModes, mBDH);
		
//		dT.displayIP(gammaIP, "gammaIP");	

		//correct image for beam hardening and air-phase gamma
		outTiff = correct4PVCBeamHardening(nowTiff, blurIP, gammaIP, jPCO, mBDH);
		
		//return corrected tiffs..
		return outTiff;
		
	}
	
	
	public ObjectDetector.RadialModes applyBeamHardeningCorrection2AirPhaseGammaData(ObjectDetector.RadialModes myModes, ImageProcessor blurIP) { 
		
		for (int i = 0 ; i < myModes.maskingThreshold.length ; i++) {
			for (int j = 0 ; j < myModes.radius.length - 1 ; j++) {
				double nowCorrFac = blurIP.getPixelValue((int)Math.floor(myModes.radius[j]) + 1, i);
				double nowRef = blurIP.getPixelValue(blurIP.getWidth() - 1, i);
				myModes.maskedRadialMinima[i][j] = myModes.maskedRadialMinima[i][j] * nowRef / nowCorrFac;
			}
		}
		
		return myModes;
	}	
	
	public ImageProcessor assembleCorrectionMapForBeamHardening(ObjectDetector.RadialModes myModes, double[][] filteredGLFParams) {
		
		TailoredMaths maths = new TailoredMaths();
		
		//create a correction function map
		double[] radius = new double[(int)maths.max(myModes.radius)];
		for (int i = 0 ; i < radius.length ; i++) radius[i] = i;
		Parametric myLogL = new Logistic.Parametric();
		FloatProcessor corrIP = new FloatProcessor(radius.length, myModes.maskingThreshold.length);
		for (int y = 0 ; y < myModes.maskingThreshold.length ; y++) {
			double[] nowGLFP = new double[6];
			for (int i = 0 ; i < 6 ; i++) nowGLFP[i] = filteredGLFParams[y][i];
			for (int x = 0 ; x < radius.length ; x++) {	
				double myValue2Put = myModes.maskedRadialModes[y][myModes.radius.length - 1];
				try {myValue2Put = myLogL.value(x, nowGLFP);} 
				catch (NotStrictlyPositiveException e) {} 
				corrIP.putPixelValue(x, y, myValue2Put);
			}
		}
		
		//ImagePlus letsSee = new ImagePlus("nonFiltered", corrIP);
		//letsSee.updateAndDraw();letsSee.show();
		
		GaussianBlur myGB = new GaussianBlur();
		double accuracy = 0.01d;
		FloatProcessor blurIP = (FloatProcessor)corrIP.duplicate();
		myGB.blurFloat(blurIP, 20, 20, accuracy);			
		//ImagePlus letsSeeM = new ImagePlus("Filtered",blurIP);
		//letsSeeM.updateAndDraw();letsSeeM.show();
		
		return blurIP;
	}
	
	public ImageProcessor assembleCorrectionMapForAirPhaseGamma(ObjectDetector.RadialModes myModes, double[][] myGapFilledHFParameters) {
		
		FitStuff fittings = new FitStuff();
		TailoredMaths maths = new TailoredMaths();
		
		//create a correction function map
		double[] radius = new double[(int)maths.max(myModes.radius)];
		for (int i = 0 ; i < radius.length ; i++) radius[i] = i;
		FitStuff.HyperbolicFunction2Params myHF = fittings.new HyperbolicFunction2Params();
		FloatProcessor corrIP = new FloatProcessor(radius.length, myModes.maskingThreshold.length);
		for (int y = 0 ; y < myModes.maskingThreshold.length ; y++) {
			double[] nowHFparams = new double[2];
			myHF.setup(StatUtils.max(myModes.radius), myModes.maskedRadialMinima[y][myModes.radius.length - 1]);
			for (int i = 0 ; i < 2 ; i++) nowHFparams[i] = myGapFilledHFParameters[y][i];
			for (int x = 0 ; x < radius.length ; x++) {	
				double myValue2Put = myModes.maskedRadialModes[y][myModes.radius.length - 1];
				try {myValue2Put = myHF.value(x, nowHFparams);} 
				catch (NotStrictlyPositiveException e) {} 
				corrIP.putPixelValue(x, y, myValue2Put);
			}
		}
		
		//ImagePlus letsSee = new ImagePlus("nonFiltered", corrIP);
		//letsSee.updateAndDraw();letsSee.show();
		
		GaussianBlur myGB = new GaussianBlur();
		double accuracy = 0.01d;
		FloatProcessor blurIP = (FloatProcessor)corrIP.duplicate();
		myGB.blurFloat(blurIP, 20, 20, accuracy);			
		//ImagePlus letsSeeM = new ImagePlus("Filtered",blurIP);
		//letsSeeM.updateAndDraw();letsSeeM.show();
		
		return blurIP;
	}
	
	public double[][] fillGapsInFittedBeamHardeningCorrectionMap(FitStuff.FittingResults myFit, ObjectDetector.RadialModes myModes, MenuWaiter.BeamDeHardeningReturn mBDH) {
		
		RollerCaster cast = new RollerCaster();
		
		double[] R2 = myFit.R2;
		int standardRadius = (int)Math.round(StatUtils.max(myModes.radius));
		double[] realRadius = new double[standardRadius];
		ArrayList<Integer> testOnes = new ArrayList<Integer>();
		ArrayList<Integer> goodOnes = new ArrayList<Integer>();		
		
		double[][] gapFilledParameters = new double[R2.length][myFit.numberOfParams];
		
		//sample the maximal beamhardening artifact in the soil
		for (int i = R2.length/3 ; i < 2 * R2.length / 3 ; i++) if (R2[i] > mBDH.severeGoodnessCriterion) testOnes.add(i);		
		double[] A = new double[testOnes.size()];			//minimum of radial brightness profile
		double[] K = new double[testOnes.size()];			//maximum of radial brightness profile
		for (int i = 0 ; i < testOnes.size() ; i++) {
			A[i] = myFit.params[testOnes.get(i)][4];
			K[i] = myFit.params[testOnes.get(i)][0];
		}
		double expectedBrightnessDifference = StatUtils.percentile(K, 50) - StatUtils.percentile(A, 50);
		
		//set all R2 of fits with more than the 2 expected brightness difference to 0;
		for (int i = 0 ; i < R2.length ; i++) if (myFit.params[i][0] - myFit.params[i][4] > expectedBrightnessDifference | myFit.params[i][0] - myFit.params[i][4] < 0) {
			R2[i] = 0;
		}
		
		//find first and last good fit
		int firstGood = 0;
		int lastGood = 0;
		for (int i = 0 ; i < R2.length ; i++) {
			if (firstGood == 0 & R2[i] > mBDH.severeGoodnessCriterion) firstGood = i;
			if (R2[i] > mBDH.severeGoodnessCriterion) lastGood = i;
		}
		
		//plug in close-to-wall values before firstGood and after lastGood
		for (int i = 0 ; i < firstGood ; i++) {
			myFit.params[i][0] = myModes.maskedRadialModes[i][myModes.radius.length - 1];
			myFit.params[i][1] = myFit.params[firstGood][1];
			myFit.params[i][2] = myFit.params[firstGood][2];
			myFit.params[i][3] = myFit.params[firstGood][3];
			myFit.params[i][4] = myModes.maskedRadialModes[i][myModes.radius.length - 1] - 1;
			myFit.params[i][5] = myFit.params[firstGood][5];
			R2[i] = mBDH.goodnessCriterion + 0.01;
		}
		for (int i = lastGood + 1 ; i < R2.length ; i++) {
			myFit.params[i][0] = myModes.maskedRadialModes[i][myModes.radius.length - 1];
			myFit.params[i][1] = myFit.params[lastGood][1];
			myFit.params[i][2] = myFit.params[lastGood][2];
			myFit.params[i][3] = myFit.params[lastGood][3];
			myFit.params[i][4] = myModes.maskedRadialModes[i][myModes.radius.length - 1] - 1;
			myFit.params[i][5] = myFit.params[lastGood][5];
			R2[i] = mBDH.goodnessCriterion + 0.01;
		}
		
		//pick out the good ones and imputed ones and make sure that a value for the first and last cross-section is set
		boolean imputedFirst = false;
		boolean imputedLast = false;
		for (int i = 0 ; i < R2.length ; i++) if (R2[i] > mBDH.goodnessCriterion) {			
			goodOnes.add(i);			
		}
		else if (i == 0 | i == R2.length - 1) {
			goodOnes.add(i);
			if (i == 0) imputedFirst = true;
			if (i == R2.length - 1) imputedLast = true;
		}
		
		//re-mould them in vectors and matrices
		int[] depth = new int[goodOnes.size()];
		if (imputedFirst) {
			for (int j = 0 ; j < myFit.numberOfParams ; j++) myFit.params[0][j] = myFit.params[goodOnes.get(1)][j];
		}
		if (imputedLast) {
			for (int j = 0 ; j < myFit.numberOfParams ; j++) myFit.params[goodOnes.size() - 1][j] = myFit.params[goodOnes.get(goodOnes.size() - 2)][j];
		}		
		
		double[][] data = new double[goodOnes.size()][myFit.numberOfParams];		
		for (int i = 0 ; i < depth.length ; i++) {
			depth[i] = goodOnes.get(i);
			for (int j = 0 ; j < myFit.numberOfParams ; j++) data[i][j] = myFit.params[depth[i]][j];
		}
		
		//init realRadius
		for (int i = 0 ; i < realRadius.length ; i++) realRadius[i] = i;
		
		//interpolate the gaps for all parameters
		for (int i = 0 ; i < myFit.numberOfParams ; i++) {			
			double[] nowData = new double[depth.length];
			for (int j = 0 ; j < nowData.length ; j++) nowData[j] = data[j][i];
			LinearInterpolator myLI = new LinearInterpolator();		
			PolynomialSplineFunction mySF = myLI.interpolate(cast.castInt2Double(depth), nowData);			 
			for (int j = 0 ; j < R2.length ; j++) gapFilledParameters[j][i] = mySF.value(j);						
		}
		
		return gapFilledParameters;
		
	}
	
	public ImageProcessor assembleCorrectionMapForAirPhaseGamma(FitStuff.FittingResults myMatrixBrightnessFit, FitStuff.FittingResults myHFFit, ObjectDetector.RadialModes myModes, MenuWaiter.BeamDeHardeningReturn mBDH) {
		
		FitStuff fittings = new FitStuff();
		RollerCaster cast = new RollerCaster();
		TailoredMaths maths = new TailoredMaths();
				
		double[] R2 = myHFFit.R2;
		double[] matrixR2 = myMatrixBrightnessFit.R2;
		int standardRadius = (int)Math.round(StatUtils.max(myModes.radius));
		double[] realRadius = new double[standardRadius];
		ArrayList<Integer> testOnes = new ArrayList<Integer>();
		ArrayList<Integer> goodOnes = new ArrayList<Integer>();		
		
		double[][] gapFilledParameters = new double[R2.length][myHFFit.numberOfParams];
		
		//sample the maximal beam-hardening artifact in the soil ... using the soil matrix fits here..
		for (int i = matrixR2.length/3 ; i < 2 * matrixR2.length / 3 ; i++) if (matrixR2[i] > mBDH.severeGoodnessCriterion) testOnes.add(i);		
		double[] A = new double[testOnes.size()];			//minimum of radial brightness profile
		double[] K = new double[testOnes.size()];			//maximum of radial brightness profile
		for (int i = 0 ; i < testOnes.size() ; i++) {
			A[i] = myMatrixBrightnessFit.params[testOnes.get(i)][4];
			K[i] = myMatrixBrightnessFit.params[testOnes.get(i)][0];
		}
		double expectedBrightnessDifference = StatUtils.percentile(K, 50) - StatUtils.percentile(A, 50);
		
		//set all R2 of fits with more than the 2 expected brightness difference to 0;
		for (int i = 0 ; i < R2.length ; i++) {
			if (myMatrixBrightnessFit.params[i][0] - myMatrixBrightnessFit.params[i][4] > expectedBrightnessDifference 
					| myMatrixBrightnessFit.params[i][0] - myMatrixBrightnessFit.params[i][4] < 0) {		
				R2[i] = 0;
			}
		}
				
		//perform a similar check for the air-phase gamma correction function
		FitStuff.HyperbolicFunction2Params myHF = fittings.new HyperbolicFunction2Params();		
		for (int i = 0 ; i < R2.length ; i++) {
			myHF.setup(StatUtils.max(myModes.radius), myModes.maskedRadialMinima[i][myModes.radius.length - 1]);
			double refMatrixBrightness = myModes.maskedRadialModes[i][myModes.radius.length - 1]; 
			double refAirPhaseBrightness = myModes.maskedRadialMinima[i][myModes.radius.length - 1];
			double[] nowParams = new double[2];			
			for (int j = 0 ; j < 2 ; j++) nowParams[j] = myHFFit.params[i][j];
			double fittedAirPhaseBrightness = myHF.value(0, nowParams);
			if ( (refMatrixBrightness - refAirPhaseBrightness) / (refMatrixBrightness - fittedAirPhaseBrightness) > 3) R2[i] = 0;			
		}		
		
		//find first and last good fit .. using the matrix fits here..
		int firstGood = 0;
		int lastGood = 0;
		for (int i = 0 ; i < matrixR2.length ; i++) {
			if (firstGood == 0 & matrixR2[i] > mBDH.severeGoodnessCriterion) firstGood = i;
			if (matrixR2[i] > mBDH.severeGoodnessCriterion) lastGood = i;
		}
		
		//plug in close-to-wall values before firstGood and after lastGood
		for (int i = 0 ; i < firstGood ; i++) {			
			myHFFit.params[i][1] = 0;
			R2[i] = mBDH.gammaGoodnessCriterion + 0.01;
		}
		for (int i = lastGood + 1 ; i < matrixR2.length ; i++) {			
			myHFFit.params[i][1] = 0;
			R2[i] = mBDH.gammaGoodnessCriterion + 0.01;
		}
	
		//pick out the good ones and imputed ones and make sure that a value for the first and last cross-section is set
		boolean imputedFirst = false;
		boolean imputedLast = false;
		for (int i = 0 ; i < R2.length ; i++) if (R2[i] > mBDH.gammaGoodnessCriterion) {			
			goodOnes.add(i);			
		}
		else if (i == 0 | i == R2.length - 1) {
			goodOnes.add(i);
			if (i == 0) imputedFirst = true;
			if (i == R2.length - 1) imputedLast = true;
		}
		
		//re-mould them in vectors and matrices
		int[] depth = new int[goodOnes.size()];
		if (imputedFirst) {
			for (int j = 0 ; j < myHFFit.numberOfParams ; j++) myHFFit.params[0][j] = myHFFit.params[goodOnes.get(1)][j];
		}
		if (imputedLast) {
			for (int j = 0 ; j < myHFFit.numberOfParams ; j++) myHFFit.params[goodOnes.size() - 1][j] = myHFFit.params[goodOnes.get(goodOnes.size() - 2)][j];
		}		
		
		//re-mould them in vectors and matrices		
		double[][] data = new double[goodOnes.size()][myHFFit.numberOfParams];		
		for (int i = 0 ; i < depth.length ; i++) {
			depth[i] = goodOnes.get(i);
			for (int j = 0 ; j < myHFFit.numberOfParams ; j++) data[i][j] = myHFFit.params[depth[i]][j];
		}
				
				
		//create a correction function map with gaps
		double[] radius = new double[(int)maths.max(myModes.radius)];
		for (int i = 0 ; i < radius.length ; i++) radius[i] = i;	
		FloatProcessor corrIP = new FloatProcessor(radius.length, myModes.maskingThreshold.length);
		for (int y = 0 ; y < myModes.maskingThreshold.length ; y++) {
			if (goodOnes.contains(y)) {
				double[] nowHFparams = new double[2];				
				myHF.setup(StatUtils.max(myModes.radius), myModes.maskedRadialMinima[y][myModes.radius.length - 1]);
				for (int i = 0 ; i < 2 ; i++) nowHFparams[i] = myHFFit.params[y][i];
				for (int x = 0 ; x < radius.length ; x++) {	
					double myValue2Put = myModes.maskedRadialModes[y][myModes.radius.length - 1];
					try {myValue2Put = myHF.value(x, nowHFparams);} 
					catch (NotStrictlyPositiveException e) {} 
					corrIP.putPixelValue(x, y, myValue2Put);
				}
			}
			else {
				for (int x = 0 ; x < radius.length ; x++) {					
					corrIP.putPixelValue(x, y, 0);
				}
			}
		}
		
//		ImagePlus letsSee = new ImagePlus("nonFiltered", corrIP);
//		letsSee.updateAndDraw();letsSee.show();
		
		//fill in the gaps in the map
		for (int y = 0 ; y < goodOnes.size() - 1 ; y++) {
			
			int lay0 = goodOnes.get(y);
			int lay1 = goodOnes.get(y+1);
			double dist = lay1 - lay0;
			
			if (dist > 1) {
				
				//save neighboring pixel rows
				double[] pa0 = new double[corrIP.getWidth()];
				double[] pa1 = new double[corrIP.getWidth()];
				double[] valueDist = new double[corrIP.getWidth()];
				for (int r = 0 ; r < corrIP.getWidth() ; r++) {
					pa0[r] = corrIP.getPixelValue(r, lay0);
					pa1[r] = corrIP.getPixelValue(r, lay1);
					valueDist[r] = pa1[r] - pa0[r];
				}
				
				//interpolate missing values from neighboring pixel rows by linear interpolation... 
				for (int lay = lay0 + 1 ; lay < lay1 ; lay++) {
					double lilDist = lay - lay0;
					for (int r = 0 ; r < corrIP.getWidth() ; r++) {
						double interpolatedValue = pa0[r] + lilDist / dist * valueDist[r];
						corrIP.putPixelValue(r,lay,interpolatedValue);
					}
				}
			}
		}
				
		//blur the final correction map..
		GaussianBlur myGB = new GaussianBlur();
		double accuracy = 0.01d;
		FloatProcessor blurIP = (FloatProcessor)corrIP.duplicate();
		myGB.blurFloat(blurIP, 20, 20, accuracy);			
		//ImagePlus letsSeeM = new ImagePlus("Filtered",blurIP);
		//letsSeeM.updateAndDraw();letsSeeM.show();
		
		return blurIP;
		
	}
	
	public ImagePlus clipImage(int i, ImagePlus nowTiff, InputOutput.MyFolderCollection mFC, MenuWaiter.ClipperMenuReturn mSCM) {
		
		InputOutput jIO = new InputOutput(); 
		ObjectDetector jOD = new ObjectDetector(); 
		RoiHandler roi = new RoiHandler();
		
		ImagePlus outTiff = new ImagePlus();		
		
		String nowGaugePath = null;
		String nowSurfPath = null;
		int[] myGandS = new int[2];
		ObjectDetector.ColCoords3D jCO = jOD.new ColCoords3D();		
		PolygonRoi[] pRoi = new PolygonRoi[nowTiff.getNSlices()];		
		int xmin = nowTiff.getWidth();
		int xmax = 0;
		int ymin = nowTiff.getHeight();
		int ymax = 0;
		
		int referenceSlice = 1;		
		
		//load gauge and surface files
		if (mSCM.isSoilColumn == true) {			
			if (mSCM.referenceIsTopMostSlice) myGandS = jIO.getTheCorrectGaugeNSurfaceFiles(mFC.myTiffs[i], mFC.myInnerCircleFiles, null);			
			if (mSCM.referenceIsSoilSurface) {
				myGandS = jIO.getTheCorrectGaugeNSurfaceFiles(mFC.myTiffs[i], mFC.myInnerCircleFiles, mFC.mySurfaceFileNames);
				nowSurfPath = mFC.mySurfaceFolder + "//" + mFC.mySurfaceFileNames[myGandS[1]];
			}
			nowGaugePath = mFC.myInnerCircleFiles[myGandS[0]];
			
			//read InnerCircle file
			int versio = jIO.checkInnerCircleFileVersion(nowGaugePath);			
			if (versio == 0) jCO = jIO.readInnerCircleVer0(nowGaugePath);	
			else jCO = jIO.readInnerCircleVer1(nowGaugePath);
			
			pRoi = roi.makeMeAPolygonRoiStack("inner", "manual", jCO, mSCM.clipFromInnerPerimeter);
					
			if (mSCM.referenceIsSoilSurface == true) {
				ImagePlus mySurfs = jIO.openTiff3D(nowSurfPath);	
				referenceSlice = jOD.findMedianSoilSurfacePosition(mySurfs);	
			}
			
			//find min and max values for clip
			for (int j = referenceSlice + mSCM.startAtSlice ; j < referenceSlice + mSCM.stopAtSlice ; j++) {
				
				Polygon p = pRoi[j-2].getNonSplineCoordinates();
				
				// for x			
				int[] nowX = new int[pRoi[j].getNCoordinates()]; 				
				nowX = p.xpoints;
				Arrays.sort(nowX, 0, nowX.length); 
				int xmi = nowX[0] + (int)Math.round(pRoi[j].getXBase());
				int xma = nowX[nowX.length - 1] + (int)Math.round(pRoi[j].getXBase());
				
				// and for y
				int[] nowY = new int[pRoi[j].getNCoordinates()]; 				
				nowY = p.ypoints;
				Arrays.sort(nowY, 0, nowY.length); 
				int ymi = nowY[0] + (int)Math.round(pRoi[j].getYBase());
				int yma = nowY[nowY.length - 1] + (int)Math.round(pRoi[j].getYBase());
				
				// remember the mins and maxes
				if (xmi < xmin) xmin = xmi - mSCM.canvasExceedsBy;
				if (xma > xmax) xmax = xma + mSCM.canvasExceedsBy;
				if (ymi < ymin) ymin = ymi - mSCM.canvasExceedsBy;
				if (yma > ymax) ymax = yma + mSCM.canvasExceedsBy;				
			}			
		} 
		else {			// if the sample is not a soil column..			
			xmin = 0 + mSCM.clipFromCanvasEdge - mSCM.canvasExceedsBy;
			xmax = nowTiff.getWidth() - mSCM.clipFromCanvasEdge + mSCM.canvasExceedsBy;
			ymin = 0 + mSCM.clipFromCanvasEdge - mSCM.canvasExceedsBy;
			ymax = nowTiff.getHeight() - mSCM.clipFromCanvasEdge + mSCM.canvasExceedsBy;			
		}			
		
		//create clip-out-roi
		float[] xpf = {xmin, xmax, xmax, xmin, xmin};
		float[] ypf = {ymin, ymin, ymax, ymax, ymin};	
		PolygonRoi coRoi = new PolygonRoi(xpf, ypf, Roi.POLYGON);
		
		//create simple Roi in case that the image does not contain a soil column
		float[] xps = {xmin + mSCM.canvasExceedsBy, xmax - mSCM.canvasExceedsBy, xmax - mSCM.canvasExceedsBy, xmin + mSCM.canvasExceedsBy, xmin + mSCM.canvasExceedsBy};
		float[] yps = {ymin + mSCM.canvasExceedsBy, ymin + mSCM.canvasExceedsBy, ymax - mSCM.canvasExceedsBy, ymax - mSCM.canvasExceedsBy, ymin + mSCM.canvasExceedsBy};
		PolygonRoi simpleRoi = new PolygonRoi(xps, yps, Roi.POLYGON);
		
		//clip and cut
		ImageStack outStack = null;
		if (!mSCM.preserveOriginialCanvasSize) {
		
			outStack = new ImageStack(xmax - xmin, ymax - ymin);
		
			for (int j = referenceSlice + mSCM.startAtSlice ; j < referenceSlice + mSCM.stopAtSlice ; j++) {
			
				nowTiff.setPosition(j);
				ImageProcessor nowIP = nowTiff.getProcessor();
			
				if (mSCM.isSoilColumn == true) simpleRoi = pRoi[j];
			
				nowIP.setRoi(simpleRoi);
				nowIP.setBackgroundValue(0);
				nowIP.fillOutside(simpleRoi);
			
				nowIP.setRoi(coRoi);
				ImageProcessor cropIP = nowIP.crop();
			
				outStack.addSlice(cropIP);
			}			
		} 
		else {
			
			outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
			
			for (int j = referenceSlice + mSCM.startAtSlice ; j < referenceSlice + mSCM.stopAtSlice ; j++) {
			
				nowTiff.setPosition(j);
				ImageProcessor nowIP = nowTiff.getProcessor();
			
				if (mSCM.isSoilColumn == true) simpleRoi = pRoi[j];
			
				nowIP.setRoi(simpleRoi);
				nowIP.setBackgroundValue(0);
				nowIP.fillOutside(simpleRoi);
			
				outStack.addSlice(nowIP);
			}
		}			 
		
		//add blank canvas to bottom if it is wished..
		if (mSCM.addCanvasExccedance2Bottom == true) {			
			ImageProcessor blankIP = null;
			if (nowTiff.getBitDepth() == 8) blankIP = new ByteProcessor(outStack.getWidth(), outStack.getHeight());
			if (nowTiff.getBitDepth() == 16) blankIP = new ShortProcessor(outStack.getWidth(), outStack.getHeight());
			for (int j = 0 ; j < mSCM.canvasExceedsBy ; j++) {
				outStack.addSlice(blankIP);	
			}
		}
		
		outTiff.setStack(outStack);
		
		//outTiff.updateAndDraw();
		//outTiff.show();
		
		return outTiff;
		
	}
	
	/*public ImagePlus correct4SteelBeamHardening(ImagePlus nowTiff, ObjectDetector.ColumnCoordinates jCO, double[][][] bhc, int standardRadius) {
		
		//init output variables
		ImagePlus outTiff = new ImagePlus();
		ImageStack outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		
		//calculate number of angles needed to paint the whole canvas..
		double spacing = 2 * Math.PI / jCO.anglesChecked;		
		float[] sR = new float[standardRadius]; 
		float[] sA = new float[jCO.anglesChecked + 1];
		
		//create array for standardradius
		for (int i = 0 ; i < standardRadius ; i++) sR[i] = i + 1;
		for (int i = 0 ; i < jCO.anglesChecked + 1 ; i++) sA[i] = i;		 

		//sweep over slices
		for (int i = 0 ; i < nowTiff.getNSlices() ; i++) {
			
			IJ.showStatus("Correcting for beam hardening in slice #" + (i + 1) + "/" + nowTiff.getNSlices());
			
			//set image to next slice
			nowTiff.setPosition(i+1);
			ImageProcessor nowIP = nowTiff.getProcessor();
			
			//calculate normalized correction functions
			double r01 = bhc[i][3][0];
			float[] ftot01 = correctionFunctionCalculator(i, bhc, 0, sR);
			
			//double r40 = bhc[i][3][1];
			//float[] ftot40 = correctionFunctionCalculator(i, bhc, 1, sR);
			
			//double r60 = bhc[i][3][2];
			//float[] ftot60 = correctionFunctionCalculator(i, bhc, 2, sR);
			
			double r80 = bhc[i][3][3];
			float[] ftot80 = correctionFunctionCalculator(i, bhc, 3, sR);
			
			//do the correction
			for (int x = 0 ; x < nowIP.getWidth() ; x++) {
				for (int y = 0 ; y < nowIP.getHeight() ; y++) {			
					
					//get distance of pixel to center
					float dx = x - (float)jCO.xmid[i];
					float dy = y - (float)jCO.ymid[i];
					float nowRadius = (float)Math.sqrt((double)(dx*dx) + (double)(dy*dy));
					
					//get angle
					double alpha = Math.atan(dy/dx);
					if (dx < 0 & dy >= 0) alpha = 2 * Math.PI + alpha;
					if (dy < 0) alpha = Math.PI + alpha;
															
					//get radius at this angle
					double dx0 = jCO.xID[i][0] - jCO.xmid[i];
					double dy0 = jCO.yID[i][0] - jCO.ymid[i];
					double radiusAtThisAngle = Math.sqrt((dx0*dx0) + (dy0*dy0));;
					for (double j = 0 ; j < jCO.anglesChecked ; j++) {
						double nowAngle  = 2 * j / jCO.anglesChecked * Math.PI;
						if (nowAngle > alpha) {
							
							double weight = (nowAngle - alpha) / spacing;							
							
							double dx1 = jCO.xID[i][(int)j-1] - jCO.xmid[i];
							double dx2 = jCO.xID[i][(int)j] - jCO.xmid[i];
							double dy1 = jCO.yID[i][(int)j-1] - jCO.ymid[i];
							double dy2 = jCO.yID[i][(int)j] - jCO.ymid[i];
							double r1 = Math.sqrt((dx1*dx1) + (dy1*dy1));
							double r2 = Math.sqrt((dx2*dx2) + (dy2*dy2));
							
							radiusAtThisAngle = weight * r2 + (1 - weight) * r1;
							
							break;
						}
					}
					
					//check if pixel is within column and if yes apply correction
					if (nowRadius < radiusAtThisAngle) {
						
						int renormalizedRadius = (int)Math.floor(nowRadius / radiusAtThisAngle * standardRadius);
						int C01 = (int)Math.round(ftot01[renormalizedRadius]);
						//int C40 = (int)Math.round(ftot40[renormalizedRadius]);
						//int C60 = (int)Math.round(ftot60[renormalizedRadius]);
						int C80 = (int)Math.round(ftot80[renormalizedRadius]);
						
						float mygray = nowIP.getPixelValue(x, y);
						//int newgray = (int)Math.round(r60 + mygray - C60);
						//int newgray = (int)Math.round(r60 + ((r80-r60)/(C80-C60)) * (mygray - C60));
						int newgray = (int)Math.round(r01 + ((r80-r01)/(C80-C01)) * (mygray - C01));
						
						nowIP.putPixel(x, y, newgray);
						
					}			
				}	
		
			}
			
			//nowTiff.updateAndDraw();
			//nowTiff.show();

			outStack.addSlice(nowIP);
		}
		
		outTiff.setStack(outStack);
		
		//outTiff.updateAndDraw();
		//outTiff.show();
		
		return outTiff;		
		
	}*/
	
	public ImagePlus correct4PVCBeamHardening(ImagePlus nowTiff, ImageProcessor blurIP, ImageProcessor gammaIP, ObjectDetector.ColCoords3D jCO, MenuWaiter.BeamDeHardeningReturn mBDH) {
		
		RoiHandler roi = new RoiHandler();
		
		//init output variables
		ImagePlus outTiff = new ImagePlus();
		ImageStack outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		
		//calculate number of angles needed to paint the whole canvas..
		double spacing = 2 * Math.PI / mBDH.anglesChecked;
		int standardRadius = blurIP.getWidth();
		float[] sR = new float[standardRadius]; 
		float[] sA = new float[mBDH.anglesChecked + 1];
		
		//create array for standardradius
		for (int i = 0 ; i < standardRadius ; i++) sR[i] = i + 1;
		for (int i = 0 ; i < mBDH.anglesChecked + 1 ; i++) sA[i] = i;	
		
		//load polygon roi of inner perimeter
		PolygonRoi[] nowRoi = roi.makeMeAPolygonRoiStack("inner", "manual", jCO, 2);

		//sweep over slices
		for (int i = 0 ; i < nowTiff.getNSlices() ; i++) {
			
			IJ.showStatus("Correcting for beam hardening in slice #" + (i + 1) + "/" + nowTiff.getNSlices());
			
			//extract X and Y of pRoi		
			nowRoi[i].fitSpline(mBDH.anglesChecked);
			Polygon myPoly = nowRoi[i].getPolygon();			
			int xID[] = myPoly.xpoints;
			int yID[] = myPoly.ypoints;
			
			//set image to next slice
			nowTiff.setPosition(i+1);
			ImageProcessor nowIP = nowTiff.getProcessor();
			
			//get matrix brightness reference values for this depth			
			double[] corrFunc = new double[standardRadius];
			double nowReference = blurIP.getPixelValue(standardRadius - 1, i);
			for (int r = 0 ; r < standardRadius ; r++) corrFunc[r] = nowReference / blurIP.getPixelValue(r, i);
			
			//get air-phase gamma reference values for this depth			
			double[] gammaFunc = new double[standardRadius];
			double nowRefGamma = gammaIP.getPixelValue(standardRadius - 1, i);
			double gammaDelta = nowReference - nowRefGamma;
			for (int r = 0 ; r < standardRadius ; r++) gammaFunc[r] = gammaDelta / (nowReference - gammaIP.getPixelValue(r, i));
					
			//do the correction
			for (int x = 0 ; x < nowIP.getWidth() ; x++) {
				for (int y = 0 ; y < nowIP.getHeight() ; y++) {			
					
					//get distance of pixel to center
					float dx = x - (float)jCO.xmid[i];
					float dy = y - (float)jCO.ymid[i];
					float nowRadius = (float)Math.sqrt((double)(dx*dx) + (double)(dy*dy));
					
					//get angle
					double alpha = Math.atan(dy/dx);
					if (dx < 0 & dy >= 0) alpha = 2 * Math.PI + alpha;
					if (dy < 0) alpha = Math.PI + alpha;
															
					//get radius at this angle
					double dx0 = xID[0] - jCO.xmid[i];
					double dy0 = yID[0] - jCO.ymid[i];
					double radiusAtThisAngle = Math.sqrt((dx0*dx0) + (dy0*dy0));;
					for (double j = 0 ; j < mBDH.anglesChecked ; j++) {
						double nowAngle  = 2 * j / mBDH.anglesChecked * Math.PI;
						if (nowAngle > alpha) {
							
							double weight = (nowAngle - alpha) / spacing;							
							
							double dx1 = xID[(int)j-1] - jCO.xmid[i];
							double dx2 = xID[(int)j] - jCO.xmid[i];
							double dy1 = yID[(int)j-1] - jCO.ymid[i];
							double dy2 = yID[(int)j] - jCO.ymid[i];
							double r1 = Math.sqrt((dx1*dx1) + (dy1*dy1));
							double r2 = Math.sqrt((dx2*dx2) + (dy2*dy2));
							
							radiusAtThisAngle = weight * r2 + (1 - weight) * r1;
							
							break;
						}
					}
					
					//check if pixel is within column and if yes apply correction
					double corrFactor = 1;
					double gammaFactor = 1;
					if (nowRadius < radiusAtThisAngle) {						
						int renormalizedRadius = (int)Math.floor(nowRadius / radiusAtThisAngle * standardRadius);
						corrFactor = corrFunc[renormalizedRadius];
						gammaFactor = gammaFunc[renormalizedRadius];
					}		
					
					//apply beam hardening correction
					float mygray = nowIP.getPixelValue(x, y);					
					double newgray = (int)Math.round(corrFactor * mygray);
					
					//also apply air-phase gamma correction
					int newestgray = (int)Math.round(nowReference - (nowReference - newgray) * gammaFactor); 
					
					nowIP.putPixel(x, y, newestgray);
				}	
		
			}
	
			outStack.addSlice(nowIP);
		}
		
		outTiff.setStack(outStack);
		
		return outTiff;		
		
	}
	
	public float[] correctionFunctionCalculator(int i, double[][][] bhc, int bhcEntry, float[] sR) {
		
		float[] ftot = new float[sR.length];
		int standardRadius = sR.length;
		
		double a = bhc[i][0][bhcEntry];
		double b = bhc[i][1][bhcEntry];
		double c = bhc[i][2][bhcEntry];
		double r = bhc[i][3][bhcEntry];
		double dy = bhc[i][4][bhcEntry];		
		double f1, f2, f3;
		
		for (int j = 0 ; j < standardRadius ; j++) {
			f1 = dy / Math.exp(a * sR[j] - a) + r;
			f2 = dy / Math.exp(b * sR[j] - b) + r;
			f3 = dy / Math.exp(c * sR[j] - c) + r;
			ftot[standardRadius - j - 1] = (float)((f1 + f2 + f3) / 3);  //switch vector around!!!! important!!
		}
		
		return ftot;
	}
	
	/*public ImagePlus putSteelColumnUprightInCenter(ImagePlus nowTiff, ObjectDetector.ColumnCoordinates prelimCC) {
		
		ObjectDetector jOD = new ObjectDetector();		
		Slicer sly = new Slicer();
		Median jMed = new Median();
		
		ObjectDetector.ColumnCoordinates newCC = jOD.new ColumnCoordinates();
		
		ImagePlus straightTiff = new ImagePlus();
		ImagePlus outTiff = new ImagePlus();
		
		//by-pass the cutting step when re-measuring column wall..
		//outTiff = nowTiff;
		
		if (prelimCC.tiltTotal > 0.01) {   //if tilting angle is too big then put column straight
			
			IJ.showStatus("Putting column straight and moving it to center of canvas, step 1 of 4...");
			
			ImagePlus boTiff0 = new ImagePlus();
			ImageStack boStack0 = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		
			//find tilting angle relative to XY-plane
			double alpha = prelimCC.tiltInXZ;
			double beta = prelimCC.tiltInYZ;
			double dz = 1000;
			
			double dx = Math.tan(alpha) * dz;
			double dy = Math.tan(beta) * dz;
			double gamma = Math.atan(dx/dy);
			
			//correct for ambiguity in atan response			
			if (dy < 0) gamma = gamma + Math.PI; 
			
			//rotate so that the tilting is only in y-direction
			for (int i = 1 ; i < nowTiff.getNSlices() + 1 ; i++) {
				
				nowTiff.setPosition(i);
				ImageProcessor nowIP = nowTiff.getProcessor();
				nowIP.setInterpolate(true);
				double gammaInDegree = gamma * 360 / 2 / Math.PI ;
				nowIP.rotate(gammaInDegree + 90);
				
				boStack0.addSlice(nowIP);
			}
			
			boTiff0.setStack(boStack0);			
						
			//reslice
			ImagePlus vertiTiff = sly.reslice(boTiff0);
			
			IJ.showStatus("Putting column straight and moving it to center of canvas, step 2 of 4...");
			
			//put column upright
			ImagePlus boTiff = new ImagePlus();
			ImageStack boStack = new ImageStack(vertiTiff.getWidth(), vertiTiff.getHeight());			
			for (int i = 1 ; i < vertiTiff.getNSlices() + 1 ; i++) {
				
				vertiTiff.setPosition(i);
				ImageProcessor nowIP = vertiTiff.getProcessor();
				nowIP.setInterpolate(true);
				double deltaInDegree = prelimCC.tiltTotal * 360 / 2 / Math.PI ;
				nowIP.rotate(-deltaInDegree);
				
				boStack.addSlice(nowIP);
			}
		
			boTiff.setStack(boStack);	
			
			//reslice back to XY-plane view
			ImagePlus straightTiff0 = sly.reslice(boTiff);
			
			IJ.showStatus("Putting column straight and moving it to center of canvas, step 3 of 4...");
			
			//rotate back to original orientation
			ImageStack straightStack = new ImageStack(straightTiff0.getWidth(), straightTiff0.getHeight());
			for (int i = 1 ; i < straightTiff0.getNSlices() + 1 ; i++) {
				
				straightTiff0.setPosition(i);
				ImageProcessor nowIP = straightTiff0.getProcessor();
				nowIP.setInterpolate(true);
				double gammaInDegree = gamma * 360 / 2 / Math.PI ;
				nowIP.rotate(-gammaInDegree - 90);
				
				straightStack.addSlice(nowIP);
			}
			
			straightTiff.setStack(straightStack);

		}
		else {
			straightTiff = nowTiff;
		}
		
		//straightTiff.show();
		//straightTiff.draw();
		
		//re-find column outlines
		newCC = jOD.findOrientationSteelColumn(straightTiff);
		
		//move column into the center of the canvas and cut out unnecessary parts of the canvas
		IJ.showStatus("Putting column straight and moving it to center of canvas, step 4 of 4...");
		double rim = 25;
		double mRadius = jMed.evaluate(newCC.medianOuterRadius);
		double toBeLeft = mRadius + rim;
		
		//check if cut-out Roi is not larger than the image..
		if (2 * toBeLeft > straightTiff.getWidth()) toBeLeft = straightTiff.getWidth() / 2;
		if (2 * toBeLeft > straightTiff.getHeight()) toBeLeft = straightTiff.getHeight() / 2;
			
		//do the cutting
		double diameter = 2 * toBeLeft;
		ImageStack outStack = new ImageStack((int)Math.round(diameter), (int)Math.round(diameter));
		//Boolean cutSuccessful = true;
		for (int i = 1 ; i < straightTiff.getNSlices() + 1 ; i++) {
			
			IJ.showStatus("Adding straightened slice " + i + "/" + straightTiff.getNSlices());
			
			straightTiff.setPosition(i);
			ImageProcessor nowIP = straightTiff.getProcessor();
			nowIP.setInterpolate(true);
			
			double XC = straightTiff.getWidth() / 2;
			double YC = straightTiff.getHeight() / 2;
			
			double dx = XC - jMed.evaluate(newCC.xmid);
			double dy = YC - jMed.evaluate(newCC.ymid);
			
			nowIP.translate(dx, dy);
			
			Roi cutRoi = new Roi(XC - toBeLeft, YC - toBeLeft, (int)Math.round(diameter), (int)Math.round(diameter));			
			nowIP.setRoi(cutRoi);
			ImageProcessor cutIP = nowIP.crop();
			
			outStack.addSlice(cutIP);
		}
		
		outTiff.setStack(outStack);
		
		
		//outTiff.draw();
		//outTiff.show();		
		
		return outTiff;
		
	}*/
	
	public ImagePlus putColumnUprightInCenter(ImagePlus nowTiff, ObjectDetector.ColCoords3D prelimCC, MenuWaiter.ColumnFinderMenuReturn jCFS) {
		
		ObjectDetector jOD = new ObjectDetector();		
		Slicer sly = new Slicer();
		Median jMed = new Median();
		
		ObjectDetector.ColCoords3D newCC = jOD.new ColCoords3D();
		
		ImagePlus straightTiff = new ImagePlus();
		ImagePlus outTiff = new ImagePlus();
		
		//by-pass the cutting step when re-measuring column wall..
		//outTiff = nowTiff;
		
		if (prelimCC.tiltTotal > 0.01) {   //if tilting angle is too big then put column straight
			
			IJ.showStatus("Putting column straight and moving it to center of canvas, step 1 of 4...");
			
			ImagePlus boTiff0 = new ImagePlus();
			ImageStack boStack0 = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		
			//find tilting angle relative to XY-plane
			double alpha = prelimCC.tiltInXZ;
			double beta = prelimCC.tiltInYZ;
			double dz = 1000;
			
			double dx = Math.tan(alpha) * dz;
			double dy = Math.tan(beta) * dz;
			double gamma = Math.atan(dx/dy);
			
			//correct for ambiguity in atan response			
			if (dy < 0) gamma = gamma + Math.PI; 
			
			//rotate so that the tilting is only in y-direction
			for (int i = 1 ; i < nowTiff.getNSlices() + 1 ; i++) {
				
				nowTiff.setPosition(i);
				ImageProcessor nowIP = nowTiff.getProcessor();
				nowIP.setInterpolate(true);
				double gammaInDegree = gamma * 360 / 2 / Math.PI ;
				nowIP.rotate(gammaInDegree + 90);
				
				boStack0.addSlice(nowIP);
			}
			
			boTiff0.setStack(boStack0);			
						
			//reslice
			ImagePlus vertiTiff = sly.reslice(boTiff0);
						
			//free memory ..
			boTiff0.flush();
			IJ.freeMemory();IJ.freeMemory();
			
			IJ.showStatus("Putting column straight and moving it to center of canvas, step 2 of 4...");
			
			//put column upright
			ImagePlus boTiff = new ImagePlus();
			ImageStack boStack = new ImageStack(vertiTiff.getWidth(), vertiTiff.getHeight());			
			for (int i = 1 ; i < vertiTiff.getNSlices() + 1 ; i++) {
				
				vertiTiff.setPosition(i);
				ImageProcessor nowIP = vertiTiff.getProcessor();
				nowIP.setInterpolate(true);
				double deltaInDegree = prelimCC.tiltTotal * 360 / 2 / Math.PI ;
				nowIP.rotate(-deltaInDegree);
				
				boStack.addSlice(nowIP);
			}

			//free memory ..
			vertiTiff.flush();
			IJ.freeMemory();IJ.freeMemory();
			
			//transfer images to boTiff
			boTiff.setStack(boStack);	
			
			//reslice back to XY-plane view
			ImagePlus straightTiff0 = sly.reslice(boTiff);
			
			//free memory from boTiff again..
			boTiff.flush();
			IJ.freeMemory();IJ.freeMemory();
			
			IJ.showStatus("Putting column straight and moving it to center of canvas, step 3 of 4...");
			
			//rotate back to original orientation
			ImageStack straightStack = new ImageStack(straightTiff0.getWidth(), straightTiff0.getHeight());
			for (int i = 1 ; i < straightTiff0.getNSlices() + 1 ; i++) {
				
				straightTiff0.setPosition(i);
				ImageProcessor nowIP = straightTiff0.getProcessor();
				nowIP.setInterpolate(true);
				double gammaInDegree = gamma * 360 / 2 / Math.PI ;
				nowIP.rotate(-gammaInDegree - 90);
				
				straightStack.addSlice(nowIP);
			}
			
			//free memory from straightTiff0 again..
			straightTiff0.flush();
			IJ.freeMemory();IJ.freeMemory();
			
			straightTiff.setStack(straightStack);

		}
		else {
			straightTiff = nowTiff;
		}
				
		//straightTiff.draw();
		//straightTiff.show();
		
		//re-find column outlines
		boolean look4PreciseCoords = false;
		newCC = jOD.findOrientationOfPVCOrAluColumn(straightTiff, jCFS, look4PreciseCoords);
		
		//move column into the center of the canvas and cut out unnecessary parts of the canvas
		IJ.showStatus("Putting column straight and moving it to center of canvas, step 4 of 4...");
		double rim = 25;
		double mRadius = jMed.evaluate(newCC.outerMajorRadius);
		double toBeLeft = mRadius + rim;
		
		//check if cut-out Roi is not larger than the image..
		if (2 * toBeLeft > straightTiff.getWidth()) toBeLeft = straightTiff.getWidth() / 2;
		if (2 * toBeLeft > straightTiff.getHeight()) toBeLeft = straightTiff.getHeight() / 2;
			
		//do the cutting
		double diameter = 2 * toBeLeft;
		ImageStack outStack = new ImageStack((int)Math.round(diameter), (int)Math.round(diameter));
		
		//Boolean cutSuccessful = true;
		for (int i = 1 ; i < straightTiff.getNSlices() + 1 ; i++) {
			
			IJ.showStatus("Moving column to center of canvas " + i + "/" + straightTiff.getNSlices());
			
			straightTiff.setPosition(i);
			ImageProcessor nowIP = straightTiff.getProcessor();
			nowIP.setInterpolate(true);
			
			double XC = straightTiff.getWidth() / 2;
			double YC = straightTiff.getHeight() / 2;
			
			double dx = XC - jMed.evaluate(newCC.xmid);
			double dy = YC - jMed.evaluate(newCC.ymid);
			
			nowIP.translate(dx, dy);
			
			Roi cutRoi = new Roi(XC - toBeLeft, YC - toBeLeft, (int)Math.round(diameter), (int)Math.round(diameter));			
			nowIP.setRoi(cutRoi);
			ImageProcessor cutIP = nowIP.crop();
			
			outStack.addSlice(cutIP);
		}
		
		//free memory from straightTiff again..
		straightTiff.flush();
		IJ.freeMemory();IJ.freeMemory();
		
		outTiff.setStack(outStack);		
		
		//outTiff.draw();
		//outTiff.show();		
		
		return outTiff;
		
	}
	
	/*public ImagePlus removeRingArtifacts(ImagePlus nowTiff, String innerCirclePath, MenuWaiter.RingArtifactRemoverOptions rARO) {
				
		ObjectDetector jOD = new ObjectDetector();
		InputOutput jIO = new InputOutput();
		RoiHandler roi = new RoiHandler();
		
		ImagePlus outTiff = new ImagePlus();
		PolygonRoi[] pRoi = new PolygonRoi[nowTiff.getNSlices()];
		
		//read gauge file
		if (rARO.useInnerCircle) {
			ObjectDetector.ColumnCoordinates jCO = jIO.readGaugeFile(innerCirclePath);	
			pRoi = roi.makeMeAPolygonRoiStack("inner", "tight", jCO, 0);
		}
		
		double[] axisOfEvil = jOD.detectRingArtifacts(nowTiff, rARO); //returns tow firstOrder polynomials describing the location of the centers of the artifacts: x = az + b; y = cz + d where a,b,c,d == axisOfEvil[0..3]
		
	
		double[] XisazPLUSbYisczPLUSd = {0, 0, 0, 0,};
		double xCenter = (float)nowTiff.getWidth() / 2;
		double yCenter = (float)nowTiff.getHeight() / 2;
		int angles = 12;
		double aincr = 2 * Math.PI / angles;		
				
		for (int z = 0 ; z < nowTiff.getNSlices() ; z++) {
			
			//get informed about column location
			double diam = nowTiff.getWidth();
			if (pRoi[z] != null) pRoi[z].getFloatWidth();
			else if (nowTiff.getWidth() > nowTiff.getHeight()) diam = nowTiff.getHeight();
			int checkPosis = (int)Math.floor(diam / 2);
			
			//scan layer z
			nowTiff.setPosition(z+1);
			ImageProcessor nowIP = nowTiff.getProcessor();
			
			//smooth layer z						
			myRF.rank(nowIP, 2, RankFilters.MEDIAN);
			nowIP.sharpen();		
						
			//sample radial profiles
			double[][] radProf = new double[angles][checkPosis-1];
			int angCount = 0;
			for (double alpha = 0 ; alpha < 2 * Math.PI - aincr; alpha += aincr) {
				for (double r = 1 ; r < checkPosis ; r++) {
					int x = (int) Math.round(xCenter + Math.sin(alpha) * r);
					int y = (int) Math.round(yCenter - Math.cos(alpha) * r);
					radProf[angCount][(int)Math.round(r) - 1] = nowIP.getPixel(x, y);
				}
				angCount++;
			}
			

			
		}
		
		return outTiff;
		
	}*/
	
	/*public ImagePlus doSteelIlluCorrAndCutOffEnds(ImagePlus nowTiff, ObjectDetector.ColumnCoordinates preciseCC) {
		
		int z;
				
		double targetValue = preciseCC.steelgrayValue;
		double corrFactor;		
		
		ImageStack outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		ImagePlus outTiff = new ImagePlus();
	
		for (z = preciseCC.topOfColumn ; z < preciseCC.bottomOfColumn + 1 ; z++) { 
			
			IJ.showStatus("Correcting illumination of slice " + (z - preciseCC.topOfColumn) + "/" + (preciseCC.bottomOfColumn - preciseCC.topOfColumn));
			
			//set stack position to the correct depth
			nowTiff.setPosition(z + 1);
			
			//calc corrfactor
			corrFactor = targetValue / preciseCC.wallgrayValues[z];
			
			//get and correct current slice
			ImageProcessor localIP = nowTiff.getProcessor();
			localIP.multiply(corrFactor);
			outStack.addSlice(localIP);
		}
		
		outTiff.setStack(outStack);		
		
		return outTiff;
	}*/
	
	///////////////////////////////////////////////
	// binarize
	///////////////////////////////////////////////
		
	public ImagePlus[] applyAChosenThresholdingMethod(ImagePlus nowTiff, InputOutput.MyFolderCollection mFC, MenuWaiter.ThresholderMenuReturn mTMR, int[] myZ) {
		
		InputOutput jIO = new InputOutput();	
		HistogramStuff hist = new HistogramStuff();
		RoiHandler rH = new RoiHandler();
		ObjectDetector jOD = new ObjectDetector();
		
		ImagePlus[] outImg = {null, null, null};
		ImageStack myStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
					
		int[] myHist = new int[256 * 256];
		int[] newHist;
		int myThresh = 0;

		AutoThresholder myAuto = new AutoThresholder();		
	
		PolygonRoi[] pRoi = null;		
		PolygonRoi[] oRoi = null;
		int[] onePercQuantile = new int[nowTiff.getNSlices()];
		
		//read gauge file AND find illumination of this column	
		if (mTMR.useInnerCircle) {
			
			//select the correct gauge and surface files	
			int[] myGandS = {0, 0};
			String nowGauge = null;
			if (mTMR.useInnerCircle){
				myGandS = jIO.getTheCorrectGaugeNSurfaceFiles(mFC.fileName, mFC.myInnerCircleFiles, null);
				nowGauge = mFC.myInnerCircleFiles[myGandS[0]];
			}
			
			ObjectDetector.ColCoords3D jCO = jOD.new ColCoords3D();
			int versio = jIO.checkInnerCircleFileVersion(nowGauge);			
			if (versio == 0) jCO = jIO.readInnerCircleVer0(nowGauge);	
			else jCO = jIO.readInnerCircleVer1(nowGauge);
		
			//create ROI stacks..
			pRoi = rH.makeMeAPolygonRoiStack("inner", "exact", jCO, 0);
			oRoi = rH.makeMeAPolygonRoiStack("inner", "manual", jCO, 10);	

		}
		
		//find threshold	
		if (!mTMR.useConstantThreshold) {
	
			//get the stacked histogram		
			for (int i = 1 ; i < nowTiff.getNSlices() + 1 ; i++) {
				
				IJ.showStatus("Getting histogram of slice #" + i + "/" + nowTiff.getNSlices());
				
				nowTiff.setPosition(i);		
				
				ImageProcessor myIP = nowTiff.getProcessor();
				ImageProcessor modIP = myIP.duplicate();
				
				//cut out everything outside column
				if (mTMR.useInnerCircle) {
					modIP.setRoi(pRoi[i - 1]);
					modIP.setColor(0);
					modIP.fillOutside(pRoi[i - 1]);
				}
				
				newHist=modIP.getHistogram();
				newHist[0] = 0; //set zero GV to zero
				int gotoc = 256;
				if (nowTiff.getBitDepth() == 16) gotoc = 256 * 256;
				for (int j = 0 ; j < gotoc ; j++) {
					myHist[j] = myHist[j] + newHist[j];
				}			
				
				//save 1% perc and wallOfgray
				double[] cumHist = hist.calcCumulativeHistogram(newHist);			
				onePercQuantile[i-1] = hist.findPercentileFromCumHist(cumHist, 0.001);	
			}		
			
			//prepare possible scaling nowTiff to 8-bit
			double[] cumHist = hist.calcCumulativeHistogram(myHist);
			double lBound = 0.0000001;double uBound = 0.99999;			
			int lowestVal = (int)Math.round(hist.findPercentileFromCumHist(cumHist, lBound));			//set lower gray value
			int largestVal = (int)Math.round(hist.findPercentileFromCumHist(cumHist, uBound));		
			float valSpan = (float)largestVal - lowestVal;
			if (!mTMR.setMaxgray2Wallgray | mTMR.useConstantThreshold == true) valSpan = largestVal - lowestVal;
		
			ImagePlus eightBitTiff = new ImagePlus();
			
			if (nowTiff.getBitDepth() == 8) {
				eightBitTiff = nowTiff.duplicate();
			}
			else {
			
				ImageStack eightBitStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
				
				for (int i = 1 ; i < nowTiff.getNSlices() + 1 ; i++) {
					
					IJ.showStatus("Scaling 16-bit image to 8-bit at slice #" + i + "/" + nowTiff.getNSlices());
					
					nowTiff.setPosition(i);
					ImageProcessor myIP = nowTiff.getProcessor();
					ImageProcessor modIP = myIP.duplicate();
					modIP.max(largestVal);
					modIP.subtract(lowestVal);
					modIP.multiply(256 / valSpan);
					modIP.min(0);
					modIP.max(255);
					ImageProcessor eightIP = modIP.convertToByte(false);
					
					eightBitStack.addSlice(eightIP);
				}
				
				eightBitTiff.setStack(eightBitStack);
			}
	
			//get the 8-bit stacked histogram
			int[] my8Hist = new int[256];
			int[] new8Hist;
			for (int i = 1 ; i < eightBitTiff.getNSlices() + 1 ; i++) {
				
				IJ.showStatus("Getting 8-bit histogram of slice #" + i + "/" + (eightBitTiff.getNSlices()));
				
				eightBitTiff.setPosition(i);			
				
				ImageProcessor myIP = eightBitTiff.getProcessor();
				ImageProcessor modIP = myIP.duplicate();
				
				//cut out everything outside column
				if (mTMR.useInnerCircle) {
					modIP.setRoi(pRoi[i - 1]);				
					modIP.setColor(0);
					modIP.fillOutside(pRoi[i - 1]);	
				}
				
				new8Hist=modIP.getHistogram();	
				for (int j = 0 ; j < my8Hist.length ; j++) {
					my8Hist[j] = my8Hist[j] + new8Hist[j];
				}			
				my8Hist[0] = 0; //set zero GV to zero
				my8Hist[255] = 0; //set last GV to zero	
			}				
		
			//find primary threshold
			myThresh = myAuto.getThreshold(mTMR.myPrimaryMethod, my8Hist);
		
			//find secondary threshold
			if(mTMR.mySecondaryMethod != null) {
				for(int i = myThresh ; i < my8Hist.length ; i++) my8Hist[i] = 0;
				myThresh = myAuto.getThreshold(mTMR.mySecondaryMethod, my8Hist);
			}			
				
			//do binarization
			for (int i = 1 ; i < eightBitTiff.getNSlices() + 1 ; i++) {
				
				IJ.showStatus("Binarizing slice #" + i + "/" + (eightBitTiff.getNSlices()));			
							
				eightBitTiff.setPosition(i);
				ImageProcessor myIP = eightBitTiff.getProcessor();
				
				//apply the threshold
				myIP.threshold(myThresh);
				
				//create a binary where the less dense phase is 255 and everything else 0
				myIP.invert();
				//for (int x = 0 ; x < myIP.getWidth() ; x++) {
				//	for (int y = 0 ; y < myIP.getHeight() ; y++) {						
				//		int thisImgPixel = myIP.getPixel(x, y);					
				//		myIP.putPixel(x, y, thisImgPixel);
				//	}
				//}
				
				//cut out everything outside column
				if (mTMR.useInnerCircle) {
					myIP.setRoi(pRoi[i - 1]);				
					myIP.setColor(0);
					myIP.fillOutside(pRoi[i - 1]);
				}
			
				myStack.addSlice(myIP);
			}
			
			//create binary out image 
			ImagePlus sillyTiff = new ImagePlus();
			sillyTiff.setStack(myStack);
			outImg[0] = sillyTiff;	
						
			//outImg[0].updateAndDraw();
			//outImg[0].show();
			
			//nowTiff.updateAndDraw();
			//nowTiff.show();
			
			//also save threshold		
			String thresholdSaverPath = mFC.myOutFolder + "\\EightBitThresholds.txt";
			jIO.writeThreshold(thresholdSaverPath, nowTiff.getShortTitle(), myThresh);	
			
			//also save threshold comparison images	
			if ((mTMR.save3DImage | mTMR.save4GeoDict) & mTMR.save4Evaluation) jIO.writeSnapshots4Comparison(mFC, nowTiff, outImg[0], oRoi, myZ);
			if (mTMR.save4Evaluation & !(mTMR.save3DImage | mTMR.save4GeoDict)) {
				for (int i = 0 ; i < myZ.length ; i++) myZ[i] = i + 1;
				jIO.writeSnapshots4Comparison(mFC,  nowTiff, outImg[0], oRoi, myZ);
			}
		}
		
		else { 	//in case that a constant threshold is used for all images..
	
			if (mTMR.minThreshold > 0 | mTMR.maxThreshold > 0) {
				
				ImageStack nowStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());		
				
				for (int i = 1 ; i < nowTiff.getNSlices() + 1 ; i++) {
				
					IJ.showStatus("Binarizing slice " + i + "/" + nowTiff.getNSlices());
				
					nowTiff.setPosition(i);
					ImageProcessor myIP = nowTiff.getProcessor();
					ImageProcessor lowIP = myIP.duplicate();
					ImageProcessor highIP = myIP.duplicate();
		
					if (mTMR.useInnerCircle) {
						lowIP.setRoi(oRoi[i - 1]);					
						lowIP.setColor(0);
						lowIP.fillOutside(oRoi[i - 1]);
						
						highIP.setRoi(oRoi[i - 1]);					
						highIP.setColor(0);
						highIP.fillOutside(oRoi[i - 1]);
					}
					
		/*			ImagePlus test = new ImagePlus("",lowIP);
					test.updateAndDraw();
					test.show();*/
					
					lowIP.threshold(mTMR.minThreshold);
					highIP.threshold(mTMR.maxThreshold);
										
					StackCalculator sC = new StackCalculator();	
					ImagePlus l = new ImagePlus("", lowIP);
					ImagePlus h = new ImagePlus("", highIP);
					ImagePlus outI = sC.subtract(l, h);
					ImageProcessor outIP = outI.getProcessor();					
					
					ImageProcessor eightIP = outIP.convertToByte(false);
				
					nowStack.addSlice(eightIP);				
				}
				
				//create binary out image 
				ImagePlus binTiff = new ImagePlus();
				binTiff.setStack(nowStack);
				outImg[0] = binTiff;
				
			/*	binTiff.updateAndDraw();
				binTiff.show();*/
				
			}
			
			//also save threshold comparison images			
			if (mTMR.save4Evaluation) jIO.writeSnapshots4Comparison(mFC, nowTiff, outImg[0], oRoi, myZ);			

		}
					
		return outImg;
	}
	
	public ImagePlus[] removePoresAboveSurface(ImagePlus nowTiff, int maxTopDepression, ImageProcessor topIP, PolygonRoi[] pRoi, PolygonRoi[] iRoi) {
		

		ImagePlus[] outTiff = new ImagePlus[2];
		ImagePlus img1 = new ImagePlus();
		ImagePlus img2 = new ImagePlus();
		
		ImageStack corrected4SurfaceStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight()); 
		ImageStack corr4ThicknessAnalyses = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight()); 
		
		//cut away soil above the soil surface
		for (int i = 0 ; i < maxTopDepression ; i++) {
			
			nowTiff.setPosition(i + 1);
			ImageProcessor nowIP = nowTiff.getProcessor();
			
			if (i < maxTopDepression) {		
				
				IJ.showStatus("Correcting for soil top surface in slice #" + (i + 1) + "/" + maxTopDepression);				
				
				ImageProcessor modIP = nowIP.duplicate();
				ImageProcessor forThIP = nowIP.duplicate();
				
				for (int x = 0 ; x < nowIP.getWidth() ; x++) {
					for (int y = 0 ; y < nowIP.getHeight() ; y++) {					
						int nowPix = nowIP.getPixel(x, y);
						int surPix = topIP.getPixel(x, y);
						if (i <= surPix) modIP.putPixelValue(x, y, 0);
						else modIP.putPixelValue(x, y, nowPix);
						if (i <= surPix - 100) forThIP.putPixelValue(x, y, 0);		//to prevent underestimation of pore diameters reaching the top..
						else forThIP.putPixelValue(x, y, nowPix);	
					}			
				}			
				
				modIP.setColor(Color.BLACK);
				modIP.setRoi(pRoi[i]);
				modIP.fillOutside(pRoi[i]);
				modIP.resetRoi();
				
				forThIP.setColor(Color.BLACK);
				forThIP.setRoi(pRoi[i]);
				forThIP.fillOutside(pRoi[i]);
				forThIP.resetRoi();
					
				if (iRoi != null) {
					modIP.setColor(Color.BLACK);
					modIP.setRoi(iRoi[i]);
					modIP.fill(iRoi[i]);
					modIP.resetRoi();
					
					forThIP.setColor(Color.BLACK);
					forThIP.setRoi(iRoi[i]);
					forThIP.fill(iRoi[i]);
					forThIP.resetRoi();
				}
										
				corrected4SurfaceStack.addSlice(modIP);
				corr4ThicknessAnalyses.addSlice(forThIP);
				
			}
		}
		
		img1.setStack(corrected4SurfaceStack);
		img2.setStack(corr4ThicknessAnalyses);
		
		outTiff[0] = img1;
		outTiff[1] = img2;
		
		return outTiff;			
	}
	
	public ImagePlus[] removePoresBelowSurface(ImagePlus nowTiff, int maxBotDepression, ImageProcessor botIP, PolygonRoi[] pRoi, PolygonRoi[] iRoi) {
		
		ImagePlus[] outTiff = new ImagePlus[2];
		ImagePlus img1 = new ImagePlus();
		ImagePlus img2 = new ImagePlus();
		
		ImageStack corrected4SurfaceStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		ImageStack corr4ThicknessAnalyses = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		
		//cut away soil above the soil surface
		for (int i = maxBotDepression ; i < nowTiff.getNSlices() ; i++) {
			
			IJ.showStatus("Correcting for soil bottom surface in slice #" + (maxBotDepression - (nowTiff.getNSlices() - i)) + "/" + maxBotDepression);				
		
			nowTiff.setPosition(i + 1);
			ImageProcessor nowIP = nowTiff.getProcessor();
		
			ImageProcessor modIP = nowIP.duplicate();
			ImageProcessor forThIP = nowIP.duplicate();
			
			for (int x = 0 ; x < nowIP.getWidth() ; x++) {
				for (int y = 0 ; y < nowIP.getHeight() ; y++) {					
					int nowPix = nowIP.getPixel(x, y);
					int surPix = nowTiff.getNSlices() - botIP.getPixel(x, y);
					if (i >= surPix) modIP.putPixelValue(x, y, 0);
					else modIP.putPixelValue(x, y, nowPix);
					if (i >= surPix + 100) forThIP.putPixelValue(x, y, 0);		//to prevent underestimation of pore diameters reaching the top..
					else forThIP.putPixelValue(x, y, nowPix);	
				}			
			}		
			
			modIP.setColor(Color.BLACK);
			modIP.setRoi(pRoi[i]);
			modIP.fillOutside(pRoi[i]);
			modIP.resetRoi();
			
			forThIP.setColor(Color.BLACK);
			forThIP.setRoi(pRoi[i]);
			forThIP.fillOutside(pRoi[i]);
			forThIP.resetRoi();
			
			corrected4SurfaceStack.addSlice(modIP);
			corr4ThicknessAnalyses.addSlice(forThIP);
		}
		

		img1.setStack(corrected4SurfaceStack);
		img2.setStack(corr4ThicknessAnalyses);
		
		outTiff[0] = img1;
		outTiff[1] = img2;
		
		return outTiff;	
		
	}
	
	public ImagePlus[] addColumnRegion2Stack(ImagePlus nowTiff, int maxTopDepression, int maxBotDepression, PolygonRoi[] pRoi, PolygonRoi[] iRoi) {
		
		ImagePlus[] outTiff = new ImagePlus[2];
		ImagePlus img1 = new ImagePlus();
		ImagePlus img2 = new ImagePlus();
		
		ImageStack corrected4SurfaceStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		ImageStack corr4ThicknessAnalyses = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		
		//cut away soil above the soil surface
		for (int i = maxTopDepression ; i < maxBotDepression ; i++) {
		
			nowTiff.setPosition(i + 1);
			ImageProcessor nowIP = nowTiff.getProcessor();
			
			IJ.showStatus("Adding in-between slice #" + (i + 1 - maxBotDepression -  maxTopDepression) + "/" + (nowTiff.getNSlices() - maxBotDepression -  maxTopDepression));
			
			ImageProcessor modIP = nowIP.duplicate();			
			ImageProcessor forThIP = nowIP.duplicate();
			
			modIP.setColor(Color.BLACK);
			modIP.setRoi(pRoi[i]);
			modIP.fillOutside(pRoi[i]);
			modIP.resetRoi();
			
			forThIP.setColor(Color.BLACK);
			forThIP.setRoi(pRoi[i]);
			forThIP.fillOutside(pRoi[i]);
			forThIP.resetRoi();
			
			/*ImagePlus tester = new ImagePlus("", modIP);
			tester.updateAndDraw();
			tester.show();*/
			
			corrected4SurfaceStack.addSlice(modIP);		
			corr4ThicknessAnalyses.addSlice(forThIP);
		}
		
		img1.setStack(corrected4SurfaceStack);
		img2.setStack(corr4ThicknessAnalyses);
		
		outTiff[0] = img1;
		outTiff[1] = img2;
		
		return outTiff;	
		
	}
	
	public ImagePlus[] stackImagePlusArrays(ImagePlus[] top, ImagePlus[] bot) {
	
		ImagePlus[] outTiff = new ImagePlus[top.length];
		
		for (int i = 0 ; i < top.length ; i++) {
									
			ImagePlus topI = top[i];
			ImagePlus botI = bot[i];
			ImageStack outStack = new ImageStack(topI.getWidth(), topI.getHeight());
			
			for (int j = 0 ; j < topI.getNSlices() ; j++) {
				topI.setPosition(j+1);
				ImageProcessor nowIP = topI.getProcessor();
				outStack.addSlice(nowIP);				
			}
			
			for (int j = 0 ; j < botI.getNSlices() ; j++) {
				botI.setPosition(j+1);
				ImageProcessor nowIP = botI.getProcessor();
				outStack.addSlice(nowIP);				
			}
			
			ImagePlus newImg = new ImagePlus();
			newImg.setStack(outStack);
			outTiff[i] = newImg;		
			
			//newImg.updateAndDraw();
			//newImg.show();
			
		}
		
		return outTiff;
				
	}
			
	public ImagePlus binarizeInTwoSteps(ImagePlus myImg) {
		
		ImagePlus outImg = new ImagePlus();
						
		ImageStack myStack = new ImageStack(myImg.getWidth(), myImg.getHeight());
		
		ImageProcessor myIP = myImg.getProcessor().convertToByte(true);
				
		int[] myHist = new int[256];
		int[] newHist;
		int myThresh;
		int i, j;
		
		AutoThresholder myAuto = new AutoThresholder();
		String[] myMethods = AutoThresholder.getMethods();
		
		for (i = 1 ; i < myImg.getStackSize() + 1 ; i++) {
			myImg.setPosition(i);			
			myIP = myImg.getProcessor().convertToByte(true);
			newHist=myIP.getHistogram();	
			for (j = 0 ; j < myHist.length ; j++) {
				myHist[j] = myHist[j] + newHist[j];
			}			
		}
		
		myThresh = myAuto.getThreshold(myMethods[7], myHist);  //13 Renyi, 7 mean 
		
		for (i = myThresh; i < myHist.length ; i++) {
			myHist[i]=0;
		}
		
		int[] cutHist = new int[myThresh];
		for (i = 0 ; i < myThresh ; i++) {
			cutHist[i] = myHist[i];
		}
		
		myThresh = myAuto.getThreshold(myMethods[13], myHist);
				
		//do binarization
		for (i = 1 ; i < myImg.getStackSize() + 1 ; i++) {
			IJ.showStatus("Binarizing slice " + i + "/" + myImg.getNSlices());			
			myImg.setPosition(i);	
			myIP = myImg.getProcessor().convertToByte(true);
			myIP.threshold(myThresh);
			myIP.invert();
			myStack.addSlice(myIP);
		}
		
		//create binary outimage 
		outImg.setStack(myStack);
			
		return outImg;
	}
	
	public ImagePlus standardizeImageGrayValues(ImagePlus nowTiff, String nowGaugePath, MenuWaiter.CalibrationReferences myNR, String myOutPath) {
		
		//init units
		RoiHandler roi = new RoiHandler();
		HistogramStuff hist = new HistogramStuff();
		TailoredMaths maths = new TailoredMaths();		
		InputOutput jIO = new InputOutput();			

		//init vars
		ImagePlus outTiff = new ImagePlus();								
		ImageStack outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());						
		int[] myHist = new int[256 * 256];
		int[] wallHist = new int[256 * 256];
		int[] newHist;
		int i, j;
		
		PolygonRoi[] pRoi = null;
		PolygonRoi[] oRoi  = null;
		PolygonRoi[] ooRoi  = null;
		
		//read gauge file
		if (myNR.useInnerCircle) {
			
			//read InnerCircle file
			ObjectDetector jOD = new ObjectDetector();
			ObjectDetector.ColCoords3D jCO = jOD.new ColCoords3D();
			int versio = jIO.checkInnerCircleFileVersion(nowGaugePath);			
			if (versio == 0) jCO = jIO.readInnerCircleVer0(nowGaugePath);	
			else jCO = jIO.readInnerCircleVer1(nowGaugePath);	
			
			int wallThickness = (int) (0.9 * Math.round(StatUtils.max(jCO.wallThickness)));
			
			if (myNR.material.equalsIgnoreCase("aluminium")) {
				pRoi = roi.makeMeAPolygonRoiStack("inner", "manual", jCO, 4);		
				oRoi = roi.makeMeAPolygonRoiStack("outer", "manual", jCO, 4);
				ooRoi = roi.makeMeAPolygonRoiStack("outerFromInner", "manual", jCO, -wallThickness-6);
			}
			else {
				pRoi = roi.makeMeAPolygonRoiStack("inner", "manual", jCO, 3);		
				oRoi = roi.makeMeAPolygonRoiStack("outer", "manual", jCO, 3);		
				ooRoi = roi.makeMeAPolygonRoiStack("outer", "manual", jCO, -wallThickness);
			}
		}
				
		//find illumination of this column	
		double[] lowerQuantile = new double[nowTiff.getNSlices()];
		double[] fractionMinimumMode = new double[nowTiff.getNSlices()];
		double[] upperQuantile = new double[nowTiff.getNSlices()];
		double[] mode = new double[nowTiff.getNSlices()];
		double[] wall = new double[nowTiff.getNSlices()];	
	
		//get the stacked histogram		
		for (i = 1 ; i < nowTiff.getNSlices() + 1 ; i++) {
			
			IJ.showStatus("Getting 16-bit histogram of slice #" + i + "/" + nowTiff.getNSlices());
			
			nowTiff.setPosition(i);		
			
			ImageProcessor myIP = nowTiff.getProcessor();
			ImageProcessor modIP = myIP.duplicate();
			ImageProcessor outIP = myIP.duplicate();
			ImageProcessor faroutIP = myIP.duplicate();
			ImageProcessor wallIP = myIP.duplicate();
			
			//cut away everything outside column
			if (myNR.useInnerCircle) {
				modIP.setRoi(pRoi[i - 1]);			
				modIP.setColor(0);
				modIP.fillOutside(pRoi[i - 1]);
			}
			
			//get histogram of soil
			newHist=modIP.getHistogram();
			newHist[0] = 0; //set zero GV to zero
			for (j = 0 ; j < myHist.length ; j++) {
				myHist[j] = myHist[j] + newHist[j];
			}
			double[] inCumHist = hist.calcCumulativeHistogram(newHist);
			
			//find mode  (2017/02/15: presently disabled)
			if (myNR.hiRef.equalsIgnoreCase("mode")) {
				mode[i-1] = (double)hist.findModeFromHistogram(newHist);
			}
			
			//cut away soil column including walls and another wall distance of the surroundings
			if (myNR.useInnerCircle) {
				faroutIP.setRoi(ooRoi[i - 1]);			
				faroutIP.setColor(0);
				faroutIP.fill(ooRoi[i - 1]);	
				faroutIP.resetRoi();
			}
			
			//get histogram of soil
			newHist=faroutIP.getHistogram();
			newHist[0] = 0; //set zero GV to zero
			for (j = 0 ; j < myHist.length ; j++) {
				myHist[j] = myHist[j] + newHist[j];
			}
			double[] faroutCumHist = hist.calcCumulativeHistogram(newHist);
			
			//cut away soil column including walls and everything outside another wall distance from the outer wall perimeter
			if (myNR.useInnerCircle) {
				outIP.setRoi(oRoi[i - 1]);				
				outIP.setColor(0);
				outIP.fill(oRoi[i - 1]);	
				outIP.resetRoi();
				
				outIP.setRoi(ooRoi[i - 1]);
				outIP.fillOutside(ooRoi[i - 1]);
				outIP.resetRoi();
			}
			
			//get histogram of soil
			newHist=outIP.getHistogram();
			newHist[0] = 0; //set zero GV to zero
			for (j = 0 ; j < myHist.length ; j++) {
				myHist[j] = myHist[j] + newHist[j];
			}
			double[] outCumHist = hist.calcCumulativeHistogram(newHist);
			
			//find reference quantiles
			if (myNR.lowRef.equalsIgnoreCase("quantile")) {
				if (myNR.sampleLowerWithinSoil) lowerQuantile[i-1] = hist.findPercentileFromCumHist(inCumHist, myNR.lowerReference); 
				else {
					if (myNR.lowerTag.equalsIgnoreCase("Outside")) lowerQuantile[i-1] = hist.findPercentileFromCumHist(outCumHist, myNR.lowerReference);
					else lowerQuantile[i-1] = hist.findPercentileFromCumHist(faroutCumHist, myNR.lowerReference);
				}
			}		
			
			if (myNR.hiRef.equalsIgnoreCase("quantile")) {					
				if (myNR.sampleUpperWithinSoil) upperQuantile[i-1] = hist.findPercentileFromCumHist(inCumHist, myNR.upperReference);
				else {
					if (myNR.upperTag.equalsIgnoreCase("Outside")) upperQuantile[i-1] = hist.findPercentileFromCumHist(outCumHist, myNR.lowerReference);
					else upperQuantile[i-1] = hist.findPercentileFromCumHist(faroutCumHist, myNR.lowerReference);
				}
			}
			
			//cut out everything but the wall
			if (myNR.useInnerCircle) {
				wallIP.setRoi(pRoi[i - 1]);			
				wallIP.setColor(0);
				wallIP.fill(pRoi[i - 1]);
			
				wallIP.setRoi(oRoi[i - 1]);
				wallIP.setColor(0);
				wallIP.fillOutside(oRoi[i - 1]);
			}
			
			//get histogram of wall
			newHist=wallIP.getHistogram();
			newHist[0] = 0; //set zero GV to zero
			for (j = 0 ; j < wallHist.length ; j++) {
				wallHist[j] = newHist[j];
				myHist[j] = myHist[j] + newHist[j];
			}	

			//DisplayThings dT = new DisplayThings();
			//dT.showMeMyRoi("pRoi, slice" + i, wallIP, pRoi[i - 1], 1);
					
			//find median wall gray			
			double[] wallCumHist = hist.calcCumulativeHistogram(wallHist);			
			wall[i-1] = hist.findPercentileFromCumHist(wallCumHist, 0.5);		
			
		/*	//normalize lower reference
			if (myNR.lowRef.equalsIgnoreCase("quantile")) {
				if (myNR.upperReference > 0) normLower[i - 1] = lowerQuantile[i-1] / upperQuantile[i-1];
				else normLower[i - 1] = lowerQuantile[i-1] / wall[i-1];				
			}
			else {
				normLower[i - 1] = wall[i-1] / upperQuantile[i-1];					
			}*/
			  
		}		
		
		//write results into file
		int windowHalfSize = 50;  //window half-size for LOESS filter
		if (myNR.lowRef.equalsIgnoreCase("minModeFrac")) myNR.originalLower = maths.LinearLOESSFilter(fractionMinimumMode, windowHalfSize);
		if (myNR.lowRef.equalsIgnoreCase("quantile")) myNR.originalLower = maths.LinearLOESSFilter(lowerQuantile, windowHalfSize);		
		if (myNR.lowRef.equalsIgnoreCase("wall")) myNR.originalLower = wall;
		
		if (myNR.hiRef.equalsIgnoreCase("quantile")) myNR.originalUpper = maths.LinearLOESSFilter(upperQuantile, windowHalfSize);
		if (myNR.hiRef.equalsIgnoreCase("mode")) myNR.originalUpper = maths.LinearLOESSFilter(mode, windowHalfSize);
		if (myNR.hiRef.equalsIgnoreCase("wall")) myNR.originalUpper = wall;
			
		String myFileName = nowTiff.getTitle().substring(0, nowTiff.getTitle().length() - 4);
		jIO.writeIlluminationCorrectionIntoAsciiFile(myNR, myOutPath, myFileName);
		
		//apply correction image
		for (i = 0 ; i < nowTiff.getNSlices() ; i++) {
			
			IJ.showStatus("Normalizing slice #" + i + "/" + nowTiff.getNSlices());
			
			nowTiff.setPosition(i + 1);
			
			ImageProcessor myIP = nowTiff.getProcessor();
			
			double nowIntercept = myNR.lowerTarget;
			double nowSlope = (myNR.upperTarget - myNR.lowerTarget) / (myNR.originalUpper[i] - myNR.originalLower[i]);
						
			for (int x = 0 ; x < myIP.getWidth() ; x++) {
				for (int y = 0 ; y < myIP.getHeight() ; y++) {
					
					double nowX = myIP.getPixelValue(x, y) - myNR.originalLower[i];
					double newPixelValue = nowIntercept + nowSlope * nowX;
					
					//introduce a correction approach for filtering discretization artifact  (John @20160921: huh.. where did this come from ..???.. let's comment it out!)
					//double fudger = nowSlope / (1 + Math.abs(nowX));
					//double artifactSmoother = 2 * rn.nextDouble() * fudger - fudger;
					
					//put new value
					//myIP.putPixelValue(x, y, newPixelValue + artifactSmoother);					
					myIP.putPixelValue(x, y, newPixelValue);
					
				}
			}
			
			outStack.addSlice(myIP);

		}
		
		outTiff.setStack(outStack);
		
		return outTiff;
		
	}
	
	
	///////////////////////////////////////////////
	// gimmicks
	//////////////////////////////////////////////
	
	public ImageProcessor invertSoilBinary(ImageProcessor myIP, PolygonRoi pRoi) {
	
		ImageProcessor modIP = myIP;
		
		modIP.invert();
		
		modIP.setRoi(pRoi);
		modIP.setColor(0);
		modIP.fillOutside(pRoi);
		modIP.resetRoi();
		
		return modIP;
				
	}
	
	public ImagePlus flipTiff(ImagePlus nowTiff) {
		
		ImageStack outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		ImagePlus outTiff = new ImagePlus();
		
		int i;
		
		for (i = nowTiff.getNSlices() ; i > 0 ; i--) {
			
			nowTiff.setPosition(i);
			
			ImageProcessor myIP = nowTiff.getProcessor();
			
			outStack.addSlice(myIP);			
		}
		outTiff.setStack(outStack);
			
		return outTiff;
		
	}
	
	public ImagePlus cutOutRealThickness(ImagePlus nowTiff, ImagePlus rawThickImp) {
		
		ImagePlus outTiff = new ImagePlus();
		ImageStack outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		
		//rawThickImp.updateAndDraw();
		//rawThickImp.show();
		
		for (int z = 0 ; z < nowTiff.getNSlices() ; z++) {
			nowTiff.setPosition(z+1);
			ImageProcessor nowIP = nowTiff.getProcessor();
			rawThickImp.setPosition(z+1);
			ImageProcessor rawIP = rawThickImp.getProcessor();
			ImageProcessor outIP = rawIP.duplicate();
			for (int x = 0 ; x < nowTiff.getWidth() ; x++) {		
				for (int y = 0 ; y < nowTiff.getHeight() ; y++) {
					int nowPix = nowIP.getPixel(x, y);
					if (nowPix == 0) outIP.putPixel(x, y, 0);
				}
			}
			outStack.addSlice(outIP);
		}
		
		outTiff.setStack(outStack);
		
		//outTiff.updateAndDraw();
		//outTiff.show();
		
		return outTiff;
	}
	
	public ImagePlus binarizeGradientMask(ImagePlus nowTiff, int myThresh) {
		
		ImagePlus outTiff = new ImagePlus();
		ImageStack outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());	
		
		//create mask for cutting out the rest..
		for (int i = 0 ; i < nowTiff.getNSlices() ; i++) {
			nowTiff.setPosition(i+1);		
		
			ImageProcessor binIP = nowTiff.getProcessor();
			binIP.threshold(myThresh);
			//binIP.dilate();			
			binIP.multiply(1/255.0);
			
			outStack.addSlice(binIP);
		}
		outTiff.setStack(outStack);
		
		return outTiff;		
	}
	
	public ImagePlus makeASubstack(ImagePlus nowTiff, int topSlice, int botSlice) {
		
		ImagePlus outTiff = new ImagePlus();
		ImageStack outStack = new ImageStack(nowTiff.getWidth(), nowTiff.getHeight());
		
		for (int i = topSlice ; i < botSlice ; i++) {
			
			nowTiff.setPosition(i + 1);
			ImageProcessor nowIP = nowTiff.getProcessor();
						
			outStack.addSlice(nowIP);			
		}
		
		outTiff.setStack(outStack);
		return outTiff;
		
	}

	public ImageProcessor fillHolesWithNeighboringValues(ImageProcessor surfaceIP, PolygonRoi[] pRoi, String topOrBottom) {
		
		JParticleCounter dPC = new JParticleCounter();		
		DisplayThings disp = new DisplayThings();
		
		//segment out deep roots			
		ImageProcessor justHoles = surfaceIP.duplicate();
		justHoles.threshold(1);
		justHoles.invert();
		
		justHoles.setBackgroundValue(0);
		if (topOrBottom.equalsIgnoreCase("top")) justHoles.fillOutside(pRoi[0]);
		else justHoles.fillOutside(pRoi[pRoi.length - 1]);
		ImageProcessor justHoles8Bit = justHoles.convertToByte(false);
		
		//identify the locations 
		ImagePlus holeOne = new ImagePlus("", justHoles8Bit);
		Object[] result = dPC.getParticles(holeOne, 1, 0d, Double.POSITIVE_INFINITY,-1, false);			
		int[][] particleLabels = (int[][]) result[1];
		long[] particleSizes = dPC.getParticleSizes(particleLabels);
		final int nParticles = particleSizes.length;			
		int[][] limits = dPC.getParticleLimits(holeOne, particleLabels, nParticles);  //xmin, xmax, ymin, ymax, zmin, zmax
		ImagePlus myHoles = disp.jDisplayParticleLabels(particleLabels, holeOne);
		IJ.freeMemory();IJ.freeMemory();
		
		//fill Holes
		ImageProcessor outIP = surfaceIP.duplicate();
		for (int i = 1 ; i < nParticles ; i++) {
			
			int xmin = limits[i][0] - 1;
			int xmax = limits[i][1] + 1;
			int ymin = limits[i][2] - 1;
			int ymax = limits[i][3] + 1;
							
			float[] X = new float[]{xmin, xmin, xmax, xmax, xmin};
			float[] Y = new float[]{ymin, ymax, ymax, ymin, ymin};
			
			PolygonRoi mRoi = new PolygonRoi(X, Y, Roi.POLYGON);
	
			//init list for hole-neighboring pixels
			ArrayList<Integer> neighbors = new ArrayList<Integer>();
			
			//cut out canvas around hole #i, remove all holes with other IDs and binarize the image.
			ImageProcessor hoIP = myHoles.getProcessor();
			
			ImageProcessor thIP = new ByteProcessor(xmax - xmin + 1, ymax - ymin + 1); 
			for (int x = xmin ; x < xmax + 1 ; x++) {
				for (int y = ymin ; y < ymax + 1 ; y++) {	
					int nowVal = (int)Math.round(hoIP.getPixelValue(x, y));
					if (nowVal == i) thIP.putPixel(x - xmin, y - ymin, 255); 		
					else thIP.putPixel(x, y, 0); 	
				}
			}
			
			// do the same for the surface elevation map
			ImageProcessor vlIP = surfaceIP.duplicate();
			vlIP.setRoi(mRoi);
			vlIP.crop();
			
			//make a copy of the binarized hole, dilate it and sample gray value of neighbors
			ImageProcessor cpIP = thIP.duplicate();
			cpIP.erode();
	
			for (int x = 0 ; x < cpIP.getWidth() ; x++) {
				for (int y = 0 ; y < cpIP.getHeight() ; y++) {						
					int nowTH = thIP.getPixel(x, y);
					int nowCP = cpIP.getPixel(x, y);
					int nowVL = (int)Math.round(vlIP.getPixelValue(x + xmin, y + ymin));
					if (nowTH == 0 & nowCP > 0 & nowVL > 0) {
						neighbors.add(nowVL);
					}
				}
			}
	
			//calculate median neighbor value
			double[] neighborsAsArray = new double[neighbors.size()]; 
			for (int j = 0 ; j < neighbors.size() ; j++) neighborsAsArray[j] = neighbors.get(j);
			int medianNeighbor = (int)StatUtils.percentile(neighborsAsArray, 10);
			
			//fill hole with neighborvalue
			for (int x = 0 ; x < cpIP.getWidth() ; x++) {
				for (int y = 0 ; y < cpIP.getHeight() ; y++) {
					int nowTH = thIP.getPixel(x, y);
					if (nowTH > 0) {
						outIP.putPixel(x + xmin, y + ymin, medianNeighbor);
					}
				}
			}				
		}
		
		return outIP;
	}
	
}
	


	


