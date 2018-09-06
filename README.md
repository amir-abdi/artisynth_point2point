# artisynth_rl
This is the code for **"Muscle Excitation Estimation in Biomechanical Simulation 
Using NAF Reinforcement Learning"** paper submitted to the Computational 
Biomechanics for Medicine XIII, A MICCAI 2018 Workshop, Granada, Spain, 
2018 September 16.

This implementation used an 
[extended version of keras-rl](https://github.com/amir-abdi/keras-rl) to train 
a reinforcement learning agent for point-to-point reaching tasks in 
biomechanical environments.  


## Dependencies

- **artisynth_core**: [ArtiSynth](https://www.artisynth.org/Main/HomePage) is a 
biomechanical modeling environment which supports both rigid bodies and finite 
elements. ArtiSynth can be downloaded from its 
[git repository](https://github.com/artisynth/artisynth_core),
and its installation guide is available 
[here](https://www.artisynth.org/Documentation/InstallGuide). 

- **keras-rl**: keras-rl is a reinforcement learning library build on top of 
the keras library. This library is extended by me to include some new 
functionalities, the details of which are available in the paper. 
Please check out the extended forked version from 
[here](https://github.com/amir-abdi/keras-rl).

- **keras**: Details available [here](https://keras.io).

- **TensorFlow**: Although keras can run with theano as its backend, we have
extensively leveraged the tensorboard features in our implementation; thus, 
we encourage users to use TensorFlow. 

- **java-json**: The message passing between the keras-rl (python) and 
artisynth (java) is through a tcp socket and the packets are sent as serialized
JSON objects. To enable json support in java, include the java-json.jar
(available in lib/ directory) in runtime libraries of the artisynth_rl project.

## Installation

- Install ArtiSynth following its 
[installation guide](https://www.artisynth.org/Documentation/InstallGuide)
- Install [TensorFlow](https://www.tensorflow.org/install/)
- Install [keras](https://keras.io/#installation)
- Checkout [keras-rl](https://github.com/amir-abdi/keras-rl)
- Checkout [artisynth_rl](https://github.com/amir-abdi/artisynth_rl)
- Set 