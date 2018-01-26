from src.config import *
from os import path as osp
def setClassPath():
    import sys
    sys.path.append(artisynth_core_path + '/classes') #'/home/amir/workspace/artisynth_core/classes')
    sys.path.append(artisynth_core_path + '/scripts') #'/home/amir/workspace/artisynth_core/scripts')
    sys.path.append('/usr/lib/python3.5')
    libDir = osp.join(artisynth_core_path, 'lib') #'/home/amir/workspace/artisynth_core/lib/'
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
        print(osp.join(libDir, classPath))
        sys.path.append(osp.join(libDir, classPath))
    print('*** Paths and jars added to classpath ***')
setClassPath()
from maspack import *
from artisynth.core import *
import artisynth.core.driver
from artisynth.core.driver import *
from maspack.matrix import *
from jythonInit import *
from jythonInit import *
import jythonInit
