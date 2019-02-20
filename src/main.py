import copy
import glob
import os
import time
import logging
from collections import deque

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F
import torch.optim as optim

# from a2c_ppo_acktr.envs import make_vec_envs
from a2c_ppo_acktr import algo
from a2c_ppo_acktr.model import Policy
from a2c_ppo_acktr.storage import RolloutStorage
from a2c_ppo_acktr.utils import get_vec_normalize, update_linear_schedule
from a2c_ppo_acktr.visualize import visdom_plot

from common.arguments import get_args
from common import config
from artisynth.make_env import make_vec_envs  # , make_env

args = get_args()

logger = logging.getLogger()

assert args.algo in ['a2c', 'ppo', 'acktr']
if args.recurrent_policy:
    assert args.algo in ['a2c', 'ppo'], \
        'Recurrent policy is not implemented for ACKTR'

num_updates = int(args.num_env_steps) // args.num_steps // args.num_processes

torch.manual_seed(args.seed)
torch.cuda.manual_seed_all(args.seed)

if args.cuda and torch.cuda.is_available() and args.cuda_deterministic:
    torch.backends.cudnn.benchmark = False
    torch.backends.cudnn.deterministic = True


def setup_logger(level, name):
    log_formatter = logging.Formatter("%(asctime)s [%(threadName)-12.12s] [%(levelname)-5.5s]  %(message)s")

    file_handler = logging.FileHandler("{0}/{1}.log".format(config.log_directory, name))
    file_handler.setFormatter(log_formatter)
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(log_formatter)
    logger.addHandler(console_handler)

    logger.setLevel(level=level)
    logger.info('Log level: %i', level)


def main():
    setup_logger(args.verbose, args.model_name)

    torch.set_num_threads(1)
    device = torch.device("cuda:0" if args.cuda else "cpu")

    if args.vis:
        from visdom import Visdom
        viz = Visdom(port=args.visdom_port)
        win = None

    # todo: extend to multi process artisynth env
    envs = make_vec_envs(args.env_name, args.seed, args.num_processes,
                         args.gamma, config.log_directory,
                         args.add_timestep, device, False, num_frame_stack=None,
                         ip=args.ip, port=args.port, wait_action=args.wait_action)

    actor_critic = Policy(envs.observation_space.shape, envs.action_space,
                          base_kwargs={'recurrent': args.recurrent_policy})
    # load model
    if args.load_path is not None:
        logger.info("loading model: {}".format(args.load_path))
        actor_critic = torch.load(args.load_path)

    actor_critic.to(device)

    if args.algo == 'a2c':
        agent = algo.A2C_ACKTR(actor_critic, args.value_loss_coef,
                               args.entropy_coef, lr=args.lr,
                               eps=args.eps, alpha=args.alpha,
                               max_grad_norm=args.max_grad_norm)
    elif args.algo == 'ppo':
        agent = algo.PPO(actor_critic, args.clip_param, args.ppo_epoch, args.num_mini_batch,
                         args.value_loss_coef, args.entropy_coef, lr=args.lr,
                         eps=args.eps,
                         max_grad_norm=args.max_grad_norm)
    elif args.algo == 'acktr':
        agent = algo.A2C_ACKTR(actor_critic, args.value_loss_coef,
                               args.entropy_coef, acktr=True)

    rollouts = RolloutStorage(args.num_steps, args.num_processes,
                              envs.observation_space.shape, envs.action_space,
                              actor_critic.recurrent_hidden_state_size)

    obs = envs.reset()
    rollouts.obs[0].copy_(obs)
    rollouts.to(device)

    episode_rewards = deque(maxlen=10)

    start = time.time()
    for iter in range(num_updates):

        if args.use_linear_lr_decay:
            # decrease learning rate linearly
            if args.algo == "acktr":
                # use optimizer's learning rate since it's hard-coded in kfac.py
                update_linear_schedule(agent.optimizer, iter, num_updates, agent.optimizer.lr)
            else:
                update_linear_schedule(agent.optimizer, iter, num_updates, args.lr)

        if args.algo == 'ppo' and args.use_linear_clip_decay:
            agent.clip_param = args.clip_param * (1 - iter / float(num_updates))

        logger.debug('Start training')
        for step in range(args.num_steps):
            # Sample actions
            with torch.no_grad():
                value, action, action_log_prob, recurrent_hidden_states = actor_critic.act(
                    rollouts.obs[step],
                    rollouts.recurrent_hidden_states[step],
                    rollouts.masks[step])

            # Obser reward and next obs
            obs, reward, done, infos = envs.step(action)

            for info in infos:
                if 'episode' in info.keys():
                    episode_rewards.append(info['episode']['r'])

            # If done then clean the history of observations.
            masks = torch.FloatTensor([[0.0] if done_ else [1.0]
                                       for done_ in done])

            rollouts.insert(obs, recurrent_hidden_states, action, action_log_prob, value, reward, masks)

        with torch.no_grad():
            next_value = actor_critic.get_value(rollouts.obs[-1],
                                                rollouts.recurrent_hidden_states[-1],
                                                rollouts.masks[-1]).detach()

        rollouts.compute_returns(next_value, args.use_gae, args.gamma, args.tau)
        value_loss, action_loss, dist_entropy = agent.update(rollouts)
        rollouts.after_update()

        # save for every interval-th episode or for the last epoch
        if iter % args.save_interval == 0 or iter == num_updates - 1:
            save_path = os.path.join(config.trained_directory,
                                     args.algo + "-" + args.env_name + ".pt")
            logger.info("Saving model: {}".format(save_path))
            torch.save(actor_critic, save_path)

        total_num_steps = (iter + 1) * args.num_processes * args.num_steps

        if iter % args.log_interval == 0:
            logger.info('{}:{}\t value_loss: {:+.4f}, action_loss: {:+.4f}, dist_entropy: {:+.4f}'.format(
                iter, total_num_steps, value_loss, action_loss, dist_entropy))

        # todo: switch to episodic and cover other locations. This log is only for episodic
        if iter % args.episode_log_interval == 0 and len(episode_rewards) > 1:
            end = time.time()
            logger.info(
                "Updates {}, num timesteps {}, FPS {} \n Last {} training episodes: mean/median "
                "reward {:.1f}/{:.1f}, min/max reward {:.1f}/{:.1f}\n".
                    format(iter, total_num_steps,
                           int(total_num_steps / (end - start)),
                           len(episode_rewards),
                           np.mean(episode_rewards),
                           np.median(episode_rewards),
                           np.min(episode_rewards),
                           np.max(episode_rewards), dist_entropy,
                           value_loss, action_loss))

        # todo: after adding episodic training, replace this
        # and len(episode_rewards) > 1 \
        if args.eval_interval is not None and iter % args.eval_interval == 0:
            logger.info('Evaluate')

            # todo: what is allow_early_resets? (False for main, True for eval)
            eval_envs = make_vec_envs(args.env_name, args.seed, args.num_processes,
                                      args.gamma, config.log_directory,
                                      args.add_timestep, device, True, num_frame_stack=None,
                                      ip=args.ip, port=args.port, wait_action=args.wait_action)

            vec_norm = get_vec_normalize(eval_envs)
            if vec_norm is not None:
                vec_norm.eval()
                vec_norm.ob_rms = get_vec_normalize(envs).ob_rms

            eval_episode_rewards = []
            rewards = []

            obs = eval_envs.reset()
            eval_recurrent_hidden_states = torch.zeros(args.num_processes,
                                                       actor_critic.recurrent_hidden_state_size, device=device)
            eval_masks = torch.zeros(args.num_processes, 1, device=device)

            # while len(eval_episode_rewards) < 10:
            for eval_step in range(args.num_steps):
                with torch.no_grad():
                    _, action, _, eval_recurrent_hidden_states = actor_critic.act(
                        obs, eval_recurrent_hidden_states, eval_masks, deterministic=True)

                # Obser reward and next obs
                obs, reward, done, infos = eval_envs.step(action)

                eval_masks = torch.tensor([[0.0] if done_ else [1.0]
                                           for done_ in done],
                                          dtype=torch.float32,
                                          device=device)
                logger.info('eval step reward: {}'.format(reward))

                if args.episodic:
                    for info in infos:
                        if 'episode' in info.keys():
                            eval_episode_rewards.append(info['episode']['r'])
                else:
                    rewards.append(reward)

            eval_envs.close()

            if args.episodic:
                logger.info("Evaluation using {} episodes: mean reward {:.5f}\n".
                            format(len(eval_episode_rewards),
                                   np.mean(eval_episode_rewards)))
            else:
                logger.info("Evaluation using {} steps: mean reward {:.5f}\n".
                            format(args.num_steps,
                                   np.mean(rewards)))

        if args.vis and iter % args.vis_interval == 0:
            try:
                # Sometimes monitor doesn't properly flush the outputs
                logger.info("Visdom log update")
                win = visdom_plot(viz, win, config.visdom_log_directory, args.env_name,
                                  args.algo, args.num_env_steps)
            except IOError:
                pass


if __name__ == "__main__":
    main()
