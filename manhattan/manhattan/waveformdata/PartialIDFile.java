package manhattan.waveformdata;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FilenameUtils;

import filehandling.sac.SACComponent;
import filehandling.spc.PartialType;
import manhattan.globalcmt.GlobalCMTID;
import manhattan.inversion.ElasticParameter;
import manhattan.template.Location;
import manhattan.template.Station;
import manhattan.template.Utilities;

/**
 * Utilities for a pair of an ID file and a waveform file. <br>
 * The files are for partial waveforms.
 * 
 * 
 * The file contains<br>
 * Numbers of stations, events, period ranges and perturbation points<br>
 * Each station information <br>
 * - name, network, position <br>
 * Each event <br>
 * - Global CMT ID Each period<br>
 * Each period range<br>
 * - min period, max period<br>
 * Each perturbation points<br>
 * - latitude, longitude, radius Each PartialID information<br>
 * - see in {@link #readPartialIDFile(Path)}<br>
 * 
 * 
 * @since 2013/12/1 or earlier
 * 
 * @version 0.3
 * 
 * @author Kensuke
 * 
 * 
 */
public final class PartialIDFile {

	public static PartialID[] readPartialIDandDataFile(Path idPath, Path dataPath, Predicate<PartialID> chooser)
			throws IOException {
		PartialID[] ids = readPartialIDFile(idPath);
		long t = System.nanoTime();
		long dataSize = Files.size(dataPath);
		PartialID lastID = ids[ids.length - 1];
		if (dataSize != lastID.startByte + lastID.npts * 8)
			throw new RuntimeException(dataPath + " is not invalid for " + idPath);
		try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(dataPath)))) {
			for (int i = 0; i < ids.length; i++) {
				if (!chooser.test(ids[i])) {
					dis.skipBytes(ids[i].npts * 8);
					ids[i] = null;
					continue;
				}
				double[] data = new double[ids[i].npts];
				for (int j = 0; j < data.length; j++)
					data[j] = dis.readDouble();
				ids[i] = ids[i].setData(data);
			}
		}
		if (chooser != null)
			ids = Arrays.stream(ids).parallel().filter(Objects::nonNull).toArray(n -> new PartialID[n]);
		System.err.println("Partial waveforms are read in " + Utilities.toTimeString(System.nanoTime() - t));
		return ids;
	}

	/**
	 * @param idPath
	 *            {@link Path} of an ID file.
	 * @return Array of {@link PartialID} without waveform data
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static PartialID[] readPartialIDFile(Path idPath) throws IOException {
		try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(idPath)))) {
			long fileSize = Files.size(idPath);
			Station[] stations = new Station[dis.readShort()];
			GlobalCMTID[] cmtIDs = new GlobalCMTID[dis.readShort()];
			double[][] periodRanges = new double[dis.readShort()][2];
			Location[] perturbationLocations = new Location[dis.readShort()];
			int headerBytes = 4 * 2 + 24 * stations.length + 15 * cmtIDs.length + 4 * 2 * periodRanges.length
					+ 4 * 3 * perturbationLocations.length;
			long idParts = fileSize - headerBytes;
			if (idParts % oneIDByte != 0)
				throw new RuntimeException(idPath + " is not valid..");
			// name(8),network(8),position(4*2)
			byte[] stationBytes = new byte[24];
			for (int i = 0; i < stations.length; i++) {
				dis.read(stationBytes);
				stations[i] = Station.createStation(stationBytes);
			}
			byte[] cmtIDBytes = new byte[15];
			for (int i = 0; i < cmtIDs.length; i++) {
				dis.read(cmtIDBytes);
				cmtIDs[i] = new GlobalCMTID(new String(cmtIDBytes).trim());
			}
			for (int i = 0; i < periodRanges.length; i++) {
				periodRanges[i][0] = dis.readFloat();
				periodRanges[i][1] = dis.readFloat();
			}
			for (int i = 0; i < perturbationLocations.length; i++)
			 	perturbationLocations[i] = new Location(dis.readFloat(), dis.readFloat(), dis.readFloat());
			int nid = (int) (idParts / oneIDByte);
			System.err.println("Reading partialID file: " + idPath);
			long t = System.nanoTime();
			byte[][] bytes = new byte[nid][oneIDByte];
			for (int i = 0; i < nid; i++)
				dis.read(bytes[i]);
			PartialID[] ids = new PartialID[nid];
			IntStream.range(0, nid).parallel().forEach(i -> {
				ids[i] = createID(bytes[i], stations, cmtIDs, periodRanges, perturbationLocations);
			});
			System.err
					.println(ids.length + " partial IDs are read in " + Utilities.toTimeString(System.nanoTime() - t));
			return ids;

		}
	}

	/**
	 * Creates lists of stations, events, partials.(if they dont exist) Options:
	 * -a: show all IDs
	 * 
	 * @param args
	 *            [options] [parameter file name]
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static void main(String[] args) throws IOException {
		if (args.length == 1) {
			PartialID[] ids = readPartialIDFile(Paths.get(args[0]));
			// print(Paths.get(args[0]));
			String header = FilenameUtils.getBaseName(Paths.get(args[0]).getFileName().toString());
			outputStations(header, ids);
			outputGlobalCMTID(header, ids);
			outputPerturbationPoints(header, ids);
		} else if (args.length == 2 && args[0].equals("-a")) {
			PartialID[] ids = readPartialIDFile(Paths.get(args[1]));
			Arrays.stream(ids).forEach(System.out::println);
		} else {
			System.out.println("usage:[-a] [id file name]\n if \"-a\", show all IDs");
		}

	}

	private static void outputPerturbationPoints(String header, PartialID[] pids) throws IOException {
		Path outPath = Paths.get(header + ".par");
		if (Files.exists(outPath))
			return;
		List<String> lines = Arrays.stream(pids).parallel()
				.map(id -> new ElasticParameter(id.partialType, id.pointLocation, 1)).distinct()
				.map(ep -> ep.toString()).sorted().collect(Collectors.toList());
		Files.write(outPath, lines);
		System.out.println(outPath + " is created as a list of perturbation. (weighting values are just set 1)");
	}

	private static void outputStations(String header, PartialID[] ids) throws IOException {
		Path outPath = Paths.get(header + ".station");
		if (Files.exists(outPath))
			return;
		List<String> lines = Arrays.stream(ids).parallel().map(id -> id.station).distinct()
				.map(s -> s.getStationName() + " " + s.getNetwork() + " " + s.getPosition())
				.collect(Collectors.toList());
		Files.write(outPath, lines);
		System.out.println(outPath + " is created as a list of stations.");
	}

	/**
	 * File size for an ID
	 */
	public static final int oneIDByte = 30;

	/**
	 * An ID information contains<br>
	 * station number(2)<br>
	 * event number(2)<br>
	 * component(1)<br>
	 * period range(1) <br>
	 * start time(4)<br>
	 * number of points(4)<br>
	 * sampling hz(4) <br>
	 * convoluted(or observed) or not(1)<br>
	 * position of a waveform for the ID in the datafile(8)<br>
	 * type of partial(1)<br>
	 * point of perturbation(2)
	 * 
	 * @param bytes
	 *            for one ID
	 * @return an ID written in the bytes
	 */
	private static PartialID createID(byte[] bytes, Station[] stations, GlobalCMTID[] ids, double[][] periodRanges,
			Location[] perturbationLocations) {
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		Station station = stations[bb.getShort()];
		GlobalCMTID eventID = ids[bb.getShort()];
		SACComponent component = SACComponent.getComponent(bb.get());
		double[] period = periodRanges[bb.get()];
		double startTime = bb.getFloat(); // starting time
		int npts = bb.getInt(); // データポイント数
		double samplingHz = bb.getFloat();
		boolean isConvolved = 0 < bb.get();
		long startByte = bb.getLong();
		PartialType partialType = PartialType.getType(bb.get());
		Location perturbationLocation = perturbationLocations[bb.getShort()];
		PartialID id = new PartialID(station, eventID, component, samplingHz, startTime, npts, period[0], period[1],
				startByte, isConvolved, perturbationLocation, partialType);
		return id;
	}

	public static PartialID[] readPartialIDandDataFile(Path idPath, Path dataPath) throws IOException {
		return readPartialIDandDataFile(idPath, dataPath, id -> true);
	}

	private PartialIDFile() {
	}

	private static void outputGlobalCMTID(String header, PartialID[] ids) throws IOException {
		Path outPath = Paths.get(header + ".globalCMTID");
		if (Files.exists(outPath))
			return;
		List<String> lines = Arrays.stream(ids).parallel().map(id -> id.globalCMTID.toString()).distinct().sorted()
				.collect(Collectors.toList());
		Files.write(outPath, lines);
		System.out.println(outPath + " is created as a list of global CMT IDs.");
	}

}
