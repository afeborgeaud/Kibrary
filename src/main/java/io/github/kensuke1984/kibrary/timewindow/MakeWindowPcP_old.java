package io.github.kensuke1984.kibrary.timewindow;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.Phases;
import io.github.kensuke1984.kibrary.util.Station;
import io.github.kensuke1984.kibrary.util.Trace;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACData;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderData;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

public class MakeWindowPcP_old {

	private Set<TimewindowInformation> infoSet;
	
	private Set<TimewindowInformation> infoSetP;
	
	private Path workdir;
	
	private double timeBefore;
	private double timeAfter;
	private double timeBeforesS;
	
	private boolean select;
	private double minCorr;
	private double maxVariance;
	private double maxRatio;
	
	private double minPeriod;
	
	private Set<TimewindowInformation> timewindowsForSelection;
	
	public MakeWindowPcP_old(Path workdir) {
		this.workdir = workdir;
		this.infoSet = new HashSet<>();
		this.infoSetP = new HashSet<>();
		timeBefore = 20;
		timeAfter = 20;
		timeBeforesS = 5;
		
		select = false;
		minCorr = 0.0;
		maxVariance = 2.;
		maxRatio = 2.;
		
		minPeriod = 6.;
		
		Path timewindowPath = workdir.resolve("selectedTimewindow_PPcP_60deg_sn4.dat");
		try {
			timewindowsForSelection = TimewindowInformationFile.read(timewindowPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws IOException {
		Path workdir = Paths.get("/work/anselme/CA_ANEL_NEW/VERTICAL/syntheticPREM_Q165/filtered_stf_6-200s");
//		Path workdir = Paths.get(".");
		
		MakeWindowPcP_old makewindow = new MakeWindowPcP_old(workdir);
		try {
			makewindow.run();
		} catch (TauModelException e) {
			e.printStackTrace();
		}
		
		Path outpath = workdir.resolve("timewindow" + Utilities.getTemporaryString() + ".dat");
		TimewindowInformationFile.write(makewindow.infoSet, outpath);
		
		Path outpathP = workdir.resolve("timewindow_P.dat");
		TimewindowInformationFile.write(makewindow.infoSetP, outpathP);
	}
	
	public void run() throws IOException, TauModelException {
		TauP_Time timetool = new TauP_Time("prem");
		timetool.parsePhaseList("P, PcP, pP");
		
//		try {
//		Utilities.runEventProcess(workdir, eventFolder -> {
		
		for (EventFolder eventFolder : Utilities.eventFolderSet(workdir)) {
			System.out.println(eventFolder);
			timetool.setSourceDepth(6371. - eventFolder.getGlobalCMTID().getEvent().getCmtLocation().getR());
			
//			Set<SACFileName> obsNames = eventFolder.sacFileSet();
//			obsNames.removeIf(sfn -> !sfn.isOBS() || !sfn.getComponent().equals(SACComponent.Z));
			
//			for (SACFileName obsName : obsNames) {
			try {
			eventFolder.sacFileSet().stream().filter(sfn -> sfn.isOBS() && sfn.getComponent().equals(SACComponent.Z)).forEach(obsName -> {
				SACHeaderData obsHeadertmp = null;
				try {
					obsHeadertmp = obsName.readHeader();
				} catch (IOException e) {
					e.printStackTrace();
				}
				
				final SACHeaderData obsHeader = obsHeadertmp;
				
				if (timewindowsForSelection.parallelStream().filter(tw -> tw.getGlobalCMTID().equals(obsName.getGlobalCMTID())
						&& tw.getStation().equals(obsHeader.getStation()) && tw.getComponent().equals(obsName.getComponent()))
						.count() == 0)
					return;
				
				double distance = obsHeader.getValue(SACHeaderEnum.GCARC);
				try {
					timetool.calculate(distance);
				} catch (TauModelException e) {
					e.printStackTrace();
				}
				if (timetool.getNumArrivals() != 3) {
					return;
				}
				Arrival arrivalS = timetool.getArrival(0);
				Arrival arrivalScS = timetool.getArrival(1);
				Arrival arrivalsS = timetool.getArrival(2);
				if (!arrivalS.getPhase().getName().equals("P") || !arrivalScS.getPhase().getName().equals("PcP")
						|| !arrivalsS.getPhase().getName().equals("pP")) {
//					System.err.println("Problem computing S or ScS " + obsName.getName());
					return;
				}
				double timeS = arrivalS.getTime();
				double timeScS = arrivalScS.getTime();
				double timesS = arrivalsS.getTime();
				
				if (timesS - timeScS < minPeriod * 1.6)
					return;
				
				SACData synData = null;
				try {
					synData = new SACFileName(Paths.get(obsName.getAbsolutePath().concat("sc"))).read();
				} catch (IOException e) {
					e.printStackTrace();
				}
				Trace synTrace = synData.createTrace().cutWindow(timeS - 5, timeS + 20);
				double deltaTimeP2P = Math.abs(synTrace.getXforMaxValue() - synTrace.getXforMinValue());
				double timeLatePeak = synTrace.getXforMaxValue() > synTrace.getXforMinValue() ? synTrace.getXforMaxValue() : synTrace.getXforMinValue();
				double timeEndOfS = timeLatePeak + deltaTimeP2P * 0.9;
				
				double startTime = timeScS - timeBefore;
				double endTime = timeScS + timeAfter;
				
				if (startTime < timeEndOfS)
					startTime = timeEndOfS;
				if (endTime > timesS - timeBeforesS)
					endTime = timesS - timeBeforesS;
				
				if (timeEndOfS > timeScS - minPeriod/2.5)
					return;
				
				TimewindowInformation timewindow_P = new TimewindowInformation(timeS - 10, timeS + 20,
						obsHeader.getStation(), obsHeader.getGlobalCMTID(),
						obsName.getComponent(), new Phase[] {Phase.P});
				infoSetP.add(timewindow_P);
				
				if (select) {
					SACData obsData = null;
					try {
						obsData = obsName.read();
					} catch (IOException e) {
						e.printStackTrace();
					}
					Trace synScS = synData.createTrace().cutWindow(startTime, endTime);
					Trace obsScS = obsData.createTrace().cutWindow(startTime, endTime);
					double shift = synScS.getXforMaxValue() - obsScS.getXforMaxValue();
					
					RealVector obsVector = obsData.createTrace().cutWindow(startTime - shift, endTime - shift).getYVector();
					RealVector synVector = new ArrayRealVector(Arrays.copyOf(synScS.getY(), obsVector.getDimension()));
					
					double corr = obsVector.dotProduct(synVector) / (obsVector.getNorm() * synVector.getNorm());
					double ratio = synVector.getLInfNorm() / obsVector.getLInfNorm();
					double variance = synVector.subtract(obsVector)
							.dotProduct(synVector.subtract(obsVector))
							/ obsVector.dotProduct(obsVector);
					
					boolean keep = corr >= minCorr && ratio < maxRatio && ratio > 1./maxRatio && variance < maxVariance;
					if (!keep)
						return;
				}
				
				TimewindowInformation timewindow = new TimewindowInformation(startTime, endTime,
						obsHeader.getStation(), obsHeader.getGlobalCMTID(),
						obsName.getComponent(), new Phase[] {Phase.PcP});
				
				infoSet.add(timewindow);
			});
			} catch (IOException e) {
				e.printStackTrace();
			}
//		}, 10, TimeUnit.HOURS);
		}
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
	}
	
	public Set<TimewindowInformation> getInfoSet() {
		return infoSet;
	}

}
