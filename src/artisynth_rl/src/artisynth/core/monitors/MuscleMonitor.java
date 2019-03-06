package artisynth.core.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import artisynth.core.materials.AxialMuscleMaterial;
import artisynth.core.materials.MasoudMillardLAM;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.modelbase.MonitorBase;
import maspack.matrix.Vector3d;

public class MuscleMonitor extends MonitorBase {

	private Muscle myMuscle = new Muscle();
	private PrintStream out;

	public MuscleMonitor(Muscle as) {
		myMuscle = as;
	}

	public Muscle getMyMuscle() {
		return myMuscle;
	}

	public void setMyMuscle(Muscle myMuscle) {
		this.myMuscle = myMuscle;
	}

	public void openFile(File f) throws FileNotFoundException {
		out = new PrintStream(f);
		writeHeader();
	}

	public void closeFile() {
		writeFooter();
		out.close();
	}

	public void writeHeader() {

	}

	public void writeFooter() {

	}

	@Override
	public void apply(double t0, double t1) {
		if (out != null) {
			double E = myMuscle.getNetExcitation();
			Vector3d PF = myMuscle.getPassiveForce();
			Vector3d F = myMuscle.getForce();
			double Fnorm = myMuscle.getForceNorm();
			double PFnorm = myMuscle.getPassiveForceNorm();
			double l = myMuscle.getLength();
			double ldot = myMuscle.getLengthDot();
			double lnod = myMuscle.getRestLength();
			AxialMuscleMaterial mat = (AxialMuscleMaterial) myMuscle
					.getMaterial(); // not

			// the best way
			double optLength = mat.getOptLength();
			double tendonRatio = mat.getTendonRatio();
			double fiberLen = l - optLength * tendonRatio;
			double normFiberLength = fiberLen / optLength;
			normFiberLength = ((MasoudMillardLAM) mat).getNormFiberLen();

			out.println(t0 + " 2 " + E + " 4 " + Fnorm * 1000 + " 6 "
					+ PFnorm * 1000 + " 8 " + l + " 10 " + fiberLen + " 12 "
					+ optLength + " 14 " + normFiberLength + " 16 " + ldot
					+ " 18 " + F + " 22 " + PF + " 26 " + lnod);
		}
	}
}