package artisynth.models.lumbarSpine;

import java.awt.Color;
import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import javax.swing.JLabel;
import javax.swing.JSeparator;

import maspack.matrix.Point3d;
import maspack.matrix.Vector3d;
import maspack.properties.PropertyList;
import maspack.render.RenderProps;
import maspack.render.Renderer.PointStyle;
import artisynth.core.gui.ControlPanel;
import artisynth.core.inverse.MotionTargetTerm;
import artisynth.core.inverse.RlTrackingController;
import artisynth.core.mechmodels.MotionTargetComponent;
import artisynth.core.mechmodels.MuscleExciter;
import artisynth.core.mechmodels.Point;
import artisynth.core.modelbase.Controller;
import artisynth.core.modelbase.ControllerBase;
import artisynth.core.modelbase.Model;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.PullController;
import artisynth.models.rl.InverseModel;

public class RlInvLumbarSpine extends LumbarSpine2 implements InverseModel {

	protected int port = 4545;
	protected double MOTION_RANGE = 0.1;

	protected double xPosition;
	protected double yPosition;
	protected double zPosition;

	public int selectedPosIndex = 0;

	protected Vector3d rightTargetRefPosition;
	protected Vector3d leftTargetRefPosition;
	protected Vector3d rightTargetRealPosition;
	protected Vector3d leftTargetRealPosition;
	protected double error_r;
	protected double error_l;
	public ControlPanel myTargetPositionerPanel;
	SpineRandomTargetController sRandomController;

	double initPosRight[] = { -0.098877193, 1.2771631, 0.0999726 };
	double initPosLeft[] = { -0.098877193, 1.2771631, -0.1000274 };

	public Vector3d getInitPosition() {
		Vector3d initPos = new Vector3d();
		for (int i = 0; i < 3; ++i) {
			initPos.set(i, (initPosRight[i] + initPosLeft[i]) / 2);
		}
		return initPos;
	}

	public static PropertyList myProps = new PropertyList(
			RlInvLumbarSpine.class, LumbarSpine2.class);

	public PropertyList getAllPropertyInfo() {
		return myProps;
	}

	@Override
	public void addController(Controller controller, Model model) {
		super.addController(controller, model);
		if (controller instanceof PullController) {
			((PullController) controller).setStiffness(500);
		}
	}

	static {
		myProps.add("xPosition * *", "x position of target", 0.0,
				"[-0.10,0.10] NW");
		myProps.add("yPosition * *", "y position of target", 0.0,
				"[-0.05,0.05] NW");
		myProps.add("zPosition * *", "z position of target", 0.0,
				"[-0.1,0.1] NW");
		myProps.add("selectedPosIndex * *",
				"Index of predefined selceted position", 0);

		myProps.add("rightTargetRefPosition * *",
				"reference position of the right target", null, "%.8g");
		myProps.add("leftTargetRefPosition * *",
				"reference position of the left target", null, "%.8g");
		myProps.addReadOnly("rightTargetRealPosition *",
				"real position of the right target point");
		myProps.addReadOnly("leftTargetRealPosition *",
				"real position of the left target point");
		myProps.addReadOnly("error_r *", "motion target error on right side");
		myProps.addReadOnly("error_l *", "motion target error on left side");
	}

	public RlInvLumbarSpine() {
		this("RlLumbarSpine");
	}

	@Override
	public void build(String[] args) throws IOException {

		parseArgs(args);
	}

	private void parseArgs(String[] args) {
		for (int i = 0; i < args.length; i += 2) {
			System.out.print(args[i]);
			if (args[i].equals("-port")) {
				this.port = Integer.parseInt(args[i + 1]);
			}
		}
	}

	public RlInvLumbarSpine(String name) {
		super(name);
		addMonitors();
		setXPosition(0);
		setYPosition(0);
		setZPosition(0);
		setRightTargetRefPosition(
				new Vector3d(-0.10437719, 1.2741631, 0.0999726));
		setLeftTargetRefPosition(
				new Vector3d(-0.10437719, 1.2741631, -0.1000274));
		setRightTargetRealPosition(new Vector3d(initPosRight));
		setLeftTargetRealPosition(new Vector3d(initPosLeft));
	}

	// ********************************************************
	// ******************* Adding ATTACH DRIVER****************
	// ********************************************************
	public void attach(DriverInterface driver) {
		super.attach(driver);

		addSimpleInverseSolver();
		// addSpineSimpleTargetController ();
		addTargetPositionerControlPanel();
		// adjustControlPanelLocations ();
	}

	// ********************************************************
	// ******************* Adding Control Panels **************
	// ********************************************************
	public void addTargetPositionerControlPanel() {

		myTargetPositionerPanel = new ControlPanel("TargetPositioner",
				"LiveUpdate");
		myTargetPositionerPanel
				.addWidget(new JLabel("Adjusting Target Position:"));
		myTargetPositionerPanel.addWidget("    x Position", this, "xPosition");
		myTargetPositionerPanel.addWidget("    y Position", this, "yPosition");
		myTargetPositionerPanel.addWidget("    z Position", this, "zPosition");

		myTargetPositionerPanel.addWidget("    pos index", this,
				"selectedPosIndex");

		myTargetPositionerPanel.addWidget(new JSeparator());
		myTargetPositionerPanel.addWidget(new JLabel(
				"Target Reference Position (prescribed to the model):"));
		myTargetPositionerPanel.addWidget("    target_r", this,
				"rightTargetRefPosition");
		myTargetPositionerPanel.addWidget("    target_l", this,
				"leftTargetRefPosition");

		myTargetPositionerPanel.addWidget(new JSeparator());
		myTargetPositionerPanel.addWidget(
				new JLabel("Target Real Position (produced by the model):"));
		myTargetPositionerPanel.addWidget("    target_r", this,
				"rightTargetRealPosition");
		myTargetPositionerPanel.addWidget("    target_l", this,
				"leftTargetRealPosition");

		myTargetPositionerPanel.addWidget(new JSeparator());
		myTargetPositionerPanel.addWidget("    right motion error", this,
				"error_r");
		myTargetPositionerPanel.addWidget("    left motion error", this,
				"error_l");
		myTargetPositionerPanel.addWidget(new JSeparator());

		myTargetPositionerPanel.setScrollable(false);

		Dimension framespringsforcesD = getControlPanels()
				.get("FrameSpringsForces").getSize();
		java.awt.Point loc = getControlPanels().get("FrameSpringsForces")
				.getLocation();
		myTargetPositionerPanel.setLocation(loc.x,
				loc.y + framespringsforcesD.height);
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

	public int getSelectedPosIndex() {
		return selectedPosIndex;
	}

	public void setSelectedPosIndex(int index) {
		selectedPosIndex = index;
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
	// ******************* Adding Controllers ****************
	// ********************************************************

	public void addSimpleInverseSolver() {

		double point_radius = 0.01;

		// Tracker t = new Tracker (mech, "spineTracker");
		RlTrackingController t = new RlTrackingController(mech,
				(InverseModel) this, "spineTracker", port);
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
		rp1.setPointRadius(point_radius);
		rp1.setPointColor(Color.CYAN);

		RenderProps rp2 = new RenderProps(rp1);
		rp2.setPointRadius(point_radius);
		rp2.setPointColor(Color.RED);
		t.setMotionRenderProps(rp1, rp2);

		sRandomController = new SpineRandomTargetController(
				(Point) targets.get(0), (Point) targets.get(1));

		addController(sRandomController);
		addController(t);
		// t.createPanel (this);

		addTrackingMonitors(t);
	}

	public void resetTargetPosition() {
		sRandomController.reset = true;
	}

	public class SpineRandomTargetController extends ControllerBase {

		private Point myp1;
		private Point myp2;
		private int[] trialIndices = { 0, 15, 5, 120, 90, 48, 80 };
		// private int[] trialIndices = { 48, 130};
		private Boolean fixed = false;

		Random r = new Random();

		public Boolean reset = false;
		public Boolean trialRun = false;

		// private ArrayList<Frame> initFrame;
		public SpineRandomTargetController(Point p1, Point p2) {
			myp1 = p1;
			myp2 = p2;

			assert (targetInitPoints_l.length == targetInitPoints_r.length);
		}

		public void apply(double t0, double t1) {
//			if (t0 == 0)
//				selectedPosIndex = 0;

			if (trialRun) {

				if (fixed) {
					if ((int) t0 % 2 == 0) {
						int index = (int) t0 / 2;
						selectedPosIndex = trialIndices[index];
					}
				} else {
					if ((int) t0 % 2 == 1) {
						int index = ((int) t0 / 2) + 1;
						selectedPosIndex = trialIndices[index];
					}
				}
			}

			if (t0 > -1 && reset) {
				reset = false;
				selectedPosIndex = r.nextInt(targetInitPoints_l.length);
			}

			if (t0 > -1) {
				if (selectedPosIndex >= targetInitPoints_l.length)
					selectedPosIndex = targetInitPoints_l.length - 1;

				Point3d pos1 = new Point3d(
						targetInitPoints_r[selectedPosIndex]);
				pos1.add(new Vector3d(xPosition, yPosition, zPosition));

				Point3d pos2 = new Point3d(
						targetInitPoints_l[selectedPosIndex]);
				pos2.add(new Vector3d(xPosition, yPosition, zPosition));

				myp1.setPosition(pos1);
				myp2.setPosition(pos2);

				// Post Processing
				Point3d er_r = new Point3d();
				Point3d er_l = new Point3d();
				er_r.sub(myp1.getPosition(), mech.frameMarkers()
						.get("ribcageMotionTarget_r").getPosition());
				er_l.sub(myp2.getPosition(), mech.frameMarkers()
						.get("ribcageMotionTarget_l").getPosition());
				error_r = er_r.norm();
				error_l = er_l.norm();
			}

		}
	}

	// Overriding addExciters class
	@Override
	public void addExciters() {
		addIndividualMex();
	}

}
