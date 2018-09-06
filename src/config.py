from pathlib import Path
import sys

artisynth_core_path = '/home/amir/workspace/artisynth_core'
keras_rl_path = str(Path.cwd() / '..')
sys.path.append(keras_rl_path)

trained_directory = Path(keras_rl_path) / 'trained'
tensorboard_log_directory = Path(keras_rl_path) / 'logs_tb'
env_log_directory = Path(keras_rl_path) / 'logs_env'
agent_log_directory = Path(keras_rl_path) / 'logs_agent'

if not trained_directory.exists():
    Path.mkdir(trained_directory)
if not tensorboard_log_directory.exists():
    Path.mkdir(tensorboard_log_directory)
if not env_log_directory.exists():
    Path.mkdir(env_log_directory)
if not agent_log_directory.exists():
    Path.mkdir(agent_log_directory)