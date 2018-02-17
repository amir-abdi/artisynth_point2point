from pathlib import Path

artisynth_core_path = '/home/amir/workspace/artisynth_core'
artisynth_projects_path = '/home/amir/workspace/artisynth_projects'
artisynth_models_path = '/home/amir/workspace/artisynth_models'
keras_rl_path = str(Path.cwd() / '..')
trained_directory = Path(keras_rl_path) / 'trained'
tensorboard_log_directory = Path(keras_rl_path) / 'tb_logs'
