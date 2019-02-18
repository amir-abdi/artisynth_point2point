import os
from pathlib import Path

root_path = str(Path.cwd() / '../output')

trained_directory = str(Path(root_path) / 'trained')
tensorboard_log_directory = str(Path(root_path) / 'logs_tb')
env_log_directory = str(Path(root_path) / 'logs_env')
agent_log_directory = str(Path(root_path) / 'logs_agent')
log_directory = str(Path(root_path) / 'logs')

os.makedirs(root_path, exist_ok=True)
os.makedirs(trained_directory, exist_ok=True)
os.makedirs(tensorboard_log_directory, exist_ok=True)
os.makedirs(env_log_directory, exist_ok=True)
os.makedirs(agent_log_directory, exist_ok=True)
os.makedirs(log_directory, exist_ok=True)
