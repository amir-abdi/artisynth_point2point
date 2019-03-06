package artisynth.core.monitors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import artisynth.core.mechmodels.FrameSpring;
import artisynth.core.modelbase.MonitorBase;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.Vector3d;
import maspack.spatialmotion.Wrench;

public class FrameSpringMonitor extends MonitorBase {

	private FrameSpring myFrameSpring;
	private PrintStream out;
	public Vector3d FSAxialForces = new Vector3d();

	public FrameSpringMonitor(FrameSpring fs) {
		myFrameSpring = fs;
	}

	public FrameSpring getFrameSpring() {
		return myFrameSpring;
	}

	public void setFrameSpring(FrameSpring fs) {
		myFrameSpring = fs;
	}

	public void openFile(File f) throws FileNotFoundException {
		out = new PrintStream(f);
		writeHeader();
	}

	public void closeFile() {
		writeFooter();
		out.close();
	}

	private void writeHeader() {
	}

	private void writeFooter() {
	}

	@Override
	public void apply(double t0, double t1) {
		if (out != null) {
			// see FrameSpring.applyForces(...) for force computation
			RigidTransform3d X21 = new RigidTransform3d();
			X21.mulInverseBoth(myFrameSpring.getAttachFrameA(),
					myFrameSpring.getFrameA().getPose());
			X21.mul(myFrameSpring.getFrameB().getPose());
			X21.mul(myFrameSpring.getAttachFrameB());

			RigidTransform3d X1W = new RigidTransform3d();
			X1W.mul(myFrameSpring.getFrameA().getPose(),
					myFrameSpring.getAttachFrameA());
			Vector3d FSAxis = new Vector3d(X21.p);
			FSAxis.transform(X1W.R);
			FSAxis.normalize();

			Wrench Fcopy = new Wrench(myFrameSpring.getSpringForce());

			double FAxialMag = Fcopy.f.dot(FSAxis);
			Vector3d FAxial = new Vector3d();
			FAxial.scale(FAxialMag, FSAxis);
			Vector3d FShear = new Vector3d(Fcopy.f);
			FShear.sub(FAxial);
			double FShearMag = FShear.norm();
			FSAxialForces.set(FAxialMag, Fcopy.m.z, FShearMag);

			// Calculating Kinematic Data
			// Translations
			RigidTransform3d initialX21 = new RigidTransform3d(
					myFrameSpring.getInitialT21());
			Vector3d p = X21.p;
			Vector3d initialp = initialX21.p;
			Vector3d localDelta = new Vector3d((p.x - initialp.x),
					(p.y - initialp.y), (p.z - initialp.z));
			Vector3d globalDelta = new Vector3d(localDelta);

			// Rotations
			globalDelta.transform(X1W.R);

			RigidTransform3d XBW = new RigidTransform3d(
					myFrameSpring.getFrameB().getPose());

			double lsx = X21.R.m21 / X21.R.m22;
			lsx = Math.atan(lsx);
			double lsy = -X21.R.m20;
			lsy = Math.asin(lsy);
			double lsz = X21.R.m10 / X21.R.m00;
			lsz = Math.atan(lsz);

			double gsx = XBW.R.m21 / XBW.R.m22;
			gsx = Math.atan(gsx);
			double gsy = -XBW.R.m20;
			gsy = Math.asin(gsy);
			double gsz = XBW.R.m10 / XBW.R.m00;
			gsz = Math.atan(gsz);

			RigidTransform3d XAW = new RigidTransform3d(
					myFrameSpring.getFrameA().getPose());

			double gsxFrameA = XAW.R.m21 / XAW.R.m22;
			gsxFrameA = Math.atan(gsxFrameA);
			double gsyFrameA = -XAW.R.m20;
			gsyFrameA = Math.asin(gsyFrameA);
			double gszFrameA = XAW.R.m10 / XAW.R.m00;
			gszFrameA = Math.atan(gszFrameA);

			double gsxFrame1 = X1W.R.m11 / X1W.R.m22;
			gsxFrame1 = Math.atan(gsxFrame1);
			double gsyFrame1 = -X1W.R.m20;
			gsyFrame1 = Math.asin(gsyFrame1);
			double gszFrame1 = X1W.R.m10 / X1W.R.m00;
			gszFrame1 = Math.atan(gszFrame1);

			RigidTransform3d X2W = new RigidTransform3d();
			X2W.mul(myFrameSpring.getFrameB().getPose());
			X2W.mul(myFrameSpring.getAttachFrameB());

			double gsxFrame2 = X2W.R.m21 / X2W.R.m22;
			gsxFrame2 = Math.atan(gsxFrame2);
			double gsyFrame2 = -X2W.R.m20;
			gsyFrame2 = Math.asin(gsyFrame2);
			double gszFrame2 = X2W.R.m10 / X2W.R.m00;
			gszFrame2 = Math.atan(gszFrame2);
		}
	}

}