package artisynth.models.lumbarSpine;

import java.awt.Color;
import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Random;

import javax.swing.JLabel;
import javax.swing.JSeparator;

import maspack.function.Function1x3;
import maspack.matrix.AxisAngle;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.RotationMatrix3d;
import maspack.matrix.Vector3d;
import maspack.matrix.VectorNd;
import maspack.properties.Property;
import maspack.properties.PropertyList;
import maspack.render.RenderProps;
import maspack.render.Renderer.LineStyle;
import maspack.render.Renderer.PointStyle;
import artisynth.core.driver.Main;
import artisynth.core.gui.ControlPanel;
import artisynth.core.inverse.MotionTargetTerm;
import artisynth.core.inverse.TargetPoint;
import artisynth.core.inverse.RlTrackingController;
import artisynth.core.inverse.TrackingController;
import artisynth.core.mechmodels.AxialSpring;
import artisynth.core.mechmodels.Frame;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.FrameSpring;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MultiPointMuscle;
import artisynth.core.mechmodels.MultiPointSpring;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.Point;
import artisynth.core.mechmodels.PointForce;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.mechmodels.MotionTarget.TargetActivity;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.Monitor;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.probes.NumericOutputProbe;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.RootModel;
import artisynth.models.lumbarSpine.OldLumbarSpine.FrameMarkerMonitor;
import artisynth.models.lumbarSpine.OldLumbarSpine.FrameSpringMonitor;
import artisynth.models.lumbarSpine.OldLumbarSpine.MultiPointMuscleMonitor;
import artisynth.models.lumbarSpine.OldLumbarSpine.MuscleExciterMonitor;
import artisynth.models.lumbarSpine.OldLumbarSpine.MuscleMonitor;
import artisynth.models.lumbarSpine.OldLumbarSpine.RigidBodyMonitor;
import artisynth.models.rl.InverseModel;

public class RlLumbarSpine extends LumbarSpine implements InverseModel {

	protected int DEAFULT_PORT = 8097;
	protected double MOTION_RANGE = 2;

	protected double xPosition;
	protected double yPosition;
	protected double zPosition;
	protected Vector3d rightTargetRefPosition;
	protected Vector3d leftTargetRefPosition;
	protected Vector3d rightTargetRealPosition;
	protected Vector3d leftTargetRealPosition;
	protected double error_r;
	protected double error_l;
	public ControlPanel myTargetPositionerPanel;
	SpineRandomTargetController sRandomController;

	public static PropertyList myProps = new PropertyList(RlLumbarSpine.class, LumbarSpine.class);

	public PropertyList getAllPropertyInfo() {
		return myProps;
	}

	static {
		myProps.add("xPosition * *", "x position of target", 0.0, "[-0.10,0.10] NW");
		myProps.add("yPosition * *", "y position of target", 0.0, "[-0.05,0.05] NW");
		myProps.add("zPosition * *", "z position of target", 0.0, "[-0.1,0.1] NW");
		myProps.add("rightTargetRefPosition * *", "reference position of the right target", null, "%.8g");
		myProps.add("leftTargetRefPosition * *", "reference position of the left target", null, "%.8g");
		myProps.addReadOnly("rightTargetRealPosition *", "real position of the right target point");
		myProps.addReadOnly("leftTargetRealPosition *", "real position of the left target point");
		myProps.addReadOnly("error_r *", "motion target error on right side");
		myProps.addReadOnly("error_l *", "motion target error on left side");
	}

	public RlLumbarSpine() {
	}

	public RlLumbarSpine(String name) {
		super(name);
		addMonitors();
		setXPosition(0);
		setYPosition(0);
		setZPosition(0);
		setRightTargetRefPosition(new Vector3d(-0.10437719, 1.2741631, 0.0999726));
		setLeftTargetRefPosition(new Vector3d(-0.10437719, 1.2741631, -0.1000274));
		setRightTargetRealPosition(new Vector3d(-0.098877193, 1.2771631, 0.0999726));
		setLeftTargetRealPosition(new Vector3d(-0.098877193, 1.2771631, -0.1000274));
	}

	// ********************************************************
	// ******************* Adding ATTACH DRIVER****************
	// ********************************************************
	public void attach(DriverInterface driver) {
		super.attach(driver);

		addSimpleInverseSolver();
		// addSpineSimpleTargetController ();
		loadOutPutProbes();
		addTargetPositionerControlPanel();
		// adjustControlPanelLocations ();
	}

	public void loadOutPutProbes() {

		// addTargetRealPositionOutPutProbe ();
		addInverseMuscleExcitations();
	}

	public void addTargetRealPositionOutPutProbe() {
		NumericOutputProbe nop = new NumericOutputProbe();
		nop.setName("TargetRealPosition");
		Property[] props = new Property[2];
		props[0] = mech.frameMarkers().get("ribcageMotionTarget_r").getProperty("position");
		props[1] = mech.frameMarkers().get("ribcageMotionTarget_l").getProperty("position");
		nop.setOutputProperties(props);
		nop.setAttachedFileName(ArtisynthPath.getSrcRelativePath(InvLumbarSpineLiftingWeight13.class,
				"outputProbes/" + "ribcageMotionTarget_position"));
		nop.setStartStopTimes(0, 15);
		addOutputProbe(nop);
	}

	public void addInverseMuscleExcitations() {

		NumericOutputProbe nop = new NumericOutputProbe();
		nop.setName("InverseMuscleExcitations");

		Property[] props = new Property[mex.size()];
		for (int i = 0; i < mex.size(); i++) {
			String exname = mex.get(i).getName();
			props[i] = mech.getMuscleExciters().get(exname).getProperty("excitation");
		}

		nop.setOutputProperties(props);
		nop.setAttachedFileName(ArtisynthPath.getSrcRelativePath(InvLumbarSpineLiftingWeight13.class,
				"outputProbes/" + "InverseMuscleExcitations.txt"));
		nop.setStartStopTimes(0, 15);
		nop.setActive(false);
		addOutputProbe(nop);
	}

	// ********************************************************
	// ******************* Adding Control Panels **************
	// ********************************************************
	public void addTargetPositionerControlPanel() {

		myTargetPositionerPanel = new ControlPanel("TargetPositioner", "LiveUpdate");
		myTargetPositionerPanel.addWidget(new JLabel("Adjusting Target Position:"));
		myTargetPositionerPanel.addWidget("    x Position", this, "xPosition");
		myTargetPositionerPanel.addWidget("    y Position", this, "yPosition");
		myTargetPositionerPanel.addWidget("    z Position", this, "zPosition");

		myTargetPositionerPanel.addWidget(new JSeparator());
		myTargetPositionerPanel.addWidget(new JLabel("Target Reference Position (prescribed to the model):"));
		myTargetPositionerPanel.addWidget("    target_r", this, "rightTargetRefPosition");
		myTargetPositionerPanel.addWidget("    target_l", this, "leftTargetRefPosition");

		myTargetPositionerPanel.addWidget(new JSeparator());
		myTargetPositionerPanel.addWidget(new JLabel("Target Real Position (produced by the model):"));
		myTargetPositionerPanel.addWidget("    target_r", this, "rightTargetRealPosition");
		myTargetPositionerPanel.addWidget("    target_l", this, "leftTargetRealPosition");

		myTargetPositionerPanel.addWidget(new JSeparator());
		myTargetPositionerPanel.addWidget("    right motion error", this, "error_r");
		myTargetPositionerPanel.addWidget("    left motion error", this, "error_l");
		myTargetPositionerPanel.addWidget(new JSeparator());

		myTargetPositionerPanel.setScrollable(false);

		Dimension framespringsforcesD = getControlPanels().get("FrameSpringsForces").getSize();
		java.awt.Point loc = getControlPanels().get("FrameSpringsForces").getLocation();
		myTargetPositionerPanel.setLocation(loc.x, loc.y + framespringsforcesD.height);
		addControlPanel(myTargetPositionerPanel);
	}

	public void adjustControlPanelLocations() {
		ControlPanel cp;
		int visibilityHeight;
		cp = getControlPanels().get("visibility");
		Dimension visibilityD = cp.getSize();
		java.awt.Point loc = getMainFrame().getLocation();

		cp = getControlPanels().get("TargetPositioner");
		cp.setLocation(loc.x + visibilityD.width, loc.y);
		visibilityHeight = visibilityD.height;

		cp = getControlPanels().get("FrameSpringsForces");
		cp.setLocation(loc.x + visibilityD.width, loc.y + visibilityHeight);
	}

	public void setXPosition(double x) {
		xPosition = x;
	}

	public double getXPosition() {
		return xPosition;
	}

	public void setYPosition(double y) {
		yPosition = y;
	}

	public double getYPosition() {
		return yPosition;
	}

	public void setZPosition(double z) {
		zPosition = z;
	}

	public double getZPosition() {
		return zPosition;
	}

	public void setRightTargetRefPosition(Vector3d v) {
		rightTargetRefPosition = v;
	}

	public Vector3d getRightTargetRefPosition() {
		return rightTargetRefPosition;
	}

	public void setLeftTargetRefPosition(Vector3d v) {
		leftTargetRefPosition = v;
	}

	public Vector3d getLeftTargetRefPosition() {
		return leftTargetRefPosition;
	}

	public void setRightTargetRealPosition(Vector3d v) {
		rightTargetRealPosition = v;
	}

	public Vector3d getRightTargetRealPosition() {
		return rightTargetRealPosition;
	}

	public void setLeftTargetRealPosition(Vector3d v) {
		leftTargetRealPosition = v;
	}

	public Vector3d getLeftTargetRealPosition() {
		return leftTargetRealPosition;
	}

	public double getError_r() {
		return error_r;
	}

	public double getError_l() {
		return error_l;
	}

	// ********************************************************
	// ******************* Adding Monitors ********************
	// ********************************************************
	public void addMonitors() {
		for (FrameSpring fs : mech.frameSprings()) {
			FrameSpringMonitor mon = new FrameSpringMonitor(fs);
			mon.setName(fs.getName() + "_monitor");
			addMonitor(mon);
		}

		for (RigidBody rb : mech.rigidBodies()) {
			RigidBodyMonitor mon = new RigidBodyMonitor(rb);
			mon.setName(rb.getName() + "_monitor");
			addMonitor(mon);
		}

		for (String str : bodyMassCenters) {
			FrameMarkerMonitor mon = new FrameMarkerMonitor(mech.frameMarkers().get(str));
			mon.setName(str + "_monitor");
			addMonitor(mon);
		}

		for (AxialSpring as : mech.axialSprings()) {
			if (as instanceof Muscle) {
				MuscleMonitor mon = new MuscleMonitor((Muscle) as);
				mon.setName(as.getName() + "_monitor");
				addMonitor(mon);
			}
		}

		for (MultiPointSpring mps : mech.multiPointSprings()) {
			if (mps instanceof MultiPointSpring) {
				MultiPointMuscleMonitor mon = new MultiPointMuscleMonitor((MultiPointMuscle) mps);
				mon.setName(mps.getName() + "_monitor");
				addMonitor(mon);
			}
		}

		for (MuscleExciter mex : this.mex) {
			MuscleExciterMonitor mon = new MuscleExciterMonitor(mex);
			mon.setName(mex.getName() + "_ex_monitor");
			addMonitor(mon);
		}
	}

	// ********************************************************
	// ******************* StepAdjustment Advance *************
	// ********************************************************
	@Override
	public StepAdjustment advance(double t0, double t1, int flags) {

		StepAdjustment ret = super.advance(t0, t1, flags);
		double tStart = 0;
		double tEnd = 100;

		if (t0 == tStart) {
			for (Monitor mon : getMonitors()) {

				if (mon instanceof FrameSpringMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/FrameSpringMonitor/" + mon.getName() + ".m");
					//System.out.println(fileName);
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((FrameSpringMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof RigidBodyMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/RigidBodyMonitor/" + mon.getName() + ".m");
					//System.out.println(fileName);
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((RigidBodyMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof FrameMarkerMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/FrameMarkerMonitor/" + mon.getName() + ".m");
					//System.out.println(fileName);
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((FrameMarkerMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MuscleMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MuscleMonitor/" + mon.getName() + ".m");
					//System.out.println(fileName);
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MuscleMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MultiPointMuscleMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MultiPointMuscleMonitor/" + mon.getName() + ".m");
					//System.out.println(fileName);
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MultiPointMuscleMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MuscleExciterMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MuscleExciterMonitor/" + mon.getName() + ".m");
					//System.out.println(fileName);
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MuscleExciterMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof LevelMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/LevelMonitor/" + mon.getName() + ".m");
					//System.out.println(fileName);
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((LevelMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName + "' (" + e.getMessage() + ")");
					}
				}
			}
		} else if (t0 == tEnd) {
			for (Monitor mon : getMonitors()) {
				if (mon instanceof FrameSpringMonitor) {
					((FrameSpringMonitor) mon).closeFile();
				}
				if (mon instanceof RigidBodyMonitor) {
					((RigidBodyMonitor) mon).closeFile();
				}
				if (mon instanceof FrameMarkerMonitor) {
					((FrameMarkerMonitor) mon).closeFile();
				}
				if (mon instanceof MuscleMonitor) {
					((MuscleMonitor) mon).closeFile();
				}
				if (mon instanceof MultiPointMuscleMonitor) {
					((MultiPointMuscleMonitor) mon).closeFile();
				}
				if (mon instanceof MuscleExciterMonitor) {
					((MuscleExciterMonitor) mon).closeFile();
				}
				if (mon instanceof LevelMonitor) {
					((LevelMonitor) mon).closeFile();
				}
			}
		}
		return ret;
	}

	// ********************************************************
	// ******************* Adding Controllers ****************
	// ********************************************************

	public void addSimpleInverseSolver() {

		// Tracker t = new Tracker (mech, "spineTracker");
		RlTrackingController t = new RlTrackingController(mech, (InverseModel) this, "spineTracker", DEAFULT_PORT);
		t.addMotionTarget(mech.frameMarkers().get("ribcageMotionTarget_r"));
		t.addMotionTarget(mech.frameMarkers().get("ribcageMotionTarget_l"));
		t.setUseTrapezoidalSolver(1);
		t.setKeepVelocityJacobianConstant(true);
		MotionTargetTerm mt = t.getMotionTargetTerm();
		// mt.setTargetWeights(new VectorNd(new double []{1, 0, 1,1, 0, 1}));
		mt.setWeight(2.5);

		mt.setKd(0.25); // Peter's edit

		for (MuscleExciter m : mex) {
			t.addExciter(m);
		}

		ArrayList<MotionTargetComponent> targets = t.getMotionTargets();

		RenderProps rp1 = new RenderProps();
		rp1.setPointStyle(PointStyle.SPHERE);
		rp1.setPointRadius(0.03);
		rp1.setPointColor(Color.CYAN);

		RenderProps rp2 = new RenderProps(rp1);
		rp2.setPointColor(Color.RED);
		t.setMotionRenderProps(rp1, rp2);

		sRandomController = new SpineRandomTargetController((Point) targets.get(0), (Point) targets.get(1));

		addController(sRandomController);
		addController(t);
		// t.createPanel (this);
	}

	// amirabdi
	public void resetTargetPosition() {
		sRandomController.reset = true;
	}

	public class SpineRandomTargetController extends ControllerBase {

		private Point myp1;
		private Point myp2;
		private Point3d ipos1;
		private Point3d ipos2;

		public Boolean reset = false;

		// private ArrayList<Frame> initFrame;
		public SpineRandomTargetController(Point p1, Point p2) {
			myp1 = p1;
			myp2 = p2;
			ipos1 = new Point3d(p1.getPosition());
			ipos2 = new Point3d(p2.getPosition());
		}

		public void apply(double t0, double t1) {
			// Neutral with Crate
			if (t0 > -1 && reset) {
				reset = false;

				Random r = new Random();
				double xPosition = r.nextDouble() * MOTION_RANGE;
				double yPosition = r.nextDouble() * MOTION_RANGE;
				double zPosition = r.nextDouble() * MOTION_RANGE;

				Point3d pos1 = new Point3d(getRightTargetRefPosition().x, getRightTargetRefPosition().y,
						getRightTargetRefPosition().z);
				pos1.add(new Vector3d(xPosition, yPosition, zPosition));
				myp1.setPosition(pos1);

				Point3d pos2 = new Point3d(getLeftTargetRefPosition().x, getLeftTargetRefPosition().y,
						getLeftTargetRefPosition().z);
				pos2.add(new Vector3d(xPosition, yPosition, zPosition));
				myp2.setPosition(pos2);

				// Tracing target real position
				setRightTargetRealPosition(mech.frameMarkers().get("ribcageMotionTarget_r").getPosition());
				setLeftTargetRealPosition(mech.frameMarkers().get("ribcageMotionTarget_l").getPosition());

				// Post Processing
				Point3d er_r = new Point3d();
				Point3d er_l = new Point3d();
				er_r.sub(myp1.getPosition(), mech.frameMarkers().get("ribcageMotionTarget_r").getPosition());
				er_l.sub(myp2.getPosition(), mech.frameMarkers().get("ribcageMotionTarget_l").getPosition());

				error_r = er_r.norm();
				error_l = er_l.norm();
			}

		}
	}

	// Overriding addExciters class
	@Override
	public void addExciters() {
		// addGroupedMex ();
		addMyFavoriteMex();
	}

}
