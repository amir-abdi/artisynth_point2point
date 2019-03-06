package artisynth.models.lumbarSpine;

import java.io.IOException;
import java.util.ArrayList;

import artisynth.core.util.ArtisynthPath;
import artisynth.models.lumbarSpine.MuscleParser.MuscleInfo;

public class MuscleParserTest {

	static String localfile = ArtisynthPath
			.getSrcRelativePath(MuscleParserTest.class, "ChristophyMuscle.txt");

	public static void doTest() {
		try {
			ArrayList<MuscleInfo> mInfo = MuscleParser.parse(localfile);
			System.out.println(mInfo);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
	}

	public static void main(String args[]) {
		doTest();
	}

}
