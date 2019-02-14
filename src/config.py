import os
from pathlib import Path

root_path = str(Path.cwd() / '../output')
os.makedirs(str(root_path), exist_ok=True)

trained_directory = Path(root_path) / 'trained'
tensorboard_log_directory = Path(root_path) / 'logs_tb'
env_log_directory = Path(root_path) / 'logs_env'
agent_log_directory = Path(root_path) / 'logs_agent'

if not trained_directory.exists():
    Path.mkdir(trained_directory)
if not tensorboard_log_directory.exists():
    Path.mkdir(tensorboard_log_directory)
if not env_log_directory.exists():
    Path.mkdir(env_log_directory)
if not agent_log_directory.exists():
    Path.mkdir(agent_log_directory)