package io.github.kensuke1984.kibrary.waveformdata;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.butterworth.BandPassFilter;
import io.github.kensuke1984.kibrary.butterworth.ButterworthFilter;
import io.github.kensuke1984.kibrary.datacorrection.StaticCorrection;
import io.github.kensuke1984.kibrary.datacorrection.StaticCorrectionFile;
import io.github.kensuke1984.kibrary.inversion.RandomNoiseMaker;
import io.github.kensuke1984.kibrary.math.FourierTransform;
import io.github.kensuke1984.kibrary.math.HilbertTransform;
import io.github.kensuke1984.kibrary.timewindow.TimewindowInformation;
import io.github.kensuke1984.kibrary.timewindow.TimewindowInformationFile;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Station;
import io.github.kensuke1984.kibrary.util.Trace;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACData;
import io.github.kensuke1984.kibrary.util.sac.SACExtension;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderData;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * 
 * Creates dataset containing observed and synthetic waveforms. <br>
 * The output is a set of an ID and waveform files.
 * 
 * Observed and synthetic waveforms in SAC files are collected from the obsDir
 * and synDir, respectively. Only SAC files, which sample rates are
 * {@link parameter.ObservedSyntheticDatasetMaker#sacSamplingHz}, are used. Both
 * folders must have event folders inside which have waveforms.
 * 
 * The static correction is applied as described in {@link StaticCorrection}
 * 
 * 
 * The sample rates of the data is
 * {@link parameter.ObservedSyntheticDatasetMaker#finalSamplingHz}.<br>
 * Timewindow information in
 * {@link parameter.ObservedSyntheticDatasetMaker#timewindowInformationPath} is
 * used for cutting windows.
 * 
 * Only pairs of a seismic source and a receiver with both an observed and
 * synthetic waveform are collected.
 * 
 * This class does not apply a digital filter, but extract information about
 * passband written in SAC files.
 * 
 * TODO <b> Assume that there are no stations with same name but different
 * network in one event</b>
 * 
 * @version 0.2.1.1
 * 
 * @author Kensuke Konishi
 * 
 */
public class ObservedSyntheticDatasetMaker implements Operation {

	private Path workPath;
	private Properties property;
	
	boolean correctionBootstrap;
	private int nSample;
	
	private boolean addNoise;
	
	/**
	 * components to be included in the dataset
	 */
	private Set<SACComponent> components;

	public ObservedSyntheticDatasetMaker(Properties property) throws IOException {
		this.property = (Properties) property.clone();
		set();
	}

	private void checkAndPutDefaults() {
		if (!property.containsKey("workPath"))
			property.setProperty("workPath", ".");
		if (!property.containsKey("obsPath"))
			property.setProperty("obsPath", ".");
		if (!property.containsKey("synPath"))
			property.setProperty("synPath", ".");
		if (!property.containsKey("components"))
			property.setProperty("components", "Z R T");
		if (!property.containsKey("convolute"))
			property.setProperty("convolute", "true");
		if (!property.containsKey("amplitudeCorrection"))
			property.setProperty("amplitudeCorrection", "false");
		if (!property.containsKey("timeCorrection"))
			property.setProperty("timeCorrection", "false");
		if (!property.containsKey("timewindowPath"))
			throw new IllegalArgumentException("There is no information about timewindowPath.");
		if (!property.containsKey("sacSamplingHz"))
			property.setProperty("sacSamplingHz", "20");
		if (!property.containsKey("finalSamplingHz"))
			property.setProperty("finalSamplingHz", "1");
		if (!property.containsKey("correctionBootstrap"))
			property.setProperty("correctionBootstrap", "false");
		if (!property.containsKey("nSample"))
			property.setProperty("nSample", "100");
		if (!property.containsKey("shiftdata"))
			property.setProperty("shiftdata", "false");
		if (!property.containsKey("shiftdataValue"))
			property.setProperty("shiftdataValue", "0.");
		if (!property.containsKey("minDistance"))
			property.setProperty("minDistance", "0.");
		if (!property.containsKey("addNoise"))
			property.setProperty("addNoise", "false");
		if (!property.containsKey("correctMantle"))
			property.setProperty("correctMantle", "false");
	}

	private void set() throws NoSuchFileException {
		checkAndPutDefaults();
		workPath = Paths.get(property.getProperty("workPath"));
		if (!Files.exists(workPath))
			throw new RuntimeException("The workPath: " + workPath + " does not exist");
		obsPath = getPath("obsPath");
		synPath = getPath("synPath");
		components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
				.collect(Collectors.toSet());
		timewindowPath = getPath("timewindowPath");
		timeCorrection = Boolean.parseBoolean(property.getProperty("timeCorrection"));
		amplitudeCorrection = Boolean.parseBoolean(property.getProperty("amplitudeCorrection"));

		if (timeCorrection || amplitudeCorrection) {
			if (!property.containsKey("staticCorrectionPath"))
				throw new RuntimeException("staticCorrectionPath is blank");
			staticCorrectionPath = getPath("staticCorrectionPath");
			if (!Files.exists(staticCorrectionPath))
				throw new NoSuchFileException(staticCorrectionPath.toString());
		}
		
		correctMantle = Boolean.parseBoolean(property.getProperty("correctMantle"));
		if (correctMantle)
			mantleCorrectionPath = getPath("mantleCorrectionPath");

		convolute = Boolean.parseBoolean(property.getProperty("convolute"));

		// sacSamplingHz
		// =Double.parseDouble(reader.getFirstValue("sacSamplingHz")); TODO
		sacSamplingHz = 20;
		finalSamplingHz = Double.parseDouble(property.getProperty("finalSamplingHz"));
		
		correctionBootstrap = Boolean.parseBoolean(property.getProperty("correctionBootstrap"));
		nSample = correctionBootstrap ? Integer.parseInt(property.getProperty("nSample")) : 1;
		
		shiftdata = Boolean.parseBoolean(property.getProperty("shiftdata"));
		if (shiftdata)
			shiftdataValue = Double.parseDouble(property.getProperty("shiftdataValue"));
		
		minDistance = Double.parseDouble(property.getProperty("minDistance"));
		
		addNoise = Boolean.parseBoolean(property.getProperty("addNoise"));
	}
	
	private Random random;

	public static void writeDefaultPropertiesFile() throws IOException {
		Path outPath = Paths
				.get(ObservedSyntheticDatasetMaker.class.getName() + Utilities.getTemporaryString() + ".properties");
		try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
			pw.println("manhattan ObservedSyntheticDatasetMaker");
			pw.println("##Path of a working directory (.)");
			pw.println("#workPath");
			pw.println("##SacComponents to be used (Z R T)");
			pw.println("#components");
			pw.println("##Path of a root folder containing observed dataset (.)");
			pw.println("#obsPath");
			pw.println("##Path of a root folder containing synthetic dataset (.)");
			pw.println("#synPath");
			pw.println("##boolean convolulte (true)");
			pw.println("#convolute");
			pw.println("##boolean timeCorrection (false)");
			pw.println("#timeCorrection");
			pw.println("##boolean amplitudeCorrection (false)");
			pw.println("#amplitudeCorrection");
			pw.println("#correctionBootstrap false");
			pw.println("#nSample 100");
			pw.println("##Path of a timewindow information file, must be defined");
			pw.println("#timewindowPath timewindow.dat");
			pw.println("##Path of a static correction file, ");
			pw.println("##if any of the corrections are true, the path must be defined");
			pw.println("#staticCorrectionPath staticCorrection.dat");
			pw.println("##double value of sac sampling Hz (20) can't be changed now");
			pw.println("#sacSamplingHz the value will be ignored");
			pw.println("##double value of sampling Hz in output files (1)");
			pw.println("#finalSamplingHz");
			pw.println("##Shift data to simulate error (false)");
			pw.println("#shiftdata");
			pw.println("##Data time shift (to test sensitivity to statics errors) (0)");
			pw.println("#shiftdataValue");
			pw.println("#minDistance");
			pw.println("#addNoise false");
			pw.println("#correctMantle false");
			pw.println("#mantleCorrectionPath mantleCorrectionPath.dat");
		}
		System.err.println(outPath + " is created.");
	}

	private boolean shiftdata;
	
	private double shiftdataValue;
	
	private boolean correctMantle;

	/**
	 * {@link Path} of a root folder containing observed dataset
	 */
	private Path obsPath;

	/**
	 * {@link Path} of a root folder containing synthetic dataset
	 */
	private Path synPath;

	/**
	 * {@link Path} of a timewindow information file
	 */
	private Path timewindowPath;

	/**
	 * {@link Path} of a static correction file
	 */
	private Path staticCorrectionPath;

	private Path mantleCorrectionPath;
	
	/**
	 * Sacのサンプリングヘルツ （これと異なるSACはスキップ）
	 */
	private double sacSamplingHz;

	/**
	 * 切り出すサンプリングヘルツ
	 */
	private double finalSamplingHz;

	/**
	 * if it is true, the dataset will contain synthetic waveforms after
	 * convolution
	 */
	private boolean convolute;

	/**
	 * If it corrects time
	 */
	private boolean timeCorrection;

	/**
	 * if it corrects amplitude ratio
	 */
	private boolean amplitudeCorrection;

	private Set<StaticCorrection> staticCorrectionSet;
	
	private Set<StaticCorrection> mantleCorrectionSet;

	private Set<TimewindowInformation> timewindowInformationSet;

	private WaveformDataWriter dataWriter;
	
	private WaveformDataWriter envelopeWriter;
	
	private WaveformDataWriter spcAmpWriter;
	
	private WaveformDataWriter spcReWriter;
	
	private WaveformDataWriter spcImWriter;
	
	private WaveformDataWriter hyWriter;

	private Set<EventFolder> eventDirs;
	private Set<Station> stationSet;
	private Set<GlobalCMTID> idSet;
	private Phase[] phases;
	private double[][] periodRanges;
	
	private double minDistance;

	private void readPeriodRanges() {
		try {
			List<double[]> ranges = new ArrayList<>();
			
			Set<SACFileName> sacfilenames = Utilities.sacFileNameSet(obsPath).stream().limit(20).collect(Collectors.toSet());
			
//			for (SACFileName name : Utilities.sacFileNameSet(obsPath)) {
			for (SACFileName name : sacfilenames) {
				if (!name.isOBS())
					continue;
				SACHeaderData header = name.readHeader();
				double[] range = new double[] { header.getValue(SACHeaderEnum.USER0),
						header.getValue(SACHeaderEnum.USER1) };
				boolean exists = false;
				if (ranges.size() == 0)
					ranges.add(range);
				for (int i = 0; !exists && i < ranges.size(); i++)
					if (Arrays.equals(range, ranges.get(i)))
						exists = true;
				if (!exists)
					ranges.add(range);
			}
			periodRanges = ranges.toArray(new double[0][]);
		} catch (Exception e) {
			throw new RuntimeException("Error in reading period ranges from SAC files.");
		}
	}

	/**
	 * 
	 * @param args
	 *            [a property file name]
	 * @throws Exception
	 *             if any
	 */
	public static void main(String[] args) throws Exception {
		Properties property = new Properties();
		if (args.length == 0)
			property.load(Files.newBufferedReader(Operation.findPath()));
		else if (args.length == 1)
			property.load(Files.newBufferedReader(Paths.get(args[0])));
		else
			throw new IllegalArgumentException("too many arguments. It should be 0 or 1(property file name)");
		ObservedSyntheticDatasetMaker osdm = new ObservedSyntheticDatasetMaker(property);

		long startT = System.nanoTime();
		System.err.println(ObservedSyntheticDatasetMaker.class.getName() + " is running.");
		osdm.run();
		System.err.println(ObservedSyntheticDatasetMaker.class.getName() + " finished in "
				+ Utilities.toTimeString(System.nanoTime() - startT));

	}

	@Override
	public void run() throws Exception {
		if (20 % finalSamplingHz != 0)
			throw new RuntimeException("Must choose a finalSamplingHz that divides 20");
		
		if (timeCorrection || amplitudeCorrection)
			staticCorrectionSet = StaticCorrectionFile.read(staticCorrectionPath);
		
		if (correctMantle) {
			System.out.println("Using mantle corrections");
			mantleCorrectionSet = StaticCorrectionFile.read(mantleCorrectionPath);
		}

		// obsDirからイベントフォルダを指定
		eventDirs = Utilities.eventFolderSet(obsPath);
		
		timewindowInformationSet = TimewindowInformationFile.read(timewindowPath)
				.stream().filter(tw -> {
					double distance = Math.toDegrees(tw.getGlobalCMTID().getEvent().getCmtLocation().getEpicentralDistance(tw.getStation().getPosition()));
					if (distance < minDistance)
						return false;
					return true;
				}).collect(Collectors.toSet());
		
		//debug
//		timewindowInformationSet = timewindowInformationSet.parallelStream().filter(t -> t.getGlobalCMTID().equals(new GlobalCMTID("200609220232A"))
//				&& t.getStation().getStationName().contentEquals("ISCO")).collect(Collectors.toSet());
		
		stationSet = timewindowInformationSet.stream().map(TimewindowInformation::getStation)
				.collect(Collectors.toSet());
		idSet = timewindowInformationSet.stream().map(TimewindowInformation::getGlobalCMTID)
				.collect(Collectors.toSet());
		phases = timewindowInformationSet.stream().map(TimewindowInformation::getPhases).flatMap(p -> Stream.of(p))
				.distinct().toArray(Phase[]::new);
		
		readPeriodRanges();
		
		//TODO
//		periodRanges = new double[][] { {12.5, 200.} };
		
		for (int isample = 0; isample < nSample; isample++) {
			if (correctionBootstrap)
				System.err.println("Random sample " + isample);
			random = new Random(); // initialize random generator with a new seed for each set of waveforms
			int n = Runtime.getRuntime().availableProcessors();
			System.out.println("Running on " + n + " processors");
			ExecutorService execs = Executors.newFixedThreadPool(n);
			String dateStr = Utilities.getTemporaryString();
			Path waveIDPath = null;
			Path waveformPath = null;
			Path envelopeIDPath = null;
			Path envelopePath = null;
			Path hyIDPath = null;
			Path hyPath = null;
			Path spcAmpIDPath = null;
			Path spcAmpPath = null;
			Path spcReIDPath = null;
			Path spcRePath = null;
			Path spcImIDPath = null;
			Path spcImPath = null;
			if (!correctionBootstrap) {
				waveIDPath = workPath.resolve("waveformID" + dateStr + ".dat");
				waveformPath = workPath.resolve("waveform" + dateStr + ".dat");
				envelopeIDPath = workPath.resolve("envelopeID" + dateStr + ".dat");
				envelopePath = workPath.resolve("envelope" + dateStr + ".dat");
				hyIDPath = workPath.resolve("hyID" + dateStr + ".dat");
				hyPath = workPath.resolve("hy" + dateStr + ".dat");
				spcAmpIDPath = workPath.resolve("spcAmpID" + dateStr + ".dat");
				spcAmpPath = workPath.resolve("spcAmp" + dateStr + ".dat");
				spcReIDPath = workPath.resolve("spcReID" + dateStr + ".dat");
				spcRePath = workPath.resolve("spcRe" + dateStr + ".dat");
				spcImIDPath = workPath.resolve("spcImID" + dateStr + ".dat");
				spcImPath = workPath.resolve("spcIm" + dateStr + ".dat");
			}
			else {
				waveIDPath = workPath.resolve("waveformID" + String.format("_RND%04d", isample) + ".dat");
				waveformPath = workPath.resolve("waveform" + String.format("_RND%04d", isample) + ".dat");
			}
			try (WaveformDataWriter bdw = new WaveformDataWriter(waveIDPath, waveformPath, stationSet, idSet,
					periodRanges, phases)) {
				envelopeWriter = new WaveformDataWriter(envelopeIDPath, envelopePath, stationSet, idSet,
						periodRanges, phases);
				hyWriter = new WaveformDataWriter(hyIDPath, hyPath, stationSet, idSet,
						periodRanges, phases);
				spcAmpWriter = new WaveformDataWriter(spcAmpIDPath, spcAmpPath,
						stationSet, idSet, periodRanges, phases);
				spcReWriter = new WaveformDataWriter(spcReIDPath, spcRePath,
						stationSet, idSet, periodRanges, phases);
				spcImWriter = new WaveformDataWriter(spcImIDPath, spcImPath,
						stationSet, idSet, periodRanges, phases);
				dataWriter = bdw;
				for (EventFolder eventDir : eventDirs)
					execs.execute(new Worker(eventDir));
				execs.shutdown();
				while (!execs.isTerminated())
					Thread.sleep(1000);
				envelopeWriter.close();
				hyWriter.close();
				spcAmpWriter.close();
				spcImWriter.close();
				spcReWriter.close();
				System.err.println("\n" + numberOfPairs.get() + " pairs of observed and synthetic waveforms are output.");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * number of OUTPUT pairs. (excluding ignored traces)
	 */
	private AtomicInteger numberOfPairs = new AtomicInteger();

	/**
	 * 与えられたイベントフォルダの観測波形と理論波形を書き込む 両方ともが存在しないと書き込まない
	 * 
	 * @author kensuke
	 * 
	 */
	private class Worker implements Runnable {

		private EventFolder obsEventDir;

		private Worker(EventFolder eventDir) {
			obsEventDir = eventDir;
		}

		@Override
		public void run() {
			Path synEventPath = synPath.resolve(obsEventDir.getGlobalCMTID().toString());
			if (!Files.exists(synEventPath))
				throw new RuntimeException(synEventPath + " does not exist.");

			Set<SACFileName> obsFiles;
			try {
				(obsFiles = obsEventDir.sacFileSet()).removeIf(sfn -> !sfn.isOBS());
			} catch (IOException e2) {
				e2.printStackTrace();
				return;
			}

			for (SACFileName obsFileName : obsFiles) {
				// データセットに含める成分かどうか
				if (!components.contains(obsFileName.getComponent()))
					continue;
				String stationName = obsFileName.getStationName();
				GlobalCMTID id = obsFileName.getGlobalCMTID();
				SACComponent component = obsFileName.getComponent();
				String name = convolute
						? stationName + "." + id + "." + SACExtension.valueOfConvolutedSynthetic(component)
						: stationName + "." + id + "." + SACExtension.valueOfSynthetic(component);
				SACFileName synFileName = new SACFileName(synEventPath.resolve(name));

				if (!synFileName.exists())
					continue;

				Set<TimewindowInformation> windows = timewindowInformationSet.stream()
						.filter(info -> info.getStation().getStationName().equals(stationName))
						.filter(info -> info.getGlobalCMTID().equals(id))
						.filter(info -> info.getComponent() == component).collect(Collectors.toSet());

				// タイムウインドウの情報が入っていなければ次へ
				if (windows.isEmpty())
					continue;
				
				SACData obsSac;
				try {
					obsSac = obsFileName.read();
				} catch (IOException e1) {
					System.err.println("error occured in reading " + obsFileName);
					e1.printStackTrace();
					continue;
				}

				SACData synSac;
				try {
					synSac = synFileName.read();
				} catch (IOException e1) {
					System.err.println("error occured in reading " + synFileName);
					e1.printStackTrace();
					continue;
				}

				// Sampling Hz of observed and synthetic must be same as the
				// value declared in the input file
				if (obsSac.getValue(SACHeaderEnum.DELTA) != 1 / sacSamplingHz
						&& obsSac.getValue(SACHeaderEnum.DELTA) == synSac.getValue(SACHeaderEnum.DELTA)) {
					System.err.println("Values of sampling Hz of observed and synthetic "
							+ (1 / obsSac.getValue(SACHeaderEnum.DELTA)) + ", "
							+ (1 / synSac.getValue(SACHeaderEnum.DELTA)) + " are invalid, they should be "
							+ sacSamplingHz);
					continue;
				}

				// bandpassの読み込み 観測波形と理論波形とで違えばスキップ
				double minPeriod = 0;
				double maxPeriod = Double.POSITIVE_INFINITY;
				if (obsSac.getValue(SACHeaderEnum.USER0) != synSac.getValue(SACHeaderEnum.USER0)
						|| obsSac.getValue(SACHeaderEnum.USER1) != synSac.getValue(SACHeaderEnum.USER1)) {
					System.err.println("band pass filter difference");
					continue;
				}
				minPeriod = obsSac.getValue(SACHeaderEnum.USER0) == -12345 ? 0 : obsSac.getValue(SACHeaderEnum.USER0);
				maxPeriod = obsSac.getValue(SACHeaderEnum.USER1) == -12345 ? 0 : obsSac.getValue(SACHeaderEnum.USER1);

				Station station = obsSac.getStation();

				for (TimewindowInformation window : windows) {
					int npts = (int) ((window.getEndTime() - window.getStartTime()) * finalSamplingHz);
					double startTime = window.getStartTime();
					double shift = 0;
					double ratio = 1;
					if (timeCorrection || amplitudeCorrection)
						try {
							StaticCorrection sc = getStaticCorrection(window);
							shift = timeCorrection ? sc.getTimeshift() : 0;
							ratio = amplitudeCorrection ? sc.getAmplitudeRatio() : 1;
							
							if (correctionBootstrap) {
								double tmp = 2 * random.nextGaussian(); 
								shift += tmp;
								System.out.println(tmp + " " + shift);
							}
						} catch (NoSuchElementException e) {
							System.err.println("There is no static correction information for\\n " + window);
							continue;
						}
					
					if (correctMantle)
						try {
							StaticCorrection sc = getMantleCorrection(window);
							shift += sc.getTimeshift();
//							if (window.getGlobalCMTID().equals(new GlobalCMTID("200911130727A")) && window.getStation().getStationName().equals("F28A")) {
//								System.out.println(sc.getTimeshift());
//								System.out.println(sc);
//							}
						} catch (NoSuchElementException e) {
							System.err.println("There is no mantle correction information for\\n " + window);
							continue;
						}
					
					if (shiftdata)
						shift += shiftdataValue;
					
					double[] obsData = null;
					if (addNoise)
						obsData = cutDataSacAddNoise(obsSac, startTime - shift, npts);
					else
						obsData = cutDataSac(obsSac, startTime - shift, npts);
					double[] synData = cutDataSac(synSac, startTime, npts);

					double[] obsEnvelope = cutEnvelopeSac(obsSac, startTime - shift, npts);
					double[] synEnvelope = cutEnvelopeSac(synSac, startTime, npts);
					
					double[] obsHy = cutHySac(obsSac, startTime - shift, npts);
					double[] synHy = cutHySac(synSac, startTime, npts);
					
					double[] obsSpcAmp = cutSpcAmpSac(obsSac, startTime - shift, npts);
					double[] synSpcAmp = cutSpcAmpSac(synSac, startTime, npts);
					
					Complex[] obsFy = cutSpcFySac(obsSac, startTime - shift, npts);
					Complex[] synFy = cutSpcFySac(synSac, startTime, npts);
					
					double[] obsSpcRe = Arrays.stream(obsFy).mapToDouble(Complex::getReal).toArray();
					double[] synSpcRe = Arrays.stream(synFy).mapToDouble(Complex::getReal).toArray();
					
					double[] obsSpcIm = Arrays.stream(obsFy).mapToDouble(Complex::getImaginary).toArray();
					double[] synSpcIm = Arrays.stream(synFy).mapToDouble(Complex::getImaginary).toArray();
					
					//debug
//					Path outpath = Paths.get("tmp.dat");
//					try (PrintWriter pw = new PrintWriter(outpath.toFile())) {
//						for (int i = 0; i < synFy.length; i++) {
//							pw.println(i + " " + synFy[i].getReal() + " " + synFy[i].getImaginary() + " " + synFy[i].abs()*synFy[i].abs());
//						}
//					} catch (IOException e) {
//						e.printStackTrace();
//					}
					
					double correctionRatio = ratio;
					
					Phase[] includePhases = window.getPhases();
					
					obsData = Arrays.stream(obsData).map(d -> d / correctionRatio).toArray();
					BasicID synID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, synData);
					BasicID obsID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, obsData);
					
					obsEnvelope = Arrays.stream(obsEnvelope).map(d -> d / correctionRatio).toArray();
					BasicID synEnvelopeID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, synEnvelope);
					BasicID obsEnvelopeID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, obsEnvelope);
					
					obsHy = Arrays.stream(obsHy).map(d -> d / correctionRatio).toArray();
					BasicID synHyID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, npts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, synHy);
					BasicID obsHyID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, npts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, obsHy);
					
					int fnpts = synSpcAmp.length;
					
					obsSpcAmp = Arrays.stream(obsSpcAmp).map(d -> d - Math.log(correctionRatio)).toArray();
					BasicID synSpcAmpID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, synSpcAmp);
					BasicID obsSpcAmpID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, obsSpcAmp);
					
					obsSpcRe = Arrays.stream(obsSpcRe).map(d -> d / correctionRatio).toArray();
					BasicID synSpcReID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, synSpcRe);
					BasicID obsSpcReID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, obsSpcRe);
					
					obsSpcIm = Arrays.stream(obsSpcIm).map(d -> d / correctionRatio).toArray();
					BasicID synSpcImID = new BasicID(WaveformType.SYN, finalSamplingHz, startTime, fnpts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, synSpcIm);
					BasicID obsSpcImID = new BasicID(WaveformType.OBS, finalSamplingHz, startTime - shift, fnpts, station, id,
							component, minPeriod, maxPeriod, includePhases, 0, convolute, obsSpcIm);
					
					try {
						dataWriter.addBasicID(obsID);
						dataWriter.addBasicID(synID);
						envelopeWriter.addBasicID(obsEnvelopeID);
						envelopeWriter.addBasicID(synEnvelopeID);
						hyWriter.addBasicID(obsHyID);
						hyWriter.addBasicID(synHyID);
						spcAmpWriter.addBasicID(obsSpcAmpID);
						spcAmpWriter.addBasicID(synSpcAmpID);
						spcReWriter.addBasicID(obsSpcReID);
						spcReWriter.addBasicID(synSpcReID);
						spcImWriter.addBasicID(obsSpcImID);
						spcImWriter.addBasicID(synSpcImID);
						numberOfPairs.incrementAndGet();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			System.err.print(".");
		}
	}

	/**
	 * ID for static correction and time window information Default is station
	 * name, global CMT id, component.
	 */
	private BiPredicate<StaticCorrection, TimewindowInformation> isPair = (s,
			t) -> s.getStation().equals(t.getStation()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
					&& s.getComponent() == t.getComponent() && t.getStartTime() < s.getSynStartTime() + 1.01 && t.getStartTime() > s.getSynStartTime() - 1.01;
	
	private BiPredicate<StaticCorrection, TimewindowInformation> isPair2 = (s,
			t) -> s.getStation().equals(t.getStation()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
					&& s.getComponent() == t.getComponent();
			
	private BiPredicate<StaticCorrection, TimewindowInformation> isPair_isotropic = (s,
			t) -> s.getStation().equals(t.getStation()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
				&& (t.getComponent() == SACComponent.R ? s.getComponent() == SACComponent.T : s.getComponent() == t.getComponent()) 
				&& t.getStartTime() < s.getSynStartTime() + 1.01 && t.getStartTime() > s.getSynStartTime() - 1.01;

	private BiPredicate<StaticCorrection, TimewindowInformation> isPair_record = (s,
			t) -> s.getStation().equals(t.getStation()) && s.getGlobalCMTID().equals(t.getGlobalCMTID())
					&& s.getComponent() == t.getComponent();
			
	private StaticCorrection getStaticCorrection(TimewindowInformation window) {
		List<StaticCorrection> corrs = staticCorrectionSet.stream().filter(s -> isPair_record.test(s, window)).collect(Collectors.toList());
		if (corrs.size() > 1)
			throw new RuntimeException("Found more than 1 static correction for window " + window);
		if (corrs.size() == 0)
			throw new RuntimeException("Found no static correction for window " + window);
		return corrs.get(0);
	}
	
	private StaticCorrection getMantleCorrection(TimewindowInformation window) {
		List<StaticCorrection> corrs = mantleCorrectionSet.stream().filter(s -> isPair_record.test(s, window)).collect(Collectors.toList());
		if (corrs.size() > 1)
			throw new RuntimeException("Found more than 1 mantle correction for window " + window);
		if (corrs.size() == 0)
			throw new RuntimeException("Found no mantle correction for window " + window);
		return corrs.get(0);
	}

	private double[] cutDataSac(SACData sac, double startTime, int npts) {
		Trace trace = sac.createTrace();
		int step = (int) (sacSamplingHz / finalSamplingHz);
		int startPoint = trace.getNearestXIndex(startTime);
		double[] waveData = trace.getY();
		return IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray();
	}
	
	private double[] cutEnvelopeSac(SACData sac, double startTime, int npts) {
		Trace trace = sac.createTrace();
		int step = (int) (sacSamplingHz / finalSamplingHz);
		int startPoint = trace.getNearestXIndex(startTime);
		HilbertTransform hilbert = new HilbertTransform(trace.getY());
		double[] waveData = hilbert.getEnvelope();
		return IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray();
	}
	
	private double[] cutHySac(SACData sac, double startTime, int npts) {
		Trace trace = sac.createTrace();
		int step = (int) (sacSamplingHz / finalSamplingHz);
		int startPoint = trace.getNearestXIndex(startTime);
		HilbertTransform hilbert = new HilbertTransform(trace.getY());
		double[] waveData = hilbert.getHy();
		return IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray();
	}
	
	private final double fStart = 0.;
	
	private final double fEnd = 0.2;
	
	private int finalFreqSamplingHz = 8;
	
	private double[] cutSpcAmpSac(SACData sac, double startTime, int npts) {
		Trace trace = sac.createTrace();
		int step = (int) (sacSamplingHz / finalSamplingHz);
		int startPoint = trace.getNearestXIndex(startTime);
		double[] cutY = trace.getYVector().getSubVector(startPoint, npts * step).toArray();
		FourierTransform fourier = new FourierTransform(cutY, finalFreqSamplingHz);
		double df = fourier.getFreqIncrement(sacSamplingHz);
		if (fEnd > sacSamplingHz)
			throw new RuntimeException("f1 must be <= sacSamplingHz");
		int fnpts = (int) ((fEnd - fStart) / df);
		double[] spcAmp = fourier.getLogA();
		return IntStream.range(0, fnpts).parallel().mapToDouble(i -> spcAmp[i]).toArray();
	}
	
	private Complex[] cutSpcFySac(SACData sac, double startTime, int npts) {
		Trace trace = sac.createTrace();
		int step = (int) (sacSamplingHz / finalSamplingHz);
		int startPoint = trace.getNearestXIndex(startTime);
		double[] cutY = trace.getYVector().getSubVector(startPoint, npts * step).toArray();
		FourierTransform fourier = new FourierTransform(cutY, finalFreqSamplingHz);
		double df = fourier.getFreqIncrement(sacSamplingHz);
		if (fEnd > sacSamplingHz)
			throw new RuntimeException("f1 must be <= sacSamplingHz");
		int fnpts = (int) ((fEnd - fStart) / df);
		Complex[] Fy = fourier.getFy();
		return IntStream.range(0, fnpts).parallel().mapToObj(i -> Fy[i])
				.collect(Collectors.toList()).toArray(new Complex[0]);
	}
	
	private final double noisePower = 1.;
	
	private double[] cutDataSacAddNoise(SACData sac, double startTime, int npts) {
		Trace trace = sac.createTrace();
		int step = (int) (sacSamplingHz / finalSamplingHz);
		int startPoint = trace.getNearestXIndex(startTime);
		double[] waveData = trace.getY();
		RealVector vector = new ArrayRealVector(IntStream.range(0, npts).parallel().mapToDouble(i -> waveData[i * step + startPoint]).toArray());
		Trace tmp = createNoiseTrace(vector.getLInfNorm());
		Trace noiseTrace = new Trace(trace.getX(), Arrays.copyOf(tmp.getY(), trace.getLength()));
//		System.out.println(noiseTrace.getLength() + " " + trace.getLength() + " " + sac.getValue(SACHeaderEnum.NPTS) + " " + sac.getValue(SACHeaderEnum.DELTA));
		trace = trace.add(noiseTrace);
		double[] waveDataNoise = trace.getY();
		return IntStream.range(0, npts).parallel().mapToDouble(i -> waveDataNoise[i * step + startPoint]).toArray();
	}
	
	private Trace createNoiseTrace(double normalize) {
		double maxFreq = 0.05;
		double minFreq = 0.01;
		int np = 6;
		ButterworthFilter bpf = new BandPassFilter(2 * Math.PI * 0.05 * maxFreq, 2 * Math.PI * 0.05 * minFreq, np);
		Trace tmp = RandomNoiseMaker.create(1., sacSamplingHz, 3276.8, 512);
		double[] u = tmp.getY();
		RealVector uvec = new ArrayRealVector(bpf.applyFilter(u));
		return new Trace(tmp.getX(), uvec.mapMultiply(noisePower * normalize / uvec.getLInfNorm()).toArray());
	}

	@Override
	public Properties getProperties() {
		return (Properties) property.clone();
	}

	@Override
	public Path getWorkPath() {
		return workPath;
	}

}
