package artisynth.models.AHA.rl;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JSeparator;

import maspack.matrix.Plane;
import maspack.matrix.Point3d;
import maspack.matrix.RigidTransform3d;
import maspack.matrix.Vector3d;
import maspack.properties.Property;
import maspack.render.RenderProps;
import maspack.render.Renderer.LineStyle;
import maspack.util.ReaderTokenizer;
import maspack.widgets.LabeledControl;
import maspack.widgets.ValueChangeEvent;
import maspack.widgets.ValueChangeListener;
import artisynth.core.driver.Main;
import artisynth.core.gui.ControlPanel;
import artisynth.core.mechmodels.AxialSpring;
import artisynth.core.mechmodels.FrameMarker;
import artisynth.core.mechmodels.MechModel;
import artisynth.core.mechmodels.MechSystemSolver.Integrator;
import artisynth.core.mechmodels.Muscle;
import artisynth.core.mechmodels.PlanarConnector;
import artisynth.core.mechmodels.PointForce;
import artisynth.core.mechmodels.RigidBody;
import artisynth.core.mechmodels.BodyConnector;
import artisynth.core.modelbase.ComponentUtils;
import artisynth.core.probes.NumericInputProbe;
import artisynth.core.probes.NumericOutputProbe;
import artisynth.core.probes.TracingProbe;
import artisynth.core.util.ArtisynthIO;
import artisynth.core.util.ArtisynthPath;
import artisynth.core.util.ScalableUnits;
import artisynth.core.workspace.DriverInterface;
import artisynth.core.workspace.RootModel;
import artisynth.models.dynjaw.FoodBolus;
import artisynth.models.dynjaw.JawDemo;
import artisynth.models.dynjaw.JawModel;
import artisynth.models.dynjaw.JawPanel;

public class JawRlDemo extends JawDemo
{
	protected String probesFilename = "incisorDispProbes.art";

	@Override
	public void setupJawModel() {
		// createIncisorPointForce();

		// show only jaw rigidbody
		myJawModel.showMeshFaces(false);
		RenderProps.setVisible(myJawModel.rigidBodies().get("jaw"), true);
		RenderProps.setVisible(myJawModel.rigidBodies().get("maxilla"), true);

		// integrator settings
		myJawModel.setMaxStepSize(0.001);
		myJawModel.setIntegrator(Integrator.BackwardEuler);

		// damping settings
		setDampingParams(10, 100, 0.0);

		RenderProps.setVisible(myJawModel.rigidBodies().get("hyoid"), true);
		addIncisorForce();

	}

	@Override
	public void attach(DriverInterface driver) {
		setWorkingDir();
		//loadProbes();

		if (getControlPanels().size() == 0) { // createControlPanel (this,
			// driver.getFrame());
			loadControlPanel(this);
		}

	}

	@Override
	public void loadProbes() {
		if (probesFilename == null || !myInputProbes.isEmpty()
				|| !myOutputProbes.isEmpty()) return;
		String probeFileFullPath = ArtisynthPath.getWorkingDir().getPath() + "/"
				+ probesFilename;
		System.out.println("Loading Probes from File: " + probeFileFullPath);

		try {
			scanProbes(
					ArtisynthIO.newReaderTokenizer(probeFileFullPath));
		} catch (Exception e) {
			System.out.println("Error reading probe file");
			e.printStackTrace();
		}
	}

}
