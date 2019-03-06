package artisynth.core.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import artisynth.core.materials.AxialMuscleMaterial;
import artisynth.core.materials.MasoudMillardLAM;
import artisynth.core.mechmodels.MultiPointMuscle;
import artisynth.core.modelbase.MonitorBase;

public class MultiPointMuscleMonitor extends MonitorBase {

	private MultiPointMuscle myMuscle = new MultiPointMuscle();
	private PrintStream out;

	public MultiPointMuscleMonitor(MultiPointMuscle as) {
		myMuscle = as;
	}

	public MultiPointMuscle getMyMuscle() {
		return myMuscle;
	}

	public void setMyMuscle(MultiPointMuscle myMuscle) {
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
			double Fnorm = myMuscle.getForceNorm();
			double PFnorm = myMuscle.getPassiveForceNorm();
			double l = myMuscle.getLength();
			double ldot = myMuscle.getLengthDot();
			AxialMuscleMaterial mat = (AxialMuscleMaterial) myMuscle
					.getMaterial(); // not
			// the
			// best
			// way
			double optLength = mat.getOptLength();
			double tendonRatio = mat.getTendonRatio();
			double fiberLen = l - optLength * tendonRatio;
			double normFiberLength = fiberLen / optLength;
			double lnod = myMuscle.getRestLength();
			normFiberLength = ((MasoudMillardLAM) mat).getNormFiberLen();

			out.println(t0 + " 2 " + E + " 4 " + Fnorm + " 6 " + PFnorm
					+ " 8 " + l + " 10 " + fiberLen + " 12 " + optLength
					+ " 14 " + normFiberLength + " 16 " + ldot + " 18 "
					+ lnod);
		}
	}
}