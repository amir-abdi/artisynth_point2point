def setClassPath():
    import sys
    sys.path.append('/home/amir/eclipse-workspace/artisynth_core/classes')
    sys.path.append('/home/amir/eclipse-workspace/artisynth_core/scripts')
    sys.path.append('/usr/lib/python3.5')
    libDir = '/home/amir/eclipse-workspace/artisynth_core/lib/'
    classPaths = [
        'argparser.jar',
        'jass.jar',
        'javaosc.jar',
        'jipopt.jar',
        'jmf.jar',
        'jython.jar',
        'looks.jar',
        'quickhull3d.jar',
        'vclipx.jar',
        'vfs2.jar',
        'matconsolectl-4.4.4.jar',
        'gluegen-rt-2.3.2.jar',
        'jogl-all-2.3.2.jar',
        'PardisoJNI.11.1.2.1',
        'RobustPreds.1.1',
        'TetgenJNI.1.5.1.0',
        'libiomp5.dylib',
        'libgomp.so.1',
        'libiomp5.so',
        'libiomp5md.dll',
    ]
    for classPath in classPaths:
        sys.path.append(libDir + classPath)
    print('*** Paths and jars added to classpath ***')
setClassPath()
from maspack import *
from artisynth.core import *
import artisynth.core.driver
from artisynth.core.driver import *
from maspack.matrix import *
from src.artisynth_helper.jythonInit import *


def loadModel(artisynth_main, name, *args):
    classname = artisynth_main.getDemoClassName(name)
    print('classname: ', classname)
    if classname == None:
        print("No class found for model " + name)
        return False
        if len(args) == 0:
            artisynth_main.loadModel (classname, name, None)
    else:
        artisynth_main.loadModel (classname, name, args)

from artisynth_helper.jythonInit import *
from artisynth_helper import jythonInit


def main():
    #artisynth.core.driver.Main() #??
    Main = artisynth.core.driver.Main.getMain()
    if Main is not None:
        Main.quit()
    artisynth.core.driver.Main.setMain(None)
    artisynth.core.driver.Main.main([])
    Main = artisynth.core.driver.Main.getMain()
    jythonInit.Main = Main

    print(Main)
    loadModel('artisynth.demos.fem.HexFrame')
    play(2.5)
    loadModelFile('HexFrame')


if __name__ == '__main__':
    main()