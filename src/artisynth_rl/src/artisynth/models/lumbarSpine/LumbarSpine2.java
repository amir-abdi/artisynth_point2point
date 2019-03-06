package artisynth.models.lumbarSpine;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;

import maspack.function.Function1x3;
import maspack.geometry.MeshFactory;
import maspack.geometry.PolygonalMesh;
import maspack.matrix.AffineTransform3d;
import maspack.matrix.AxisAngle;
import maspack.matrix.Matrix3d;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.Vector3d;
import maspack.properties.PropertyList;
import maspack.render.RenderProps;
import maspack.render.Renderer.LineStyle;
import maspack.render.Renderer.PointStyle;
import maspack.render.Renderer.Shading;
import maspack.spatialmotion.Wrench;
import artisynth.core.gui.ControlPanel;
import artisynth.core.inverse.TargetPoint;
import artisynth.core.inverse.TrackingController;
import artisynth.core.materials.AxialMuscleMaterial;
import artisynth.core.materials.HeuerOffLinFM;
import artisynth.core.materials.MasoudMillardLAM;
import artisynth.core.mechmodels.AxialSpring;
import artisynth.core.mechmodels.BodyConnector;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.FrameSpring;
import artisynth.core.mechmodels.MechModel;
import artisynth.core.mechmodels.MeshComponent;
import artisynth.core.mechmodels.MeshComponentList;
import artisynth.core.mechmodels.MultiPointMuscle;
import artisynth.core.mechmodels.MultiPointSpring;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.PointForce;
import artisynth.core.mechmodels.RevoluteJoint;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.mechmodels.RigidMeshComp;
import artisynth.core.mechmodels.SolidJoint;
import artisynth.core.modelbase.ComponentList;
import artisynth.core.modelbase.Controller;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.Model;
import artisynth.core.modelbase.Monitor;
import artisynth.core.modelbase.StepAdjustment;
import artisynth.core.monitors.FrameMarkerMonitor;
import artisynth.core.monitors.FrameSpringMonitor;
import artisynth.core.monitors.MultiPointMuscleMonitor;
import artisynth.core.monitors.MuscleExciterMonitor;
import artisynth.core.monitors.MuscleMonitor;
import artisynth.core.monitors.RigidBodyMonitor;
import artisynth.core.monitors.TargetPointMonitor;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.PullController;
import artisynth.core.workspace.RootModel;
import artisynth.models.lumbarSpine.MuscleParser.MuscleInfo;
import artisynth.models.lumbarSpine.MuscleParser.MusclePoint;

public class LumbarSpine2 extends RootModel implements ItemListener {

	MechModel mech;
	protected double forceByIAP = 0.060; //
	protected double validationForce;
	protected double crateForce;
	protected double farCrateForce;
	protected ArrayList<MuscleInfo> allMusclesInfo;
	protected ArrayList<Muscle> allMuscles = new ArrayList<Muscle>();
	protected ArrayList<MultiPointMuscle> allMultiPointMuscles = new ArrayList<MultiPointMuscle>();
	protected ArrayList<String> allMuscleGeneralNames = new ArrayList<String>();
	protected ArrayList<String> allMultiPointMuscleGeneralNames = new ArrayList<String>();
	protected ArrayList<String> thorax = new ArrayList<String>();
	protected ArrayList<String> lumbar = new ArrayList<String>();
	protected ArrayList<String> pelvis = new ArrayList<String>();
	protected ArrayList<String> includedMuscleGroupNames = new ArrayList<String>();
	protected ArrayList<String> bodyMassCenters = new ArrayList<String>();
	protected ArrayList<String> muscleInjuryLevels = new ArrayList<String>();
	protected ArrayList<ArrayList<String>> LTpL = new ArrayList<ArrayList<String>>(),
			LTpT = new ArrayList<ArrayList<String>>(),
			ILpL = new ArrayList<ArrayList<String>>(),
			ILpT = new ArrayList<ArrayList<String>>(),
			MF = new ArrayList<ArrayList<String>>(),
			LD = new ArrayList<ArrayList<String>>(),
			QL = new ArrayList<ArrayList<String>>(),
			Ps = new ArrayList<ArrayList<String>>(),
			RA = new ArrayList<ArrayList<String>>(),
			EO = new ArrayList<ArrayList<String>>(),
			IO = new ArrayList<ArrayList<String>>();

	// Geometries
	protected String meshesPath = "../../../../meshes/lumbarSpine/";
	public final String[] muscleGroupNames = { "Latissimus_Dorsi",
			"Rectus_Abdominis", "Int_Oblique", "Ext_Oblique", "Multifidus",
			"Psoas_major", "quadratus_lumborum",
			"longissimus_thoracis_pars_lumborum",
			"longissimus_thoracis_pars_thoracis",
			"iliocostalis_lumborum_pars_lumborum",
			"iliocostalis_lumborum_pars_thoracis" };
	public final String[] muscleGroupBriefNames = { "LD", "RA", "IO", "EO",
			"MF", "Ps", "QL", "LTpL", "LTpT", "ILpL", "ILpT" };
	public final double[] sarcomereLengths = { 2.3, 2.83, 2.83, 2.83, 2.27,
			3.11, 2.38, 2.31, 2.31, 2.37, 2.37 };
	boolean[] muscleVisible;

	// Properties
	public static PropertyList myProps = new PropertyList(LumbarSpine2.class,
			RootModel.class);

	public PropertyList getAllPropertyInfo() {
		return myProps;
	}

	static {
		myProps.add("boneAlpha * *", "transparency of bones", 0.2, "[0,1] NW");
		myProps.add("frameMarkerAlpha * *", "transparency of frame marker", 0.0,
				"[0,1] NW");
		myProps.add("forceByIAP * *", "force equivalent of IAP", 0.090,
				"[0,1] NW");
		myProps.add("validationForce * *",
				"horizontal force used for validation", 0.0, "[0,1] NW");
		myProps.add("crateForce * *", "weight of crate", 0.0, "[0,1] NW");
		myProps.add("farCrateForce * *", "weight of farCrate", 0.0, "[0,1] NW");
		myProps.addReadOnly("torsojntForces *", "forces");
		myProps.addReadOnly("l1L2Forces *", "Axialforces");
		myProps.addReadOnly("l2L3Forces *", "Axialforces");
		myProps.addReadOnly("l3L4Forces *", "Axialforces");
		myProps.addReadOnly("l4L5Forces *", "Axialforces");
		myProps.addReadOnly("l5S1Forces *", "Axialforces");
		myProps.addReadOnly("torsojntAxialForces *", "Axialforces");
		myProps.addReadOnly("l1L2AxialForces *", "Axialforces");
		myProps.addReadOnly("l2L3AxialForces *", "Axialforces");
		myProps.addReadOnly("l3L4AxialForces *", "Axialforces");
		myProps.addReadOnly("l4L5AxialForces *", "Axialforces");
		myProps.addReadOnly("l5S1AxialForces *", "Axialforces");
	}

	ComponentList<MuscleExciter> mex = new ComponentList<MuscleExciter>(
			MuscleExciter.class, "mex", "muscleExcitation");;

	public RenderProps boneRenderProps;
	public RenderProps fmRenderPropsCOM;
	public RenderProps fmRenderPropsMuscle;
	public RenderProps fmRenderPropsCOF;

	public LumbarSpine2(String name) {

		super(name);

		BodyConnector.useOldDerivativeMethod = true;
		mech = new MechModel("mech");
		makeThorax();
		addRigidBodies();

		addAbdomen();
		loadMusclesInfo();
		addFrameMarkers();
		makeMuscles();

		addMuscles();
		addExciters();

//		transformRigidBodies();
		addFrameSprings();
		addFixators();
		doLevelCalculations();
		mech.setGravity(0, -9.81, 0);
		addModel(mech);
	}

	// ********************************************************
	// ******************* Adding ATTACH DRIVER ***************
	// ********************************************************
	public void attach(DriverInterface driver) {
		super.attach(driver);

		try {
			driver.getViewer().setUpVector(new Vector3d(0, 1, 0));
		} catch (Exception e) {
			System.out.println("No GUI");
		}

		setViewerEye(new Point3d(-0.0760112, 1.13265, 1.50791));
		setViewerCenter(new Point3d(-0.0760112, 1.13265, 0.00102557));
		addHeadNeckWeight();

		addIntraAbdominalPressure();
		addValidationForce();

		addController(new ThetaController());
		addControlPanels();
	}

	// ********************************************************
	// ******************* Adding Control Panels **************
	// ********************************************************
	public void addControlPanels() {
		addVisibilityControlPanel();
		addFSForcesControlPanel();
	}

	public void addVisibilityControlPanel() {
		ControlPanel myPanel = new ControlPanel("Visibility", "LiveUpdate");
		myPanel.addWidget(new JLabel("Render Properties:"));
		myPanel.addWidget("    Bone", this, "boneAlpha");
		myPanel.addWidget("    Frame Marker", this, "frameMarkerAlpha");

		myPanel.addWidget(new JSeparator());
		myPanel.addWidget(new JLabel("Applied Forces:"));
		myPanel.addWidget("    IAP", this, "forceByIAP");
		myPanel.addWidget("    Validation Force", this, "validationForce");
		myPanel.addWidget("    Crate Force", this, "crateForce");
		myPanel.addWidget("    Far Crate Force", this, "farCrateForce");

		myPanel.addWidget(new JSeparator());
		myPanel.addWidget(new JLabel("Muscle visibility flags:"));
		for (int i = 0; i < includedMuscleGroupNames.size(); i++) {
			String name = includedMuscleGroupNames.get(i);
			JCheckBox check = new JCheckBox("    " + name);
			check.setActionCommand(name + "Visible");
			check.setSelected(muscleVisible[i]);
			check.addItemListener(this);
			myPanel.addWidget(check);
		}

		myPanel.addWidget(new JSeparator());
		myPanel.addWidget(new JLabel("Muscle Excitations: "));

		for (int i = 0; i < mex.size(); i++) {
			String name = mex.get(i).getName();
			myPanel.addWidget("   " + name, mex.get(i), "excitation");
		}

		addControlPanel(myPanel);
	}

	public void addFSForcesControlPanel() {

		ControlPanel myPanel = new ControlPanel("FrameSpringsForces",
				"LiveUpdate");
		myPanel.addWidget(new JLabel("FrameSprings:"));
		myPanel.addWidget("    Torso", this, "torsojntForces");
		myPanel.addWidget("    L1-L2", this, "l1L2Forces");
		myPanel.addWidget("    L2-L3", this, "l2L3Forces");
		myPanel.addWidget("    L3-L4", this, "l3L4Forces");
		myPanel.addWidget("    L4-L5", this, "l4L5Forces");
		myPanel.addWidget("    L5-S1", this, "l5S1Forces");
		myPanel.addWidget(new JSeparator());
		myPanel.addWidget(new JLabel("Axial and Shear Forces:"));
		myPanel.addWidget("    Torso", this, "torsojntAxialForces");
		myPanel.addWidget("    L1-L2", this, "l1L2AxialForces");
		myPanel.addWidget("    L2-L3", this, "l2L3AxialForces");
		myPanel.addWidget("    L3-L4", this, "l3L4AxialForces");
		myPanel.addWidget("    L4-L5", this, "l4L5AxialForces");
		myPanel.addWidget("    L5-S1", this, "l5S1AxialForces");

		Dimension visibilityD = getControlPanels().get("Visibility").getSize();
		java.awt.Point loc = getControlPanels().get("Visibility").getLocation();
		myPanel.setLocation(loc.x + visibilityD.width, loc.y);
		addControlPanel(myPanel);
	}

	// ********************************************************
	// ******************* Adding Controllers *****************
	// ********************************************************
	@Override
	public void addController(Controller controller, Model model) {
		super.addController(controller, model);
		if (controller instanceof PullController) {
			((PullController) controller).setStiffness(500);
		}
	}

	public void addHeadNeckWeight() {
		PointForceController pfc = new PointForceController(
				addPointForce("HeadNeck_centroid", new Vector3d(0, 1, 0), 0),
				new HeadNeckWeight());
		addController(pfc);

	}

	public void addIntraAbdominalPressure() {
		// Intra Abdominal Pressure
		BRerfPointFController pfc = new BRerfPointFController("thorax",
				addPointForce("IAP",
						new Vector3d(Math.sin(Math.PI / 6),
								Math.cos(Math.PI / 6), 0),
						0),
				new AbdominalPressureForce());
		addController(pfc);
	}

	public void addValidationForce() {
		PointForceController pfc = new PointForceController(
				addPointForce("T3_centroid", new Vector3d(-1, 0, 0), 0),
				new ValidationForce());
		addController(pfc);

		pfc = new PointForceController(
				addPointForce("crate", new Vector3d(0, 1, 0), 0),
				new CrateForce(crateForce));
		addController(pfc);

		pfc = new PointForceController(
				addPointForce("farCrate", new Vector3d(0, 1, 0), 0),
				new FarCrateForce(farCrateForce));
		addController(pfc);
	}

	public PointForce addPointForce(String frameMarkerName, Vector3d direction,
			double magnitude) {
		FrameMarker m = mech.frameMarkers().get(frameMarkerName);
		PointForce pf = new PointForce(direction, m);
		pf.setMagnitude(magnitude);
		pf.setAxisLength(0.1);
		pf.setForceScaling(1000.0);

		RenderProps.setPointStyle(m, PointStyle.SPHERE);
		RenderProps.setPointColor(m, Color.cyan);
		RenderProps.setPointRadius(m, 0.002);

		RenderProps.setLineStyle(pf, LineStyle.CYLINDER);
		RenderProps.setLineRadius(pf, pf.getAxisLength() / 50);
		RenderProps.setLineColor(pf, Color.pink);

		mech.addForceEffector(pf);
		return pf;
	}

	// HeadNeckWeight
	public class HeadNeckWeight extends Function1x3 {

		public HeadNeckWeight() {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(0, -0.055, 0); //
			return p;
		}
	}

	// Force Functions
	public class AbdominalPressureForce extends Function1x3 {

		public AbdominalPressureForce() {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(-forceByIAP * Math.sin(Math.PI / 12),
					forceByIAP * Math.cos(Math.PI / 12), 0);
			return p;
		}
	}

	// Validation Force
	public class ValidationForce extends Function1x3 {

		public ValidationForce() {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(validationForce, 0, 0);
			return p;
		}
	}

	// addCrate
	public class CrateForce extends Function1x3 {

		public CrateForce(double F) {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(0, -crateForce, 0);
			return p;
		}
	}

	// addFarCrate
	public class FarCrateForce extends Function1x3 {

		public FarCrateForce(double F) {
		}

		@Override
		public Point3d eval(double x) {
			Point3d p = new Point3d(0, -farCrateForce, 0);
			return p;
		}
	}

	// PFController
	public class BRerfPointFController extends ControllerBase {

		private PointForce pf;
		private Function1x3 myFunction;
		private String rbname;

		public BRerfPointFController(String rbname, PointForce pointForce,
				Function1x3 func) {
			pf = pointForce;
			myFunction = func;
			this.rbname = rbname;
		}

		@Override
		public void apply(double t0, double t1) {

			RigidTransform3d pose = new RigidTransform3d(
					mech.rigidBodies().get(rbname).getPose());
			Point3d f = new Point3d(myFunction.eval(t0));
			f.transform(pose.R);
			pf.setForce(f);
		}

	}

	// ********************************************************
	// ******************* Adding Monitors ********************
	// ********************************************************
	public void addMonitors() {
		// added by amirabdi
		for (FrameMarker fm : mech.frameMarkers()) {
			FrameMarkerMonitor mon = new FrameMarkerMonitor(fm);
			mon.setName(fm.getName() + "_monitor");
			addMonitor(mon);
		}

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

		for (AxialSpring as : mech.axialSprings()) {
			if (as instanceof Muscle) {
				MuscleMonitor mon = new MuscleMonitor((Muscle) as);
				mon.setName(as.getName() + "_monitor");
				addMonitor(mon);
			}
		}

		for (MultiPointSpring mps : mech.multiPointSprings()) {
			if (mps instanceof MultiPointSpring) {
				MultiPointMuscleMonitor mon = new MultiPointMuscleMonitor(
						(MultiPointMuscle) mps);
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
		double tEnd = 30;

		if (t0 == tStart) {
			for (Monitor mon : getMonitors()) {

				if (mon instanceof FrameSpringMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/FrameSpringMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((FrameSpringMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName
								+ "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof RigidBodyMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/RigidBodyMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((RigidBodyMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName
								+ "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof FrameMarkerMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/FrameMarkerMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((FrameMarkerMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName
								+ "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof TargetPointMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/TargetPointMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((TargetPointMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName
								+ "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MuscleMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MuscleMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MuscleMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName
								+ "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MultiPointMuscleMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MultiPointMuscleMonitor/" + mon.getName()
									+ ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MultiPointMuscleMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName
								+ "' (" + e.getMessage() + ")");
					}
				}

				if (mon instanceof MuscleExciterMonitor) {
					String fileName = ArtisynthPath.getSrcRelativePath(this,
							"out/MuscleExciterMonitor/" + mon.getName() + ".m");
					File f = new File(fileName);
					try {
						File parent = f.getParentFile();
						parent.mkdirs();
						((MuscleExciterMonitor) mon).openFile(f);
					} catch (FileNotFoundException e) {
						System.err.println("Cannot open file '" + fileName
								+ "' (" + e.getMessage() + ")");
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
			}
		}
		return ret;
	}

	// ********************************************************
	// ******************* Other Methods **********************
	// ********************************************************

	public void addRigidBodies() {

		// The following components are integrated with the thorax as a
		// composite body; thus, they are not added one by one.
		// Refer to the makeThorax() method for more info.
		// addBone ("ribcage", "ribcage_s");
		// addBone ("T1", "thoracic1_s");
		// addBone ("T2", "thoracic2_s");
		// addBone ("T3", "thoracic3_s");
		// addBone ("T4", "thoracic4_s");
		// addBone("T5", "thoracic5_s");
		// addBone ("T6", "thoracic6_s");
		// addBone ("T7", "thoracic7_s");
		// addBone ("T8", "thoracic8_s");
		// addBone ("T9", "thoracic9_s");
		// addBone ("T10", "thoracic10_s");
		// addBone ("T11", "thoracic11_s");
		// addBone("T12", "thoracic12_s");
		addBone("L1", "lumbar1");
		addBone("L2", "lumbar2");
		addBone("L3", "lumbar3");
		addBone("L4", "lumbar4");
		addBone("L5", "lumbar5");
		addBone("sacrum", "sacrum");
		addBone("pelvis_rv", "pelvis_rv");
		addBone("pelvis_lv", "pelvis_lv");
		addBone("r_humerus", "arm_r_humerus");
		addBone("l_humerus", "humerus_lv");

		// Grouping the vertebrae
		thorax.add("ribcage");
		for (int i = 1; i <= 12; i++) {
			thorax.add("T" + Integer.toString(i));
		}

		for (int i = 1; i <= 5; i++) {
			lumbar.add("L" + Integer.toString(i));
		}

		pelvis.add("pelvis_rv");
		pelvis.add("pelvis_lv");

		// Transformation: scaling the size of all vertebrae
		AffineTransform3d trans = new AffineTransform3d();
		trans.applyScaling(.87, .87, .87);

		// composite
		RigidBody rcb = mech.rigidBodies().get("thorax");

		rcb.transformGeometry(trans);

		for (String str : lumbar) {
			RigidBody rb = mech.rigidBodies().get(str);
			rb.transformGeometry(trans);
		}

		trans = new AffineTransform3d();
		trans.applyScaling(.98, .98, .98);
		RigidBody rb = mech.rigidBodies().get("r_humerus");
		rb.transformGeometry(trans);
		rb = mech.rigidBodies().get("l_humerus");
		rb.transformGeometry(trans);

		// composite
		rcb = mech.rigidBodies().get("thorax");
		rcb.transformGeometry(new RigidTransform3d(-0.104, 1.0412, 0, 0, 0, 0));

		rb = mech.rigidBodies().get("L1");
		rb.transformGeometry(new RigidTransform3d(-0.1065, 1.1302, 0, 0, 0, 0));
		rb.setInertia(1.677, 0.01113, 0.01753, 0.0064);
		rb.setCenterOfMass(new Point3d(0.03274, 0.01940727, 0.00132891));

		rb = mech.rigidBodies().get("L2");
		rb.transformGeometry(new RigidTransform3d(-0.0951, 1.1014, 0, 0, 0, 0));
		rb.setInertia(1.689, 0.01091, 0.01682, 0.00591);
		rb.setCenterOfMass(new Point3d(0.02524622, 0.02021242, 0));

		rb = mech.rigidBodies().get("L3");
		rb.transformGeometry(new RigidTransform3d(-0.0884, 1.0663, 0, 0, 0, 0));
		rb.setInertia(1.67, 0.01066, 0.01608, 0.00541);
		rb.setCenterOfMass(new Point3d(0.017839, 0.02327394, 0));

		rb = mech.rigidBodies().get("L4");
		rb.transformGeometry(new RigidTransform3d(-0.089, 1.0331, 0, 0, 0, 0));
		rb.setInertia(1.799, 0.01123, 0.01643, 0.0052);
		rb.setCenterOfMass(new Point3d(0.01816, 0.02179527, 0));

		rb = mech.rigidBodies().get("L5");
		rb.transformGeometry(new RigidTransform3d(-0.098, 1.001, 0, 0, 0, 0));
		rb.setInertia(1.824, 0.01219, 0.01765, 0.00546);
		rb.setCenterOfMass(new Point3d(0.01728, 0.0183, 0));

		rb = mech.rigidBodies().get("sacrum");
		rb.transformGeometry(new RigidTransform3d(0, 0.93, 0, 0, 0, 0));
		rb.setInertia(7.486, 0.075, 0.08, 0.03);

		rb = mech.rigidBodies().get("r_humerus");
		rb.transformGeometry(
				new RigidTransform3d(-0.08049, 1.38654, 0.16907, 0, 0, 0));
		rb.setDensity(14000 * 1.814);

		rb = mech.rigidBodies().get("l_humerus");
		rb.transformGeometry(
				new RigidTransform3d(-0.08049, 1.38654, -0.16907, 0, 0, 0));
		rb.setDensity(18570 * 1.814);

		for (String st : pelvis) {
			rb = mech.rigidBodies().get(st);
			rb.transformGeometry(new RigidTransform3d(0, 0.979, 0, 0, 0, 0));
		}

		// rb = mech.rigidBodies ().get ("ribcage");
		rb = mech.rigidBodies().get("thorax");
		rb.setInertia(18.619, 0.165, 0.15, 0.125);
		rb.setCenterOfMass(new Point3d(0.02244, 0.20665, 0));

		// RenderProperties
		boneRenderProps = new RenderProps();
		boneRenderProps.setFaceColor(new Color(238, 232, 170));
		boneRenderProps.setAlpha(1);
		boneRenderProps.setShading(Shading.SMOOTH);
		for (RigidBody rbb : mech.rigidBodies()) {
			rbb.setRenderProps(boneRenderProps);
		}
	}

	protected String getGeometriesPath() {
		return getMeshesPath() + "geometry/";
	}

	protected String getMeshesPath() {
		return ArtisynthPath.getSrcRelativePath(LumbarSpine2.class, meshesPath);
	}

	public void addBone(String name, String fileName) {

		String rigidBodyPath = getGeometriesPath();
		RigidBody rb = new RigidBody(name);
		PolygonalMesh mesh = null;
		try {
			mesh = new PolygonalMesh(
					new File(rigidBodyPath + fileName + ".obj"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		rb.setSurfaceMesh(mesh, null);
		rb.setDensity(1500); // Because we have already assigned the mass
		// rb.setDensity (0);
		mech.addRigidBody(rb);
	}

	public void makeThorax() {

		RigidBody rcb = new RigidBody("thorax");
		addMesh(rcb, "ribcage", "ribcage_s");
		addMesh(rcb, "T1", "thoracic1_s");
		addMesh(rcb, "T2", "thoracic2_s");
		addMesh(rcb, "T3", "thoracic3_s");
		addMesh(rcb, "T4", "thoracic4_s");
		addMesh(rcb, "T5", "thoracic5_s");
		addMesh(rcb, "T6", "thoracic6_s");
		addMesh(rcb, "T7", "thoracic7_s");
		addMesh(rcb, "T8", "thoracic8_s");
		addMesh(rcb, "T9", "thoracic9_s");
		addMesh(rcb, "T10", "thoracic10_s");
		addMesh(rcb, "T11", "thoracic11_s");
		addMesh(rcb, "T12", "thoracic12_s");
		mech.addRigidBody(rcb);
	}

	public void addMesh(RigidBody rcb, String name, String fileName) {

		String rigidBodyPath = getGeometriesPath();
		RigidMeshComp mc = new RigidMeshComp(name);
		PolygonalMesh mesh = null;
		try {
			mesh = new PolygonalMesh(
					new File(rigidBodyPath + fileName + ".obj"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		mc.setMesh(mesh);
		rcb.addMeshComp(mc);
	}

	public void fmTransformation(String rbName, Vector3d centroid) {
		for (FrameMarker fm : mech.frameMarkers()) {
			if (rbName.equals(fm.getFrame().getName())) {
				fm.transformGeometry(
						new RigidTransform3d(centroid, AxisAngle.IDENTITY));
			}
		}
	}

	public void inertiaTransformation(String rbName, Vector3d centroid) {
		if ("thorax".equals(rbName)) {
			RigidBody rcb = mech.rigidBodies().get(rbName);
			rcb.setCenterOfMass(new Point3d(0, 0, 0));
		} else {
			RigidBody rb = mech.rigidBodies().get(rbName);
			rb.setCenterOfMass(new Point3d(0, 0, 0));
		}

	}

	// The version for transfering everything to the center of mass instead of
	// centroid.
	public void transformRigidBodiesToCOM() {
		Vector3d COM = new Vector3d();
		PolygonalMesh mesh;

		for (RigidBody rb : mech.rigidBodies()) {
			COM = new Vector3d(rb.getCenterOfMass().x, rb.getCenterOfMass().y,
					rb.getCenterOfMass().z);

			if (rb.getName().equals("thorax")) {
				RigidBody rcb = mech.rigidBodies().get("thorax");
				MeshComponentList<RigidMeshComp> rcbMeshes = rcb.getMeshComps();
				COM.negate();

				// Translating all meshes so that thorax_centroid is set as the
				// Origin of
				// thorax frame ()
				for (String str : thorax) {
					MeshComponent mc = rcbMeshes.get(str);
					mesh = (PolygonalMesh) mc.getMesh();
					mesh.transform(
							new RigidTransform3d(COM, AxisAngle.IDENTITY));
				}
				fmTransformation(rcb.getName(), COM);
				inertiaTransformation(rcb.getName(), COM);

				// Translating the origin thorax frame so that its
				// World-coordinate
				// is set to original world coordinate of thorax-centroid
				COM.negate();
				rcb.transformGeometry(
						new RigidTransform3d(COM, AxisAngle.IDENTITY));
			} else {
				mesh = new PolygonalMesh();
				mesh = rb.getSurfaceMesh();
				COM.negate();
				mesh.transform(new RigidTransform3d(COM, AxisAngle.IDENTITY));
				fmTransformation(rb.getName(), COM);
				inertiaTransformation(rb.getName(), COM);
				COM.negate();
				rb.transformGeometry(
						new RigidTransform3d(COM, AxisAngle.IDENTITY));
			}
		}
	}

	public void addAbdomen() {
		// ******************* Creating abdomen*************************
		RigidBody rb;

		Matrix3d m;
		Vector3d p;
		RigidTransform3d rt;
		AffineTransform3d affTrans;
		// Creates a semi-circle
		rt = new RigidTransform3d();
		rt.setTranslation(new Vector3d(-1, 0, 0));

		PolygonalMesh mesh = null;
		String abdomenMeshPath = getGeometriesPath() + "abdomen.obj";

		boolean constructMeshUsingCSG = false;
		if (constructMeshUsingCSG) {
			PolygonalMesh mesh1, mesh2;
			mesh1 = MeshFactory.createOctahedralSphere(1, 3);
			mesh2 = MeshFactory.createBox(2, 2, 2);
			mesh2.transform(rt);
			mesh = MeshFactory.getSubtraction(mesh1, mesh2);
			try {
				mesh.write(new File(abdomenMeshPath), "%g");
			} catch (Exception e) {
			}
		} else {
			try {
				mesh = new PolygonalMesh(abdomenMeshPath);
			} catch (Exception e) {
				System.out.println(
						"ERROR reading mesh file " + abdomenMeshPath + ":");
				System.out.println(e.getMessage());
			}
		}

		// Transforms the semi-circle to a semi-ellipsoid
		m = new Matrix3d(0.01, 0, 0, 0, 0.08, 0, 0, 0, 0.05);
		p = new Vector3d(0, 0, 0);

		affTrans = new AffineTransform3d(m, p);
		mesh.transform(affTrans);

		rb = new RigidBody("abdomen");
		rb.setSurfaceMesh(mesh, null);
		rb.setPosition(new Point3d(0, 0, 0));
		// rb.setMass(0);
		AffineTransform3d trans = new AffineTransform3d();
		rb.transformGeometry(trans);
		rb.transformGeometry(new RigidTransform3d(-0.008, 1.091, 0, 0, 0, 0));
		RenderProps.setFaceColor(rb, Color.white);
		mech.addRigidBody(rb);
	}

	public void addFrameMarkers() {

		fmRenderPropsMuscle = new RenderProps();
		fmRenderPropsMuscle.setPointStyle(PointStyle.SPHERE);
		fmRenderPropsMuscle.setPointRadius(0.0042);
		fmRenderPropsMuscle.setPointColor(Color.RED);
		fmRenderPropsMuscle.setAlpha(0.0);

		fmRenderPropsCOM = new RenderProps();
		fmRenderPropsCOM.setPointStyle(PointStyle.SPHERE);
		fmRenderPropsCOM.setPointRadius(0.008);
		fmRenderPropsCOM.setPointColor(Color.BLUE);
		fmRenderPropsCOM.setAlpha(0.0);

		// Adding Muscle Frame Makers
		FrameMarker fm;
		boolean ok = true;
		int i = 0;

		for (MuscleInfo mInfo : allMusclesInfo) {
			for (MusclePoint mp : mInfo.points) {

				if (ok) {
					fm = new FrameMarker(mech.rigidBodies().get(mp.body),
							mp.pnt);
					fm.setName(mInfo.name + "_fm" + Integer.toString(i));
					i++;
					fm.setRenderProps(fmRenderPropsMuscle);
					mech.addFrameMarker(fm);
				}
			}
			i = 0;
		}

		// Adding FrameMarkers for Rigid Bodies' COM
		for (RigidBody rb : mech.rigidBodies()) {
			if (rb.getName().equals("thorax")) {
				// Currently we are assuming the the centroids of the ribcage
				// and thorax are the same.
				RigidBody rcb = mech.rigidBodies().get("thorax");
				MeshComponentList<RigidMeshComp> rcbMeshes = rcb.getMeshComps();

				for (String str : thorax) {
					MeshComponent mc = rcbMeshes.get(str);
					Point3d com = new Point3d();
					mc.getMesh().computeCentroid(com);
					fm = new FrameMarker(rb, com);
					fm.setName(mc.getName() + "_centroid");
					bodyMassCenters.add(fm.getName());
					mech.addFrameMarker(fm);
					fm.setRenderProps(fmRenderPropsCOM);
				}

				Point3d com = new Point3d(mech.frameMarkers()
						.get("ribcage_centroid").getLocation());
				fm = new FrameMarker(rb, com);
				fm.setName(rb.getName() + "_centroid");
				bodyMassCenters.add(fm.getName());
				mech.addFrameMarker(fm);
				fm.setRenderProps(fmRenderPropsCOM);
			} else {
				Point3d com = new Point3d();
				rb.getSurfaceMesh().computeCentroid(com);
				// com.inverseTransform(rb.getPose());
				fm = new FrameMarker(rb, com);
				// fm = new FrameMarker (rb, rb.getCenterOfMass ());
				fm.setName(rb.getName() + "_centroid");
				bodyMassCenters.add(fm.getName());
				mech.addFrameMarker(fm);
				fm.setRenderProps(fmRenderPropsCOM);
			}
		}

		// FrameMarkers for Centers of Masses
//		makeCOMfm(mech.rigidBodies().get("thorax"),
//				new Point3d(0.02244, 0.20665, 0));
//		makeCOMfm(mech.rigidBodies().get("L1"),
//				new Point3d(0.03274, 0.01940727, 0.00132891));
//		makeCOMfm(mech.rigidBodies().get("L2"),
//				new Point3d(0.02524622, 0.02021242, 0));
//		makeCOMfm(mech.rigidBodies().get("L3"),
//				new Point3d(0.017839, 0.02327394, 0));
//		makeCOMfm(mech.rigidBodies().get("L4"),
//				new Point3d(0.01816, 0.02179527, 0));
//		makeCOMfm(mech.rigidBodies().get("L5"),
//				new Point3d(0.01728, 0.0183, 0));

		// FrameMarker for Head and Neck Weight
		Point3d T1_centroid = new Point3d(
				mech.frameMarkers().get("T1_centroid").getLocation());
		// fm =
		// new FrameMarker (mech.rigidBodies ().get ("ribcage"), new Point3d (
		// T1_centroid.x + 0.06, T1_centroid.y + 0.137, T1_centroid.z + 0));
		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(T1_centroid.x + 0.06, T1_centroid.y + 0.137,
						T1_centroid.z + 0));
		fm.setName("HeadNeck_centroid");
		fm.setRenderProps(fmRenderPropsCOM);
		// fm.setExternalForce (new Vector3d (0, -55, 0));
		mech.addFrameMarker(fm);

		// FrameMarker for IAP
		Point3d T12_centroid = new Point3d(
				mech.frameMarkers().get("T12_centroid").getLocation());
		// fm =
		// new FrameMarker (mech.rigidBodies ().get ("ribcage"), new Point3d (
		// T12_centroid.x + 0.08, T12_centroid.y + 0, T12_centroid.z + 0));
		fm = new FrameMarker(mech.rigidBodies().get("thorax"), new Point3d(
				T12_centroid.x + 0.08, T12_centroid.y + 0, T12_centroid.z + 0));
		fm.setName("IAP");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		// FrameMarker for Crate
		// fm =
		// new FrameMarker (mech.rigidBodies ().get ("ribcage"), new Point3d (
		// T12_centroid.x + 0.29, T12_centroid.y + 0, T12_centroid.z + 0));
		fm = new FrameMarker(mech.rigidBodies().get("thorax"), new Point3d(
				T12_centroid.x + 0.29, T12_centroid.y + 0, T12_centroid.z + 0));
		fm.setName("crate");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		// FrameMarker for farCrate
		// fm =
		// new FrameMarker (mech.rigidBodies ().get ("ribcage"), new Point3d (
		// T12_centroid.x + 0.29 + 0.30, T12_centroid.y + 0, T12_centroid.z +
		// 0));
		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(T12_centroid.x + 0.29 + 0.30, T12_centroid.y + 0,
						T12_centroid.z + 0));
		fm.setName("farCrate");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		// FrameMarker for Offset Motion Targets
		Point3d ribcage_centroid = new Point3d(
				mech.frameMarkers().get("ribcage_centroid").getLocation());
		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(ribcage_centroid.x, ribcage_centroid.y + 0,
						ribcage_centroid.z + 0.10));
		fm.setName("ribcageMotionTarget_r");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		fm = new FrameMarker(mech.rigidBodies().get("thorax"),
				new Point3d(ribcage_centroid.x, ribcage_centroid.y + 0,
						ribcage_centroid.z - 0.10));
		fm.setName("ribcageMotionTarget_l");
		fm.setRenderProps(fmRenderPropsCOM);
		mech.addFrameMarker(fm);

		// FrameMarkers for FrameSprings
		fm = new FrameMarker();
		fm.setName("L5_S1_IVDjnt_A");
		fm.setFrame(mech.rigidBodies().get("sacrum"));
		fm.setLocation(new Point3d(-0.102, 0.05600000000000005, 0.0));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("L4_L5_IVDjnt_A");
		fm.setFrame(mech.rigidBodies().get("L5"));
		fm.setLocation(new Point3d(0.009498444198113215, 0.017414155377358576,
				-6.423386455857517E-19));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("L3_L4_IVDjnt_A");
		fm.setFrame(mech.rigidBodies().get("L4"));
		fm.setLocation(new Point3d(0.002644404952830179, 0.019190267122641602,
				-6.423386455857517E-19));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("L2_L3_IVD_jnt_A");
		fm.setFrame(mech.rigidBodies().get("L3"));
		fm.setLocation(new Point3d(-0.002899711084905665, 0.018892314622641626,
				-2.9457568459983515E-19));

		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("L1_L2_IVD_jnt_A");
		fm.setFrame(mech.rigidBodies().get("L2"));
		fm.setLocation(new Point3d(-0.004038857735849055, 0.014631359622641504,
				1.5547050020546855E-19));

		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("Torsojnt_A");
		fm.setFrame(mech.rigidBodies().get("L1"));
		fm.setLocation(new Point3d(-0.00456781773584905, 0.016483901509434018,
				1.5547050020546855E-19));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("Torsojnt_B");
		// fm.setFrame (mech.rigidBodies ().get ("ribcage"));
		fm.setFrame(mech.rigidBodies().get("thorax"));
		fm.setLocation(new Point3d(-0.020287463301886785, 0.1352094431603772,
				6.032547169813912E-7));
		mech.addFrameMarker(fm);

		// adding Frame Marker for Abdjnt

		fm = new FrameMarker();
		fm.setName("Abdjnt_A");
		fm.setFrame(mech.rigidBodies().get("abdomen"));
		fm.setLocation(new Point3d(-0.09, -0.09000000000000008, 0.0));
		mech.addFrameMarker(fm);

		fm = new FrameMarker();
		fm.setName("Abdjnt_B");
		fm.setFrame(mech.rigidBodies().get("sacrum"));
		fm.setLocation(new Point3d(-0.098, 0.07099999999999984, 0.0));
		mech.addFrameMarker(fm);
	}

	public void makeCOMfm(RigidBody rb, Point3d com) {
		FrameMarker fm;
		fm = new FrameMarker(rb, com);
		fm.setName(rb.getName() + "_COM");
		bodyMassCenters.add(fm.getName());
		mech.addFrameMarker(fm);
		fm.setRenderProps(fmRenderPropsCOM);
	}

	public void addAdditionalFrameMarkers() {
	}

	/**
	 * Reads muscles information from the musculoskeletal model of lumbar spine
	 * by Christophy et al. (2012) inside OpenSim.
	 */
	public void loadMusclesInfo() {

		allMusclesInfo = null;
		try {
			allMusclesInfo = MuscleParser
					.parse(getMeshesPath() + "ChristophyMuscle.txt");
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		for (MuscleInfo mInfo : allMusclesInfo) {
			for (MusclePoint mp : mInfo.points) {
				// if ("torso".equals (mp.body))
				// mp.body = "ribcage";
				if ("torso".equals(mp.body))
					mp.body = "thorax";
				else if ("pelvis".equals(mp.body))
					mp.body = "pelvis_rv";
				else if ("lumbar1".equals(mp.body))
					mp.body = "L1";
				else if ("lumbar2".equals(mp.body))
					mp.body = "L2";
				else if ("lumbar3".equals(mp.body))
					mp.body = "L3";
				else if ("lumbar4".equals(mp.body))
					mp.body = "L4";
				else if ("lumbar5".equals(mp.body))
					mp.body = "L5";
				else if ("Abdomen".equals(mp.body))
					mp.body = "abdomen";
			}
		}
	}

	public void makeMuscles() {

		Muscle m;
		MultiPointMuscle mpm;
		int i = 0;
		int j = 0;
		int k = 0;

		for (MuscleInfo mInfo : allMusclesInfo) {
			if (mInfo.points.size() > 2) {
				mpm = new MultiPointMuscle(mInfo.name);
				i = 0;
				j = 0;
				for (MusclePoint mp : mInfo.points) {
					k = 0;
					for (String rbName : muscleInjuryLevels) {
						if (rbName.equals(mp.body)) {
							if (!mInfo.group.equals("Psoas_major_r")
									&& !mInfo.group.equals("Psoas_major_l")) {
								k++;
							}
						}
					}
					if (k == 0) { // This checks if point mp is not on the
									// injured
									// vertebral level so that to be removed
						mpm.addPoint(mech.frameMarkers().get(
								(mInfo.name + "_fm" + Integer.toString(i))));
						j++;
					}
					i++;
				}

				if (j > 1) {
					mpm.setMaterial(getMuscleMaterial(mInfo));
					mpm.setRestLength(mpm.getLength());
					allMultiPointMuscles.add(mpm);
				}
			} else if (mech.frameMarkers().get((mInfo.name + "_fm0")) != null) {
				m = new Muscle(mInfo.name);
				k = 0;
				for (MusclePoint mp : mInfo.points) {
					for (String rbName : muscleInjuryLevels) {
						if (rbName.equals(mp.body)) {
							if (!mInfo.group.equals("Psoas_major_r")
									&& !mInfo.group.equals("Psoas_major_l")) {
								k++;
							}
						}
					}
				}
				if (k == 0) {
					m.setPoints(mech.frameMarkers().get((mInfo.name + "_fm0")),
							mech.frameMarkers().get((mInfo.name + "_fm1")));
					m.setMaterial(getMuscleMaterial(mInfo));
					m.setRestLength(m.getLength());
					allMuscles.add(m);
				}
			}
		}

		for (Muscle mus : allMuscles) {

			String str = mus.getName();
			String[] chars = str.split("");
			String muscleGeneralName = new String();
			for (i = 0; i < chars.length - 2; i++) {
				muscleGeneralName = muscleGeneralName + chars[i];
			}
			allMuscleGeneralNames.add(muscleGeneralName);

		}

		for (MultiPointMuscle mpmus : allMultiPointMuscles) {

			String str = mpmus.getName();
			String[] chars = str.split("");
			String multiPointMuscleGeneralName = new String();
			for (i = 0; i < chars.length - 2; i++) {
				multiPointMuscleGeneralName = multiPointMuscleGeneralName
						+ chars[i];
			}
			allMultiPointMuscleGeneralNames.add(multiPointMuscleGeneralName);
		}
	}

	public void addMuscles() {

		// addMuscleGroup ("Latissimus_Dorsi", "_r", Color.white);
		// addMuscleGroup ("Latissimus_Dorsi", "_l", Color.white);
		addMuscleGroup("Rectus_Abdominis", "_r", Color.white);
		addMuscleGroup("Rectus_Abdominis", "_l", Color.white);
		addMuscleGroup("Int_Oblique", "_r", Color.white);
		addMuscleGroup("Int_Oblique", "_l", Color.white);
		addMuscleGroup("Ext_Oblique", "_r", Color.white);
		addMuscleGroup("Ext_Oblique", "_l", Color.white);
		addMuscleGroup("Multifidus", "_r", Color.white);
		addMuscleGroup("Multifidus", "_l", Color.white);
		addMuscleGroup("Psoas_major", "_r", Color.white);
		addMuscleGroup("Psoas_major", "_l", Color.white);
		addMuscleGroup("quadratus_lumborum", "_r", Color.white);
		addMuscleGroup("quadratus_lumborum", "_l", Color.white);
		addMuscleGroup("longissimus_thoracis_pars_lumborum", "_r", Color.white);
		addMuscleGroup("longissimus_thoracis_pars_lumborum", "_l", Color.white);
		addMuscleGroup("longissimus_thoracis_pars_thoracis", "_r", Color.white);
		addMuscleGroup("longissimus_thoracis_pars_thoracis", "_l", Color.white);
		addMuscleGroup("iliocostalis_lumborum_pars_lumborum", "_r",
				Color.white);
		addMuscleGroup("iliocostalis_lumborum_pars_lumborum", "_l",
				Color.white);
		addMuscleGroup("iliocostalis_lumborum_pars_thoracis", "_r",
				Color.white);
		addMuscleGroup("iliocostalis_lumborum_pars_thoracis", "_l",
				Color.white);

		// Visibility of all included Muscles inside Control Panel
		muscleVisible = new boolean[includedMuscleGroupNames.size()];
		for (int i = 0; i < includedMuscleGroupNames.size(); i++) {
			muscleVisible[i] = false;
		}

	}

	public void addMuscleGroup(String str, String side, Color color) {

		ArrayList<Muscle> gMuscle = new ArrayList<Muscle>();
		ArrayList<MultiPointMuscle> gMultiPointMuscle = new ArrayList<MultiPointMuscle>();

		for (MuscleInfo mInfo : allMusclesInfo) {

			if ((str + side).equals(mInfo.group)) {

				for (Muscle m : allMuscles) {
					if (m.getName().equals(mInfo.name)) {
						mech.addAxialSpring(m);
						gMuscle.add(m);

						if (includedMuscleGroupNames.size() < 1) {
							includedMuscleGroupNames.add(str);
						} else {
							if (!includedMuscleGroupNames
									.get(includedMuscleGroupNames.size() - 1)
									.equals(str))
								includedMuscleGroupNames.add(str);
						}
					}
				}

				for (MultiPointMuscle mpm : allMultiPointMuscles) {
					if (mpm.getName().equals(mInfo.name)) {
						mech.addMultiPointSpring(mpm);
						gMultiPointMuscle.add(mpm);

						if (includedMuscleGroupNames.size() < 1) {
							includedMuscleGroupNames.add(str);
						} else {
							if (!includedMuscleGroupNames
									.get(includedMuscleGroupNames.size() - 1)
									.equals(str))
								includedMuscleGroupNames.add(str);
						}
					}
				}
			}

		}

		// Render Properties of the muscle group
		RenderProps rp = new RenderProps();
		rp.setLineStyle(LineStyle.CYLINDER);
		rp.setLineColor(color);
		rp.setLineRadius(0.0025);

		for (Muscle m : gMuscle) {
			m.setExcitationColor(Color.red);
			m.setRenderProps(rp);
		}
		for (MultiPointMuscle mpm : gMultiPointMuscle) {
			mpm.setExcitationColor(Color.red);
			mpm.setRenderProps(rp);
		}

	}

	public void disableMuscle(String rbName) {

		for (MuscleInfo mInfo : allMusclesInfo) {

			if (mech.axialSprings().get(mInfo.name) != null) {
				for (MusclePoint mp : mInfo.points) {
					if (rbName.equals(mp.body)) {
						((Muscle) mech.axialSprings().get(mInfo.name))
								.setEnabled(false);
					}
				}
			}
			// else if (mech.multiPointSprings ().get (mInfo.name) != null) {
			// for (MusclePoint mp : mInfo.points) {
			// if (rbName.equals (mp.body)) {
			// ((MultiPointMuscle)mech.multiPointSprings ().get (mInfo.name))
			// .setEnabled (false);
			// }
			// }
			// }
		}
	}

	public void addExciters() {
		addGroupedMex();
		// addMyFavoriteMex ();
		// addIndividualMex ();
	}

	// Groups right and left excitations together in one excitation
	public void addGroupedMex() {
		for (String str : includedMuscleGroupNames) {

			if (str == "Rectus_Abdominis") {
				MuscleExciter mRight = new MuscleExciter(str + "_r");
				MuscleExciter mLeft = new MuscleExciter(str + "_l");

				for (MuscleInfo mInfo : allMusclesInfo) {

					if (mech.axialSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							AxialSpring as = mech.axialSprings()
									.get(mInfo.name);
							mRight.addTarget((Muscle) as, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							AxialSpring as = mech.axialSprings()
									.get(mInfo.name);
							mLeft.addTarget((Muscle) as, 1.0);
						}
					} else if (mech.multiPointSprings()
							.get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							MultiPointSpring mps = mech.multiPointSprings()
									.get(mInfo.name);
							mRight.addTarget((MultiPointMuscle) mps, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							MultiPointSpring mps = mech.multiPointSprings()
									.get(mInfo.name);
							mLeft.addTarget((MultiPointMuscle) mps, 1.0);
						}
					}

				}
				mex.add(mRight);
				mex.add(mLeft);
				mech.addMuscleExciter(mRight);
				mech.addMuscleExciter(mLeft);
			} else if (str == "iliocostalis_lumborum_pars_thoracis") {
				MuscleExciter mRight = new MuscleExciter(str + "_r");
				MuscleExciter mLeft = new MuscleExciter(str + "_l");

				for (MuscleInfo mInfo : allMusclesInfo) {

					if (mech.axialSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							AxialSpring as = mech.axialSprings()
									.get(mInfo.name);
							mRight.addTarget((Muscle) as, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							AxialSpring as = mech.axialSprings()
									.get(mInfo.name);
							mLeft.addTarget((Muscle) as, 1.0);
						}
					} else if (mech.multiPointSprings()
							.get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							MultiPointSpring mps = mech.multiPointSprings()
									.get(mInfo.name);
							mRight.addTarget((MultiPointMuscle) mps, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							MultiPointSpring mps = mech.multiPointSprings()
									.get(mInfo.name);
							mLeft.addTarget((MultiPointMuscle) mps, 1.0);
						}
					}

				}
				mex.add(mRight);
				mex.add(mLeft);
				mech.addMuscleExciter(mRight);
				mech.addMuscleExciter(mLeft);
			} else if (str == "quadratus_lumborum") {
				MuscleExciter mRight = new MuscleExciter(str + "_r");
				MuscleExciter mLeft = new MuscleExciter(str + "_l");

				for (MuscleInfo mInfo : allMusclesInfo) {

					if (mech.axialSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							AxialSpring as = mech.axialSprings()
									.get(mInfo.name);
							mRight.addTarget((Muscle) as, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							AxialSpring as = mech.axialSprings()
									.get(mInfo.name);
							mLeft.addTarget((Muscle) as, 1.0);
						}
					} else if (mech.multiPointSprings()
							.get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")) {
							MultiPointSpring mps = mech.multiPointSprings()
									.get(mInfo.name);
							mRight.addTarget((MultiPointMuscle) mps, 1.0);
						}
						if (mInfo.group.equals(str + "_l")) {
							MultiPointSpring mps = mech.multiPointSprings()
									.get(mInfo.name);
							mLeft.addTarget((MultiPointMuscle) mps, 1.0);
						}
					}

				}
				mex.add(mRight);
				mex.add(mLeft);
				mech.addMuscleExciter(mRight);
				mech.addMuscleExciter(mLeft);
			} else {
				MuscleExciter m = new MuscleExciter(str);

				for (MuscleInfo mInfo : allMusclesInfo) {

					if (mech.axialSprings().get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")
								|| mInfo.group.equals(str + "_l")) {
							AxialSpring as = mech.axialSprings()
									.get(mInfo.name);
							m.addTarget((Muscle) as, 1.0);
						}
					} else if (mech.multiPointSprings()
							.get(mInfo.name) != null) {
						if (mInfo.group.equals(str + "_r")
								|| mInfo.group.equals(str + "_l")) {
							MultiPointSpring mps = mech.multiPointSprings()
									.get(mInfo.name);
							m.addTarget((MultiPointMuscle) mps, 1.0);
						}
					}

				}
				mex.add(m);
				mech.addMuscleExciter(m);
			}
		}
	}

	public void setInitialExcitation() {

		for (String str : includedMuscleGroupNames) {
			if (str == "Rectus_Abdominis") {
				Muscle as = (Muscle) mech.axialSprings().get("rect_abd_r");
				if (as != null) {
					as.setExcitation(0.079899);
				}
				as = (Muscle) mech.axialSprings().get("rect_abd_l");
				if (as != null) {
					as.setExcitation(0.003019);
				}
			} else if (str == "Multifidus") {
				excitationSetter("Multifidus", 0.133196);
			} else if (str == "Psoas_major") {
				excitationSetter("Psoas_major", 0.140366);
			} else if (str == "quadratus_lumborum") {
				excitationSetter("quadratus_lumborum", 0.059011);
			} else if (str == "longissimus_thoracis_pars_lumborum") {
				excitationSetter("longissimus_thoracis_pars_lumborum",
						0.057680);
			} else if (str == "longissimus_thoracis_pars_thoracis") {
				excitationSetter("longissimus_thoracis_pars_thoracis",
						0.340988);
			} else if (str == "iliocostalis_lumborum_pars_lumborum") {
				excitationSetter("iliocostalis_lumborum_pars_lumborum",
						0.083122);
			} else if (str == "iliocostalis_lumborum_pars_thoracis") {
				excitationSetter("iliocostalis_lumborum_pars_thoracis",
						0.182055);
			}

		}
	}

	public void excitationSetter(String str, double a) {
		for (MuscleInfo mInfo : allMusclesInfo) {

			if (mech.axialSprings().get(mInfo.name) != null) {
				if (mInfo.group.equals(str + "_r")
						|| mInfo.group.equals(str + "_l")) {
					Muscle as = (Muscle) mech.axialSprings().get(mInfo.name);
					as.setExcitation(a);
				}
			} else if (mech.multiPointSprings().get(mInfo.name) != null) {
				if (mInfo.group.equals(str + "_r")
						|| mInfo.group.equals(str + "_l")) {
					MultiPointMuscle mps = (MultiPointMuscle) mech
							.multiPointSprings().get(mInfo.name);
					mps.setExcitation(a);
				}
			}
		}
	}

	// Adds right and left excitations for each muscle group separately
	public void addSeparateRandLGroupedMex() {
		for (String str : includedMuscleGroupNames) {
			MuscleExciter mRight = new MuscleExciter(str + "_r");
			MuscleExciter mLeft = new MuscleExciter(str + "_l");

			for (MuscleInfo mInfo : allMusclesInfo) {

				if (mech.axialSprings().get(mInfo.name) != null) {
					if (mInfo.group.equals(str + "_r")) {
						AxialSpring as = mech.axialSprings().get(mInfo.name);
						mRight.addTarget((Muscle) as, 1.0);
					}
					if (mInfo.group.equals(str + "_l")) {
						AxialSpring as = mech.axialSprings().get(mInfo.name);
						mLeft.addTarget((Muscle) as, 1.0);
					}
				} else if (mech.multiPointSprings().get(mInfo.name) != null) {
					if (mInfo.group.equals(str + "_r")) {
						MultiPointSpring mps = mech.multiPointSprings()
								.get(mInfo.name);
						mRight.addTarget((MultiPointMuscle) mps, 1.0);
					}
					if (mInfo.group.equals(str + "_l")) {
						MultiPointSpring mps = mech.multiPointSprings()
								.get(mInfo.name);
						mLeft.addTarget((MultiPointMuscle) mps, 1.0);
					}
				}

			}
			mex.add(mRight);
			mex.add(mLeft);
			mech.addMuscleExciter(mRight);
			mech.addMuscleExciter(mLeft);
		}
	}

	public void addAllFasciclesIndividuallyMex() {

		for (AxialSpring as : mech.axialSprings()) {
			MuscleExciter m = new MuscleExciter(as.getName());
			m.addTarget((Muscle) as, 1.0);
			mex.add(m);
			mech.addMuscleExciter(m);
		}

		for (MultiPointSpring mps : mech.multiPointSprings()) {
			MuscleExciter m = new MuscleExciter(mps.getName());
			m.addTarget((MultiPointMuscle) mps, 1.0);
			mex.add(m);
			mech.addMuscleExciter(m);
		}

	}

	public void addMyFavoriteMex() {

		for (MuscleInfo mInfo : allMusclesInfo) {
			if ("Rectus_Abdominis_r".equals(mInfo.group)
					|| "Rectus_Abdominis_l".equals(mInfo.group)
					|| "longissimus_thoracis_pars_lumborum_r"
							.equals(mInfo.group)
					|| "longissimus_thoracis_pars_lumborum_l"
							.equals(mInfo.group)
					|| "iliocostalis_lumborum_pars_thoracis_r"
							.equals(mInfo.group)
					|| "iliocostalis_lumborum_pars_thoracis_l"
							.equals(mInfo.group)) {
				if (mech.axialSprings().get(mInfo.name) != null) {
					MuscleExciter m = new MuscleExciter(mInfo.name);
					m.addTarget((Muscle) mech.axialSprings().get(mInfo.name),
							1.0);
					mex.add(m);
					mech.addMuscleExciter(m);
				} else if (mech.multiPointSprings().get(mInfo.name) != null) {
					MuscleExciter m = new MuscleExciter(mInfo.name);
					m.addTarget((MultiPointMuscle) mech.multiPointSprings()
							.get(mInfo.name), 1.0);
					mex.add(m);
					mech.addMuscleExciter(m);
				}
			}

			else {
				String str = mInfo.name;
				String[] chars = str.split("");
				String exciterName = new String();
				for (int i = 0; i < chars.length - 2; i++) {
					exciterName = exciterName + chars[i];
				}

				if (mech.axialSprings().get(mInfo.name) != null) {
					if (mInfo.name.equals(exciterName + "_l")) {
						MuscleExciter m = new MuscleExciter(exciterName);
						m.addTarget(
								(Muscle) mech.axialSprings().get(mInfo.name),
								1.0);
						if (mech.axialSprings()
								.get(exciterName + "_r") != null) {
							m.addTarget((Muscle) mech.axialSprings()
									.get(exciterName + "_r"), 1.0);
						}
						mex.add(m);
						mech.addMuscleExciter(m);
					} else if (mech.axialSprings()
							.get(exciterName + "_l") == null) {
						MuscleExciter m = new MuscleExciter(exciterName);
						m.addTarget(
								(Muscle) mech.axialSprings().get(mInfo.name),
								1.0);
						mex.add(m);
						mech.addMuscleExciter(m);
					}

				} else if (mech.multiPointSprings().get(mInfo.name) != null) {
					if (mInfo.name.equals(exciterName + "_l")) {
						MuscleExciter m = new MuscleExciter(exciterName);
						m.addTarget((MultiPointMuscle) mech.multiPointSprings()
								.get(mInfo.name), 1.0);
						if (mech.multiPointSprings()
								.get(exciterName + "_r") != null) {
							m.addTarget(
									(MultiPointMuscle) mech.multiPointSprings()
											.get(exciterName + "_r"),
									1.0);
						}
						mex.add(m);
						mech.addMuscleExciter(m);
					} else if (mech.multiPointSprings()
							.get(exciterName + "_l") == null) {
						MuscleExciter m = new MuscleExciter(exciterName);
						m.addTarget((MultiPointMuscle) mech.multiPointSprings()
								.get(mInfo.name), 1.0);
						mex.add(m);
						mech.addMuscleExciter(m);
					}
				}
			}

		}
	}

	public void addIndividualMex() {

		for (MuscleInfo mInfo : allMusclesInfo) {
			String exciterName = mInfo.name;

			if (mech.axialSprings().get(mInfo.name) != null) {
				MuscleExciter m = new MuscleExciter(exciterName);
				m.addTarget((Muscle) mech.axialSprings().get(mInfo.name), 1.0);
				mex.add(m);
				mech.addMuscleExciter(m);
			} else if (mech.multiPointSprings().get(mInfo.name) != null) {
				MuscleExciter m = new MuscleExciter(exciterName);
				m.addTarget((MultiPointMuscle) mech.multiPointSprings()
						.get(mInfo.name), 1.0);
				mex.add(m);
				mech.addMuscleExciter(m);
			}
		}
	}

	private void updateVisibility() {

		for (int i = 0; i < includedMuscleGroupNames.size(); i++) {
			boolean seeMe = (muscleVisible[i]);

			for (MuscleInfo mInfo : allMusclesInfo) {

				if (mech.axialSprings().get(mInfo.name) != null) {
					if (mInfo.group
							.equals(includedMuscleGroupNames.get(i) + "_r")
							|| mInfo.group.equals(
									includedMuscleGroupNames.get(i) + "_l"))
						RenderProps.setVisible(
								mech.axialSprings().get(mInfo.name), seeMe);
				} else if (mech.multiPointSprings().get(mInfo.name) != null) {
					if (mInfo.group
							.equals(includedMuscleGroupNames.get(i) + "_r")
							|| mInfo.group.equals(
									includedMuscleGroupNames.get(i) + "_l"))
						RenderProps.setVisible(
								mech.multiPointSprings().get(mInfo.name),
								seeMe);
				}
			}
		}
	}

	public void setMuscleVisible(boolean visible, int idx) {
		muscleVisible[idx] = visible;
		updateVisibility();
	}

	public boolean getMuscleVisible(int idx) {
		return muscleVisible[idx];
	}

	public void setBoneAlpha(double alpha) {
		boneRenderProps.setAlpha(alpha);
		for (RigidBody rb : mech.rigidBodies()) {
			RenderProps.setAlpha(rb, alpha);
		}
		// updateVisibility ();
	}

	public double getBoneAlpha() {
		return boneRenderProps.getAlpha();
	}

	public void setFrameMarkerAlpha(double alpha) {
		fmRenderPropsMuscle.setAlpha(alpha);
		fmRenderPropsCOM.setAlpha(alpha);
		for (FrameMarker fm : mech.frameMarkers()) {
			// RenderProps.setAlpha (fm, alpha);
			fm.setRenderProps(fmRenderPropsMuscle);
		}
		for (RigidBody rb : mech.rigidBodies()) {
			mech.frameMarkers().get(rb.getName() + "_centroid")
					.setRenderProps(fmRenderPropsCOM);
		}
		// updateVisibility ();
	}

	public double getFrameMarkerAlpha() {
		return fmRenderPropsMuscle.getAlpha();
	}

	public void setForceByIAP(double mag) {
		forceByIAP = mag;
	}

	public double getForceByIAP() {
		return forceByIAP;
	}

	public void setValidationForce(double mag) {
		validationForce = mag;
	}

	public double getValidationForce() {
		return validationForce;
	}

	public void setCrateForce(double mag) {
		crateForce = mag;
	}

	public double getCrateForce() {
		return crateForce;
	}

	public void setFarCrateForce(double mag) {
		farCrateForce = mag;
	}

	public double getFarCrateForce() {
		return farCrateForce;
	}

	public Wrench getTorsojntForces() {
		Wrench forces;
		forces = new Wrench(
				mech.frameSprings().get("Torsojnt").getSpringForce());
		return forces;
	}

	public Wrench getL1L2Forces() {
		Wrench forces;
		forces = new Wrench(
				mech.frameSprings().get("L1_L2_IVD_jnt").getSpringForce());
		return forces;
	}

	public Wrench getL2L3Forces() {
		Wrench forces;
		forces = new Wrench(
				mech.frameSprings().get("L2_L3_IVD_jnt").getSpringForce());
		return forces;
	}

	public Wrench getL3L4Forces() {
		Wrench forces;
		forces = new Wrench(
				mech.frameSprings().get("L3_L4_IVDjnt").getSpringForce());
		return forces;
	}

	public Wrench getL4L5Forces() {
		Wrench forces;
		forces = new Wrench(
				mech.frameSprings().get("L4_L5_IVDjnt").getSpringForce());
		return forces;
	}

	public Wrench getL5S1Forces() {
		Wrench forces;
		forces = new Wrench(
				mech.frameSprings().get("L5_S1_IVDjnt").getSpringForce());
		return forces;
	}

	public Vector3d getTorsojntAxialForces() {
		Vector3d forces = new Vector3d();
		FrameSpringMonitor fsMon = (FrameSpringMonitor) getMonitors()
				.get("Torsojnt_monitor");
		if (fsMon != null) {
			forces = new Vector3d(fsMon.FSAxialForces);
		}
		return forces;
	}

	public Vector3d getL1L2AxialForces() {
		Vector3d forces = new Vector3d();
		FrameSpringMonitor fsMon = (FrameSpringMonitor) getMonitors()
				.get("L1_L2_IVD_jnt_monitor");
		if (fsMon != null) {
			forces = new Vector3d(fsMon.FSAxialForces);
		}
		return forces;
	}

	public Vector3d getL2L3AxialForces() {
		Vector3d forces = new Vector3d();
		FrameSpringMonitor fsMon = (FrameSpringMonitor) getMonitors()
				.get("L2_L3_IVD_jnt_monitor");
		if (fsMon != null) {
			forces = new Vector3d(fsMon.FSAxialForces);
		}
		return forces;
	}

	public Vector3d getL3L4AxialForces() {
		Vector3d forces = new Vector3d();
		FrameSpringMonitor fsMon = (FrameSpringMonitor) getMonitors()
				.get("L3_L4_IVDjnt_monitor");
		if (fsMon != null) {
			forces = new Vector3d(fsMon.FSAxialForces);
		}
		return forces;
	}

	public Vector3d getL4L5AxialForces() {
		Vector3d forces = new Vector3d();
		FrameSpringMonitor fsMon = (FrameSpringMonitor) getMonitors()
				.get("L4_L5_IVDjnt_monitor");
		if (fsMon != null) {
			forces = new Vector3d(fsMon.FSAxialForces);
		}
		return forces;
	}

	public Vector3d getL5S1AxialForces() {
		Vector3d forces = new Vector3d();
		FrameSpringMonitor fsMon = (FrameSpringMonitor) getMonitors()
				.get("L5_S1_IVDjnt_monitor");
		if (fsMon != null) {
			forces = new Vector3d(fsMon.FSAxialForces);
		}
		return forces;
	}

	protected MasoudMillardLAM getMuscleMaterial(MuscleInfo mInfo) {
		MasoudMillardLAM m = new MasoudMillardLAM();
		m.setOptLength(mInfo.optFiberLen);
		m.setMaxForce(mInfo.maxForce / 1000 * 100 / 46.6);
		m.setMyFMTratioLit(mInfo.fiberRatio);
		m.setPassiveFraction(1);
		for (int i = 0; i < 11; i++) {
			String str = muscleGroupNames[i];
			if ((str + "_r").equals(mInfo.group)
					|| (str + "_l").equals(mInfo.group)) {
				m.setMySarcomereLenLit(sarcomereLengths[i]);
			}
		}
		// m.setMySarcomereLenLit(2.8);
		return m;
	}

	public void addFrameSprings() {

		FrameSpring fs;
		SolidJoint sj;
		RevoluteJoint rj;
		Point3d cv = new Point3d();
		RenderProps fsRenderProps = new RenderProps();
		fsRenderProps.setLineStyle(LineStyle.SPINDLE);
		fsRenderProps.setLineRadius(0.005);
		fsRenderProps.setLineColor(Color.green);
		Vector3d move = new Vector3d();
		// fsRenderProps.setAlpha (0.0);

		// OffsetLinearFrameMaterial mat =
		// new OffsetLinearFrameMaterial (500000, 600, 0, 0);
		// HanOfLiFrMa mat =
		// new HanOfLiFrMa (500000, 600, 0, 0);
		HeuerOffLinFM mat = new HeuerOffLinFM(500000, 600, 0, 0);
		mat.setStiffness(435000, 2420000, 523000);
		// mat.setStiffness (251000, 438000, 332000);

		fs = new FrameSpring("Hipjnt");
		fs.setFrameA(mech.rigidBodies().get("pelvis_rv"));
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("sacrum"));
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L5_S1_IVDjnt");
		fs.setFrameA(mech.rigidBodies().get("sacrum"));
		cv = new Point3d(
				mech.frameMarkers().get("L5_S1_IVDjnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));

		fs.setFrameB(mech.rigidBodies().get("L5"));
		cv = new Point3d(
				mech.frameMarkers().get("L4_L5_IVDjnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L4_L5_IVDjnt");
		fs.setFrameA(mech.rigidBodies().get("L5"));
		cv = new Point3d(
				mech.frameMarkers().get("L4_L5_IVDjnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("L4"));
		cv = new Point3d(
				mech.frameMarkers().get("L3_L4_IVDjnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L3_L4_IVDjnt");
		fs.setFrameA(mech.rigidBodies().get("L4"));
		cv = new Point3d(
				mech.frameMarkers().get("L3_L4_IVDjnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("L3"));
		cv = new Point3d(
				mech.frameMarkers().get("L2_L3_IVD_jnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L2_L3_IVD_jnt");
		fs.setFrameA(mech.rigidBodies().get("L3"));
		cv = new Point3d(
				mech.frameMarkers().get("L2_L3_IVD_jnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("L2"));
		cv = new Point3d(
				mech.frameMarkers().get("L1_L2_IVD_jnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("L1_L2_IVD_jnt");
		fs.setFrameA(mech.rigidBodies().get("L2"));
		cv = new Point3d(
				mech.frameMarkers().get("L1_L2_IVD_jnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setFrameB(mech.rigidBodies().get("L1"));
		cv = new Point3d(mech.frameMarkers().get("Torsojnt_A").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		fs = new FrameSpring("Torsojnt");
		fs.setFrameA(mech.rigidBodies().get("L1"));
		cv = new Point3d(mech.frameMarkers().get("Torsojnt_A").getLocation());
		fs.setAttachFrameA(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));

		fs.setFrameB(mech.rigidBodies().get("thorax"));
		cv = new Point3d(mech.frameMarkers().get("Torsojnt_B").getLocation());
		fs.setAttachFrameB(new RigidTransform3d(cv.x + move.x, cv.y + move.y,
				cv.z + move.z));
		fs.setMaterial(mat);
		fs.setInitialT21();
		mech.addFrameSpring(fs);

		for (FrameSpring fsr : mech.frameSprings())
			fsr.setRenderProps(fsRenderProps);
		mech.frameSprings().get("Hipjnt").setRenderProps(null);

		// Fixing the pelvis to the ground and setting abdomen's dynamic to
		// false
		mech.rigidBodies().get("sacrum").setDynamic(false);
		mech.rigidBodies().get("pelvis_rv").setDynamic(false);
		mech.rigidBodies().get("pelvis_lv").setDynamic(false);
		mech.rigidBodies().get("abdomen").setDynamic(false);

		// Adding Solid Joints
		sj = new SolidJoint(mech.rigidBodies().get("r_humerus"),
				mech.rigidBodies().get("thorax"));
		sj.setName("ribcage-r_humerus_jnt");
		mech.addBodyConnector(sj);
		sj = new SolidJoint(mech.rigidBodies().get("l_humerus"),
				mech.rigidBodies().get("thorax"));
		sj.setName("ribcage-l_humerus_jnt");
		mech.addBodyConnector(sj);

		// Adding Revolute Joints
		rj = new RevoluteJoint();
		rj.setName(("Abdjnt"));

		Point3d pntA = new Point3d(
				mech.frameMarkers().get("Abdjnt_A").getLocation());
		Point3d pntB = new Point3d(
				mech.frameMarkers().get("Abdjnt_B").getLocation());
		rj.setBodies(mech.rigidBodies().get("abdomen"),
				new RigidTransform3d(pntA.x, pntA.y, pntA.z),
				mech.rigidBodies().get("sacrum"),
				new RigidTransform3d(pntB.x, pntB.y, pntB.z));
		RenderProps.setVisible(rj, false);
		mech.addBodyConnector(rj);

	}

	public void addFixators() {

		// fixate ("T12", "L1", "T12_L1_Solidjnt");
		fixate("thorax", "L1", "T12_L1_Solidjnt");
		// fixate ("L1", "L2", "L1_L2_jnt");
		// fixate("L2", "L3", "L2_L3_Solidjnt");
		// fixate("L3", "L4", "L3_L4_Solidjnt");
		// fixate("L4", "L5", "L4_L5_Solidjnt");
		// fixate ("L5", "sacrum", "L5_S1_jnt");
	}

	public void fixate(String rb1, String rb2, String name) {
		SolidJoint sj = new SolidJoint(mech.rigidBodies().get(rb1),
				mech.rigidBodies().get(rb2));
		sj.setName(name);
		mech.addBodyConnector(sj);
	}

	public void doLevelCalculations() {
		doLevelCalculationFor(muscleGroupNames[0], LD);
		doAbdLevelCalculationFor(muscleGroupNames[1], RA);
		doAbdLevelCalculationFor(muscleGroupNames[2], IO);
		doAbdLevelCalculationFor(muscleGroupNames[3], EO);
		doLevelCalculationFor(muscleGroupNames[4], MF);
		doLevelCalculationFor(muscleGroupNames[5], Ps);
		doLevelCalculationFor(muscleGroupNames[6], QL);
		doLevelCalculationFor(muscleGroupNames[7], LTpL);
		doLevelCalculationFor(muscleGroupNames[8], LTpT);
		doLevelCalculationFor(muscleGroupNames[9], ILpL);
		doLevelCalculationFor(muscleGroupNames[10], ILpT);
	}

	public void doLevelCalculationFor(String mGroupName,
			ArrayList<ArrayList<String>> mGroup) {
		// mGroup.add (new ArrayList<String> ());

		for (FrameSpring fs : mech.frameSprings()) {
			// calculate midpoint of the FrameSpring
			double PCSA = 0;
			ArrayList<String> Level = new ArrayList<String>();
			Level.add(fs.getName());
			for (MuscleInfo mInfo : allMusclesInfo) {
				if ((mGroupName + "_r").equals(mInfo.group)
						|| (mGroupName + "_l").equals(mInfo.group)) {
					Vector3d levelPoint = new Vector3d();
					Vector3d rostralEnd;
					Vector3d caudalEnd;
					rostralEnd = new Vector3d(mech.frameMarkers()
							.get(fs.getFrameB().getName() + "_centroid")
							.getPosition());
					caudalEnd = new Vector3d(mech.frameMarkers()
							.get(fs.getFrameA().getName() + "_centroid")
							.getPosition());
					// Two special cases
					// if ("ribcage".equals (fs.getFrameB ().getName ())) {
					if ("thorax".equals(fs.getFrameB().getName())) {

						rostralEnd = new Vector3d(mech.frameMarkers()
								.get("T12_centroid").getPosition());
					}
					if ("sacrum".equals(fs.getFrameA().getName())) {
						caudalEnd.y = 0.99;
					}

					levelPoint.add(rostralEnd, caudalEnd);
					levelPoint.scale(0.5);

					// Find and save the name of the muscle fascicles that pass
					// through this level (midpoint)
					double sign = 0;
					Vector3d pnt1;
					Vector3d pnt2 = new Vector3d();
					for (AxialSpring as : mech.axialSprings()) {
						if (mInfo.name.equals(as.getName())) {
							pnt1 = new Vector3d(
									as.getFirstPoint().getPosition());
							pnt2 = new Vector3d(
									as.getSecondPoint().getPosition());
							sign = (pnt1.y - levelPoint.y)
									* (pnt2.y - levelPoint.y);
							if (sign < 0) {
								Level.add(as.getName());
								Muscle m = new Muscle();
								m = (Muscle) as;
								PCSA = PCSA + ((AxialMuscleMaterial) m
										.getMaterial()).getMaxForce();
							}
						}
					}
					for (MultiPointSpring mps : mech.multiPointSprings()) {
						if (mInfo.name.equals(mps.getName())) {
							pnt1 = new Vector3d(mps.getPoint(0).getPosition());
							pnt2 = new Vector3d(
									mps.getPoint(mps.numPoints() - 1)
											.getPosition());
							sign = (pnt1.y - levelPoint.y)
									* (pnt2.y - levelPoint.y);
							if (sign < 0) {
								Level.add(mps.getName());
								MultiPointMuscle mpm = new MultiPointMuscle();
								mpm = (MultiPointMuscle) mps;
								PCSA = PCSA + ((AxialMuscleMaterial) mpm
										.getMaterial()).getMaxForce();
							}
						}
					}
				}
			}
			Level.add(String.valueOf(PCSA));
			mGroup.add(Level);
		}
		saveLevelCalculationsInText(mGroupName, mGroup);
	}

	public void doAbdLevelCalculationFor(String mGroupName,
			ArrayList<ArrayList<String>> mGroup) {

		for (FrameSpring fs : mech.frameSprings()) {
			double PCSA = 0;
			ArrayList<String> Level = new ArrayList<String>();
			Level.add(fs.getName());
			for (MuscleInfo mInfo : allMusclesInfo) {
				if ((mGroupName + "_r").equals(mInfo.group)
						|| (mGroupName + "_l").equals(mInfo.group)) {
					for (AxialSpring as : mech.axialSprings()) {
						if (mInfo.name.equals(as.getName())) {
							Level.add(as.getName());

							PCSA = PCSA
									+ ((AxialMuscleMaterial) as.getMaterial())
											.getMaxForce();
						}
					}
					for (MultiPointSpring mps : mech.multiPointSprings()) {
						if (mInfo.name.equals(mps.getName())) {
							Level.add(mps.getName());
							MultiPointMuscle mpm = new MultiPointMuscle();
							mpm = (MultiPointMuscle) mps;
							PCSA = PCSA
									+ ((AxialMuscleMaterial) mpm.getMaterial())
											.getMaxForce();
						}
					}

				}

			}
			Level.add(String.valueOf(PCSA));
			mGroup.add(Level);
		}
		saveLevelCalculationsInText(mGroupName, mGroup);
	}

	public void saveLevelCalculationsInText(String mgName,
			ArrayList<ArrayList<String>> mGroup) {

		String fileName = ArtisynthPath.getSrcRelativePath(this,
				"out/LevelInfo/" + mgName + ".txt");
		File f = new File(fileName);
		try {
			File parent = f.getParentFile();
			parent.mkdirs();
			PrintStream out = new PrintStream(f);

			out.close();
		} catch (FileNotFoundException e) {
			System.err.println("Cannot open file '" + fileName + "' ("
					+ e.getMessage() + ")");
		}
	}

	private class ThetaController extends ControllerBase {

		private double theta;
		private RevoluteJoint rj;
		private RigidTransform3d myXBA;
		private RigidTransform3d rt;

		public ThetaController() {
			myXBA = new RigidTransform3d();
		}

		@Override
		public void apply(double t0, double t1) {

			theta = 0;
			rt = mech.rigidBodies().get("sacrum").getPose();
			myXBA = new RigidTransform3d(rt);
			rt = mech.rigidBodies().get("L5").getPose();
			myXBA.invert();
			myXBA.mul(new RigidTransform3d(rt));
			theta += myXBA.R.m10;

			for (int i = 1; i < 5; i++) {
				rt = mech.rigidBodies().get("L" + Integer.toString(i + 1))
						.getPose();
				myXBA = new RigidTransform3d(rt);
				rt = mech.rigidBodies().get("L" + Integer.toString(i))
						.getPose();
				myXBA.invert();
				myXBA.mul(new RigidTransform3d(rt));
				theta += myXBA.R.m10;
			}

			rj = (RevoluteJoint) mech.bodyConnectors().get("Abdjnt");
			rj.setTheta(-40 * theta);

		}

	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		Object source = e.getItemSelectable();

		if (source instanceof JCheckBox) {
			JCheckBox chk = (JCheckBox) source;
			String cmd = chk.getActionCommand();

			for (int i = 0; i < includedMuscleGroupNames.size(); i++) {
				String vcommand = includedMuscleGroupNames.get(i) + "Visible";
				boolean visible = chk.isSelected();

				if (vcommand.equals(cmd)) {
					setMuscleVisible(visible, i);
					rerender();
					return;
				}
			}
		}

	}
	
	protected void addTrackingMonitors(TrackingController t) {
		for (TargetPoint pt : t.getTargetPoints()) {
			TargetPointMonitor mon = new TargetPointMonitor(pt);
			mon.setName(pt.getName() + "_monitor");
			addMonitor(mon);
		}
	}

	protected Point3d[] targetInitPoints_r = {
			new Point3d(-0.16022106, 1.2668642, 0.099972676),
			new Point3d(-0.15734228, 1.2677512, 0.099965225),
			new Point3d(-0.15382473, 1.268729, 0.099980124),
			new Point3d(-0.15000129, 1.2697013, 0.099973882),
			new Point3d(-0.14577777, 1.2706786, 0.099965333),
			new Point3d(-0.14104307, 1.271639, 0.099962722),
			new Point3d(-0.13573368, 1.2725806, 0.099960278),
			new Point3d(-0.13014433, 1.2734181, 0.099956309),
			new Point3d(-0.12374709, 1.2744677, 0.10072682),
			new Point3d(-0.11431808, 1.2733436, 0.097082358),
			new Point3d(-0.1041209, 1.274201, 0.099958132),
			new Point3d(-0.094037266, 1.2741154, 0.10003758),
			new Point3d(-0.083411766, 1.273596, 0.10000309),
			new Point3d(-0.066522546, 1.2713864, 0.099912778),
			new Point3d(-0.041524222, 1.2629235, 0.099876108),
			new Point3d(-0.022828388, 1.2558636, 0.100001),
			new Point3d(-0.0061223274, 1.2491872, 0.1000706),
			new Point3d(-0.0039011489, 1.2491846, 0.099895609),
			new Point3d(0.018176048, 1.2352076, 0.099952324),
			new Point3d(0.036148728, 1.2241684, 0.09997453),
			new Point3d(0.064696612, 1.2048463, 0.1008641),

			//
			// new Point3d (-0.084976669, 1.2853213, 0.065785834),
			// new Point3d (-0.089625765, 1.285319, 0.066917663),
			// new Point3d (-0.094231584, 1.2848117, 0.068727918),
			// new Point3d (-0.098924585, 1.2827015, 0.074689033),
			// new Point3d (-0.10240503, 1.2805924, 0.080996135),
			// new Point3d (-0.10322708, 1.2778593, 0.088074272),
			// new Point3d (-0.10392071, 1.2758032, 0.094083004),
			// new Point3d (-0.10410207, 1.2742215, 0.10002286),
			// new Point3d (-0.10336226, 1.2726123, 0.10589806),
			// new Point3d (-0.10236346, 1.2704016, 0.1120077),
			// new Point3d (-0.10132299, 1.267464, 0.11826758),
			// new Point3d (-0.097154591, 1.2645443, 0.12395826),
			// new Point3d (-0.093513907, 1.2604536, 0.12986165),
			// new Point3d (-0.090515271, 1.2584975, 0.13224194),

			new Point3d(-0.1078577792439057, 1.2664133513007774,
					0.10001477040647389),
			new Point3d(-0.11687860326701303, 1.264275585326783,
					0.100051362820949),
			new Point3d(-0.12622477372923935, 1.264698649215945,
					0.1000746854334188),
			new Point3d(-0.13483292551335307, 1.266246325787903,
					0.10008338319763073),
			new Point3d(-0.1427381691313984, 1.2672446850314265,
					0.10005732222373011),
			new Point3d(-0.1504294355902736, 1.2672092147789655,
					0.10001777277632164),
			new Point3d(-0.15802384706640177, 1.2665671617259306,
					0.09997363073721006),
			new Point3d(-0.1639053692553805, 1.264723641250553,
					0.09991894574222455),
			new Point3d(-0.1673113180183604, 1.2626081711428874,
					0.09981033829916865),
			new Point3d(-0.16791085499055666, 1.2616852206456217,
					0.0997019839996964),
			new Point3d(-0.16535074587109955, 1.2623264290640965,
					0.0995963421162811),
			new Point3d(-0.1613939619764294, 1.2647880117207058,
					0.09967108014574134),
			new Point3d(-0.15739865230982425, 1.2664173390306446,
					0.09985948172871535),
			new Point3d(-0.15456256379111616, 1.2666145577430503,
					0.10006145247979709),
			new Point3d(-0.15355133059910567, 1.2658438014247348,
					0.10018236108591293),
			new Point3d(-0.15425317627661006, 1.2649542529394604,
					0.1001804442893283),
			new Point3d(-0.15608375322955137, 1.2645142779473213,
					0.10006612734034133),
			new Point3d(-0.15847332072870662, 1.2644128135520523,
					0.09989195962070861),
			new Point3d(-0.16112916453896411, 1.2643701099742075,
					0.09971049904324367),
			new Point3d(-0.16392402770475603, 1.2642452449727928,
					0.09955502491994443),
			new Point3d(-0.16674674938396353, 1.2640319238959565,
					0.09943853658928965),
			new Point3d(-0.16961727558149473, 1.26317078023978,
					0.09937001004539923),
			new Point3d(-0.17270169361241977, 1.2616963082740487,
					0.09934358712610597),
			new Point3d(-0.17601040771134355, 1.2601648244506094,
					0.09934418668427135),
			new Point3d(-0.17945901276131332, 1.2586527052508598,
					0.09935758615407954),
			new Point3d(-0.18297749626759688, 1.255910072725995,
					0.09940478541390552),
			new Point3d(-0.18683622011704618, 1.2530187506564308,
					0.09958506940922997),
			new Point3d(-0.19109176144689333, 1.2503577896235398,
					0.099867268591616),
			new Point3d(-0.19561790858794645, 1.248242489121907,
					0.1001308583024766),
			new Point3d(-0.2001452024329362, 1.2465366511907285,
					0.10015574484158382),
			new Point3d(-0.20447267307926237, 1.245096116695757,
					0.0998840216997797),
			new Point3d(-0.20807696223674454, 1.243886068882863,
					0.09968191781062859),
			new Point3d(-0.2098372269907965, 1.243020733545212,
					0.09943908547596247),
			new Point3d(-0.20978701391334503, 1.242834709610193,
					0.09926570892309718),
			new Point3d(-0.20848688187061784, 1.2433845235055663,
					0.09931193897415361),
			new Point3d(-0.20508389029298343, 1.243993772721076,
					0.09964697090412258),
			new Point3d(-0.2009489813541566, 1.245040227181764,
					0.10005295999181947),
			new Point3d(-0.19626873051043625, 1.2460210480637441,
					0.1003410598358333),
			new Point3d(-0.19090584981671818, 1.2467982123164596,
					0.10041313191133924),
			new Point3d(-0.18515496841539905, 1.2479215758485103,
					0.10024642057198409),
			new Point3d(-0.1795402962457595, 1.2494635899884179,
					0.0998051099402636),
			new Point3d(-0.17433943878475913, 1.2510329370844564,
					0.09923939030153889),
			new Point3d(-0.16990139714420416, 1.252327342931177,
					0.09887153178701857),
			new Point3d(-0.16657044026399942, 1.2530001455984596,
					0.09896832302532475),
			new Point3d(-0.1644764057593235, 1.2531230892991265,
					0.09962733836108345),
			new Point3d(-0.16356073034912172, 1.2532894406211694,
					0.10052822469446951),
			new Point3d(-0.16233274961124983, 1.2527178816112758,
					0.10114671251172964),
			new Point3d(-0.15968239088801414, 1.2521577365850014,
					0.10109382735977424),
			new Point3d(-0.15575248113533038, 1.252672971142207,
					0.10020504675506924),
			new Point3d(-0.15061397400388002, 1.2540507866129347,
					0.09892992700290579),
			new Point3d(-0.145946422503747, 1.2573282992225638,
					0.0978644887040822),
			new Point3d(-0.14331202855136332, 1.2621674382346275,
					0.09743275384706349),
			new Point3d(-0.14215623205335573, 1.2655421735335568,
					0.09772884510742799),
			new Point3d(-0.1426337012204028, 1.26672475065125,
					0.09849343964658043),
			new Point3d(-0.1451143510692972, 1.2662810632635177,
					0.09936231594743944),
			new Point3d(-0.14940705676518012, 1.265008647813105,
					0.1001264386749962),
			new Point3d(-0.15396814239520762, 1.263463545498395,
					0.10031786089563656),
			new Point3d(-0.15477474234388605, 1.2622228925070327,
					0.10005675688960584),
			new Point3d(-0.14984963681687843, 1.2611698962838298,
					0.0995668555484575),
			new Point3d(-0.1417218252333887, 1.2623285828291846,
					0.09946186223085163),
			new Point3d(-0.1328815829641391, 1.2638346471686301,
					0.09946902899958152),
			new Point3d(-0.12615499842518846, 1.2651262922299014,
					0.09966276790101665),
			new Point3d(-0.12270852212046898, 1.265660593387626,
					0.10004391070645034),
			new Point3d(-0.1213217625265631, 1.2650685940586495,
					0.10030942540131939),
			new Point3d(-0.12138127930936891, 1.264476723710991,
					0.10031572554512727),
			new Point3d(-0.12161468861840419, 1.2640206113303818,
					0.10006656689671516),
			new Point3d(-0.12035768254278184, 1.2635477236473598,
					0.0997301639702285),
			new Point3d(-0.11854918362857372, 1.263649044266189,
					0.09950700787756053),
			new Point3d(-0.11711774371184437, 1.263975112788123,
					0.09954797093820897),
			new Point3d(-0.11657820048028913, 1.2642576441977924,
					0.0998309398062497),
			new Point3d(-0.11698966775478992, 1.2643338183680661,
					0.10015939838569346),
			new Point3d(-0.11844250936202436, 1.2664313457205856,
					0.10032964123756109),
			new Point3d(-0.12014909112167949, 1.2698573684184107,
					0.10026633032757813),
			new Point3d(-0.12143501320362525, 1.2731843972783123,
					0.09999165692446114),
			new Point3d(-0.12196513050495442, 1.274789477266607,
					0.09963871396167674),
			new Point3d(-0.12201044613938396, 1.2751901236594136,
					0.09933937371766345),
			new Point3d(-0.1218941078922253, 1.275032124401975,
					0.09920776336315415),
			new Point3d(-0.12182043317375403, 1.274828779726007,
					0.09931041152825065),
			new Point3d(-0.12183779299986175, 1.274828449262685,
					0.09961223450692303),
			new Point3d(-0.12184815434856044, 1.275015724560583,
					0.1000116106383051),
			new Point3d(-0.12166212308658388, 1.2751783690798846,
					0.10039040860342856),
			new Point3d(-0.12124062171860502, 1.2752423641032065,
					0.10070789933293688),
			new Point3d(-0.12055876270805013, 1.2752103022093404,
					0.10093392531162332),
			new Point3d(-0.1184952216071411, 1.274502594244319,
					0.10107510622330113),
			new Point3d(-0.11352194980401384, 1.2725696944582483,
					0.10097404558101723),
			new Point3d(-0.10624741441438751, 1.2719201697834717,
					0.10075816463541518),
			new Point3d(-0.09881715605155784, 1.2734771703622865,
					0.10075301722458212),
			new Point3d(-0.09157610338959289, 1.2744613370838453,
					0.10047315721994159),
			new Point3d(-0.08541960614049568, 1.274241520995008,
					0.1000738197357219),
			new Point3d(-0.08130439587571431, 1.2729293906071533,
					0.09971454277279405),
			new Point3d(-0.07908549714016001, 1.2715597095452766,
					0.09956088232817889),
			new Point3d(-0.07783437909137875, 1.2706128278989621,
					0.0997206292056118),
			new Point3d(-0.0764171227438008, 1.270173621246084,
					0.10020940026917342),
			new Point3d(-0.0741570529592047, 1.2700637728016941,
					0.10087572572211985),
			new Point3d(-0.07062044019839635, 1.2707876702811343,
					0.10144594741923589),
			new Point3d(-0.06594740984735561, 1.2714770836259632,
					0.10177195220899819),
			new Point3d(-0.06061466691361919, 1.2718993605605509,
					0.10178368456250297),
			new Point3d(-0.055183341928194275, 1.2716953006500313,
					0.10150549634736786),
			new Point3d(-0.05021337845588196, 1.2709302015825183,
					0.10105773931584379),
			new Point3d(-0.04613940557301727, 1.2696945169246339,
					0.10061740829630297),
			new Point3d(-0.04308298224356276, 1.268132987830791,
					0.10036012956420864),
			new Point3d(-0.04058868902209167, 1.2667387885734436,
					0.10043514927837298),
			new Point3d(-0.03811374530246867, 1.2655886671692604,
					0.10086019796805641),
			new Point3d(-0.035186309999219643, 1.2646449051409616,
					0.10154112163750993),
			new Point3d(-0.031569466233379744, 1.2638660553664245,
					0.10231484619577662),
			new Point3d(-0.02726152560546103, 1.2631154174293235,
					0.10300855867383475),
			new Point3d(-0.022556839696141917, 1.262180910886444,
					0.10347478143798795),
			new Point3d(-0.018013806699185207, 1.2608626071710163,
					0.10362378268236519),
			new Point3d(-0.014324563618270688, 1.2590082494013786,
					0.10341817436813953),
			new Point3d(-0.0119049409127416, 1.2567813382695223,
					0.10289978849349324),
			new Point3d(-0.01051714013138962, 1.2543722482196582,
					0.10217235073587656),
			new Point3d(-0.009370744770003889, 1.252142330792651,
					0.10141248034034324),
			new Point3d(-0.0074266067356981175, 1.2505168966298545,
					0.10081401558040283),
			new Point3d(-0.0038276066822590295, 1.249605157658285,
					0.10052302938858555),
			new Point3d(0.0014741579987850187, 1.2490521593422192,
					0.10059577922832905),
			new Point3d(0.0073646991441911214, 1.2482307149050165,
					0.10096349897142623),
			new Point3d(0.012161705767889268, 1.246737344580494,
					0.10148624634468427),
			new Point3d(0.014499981201767766, 1.2446286688127595,
					0.10199672685362596),
			new Point3d(0.014296433753673189, 1.2421914990208354,
					0.10226011318358745),
			new Point3d(0.013167014264892607, 1.2399638018007653,
					0.10209216382947782),
			new Point3d(0.013165898609663308, 1.2383333348574848,
					0.10152237325626703),
			new Point3d(0.015317313399584679, 1.2372361789247568,
					0.10078375781594724),
			new Point3d(0.018889769772012576, 1.2363462797418712,
					0.10009725129249349),
			new Point3d(0.022184647162183914, 1.2352875575055537,
					0.09961818273177556),
			new Point3d(0.023800480015060722, 1.2340617901258162,
					0.09942291148987614),
			new Point3d(0.02348350990117945, 1.2329763250333354,
					0.09947693892884803),
			new Point3d(0.02174895866194771, 1.2319973858833528,
					0.09969675129609483),
			new Point3d(0.01995699577735461, 1.2316800108578454,
					0.10000682586318013),
			new Point3d(0.018821851706877335, 1.2317590776892917,
					0.10033236375554802),
			new Point3d(0.018358696137733152, 1.2322891519156611,
					0.10062456037832655),
			new Point3d(0.018052102790575876, 1.2331177020473012,
					0.10084417681599918),
			new Point3d(0.01738911507201634, 1.2340959903150157,
					0.10096609283112674),
			new Point3d(0.01613264025967451, 1.2351697365038266,
					0.10098650187762098),
			new Point3d(0.014384141465412802, 1.2363700384308023,
					0.10091965008720116),
			new Point3d(0.012435699715371826, 1.2377293888151086,
					0.10079079488762872),
			new Point3d(0.010547524935836595, 1.2391606687227052,
					0.1006271538343035),
			new Point3d(0.008673251432012587, 1.2405127564697884,
					0.10041860487199808),
			new Point3d(0.006495149636918299, 1.2417228396014481,
					0.10017289832449829),
			new Point3d(0.003828137019226152, 1.2429074736082657,
					0.09991293464877042),
			new Point3d(7.558242694289236E-4, 1.2441398221876687,
					0.09966295581280035),
			new Point3d(-0.00237539876270037, 1.245484510797278,
					0.09943412502799752),
			new Point3d(-0.005246674453024474, 1.2468963937711972,
					0.09925690414971566),
			new Point3d(-0.007517516642282628, 1.248457957781569,
					0.0991636667691257),
			new Point3d(-0.009093022346318036, 1.249967150507024,
					0.09916710407535832),
			new Point3d(-0.010238646944436172, 1.2512509432444392,
					0.09923074721371263),
			new Point3d(-0.01141009882660683, 1.2521758494196955,
					0.09928468768832971),
			new Point3d(-0.013018115853022578, 1.2527102514159199,
					0.09924715421358193),
			new Point3d(-0.015014206232932162, 1.2531364084942522,
					0.0990861885894138),
			new Point3d(-0.016994999458340074, 1.2536190156403155,
					0.0988449696439541),
			new Point3d(-0.018568332667853214, 1.2541825417858548,
					0.09862148209770541),
			new Point3d(-0.019863119402204688, 1.2544686486660916,
					0.09853688402731482),
			new Point3d(-0.02130675617604531, 1.2545533651127114,
					0.09865353000397147),
			new Point3d(-0.023489517631835835, 1.2545964822298332,
					0.09898024868352773),
			new Point3d(-0.026314884908924495, 1.2549262953599991,
					0.0994359716842641),
			new Point3d(-0.02909910663004456, 1.2556718506606201,
					0.09986323883752618),
			new Point3d(-0.03101773838134667, 1.2567997808358768,
					0.10007542278613214),
			new Point3d(-0.03205908118220388, 1.2579134326848773,
					0.100014413017283),
			new Point3d(-0.0328281572044738, 1.258733869723872,
					0.09969560311005225),
			new Point3d(-0.03415286929134033, 1.2591791833155317,
					0.09923800958858418),
			new Point3d(-0.03644931769975269, 1.2593801392583734,
					0.09884738470569442),
			new Point3d(-0.03931860324812241, 1.259719118449763,
					0.0987116301733947),
			new Point3d(-0.04198562084479593, 1.2603476371367597,
					0.09892205798570843),
			new Point3d(-0.043939613982950515, 1.261386322148722,
					0.0994202403298997),
			new Point3d(-0.045174181086276716, 1.2625101424551048,
					0.09999538366548053),
			new Point3d(-0.0462288310568698, 1.2634174866167243,
					0.10034966971368954),
			new Point3d(-0.04825681066705732, 1.2632158781410159,
					0.10025072592665117),
			new Point3d(-0.05194807134899677, 1.2624480753030283,
					0.09978477771972553),
			new Point3d(-0.056620860991465724, 1.2624829699405526,
					0.09922681848343647),
			new Point3d(-0.06125224588706964, 1.2635583016894731,
					0.09888638807049834),
			new Point3d(-0.06513956497164537, 1.2652481128049728,
					0.09892874045992313),
			new Point3d(-0.06880825348336016, 1.266112661340491,
					0.09938423478129774),
			new Point3d(-0.07329252022045517, 1.2661158834220652,
					0.10009823564182425),
			new Point3d(-0.07902377268867776, 1.266130619776854,
					0.10083155380147904),
			new Point3d(-0.08590237773302117, 1.2666592100760594,
					0.10128849280330471),
			new Point3d(-0.09373280504900254, 1.2675420123923347,
					0.1013360649636666),
			new Point3d(-0.10222303402276871, 1.268473500778589,
					0.10102483636677881),
			new Point3d(-0.11092560671231648, 1.2694198543380666,
					0.10052278024101687),
			new Point3d(-0.11945813634055366, 1.270031593703885,
					0.10007952454481682),
			new Point3d(-0.12718946289483463, 1.27088781653218,
					0.09983896253416583),
			new Point3d(-0.1334640614581321, 1.271970634747248,
					0.0998379205063883),
			new Point3d(-0.13805964957062605, 1.272870549500456,
					0.10005761129616157),
			new Point3d(-0.1413941481757987, 1.2730450951041745,
					0.10041112647573873),
			new Point3d(-0.14411182476626255, 1.2722591143612134,
					0.10078445945783678),
			new Point3d(-0.14677850923677185, 1.2708937623685843,
					0.10108566337700313),
			new Point3d(-0.14966044636226525, 1.2695149758097133,
					0.1012661817171589),
			new Point3d(-0.15261517434624344, 1.2684772401938742,
					0.10132006219932273),
			new Point3d(-0.15526206394277292, 1.2678483386712673,
					0.10127228561314792),
			new Point3d(-0.15708393221181952, 1.267467180479846,
					0.10117840805369835),
			new Point3d(-0.1577389790505596, 1.267187030965101,
					0.10100247830623758),
			new Point3d(-0.1576070185859959, 1.267189875443916,
					0.10072385902385465),
			new Point3d(-0.15718185928695616, 1.2674074351306583,
					0.1003560060961273),
			new Point3d(-0.15670871240365714, 1.2675846711758982,
					0.09992080512268287),
			new Point3d(-0.15630209728553282, 1.2676411498362952,
					0.09949838392958954),
			new Point3d(-0.15597408594610918, 1.2676704725624521,
					0.0991501269397583),
			new Point3d(-0.1524982918234889, 1.2654512464804826,
					0.09904921827810456),
			new Point3d(-0.14612527447838874, 1.2654610515321132,
					0.09914704300072702),
			new Point3d(-0.13557426099734024, 1.266266550857915,
					0.09943050788211326),
			new Point3d(-0.12086559325591316, 1.2678893991884443,
					0.099778718963003),
			new Point3d(-0.10422475502116191, 1.2717659072177685,
					0.10012790875363697),
			new Point3d(-0.08922122916978174, 1.2732681417668383,
					0.10040170164550373),
			new Point3d(-0.07882147000195244, 1.2716188887789814,
					0.1004982258740458),
			new Point3d(-0.07385558943595266, 1.2683165948340607,
					0.1003770846343774),
			new Point3d(-0.07247350364159555, 1.2653654882494163,
					0.10007491787335437),
			new Point3d(-0.0720639580844219, 1.263693620653939,
					0.09971380561572993),
			new Point3d(-0.07083182267316601, 1.2631879556350172,
					0.09946128736269308),
			new Point3d(-0.06825555667617615, 1.2636367511694975,
					0.09942798352676598),
			new Point3d(-0.06517343916624624, 1.2643338504281505,
					0.09963675454696906),
			new Point3d(-0.06311367204369034, 1.2645201830627624,
					0.099998970161464),
			new Point3d(-0.06293789381404702, 1.2642563682933308,
					0.10035400533119529),
			new Point3d(-0.06444405349260982, 1.2640585127094788,
					0.10055782126947438),
			new Point3d(-0.06685266798821442, 1.264249815646496,
					0.10053481301469089),
			new Point3d(-0.06944981608009235, 1.2648375809510044,
					0.10032122030763431),
			new Point3d(-0.07186941621699527, 1.2656315787494692,
					0.10000423256389519),
			new Point3d(-0.07406126499221045, 1.2665118040123304,
					0.09970116325231144),
			new Point3d(-0.07613756800314714, 1.2674122110371129,
					0.09950798922427867),
			new Point3d(-0.07795261590684302, 1.2686469405850769,
					0.09946183373966287),
			new Point3d(-0.07909055528018182, 1.2702957667092434,
					0.0995559804970825),
			new Point3d(-0.07915709184307504, 1.2721318365566088,
					0.0997232127781768),
			new Point3d(-0.07813690222324335, 1.2738536999956467,
					0.09987537000406146),
			new Point3d(-0.07630712917548096, 1.2750875185892383,
					0.09994463307602887),
			new Point3d(-0.07663658026870361, 1.2712839871937336,
					0.09995159086574948),
			new Point3d(-0.08150253550102148, 1.266562939196061,
					0.1000634776925999),
			new Point3d(-0.08988264820527735, 1.2638612773541824,
					0.10024044134128572),
			new Point3d(-0.09799320599407867, 1.2636964549128218,
					0.1000623693860422),
			new Point3d(-0.10088422515610787, 1.2638115194872859,
					0.09998150223800235),
			new Point3d(-0.09981322516144531, 1.2645141035056653,
					0.10003401106801832),
			new Point3d(-0.09767200373655908, 1.26540878668302,
					0.10010534305711373),
			new Point3d(-0.09662448954460315, 1.2656414370108984,
					0.10009543005158657),
			new Point3d(-0.0972368550513024, 1.2653175522481626,
					0.09999701752792536),
			new Point3d(-0.09841360559779734, 1.2647724346211024,
					0.09986780631927235),
			new Point3d(-0.09894312764685458, 1.2644107538034626,
					0.09978963742656798),
			new Point3d(-0.09679624029130618, 1.2640624488571943,
					0.09991907968425778),
			new Point3d(-0.09230992500121124, 1.2640642583694972,
					0.10007731014642635),
			new Point3d(-0.08802596251281843, 1.2646219181481235,
					0.10021700534462841),
			new Point3d(-0.08542241925922661, 1.2648913164519342,
					0.10026233356972947),
			new Point3d(-0.08232634232552202, 1.2642370005541996,
					0.10010919220410974),
			new Point3d(-0.08023309997870204, 1.2640651503911327,
					0.0999153164947712),
			new Point3d(-0.07624750145711566, 1.2634530225549607,
					0.09989408320876901),
			new Point3d(-0.07194330922471528, 1.2635460907206477,
					0.09994576442328053),
			new Point3d(-0.06844001144764848, 1.263838387248306,
					0.10010537991179372),
			new Point3d(-0.06575486883921984, 1.2640211350376345,
					0.10017552567938756),
			new Point3d(-0.0638916912085736, 1.264041451720289,
					0.10017212851532177),
			new Point3d(-0.06045259047625237, 1.2633384369467173,
					0.10005658180617016),
			new Point3d(-0.057452545332350814, 1.2631468295476955,
					0.09995430185360801),
			new Point3d(-0.05552832190578434, 1.2631078340879425,
					0.09989984439490046),
			new Point3d(-0.05505172498809383, 1.2631041373675587,
					0.09993092335270293),
			new Point3d(-0.05586673252915884, 1.2632472348722004,
					0.10005251729317126),
			new Point3d(-0.05743393584509429, 1.2636564607789265,
					0.1001883107533549),
			new Point3d(-0.058932477318176815, 1.2641602398340075,
					0.10016756136712109),
			new Point3d(-0.05942861067057388, 1.2643283296945342,
					0.10013672206924173),
			new Point3d(-0.05928562252845823, 1.2644828189066795,
					0.10009517627137954),
			new Point3d(-0.059110594843169904, 1.2645318841931767,
					0.10002500438293982),
			new Point3d(-0.059110934320756855, 1.2643257196823114,
					0.09994710878754),
			new Point3d(-0.05916394357250502, 1.2640155297283633,
					0.09990871487382968),
			new Point3d(-0.05912939740648981, 1.2638776095929165,
					0.09994064517817654),
			new Point3d(-0.059019370006283225, 1.2640073767897946,
					0.10003219994576006),
			new Point3d(-0.05894817410185802, 1.264239238235797,
					0.10013212389288714),
			new Point3d(-0.05813673985362251, 1.2667494024885195,
					0.10022928713927663),
			new Point3d(-0.05685381133731116, 1.2685859375734865,
					0.10025412840489356),
			new Point3d(-0.055272915096425644, 1.2694787536460963,
					0.10018623533676989),
			new Point3d(-0.05372945401750324, 1.26942938433873,
					0.10004291462355262),
			new Point3d(-0.0525902384867596, 1.2688895604535069,
					0.09991040508974847),
			new Point3d(-0.05199361340263542, 1.2682737456636455,
					0.09989782657020743),
			new Point3d(-0.05168669308767881, 1.2678167144062327,
					0.10006479241194983),
			new Point3d(-0.05117234203964521, 1.2675765579520912,
					0.10036774916370766),
			new Point3d(-0.05009023265796611, 1.2674522278114217,
					0.1006559226376537),
			new Point3d(-0.04855343732920779, 1.267206086885736,
					0.10070904166371344),
			new Point3d(-0.04710441620318888, 1.2666332322729896,
					0.10036402787385085),
			new Point3d(-0.046451029050809334, 1.2655210078310184,
					0.09968498744377342),
			new Point3d(-0.04662612492663265, 1.264481736452833,
					0.09900864016093681),
			new Point3d(-0.046936764171499445, 1.26392730652568,
					0.09874569863189413),
			new Point3d(-0.0473681842512153, 1.2630950687742677,
					0.09914483828183485),
			new Point3d(-0.048120481642899914, 1.262686685843983,
					0.10004812362406988),
			new Point3d(-0.049531316295137816, 1.2621369332848624,
					0.1009740876863968),
			new Point3d(-0.05272832234783, 1.2606211639724176,
					0.10155599191752396),
			new Point3d(-0.05760665682711165, 1.2600006010699383,
					0.10155031724579631),
			new Point3d(-0.06365568148365897, 1.260639468419402,
					0.1009317269238119),
			new Point3d(-0.07051943678218504, 1.2625623569900444,
					0.10000266469097153),
			new Point3d(-0.07849469422170272, 1.2642225265885603,
					0.09917456268148817),
			new Point3d(-0.08777694843669653, 1.265711372308634,
					0.0987386409087921),
			new Point3d(-0.09818116155570823, 1.2672778562807925,
					0.09878854001344682),
			new Point3d(-0.10917596180408308, 1.2687868167725005,
					0.09924774391479488),
			new Point3d(-0.11998902432112576, 1.2702333872730736,
					0.09993307654708464),
			new Point3d(-0.1297764548520684, 1.271269016406588,
					0.10063167901150104),
			new Point3d(-0.13773162166410852, 1.2717078111439866,
					0.10104138630886954),
			new Point3d(-0.14196176946079664, 1.2709823884313907,
					0.10100755285133135),
			new Point3d(-0.14241378077845349, 1.2702484180476596,
					0.1006681873710462),
			new Point3d(-0.1404232143135162, 1.270411859072003,
					0.10024197439170997),
			new Point3d(-0.13817894095363048, 1.2712258136436054,
					0.09997658524906258),
			new Point3d(-0.136432043271807, 1.2715847843547883,
					0.09978082253867956),
			new Point3d(-0.1356277044001946, 1.271551276984038,
					0.09968811803373732),
			new Point3d(-0.13579444598829732, 1.2711089052200912,
					0.09970157708128463),
			new Point3d(-0.13653049173746962, 1.2708063074652,
					0.09981602786687929),
			new Point3d(-0.1372594789639168, 1.2708486216925658,
					0.10000591843427507),
			new Point3d(-0.1378439375412962, 1.2706488282714838,
					0.10022458241252227),
			new Point3d(-0.13841117745645026, 1.2705204748945398,
					0.10040090249352275),
			new Point3d(-0.13935652158693462, 1.2699610147154226,
					0.1004833742831475),
			new Point3d(-0.14087405449587176, 1.2695095567579497,
					0.1004368053871517),
			new Point3d(-0.1429282989087069, 1.2690639053313184,
					0.10027840598990452),
			new Point3d(-0.14526471617844172, 1.2689002850118987,
					0.10005983154689814),
			new Point3d(-0.1477634152455134, 1.2684663900553412,
					0.09984989866574369),
			new Point3d(-0.15026462497911955, 1.2682987424987224,
					0.09968902498942997),
			new Point3d(-0.1526417519835604, 1.2681343101205593,
					0.09961778182203201),
			new Point3d(-0.15488554115077172, 1.2679294456720778,
					0.09963758485651468),
			new Point3d(-0.15693393422004662, 1.2678503080299106,
					0.09972564538314431),
			new Point3d(-0.1587206907482203, 1.2677815215578492,
					0.09983333551302394),
			new Point3d(-0.16032767153584887, 1.2673802414174788,
					0.09990291048688817),
			new Point3d(-0.16180005232696587, 1.2669259937069677,
					0.0998897818050177),
			new Point3d(-0.16309269876383373, 1.2665638127626762,
					0.09977525660921466),
			new Point3d(-0.16422321484024302, 1.2661446972400952,
					0.09956990116724491),
			new Point3d(-0.1652672877971104, 1.2656453285658995,
					0.09930662027471551),
			new Point3d(-0.16627020048208496, 1.265190530803414,
					0.09903244814990211),
			new Point3d(-0.16721785650098533, 1.2648508406639107,
					0.0987935909338151),
			new Point3d(-0.16809349381893612, 1.2646008354929645,
					0.09862509180900098),
			new Point3d(-0.16890007854712222, 1.2644074975099941,
					0.09854608226425932),
			new Point3d(-0.1696382150545173, 1.2642764020840382,
					0.0985573614196651),
			new Point3d(-0.17028438526794654, 1.264220251546046,
					0.09864300206657806),
			new Point3d(-0.17079045503315948, 1.2642465043465052,
					0.09877298362399477),
			new Point3d(-0.171132646951565, 1.264263554099767,
					0.09891456342609893),
			new Point3d(-0.17131790046893391, 1.2641816499765433,
					0.09903992075569515),
			new Point3d(-0.17138545343621026, 1.2639427681482334,
					0.09913262438766052),
			new Point3d(-0.17135022474122263, 1.263693132560396,
					0.09918783250304627),
			new Point3d(-0.1712354406328889, 1.2634892992940625,
					0.09921342405670341),
			new Point3d(-0.17105112419436433, 1.2633653116219994,
					0.09924932951313666),
			new Point3d(-0.17080437399502182, 1.2633349551743154,
					0.09931137888575078),
			new Point3d(-0.17046722971225028, 1.2633616733585928,
					0.09931834746629448),
			new Point3d(-0.1657934196384452, 1.2600832982362462,
					0.09944614498560282),
			new Point3d(-0.15665341995370166, 1.2582239759493723,
					0.09968467777828277),
			new Point3d(-0.14323188991522298, 1.260023040766247,
					0.10005878913317624),
			new Point3d(-0.1254209255061396, 1.2643076612806732,
					0.10037207839282729),
			new Point3d(-0.10806662907381274, 1.26931674059583,
					0.10030011043644614),
			new Point3d(-0.09412327457039693, 1.2710318540614678,
					0.1000623003860908),
			new Point3d(-0.08596415501656457, 1.269653914053409,
					0.09974250511967739),
			new Point3d(-0.08306315078980632, 1.2674517335487547,
					0.09938897632611764),
			new Point3d(-0.08302005378080561, 1.2660085905092797,
					0.099062222750396),
			new Point3d(-0.08340006253046174, 1.2656501939331335,
					0.09881635809898431),
			new Point3d(-0.08271213684420294, 1.2659591601469282,
					0.09870126989402316),
			new Point3d(-0.08045867708512695, 1.2671411428128037,
					0.09873715084408832),
			new Point3d(-0.07662051517959967, 1.2692094298734125,
					0.09885482572480506),
			new Point3d(-0.0721967058422076, 1.2704495245425482,
					0.09900879296261551),
			new Point3d(-0.06907282711620052, 1.2703795558316562,
					0.099108121184061),
			new Point3d(-0.06823624343265249, 1.2693105480703333,
					0.09908280263358756),
			new Point3d(-0.06911046286260243, 1.2681412112507175,
					0.09893425641715331),
			new Point3d(-0.07016983209455278, 1.2676327610825784,
					0.09871468237989106),
			new Point3d(-0.0701578426088293, 1.2679539181304063,
					0.0985398922111732),
			new Point3d(-0.06896024412446, 1.2686763218185495,
					0.0985084995613804),
			new Point3d(-0.06759338858546148, 1.2691596828105915,
					0.09863526286169644),
			new Point3d(-0.06714886159829116, 1.2690877475966678,
					0.09885888193439611),
			new Point3d(-0.06790494021107966, 1.268718218863086,
					0.099084141523688),
			new Point3d(-0.06924076457922272, 1.2685055381193184,
					0.09920564552220536),
			new Point3d(-0.07029471542220483, 1.2686947945632725,
					0.09916073698741361), };

	protected Point3d[] targetInitPoints_l = {
			new Point3d(-0.15736449, 1.2677175, -0.10003477),
			new Point3d(-0.15385818, 1.2686965, -0.10001987),
			new Point3d(-0.15003634, 1.2696604, -0.10002611),
			new Point3d(-0.14580963, 1.2706344, -0.10003466),
			new Point3d(-0.14106973, 1.2715997, -0.10003727),
			new Point3d(-0.13575498, 1.2725459, -0.10003972),
			new Point3d(-0.1301604, 1.2733868, -0.10004369),
			new Point3d(-0.12401658, 1.2737948, -0.099271862),
			new Point3d(-0.11255936, 1.27489, -0.10290393),
			new Point3d(-0.1040627, 1.2742504, -0.10004185),
			new Point3d(-0.094036334, 1.2740866, -0.099962415),
			new Point3d(-0.083356811, 1.2736796, -0.099996882),
			new Point3d(-0.066589408, 1.2712788, -0.10008718),
			new Point3d(-0.041639239, 1.2627942, -0.10012382),
			new Point3d(-0.022847545, 1.255879, -0.099999003),
			new Point3d(-0.006060514, 1.2492934, -0.099929363),
			new Point3d(-0.0039373351, 1.2492327, -0.10010438),
			new Point3d(0.018242328, 1.2352312, -0.10004766),
			new Point3d(0.036199222, 1.2241887, -0.10002546),
			new Point3d(0.0653658, 1.2059401, -0.099131789),
			new Point3d(0.080382145, 1.1871811, -0.09987133),

			//
			// new Point3d (-0.085321785, 1.2577053, -0.13229809),
			// new Point3d (-0.08897151, 1.2590479, -0.13134833),
			// new Point3d (-0.09269149, 1.2604957, -0.12978243),
			// new Point3d (-0.096881648, 1.2642335, -0.12444599),
			// new Point3d (-0.10074356, 1.2677585, -0.11858475),
			// new Point3d (-0.10222793, 1.2706843, -0.11179449),
			// new Point3d (-0.10327604, 1.2726883, -0.1058917),
			// new Point3d (-0.1040841, 1.2742294, -0.09997714),
			// new Point3d (-0.10380464, 1.2758458, -0.094075316),
			// new Point3d (-0.10302357, 1.2780248, -0.08784587),
			// new Point3d (-0.10214886, 1.2807165, -0.081291153),
			// new Point3d (-0.10034388, 1.2826916, -0.075191195),
			// new Point3d (-0.094892385, 1.2849487, -0.068627864),
			// new Point3d (-0.091284795, 1.2858693, -0.065874672),

			new Point3d(-0.10788672156057863, 1.2665148827300161,
					-0.09998520172780215),
			new Point3d(-0.11693435782994785, 1.2645028688647368,
					-0.09994850026305938),
			new Point3d(-0.1262569277206657, 1.2651339951538216,
					-0.09992483816610186),
			new Point3d(-0.13477267966059603, 1.2667884200801574,
					-0.09991587306152523),
			new Point3d(-0.1424992462253266, 1.2678366354913437,
					-0.09994165904992028),
			new Point3d(-0.14988715018533397, 1.2678052576441161,
					-0.09998060386569639),
			new Point3d(-0.15646269091670065, 1.2671779175103468,
					-0.10001934356150889),
			new Point3d(-0.16142658981484037, 1.2653905450829548,
					-0.10006458080874786),
			new Point3d(-0.16419054803515143, 1.2634434691957999,
					-0.10016356767827307),
			new Point3d(-0.16435896512212805, 1.2627125648754336,
					-0.10026383468488788),
			new Point3d(-0.16185693448797364, 1.2636742175820355,
					-0.10036859668082417),
			new Point3d(-0.15800565302440644, 1.2661290007051265,
					-0.10029571987613557),
			new Point3d(-0.15419188652841015, 1.2675632585310426,
					-0.10011152497406085),
			new Point3d(-0.1516227044933477, 1.2675136766000004,
					-0.09991491815581177),
			new Point3d(-0.15094609493316805, 1.2665638045205436,
					-0.09979937393673095),
			new Point3d(-0.1520232330102157, 1.2656136255436885,
					-0.09980603670575662),
			new Point3d(-0.15425010624040467, 1.2652035319421613,
					-0.09992427909869213),
			new Point3d(-0.1570442267821221, 1.2651307417365452,
					-0.10010164595110399),
			new Point3d(-0.16010070314438835, 1.2650275572596157,
					-0.10028577599763498),
			new Point3d(-0.16329091110796173, 1.2647214768954789,
					-0.10044340599022718),
			new Point3d(-0.1665062763056612, 1.2641894529222972,
					-0.10056125680386482),
			new Point3d(-0.1697659008499883, 1.262929113234549,
					-0.1006297887234699),
			new Point3d(-0.17323091930533002, 1.261027601292993,
					-0.10065459474347867),
			new Point3d(-0.17689425229327876, 1.2590899699079208,
					-0.100650972023301),
			new Point3d(-0.18064783594488856, 1.2572215606633395,
					-0.10063375997021608),
			new Point3d(-0.18435635096093447, 1.2548616436990934,
					-0.10058771333619815),
			new Point3d(-0.1882915461631348, 1.2522506746235051,
					-0.1004081606894592),
			new Point3d(-0.19250850743148362, 1.2499897973756617,
					-0.10012737486795431),
			new Point3d(-0.1968195378519607, 1.248291864028037,
					-0.09986552578791268),
			new Point3d(-0.20073990341058567, 1.2467998700761171,
					-0.09984319777203493),
			new Point3d(-0.20374553564336526, 1.2454845789155846,
					-0.10011427921363578),
			new Point3d(-0.20550472742908046, 1.244704169746317,
					-0.1002998671575816),
			new Point3d(-0.2060161612282665, 1.2446095492552296,
					-0.10051809774354087),
			new Point3d(-0.2054233432701965, 1.2449720192159304,
					-0.10067525808008441),
			new Point3d(-0.20429697229120694, 1.245459779782377,
					-0.10063339847861603),
			new Point3d(-0.2015638105036009, 1.2455344741562357,
					-0.10031611388245516),
			new Point3d(-0.19808729266955255, 1.2459063602451468,
					-0.0999246901378611),
			new Point3d(-0.19369718361551685, 1.2464430850411707,
					-0.0996419620219157),
			new Point3d(-0.1882438128066393, 1.2470452549114164,
					-0.09956899861264844),
			new Point3d(-0.18236380110904604, 1.2481185818001406,
					-0.09973400490441842),
			new Point3d(-0.1767208037724054, 1.2498876475220047,
					-0.10017456562053159),
			new Point3d(-0.17164391155972517, 1.2517374976054496,
					-0.10074120307554699),
			new Point3d(-0.1676060162330047, 1.2531414421676461,
					-0.10111363883546955),
			new Point3d(-0.16478328487660485, 1.2536694091322804,
					-0.10102257217229069),
			new Point3d(-0.1630954763076567, 1.2536254875177324,
					-0.10036726314075575),
			new Point3d(-0.16210747215087543, 1.2535195419422984,
					-0.0994663629672751),
			new Point3d(-0.1603725823902319, 1.252631188816745,
					-0.09884366282874774),
			new Point3d(-0.15721327650131894, 1.2521894512924048,
					-0.09889092823005254),
			new Point3d(-0.1525510516370863, 1.2530792610664503,
					-0.09976891599424778),
			new Point3d(-0.14682197625883006, 1.254974581045411,
					-0.10103198776325178),
			new Point3d(-0.14199679177216135, 1.2584440558065784,
					-0.10209339562241627),
			new Point3d(-0.1396938550265148, 1.2627526285361266,
					-0.1025336592643766),
			new Point3d(-0.13925310626568235, 1.2653421667144322,
					-0.10224998341682667),
			new Point3d(-0.14065047750128862, 1.2654666915922936,
					-0.101492770155706),
			new Point3d(-0.14398446522665093, 1.2645392455883295,
					-0.10062690733513895),
			new Point3d(-0.1488906408631649, 1.2639721504122472,
					-0.09987020876629009),
			new Point3d(-0.15245246628539486, 1.2636147222112135,
					-0.09967633869908081),
			new Point3d(-0.1521817659204999, 1.2630146375989777,
					-0.09992486629856899),
			new Point3d(-0.1468261614293081, 1.2626260250506827,
					-0.10040498818359171),
			new Point3d(-0.13853429765038672, 1.263545226208919,
					-0.1005090342685994),
			new Point3d(-0.12996972137191315, 1.2648788265921447,
					-0.10050704644795602),
			new Point3d(-0.12376205561698331, 1.2658351474885103,
					-0.1003216598650947),
			new Point3d(-0.12091701648883144, 1.2660502385033814,
					-0.09994768582764302),
			new Point3d(-0.11943597741342886, 1.2652519436815775,
					-0.09968159989087431),
			new Point3d(-0.11894393067209229, 1.2647049302279212,
					-0.09966929202720487),
			new Point3d(-0.11873027688838106, 1.2645153858866873,
					-0.09991202037479784),
			new Point3d(-0.11742468367080017, 1.264308044029995,
					-0.10024688328903252),
			new Point3d(-0.11588052925443748, 1.2645917229410695,
					-0.10047296522161592),
			new Point3d(-0.11474412167934324, 1.2648774834355905,
					-0.10043590752618481),
			new Point3d(-0.11434321513493431, 1.2649195281248193,
					-0.10015547660789878),
			new Point3d(-0.11463818474294936, 1.2647749619898998,
					-0.09982629090219021),
			new Point3d(-0.11585913314529298, 1.2669163455745849,
					-0.09965308537267507),
			new Point3d(-0.11726139320577454, 1.2705021258964715,
					-0.09971178219612023),
			new Point3d(-0.11819925349770248, 1.2740659591213086,
					-0.09998022286827998),
			new Point3d(-0.11855250569097217, 1.2758833570171972,
					-0.10032917700827435),
			new Point3d(-0.11851804235285839, 1.2763855368942116,
					-0.10062655863830047),
			new Point3d(-0.11843214218513147, 1.276117794550908,
					-0.10075932421320334),
			new Point3d(-0.11854118869133771, 1.2755406635813722,
					-0.10066143593283827),
			new Point3d(-0.11890356875036875, 1.2749860652076,
					-0.10036617804120077),
			new Point3d(-0.11940557922478189, 1.2745958715293864,
					-0.09997303264761546),
			new Point3d(-0.11987003285178868, 1.2744353802105377,
					-0.09960018212556146),
			new Point3d(-0.12011361292181141, 1.2745022184714057,
					-0.09928755570446061),
			new Point3d(-0.12006571276306933, 1.2747563419622459,
					-0.09906495173983869),
			new Point3d(-0.11838970723868708, 1.2742052483627642,
					-0.09892464490690599),
			new Point3d(-0.11422252547890893, 1.2729950506460241,
					-0.09902427507652546),
			new Point3d(-0.10739834677512958, 1.2724707643971487,
					-0.0992377658238629),
			new Point3d(-0.09867384413148743, 1.2732625242024544,
					-0.09924681624714765),
			new Point3d(-0.08993732470757494, 1.274499153668378,
					-0.09952012510308371),
			new Point3d(-0.08227681245602508, 1.2746804993730898,
					-0.09990100404427404),
			new Point3d(-0.07676856145026321, 1.2736548511141323,
					-0.10023270005170129),
			new Point3d(-0.07341983476723432, 1.272295327313904,
					-0.10035749885868073),
			new Point3d(-0.07140326577122556, 1.2710519670162743,
					-0.10017546364839824),
			new Point3d(-0.06963762912139537, 1.2701012697677585,
					-0.09967564977581457),
			new Point3d(-0.0674238516702195, 1.2695275010869544,
					-0.09901018276832373),
			new Point3d(-0.06410435639484804, 1.270161906937683,
					-0.0984468965549873),
			new Point3d(-0.059636846506779466, 1.2711549941785008,
					-0.09812820549157095),
			new Point3d(-0.05432213338849188, 1.2722344839762803,
					-0.0981170200739024),
			new Point3d(-0.048723076359660736, 1.2728728867059125,
					-0.09838667023217827),
			new Point3d(-0.04349824569393547, 1.2729924201623941,
					-0.09881885823003764),
			new Point3d(-0.03924680905443326, 1.2724726565118496,
					-0.0992444791501683),
			new Point3d(-0.03627200250232736, 1.271246355368895,
					-0.09949961500066655),
			new Point3d(-0.03421542565180988, 1.2698041233307733,
					-0.0994397747036977),
			new Point3d(-0.032546694759207004, 1.2682933545905433,
					-0.09904401062746547),
			new Point3d(-0.030714787781279083, 1.2668449234674704,
					-0.09839678224371388),
			new Point3d(-0.02833122078617158, 1.2656070936175299,
					-0.09765135732929868),
			new Point3d(-0.02520713826995438, 1.2645892237409064,
					-0.09697545915667004),
			new Point3d(-0.021474542044089752, 1.2636722065707489,
					-0.09651673005431098),
			new Point3d(-0.017579299794825386, 1.262670927449081,
					-0.09636757008450324),
			new Point3d(-0.01415384078367153, 1.2613740169689323,
					-0.09656776013108985),
			new Point3d(-0.011629397880343933, 1.2598518834222288,
					-0.09707644966620037),
			new Point3d(-0.00987257403147043, 1.2581593755376637,
					-0.0977907513635359),
			new Point3d(-0.008206910390632267, 1.2564099659393577,
					-0.09853859562523387),
			new Point3d(-0.005741577649815158, 1.2548639020836514,
					-0.09913163758706264),
			new Point3d(-0.0017809536184186184, 1.253594484967311,
					-0.09942670549210322),
			new Point3d(0.0036055991756554764, 1.2523274584143285,
					-0.0993660405635947),
			new Point3d(0.009255131686097727, 1.2506797050959522,
					-0.09901257137662618),
			new Point3d(0.013561846760743047, 1.24847033136726,
					-0.0985013441753203),
			new Point3d(0.015422521020432647, 1.245780829193157,
					-0.09799782668906637),
			new Point3d(0.01503646515221008, 1.2430281448585427,
					-0.09773676773526947),
			new Point3d(0.014199046234067293, 1.2408276203661532,
					-0.0979033079380117),
			new Point3d(0.0149462624266629, 1.2394184849601786,
					-0.09846675833326146),
			new Point3d(0.018042500708329946, 1.2385233943775682,
					-0.09919353197094405),
			new Point3d(0.022516278317628, 1.2376541794713376,
					-0.09986558984071867),
			new Point3d(0.02641791012666894, 1.2364195378851506,
					-0.10033380676843855),
			new Point3d(0.028207401918903894, 1.2348426371075292,
					-0.10052700553267371),
			new Point3d(0.02765116125275851, 1.2333367664391546,
					-0.10047930819587839),
			new Point3d(0.025348793315656433, 1.231972597290475,
					-0.10027084751929233),
			new Point3d(0.02279136475170517, 1.231394901306083,
					-0.09997288577042718),
			new Point3d(0.020743138213470125, 1.2314110107457408,
					-0.09965810478623623),
			new Point3d(0.019298358249059712, 1.2320773671666365,
					-0.09937312006406414),
			new Point3d(0.018041631611020154, 1.233160718732394,
					-0.09915581828379878),
			new Point3d(0.016547689511263196, 1.2344401701755918,
					-0.09903184101632401),
			new Point3d(0.014640601365632215, 1.2357995686484853,
					-0.09900694084340482),
			new Point3d(0.012448996083088143, 1.2372193732282095,
					-0.09906918420799439),
			new Point3d(0.01027001690666712, 1.2386745051875494,
					-0.09919524605776985),
			new Point3d(0.008333090180403794, 1.2400884462915518,
					-0.09935843441519182),
			new Point3d(0.0065789537740800555, 1.241327619878818,
					-0.09956876951684832),
			new Point3d(0.004653974742061531, 1.2423635161530557,
					-0.09981760047122643),
			new Point3d(0.002329775071468136, 1.2433533014915807,
					-0.10008095563033778),
			new Point3d(-3.462882144951193E-4, 1.244369075285349,
					-0.1003338761398333),
			new Point3d(-0.0030592975663612534, 1.2454677549133053,
					-0.10056470497274698),
			new Point3d(-0.005517852778885387, 1.2465713159870782,
					-0.10074264781665715),
			new Point3d(-0.007451625085277738, 1.247791357290228,
					-0.10083521148294768),
			new Point3d(-0.008819570492287186, 1.2489713835656822,
					-0.10083023008758012),
			new Point3d(-0.009860281412979502, 1.2499893062424678,
					-0.1007649155182573),
			new Point3d(-0.010952632353762575, 1.250785561560059,
					-0.10070995680020048),
			new Point3d(-0.012419093616334512, 1.251359872809706,
					-0.1007473898369492),
			new Point3d(-0.014172440942511409, 1.2519835780495296,
					-0.10090871737861722),
			new Point3d(-0.015859753024528394, 1.2527177337827518,
					-0.10114977755343543),
			new Point3d(-0.017187356318524628, 1.2534943936426766,
					-0.10137256620487692),
			new Point3d(-0.018409267854166047, 1.2538935460774232,
					-0.1014570048110414),
			new Point3d(-0.02003724985956142, 1.2539314076765296,
					-0.10134147374027208),
			new Point3d(-0.022670595112001854, 1.2538256771559597,
					-0.10101658935508878),
			new Point3d(-0.026128149399700085, 1.2539846879094148,
					-0.10056172456561424),
			new Point3d(-0.029547161459828244, 1.2546694943061294,
					-0.10013374746128814),
			new Point3d(-0.03189126790796728, 1.2559435634693026,
					-0.09992083677385888),
			new Point3d(-0.03304171476830632, 1.2573792705652977,
					-0.09998245971343114),
			new Point3d(-0.03360799625377966, 1.2586346698908628,
					-0.10030285191010611),
			new Point3d(-0.034514903250931865, 1.2594957273007965,
					-0.10076141223887386),
			new Point3d(-0.03638194209108382, 1.2599175734223513,
					-0.10115188185557762),
			new Point3d(-0.03901377451611092, 1.2601655603648474,
					-0.10128763924792245),
			new Point3d(-0.04176406858680859, 1.260389360507628,
					-0.1010778149486445),
			new Point3d(-0.04409999066644789, 1.2608654753509316,
					-0.10057901716355358),
			new Point3d(-0.04583641922649644, 1.2614877978069756,
					-0.10000090693028549),
			new Point3d(-0.047214341715415824, 1.2622177016274567,
					-0.09964430340730586),
			new Point3d(-0.04908258678158553, 1.2624047827126812,
					-0.09974592459033826),
			new Point3d(-0.052186508177842886, 1.2623769244879632,
					-0.10021506749381501),
			new Point3d(-0.05616062113362098, 1.2631394347549432,
					-0.10077157459315984),
			new Point3d(-0.06020735917672624, 1.2645274548081276,
					-0.10110853425003248),
			new Point3d(-0.06383551928088478, 1.2660880779252206,
					-0.10106524425820079),
			new Point3d(-0.06765139785425453, 1.266524134066696,
					-0.10061199612130953),
			new Point3d(-0.07256923878648644, 1.2660669693685966,
					-0.09990045053231704),
			new Point3d(-0.07877386887254, 1.2657672166472484,
					-0.09916795991354997),
			new Point3d(-0.08589428861966548, 1.2663209342010533,
					-0.09871122095648706),
			new Point3d(-0.09362274613356501, 1.2676650028602225,
					-0.09866386693727167),
			new Point3d(-0.10174019440088866, 1.2694093011643361,
					-0.09897239147285297),
			new Point3d(-0.1099305459066644, 1.2711849420340033,
					-0.09946695529413181),
			new Point3d(-0.11796411068823275, 1.2723059027956036,
					-0.09990196311217991),
			new Point3d(-0.12522590322203214, 1.2731880019513357,
					-0.10013817010966639),
			new Point3d(-0.13120139067322686, 1.2739464669230414,
					-0.10013951924152986),
			new Point3d(-0.13568750113143416, 1.2742954499548145,
					-0.09992324421375372),
			new Point3d(-0.13908093710858394, 1.2738607412245544,
					-0.09957383239858471),
			new Point3d(-0.1419898732632993, 1.2725652884745047,
					-0.09920404916011213),
			new Point3d(-0.14493541287501008, 1.2708885924303164,
					-0.09890584386536204),
			new Point3d(-0.14814299930270386, 1.2694311323153507,
					-0.09872804401121027),
			new Point3d(-0.15143929301116577, 1.2685138101406983,
					-0.0986764776850583),
			new Point3d(-0.15442596052877353, 1.2681358840919932,
					-0.09872575999908331),
			new Point3d(-0.15629061448507492, 1.2678150781572917,
					-0.09881971597297963),
			new Point3d(-0.15675031638762416, 1.2675024808895032,
					-0.09899482926934984),
			new Point3d(-0.1562745094650043, 1.26749712216556,
					-0.09927146596874241),
			new Point3d(-0.1554317200711013, 1.2677930034760065,
					-0.09963596461713958),
			new Point3d(-0.1546114976934504, 1.2681655535304894,
					-0.10006735519224533),
			new Point3d(-0.1539198349656438, 1.2683982072481705,
					-0.10048599468612725),
			new Point3d(-0.1533690948275261, 1.2685032146624513,
					-0.10083117359073378),
			new Point3d(-0.14977934327219827, 1.2666613052020308,
					-0.10092863818771981),
			new Point3d(-0.14338396527256222, 1.2667320902236332,
					-0.1008301299077895),
			new Point3d(-0.13316243070655773, 1.2676271843968623,
					-0.10055032057656965),
			new Point3d(-0.11860496765371119, 1.268869663022583,
					-0.1002061020977519),
			new Point3d(-0.10197463199996512, 1.272162720909317,
					-0.09985903953370424),
			new Point3d(-0.0869822166795032, 1.2732477607761847,
					-0.09958576398092896),
			new Point3d(-0.07650942201130471, 1.2714599475219015,
					-0.09948834660462275),
			new Point3d(-0.07135535581518682, 1.2682655067695305,
					-0.09960728030911326),
			new Point3d(-0.06971316932942032, 1.265553344985138,
					-0.09990594437184118),
			new Point3d(-0.06905937693251334, 1.2641320022300613,
					-0.10026314384018942),
			new Point3d(-0.06776860267961657, 1.2637720384293352,
					-0.10051439998594566),
			new Point3d(-0.06541287509953474, 1.2641622134291406,
					-0.1005511230090607),
			new Point3d(-0.06286339257867853, 1.2646156168443394,
					-0.10034970572584534),
			new Point3d(-0.061574053532899006, 1.26451394058014,
					-0.09999510359041656),
			new Point3d(-0.0622237267459163, 1.2640944348323517,
					-0.0996446540216939),
			new Point3d(-0.06442956137161517, 1.2639868490086008,
					-0.0994421653662562),
			new Point3d(-0.06733838594746908, 1.2645185240703687,
					-0.09946441666844313),
			new Point3d(-0.07018272338912686, 1.2655209757461867,
					-0.09967626922268526),
			new Point3d(-0.07263616672762933, 1.2666723039909014,
					-0.09999158985404005),
			new Point3d(-0.07472683888853307, 1.267722643483649,
					-0.10029406388864619),
			new Point3d(-0.0766633296750511, 1.2685484328067966,
					-0.10048809217421888),
			new Point3d(-0.07835949477146648, 1.269496196875019,
					-0.10053594928140842),
			new Point3d(-0.07944013584150775, 1.2707166482787209,
					-0.1004432711318564),
			new Point3d(-0.07951175797838059, 1.2721036382244448,
					-0.10027647076353916),
			new Point3d(-0.07850490305417256, 1.273454290682551,
					-0.10012389261355184),
			new Point3d(-0.07664081732279134, 1.2744804556839795,
					-0.10005416723749654),
			new Point3d(-0.07661600791766199, 1.271431306860203,
					-0.10004835381847847),
			new Point3d(-0.08115361772275675, 1.2674566430770717,
					-0.0999342211685547),
			new Point3d(-0.0892561949476051, 1.2651601238601866,
					-0.099754359976323),
			new Point3d(-0.09586330353458497, 1.2643939337693408,
					-0.0999250728166065),
			new Point3d(-0.09801286217909334, 1.2645095945084681,
					-0.09999666648528364),
			new Point3d(-0.09673949842151892, 1.2653236282739806,
					-0.09994072952083141),
			new Point3d(-0.09464206519171688, 1.2662428366777954,
					-0.09986996500120805),
			new Point3d(-0.09372242298423604, 1.2663785076443193,
					-0.09988215553379987),
			new Point3d(-0.09449563689029981, 1.265990476260106,
					-0.09998306372085289),
			new Point3d(-0.09581176431305502, 1.265433329069283,
					-0.10011417697036648),
			new Point3d(-0.0964048698884651, 1.2650537508579824,
					-0.10019322134472737),
			new Point3d(-0.09411862244181633, 1.2647093842981072,
					-0.10006194900893449),
			new Point3d(-0.0894996796243306, 1.2646716887493321,
					-0.09990202265887978),
			new Point3d(-0.08522949156635137, 1.2651645785137602,
					-0.09976270680131186),
			new Point3d(-0.08273629419792031, 1.2654603495648888,
					-0.09971881787577898),
			new Point3d(-0.07957678939291862, 1.2648178128803926,
					-0.09987106336056482),
			new Point3d(-0.07751566167777924, 1.2646744983380533,
					-0.10006529312566242),
			new Point3d(-0.07347877685835495, 1.264111751089628,
					-0.1000856663680665),
			new Point3d(-0.06922744892062381, 1.264182856012049,
					-0.10003478121246959),
			new Point3d(-0.06592943163667682, 1.2645097261013465,
					-0.09987773510834277),
			new Point3d(-0.06315799148168134, 1.2646507027021001,
					-0.09980662320532105),
			new Point3d(-0.06112511572009091, 1.264662564400116,
					-0.09980777117238558),
			new Point3d(-0.05762871538597322, 1.2639763711218326,
					-0.09992246401979311),
			new Point3d(-0.054667182774760854, 1.263806368356347,
					-0.10002521400734876),
			new Point3d(-0.05290081863635422, 1.2638269123450332,
					-0.10008160262714036),
			new Point3d(-0.05271517680886071, 1.2638998053512516,
					-0.10005384470493153),
			new Point3d(-0.053920379864606245, 1.2641222927014182,
					-0.0999360973455144),
			new Point3d(-0.055683866872573234, 1.264518598263349,
					-0.09980217396416703),
			new Point3d(-0.056721805737757175, 1.2648078070199487,
					-0.09981917216263887),
			new Point3d(-0.056730970220278086, 1.2650062253509038,
					-0.09984393497908382),
			new Point3d(-0.056319549885030254, 1.2652770574407757,
					-0.09988125133503829),
			new Point3d(-0.05610915385936598, 1.2653849092140834,
					-0.09995065338653586),
			new Point3d(-0.05622523906131756, 1.2651640202215442,
					-0.10003031497593282),
			new Point3d(-0.05643279512254346, 1.2648028317942084,
					-0.10007108656521914),
			new Point3d(-0.05650013125087478, 1.2646103496176575,
					-0.10004072908337583),
			new Point3d(-0.0563913967899417, 1.2647028031655834,
					-0.09994932454820427),
			new Point3d(-0.05622338505689013, 1.2649207727711396,
					-0.09984815272292705),
			new Point3d(-0.05533400541706623, 1.2674521394417233,
					-0.09974983887254679),
			new Point3d(-0.05394400508867333, 1.269358569930225,
					-0.09972321047838269),
			new Point3d(-0.05220888047899064, 1.2703887671925538,
					-0.0997882219501757),
			new Point3d(-0.05048645117221386, 1.2704921219637895,
					-0.09992796705997083),
			new Point3d(-0.04922884544285309, 1.270048517247661,
					-0.10005798705249402),
			new Point3d(-0.04867045833923782, 1.2693932546823636,
					-0.1000714294167608),
			new Point3d(-0.04861120085581967, 1.2687360899792788,
					-0.09990944616914206),
			new Point3d(-0.04851847845268422, 1.2681970214271578,
					-0.09961368005695251),
			new Point3d(-0.04789563734577418, 1.2678179314217428,
					-0.0993317020101857),
			new Point3d(-0.04664395699870154, 1.2675445309575435,
					-0.09928155646649287),
			new Point3d(-0.04514049432049695, 1.267273734164555,
					-0.09962530376202813),
			new Point3d(-0.04414420012064873, 1.266674404844016,
					-0.10029838240386514),
			new Point3d(-0.04397631116444026, 1.2660636626350383,
					-0.10096754841305253),
			new Point3d(-0.04429323210658169, 1.2655034199747017,
					-0.10123061897751072),
			new Point3d(-0.04537109166276497, 1.2640384874409456,
					-0.10084296530231507),
			new Point3d(-0.04729912554814919, 1.2627169005149577,
					-0.09995018752189773),
			new Point3d(-0.04991221442716284, 1.2615719075242229,
					-0.09902475146649137),
			new Point3d(-0.053942987825335464, 1.259978918818971,
					-0.09843928829913673),
			new Point3d(-0.059021859875479456, 1.2597715421907605,
					-0.09844454451910445),
			new Point3d(-0.06458554975422476, 1.261180355467585,
					-0.09906538002076494),
			new Point3d(-0.07068425484407041, 1.2634579941660586,
					-0.09999526197092021),
			new Point3d(-0.07803797323616, 1.2653126856987587,
					-0.10082194468565515),
			new Point3d(-0.08708225112090866, 1.2666374314764903,
					-0.10125800858828592),
			new Point3d(-0.0976614740233667, 1.2677010699120825,
					-0.10121033702112812),
			new Point3d(-0.10908317629578633, 1.2689563197871128,
					-0.10075216273412707),
			new Point3d(-0.12040328983430171, 1.270169788657082,
					-0.10006648430068484),
			new Point3d(-0.13065674075920117, 1.2712277456677274,
					-0.09936637946269472),
			new Point3d(-0.13784128894336625, 1.2707525094857253,
					-0.09895630210734588),
			new Point3d(-0.14103053871356916, 1.2693793809716407,
					-0.09898385490505054),
			new Point3d(-0.14095962389070862, 1.269021194615292,
					-0.09932276080009697),
			new Point3d(-0.1386659045697264, 1.270054097211917,
					-0.099749985118956),
			new Point3d(-0.1355822032235752, 1.2718028989130017,
					-0.10000572378289442),
			new Point3d(-0.13310327483892326, 1.2727205886343322,
					-0.1001882481931684),
			new Point3d(-0.13171402037196206, 1.2728838312441015,
					-0.10026914584149965),
			new Point3d(-0.13144756128649826, 1.2724623775586692,
					-0.10024659796919683),
			new Point3d(-0.1319186745391005, 1.2720554490775011,
					-0.10012689095587184),
			new Point3d(-0.13255553088748956, 1.2719533999124093,
					-0.09993570388979041),
			new Point3d(-0.13321660281626868, 1.2716382744064332,
					-0.09971943167564339),
			new Point3d(-0.13398588271528666, 1.2714979715261834,
					-0.09954774408051374),
			new Point3d(-0.1352170251767712, 1.2710414717751795,
					-0.0994708634364174),
			new Point3d(-0.13706913373898433, 1.2707905345256585,
					-0.09952289473762957),
			new Point3d(-0.13950253196175585, 1.270568515684327,
					-0.09968659161845393),
			new Point3d(-0.14225079094702192, 1.2705500218057824,
					-0.09991065283319811),
			new Point3d(-0.14520850695057236, 1.2700706225458163,
					-0.10012734724418716),
			new Point3d(-0.14821798977860043, 1.2696667909985728,
					-0.10029582375581389),
			new Point3d(-0.15115731513977848, 1.2690662092123672,
					-0.10037453805885618),
			new Point3d(-0.15401158247416244, 1.2682843710187282,
					-0.10036019069168957),
			new Point3d(-0.1567047786355832, 1.2675660092056649,
					-0.10027402127131951),
			new Point3d(-0.15914563688705272, 1.2668746652353697,
					-0.10016415705223168),
			new Point3d(-0.16139053821443528, 1.2659701753535204,
					-0.10008929443150068),
			new Point3d(-0.16345569627474182, 1.265184272457525,
					-0.10009578079940493),
			new Point3d(-0.1652692781609066, 1.264667563909116,
					-0.10020390916165663),
			new Point3d(-0.16681947690616497, 1.264239498583584,
					-0.10040417125556765),
			new Point3d(-0.1681683694965257, 1.2638103716928704,
					-0.10066391920109691),
			new Point3d(-0.1693572942865348, 1.2634315631919344,
					-0.10093598907153617),
			new Point3d(-0.1703766266569843, 1.2631145147077807,
					-0.10117392478630292),
			new Point3d(-0.17122103892764076, 1.2628028614410631,
					-0.10134236992140584),
			new Point3d(-0.1719110192215549, 1.262463845078763,
					-0.10142180628658182),
			new Point3d(-0.172467752286021, 1.2621299154821295,
					-0.10141110187973458),
			new Point3d(-0.17288828833256428, 1.261859434508476,
					-0.10132611112779805),
			new Point3d(-0.17314233002642962, 1.26171226457322,
					-0.10119712992497416),
			new Point3d(-0.17321870127699432, 1.2616490490110628,
					-0.10105746646931543),
			new Point3d(-0.17313097170794084, 1.2616144073539017,
					-0.10093538281451768),
			new Point3d(-0.1729176870204968, 1.2615588819577064,
					-0.10084729797174485),
			new Point3d(-0.17259104000045866, 1.261611277403549,
					-0.10079748259933408),
			new Point3d(-0.1721713014254435, 1.2617946118765597,
					-0.1007772062216522),
			new Point3d(-0.1715752920502329, 1.262072680412723,
					-0.10074580630925199),
			new Point3d(-0.1707108726769323, 1.2624053482030761,
					-0.10068643882329958),
			new Point3d(-0.16979060278579436, 1.2628958756572453,
					-0.10067996554785026),
			new Point3d(-0.16483329040516642, 1.26044143109099,
					-0.10055122972895127),
			new Point3d(-0.1554886239368785, 1.259284406777626,
					-0.10030911896726054),
			new Point3d(-0.14189869349632261, 1.2615854600609295,
					-0.09993066417188241),
			new Point3d(-0.1239267336493119, 1.2656423177694185,
					-0.0996178865622975),
			new Point3d(-0.10594188511108943, 1.2697846880661805,
					-0.099688055434082),
			new Point3d(-0.09137562612691696, 1.271016034194347,
					-0.09991882416761014),
			new Point3d(-0.08270849932701861, 1.269364514798633,
					-0.10023078548209889),
			new Point3d(-0.0794382859142823, 1.2668647022080046,
					-0.10057731020448525),
			new Point3d(-0.07916977286121349, 1.2651773755338491,
					-0.10089898453367908),
			new Point3d(-0.07952074248781224, 1.264521271945536,
					-0.10114282876461698),
			new Point3d(-0.07914357878575919, 1.2645629752923946,
					-0.1012620168894149),
			new Point3d(-0.07756144153209923, 1.2655587969978097,
					-0.10123560281968469),
			new Point3d(-0.07461846270234941, 1.2675029632399817,
					-0.10112787292062109),
			new Point3d(-0.07121599708163583, 1.2687265571692592,
					-0.10098138078038997),
			new Point3d(-0.06903138169093975, 1.2687273184153964,
					-0.100885049683839),
			new Point3d(-0.06898799204249152, 1.267906437409419,
					-0.1009108556340673),
			new Point3d(-0.07048284476844438, 1.2671226271642848,
					-0.10105844108543897),
			new Point3d(-0.07202222067356515, 1.2670202335483152,
					-0.10127580106012694),
			new Point3d(-0.07235760244398919, 1.2675612428722434,
					-0.1014476245562722),
			new Point3d(-0.0714539172344781, 1.2682322093645946,
					-0.10147546069180745),
			new Point3d(-0.07035579479035324, 1.2684540866465297,
					-0.10134441422078222),
			new Point3d(-0.0701621556087523, 1.2680910705851465,
					-0.10111593321526526),
			new Point3d(-0.0710656043879377, 1.2675168445873854,
					-0.10088727368811376),
			new Point3d(-0.07236213725779604, 1.2672623661598577,
					-0.10076613137663774),
			new Point3d(-0.07309594914119713, 1.2675498831853433,
					-0.10081636737103107), };
}
