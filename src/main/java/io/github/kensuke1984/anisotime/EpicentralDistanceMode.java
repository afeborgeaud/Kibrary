/**
 * 
 */
package io.github.kensuke1984.anisotime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Epicentral distance mode.
 * 
 * TODO Catalog share
 * TODO P-SV SH
 * 
 * @author Kensuke Konishi
 * @version 0.2.1b
 * 
 */
class EpicentralDistanceMode extends Computation {

	private VelocityStructure structure;
	private double eventR;
	private Phase[] targetPhases;
	/**
	 * [rad]
	 */
	private double epicentralDistance;

	private final RaypathCatalog catalog;

	/**
	 * @param travelTimeTool
	 *            parent
	 * @param targetPhases
	 *            Array of PhaseNames
	 * @param epicentralDistance
	 *            target delta [rad]
	 * @param structure
	 *            structure
	 * @param eventR
	 *            radius (not depth) [km]
	 */
	EpicentralDistanceMode(ANISOtime travelTimeTool, Phase[] targetPhases, double epicentralDistance,
			VelocityStructure structure, double eventR) {
		super(travelTimeTool);
		this.structure = structure;
		this.eventR = eventR;
		this.epicentralDistance = epicentralDistance;
		this.targetPhases = targetPhases;
		// TODO mmesh deltaR
		this.catalog = RaypathCatalog.computeCatalogue(structure, ComputationalMesh.simple(), 10);
	}

	@Override
	public void run() {
		int polarization = travelTimeTool.getPolarization();
		boolean psv = false;
		boolean sh = false;
		switch (polarization) {
		case 0:
			psv = true;
			sh = true;
			break;
		case 1:
			psv = true;
			break;
		case 2:
			sh = true;
			break;
		default:
			throw new RuntimeException("Unexpected happens");
		}
		raypaths = new ArrayList<>();
		phases = new ArrayList<>();

		double deltaR = 10; // TODO
		for (Phase phase : targetPhases) {
			Raypath[] raypaths = catalog.searchPath( phase, eventR, epicentralDistance);
			if (raypaths.length == 0)
				continue;
			for (int i = 0; i < raypaths.length; i++) {
				this.raypaths.add(raypaths[i]);
				phases.add(phase);
			}

		}
		for (int i = 0; i < phases.size(); i++) {
			Phase phase = phases.get(i);
			if (!phase.isDiffracted())
				continue;
			Raypath raypath = raypaths.get(i);
			double delta = raypath.computeDelta(eventR, phase);
			double dDelta = Math.toDegrees(epicentralDistance - delta);
			phases.set(i, Phase.create(phase.toString() + dDelta));
		}
		int n = raypaths.size();
		// System.out.println("Whats done is done");
		double[] delta = new double[n];
		Arrays.fill(delta, epicentralDistance);
		showResult(delta, raypaths, phases);

	}

	@Override
	ComputationMode getMode() {
		return ComputationMode.EPICENTRAL_DISTANCE;
	}

}
