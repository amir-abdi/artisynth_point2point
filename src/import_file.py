from fileinput import filename
from pathlib import Path
import socket
import sys
from src.config import keras_rl_path
from src.config import trained_directory_path
sys.path.append(keras_rl_path)
import numpy as np
from src.point_model2d import *
from rl.agents.dqn import DQNAgent
from rl.agents.dqn import NAFAgent
from rl.agents.ddpg import DDPGAgent
from rl.random import OrnsteinUhlenbeckProcess
from keras.utils.generic_utils import get_custom_objects
from pathlib import Path
from rl.agents.dqn import NAFAgent
from fileinput import filename
from pathlib import Path
import socket
import sys
from src.config import keras_rl_path
sys.path.append(keras_rl_path)
import time
import json
from threading import Thread
import keras
from keras.models import Sequential
from keras.optimizers import Adam
from keras.models import Sequential, Model
from keras.layers import Dense, Activation, Flatten, Input, Concatenate
from keras.optimizers import Adam
import numpy as np
from src.point_model2d import *
#from rl.agents.dqn import DQNAgent
#from rl.agents.ddpg import DDPGAgent
from rl.random import OrnsteinUhlenbeckProcess
from keras import backend as K
from keras.utils.generic_utils import get_custom_objects
from pathlib import Path
from rl.core import Processor

def mylogistic(x):
    return 1 / (1 + K.exp(-0.1 * x))


def load_weights(agent, weight_filename):
    import os
    filename_temp, extension = os.path.splitext(weight_filename)
    if Path.exists(Path(filename_temp + '.h5f')) or \
        Path.exists(Path(filename_temp + '_actor.h5f')):
        agent.load_weights(str(weight_filename))
        print('weights loaded from ', str(weight_filename))


get_custom_objects().update({'mylogistic': Activation(mylogistic)})

