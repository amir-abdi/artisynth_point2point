from ..config import *
from os import path as osp
import os
import sys
def setClassPath():
    sys.path.append(osp.join(os.getcwd(), 'lib/JyNI.jar'))
    sys.path.append('/usr/lib/python3.5')
    sys.path.append('/usr/local/lib/python3.5/dist-packages/')
    sys.path.append(osp.join(artisynth_core_path,'classes'))
    sys.path.append(osp.join(artisynth_models_path,'classes'))
    sys.path.append(osp.join(artisynth_projects_path,'classes'))
    sys.path.append(artisynth_core_path + '/scripts')
    libDir = osp.join(artisynth_core_path, 'lib')
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
import tensorflow

def start_artisynth():
    artisynth_main = artisynth.core.driver.Main.getMain()
    if artisynth_main is not None:
        artisynth_main.quit()
    artisynth.core.driver.Main.setMain(None)
    artisynth.core.driver.Main.main([])
    artisynth_main = artisynth.core.driver.Main.getMain()
    return artisynth_main


import math
