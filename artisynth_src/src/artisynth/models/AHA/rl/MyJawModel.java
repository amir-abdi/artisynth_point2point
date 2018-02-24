package artisynth.models.AHA.rl;

import java.io.IOException;

import artisynth.core.mechmodels.FrameMarker;
import artisynth.models.dynjaw.JawModel;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;


public class MyJawModel extends JawModel{
	public MyJawModel() throws IOException {
		super();
	}

	protected MyJawModel myJawModel;
	public MyJawModel(String name, boolean fixedLaryngeal,
			boolean useComplexJoint, boolean useCurvJoint) throws IOException 
	{
		super(name, fixedLaryngeal, useComplexJoint, useCurvJoint);
	}

	protected RigidTransform3d XCentricRotationRef = new RigidTransform3d();

	public void setJawCentricRotationRef(double jawCentricRotation) {
		this.jawCentricRotation = jawCentricRotation;
		if (XCondyleToWorld == null) {
			FrameMarker condyle = myFrameMarkers.get("ltmj");
			if (condyle == null) return;
			Point3d condylePt = new Point3d(condyle.getLocation()); // body coords
			// ?
			condylePt.x = 0; // assume standard jaw position
			XCondyleToWorld = new RigidTransform3d();
			XCondyleToWorld.p.negate(condylePt);
		}
		XCentricRotation.p.setZero();
		XCentricRotation.R.setAxisAngle(1, 0, 0, Math
				.toRadians(jawCentricRotation));
		XCentricRotation.mul(XCentricRotation, XCondyleToWorld);
		XCentricRotation.mulInverseLeft(XCondyleToWorld, XCentricRotation);

		myRigidBodies.get("ref_jaw").transformGeometry(XCentricRotation);
	}
}
